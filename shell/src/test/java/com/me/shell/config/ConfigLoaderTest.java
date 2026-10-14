package com.me.shell.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.me.shell.config.ParamBinding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigLoaderTest
{
   @TempDir
   Path configDir;

   private void write(final String name, final String... lines) throws Exception
   {
      final Path file = "console-compact.json".equals(name) //
         ? this.configDir.resolve(name) //
         : Files.createDirectories(this.configDir.resolve("devices")).resolve(name);
      Files.write(file, List.of(lines));
   }

   @Test
   void exactMatchWinsOverPrefixMatch() throws Exception
   {
      write("a.json", "{", "  \"match\": [\"De\"],", "  \"knobs\": {\"1\": \"FromPrefix\"}", "}");
      write("b.json", "{", "  \"match\": [\"Device B\"],", "  \"knobs\": {\"1\": \"FromExact\"}", "}");
      write("console-compact.json", "{", "  \"midiChannel\": 0,", "  \"knobs\": [{\"id\": 1, \"cc\": 71}]", "}");

      final ConfigLoader loader = new ConfigLoader(this.configDir);
      final Optional<DeviceMap> found = loader.findDeviceMap("Device B", m -> {});

      assertTrue(found.isPresent());
      assertEquals("FromExact", found.get().bindingForKnob(1).map(b -> b.name).orElse(null));
   }

   @Test
   void invalidFilesAreSkippedWithWarning() throws Exception
   {
      write("broken.json", "{ not valid json !!");
      write("good.json", "{", "  \"match\": [\"Good\"],", "  \"knobs\": {\"2\": \"Param\"}", "}");

      final ConfigLoader loader = new ConfigLoader(this.configDir);
      final List<String> warnings = new ArrayList<>();
      final Optional<DeviceMap> found = loader.findDeviceMap("Good", warnings::add);

      assertTrue(found.isPresent());
      assertEquals("Param", found.get().bindingForKnob(2).map(b -> b.name).orElse(null));
      assertEquals(1, warnings.size());
      assertTrue(warnings.get(0).contains("broken.json"));
   }

   @Test
   void containsMatchHandlesVendorPrefixedDeviceNames() throws Exception
   {
      write("prol2.json", "{", "  \"match\": [\"Pro-L 2\"],", "  \"knobs\": {\"1\": \"Threshold\"}", "}");
      final ConfigLoader loader = new ConfigLoader(this.configDir);
      final Optional<DeviceMap> found = loader.findDeviceMap("FabFilter Pro-L 2 (VST3)", m -> {});
      assertTrue(found.isPresent());
      assertEquals("Threshold", found.get().bindingForKnob(1).map(b -> b.name).orElse(null));
   }

   @Test
   void objectFormWithRangeParses() throws Exception
   {
      write("scaled.json", "{", //
         "  \"match\": [\"Scaled\"],", //
         "  \"knobs\": {\"2\": {\"param\": \"Link\", \"range\": [0.0, 0.5]}, \"3\": \"Plain\"}", //
         "}");
      final ConfigLoader loader = new ConfigLoader(this.configDir);
      final Optional<DeviceMap> found = loader.findDeviceMap("Scaled", m -> {});

      assertTrue(found.isPresent());
      final ParamBinding link = found.get().bindingForKnob(2).orElse(null);
      assertTrue(link != null);
      assertEquals("Link", link.name);
      assertEquals(0.0, link.min);
      assertEquals(0.5, link.max);
      assertEquals("Plain", found.get().bindingForKnob(3).map(b -> b.name).orElse(null));
   }

   @Test
   void invalidRangeFallsBackToFullRangeWithWarning() throws Exception
   {
      write("badrange.json", "{", //
         "  \"match\": [\"BadRange\"],", //
         "  \"knobs\": {\"1\": {\"param\": \"P\", \"range\": [0.0]}}", //
         "}");
      final ConfigLoader loader = new ConfigLoader(this.configDir);
      final List<String> warnings = new ArrayList<>();
      final Optional<DeviceMap> found = loader.findDeviceMap("BadRange", warnings::add);

      assertTrue(found.isPresent());
      final ParamBinding binding = found.get().bindingForKnob(1).orElse(null);
      assertTrue(binding != null);
      assertEquals(0.0, binding.min);
      assertEquals(1.0, binding.max);
      assertTrue(warnings.stream().anyMatch(w -> w.contains("invalid range")));
   }

   @Test
   void knobIdsAndCcsAreValidated() throws Exception
   {
      write("console-compact.json", "{", "  \"midiChannel\": 0,", "  \"knobs\": [{\"id\": 1, \"cc\": 200}]", "}");

      final ConfigLoader loader = new ConfigLoader(this.configDir);
      final List<String> warnings = new ArrayList<>();
      try
      {
         loader.loadHardware(warnings::add);
         assertTrue(false, "expected IOException for cc > 127");
      }
      catch (final Exception expected)
      {
         assertTrue(expected.getMessage().contains("cc must be 0..127"));
      }
   }
}
