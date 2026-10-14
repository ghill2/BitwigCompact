package com.me.shell.monitor;

/**
 * Observer of shell state for external visualisation (the monitor window).
 * Called from shell callbacks; implementations should do their work quickly
 * (e.g. fire a UDP packet) and never block.
 */
public interface ShellMonitor
{
   /**
    * Full state snapshot after a device selection. An empty device name means
    * no device is selected.
    */
   void deviceChanged(DeviceSnapshot snapshot);

   /**
    * A knob's position or value changed, from the hardware or from Bitwig.
    *
    * @param knobId the knob (1..12)
    * @param knobPosition the knob's physical position, 0..1 (matches its display/LED)
    * @param paramValue the mapped parameter value after range scaling, 0..1
    */
   void knobUpdated(int knobId, double knobPosition, double paramValue);

   /** Release resources. */
   default void close()
   {
   }
}
