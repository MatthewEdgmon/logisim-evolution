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

package com.cburch.logisim.std.hdl;

import java.util.Arrays;

import com.cburch.hdl.HdlModel;
import com.cburch.hdl.HdlModelListener;
import com.cburch.logisim.util.EventSourceWeakSupport;

public abstract class HdlContent implements HdlModel, Cloneable {

  protected static <T> T[] concat(T[] first, T[] second) {
    T[] result = Arrays.copyOf(first, first.length + second.length);
    System.arraycopy(second, 0, result, first.length, second.length);
    return result;
  }

  protected EventSourceWeakSupport<HdlModelListener> listeners;
  //protected EventSourceWeakSupport<CircuitListener> circlisteners;

  protected HdlContent() {
    this.listeners = null;
  }

  @Override
  public void addHdlModelListener(HdlModelListener l) {
    if (listeners == null) {
      listeners = new EventSourceWeakSupport<HdlModelListener>();
    }
    listeners.add(l);
  }

  // public void addCircuitListener(CircuitListener l) {
  //   if (circlisteners == null) {
  //     circlisteners = new EventSourceWeakSupport<CircuitListener>();
  //   }
  //   circlisteners.add(l);
  // }
  // public void removeCircuitListener(CircuitListener l) {
  //   if (circlisteners == null) {
  //     return;
  //   }
  //   circlisteners.remove(l);
  // }

  @Override
  public HdlContent clone() throws CloneNotSupportedException {
    HdlContent ret = (HdlContent) super.clone();
    ret.listeners = null;
    int n = 0;
    if (listeners != null) {
      for (HdlModelListener l : listeners) {
        n++;
      }
    }
    return ret;
  }

  public String toString() {
    String s = super.toString() + " " + listeners;
    if (listeners != null) {
      s += " [";
      for (HdlModelListener l : listeners) {
        s += "\n" + l.getClass() + " " + l.toString();
      }
      s += "\n]";
    }
    return s;
  }

  @Override
  public abstract boolean compare(HdlModel model);

  @Override
  public abstract boolean compare(String value);

  protected void fireContentSet() {
    if (listeners == null) {
      return;
    }

    boolean found = false;
    for (HdlModelListener l : listeners) {
      found = true;
      l.contentSet(this);
    }

    if (!found) {
      listeners = null;
    }
  }

  protected void fireAboutToSave() {
    if (listeners == null) {
      return;
    }

    boolean found = false;
    for (HdlModelListener l : listeners) {
      found = true;
      l.aboutToSave(this);
    }

    if (!found) {
      listeners = null;
    }
  }

  protected void fireAppearanceChanged() {
    if (listeners == null) {
      return;
    }

    boolean found = false;
    for (HdlModelListener l : listeners) {
      found = true;
      l.appearanceChanged(this);
    }

    if (!found) {
      listeners = null;
    }
  }

  public void displayChanged() {
    if (listeners == null) {
      return;
    }

    boolean found = false;
    for (HdlModelListener l : listeners) {
      found = true;
      l.displayChanged(this);
    }

    if (!found) {
      listeners = null;
    }
  }

  // protected void fireNameChange() {
  //   if (circlisteners == null) {
  //     return;
  //   }

  //   boolean found = false;
  //   for (CircuitListener l : circlisteners) {
  //     found = true;
  //     l.circuitChanged(CircuitEvent.ACTION_SET_NAME);
  //   }

  //   if (!found) {
  //     circlisteners = null;
  //   }
  // }

  @Override
  public abstract String getContent();

  @Override
  public abstract String getName();

  @Override
  public void removeHdlModelListener(HdlModelListener l) {
    if (listeners == null) {
      return;
    }
    listeners.remove(l);
    if (listeners.isEmpty()) {
      listeners = null;
    }
  }

  @Override
  public abstract boolean setContent(String content); // does not necessarily validate

}
