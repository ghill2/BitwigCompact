package com.me.shell.monitor;

/**
 * One knob's binding state, serialized to the monitor window.
 */
public final class KnobStatus
{
   /** Knob id, 1..12. */
   public int    knobId;

   /** The knob's fixed CC number. */
   public int    cc;

   /** Parameter name, or null when the knob is unmapped on the current device. */
   public String param;

   /** Mapped parameter range: value at CC 0. */
   public double min;

   /** Mapped parameter range: value at CC 127. */
   public double max;

   /** Whether the knob has a parameter on the current device. */
   public boolean mapped;

   /** Last known parameter value, or -1 when unknown. */
   public double value;

   public KnobStatus()
   {
   }

   public KnobStatus(final int knobId, final int cc, final String param, final double min, final double max,
      final boolean mapped, final double value)
   {
      this.knobId = knobId;
      this.cc = cc;
      this.param = param;
      this.min = min;
      this.max = max;
      this.mapped = mapped;
      this.value = value;
   }
}
