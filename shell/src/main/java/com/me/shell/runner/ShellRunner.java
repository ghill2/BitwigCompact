package com.me.shell.runner;

import com.me.shell.ConsoleCompactShell;
import com.me.shell.ShellOutput;
import com.me.shell.monitor.UdpStatusBroadcaster;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiDevice.Info;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Receiver;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Transmitter;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Scanner;

/**
 * Standalone dev/test harness for the shell. Not used when running inside Bitwig.
 *
 * Usage:
 *   java -jar console-compact-shell.jar --list-ports
 *   java -jar console-compact-shell.jar --simulate [--config &lt;dir&gt;]
 *   java -jar console-compact-shell.jar --hardware &lt;port-name-substring&gt; [--config &lt;dir&gt;]
 *
 * Default config dir: ~/Documents/Bitwig Studio/ControllerBitwig
 */
public final class ShellRunner
{
   public static void main(final String[] args)
   {
      int exit = 0;
      try
      {
         if (args.length == 0)
         {
            usage();
            exit = 2;
         }
         else
         {
            switch (args[0])
            {
               case "--list-ports":
                  listPorts();
                  break;
               case "--simulate":
                  simulate(configDir(args));
                  break;
               case "--hardware":
                  hardware(args, configDir(args));
                  break;
               default:
                  usage();
                  exit = 2;
            }
         }
      }
      catch (final Exception e)
      {
         System.err.println("error: " + e.getMessage());
         exit = 1;
      }
      if (exit != 0)
      {
         System.exit(exit);
      }
   }

   private static void usage()
   {
      System.out.println("usage: ShellRunner --list-ports");
      System.out.println("       ShellRunner --simulate [--config <dir>]");
      System.out.println("       ShellRunner --hardware <port-name-substring> [--config <dir>]");
   }

   private static Path configDir(final String[] args)
   {
      for (int i = 1; i < args.length - 1; i++)
      {
         if ("--config".equals(args[i]))
         {
            return Paths.get(args[i + 1]);
         }
      }
      return Paths.get(System.getProperty("user.home"), "Documents", "Bitwig Studio", "ControllerBitwig");
   }

   private static void listPorts() throws MidiUnavailableException
   {
      for (final Info info : MidiSystem.getMidiDeviceInfo())
      {
         final MidiDevice device = MidiSystem.getMidiDevice(info);
         final boolean in = device.getMaxTransmitters() != 0;
         final boolean out = device.getMaxReceivers() != 0;
         if (!in && !out)
         {
            continue;
         }
         System.out.println((in ? "in  " : "    ") + (out ? "out " : "    ") + " " + info.getName() + "  ["
            + info.getVendor() + "]");
      }
   }

   private static ShellOutput consoleOutput()
   {
      return new ShellOutput()
      {
         @Override
         public void sendMidi(final int status, final int d1, final int d2)
         {
            System.out.println("[OUT midi ] status=0x" + Integer.toHexString(status) + " d1=" + d1 + " d2=" + d2);
         }

         @Override
         public void setParameter(final String name, final double value)
         {
            System.out.println("[OUT param] " + name + " -> " + String.format(Locale.ROOT, "%.4f", Double.valueOf(value)));
         }

         @Override
         public void log(final String message)
         {
            System.out.println("[shell    ] " + message);
         }
      };
   }

