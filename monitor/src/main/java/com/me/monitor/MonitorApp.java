package com.me.monitor;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.WindowConstants;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Robot;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * The Console Compact monitor window: always on top, shows the 12 knobs with their mapped
 * parameter names, live values and drawn dial positions. Listens for the shell's UDP packets,
 * which are sent by both the Bitwig extension and the standalone simulator.
 *
 * Usage: java -jar console-compact-monitor.jar [port]   (default port 51234)
 */
public final class MonitorApp
{
   private static final Color BACKGROUND = new Color(0x141414);

   private final KnobCell[] cells = new KnobCell[12];
   private final JLabel    deviceLabel = new JLabel("waiting for shell…", JLabel.CENTER);
   private final JLabel    mapLabel    = new JLabel("", JLabel.CENTER);
   private final MonitorListener listener;

   private JFrame   frame;
   private File     screenshotFile;
   private boolean  captureScheduled;

   private MonitorApp(final int port) throws Exception
   {
      for (int i = 0; i < this.cells.length; i++)
      {
         this.cells[i] = new KnobCell(i + 1, i); // cc is a placeholder until a device snapshot arrives
      }

      this.listener = new MonitorListener(port, message -> SwingUtilities.invokeLater(() -> handle(message)));
   }

   private void show()
   {
      this.frame = new JFrame("Console Compact Monitor");
      this.frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
      this.frame.setAlwaysOnTop(true);
      this.frame.setBackground(BACKGROUND);

      this.deviceLabel.setFont(this.deviceLabel.getFont().deriveFont(Font.BOLD, 16f));
      this.deviceLabel.setForeground(new Color(0xE0E0E0));
      this.deviceLabel.setBorder(BorderFactory.createEmptyBorder(10, 10, 2, 10));
      this.mapLabel.setFont(this.mapLabel.getFont().deriveFont(Font.PLAIN, 11f));
      this.mapLabel.setForeground(new Color(0x777777));
      this.mapLabel.setBorder(BorderFactory.createEmptyBorder(0, 10, 8, 10));

      final JPanel header = new JPanel(new BorderLayout());
      header.setBackground(BACKGROUND);
      header.add(this.deviceLabel, BorderLayout.CENTER);
      header.add(this.mapLabel, BorderLayout.SOUTH);

      final JPanel grid = new JPanel(new GridLayout(3, 4, 6, 6));
      grid.setBackground(BACKGROUND);
      grid.setBorder(BorderFactory.createEmptyBorder(0, 6, 6, 6));
      for (final KnobCell cell : this.cells)
      {
         grid.add(cell);
      }

      this.frame.getContentPane().setLayout(new BorderLayout());
      this.frame.getContentPane().setBackground(BACKGROUND);
      this.frame.getContentPane().add(header, BorderLayout.NORTH);
      this.frame.getContentPane().add(grid, BorderLayout.CENTER);

      this.frame.pack();
      this.frame.setLocationByPlatform(true);

      this.frame.addWindowListener(new WindowAdapter()
      {
         @Override
         public void windowClosing(final WindowEvent e)
         {
            MonitorApp.this.listener.stop();
         }
      });

      try
      {
         this.listener.start();
      }
      catch (final Exception e)
      {
         this.deviceLabel.setText("cannot listen on port: " + e.getMessage());
      }

      this.frame.setVisible(true);
   }

   private void handle(final Protocol.Message message)
   {
      if ("device".equals(message.type))
      {
         this.deviceLabel.setText(message.device == null || message.device.isEmpty() ? "no device selected" : message.device);
         this.mapLabel.setText(message.map == null ? "" : message.map);
         if (message.knobs != null)
         {
            for (final Protocol.Knob knob : message.knobs)
            {
               if (knob.knobId >= 1 && knob.knobId <= this.cells.length)
               {
                  this.cells[knob.knobId - 1].updateBinding(knob);
               }
            }
         }
         if (this.screenshotFile != null)
         {
            captureScreenshotLater();
         }
      }
      else if ("knob".equals(message.type) && message.id >= 1 && message.id <= this.cells.length)
      {
         this.cells[message.id - 1].updatePosition(message.pos, message.value);
      }
   }

   /** Dev aid for the readme screenshot (--screenshot <file>): once the snapshot has rendered,
    *  capture the window via Robot and exit. */
   private void captureScreenshotLater()
   {
      if (this.captureScheduled)
      {
         return;
      }
      this.captureScheduled = true;
      final Timer timer = new Timer(1200, event -> {
         try
         {
            final BufferedImage image = new Robot().createScreenCapture(this.frame.getBounds());
            ImageIO.write(image, "png", this.screenshotFile);
            System.out.println("screenshot written: " + this.screenshotFile.getAbsolutePath());
         }
         catch (final Exception ex)
         {
            System.err.println("screenshot failed: " + ex.getMessage());
         }
         System.exit(0);
      });
      timer.setRepeats(false);
      timer.start();
   }

   public static void main(final String[] args)
   {
      int port = 51234;
      File screenshot = null;
      for (int i = 0; i < args.length; i++)
      {
         if ("--screenshot".equals(args[i]) && i + 1 < args.length)
         {
            screenshot = new File(args[++i]);
         }
         else
         {
            port = parsePort(args[i]);
         }
      }
      final int resolvedPort = port;
      final File shot = screenshot;
      SwingUtilities.invokeLater(() -> {
         try
         {
            final MonitorApp app = new MonitorApp(resolvedPort);
            app.screenshotFile = shot;
            app.show();
         }
         catch (final Exception e)
         {
            System.err.println("failed to start monitor: " + e.getMessage());
            System.exit(1);
         }
      });
   }

   private static int parsePort(final String arg)
   {
      try
      {
         return Integer.parseInt(arg.trim());
      }
      catch (final NumberFormatException e)
      {
         System.err.println("invalid port '" + arg + "', using 51234");
         return 51234;
      }
   }
}
