package com.me.shell.monitor;

import com.google.gson.Gson;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Sends shell state as JSON datagrams to the monitor window on localhost.
 * Fire and forget: when nothing listens, packets are dropped silently.
 *
 * Port: system property "consolecompact.monitor.port" (default 51234; 0 disables broadcasting).
 */
public final class UdpStatusBroadcaster implements ShellMonitor
{
   public static final int DEFAULT_PORT = 51234;

   private static final Gson GSON = new Gson();

   private final DatagramSocket socket;

   public UdpStatusBroadcaster()
   {
      this(Integer.parseInt(System.getProperty("consolecompact.monitor.port", String.valueOf(DEFAULT_PORT))));
   }

   public UdpStatusBroadcaster(final int port)
   {
      DatagramSocket created = null;
      if (port > 0 && port <= 65535)
      {
         try
         {
            created = new DatagramSocket();
            created.connect(InetAddress.getLoopbackAddress(), port);
         }
         catch (final IOException e)
         {
            if (created != null)
            {
               created.close();
            }
            created = null;
         }
      }
      this.socket = created;
   }

   @Override
   public void deviceChanged(final DeviceSnapshot snapshot)
   {
      final Map<String, Object> message = new LinkedHashMap<>();
      message.put("type", "device");
      message.put("device", snapshot.device);
      message.put("map", snapshot.map);
      message.put("knobs", snapshot.knobs);
      send(GSON.toJson(message));
   }

   @Override
   public void knobUpdated(final int knobId, final double knobPosition, final double paramValue)
   {
      final Map<String, Object> message = new LinkedHashMap<>();
      message.put("type", "knob");
      message.put("id", Integer.valueOf(knobId));
      message.put("pos", Double.valueOf(knobPosition));
      message.put("value", Double.valueOf(paramValue));
      send(GSON.toJson(message));
   }

   private void send(final String json)
   {
      if (this.socket == null)
      {
         return;
      }
      try
      {
         final byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
         this.socket.send(new DatagramPacket(bytes, bytes.length));
      }
      catch (final IOException ignored)
      {
         // fire and forget
      }
   }

   @Override
   public void close()
   {
      if (this.socket != null)
      {
         this.socket.close();
      }
   }
}
