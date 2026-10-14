package com.me.shell.config;

import com.google.gson.JsonElement;

import java.util.List;
import java.util.Map;

/**
 * POJO for a per-device mapping file in devices/*.json, populated by Gson.
 */
public final class DeviceConfig
{
   /**
    * Device names this config applies to. Case-insensitive; exact matches win over prefix matches
    * (a "match" entry may be a prefix of the reported device name).
    */
   public List<String>            match;

   /**
    * Knob id (as string) -> binding. A value is either a plain parameter-name string, or an
    * object: {"param": "Name", "range": [value at CC 0, value at CC 127]}.
    */
   public Map<String, JsonElement> knobs;
}
