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

package com.cburch.logisim.std.gates;
import static com.cburch.logisim.std.Strings.S;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.Area;
import java.awt.geom.Rectangle2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.GeneralPath;
import java.awt.geom.Point2D;
import java.util.HashMap;

import com.cburch.logisim.data.Direction;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.instance.InstancePainter;
import com.cburch.logisim.util.GraphicsUtil;

public class PainterShaped {
  private static GeneralPath computeShield(int width, int height) {
    GeneralPath base;
    if (width < 40) {
      base = SHIELD_NARROW;
    } else if (width < 60) {
      base = SHIELD_MEDIUM;
    } else {
      base = SHIELD_WIDE;
    }

    if (height <= width) { // no wings
      return base;
    } else { // we need to add wings
      int wingHeight = (height - width) / 2;
      int dx = Math.min(20, wingHeight / 4);

      GeneralPath path = new GeneralPath();
      path.moveTo(-width, -height / 2);
      path.quadTo(-width + dx, -(width + height) / 4, -width, -width / 2);
      path.append(base, true);
      path.quadTo(-width + dx, (width + height) / 4, -width, height / 2);
      return path;
    }
  }

  private static int[] getInputLineLengths(GateAttributes attrs,
      AbstractGate factory) {
    int inputs = attrs.inputs;
    int mainHeight = ((Integer) attrs.size.getValue()).intValue();
    Integer key = Integer.valueOf(inputs * 31 + mainHeight);
    Object ret = INPUT_LENGTHS.get(key);
    if (ret != null) {
      return (int[]) ret;
    }

    Direction facing = attrs.facing;
    if (facing != Direction.EAST) {
      attrs = (GateAttributes) attrs.clone();
      attrs.facing = Direction.EAST;
    }

    int[] lengths = new int[inputs];
    INPUT_LENGTHS.put(key, lengths);
    int width = mainHeight;
    Location loc0 = OrGate.FACTORY.getInputOffset(attrs, 0);
    Location locn = OrGate.FACTORY.getInputOffset(attrs, inputs - 1);
    int totalHeight = 10 + loc0.manhattanDistanceTo(locn);
    if (totalHeight < width)
      totalHeight = width;

    GeneralPath path = computeShield(width, totalHeight);
    for (int i = 0; i < inputs; i++) {
      Location loci = OrGate.FACTORY.getInputOffset(attrs, i);
      Point2D p = new Point2D.Float(loci.getX() + 1, loci.getY());
      int iters = 0;
      while (path.contains(p) && iters < 15) {
        iters++;
        p.setLocation(p.getX() + 1, p.getY());
      }
      if (iters >= 15)
        iters = 0;
      lengths[i] = iters;
    }

    /*
     * used prior to 2.5.1, when moved to GeneralPath int wingHeight =
     * (totalHeight - mainHeight) / 2; double wingCenterX = wingHeight *
     * Math.sqrt(3) / 2; double mainCenterX = mainHeight * Math.sqrt(3) / 2;
     *
     * for (int i = 0; i < inputs; i++) { Location loci =
     * factory.getInputOffset(attrs, i); int disti = 5 +
     * loc0.manhattanDistanceTo(loci); if (disti > totalHeight - disti) { //
     * ensure on top half disti = totalHeight - disti; } double dx; if
     * (disti < wingHeight) { // point is on wing int dy = wingHeight / 2 -
     * disti; dx = Math.sqrt(wingHeight * wingHeight - dy * dy) -
     * wingCenterX; } else { // point is on main shield int dy = totalHeight
     * / 2 - disti; dx = Math.sqrt(mainHeight * mainHeight - dy * dy) -
     * mainCenterX; } lengths[i] = (int) (dx - 0.5); }
     */
    return lengths;
  }

