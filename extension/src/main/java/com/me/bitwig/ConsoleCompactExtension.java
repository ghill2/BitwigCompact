package com.me.bitwig;

import com.bitwig.extension.controller.ControllerExtension;
import com.bitwig.extension.controller.api.ControllerHost;
import com.bitwig.extension.controller.api.CursorTrack;
import com.bitwig.extension.controller.api.MidiIn;
import com.bitwig.extension.controller.api.MidiOut;
import com.bitwig.extension.controller.api.PinnableCursorDevice;
import com.me.shell.ConsoleCompactShell;
import com.me.shell.ShellOutput;
import com.me.shell.monitor.UdpStatusBroadcaster;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Thin adapter that hosts the standalone {@link ConsoleCompactShell} inside Bitwig and wires it to
 * the Controller API.
 *
 * Shell input wiring:
 *   cursor device name observer    -> shell.deviceSelected(name)
 *   direct parameter observers     -> shell.parameterValueChanged(name, value)
     MidiIn callback                -> shell.midiReceived(status, d1, d2)
 *
 * Shell output wiring:
 *   sendMidi    -> MidiOut.sendMidi (CC to the hardware, updates knob position displays)
 *   setParameter -> PinnableCursorDevice.setDirectParameterValueNormalized (by parameter name)
 */
public final class ConsoleCompactExtension extends ControllerExtension
{
   /** Fine-resolution granularity, same value the official Komplete Kontrol extension uses. */
   private static final Integer GRANULARITY = 2;

   /** Cap for values buffered before their parameter name is known. */
   private static final int MAX_PENDING_VALUES = 2048;

   private ControllerHost        host;
   private MidiIn                midiIn;
   private MidiOut               midiOut;
   private CursorTrack           cursorTrack;
   private PinnableCursorDevice  cursorDevice;

   private ConsoleCompactShell   shell;
   private boolean               debug;
   private UdpStatusBroadcaster  monitor;

   /** Direct-parameter resolution state; keys are parameter ids/names exactly as Bitwig reports them. */
   private final Map<String, String> nameToId       = new HashMap<>();
   private final Map<String, String> idToName       = new HashMap<>();
   /** Values that arrived before the matching (id -> name) pair did, flushed once the name shows up. */
   private final Map<String, Double> pendingValues  = new HashMap<>();
   private final Set<String>         reportedMissing = new HashSet<>();

   /** Parameter dump (config authoring aid): ordered id -> name plus last known values. */
   private final Map<String, String> dumpParams   = new LinkedHashMap<>();
   private final Map<String, Double> dumpValues   = new HashMap<>();
   private String  dumpDevice         = "";
   private boolean dumpFlushScheduled = false;
   private boolean dumpWriteFailed    = false;

   protected ConsoleCompactExtension(final ConsoleCompactDefinition definition, final ControllerHost host)
   {
      super(definition, host);
   }

   @Override
   public void init()
   {
      this.host = getHost();

      this.midiIn = this.host.getMidiInPort(0);
      this.midiIn.setMidiCallback(this::onMidiIn);
      this.midiOut = this.host.getMidiOutPort(0);

      // Same pattern as the official Softube Console 1 and Komplete Kontrol extensions:
      // a cursor track (follows the DAW's selected track) plus the no-arg device cursor,
      // which follows the selected device. (createEditorCursorDevice() is rejected by
      // Bitwig; the 4-arg createCursorDevice overload targets a named device instead.)
      this.cursorTrack = this.host.createCursorTrack(0, 0);
      this.cursorDevice = this.cursorTrack.createCursorDevice();

      // The shell must exist before observers fire into it.
      this.shell = new ConsoleCompactShell(configDir(), new ExtensionShellOutput());
      this.debug = Boolean.parseBoolean(System.getProperty("consolecompact.debug", "true"));
      this.shell.setDebug(this.debug);
      log("config dir: " + configDir());
      final boolean ready = this.shell.start();
      if (!ready)
      {
         log("shell could not start; check the config directory: " + configDir());
      }

      // Broadcast shell state to the monitor window (localhost UDP; no-op when nothing listens).
      this.monitor = new UdpStatusBroadcaster();
      this.shell.setMonitor(this.monitor);

      // Direct parameter access (per-plugin parameters, by name). See the official Komplete Kontrol
      // extension's DirectParameterControl for the reference usage of these calls.
      this.cursorDevice.addDirectParameterIdObserver(this::onParameterIds);
      this.cursorDevice.addDirectParameterNameObserver(40, this::onParameterName);
      this.cursorDevice.addDirectParameterNormalizedValueObserver(this::onParameterValue);

      this.cursorDevice.name().markInterested();
      this.cursorDevice.name().addValueObserver(this::onDeviceName);

      this.host.showPopupNotification("MyController Initialized");
   }

   @Override
   public void flush()
   {
      // The shell sends MIDI immediately from its callbacks; nothing deferred yet.
   }

   @Override
   public void exit()
   {
      if (this.monitor != null)
      {
         this.monitor.close();
      }
      if (this.host != null)
      {
         this.host.showPopupNotification("MyController Exited");
      }
   }

   private static Path configDir()
   {
      final String override = System.getProperty("consolecompact.config.dir");
      if (override != null && !override.isBlank())
      {
         return Paths.get(override);
      }
      return Paths.get(System.getProperty("user.home"), "Documents", "Bitwig Studio", "ControllerBitwig");
   }

   /**
    * Where last-device-params.txt is written. Defaults to the config directory;
    * override with -Dconsolecompact.dump.dir (e.g. to point at a repository checkout).
    */
   private static Path dumpDir()
   {
      final String override = System.getProperty("consolecompact.dump.dir");
      if (override != null && !override.isBlank())
      {
         return Paths.get(override);
      }
      return configDir();
   }

