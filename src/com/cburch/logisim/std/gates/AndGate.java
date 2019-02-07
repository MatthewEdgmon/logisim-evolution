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

import java.awt.Graphics;

import com.bfh.logisim.fpgagui.FPGAReport;
import com.bfh.logisim.hdlgenerator.AbstractHDLGeneratorFactory;
import com.cburch.logisim.analyze.model.Expression;
import com.cburch.logisim.analyze.model.Expressions;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.instance.InstancePainter;
import com.cburch.logisim.instance.InstanceState;
import com.cburch.logisim.util.GraphicsUtil;

class AndGate extends AbstractGate {

  public static AndGate FACTORY = new AndGate();

  private AndGate() {
    super("AND Gate", S.getter("andGateComponent"));
    setRectangularLabel("&");
    setIconNames("andGate.gif", "andGateRect.gif", "dinAndGate.gif");
  }

  @Override
  protected Expression computeExpression(Expression[] inputs, int numInputs) {
    Expression ret = inputs[0];
    for (int i = 1; i < numInputs; i++) {
      ret = Expressions.and(ret, inputs[i]);
    }
    return ret;
  }

  @Override
  protected Value computeOutput(Value[] inputs, int numInputs,
      InstanceState state) {
    return GateFunctions.computeAnd(inputs, numInputs);
  }

  @Override
  protected Value getIdentity() {
    return Value.TRUE;
  }

  @Override
  public AbstractHDLGeneratorFactory getHDLGenerator(AbstractHDLGeneratorFactory.HDLCTX ctx) {
    if (ctx.lang.equals("VHDL"))
      return GateVhdlGenerator.forAnd(ctx);
    else
      return GateVerilogGenerator.forAnd(ctx);
  }

  @Override
  protected void paintDinShape(InstancePainter painter, int width,
      int height, int inputs) {
    PainterDin.paintAnd(painter, width, height, false);
  }

  @Override
  protected void paintIconShaped(InstancePainter painter) {
    Graphics g = painter.getGraphics();
    int[] xp = new int[] { 10, 2, 2, 10 };
    int[] yp = new int[] { 2, 2, 18, 18 };
    g.drawPolyline(xp, yp, 4);
    GraphicsUtil.drawCenteredArc(g, 10, 10, 8, -90, 180);
  }

  @Override
  protected void paintShape(InstancePainter painter, int width, int height) {
    PainterShaped.paintAnd(painter, width, height);
  }
}
