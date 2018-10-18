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

package com.cburch.logisim.circuit.appear;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import com.cburch.draw.model.CanvasObject;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.data.AttributeOption;
import com.cburch.logisim.instance.Instance;
import com.cburch.logisim.circuit.CircuitAttributes;

class DefaultAppearance {

  private static class CompareLocations implements Comparator<Instance> {
    private boolean byX;

    CompareLocations(boolean byX) {
      this.byX = byX;
    }

    public int compare(Instance a, Instance b) {
      Location aloc = a.getLocation();
      Location bloc = b.getLocation();
      if (byX) {
        int ax = aloc.getX();
        int bx = bloc.getX();
        if (ax != bx) {
          return ax < bx ? -1 : 1;
        }
      } else {
        int ay = aloc.getY();
        int by = bloc.getY();
        if (ay != by) {
          return ay < by ? -1 : 1;
        }
      }
      return aloc.compareTo(bloc);
    }
  }

  static void sortPinList(List<Instance> pins, Direction facing) {
    if (facing == Direction.NORTH || facing == Direction.SOUTH) {
      Comparator<Instance> sortHorizontal = new CompareLocations(true);
      Collections.sort(pins, sortHorizontal);
    } else {
      Comparator<Instance> sortVertical = new CompareLocations(false);
      Collections.sort(pins, sortVertical);
    }
  }

  public static List<CanvasObject> build(Collection<Instance> pins, String name, AttributeOption style) {
    if (style == CircuitAttributes.APPEAR_CLASSIC) {
      return DefaultClassicAppearance.build(pins);
    } else {
      return DefaultEvolutionAppearance.build(pins, name);
    }
  }

  private DefaultAppearance() { }
}
