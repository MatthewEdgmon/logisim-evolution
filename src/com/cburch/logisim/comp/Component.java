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

package com.cburch.logisim.comp;

import java.awt.Graphics;
import java.util.List;

import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Location;

public interface Component extends Location.At {
  // listener methods
  public void addComponentListener(ComponentListener l);

  public boolean contains(Location pt);

  public boolean contains(Location pt, Graphics g);

  public void draw(ComponentDrawContext context);

  public boolean endsAt(Location pt);

  // user interface methods
  public void expose(ComponentDrawContext context);

  public AttributeSet getAttributeSet();

  public Bounds getBounds();

  public Bounds getBounds(Graphics g);

  public EndData getEnd(int index);

  // propagation methods
  public List<EndData> getEnds(); // list of EndDatas
  // basic information methods

  public ComponentFactory getFactory();

  /**
   * Retrieves information about a special-purpose feature for this component.
   * This technique allows future Logisim versions to add new features for
   * components without requiring changes to existing components. It also
   * removes the necessity for the Component API to directly declare methods
   * for each individual feature. In most cases, the <code>key</code> is a
   * <code>Class</code> object corresponding to an interface, and the method
   * should return an implementation of that interface if it supports the
   * feature.
   *
   * As of this writing, possible values for <code>key</code> include:
   * <code>Pokable.class</code>, <code>CustomHandles.class</code>,
   * <code>WireRepair.class</code>, <code>TextEditable.class</code>,
   * <code>MenuExtender.class</code>, <code>ToolTipMaker.class</code>,
   * <code>ExpressionComputer.class</code>, and <code>Loggable.class</code>.
   *
   * @param key
   *            an object representing a feature.
   * @return an object representing information about how the component
   *         supports the feature, or <code>null</code> if it does not support
   *         the feature.
   */
  public Object getFeature(Object key);

  public void propagate(CircuitState state);

  public void removeComponentListener(ComponentListener l);
}