   private void onMidiIn(final int status, final int data1, final int data2)
   {
      if (this.shell != null)
      {
         this.shell.midiReceived(status, data1, data2);
      }
   }

   private void onDeviceName(final String name)
   {
      log("device name observed: '" + name + "'");
      this.dumpDevice = name == null ? "" : name.trim();
      resetDump();
      if (this.shell != null)
      {
         this.shell.deviceSelected(name);
      }
   }

   private void onParameterIds(final String[] ids)
   {
      // A new device's parameter set arrived: reset resolution state.
      this.nameToId.clear();
      this.idToName.clear();
      this.pendingValues.clear();
      this.reportedMissing.clear();
      resetDump();
      log("device parameters: " + (ids == null ? 0 : ids.length));
   }

   private void onParameterName(final String id, final String name)
   {
      if (id == null || name == null || name.isBlank())
      {
         return;
      }
      if (this.debug)
      {
         log("device param: '" + name + "'   (use this name in devices/*.json)");
      }
      this.idToName.put(id, name);
      this.idToName.putIfAbsent(canonicalId(id), name);
      // First id wins for duplicate names, keeping the mapping stable.
      this.nameToId.putIfAbsent(name, id);

      this.dumpParams.put(id, name);
      writeDumpNow();

      final Double pending = this.pendingValues.remove(id);
      if (pending != null && this.shell != null)
      {
         this.shell.parameterValueChanged(name, pending.doubleValue());
      }
   }

   private void onParameterValue(final String id, final double value)
   {
      if (this.shell == null)
      {
         return;
      }
      this.dumpValues.put(id, Double.valueOf(value));
      scheduleDumpWrite();

      String name = this.idToName.get(id);
      if (name == null)
      {
         name = this.idToName.get(canonicalId(id));
      }
      if (name != null)
      {
         if (this.debug)
         {
            log("vst param '" + name + "' = " + value);
         }
         this.shell.parameterValueChanged(name, value);
      }
      else if (this.pendingValues.size() < MAX_PENDING_VALUES)
      {
         this.pendingValues.put(id, Double.valueOf(value));
      }
   }

   /**
    * Bitwig reports some plugin parameters under aliased ids (e.g. "CONTENTS/x" vs
    * "CONTENTS/ROOT_GENERIC_MODULE/x"). Collapse the alias so ids resolve against names.
    */
   private static String canonicalId(final String id)
   {
      final String marker = "ROOT_GENERIC_MODULE/";
      final int index = id.indexOf(marker);
      if (index < 0)
      {
         return id;
      }
      return id.substring(0, index) + id.substring(index + marker.length());
   }

   private void log(final String message)
   {
      if (this.host != null)
      {
         this.host.println("[console-compact] " + message);
      }
   }

   private void resetDump()
   {
      this.dumpParams.clear();
      this.dumpValues.clear();
      writeDumpNow();
   }

   /** Throttled dump write for value updates (name/id/device changes write immediately). */
   private void scheduleDumpWrite()
   {
      if (!this.debug || this.dumpFlushScheduled)
      {
         return;
      }
      this.dumpFlushScheduled = true;
      this.host.scheduleTask(() -> {
         ConsoleCompactExtension.this.dumpFlushScheduled = false;
         ConsoleCompactExtension.this.writeDumpNow();
      }, 300);
   }

   /** Writes last-device-params.txt into the config dir: one line per parameter, in the order the plugin reports them. */
   private void writeDumpNow()
   {
      if (!this.debug)
      {
         return;
      }
      try
      {
         final StringBuilder sb = new StringBuilder();
         sb.append("device: ").append(this.dumpDevice.isEmpty() ? "<none>" : this.dumpDevice).append('\n');
         sb.append("params: ").append(this.dumpParams.size()).append('\n');
         sb.append("# name\tvalue\tid\n");
         for (final Map.Entry<String, String> entry : this.dumpParams.entrySet())
         {
            final Double value = this.dumpValues.get(entry.getKey());
            sb.append(entry.getValue()).append('\t') //
               .append(value == null ? "-" : String.format(Locale.ROOT, "%.4f", value)) //
               .append('\t').append(entry.getKey()).append('\n');
         }
         Files.createDirectories(dumpDir());
         Files.write(dumpDir().resolve("last-device-params.txt"), sb.toString().getBytes(StandardCharsets.UTF_8));
         this.dumpWriteFailed = false;
      }
      catch (final IOException e)
      {
         if (!this.dumpWriteFailed)
         {
            this.dumpWriteFailed = true;
            log("cannot write parameter dump: " + e.getMessage());
         }
      }
   }

   private final class ExtensionShellOutput implements ShellOutput
   {
      @Override
      public void sendMidi(final int status, final int data1, final int data2)
      {
         ConsoleCompactExtension.this.midiOut.sendMidi(status, data1, data2);
      }

      @Override
      public void setParameter(final String parameterName, final double normalizedValue)
      {
         final String id = ConsoleCompactExtension.this.nameToId.get(parameterName);
         if (id == null)
         {
            if (ConsoleCompactExtension.this.reportedMissing.add(parameterName))
            {
               ConsoleCompactExtension.this.log("parameter '" + parameterName
                 + "' not found on this device (check devices/*.json)");
            }
            return;
         }
         ConsoleCompactExtension.this.cursorDevice.setDirectParameterValueNormalized(id, Double.valueOf(normalizedValue), GRANULARITY);
      }

      @Override
      public void log(final String message)
      {
         ConsoleCompactExtension.this.log(message);
      }
   }
}
