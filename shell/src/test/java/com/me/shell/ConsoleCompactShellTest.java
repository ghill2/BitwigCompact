package com.me.shell;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsoleCompactShellTest
{
   @TempDir
   Path configDir;

   private static final class RecordingOutput implements ShellOutput
   {
      final List<String> events = new ArrayList<>();

      @Override
      public void sendMidi(final int status, final int d1, final int d2)
      {
         this.events.add("midi " + d1 + " " + d2);
      }

      @Override
      public void setParameter(final String name, final double value)
      {
         this.events.add("param " + name + " " + value);
      }

      @Override
      public void log(final String message)
      {
         this.events.add("log " + message);
      }
   }

   private RecordingOutput givenHardwareAndTwoDevices() throws Exception
   {
      Files.write(this.configDir.resolve("console-compact.json"), List.of( //
         "{",
         "  \"midiChannel\": 0,",
         "  \"knobs\": [",
         "    {\"id\": 1, \"cc\": 71},",
         "    {\"id\": 2, \"cc\": 72},",
         "    {\"id\": 3, \"cc\": 73}",
         "  ]",
         "}"));
      final Path devices = Files.createDirectories(this.configDir.resolve("devices"));
      Files.write(devices.resolve("testdev.json"), List.of( //
         "{",
         "  \"match\": [\"TestDev\"],",
         "  \"knobs\": {\"1\": \"Cutoff\", \"2\": \"Resonance\"}",
         "}"));
      Files.write(devices.resolve("other.json"), List.of( //
         "{",
         "  \"match\": [\"Other\"],",
         "  \"knobs\": {\"3\": \"Wobble\"}",
         "}"));
      final RecordingOutput out = new RecordingOutput();
      final ConsoleCompactShell shell = new ConsoleCompactShell(this.configDir, out);
      assertTrue(shell.start());
      return out;
   }

   private ConsoleCompactShell newShell(final ShellOutput out)
   {
      final ConsoleCompactShell shell = new ConsoleCompactShell(this.configDir, out);
      assertTrue(shell.start());
      return shell;
   }

   @Test
   void knobTurnSetsParameterByName() throws Exception
   {
      final RecordingOutput out = givenHardwareAndTwoDevices();
      final ConsoleCompactShell shell = newShell(out);
      shell.deviceSelected("TestDev");
      out.events.clear();

      shell.midiReceived(0xB0, 71, 64);

      assertEquals(List.of("param Cutoff " + 64 / 127.0), out.events);
   }

   @Test
   void wrongChannelOrUnknownCcIsIgnored() throws Exception
   {
      final RecordingOutput out = givenHardwareAndTwoDevices();
      final ConsoleCompactShell shell = newShell(out);
      shell.deviceSelected("TestDev");
      out.events.clear();

      shell.midiReceived(0xB1, 71, 64); // wrong channel
      shell.midiReceived(0xB0, 99, 64); // unknown cc
      shell.midiReceived(0x90, 71, 64); // not a CC

      assertTrue(out.events.isEmpty());
   }

   @Test
   void valueChangeUpdatesKnobDisplayWithDeDup() throws Exception
   {
      final RecordingOutput out = givenHardwareAndTwoDevices();
      final ConsoleCompactShell shell = newShell(out);
      shell.deviceSelected("TestDev");
      out.events.clear();

      shell.parameterValueChanged("Cutoff", 0.5); // 0.5 * 127 = 63.5 -> 64
      shell.parameterValueChanged("Cutoff", 0.5); // same CC value: de-duplicated
      shell.parameterValueChanged("Unmapped", 0.5); // not bound to any knob

      assertEquals(List.of("midi 71 64"), out.events);
   }

   @Test
   void unmappedKnobsAreBlankedOnSelection() throws Exception
   {
      final RecordingOutput out = givenHardwareAndTwoDevices();
      final ConsoleCompactShell shell = newShell(out);

      shell.deviceSelected("TestDev"); // knob 3 is unmapped

      assertTrue(out.events.contains("midi 73 0"));
   }

   @Test
   void deviceSwitchRemapsAndResets() throws Exception
   {
      final RecordingOutput out = givenHardwareAndTwoDevices();
      final ConsoleCompactShell shell = newShell(out);
      shell.deviceSelected("TestDev");
      shell.parameterValueChanged("Cutoff", 1.0);
      assertTrue(out.events.contains("midi 71 127"));
      out.events.clear();

      shell.deviceSelected("Other");

      // knob 1 was mapped on the previous device and is now unmapped -> blanked
      assertTrue(out.events.contains("midi 71 0"));
      out.events.clear();

      shell.parameterValueChanged("Wobble", 0.5);
      shell.parameterValueChanged("Cutoff", 0.75); // no longer mapped on this device
      assertEquals(List.of("midi 73 64"), out.events);
   }

   @Test
   void unmappedDeviceLeavesKnobsDark() throws Exception
   {
      final RecordingOutput out = givenHardwareAndTwoDevices();
      final ConsoleCompactShell shell = newShell(out);

      shell.deviceSelected("Something Unknown");

      assertTrue(out.events.stream().anyMatch(e -> e.startsWith("log ") && e.contains("no mapping")));
      assertFalse(out.events.contains("midi 71 64"));
      shell.midiReceived(0xB0, 71, 64);
      assertFalse(out.events.contains("param Cutoff " + 64 / 127.0));
   }

   @Test
   void matchingIsCaseInsensitiveExactThenPrefix() throws Exception
   {
      final RecordingOutput out = givenHardwareAndTwoDevices();
      final ConsoleCompactShell shell = newShell(out);

      shell.deviceSelected("TESTDEV"); // exact, different case
      assertTrue(out.events.stream().anyMatch(e -> e.contains("via testdev.json")));

      shell.deviceSelected("TestDev Plus"); // prefix match
      assertTrue(out.events.stream().anyMatch(e -> e.contains("via testdev.json")));
   }

   @Test
   void missingHardwareConfigLogsErrorAndStaysAlive() throws Exception
   {
      final RecordingOutput out = new RecordingOutput();
      final ConsoleCompactShell shell = new ConsoleCompactShell(this.configDir, out);

      assertFalse(shell.start());
      shell.deviceSelected("TestDev"); // must not throw
      shell.midiReceived(0xB0, 71, 64);
      shell.parameterValueChanged("Cutoff", 0.5);
      assertTrue(out.events.stream().anyMatch(e -> e.startsWith("log ERROR")));
   }

   /** Writes a one-knob hardware config and a device config whose knob 1 uses the given JSON binding. */
   private void givenScaledConfig(final String bindingJson) throws Exception
   {
      Files.write(this.configDir.resolve("console-compact.json"), List.of( //
         "{", "  \"midiChannel\": 0,", "  \"knobs\": [{\"id\": 1, \"cc\": 71}]", "}"));
      final Path devices = Files.createDirectories(this.configDir.resolve("devices"));
      Files.write(devices.resolve("scaled.json"), List.of( //
         "{", "  \"match\": [\"Scaled\"],", "  \"knobs\": {\"1\": " + bindingJson + "}", "}"));
   }

   @Test
   void rangeScalingMapsKnobTravelToParameterSubRange() throws Exception
   {
      givenScaledConfig("{\"param\": \"Link\", \"range\": [0.0, 0.5]}");
      final RecordingOutput out = new RecordingOutput();
      final ConsoleCompactShell shell = newShell(out);
      shell.deviceSelected("Scaled");
      out.events.clear();

      shell.midiReceived(0xB0, 71, 0);   // bottom of travel -> 0.0
      shell.midiReceived(0xB0, 71, 127); // top of travel    -> 0.5 (full useful range in half the CC travel)
      shell.midiReceived(0xB0, 71, 64);  // mid travel       -> 64/127 * 0.5

      assertEquals(List.of( //
         "param Link 0.0", //
         "param Link 0.5", //
         "param Link " + 64 / 127.0 * 0.5), out.events);
   }

   @Test
   void rangeScalingClampsLedFeedbackToKnobTravel() throws Exception
   {
      givenScaledConfig("{\"param\": \"Link\", \"range\": [0.0, 0.5]}");
      final RecordingOutput out = new RecordingOutput();
      final ConsoleCompactShell shell = newShell(out);
      shell.deviceSelected("Scaled");
      out.events.clear();

      shell.parameterValueChanged("Link", 0.7);  // above mapped max: LED pins at 127
      shell.parameterValueChanged("Link", 0.25); // 0.25/0.5 * 127 = 63.5 -> 64
      shell.parameterValueChanged("Link", 0.0);  // -> 0

      assertEquals(List.of("midi 71 127", "midi 71 64", "midi 71 0"), out.events);
   }

   @Test
   void reversedRangeInvertsKnob() throws Exception
   {
      givenScaledConfig("{\"param\": \"Inv\", \"range\": [1.0, 0.0]}");
      final RecordingOutput out = new RecordingOutput();
      final ConsoleCompactShell shell = newShell(out);
      shell.deviceSelected("Scaled");
      out.events.clear();

      shell.midiReceived(0xB0, 71, 0);   // CC 0 -> param 1.0
      shell.midiReceived(0xB0, 71, 127); // CC 127 -> param 0.0
      shell.parameterValueChanged("Inv", 1.0); // value 1.0 -> CC 0

      assertEquals(List.of("param Inv 1.0", "param Inv 0.0", "midi 71 0"), out.events);
   }

   private static final class RecordingMonitor implements com.me.shell.monitor.ShellMonitor
   {
      final List<String> events = new ArrayList<>();

      @Override
      public void deviceChanged(final com.me.shell.monitor.DeviceSnapshot snapshot)
      {
         final StringBuilder knobs = new StringBuilder();
         for (final com.me.shell.monitor.KnobStatus knob : snapshot.knobs)
         {
            if (knobs.length() > 0)
            {
               knobs.append(",");
            }
            knobs.append(knob.knobId).append("=").append(knob.mapped ? knob.param : "-");
            knobs.append("/cc").append(knob.cc);
         }
         this.events.add("device " + snapshot.device + " " + snapshot.map + " [" + knobs + "]");
      }

      @Override
      public void knobUpdated(final int knobId, final double knobPosition, final double paramValue)
      {
         this.events.add("knob " + knobId + " " + knobPosition + " " + paramValue);
      }
   }

   @Test
   void monitorReceivesSnapshotAndLiveUpdates() throws Exception
   {
      final RecordingOutput out = givenHardwareAndTwoDevices();
      final ConsoleCompactShell shell = newShell(out);
      final RecordingMonitor monitor = new RecordingMonitor();
      shell.setMonitor(monitor);
      monitor.events.clear();

      shell.deviceSelected("TestDev");
      shell.midiReceived(0xB0, 71, 64);         // knob 1 to 64/127
      shell.parameterValueChanged("Cutoff", 1.0); // -> knob position 1.0

      assertTrue(monitor.events.stream().anyMatch(e -> e.equals( //
         "device TestDev testdev.json [1=Cutoff/cc71,2=Resonance/cc72,3=-/cc73]")));
      assertTrue(monitor.events.contains("knob 1 " + 64 / 127.0 + " " + 64 / 127.0));
      assertTrue(monitor.events.contains("knob 1 1.0 1.0"));
   }
}
