/**
 * This file is part of Logisim-evolution.
 *
 * Logisim-evolution is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 *
 * Logisim-evolution is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with Logisim-evolution.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Original code by Carl Burch (http://www.cburch.com), 2011.
 * Subsequent modifications by:
 *   + Haute École Spécialisée Bernoise
 *     http://www.bfh.ch
 *   + Haute École du paysage, d'ingénierie et d'architecture de Genève
 *     http://hepia.hesge.ch/
 *   + Haute École d'Ingénierie et de Gestion du Canton de Vaud
 *     http://www.heig-vd.ch/
 *   + REDS Institute - HEIG-VD, Yverdon-les-Bains, Switzerland
 *     http://reds.heig-vd.ch
 * This version of the project is currently maintained by:
 *   + Kevin Walsh (kwalsh@holycross.edu, http://mathcs.holycross.edu/~kwalsh)
 */

package com.cburch.logisim.gui.start;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;

import com.cburch.logisim.Main;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.util.GraphicsUtil;
import com.cburch.logisim.util.UniquelyNamedThread;

public class About {
  private static class MyPanel extends JPanel implements AncestorListener {
    private static int toDim(int offs) {
      return offs * 3 / 2;
    }

    private static int toX(int x0, int offs) {
      return x0 + offs * 3 / 2;
    }

    private static int toY(int y0, int offs) {
      return y0 + offs * 3 / 2;
    }

    private static final long serialVersionUID = 1L;
    private final Color fadeColor = new Color(255, 255, 255, 128);
    private final Color headerColor = new Color(143, 0, 0);
    private final Color gateColor = Color.DARK_GRAY;

    private final Font headerFont = new Font("Monospaced", Font.BOLD, 34);
    private final Font versionFont = new Font("Serif", Font.PLAIN
        | Font.ITALIC, 20);
    private final Font copyrightFont = new Font("Serif", Font.ITALIC, 12);
    private boolean upper = false;
    private boolean lower = true;
    private AboutCredits credits;

    private PanelThread thread = null;

    public MyPanel() {
      setLayout(null);

      setPreferredSize(new Dimension(ABOUT_WIDTH, ABOUT_HEIGHT));
      setBackground(Color.WHITE);
      addAncestorListener(this);

      credits = new AboutCredits();
      credits.setBounds(0, ABOUT_HEIGHT / 2, ABOUT_WIDTH, ABOUT_HEIGHT / 2);
      add(credits);
    }

    public void ancestorAdded(AncestorEvent arg0) {
      if (thread == null) {
        thread = new PanelThread(this);
        thread.start();
      }
    }

    public void ancestorMoved(AncestorEvent arg0) {
    }

    public void ancestorRemoved(AncestorEvent arg0) {
      if (thread != null) {
        thread.running = false;
      }
    }

    private void drawAnd(Graphics g, int x0, int y0, int x, int y) {
      int[] xp = new int[4];
      int[] yp = new int[4];
      xp[0] = toX(x0, x - 25);
      yp[0] = toY(y0, y - 25);
      xp[1] = toX(x0, x - 50);
      yp[1] = yp[0];
      xp[2] = xp[1];
      yp[2] = toY(y0, y + 25);
      xp[3] = xp[0];
      yp[3] = yp[2];
      int diam = toDim(50);
      g.drawArc(xp[1], yp[1], diam, diam, -90, 180);
      g.drawPolyline(xp, yp, 4);
    }

    private void drawCircuit(Graphics g, int x0, int y0) {
      Graphics2D g2 = (Graphics2D) g;
      g2.setStroke(new BasicStroke(5.0f));
      drawWires(g, x0, y0);
      g.setColor(gateColor);
      drawNot(g, x0, y0, 70, 10);
      drawNot(g, x0, y0, 70, 110);
      drawAnd(g, x0, y0, 130, 30);
      drawAnd(g, x0, y0, 130, 90);
      drawOr(g, x0, y0, 220, 60);
    }

    private void drawNot(Graphics g, int x0, int y0, int x, int y) {
      int[] xp = new int[4];
      int[] yp = new int[4];
      xp[0] = toX(x0, x - 10);
      yp[0] = toY(y0, y);
      xp[1] = toX(x0, x - 29);
      yp[1] = toY(y0, y - 7);
      xp[2] = xp[1];
      yp[2] = toY(y0, y + 7);
      xp[3] = xp[0];
      yp[3] = yp[0];
      g.drawPolyline(xp, yp, 4);
      int diam = toDim(10);
      g.drawOval(xp[0], yp[0] - diam / 2, diam, diam);
    }

    private void drawOr(Graphics g, int x0, int y0, int x, int y) {
      int cx = toX(x0, x - 50);
      int cd = toDim(62);
      GraphicsUtil.drawCenteredArc(g, cx, toY(y0, y - 37), cd, -90, 53);
      GraphicsUtil.drawCenteredArc(g, cx, toY(y0, y + 37), cd, 90, -53);
      GraphicsUtil.drawCenteredArc(g, toX(x0, x - 93), toY(y0, y),
          toDim(50), -30, 60);
    }

