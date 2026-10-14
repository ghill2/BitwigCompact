package com.me.shell.config;

/**
 * A knob's parameter binding: the parameter name plus the normalized value range the knob covers.
 *
 * range = [value at CC 0, value at CC 127]; [0, 1] is the default (full parameter range).
 * A sub-range like [0, 0.5] spreads the knob's full travel over the first half of the parameter;
 * a reversed range like [1, 0] inverts the knob.
 */
public final class ParamBinding
{
   public final String name;
   public final double min;
   public final double max;

   public ParamBinding(final String name, final double min, final double max)
   {
      this.name = name;
      this.min = min;
      this.max = max;
   }

   /** Is this the default full range? Used for logging. */
   public boolean isFullRange()
   {
      return this.min == 0.0 && this.max == 1.0;
   }

   /** CC value (0..127) to parameter value, linear across the range. */
   public double ccToValue(final int cc)
   {
      return this.min + cc / 127.0 * (this.max - this.min);
   }

   /** Parameter value to CC value (0..127), clamped when the value is outside the mapped range. */
   public int valueToCc(final double value)
   {
      if (this.max == this.min)
      {
         return 64;
      }
      final double cc = (value - this.min) / (this.max - this.min) * 127.0;
      return Math.max(0, Math.min(127, (int) Math.round(cc)));
   }

   @Override
   public String toString()
   {
      if (isFullRange())
      {
         return this.name;
      }
      return this.name + "[" + this.min + ".." + this.max + "]";
   }
}
