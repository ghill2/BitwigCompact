package com.me.shell;

/**
 * Events flowing INTO the shell, from the host world (the Bitwig extension) or a test harness.
 */
public interface ShellInput
{
   /**
    * The selected device changed. Blank or null means no device is selected.
    *
    * @param deviceName name of the device as Bitwig reports it
    */
   void deviceSelected(String deviceName);

   /**
    * A parameter of the selected device reported a new value.
    *
    * @param parameterName parameter name as Bitwig displays it
    * @param normalizedValue value in [0,1]
    */
   void parameterValueChanged(String parameterName, double normalizedValue);

   /**
    * Short MIDI message received from the controller hardware.
    *
    * @param status MIDI status byte
    * @param data1 first data byte (CC number for CC messages)
    * @param data2 second data byte (CC value for CC messages)
    */
   void midiReceived(int status, int data1, int data2);
}
