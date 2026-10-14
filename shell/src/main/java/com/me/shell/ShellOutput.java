package com.me.shell;

/**
 * Events flowing OUT of the shell, toward the controller hardware and the host.
 */
public interface ShellOutput
{
   /**
    * Send a short MIDI message to the controller, e.g. a CC that updates a knob position display.
    */
   void sendMidi(int status, int data1, int data2);

   /**
    * Set a parameter of the selected device, by name, to a normalized value in [0,1].
    */
   void setParameter(String parameterName, double normalizedValue);

   /**
    * Diagnostic logging.
    */
   void log(String message);
}
