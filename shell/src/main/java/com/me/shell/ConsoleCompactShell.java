package com.me.shell;

import com.me.shell.config.ConfigLoader;
import com.me.shell.config.DeviceMap;
import com.me.shell.config.HardwareMap;
import com.me.shell.config.KnobConfig;
import com.me.shell.config.ParamBinding;
import com.me.shell.monitor.DeviceSnapshot;
import com.me.shell.monitor.KnobStatus;
import com.me.shell.monitor.ShellMonitor;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Core of the Console Compact integration. Pure Java, no Bitwig dependencies.
 *
 * Inbound events ({@link ShellInput}): device selection, parameter value changes, raw MIDI from the hardware.
 * Outbound events ({@link ShellOutput}): MIDI to the hardware (knob position displays), parameter updates by name.
 *
 * The same instance runs inside the Bitwig extension (fed by the Controller API) and in the standalone
 * test harness (fed by javax.sound.midi or the simulator).
 */
public final class ConsoleCompactShell implements ShellInput
{
   private static final int CC_MESSAGE = 0xB;

   private final ShellOutput  out;
   private final ConfigLoader configLoader;

   private HardwareMap               hardware;
   private String                    currentDevice = "";
   private DeviceMap                 currentMap;
   private final Map<Integer, Integer> lastSentCc = new HashMap<>();
   private final Map<Integer, Double>  knobValue  = new HashMap<>();
   private boolean                   debug = false;
   private ShellMonitor              monitor;

   public ConsoleCompactShell(final Path configDir, final ShellOutput out)
   {
      this.out = out;
      this.configLoader = new ConfigLoader(configDir);
   }

   /**
    * Verbose logging of every inbound MIDI message and the decision made about it.
    * The extension enables this (via the consolecompact.debug system property, default true)
    * while bringing the hardware up.
    */
   public void setDebug(final boolean debug)
   {
      this.debug = debug;
   }

   /** Optional state observer for visualisation (the monitor window); the caller owns closing it. */
   public void setMonitor(final ShellMonitor monitor)
   {
      this.monitor = monitor;
   }

   /**
    * Loads the hardware definition from console-compact.json. Failures are logged, not thrown, so a
    * host like the Bitwig extension stays alive with an inert shell instead of dying in init().
    *
    * @return true if the shell is ready to process events
    */
   public boolean start()
   {
      try
      {
         this.hardware = this.configLoader.loadHardware(msg -> this.out.log("WARNING: " + msg));
      }
      catch (final IOException e)
      {
         this.out.log("ERROR: cannot load hardware config: " + e.getMessage());
         return false;
      }
      final StringBuilder knobs = new StringBuilder();
      for (final KnobConfig knob : this.hardware.knobs())
      {
         if (knobs.length() > 0)
         {
            knobs.append(", ");
         }
         knobs.append(knob.id).append("=CC").append(knob.cc);
      }
      this.out.log("Console Compact ready: " + this.hardware.knobs().size() + " knobs, MIDI channel "
         + (this.hardware.channel() + 1) + " [" + knobs + "]");
      return true;
   }

   @Override
   public void deviceSelected(final String deviceName)
   {
      if (this.hardware == null)
      {
         return;
      }
      final String name = deviceName == null ? "" : deviceName.trim();
      if (name.equals(this.currentDevice))
      {
         if (this.debug)
         {
            this.out.log("device unchanged: '" + name + "' (skipped)");
         }
         return;
      }
      if (this.debug)
      {
         this.out.log("device selected: '" + name + "'");
      }
      this.currentDevice = name;
      this.currentMap = null;
      this.lastSentCc.clear();
      this.knobValue.clear();
      if (name.isEmpty())
      {
         this.out.log("no device selected - knob mappings cleared");
         blankUnmappedKnobs();
         emitSnapshot();
         return;
      }
      try
      {
         this.currentMap = this.configLoader.findDeviceMap(name, msg -> this.out.log("WARNING: " + msg)).orElse(null);
      }
      catch (final IOException e)
      {
         this.out.log("ERROR: cannot read device configs: " + e.getMessage());
      }
      if (this.currentMap == null)
      {
         this.out.log("device '" + name + "': no mapping found (configs available: "
            + this.configLoader.availableConfigNames() + ")");
         blankUnmappedKnobs();
         emitSnapshot();
         return;
      }
      final StringBuilder bindings = new StringBuilder();
      this.currentMap.knobBindings().forEach((knobId, binding) -> {
         if (bindings.length() > 0)
         {
            bindings.append(", ");
         }
         bindings.append(knobId).append("->").append(binding);
      });
      this.out.log("device '" + name + "' via " + this.currentMap.source() + ": " + bindings);
      blankUnmappedKnobs();
      emitSnapshot();
   }