  static void paintAnd(InstancePainter painter, int width, int height, boolean solid) {
    Graphics2D g = painter.getGraphics();
    GraphicsUtil.switchToWidth(g, 2);

    Area path;
    if (width < 40) {
      path = AND_PATH_NARROW;
    } else if (width < 60) {
      path = AND_PATH_MEDIUM;
    } else {
      path = AND_PATH_WIDE;
    }
    if (solid) {
      g.setColor(painter.getPalette().SOLID);
      g.fill(path);
      g.setColor(painter.getPalette().LINE);
    }
    g.draw(path);
    if (height > width) {
      g.drawLine(-width + 1, -height / 2, -width + 1, height / 2);
    }
  }

  static void paintInputLines(InstancePainter painter, AbstractGate factory) {
    Location loc = painter.getLocation();
    boolean printView = painter.isPrintView();
    GateAttributes attrs = (GateAttributes) painter.getAttributeSet();
    Direction facing = attrs.facing;
    int inputs = attrs.inputs;
    int negated = attrs.negated;

    int[] lengths = getInputLineLengths(attrs, factory);
    if (painter.getInstance() == null) { // drawing ghost - negation bubbles only
      for (int i = 0; i < inputs; i++) {
        boolean iNegated = ((negated >> i) & 1) == 1;
        if (iNegated) {
          Location offs = factory.getInputOffset(attrs, i);
          Location loci = loc.translate(offs.getX(), offs.getY());
          Location cent = loci.translate(facing, lengths[i] + 5);
          painter.drawDongle(cent.getX(), cent.getY());
        }
      }
    } else {
      Graphics g = painter.getGraphics();
      Color baseColor = g.getColor();
      GraphicsUtil.switchToWidth(g, 3);
      for (int i = 0; i < inputs; i++) {
        Location offs = factory.getInputOffset(attrs, i);
        Location src = loc.translate(offs.getX(), offs.getY());
        int len = lengths[i];
        if (len != 0 && (!printView || painter.isPortConnected(i + 1))) {
          g.setColor(painter.getPortColor(i + 1));
          Location dst = src.translate(facing, len);
          g.drawLine(src.getX(), src.getY(), dst.getX(), dst.getY());
        }
        if (((negated >> i) & 1) == 1) {
          Location cent = src.translate(facing, lengths[i] + 5);
          g.setColor(baseColor);
          painter.drawDongle(cent.getX(), cent.getY());
          GraphicsUtil.switchToWidth(g, 3);
        }
      }
    }
  }

  static void paintNot(InstancePainter painter) {
    if (painter.getAttributeValue(NotGate.ATTR_SIZE) == NotGate.SIZE_NARROW)
      painter.drawAndFill(NOT_PATH_NARROW);
    else
      painter.drawAndFill(NOT_PATH_WIDE);
  }

  static void paintBuffer(InstancePainter painter) {
    painter.drawAndFill(BUFFER_PATH);
  }

  static void paintOr(InstancePainter painter, int width, int height, boolean solid) {
    Graphics2D g = painter.getGraphics();
    GraphicsUtil.switchToWidth(g, 2);

    GeneralPath path;
    if (width < 40) {
      path = OR_PATH_NARROW;
    } else if (width < 60) {
      path = OR_PATH_MEDIUM;
    } else {
      path = OR_PATH_WIDE;
    }
    if (solid) {
      g.setColor(painter.getPalette().SOLID);
      g.fill(path);
      g.setColor(painter.getPalette().LINE);
    }
    g.draw(path);
    if (height > width) {
      paintShield(g, 0, width, height);
    }
  }

  private static void paintShield(Graphics g, int xlate, int width, int height) {
    GraphicsUtil.switchToWidth(g, 2);
    g.translate(xlate, 0);
    ((Graphics2D) g).draw(computeShield(width, height));
    g.translate(-xlate, 0);
  }

  static void paintXor(InstancePainter painter, int width, int height, boolean solid) {
    Graphics g = painter.getGraphics();
    paintOr(painter, width - 10, width - 10, solid);
    paintShield(g, -10, width - 10, height);
  }

  private static final GeneralPath OR_PATH_NARROW;
  private static final GeneralPath OR_PATH_MEDIUM;
  private static final GeneralPath OR_PATH_WIDE;
  private static final GeneralPath SHIELD_NARROW;
  private static final GeneralPath SHIELD_MEDIUM;
  private static final GeneralPath SHIELD_WIDE;

