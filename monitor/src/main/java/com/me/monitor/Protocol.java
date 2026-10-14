package com.me.monitor;

import java.util.List;

/**
 * JSON messages sent by the shell's UdpStatusBroadcaster.
 * Field names must stay in sync with com.me.shell.monitor.
 */
final class Protocol
{
   private Protocol()
   {
   }

   static final class Message
   {
      String          type;
      String          device;
      String          map;
      List<Knob>      knobs;
      int             id;
      double          pos;
      double          value;
   }

   @SuppressWarnings("unused")
   static final class Knob
   {
      int     knobId;
      int     cc;
      String  param;
      double  min;
      double  max;
      boolean mapped;
      double  value;
   }
}