   private static void simulate(final Path configDir)
   {
      final ConsoleCompactShell shell = new ConsoleCompactShell(configDir, consoleOutput());
      shell.setMonitor(new UdpStatusBroadcaster());
      if (!shell.start())
      {
         System.err.println("failed to load hardware config from " + configDir + " (edit console-compact.json)");
         System.exit(1);
      }
      System.out.println("config dir: " + configDir);
      System.out.println("commands: device <name> | cc <cc> <value> | value <param> <0..1> | quit");
      try (Scanner scanner = new Scanner(System.in, StandardCharsets.UTF_8))
      {
         while (scanner.hasNextLine())
         {
            final String[] tokens = scanner.nextLine().trim().split("\\s+");
            if (tokens.length == 0 || tokens[0].isEmpty())
            {
               continue;
            }
            try
            {
               switch (tokens[0])
               {
                  case "device":
                     shell.deviceSelected(tokens.length > 1
                        ? String.join(" ", java.util.Arrays.copyOfRange(tokens, 1, tokens.length))
                        : "");
                     break;
                  case "cc":
                     shell.midiReceived(0xB0, Integer.parseInt(tokens[1]), Integer.parseInt(tokens[2]));
                     break;
                  case "value":
                     // name may contain spaces: "value Release Time 1.0"
                     shell.parameterValueChanged( //
                        String.join(" ", java.util.Arrays.copyOfRange(tokens, 1, tokens.length - 1)), //
                        Double.parseDouble(tokens[tokens.length - 1]));
                     break;
                  case "quit":
                     return;
                  default:
                     System.out.println("unknown command");
               }
            }
            catch (final Exception e)
            {
               System.out.println("bad command: " + e.getMessage());
            }
         }
      }
   }

   private static void hardware(final String[] args, final Path configDir) throws Exception
   {
      if (args.length < 2)
      {
         usage();
         System.exit(2);
      }
      final String hint = args[1].toLowerCase(Locale.ROOT);
      final MidiDevice in = findDevice(hint, true);
      final MidiDevice out = findDevice(hint, false);
      if (in == null || out == null)
      {
         System.err.println("no MIDI device matching '" + args[1] + "' (use --list-ports)");
         System.exit(1);
      }
      in.open();
      out.open();
      final Receiver outReceiver = out.getReceiver();

      final ConsoleCompactShell shell = new ConsoleCompactShell(configDir, new ShellOutput()
      {
         @Override
         public void sendMidi(final int status, final int d1, final int d2)
         {
            try
            {
               outReceiver.send(new ShortMessage(status, d1, d2), -1L);
            }
            catch (final InvalidMidiDataException e)
            {
               System.err.println("cannot send midi: " + e.getMessage());
            }
         }

         @Override
         public void setParameter(final String name, final double value)
         {
            // No Bitwig in standalone mode; just show what would be sent.
            System.out.println("[OUT param] (not connected to Bitwig) " + name + " -> "
               + String.format(Locale.ROOT, "%.4f", Double.valueOf(value)));
         }

         @Override
         public void log(final String message)
         {
            System.out.println("[shell    ] " + message);
         }
      });
      shell.setMonitor(new UdpStatusBroadcaster());
      if (!shell.start())
      {
         System.exit(1);
      }

      final Transmitter transmitter = in.getTransmitter();
      transmitter.setReceiver(new Receiver()
      {
         @Override
         public void send(final MidiMessage message, final long timeStamp)
         {
            if (message instanceof ShortMessage)
            {
               final ShortMessage shortMessage = (ShortMessage) message;
               System.out.println("[IN  midi ] status=0x" + Integer.toHexString(shortMessage.getStatus()) //
                  + " d1=" + shortMessage.getData1() + " d2=" + shortMessage.getData2());
               shell.midiReceived(shortMessage.getStatus(), shortMessage.getData1(), shortMessage.getData2());
            }
         }

         @Override
         public void close()
         {
         }
      });

      System.out.println("listening on '" + in.getDeviceInfo().getName() + "', sending to '" + out.getDeviceInfo().getName() + "'");
      System.out.println("raw MIDI is printed so you can discover your knobs' real CC numbers;");
      System.out.println("device selection only exists inside Bitwig, so knob displays stay dark here. Ctrl-C to quit.");
      Thread.currentThread().join();
   }

   private static MidiDevice findDevice(final String hint, final boolean wantInput) throws MidiUnavailableException
   {
      for (final Info info : MidiSystem.getMidiDeviceInfo())
      {
         if (!info.getName().toLowerCase(Locale.ROOT).contains(hint))
         {
            continue;
         }
         final MidiDevice device = MidiSystem.getMidiDevice(info);
         final boolean usable = wantInput ? device.getMaxTransmitters() != 0 : device.getMaxReceivers() != 0;
         if (usable)
         {
            return device;
         }
      }
      return null;
   }
}
