package com.me.shell.config;

import java.util.List;

/**
 * POJO for console-compact.json, populated by Gson. Unknown keys (like "_comment") are ignored.
 */
public final class HardwareConfig
{
   /** MIDI channel, 0-based (0 = channel 1). */
   public Integer       midiChannel;

   /** The knobs with their fixed CC numbers. */
   public List<KnobConfig> knobs;
}
