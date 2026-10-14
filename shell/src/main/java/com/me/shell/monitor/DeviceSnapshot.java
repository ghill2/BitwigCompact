package com.me.shell.monitor;

import java.util.List;

/**
 * Snapshot of the shell state, serialized to the monitor window.
 * Field names are the JSON protocol - keep in sync with the monitor module.
 */
public final class DeviceSnapshot
{
   public String         device;
   public String         map;
   public List<KnobStatus> knobs;

   public DeviceSnapshot()
   {
   }

   public DeviceSnapshot(final String device, final String map, final List<KnobStatus> knobs)
   {
      this.device = device;
      this.map = map;
      this.knobs = knobs;
   }
}
