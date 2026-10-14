package com.me.shell.config;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Loads the hardware definition (console-compact.json) and the per-device mappings (devices/*.json).
 *
 * Layout of a config directory:
 *   console-compact.json     the hardware: MIDI channel + knobs with fixed CCs
 *   devices/&lt;name&gt;.json  one file per device: knob id -> parameter name
 */
public final class ConfigLoader
{
   private static final Gson GSON = new Gson();

   private final Path configDir;
   private final Path devicesDir;

   public ConfigLoader(final Path configDir)
   {
      this.configDir = configDir;
      this.devicesDir = configDir.resolve("devices");
   }

   public Path configDir()
   {
      return this.configDir;
   }

   public HardwareMap loadHardware(final Consumer<String> warnings) throws IOException
   {
      final Path file = this.configDir.resolve("console-compact.json");
      if (!Files.isRegularFile(file))
      {
         throw new IOException("missing " + file);
      }
      final HardwareConfig config;
      try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8))
      {
         config = GSON.fromJson(reader, HardwareConfig.class);
      }
      catch (final JsonParseException e)
      {
         throw new IOException("invalid JSON in " + file + ": " + e.getMessage(), e);
      }
      if (config == null || config.knobs == null || config.knobs.isEmpty())
      {
         throw new IOException("no knobs defined in " + file);
      }
      final int channel = config.midiChannel == null ? 0 : config.midiChannel;
      if (channel < 0 || channel > 15)
      {
         throw new IOException("midiChannel must be 0..15 in " + file);
      }
      final List<KnobConfig> knobs = new ArrayList<>(config.knobs);
      knobs.sort(Comparator.comparing(k -> k.id == null ? Integer.MAX_VALUE : k.id));
      for (final KnobConfig knob : knobs)
      {
         if (knob.id == null || knob.cc == null)
         {
            throw new IOException("knob entries need numeric 'id' and 'cc' in " + file);
         }
         if (knob.cc < 0 || knob.cc > 127)
         {
            throw new IOException("cc must be 0..127 in " + file);
         }
      }
      return new HardwareMap(channel, knobs);
   }

   /**
    * Find the mapping for a device name. Exact (case-insensitive) matches win over prefix matches;
    * among equals, files are considered in sorted filename order.
    *
    * @param warnings sink for non-fatal problems (invalid files are skipped)
    */
   public Optional<DeviceMap> findDeviceMap(final String deviceName, final Consumer<String> warnings) throws IOException
   {
      if (deviceName == null || deviceName.isBlank() || !Files.isDirectory(this.devicesDir))
      {
         return Optional.empty();
      }
      final List<Path> files;
      try (Stream<Path> stream = Files.list(this.devicesDir))
      {
         files = stream //
            .filter(f -> f.getFileName().toString().toLowerCase().endsWith(".json")) //
            .sorted(Comparator.comparing(p -> p.getFileName().toString())) //
            .collect(Collectors.toList());
      }
      final List<DeviceMap> candidates = new ArrayList<>();
      for (final Path file : files)
      {
         readDeviceMap(file, warnings).ifPresent(candidates::add);
      }
      for (final DeviceMap candidate : candidates)
      {
         if (candidate.matchesExactly(deviceName))
         {
            return Optional.of(candidate);
         }
      }
      for (final DeviceMap candidate : candidates)
      {
         if (candidate.matchesByPrefix(deviceName))
         {
            return Optional.of(candidate);
         }
      }
      // Loosest rule last: name contained anywhere (handles "FabFilter Pro-L 2" vs config "Pro-L 2").
      for (final DeviceMap candidate : candidates)
      {
         if (candidate.matchesByNameContains(deviceName))
         {
            return Optional.of(candidate);
         }
      }
      return Optional.empty();
   }

   /** Filenames in devices/, for the "no mapping found" log line. */
   public List<String> availableConfigNames()
   {
      if (!Files.isDirectory(this.devicesDir))
      {
         return List.of();
      }
      try (Stream<Path> stream = Files.list(this.devicesDir))
      {
         return stream //
            .map(p -> p.getFileName().toString()) //
            .sorted() //
            .collect(Collectors.toList());
      }
      catch (final IOException e)
      {
         return List.of();
      }
   }

   private Optional<DeviceMap> readDeviceMap(final Path file, final Consumer<String> warnings)
   {
      try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8))
      {
         final DeviceConfig config = GSON.fromJson(reader, DeviceConfig.class);
         if (config == null || config.match == null || config.match.isEmpty() || config.knobs == null || config.knobs.isEmpty())
         {
            warnings.accept("ignoring " + file.getFileName() + ": missing or empty 'match'/'knobs'");
            return Optional.empty();
         }
         final Map<String, ParamBinding> bindings = parseKnobs(file.getFileName().toString(), config.knobs, warnings);
         if (bindings.isEmpty())
         {
            warnings.accept("ignoring " + file.getFileName() + ": no valid knob bindings");
            return Optional.empty();
         }
         return Optional.of(new DeviceMap(file.getFileName().toString(), config.match, bindings));
      }
      catch (final IOException | JsonParseException e)
      {
         warnings.accept("ignoring " + file.getFileName() + ": " + e.getMessage());
         return Optional.empty();
      }
   }

   /**
    * Accepts knob values as either a plain parameter-name string or an object
    * {"param": name, "range": [at CC 0, at CC 127]}. Malformed entries warn and are skipped.
    */
   private static Map<String, ParamBinding> parseKnobs(final String file, final Map<String, JsonElement> knobs,
      final Consumer<String> warnings)
   {
      final Map<String, ParamBinding> result = new HashMap<>();
      for (final Map.Entry<String, JsonElement> entry : knobs.entrySet())
      {
         final JsonElement element = entry.getValue();
         if (element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isString())
         {
            result.put(entry.getKey(), new ParamBinding(element.getAsString(), 0.0, 1.0));
            continue;
         }
         if (element == null || !element.isJsonObject())
         {
            warnings.accept(file + ": knob " + entry.getKey() + " must be a name string or a {param, range} object - skipped");
            continue;
         }
         final JsonObject object = element.getAsJsonObject();
         final JsonElement param = object.get("param");
         if (param == null || !param.isJsonPrimitive() || !param.getAsJsonPrimitive().isString())
         {
            warnings.accept(file + ": knob " + entry.getKey() + " object needs a 'param' string - skipped");
            continue;
         }
         double min = 0.0;
         double max = 1.0;
         final JsonElement range = object.get("range");
         if (range != null)
         {
            boolean valid = range.isJsonArray() && range.getAsJsonArray().size() == 2;
            if (valid)
            {
               min = range.getAsJsonArray().get(0).getAsDouble();
               max = range.getAsJsonArray().get(1).getAsDouble();
               valid = !Double.isNaN(min) && !Double.isNaN(max) && min != max;
            }
            if (!valid)
            {
               warnings.accept(file + ": knob " + entry.getKey() + " has an invalid range - using full range [0, 1]");
               min = 0.0;
               max = 1.0;
            }
         }
         result.put(entry.getKey(), new ParamBinding(param.getAsString(), min, max));
      }
      return result;
   }
}
