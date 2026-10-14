package com.me.shell.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Immutable resolved mapping for one device: knob id -> parameter binding (name + value range).
 */
public final class DeviceMap
{
   private final String                       source;
   private final List<String>                 match;
   private final Map<Integer, ParamBinding>   knobToBinding;
   private final Map<String, List<Integer>>   paramToKnobs;

   public DeviceMap(final String source, final List<String> match, final Map<String, ParamBinding> knobs)
   {
      this.source = source;
      this.match = List.copyOf(match);
      final Map<Integer, ParamBinding> forward = new TreeMap<>();
      final Map<String, List<Integer>> reverse = new HashMap<>();
      for (final Map.Entry<String, ParamBinding> entry : knobs.entrySet())
      {
         final Integer knobId = parseKnobId(entry.getKey());
         final ParamBinding binding = entry.getValue();
         if (knobId == null || binding == null || binding.name == null || binding.name.isBlank())
         {
            continue;
         }
         forward.put(knobId, binding);
         reverse.computeIfAbsent(binding.name, k -> new ArrayList<>()).add(knobId);
      }
      this.knobToBinding = Collections.unmodifiableMap(forward);
      final Map<String, List<Integer>> frozen = new HashMap<>();
      reverse.forEach((name, ids) -> frozen.put(name, List.copyOf(ids)));
      this.paramToKnobs = Collections.unmodifiableMap(frozen);
   }

   private static Integer parseKnobId(final String key)
   {
      try
      {
         return Integer.valueOf(key.trim());
      }
      catch (final NumberFormatException e)
      {
         return null;
      }
   }

   /** Name of the file this mapping was loaded from, for logging. */
   public String source()
   {
      return this.source;
   }

   /** Ordered knob id -> binding view, for logging. */
   public Map<Integer, ParamBinding> knobBindings()
   {
      return this.knobToBinding;
   }

   public Optional<ParamBinding> bindingForKnob(final int knobId)
   {
      return Optional.ofNullable(this.knobToBinding.get(knobId));
   }

   public List<Integer> knobsForParameter(final String parameterName)
   {
      final List<Integer> knobs = this.paramToKnobs.get(parameterName);
      return knobs == null ? List.of() : knobs;
   }

   public boolean matchesExactly(final String deviceName)
   {
      final String target = normalize(deviceName);
      for (final String candidate : this.match)
      {
         if (normalize(candidate).equals(target))
         {
            return true;
         }
      }
      return false;
   }

   public boolean matchesByPrefix(final String deviceName)
   {
      final String target = normalize(deviceName);
      for (final String candidate : this.match)
      {
         final String normalized = normalize(candidate);
         if (!normalized.isEmpty() && target.startsWith(normalized))
         {
            return true;
         }
      }
      return false;
   }

   /** Loosest rule: the match entry appears anywhere in the device name (e.g. "Pro-L 2" in "FabFilter Pro-L 2"). */
   public boolean matchesByNameContains(final String deviceName)
   {
      final String target = normalize(deviceName);
      for (final String candidate : this.match)
      {
         final String normalized = normalize(candidate);
         if (!normalized.isEmpty() && target.contains(normalized))
         {
            return true;
         }
      }
      return false;
   }

   private static String normalize(final String s)
   {
      return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
   }
}