    private void drawText(Graphics g, int x, int y) {
      Graphics2D g2 = (Graphics2D)g;
      g2.setRenderingHint(
          RenderingHints.KEY_TEXT_ANTIALIASING,
          RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
      g2.setRenderingHint(
          RenderingHints.KEY_ANTIALIASING,
          RenderingHints.VALUE_ANTIALIAS_ON);

      FontMetrics fm;
      String str;

      y += 12;

      g.setColor(headerColor);
      g.setFont(headerFont);
      g.drawString("Logisim-Evolution", x, y);

      g.setFont(copyrightFont);
      fm = g.getFontMetrics();
      str = "\u00a9 " + Main.COPYRIGHT_YEAR;
      g.drawString(str, x + IMAGE_WIDTH - fm.stringWidth(str), y);

      g.setFont(versionFont);
      fm = g.getFontMetrics();
      str = "Version " + Main.VERSION.mainVersion();
      y += fm.getHeight() * 5/4;
      g.drawString(str, x + IMAGE_WIDTH - fm.stringWidth(str), y);

      g.setFont(copyrightFont);
      fm = g.getFontMetrics();
      str = Main.VERSION.rev();
      if (str != null && !str.equals("")) {
        y += fm.getHeight() * 5/4;
        g.drawString(str, x + IMAGE_WIDTH - fm.stringWidth(str), y);
      }
      str = Main.VERSION.edition();
      if (str != null && !str.equals("")) {
        y += fm.getHeight() * 5/4;
        g.drawString(str, x + IMAGE_WIDTH - fm.stringWidth(str), y);
      }
    }

    static final Color color0 = Value.FALSE.getColor();
    static final Color color1 = Value.TRUE.getColor();

    private static void setColor(Graphics g, boolean b) {
      g.setColor(b ? color1 : color0);
    }

    private void drawWires(Graphics g, int x0, int y0) {
      boolean upperAnd = (!upper) && lower;
      boolean lowerAnd = (!lower) && upper;
      boolean out = upperAnd || lowerAnd;
      int x, y;

      setColor(g, upper);
      x = toX(x0, 20);
      y = toY(y0, 10);
      g.fillOval(x - 7, y - 7, 14, 14);
      g.drawLine(toX(x0, 0), y, toX(x0, 40), y);
      g.drawLine(x, y, x, toY(y0, 70));
      y = toY(y0, 70);
      g.drawLine(x, y, toX(x0, 80), y);
      setColor(g, !upper);
      y = toY(y0, 10);
      g.drawLine(toX(x0, 70), y, toX(x0, 80), y);

      setColor(g, lower);
      x = toX(x0, 30);
      y = toY(y0, 110);
      g.fillOval(x - 7, y - 7, 14, 14);
      g.drawLine(toX(x0, 0), y, toX(x0, 40), y);
      g.drawLine(x, y, x, toY(y0, 50));
      y = toY(y0, 50);
      g.drawLine(x, y, toX(x0, 80), y);
      setColor(g, !lower);
      y = toY(y0, 110);
      g.drawLine(toX(x0, 70), y, toX(x0, 80), y);

      setColor(g, upperAnd);
      x = toX(x0, 150);
      y = toY(y0, 30);
      g.drawLine(toX(x0, 130), y, x, y);
      g.drawLine(x, y, x, toY(y0, 45));
      y = toY(y0, 45);
      g.drawLine(x, y, toX(x0, 174), y);
      setColor(g, lowerAnd);
      y = toY(y0, 90);
      g.drawLine(toX(x0, 130), y, x, y);
      g.drawLine(x, y, x, toY(y0, 75));
      y = toY(y0, 75);
      g.drawLine(x, y, toX(x0, 174), y);

      setColor(g, out);
      y = toY(y0, 60);
      g.drawLine(toX(x0, 220), y, toX(x0, 240), y);
    }

    @Override
    public void paintComponent(Graphics g) {
      super.paintComponent(g);
      try {
        int x = IMAGE_BORDER;
        int y = IMAGE_BORDER;
        drawCircuit(g, x + 10, y + 55);
        g.setColor(fadeColor);
        g.fillRect(x, y, IMAGE_WIDTH, IMAGE_HEIGHT);
        drawText(g, x, y);
      } catch (Throwable t) {
      }
    }
  }

  private static class PanelThread extends UniquelyNamedThread {
    private MyPanel panel;
    private boolean running = true;

    PanelThread(MyPanel panel) {
      super("About-PanelThread");
      this.panel = panel;
    }

    @Override
    public void run() {
      long start = System.currentTimeMillis();
      while (running) {
        long elapse = System.currentTimeMillis() - start;
        int count = (int) (elapse / 500) % 4;
        panel.upper = (count == 2 || count == 3);
        panel.lower = (count == 1 || count == 2);
        panel.credits.setScroll((int) elapse);
        panel.repaint();
        try {
          Thread.sleep(20);
        } catch (InterruptedException ex) {
        }
      }
    }
  }

  public static MyPanel getImagePanel() {
    return new MyPanel();
  }

  public static void showAboutDialog(JFrame owner) {
    MyPanel imgPanel = getImagePanel();
    JPanel panel = new JPanel(new BorderLayout());
    panel.add(imgPanel);
    panel.setBorder(BorderFactory.createLineBorder(Color.BLACK, 2));

    JOptionPane.showMessageDialog(owner, panel, "Logisim-evolution "
        + Main.VERSION_NAME, JOptionPane.PLAIN_MESSAGE);
  }

  static final int IMAGE_BORDER = 30;
  static final int IMAGE_WIDTH = 430;
  static final int IMAGE_HEIGHT = 284;
  static final int ABOUT_WIDTH = IMAGE_WIDTH + 2 * IMAGE_BORDER;
  static final int ABOUT_HEIGHT = IMAGE_HEIGHT + 2 * IMAGE_BORDER;

  private About() {
  }
}
