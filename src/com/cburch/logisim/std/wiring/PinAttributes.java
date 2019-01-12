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

package com.cburch.logisim.std.wiring;

import java.util.Arrays;
import java.util.List;

import com.cburch.logisim.circuit.RadixOption;
import com.cburch.logisim.comp.EndData;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.instance.StdAttr;

class PinAttributes extends ProbeAttributes {
  public static PinAttributes instance = new PinAttributes();

  private static final List<Attribute<?>> ATTRIBUTES = Arrays
      .asList(new Attribute<?>[] { StdAttr.FACING, Pin.ATTR_TYPE,
        StdAttr.WIDTH, Pin.ATTR_TRISTATE, Pin.ATTR_PULL,
        StdAttr.LABEL, StdAttr.LABEL_LOC, StdAttr.LABEL_FONT,
        RadixOption.ATTRIBUTE });

  BitWidth width = BitWidth.ONE;
  boolean threeState = false; // true;
  int type = EndData.INPUT_ONLY;
  Object pull = Pin.PULL_NONE;

  public PinAttributes() {
  }

  @Override
  public List<Attribute<?>> getAttributes() {
    return ATTRIBUTES;
  }

  @Override
  public <V> V getValue(Attribute<V> attr) {
    if (attr == StdAttr.WIDTH)
      return (V) width;
    if (attr == Pin.ATTR_TRISTATE)
      return (V) Boolean.valueOf(threeState);
    if (attr == Pin.ATTR_TYPE)
      return (V) Boolean.valueOf(type == EndData.OUTPUT_ONLY);
    if (attr == Pin.ATTR_PULL)
      return (V) pull;
    return super.getValue(attr);
  }

  boolean isInput() {
    return type != EndData.OUTPUT_ONLY;
  }

  boolean isOutput() {
    return type != EndData.INPUT_ONLY;
  }

  @Override
  public <V> void updateAttr(Attribute<V> attr, V value) {
    if (attr == StdAttr.WIDTH)
      width = (BitWidth) value;
    else if (attr == Pin.ATTR_TRISTATE)
      threeState = ((Boolean) value).booleanValue();
    else if (attr == Pin.ATTR_TYPE)
      type = ((Boolean) value).booleanValue()
          ? EndData.OUTPUT_ONLY
          : EndData.INPUT_ONLY;
    else if (attr == Pin.ATTR_PULL)
      pull = value;
    else
      super.updateAttr(attr, value);
  }
}
