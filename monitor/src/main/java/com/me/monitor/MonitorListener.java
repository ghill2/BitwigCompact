package com.me.monitor;

import com.google.gson.Gson;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.charset.StandardCharsets;

/**
 * UDP listener for shell state packets. Runs on a daemon thread; callbacks arrive off the EDT,
 * implementations must marshal to the EDT themselves.
 */
final class MonitorListener
{
   interface Callback
   {
      void onMessage(Protocol.Message message);
   }

   private static final int MAX_PACKET = 65536;

   private final Gson     gson     = new Gson();
   private final int      port;
   private final Callback callback;

   private DatagramSocket socket;
   private Thread         thread;

   MonitorListener(final int port, final Callback callback)
   {
      this.port = port;
      this.callback = callback;
   }

   void start() throws Exception
   {
      this.socket = new DatagramSocket(this.port);
      this.thread = new Thread(this::run, "monitor-listener");
      this.thread.setDaemon(true);
      this.thread.start();
   }

   void stop()
   {
      if (this.socket != null && !this.socket.isClosed())
      {
         this.socket.close();
      }
   }

   private void run()
   {
      final byte[] buffer = new byte[MAX_PACKET];
      while (this.socket != null && !this.socket.isClosed())
      {
         final DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
         try
         {
            this.socket.receive(packet);
            final String json = new String(buffer, 0, packet.getLength(), StandardCharsets.UTF_8);
            final Protocol.Message message = this.gson.fromJson(json, Protocol.Message.class);
            if (message != null && message.type != null)
            {
               this.callback.onMessage(message);
            }
         }
         catch (final Exception e)
         {
            if (this.socket == null || this.socket.isClosed())
            {
               return;
            }
            // transient receive error: keep listening
         }
      }
   }
}
