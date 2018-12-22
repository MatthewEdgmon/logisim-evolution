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

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.SubcircuitFactory;
import com.cburch.logisim.comp.ComponentFactory;
import com.cburch.logisim.file.LibraryEvent;
import com.cburch.logisim.file.LibraryListener;
import com.cburch.logisim.gui.menu.EditHandler;
import com.cburch.logisim.gui.menu.LogisimMenuBar;
import com.cburch.logisim.gui.menu.ProjectCircuitActions;
import com.cburch.logisim.gui.menu.ProjectLibraryActions;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.proj.ProjectEvent;
import com.cburch.logisim.proj.ProjectListener;
import com.cburch.logisim.std.base.Base;
import com.cburch.logisim.std.hdl.VhdlContent;
import com.cburch.logisim.std.hdl.VhdlEntity;
import com.cburch.logisim.tools.AddTool;
import com.cburch.logisim.tools.Library;
import com.cburch.logisim.tools.Tool;

public class LayoutEditHandler extends EditHandler
  implements ProjectListener, LibraryListener, PropertyChangeListener {
  private Frame frame;

  LayoutEditHandler(Frame frame) {
    this.frame = frame;

    Project proj = frame.getProject();
    LayoutClipboard.forComponents.addPropertyChangeListener(LayoutClipboard.contentsProperty, this);
    LayoutClipboard.forCircuit.addPropertyChangeListener(LayoutClipboard.contentsProperty, this);
    LayoutClipboard.forVhdl.addPropertyChangeListener(LayoutClipboard.contentsProperty, this);
    LayoutClipboard.forLibrary.addPropertyChangeListener(LayoutClipboard.contentsProperty, this);
    proj.addProjectListener(this);
    proj.addLibraryListener(this);
  }

  @Override
  public void addControlPoint() {
    ; // not yet supported in layout mode
  }

  @Override
  public void computeEnabled() {
    Project proj = frame.getProject();
    
    // check if some components are selected
    Selection sel = proj == null ? null : proj.getSelection();
    boolean selComp = sel != null && !sel.isEmpty();
    boolean modComp = proj != null
        && proj.getLogisimFile().contains(proj.getCurrentCircuit());

    // check if a project or library subcircuit or vhdl tool is selected in toolbox
    Tool tool = frame.getSelectedToolboxTool();
    boolean selProjTool = tool != null && proj.getLogisimFile().containsFromSource(tool);
    boolean selLibTool = !selProjTool && tool instanceof AddTool
        && ((AddTool)tool).isSubcircuitOrVhdl();

    // check if a library is selected in toolbox
    Library lib = frame.getSelectedToolboxLibrary();
    boolean selLib = (lib != null && proj.getLogisimFile().getLibraries().contains(lib));

    setEnabled(LogisimMenuBar.CUT,
        (selComp && modComp) // cut components from project circuit
        || selProjTool // cut circuit or vhdl from project
        || selLib); // cut library from project
    setEnabled(LogisimMenuBar.COPY, 
        selComp // copy components from project circuit
        || selProjTool // copy circuit or vhdl from project
        || selLibTool // copy circuit or vhdl from library
        || selLib); // copy library from project
    setEnabled(LogisimMenuBar.PASTE,
        (modComp && !LayoutClipboard.forComponents.isEmpty()) // paste components
        || !LayoutClipboard.forCircuit.isEmpty() // paste circuit
        || !LayoutClipboard.forVhdl.isEmpty() // paste vhdl
        || !LayoutClipboard.forLibrary.isEmpty()); // paste library
    setEnabled(LogisimMenuBar.DELETE,
        (selComp && modComp) // delete components from project circuit
        || selProjTool // delete circuit or vhdl from project
        || selLib); // delete library from project
    setEnabled(LogisimMenuBar.DUPLICATE,
        (selComp && modComp) // duplicate components within project circuit
        || selProjTool // duplicate circuit or vhdl within project
        || selLibTool); // duplicate circuit or vhdl from library into project
    setEnabled(LogisimMenuBar.SELECT_ALL, true);
    setEnabled(LogisimMenuBar.RAISE, false); // todo: move circuit/lib up
    setEnabled(LogisimMenuBar.LOWER, false); // todo: move circuit/lib down
    setEnabled(LogisimMenuBar.RAISE_TOP, false); // todo: move circuit/lib up
    setEnabled(LogisimMenuBar.LOWER_BOTTOM, false); // todo: move circuit/lib down
    setEnabled(LogisimMenuBar.ADD_CONTROL, false);
    setEnabled(LogisimMenuBar.REMOVE_CONTROL, false);
  }

  @Override
  public void copy() {
    Project proj = frame.getProject();
    // copy components from circuit
    Selection sel = frame.getCanvas().getSelection();
    if (sel != null && !sel.isEmpty()) {
      SelectionActions.copy(proj, sel);
      return;
    }
    // copy circuit or vhdl from project or library
    Tool tool = frame.getSelectedToolboxTool();
    if (tool instanceof AddTool) {
      ComponentFactory f = ((AddTool)tool).getFactory();
      if (f instanceof SubcircuitFactory) {
        Circuit c = ((SubcircuitFactory)f).getSubcircuit();
        SelectionActions.copy(proj, c);
      } else if (f instanceof VhdlEntity) {
        VhdlContent c = ((VhdlEntity)f).getContent();
        SelectionActions.copy(proj, c);
      }
      return;
    }
    // copy library from project
    Library lib = frame.getSelectedToolboxLibrary();
    if (lib != null && proj.getLogisimFile().getLibraries().contains(lib)) {
      SelectionActions.copy(proj, lib);
      return;
    }
  }

  @Override
  public void cut() {
    Project proj = frame.getProject();
    // cut components from circuit
    Selection sel = frame.getCanvas().getSelection();
    if (sel != null && !sel.isEmpty()) {
      proj.doAction(SelectionActions.cut(proj, sel));
      return;
    }
    // cut circuit or vhdl from project
    Tool tool = frame.getSelectedToolboxTool();
    if (tool instanceof AddTool && proj.getLogisimFile().containsFromSource(tool)) {
      ComponentFactory f = ((AddTool)tool).getFactory();
      if (f instanceof SubcircuitFactory) {
        Circuit c = ((SubcircuitFactory)f).getSubcircuit();
        SelectionActions.copy(proj, c);
        ProjectCircuitActions.doRemoveCircuit(proj, c);
      } else if (f instanceof VhdlEntity) {
        VhdlContent c = ((VhdlEntity)f).getContent();
        SelectionActions.copy(proj, c);
        ProjectCircuitActions.doRemoveVhdl(proj, c);
      }
      return;
    }
    // cut library from project
    Library lib = frame.getSelectedToolboxLibrary();
    if (lib != null && proj.getLogisimFile().getLibraries().contains(lib)) {
      SelectionActions.copy(proj, lib);
      ProjectLibraryActions.doUnloadLibrary(proj, lib);
      return;
    }
  }

  @Override
  public void delete() {
    Project proj = frame.getProject();
    // delete components from circuit
    Selection sel = frame.getCanvas().getSelection();
    if (sel != null && !sel.isEmpty()) {
      proj.doAction(SelectionActions.delete(sel));
      return;
    }
    // delete circuit or vhdl from project
    Tool tool = frame.getSelectedToolboxTool();
    if (tool instanceof AddTool && proj.getLogisimFile().containsFromSource(tool)) {
      ComponentFactory f = ((AddTool)tool).getFactory();
      if (f instanceof SubcircuitFactory) {
        Circuit c = ((SubcircuitFactory)f).getSubcircuit();
        ProjectCircuitActions.doRemoveCircuit(proj, c);
      } else if (f instanceof VhdlEntity) {
        VhdlContent c = ((VhdlEntity)f).getContent();
        ProjectCircuitActions.doRemoveVhdl(proj, c);
      }
      return;
    }
    // delete library from project
    Library lib = frame.getSelectedToolboxLibrary();
    if (lib != null && proj.getLogisimFile().getLibraries().contains(lib)) {
      ProjectLibraryActions.doUnloadLibrary(proj, lib);
      return;
    }
  }

  @Override
  public void duplicate() {
    Project proj = frame.getProject();
    // duplicate componets within project circuit
    Selection sel = frame.getCanvas().getSelection();
    if (sel != null && !sel.isEmpty()) {
      proj.doAction(SelectionActions.duplicate(sel));
      return;
    }
    // duplicate circuit or vhdl from project or library into project
    Tool tool = frame.getSelectedToolboxTool();
    if (tool instanceof AddTool && proj.getLogisimFile().containsFromSource(tool)) {
      ComponentFactory f = ((AddTool)tool).getFactory();
      if (f instanceof SubcircuitFactory) {
        Circuit c = ((SubcircuitFactory)f).getSubcircuit();
        // ProjectCircuitActions.doDuplicateCircuit(proj, c); // todo
      } else if (f instanceof VhdlEntity) {
        VhdlContent c = ((VhdlEntity)f).getContent();
        // ProjectCircuitActions.doDuplicateVhdl(proj, c); // todo
      }
      return;
    }
  }

  public void libraryChanged(LibraryEvent e) {
    int action = e.getAction();
    if (action == LibraryEvent.ADD_LIBRARY) {
      computeEnabled();
    } else if (action == LibraryEvent.REMOVE_LIBRARY) {
      computeEnabled();
    }
  }

  @Override
  public void lower() {
    ; // not yet supported in layout mode
  }

  @Override
  public void lowerBottom() {
    ; // not yet supported in layout mode
  }

  @Override
  public void paste() {
    Project proj = frame.getProject();
    Selection sel = frame.getCanvas().getSelection();
    selectSelectTool(proj);
    proj.doAction(SelectionActions.pasteMaybe(proj, sel));
  }

  public void projectChanged(ProjectEvent e) {
    int action = e.getAction();
    if (action == ProjectEvent.ACTION_SET_FILE) {
      computeEnabled();
    } else if (action == ProjectEvent.ACTION_SET_CURRENT) {
      computeEnabled();
    } else if (action == ProjectEvent.ACTION_SELECTION) {
      computeEnabled();
    } else if (action == ProjectEvent.ACTION_SET_TOOL) {
      computeEnabled();
    }
  }

  public void propertyChange(PropertyChangeEvent event) {
    if (event.getPropertyName().equals(LayoutClipboard.contentsProperty))
      computeEnabled();
  }

  @Override
  public void raise() {
    ; // not yet supported in layout mode
  }

  @Override
  public void raiseTop() {
    ; // not yet supported in layout mode
  }

  @Override
  public void removeControlPoint() {
    ; // not yet supported in layout mode
  }

  @Override
  public void selectAll() {
    Project proj = frame.getProject();
    Selection sel = frame.getCanvas().getSelection();
    selectSelectTool(proj);
    Circuit circ = proj.getCurrentCircuit();
    sel.addAll(circ.getWires());
    sel.addAll(circ.getNonWires());
    proj.repaintCanvas();
  }

  private void selectSelectTool(Project proj) {
    for (Library sub : proj.getLogisimFile().getLibraries()) {
      if (sub instanceof Base) {
        Base base = (Base) sub;
        Tool tool = base.getTool("Edit Tool");
        if (tool != null)
          proj.setTool(tool);
      }
    }
  }
}
