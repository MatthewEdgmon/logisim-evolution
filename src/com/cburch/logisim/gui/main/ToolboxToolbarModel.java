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

package com.cburch.logisim.gui.main;
import static com.cburch.logisim.gui.main.Strings.S;

import java.util.List;

import com.cburch.draw.toolbar.AbstractToolbarModel;
import com.cburch.draw.toolbar.ToolbarItem;
import com.cburch.logisim.gui.menu.LogisimMenuBar;
import com.cburch.logisim.util.UnmodifiableList;

class ToolboxToolbarModel extends AbstractToolbarModel
  implements MenuListener.EnabledListener {
  private LogisimToolbarItem itemAdd;
  private LogisimToolbarItem itemUp;
  private LogisimToolbarItem itemDown;
  private LogisimToolbarItem itemDelete;
  private List<ToolbarItem> items;

  public ToolboxToolbarModel(MenuListener menu) {
    itemAdd = new LogisimToolbarItem(menu, "projadd.gif",
        LogisimMenuBar.ADD_CIRCUIT,
        S.getter("projectAddCircuitTip"));
    itemUp = new LogisimToolbarItem(menu, "projup.gif",
        LogisimMenuBar.MOVE_CIRCUIT_UP,
        S.getter("projectMoveCircuitUpTip"));
    itemDown = new LogisimToolbarItem(menu, "projdown.gif",
        LogisimMenuBar.MOVE_CIRCUIT_DOWN,
        S.getter("projectMoveCircuitDownTip"));
    itemDelete = new LogisimToolbarItem(menu, "projdel.gif",
        LogisimMenuBar.REMOVE_CIRCUIT,
        S.getter("projectRemoveCircuitTip"));

    items = UnmodifiableList.create(new ToolbarItem[] { itemAdd, itemUp,
      itemDown, itemDelete, });

    menu.addEnabledListener(this);
  }

  @Override
  public List<ToolbarItem> getItems() {
    return items;
  }

  @Override
  public boolean isSelected(ToolbarItem item) {
    return false;
  }

  @Override
  public void itemSelected(ToolbarItem item) {
    if (item instanceof LogisimToolbarItem) {
      ((LogisimToolbarItem) item).doAction();
    }
  }

  //
  // EnabledListener methods
  //
  public void menuEnableChanged(MenuListener source) {
    fireToolbarAppearanceChanged();
  }
}
