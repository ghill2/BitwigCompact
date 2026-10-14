package com.me.shell.config;

/**
 * One knob of the controller with its fixed (never changing) CC number.
 */
public final class KnobConfig
{
   /** Knob id, 1..12 for the Console Compact. */
   public Integer id;

   /** Fixed CC number the knob sends/receives, 0..127. */
   public Integer cc;

   /** Optional human-readable label. */
   public String  label;
}
