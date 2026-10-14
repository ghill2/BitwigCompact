package com.me.bitwig;

import java.util.UUID;

import com.bitwig.extension.api.PlatformType;
import com.bitwig.extension.controller.AutoDetectionMidiPortNamesList;
import com.bitwig.extension.controller.ControllerExtensionDefinition;
import com.bitwig.extension.controller.api.ControllerHost;

public final class ConsoleCompactDefinition extends ControllerExtensionDefinition
{
   /**
    * Same id as the previous "ControllerBitwig" extension, so existing Bitwig controller
    * preferences keep working after the rewrite.
    */
   private static final UUID DRIVER_ID = UUID.fromString("93d7b73a-22de-4503-9ec4-2057f8765431");

   public ConsoleCompactDefinition()
   {
   }

   @Override
   public String getName()
   {
      return "BitwigCompact";
   }

   @Override
   public String getAuthor()
   {
      return "fhill2";
   }

   @Override
   public String getVersion()
   {
      return "0.2";
   }

   @Override
   public UUID getId()
   {
      return DRIVER_ID;
   }

   @Override
   public String getHardwareVendor()
   {
      return "George Hill";
   }

   @Override
   public String getHardwareModel()
   {
      return "Console Compact";
   }

   @Override
   public int getRequiredAPIVersion()
   {
      return 25;
   }

   @Override
   public int getNumMidiInPorts()
   {
      return 1;
   }

   @Override
   public int getNumMidiOutPorts()
   {
      return 1;
   }

   @Override
   public void listAutoDetectionMidiPortNames(final AutoDetectionMidiPortNamesList list, final PlatformType platformType)
   {
      // TODO: once the exact port names of the Console Compact are known, enable auto detection, e.g.
      // list.add(new String[] { "Console Compact MIDI 1" }, new String[] { "Console Compact MIDI 1" });
      // Until then the ports are selected manually in Bitwig's controller preferences.
   }

   @Override
   public ConsoleCompactExtension createInstance(final ControllerHost host)
   {
      return new ConsoleCompactExtension(this, host);
   }
}