   @Override
   public void parameterValueChanged(final String parameterName, final double normalizedValue)
   {
      if (this.hardware == null || this.currentMap == null || parameterName == null)
      {
         return;
      }
      final List<Integer> knobs = this.currentMap.knobsForParameter(parameterName);
      if (knobs.isEmpty())
      {
         return;
      }
      for (final int knobId : knobs)
      {
         final Optional<ParamBinding> binding = this.currentMap.bindingForKnob(knobId);
         if (binding.isPresent())
         {
            final int ccValue = binding.get().valueToCc(normalizedValue);
            sendDisplayCc(knobId, ccValue);
            this.knobValue.put(knobId, Double.valueOf(normalizedValue));
            if (this.monitor != null)
            {
               this.monitor.knobUpdated(knobId, ccValue / 127.0, normalizedValue);
            }
         }
      }
   }

   @Override
   public void midiReceived(final int status, final int data1, final int data2)
   {
      if (this.hardware == null)
      {
         return;
      }
      if (this.debug)
      {
         this.out.log("midi in: status=0x" + Integer.toHexString(status) + " d1=" + data1 + " d2=" + data2);
      }
      if (status >> 4 != CC_MESSAGE)
      {
         if (this.debug)
         {
            this.out.log("  ignored: not a CC message");
         }
         return;
      }
      if ((status & 0xF) != this.hardware.channel())
      {
         if (this.debug)
         {
            this.out.log("  ignored: channel " + ((status & 0xF) + 1) + ", expected channel "
               + (this.hardware.channel() + 1));
         }
         return;
      }
      if (this.currentMap == null)
      {
         if (this.debug)
         {
            this.out.log("  ignored: no device mapping loaded (see device selection logs)");
         }
         return;
      }
      final int knobId = this.hardware.knobForCc(data1).orElse(-1);
      if (knobId < 0)
      {
         if (this.debug)
         {
            this.out.log("  ignored: CC " + data1 + " is not one of the hardware knobs");
         }
         return;
      }
      final Optional<ParamBinding> binding = this.currentMap.bindingForKnob(knobId);
      if (binding.isEmpty())
      {
         if (this.debug)
         {
            this.out.log("  ignored: knob " + knobId + " has no parameter on this device");
         }
         return;
      }
      final double value = binding.get().ccToValue(data2);
      if (this.debug)
      {
         this.out.log("  knob " + knobId + " (CC " + data1 + ") -> '" + binding.get().name + "' = " + value
            + (binding.get().isFullRange() ? "" : " (scaled " + binding.get() + ")"));
      }
      this.out.setParameter(binding.get().name, value);
      this.knobValue.put(knobId, Double.valueOf(value));
      if (this.monitor != null)
      {
         this.monitor.knobUpdated(knobId, data2 / 127.0, value);
      }
   }

   /** Position displays of unmapped knobs go to 0 so stale positions are not misleading. */
   private void blankUnmappedKnobs()
   {
      for (final KnobConfig knob : this.hardware.knobs())
      {
         final boolean mapped = this.currentMap != null && this.currentMap.bindingForKnob(knob.id).isPresent();
         if (!mapped)
         {
            sendDisplayCc(knob.id, 0);
         }
      }
   }

   /** Update a knob position display; de-duplicated per knob (also suppresses set/observe echoes). */
   private void sendDisplayCc(final int knobId, final int ccValue)
   {
      final Optional<Integer> cc = this.hardware.ccForKnob(knobId);
      if (cc.isEmpty())
      {
         return;
      }
      final Integer last = this.lastSentCc.get(knobId);
      if (last != null && last == ccValue)
      {
         return;
      }
      this.lastSentCc.put(knobId, ccValue);
      this.out.sendMidi(0xB0 | this.hardware.channel(), cc.get(), ccValue);
   }

   /** Sends the full state snapshot to the monitor (if attached). */
   private void emitSnapshot()
   {
      if (this.monitor == null || this.hardware == null)
      {
         return;
      }
      final List<KnobStatus> knobs = new ArrayList<>();
      for (final KnobConfig config : this.hardware.knobs())
      {
         final Optional<ParamBinding> binding = this.currentMap == null
            ? Optional.empty()
            : this.currentMap.bindingForKnob(config.id);
         final Double last = this.knobValue.get(config.id);
         knobs.add(new KnobStatus(config.id, config.cc, //
            binding.map(b -> b.name).orElse(null), //
            binding.map(b -> Double.valueOf(b.min)).orElse(Double.valueOf(0.0)).doubleValue(), //
            binding.map(b -> Double.valueOf(b.max)).orElse(Double.valueOf(1.0)).doubleValue(), //
            binding.isPresent(), //
            last == null ? -1.0 : last.doubleValue()));
      }
      this.monitor.deviceChanged(new DeviceSnapshot(this.currentDevice, //
         this.currentMap == null ? null : this.currentMap.source(), knobs));
   }
}
