package com.me.shell.config;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Immutable view of the controller hardware: the knobs with their fixed CC numbers.
 */
public final class HardwareMap
{
   private final int                  channel;
   private final List<KnobConfig>     knobs;
   private final Map<Integer, Integer> ccToKnob = new TreeMap<>();
   private final Map<Integer, Integer> knobToCc = new TreeMap<>();

   public HardwareMap(final int channel, final List<KnobConfig> knobs)
   {
      this.channel = channel;
      this.knobs = List.copyOf(knobs);
      for (final KnobConfig knob : this.knobs)
      {
         this.ccToKnob.put(knob.cc, knob.id);
         this.knobToCc.put(knob.id, knob.cc);
      }
   }

   public int channel()
   {
      return this.channel;
   }

   public List<KnobConfig> knobs()
   {
      return this.knobs;
   }

   public Optional<Integer> knobForCc(final int cc)
   {
      return Optional.ofNullable(this.ccToKnob.get(cc));
   }

   public Optional<Integer> ccForKnob(final int knobId)
   {
      return Optional.ofNullable(this.knobToCc.get(knobId));
   }
}