  private static final Area AND_PATH_NARROW;
  private static final Area AND_PATH_MEDIUM;
  private static final Area AND_PATH_WIDE;

  private static final Area NOT_PATH_NARROW;
  private static final Area NOT_PATH_WIDE;

  private static final GeneralPath BUFFER_PATH;

  static {
    OR_PATH_NARROW = new GeneralPath();
    OR_PATH_NARROW.moveTo(0, 0);
    OR_PATH_NARROW.quadTo(-10, -15, -30, -15);
    OR_PATH_NARROW.quadTo(-22, 0, -30, 15);
    OR_PATH_NARROW.quadTo(-10, 15, 0, 0);
    OR_PATH_NARROW.closePath();

    OR_PATH_MEDIUM = new GeneralPath();
    OR_PATH_MEDIUM.moveTo(0, 0);
    OR_PATH_MEDIUM.quadTo(-20, -25, -50, -25);
    OR_PATH_MEDIUM.quadTo(-37, 0, -50, 25);
    OR_PATH_MEDIUM.quadTo(-20, 25, 0, 0);
    OR_PATH_MEDIUM.closePath();

    OR_PATH_WIDE = new GeneralPath();
    OR_PATH_WIDE.moveTo(0, 0);
    OR_PATH_WIDE.quadTo(-25, -35, -70, -35);
    OR_PATH_WIDE.quadTo(-50, 0, -70, 35);
    OR_PATH_WIDE.quadTo(-25, 35, 0, 0);
    OR_PATH_WIDE.closePath();

    SHIELD_NARROW = new GeneralPath();
    SHIELD_NARROW.moveTo(-30, -15);
    SHIELD_NARROW.quadTo(-22, 0, -30, 15);

    SHIELD_MEDIUM = new GeneralPath();
    SHIELD_MEDIUM.moveTo(-50, -25);
    SHIELD_MEDIUM.quadTo(-37, 0, -50, 25);

    SHIELD_WIDE = new GeneralPath();
    SHIELD_WIDE.moveTo(-70, -35);
    SHIELD_WIDE.quadTo(-50, 0, -70, 35);

    AND_PATH_NARROW = new Area(new Rectangle2D.Float(-30, -15, 15, 30));
    AND_PATH_NARROW.add(new Area(new Ellipse2D.Float(-30, -15, 30, 30)));

    AND_PATH_MEDIUM = new Area(new Rectangle2D.Float(-50, -25, 25, 50));
    AND_PATH_MEDIUM.add(new Area(new Ellipse2D.Float(-50, -25, 50, 50)));

    AND_PATH_WIDE = new Area(new Rectangle2D.Float(-70, -35, 35, 70));
    AND_PATH_WIDE.add(new Area(new Ellipse2D.Float(-70, -35, 70, 70)));

    GeneralPath tri = new GeneralPath();
    tri.moveTo(-6, 0);
    tri.lineTo(-19,-6);
    tri.lineTo(-19, 6);
    tri.closePath();
    NOT_PATH_NARROW = new Area(tri);
    NOT_PATH_NARROW.add(new Area(new Ellipse2D.Float(-6, -3, 6, 6)));

    tri = new GeneralPath();
    tri.moveTo(-10, 0);
    tri.lineTo(-29,-7);
    tri.lineTo(-29, 7);
    tri.closePath();
    NOT_PATH_WIDE = new Area(tri);
    NOT_PATH_WIDE.add(new Area(new Ellipse2D.Float(-9, -4, 9, 9)));

    BUFFER_PATH = new GeneralPath();
    BUFFER_PATH.moveTo(0, 0);
    BUFFER_PATH.lineTo(-19, -7);
    BUFFER_PATH.lineTo(-19,  7);
    BUFFER_PATH.closePath();
  }

  private static HashMap<Integer, int[]> INPUT_LENGTHS = new HashMap<Integer, int[]>();

  private PainterShaped() {
  }
}
