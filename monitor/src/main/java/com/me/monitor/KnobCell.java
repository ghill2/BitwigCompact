package com.me.monitor;

import javax.swing.JPanel;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.util.Locale;

/**
 * One knob cell: a drawn rotary dial showing the knob position, the parameter name,
 * knob number + CC, range tag and the live value.
 */
final class KnobCell extends JPanel
{
   /** Java arc angles: 0 = 3 o'clock, clockwise. Dial starts at 7:30 (135 deg). */
   private static final double START_ANGLE = 135.0;
   private static final double SWEEP       = 270.0;

   private static final Color BACKGROUND   = new Color(0x1E1E1E);
   private static final Color CELL_BORDER  = new Color(0x3A3A3A);
   private static final Color TRACK        = new Color(0x4A4A4A);
   private static final Color FILLED       = new Color(0x4EC9B0);
   private static final Color KNOB_BODY    = new Color(0x2D2D30);
   private static final Color TEXT         = new Color(0xE0E0E0);
   private static final Color TEXT_DIM     = new Color(0x777777);
   private static final Color ACCENT       = new Color(0x4EC9B0);

   private final int    knobId;
   private final int    cc;
   private       String param;
   private       double min;
   private       double max       = 1.0;
   private       boolean mapped;
   private       double position;  // 0..1 knob position
   private       double paramValue = -1.0;

   KnobCell(final int knobId, final int cc)
   {
      this.knobId = knobId;
      this.cc = cc;
      setPreferredSize(new Dimension(150, 160));
      setBackground(BACKGROUND);
   }

   /** Full binding update from a device snapshot. */
   void updateBinding(final Protocol.Knob status)
   {
      this.param = status.param;
      this.min = status.min;
      this.max = status.max;
      this.mapped = status.mapped;
      this.paramValue = status.value >= 0 ? status.value : -1.0;
      this.position = this.paramValue >= 0 && this.max != this.min
         ? clamp((this.paramValue - this.min) / (this.max - this.min))
         : 0.0;
      repaint();
   }

   /** Live update from a knob turn or Bitwig-side value change. */
   void updatePosition(final double position, final double paramValue)
   {
      this.position = clamp(position);
      this.paramValue = paramValue;
      repaint();
   }

   boolean isMapped()
   {
      return this.mapped;
   }

   private static double clamp(final double v)
   {
      return Math.max(0.0, Math.min(1.0, v));
   }

   @Override
   protected void paintComponent(final Graphics g)
   {
      super.paintComponent(g);
      final Graphics2D g2 = (Graphics2D) g.create();
      try
      {
         g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

         final int w = getWidth();
         final int h = getHeight();

         // cell panel
         g2.setColor(BACKGROUND);
         g2.fillRoundRect(0, 0, w, h, 12, 12);
         g2.setColor(CELL_BORDER);
         g2.drawRoundRect(0, 0, w - 1, h - 1, 12, 12);

         // knob id + cc, small, top
         g2.setColor(TEXT_DIM);
         g2.setFont(g2.getFont().deriveFont(Font.PLAIN, 11f));
         g2.drawString(String.valueOf(this.knobId), 8, 18);
         final String ccText = "CC" + this.cc;
         final FontMetrics smallMetrics = g2.getFontMetrics();
         g2.drawString(ccText, w - 8 - smallMetrics.stringWidth(ccText), 18);

         // dial geometry
         final double radius = 34;
         final double cx = w / 2.0;
         final double cy = 58;
         final double outer = radius + 4;

         if (this.mapped)
         {
            // track
            g2.setColor(TRACK);
            g2.draw(new Arc2D.Double(cx - outer, cy - outer, outer * 2, outer * 2, START_ANGLE, SWEEP, Arc2D.OPEN));
            // value arc
            g2.setColor(ACCENT);
            g2.setStroke(new java.awt.BasicStroke(3f));
            g2.draw(new Arc2D.Double(cx - outer, cy - outer, outer * 2, outer * 2, START_ANGLE, SWEEP * this.position,
               Arc2D.OPEN));
            g2.setStroke(new java.awt.BasicStroke(1f));
            // knob body + indicator
            g2.setColor(KNOB_BODY);
            g2.fill(new Ellipse2D.Double(cx - radius + 6, cy - radius + 6, (radius - 6) * 2, (radius - 6) * 2));
            final double angle = Math.toRadians(START_ANGLE + SWEEP * this.position);
            final double rInner = radius - 10;
            final double rOuter = radius - 4;
            g2.setColor(ACCENT);
            g2.draw(new Line2D.Double(cx + Math.cos(angle) * rInner, cy + Math.sin(angle) * rInner, //
               cx + Math.cos(angle) * rOuter, cy + Math.sin(angle) * rOuter));
         }
         else
         {
            g2.setColor(new Color(0x333333));
            g2.draw(new Arc2D.Double(cx - outer, cy - outer, outer * 2, outer * 2, START_ANGLE, SWEEP, Arc2D.OPEN));
            g2.setColor(new Color(0x2A2A2A));
            g2.fill(new Ellipse2D.Double(cx - radius + 6, cy - radius + 6, (radius - 6) * 2, (radius - 6) * 2));
         }

         // parameter name (truncate to fit)
         g2.setColor(this.mapped ? TEXT : TEXT_DIM);
         g2.setFont(g2.getFont().deriveFont(Font.BOLD, 12f));
         drawCentered(g2, this.mapped && this.param != null ? this.param : "—", w / 2, 118, w - 12);

         // range tag when scaled
         if (this.mapped && (this.min != 0.0 || this.max != 1.0))
         {
            g2.setColor(TEXT_DIM);
            g2.setFont(g2.getFont().deriveFont(Font.PLAIN, 10f));
            drawCentered(g2, String.format(Locale.ROOT, "[%.2f..%.2f]", Double.valueOf(this.min), Double.valueOf(this.max)),
               w / 2, 133, w - 12);
         }

         // value readout
         g2.setColor(this.mapped ? ACCENT : TEXT_DIM);
         g2.setFont(g2.getFont().deriveFont(Font.BOLD, 13f));
         final String valueText = this.mapped && this.paramValue >= 0
            ? String.format(Locale.ROOT, "%.2f  (%d%%)", Double.valueOf(this.paramValue),
               Integer.valueOf((int) Math.round(this.position * 100)))
            : "—";
         drawCentered(g2, valueText, w / 2, this.mapped && (this.min != 0.0 || this.max != 1.0) ? 150 : 140, w - 12);
      }
      finally
      {
         g2.dispose();
      }
   }

   private static void drawCentered(final Graphics2D g2, final String text, final int cx, final int y, final int maxWidth)
   {
      FontMetrics metrics = g2.getFontMetrics();
      String drawn = text;
      while (metrics.stringWidth(drawn) > maxWidth && drawn.length() > 3)
      {
         drawn = drawn.substring(0, drawn.length() - 2) + "…";
      }
      g2.drawString(drawn, cx - metrics.stringWidth(drawn) / 2, y);
   }
}
