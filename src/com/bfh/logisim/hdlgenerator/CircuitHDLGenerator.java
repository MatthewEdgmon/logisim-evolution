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

package com.bfh.logisim.hdlgenerator;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import com.bfh.logisim.library.DynamicClock;
import com.bfh.logisim.netlist.CorrectLabel;
import com.bfh.logisim.netlist.Net;
import com.bfh.logisim.netlist.Netlist;
import com.bfh.logisim.netlist.NetlistComponent;
import com.bfh.logisim.netlist.Path;
import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitAttributes;
import com.cburch.logisim.circuit.SubcircuitFactory;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.comp.EndData;
import com.cburch.logisim.hdl.Hdl;
import com.cburch.logisim.instance.Instance;
import com.cburch.logisim.std.wiring.ClockHDLGenerator;
import com.cburch.logisim.std.wiring.Pin;

public class CircuitHDLGenerator extends HDLGenerator {

	private Circuit circ;
  private Netlist _circNets; // netlist for this circ; _nets is for our parent

	public CircuitHDLGenerator(ComponentContext ctx, Circuit circ) {
    super(ctx, "circuit",
        "Circuit_"+ CorrectLabel.getCorrectLabel(circ.getName().toUpperCase()),
        "i_Circ");
		this.circ = circ;
		this._circNets = ctx.getNetlist(circ);
    
    // Normal ports
    for (Component pin : circ.getNonWires()) {
      if (!(pin.getFactory() instanceof Pin))
        continue;
      String label = NetlistComponent.labelOf(pin);
      EndData end = pin.getEnd(0);
      if (end.isInput()) // note: if data goes into Pin component, then port is an output
        outPorts.add(label, end.getWidth().getWidth(), -1, null);
      else
        inPorts.add(label, end.getWidth().getWidth(), -1, null);
    }

    // Note: Other setup is deferred until later, because it depends on Netlist
    // state that won't be ready until during or after global
    // validation/analysis of the entire design hierarchy and all Netslists.
  }

  private boolean finishedSetup = false;
  public void notifyNetlistReady() {
    if (finishedSetup)
      return;
    finishedSetup = true;
    
    // recurse for subcircuits first
    for (NetlistComponent subcirc : _circNets.subcircuits) {
      CircuitHDLGenerator g = (CircuitHDLGenerator)subcirc.hdlSupport;
      g.notifyNetlistReady();
    }

    // hidden ports
    Netlist.Int3 hidden = _circNets.numHiddenBits();
    inPorts.addVector("LOGISIM_HIDDEN_FPGA_INPUT", hidden.in, -1, null);
    inOutPorts.addVector("LOGISIM_HIDDEN_FPGA_INOUT", hidden.inout, -1, null);
    outPorts.addVector("LOGISIM_HIDDEN_FPGA_OUTPUT", hidden.out, -1, null);

    // global clock buses
    for (int i = 0; i < ctx.clockbus.shapes().size(); i++)
			inPorts.add(ClockHDLGenerator.CLK_TREE_NET + i, ClockHDLGenerator.CLK_TREE_WIDTH, -1, null);

    // dynamic clock
    NetlistComponent dynClock = _circNets.dynamicClock();
    if (dynClock != null)
      outPorts.add("LOGISIM_DYNAMIC_CLOCK_OUT",
          dynClock.original.getAttributeSet().getValue(DynamicClock.WIDTH_ATTR).getWidth(), -1, null);

    // Nets
    for (Net net: _circNets.nets)
      wires.add(net.name, net.bitWidth());
  }

  // For a given external end corresponding to some internal Pin, return the
  // internal net connected to that Pin.
  public Net getInternalNetFor(NetlistComponent comp, int end) {
    // The NetlistComponent is embedded in some parent circuit, and the end
    // refers to the port ordering as seen from that parent. This matches the
    // ordering used by NetlistComponent, which uses the ordering defined by
    // Component.getEnds(), which in turn comes from CircuitAttributes, which is
    // based ultimately on the appearance and the sorting order of the pins
    // within the appearance (not on the canvas). Here we need to figure out
    // which Pin underlies that end, so that we can figure out which internal
    // net it belongs to. Forunately, SubcircuitFactory tucks the needed
    // correspondence away inside the circuit attributes.
    CircuitAttributes attrs = (CircuitAttributes)comp.original.getAttributeSet();
    Component pin = attrs.getPinInstances()[end].getComponent();
    return getPortMappingForPin(pin);
  }

  // For a given internal Pin, get the internal net conncted to it.
  public Net getPortMappingForPin(Component pin) {
    return _circNets.netAt.get(pin.getEnd(0).getLocation());
  }

  // For a given internal Pin, get the external end number.
	private int getEndIndex(NetlistComponent comp, NetlistComponent pin) {
    CircuitAttributes attrs = (CircuitAttributes)comp.original.getAttributeSet();
    Instance[] ports = attrs.getPinInstances();
    for (int i = 0; i < ports.length; i++)
      if (ports[i].getComponent() == pin.original)
        return i;
    return -1;
	}

  // For a given internal Pin, get a mapping to an externally connected Net
  private void getMapForCircuitPort(Hdl.Map map, NetlistComponent comp, NetlistComponent pin, boolean isOutput) {
    int endid = getEndIndex(comp, pin);
    if (endid < 0) {
      _err.AddFatalError("INTERNAL ERROR: Missing sub-circuit port '%s'.", pin.label());
      return;
    }
    int w = pin.original.getEnd(0).getWidth().getWidth();
    PortInfo p = new PortInfo(pin.label(), ""+w, endid, isOutput ? null : false);
    super.getPortMappings(map, comp, p);
  }

	@Override
  protected Hdl.Map getPortMappings(NetlistComponent comp) {
    Hdl.Map map = new Hdl.Map(_lang, _err);
		if (comp != null) {
      // Mappings for a subcircuit within the circuit design under tests
			SubcircuitFactory sub = (SubcircuitFactory) comp.original.getFactory();

      // Clock trees
      for (int i = 0; i < ctx.clockbus.shapes().size(); i++) {
        String clkTree = ClockHDLGenerator.CLK_TREE_NET + i;
        map.add(clkTree, clkTree);
			}

      // Dynamic clock - should not be present in subcircuit

      // Hidden ports
      NetlistComponent.Range3 r = comp.getLocalHiddenPortIndices();
			if (r.end.in >= r.start.in)
        map.add("LOGISIM_HIDDEN_FPGA_INPUT", "LOGISIM_HIDDEN_FPGA_INPUT", r.end.in, r.start.in);
			if (r.end.inout >= r.start.inout)
        map.add("LOGISIM_HIDDEN_FPGA_INOUT", "LOGISIM_HIDDEN_FPGA_INOUT", r.end.inout, r.start.inout);
			if (r.end.out >= r.start.out)
        map.add("LOGISIM_HIDDEN_FPGA_OUTPUT", "LOGISIM_HIDDEN_FPGA_OUTPUT", r.end.out, r.start.out);

      // Normal ports
      for (NetlistComponent pin : _circNets.inpins)
        getMapForCircuitPort(map, comp, pin, false);
      for (NetlistComponent pin : _circNets.outpins)
        getMapForCircuitPort(map, comp, pin, true);

		} else {
      // Mappings for the circuit design under test, within the TopLevelHDLGenerator circuit
      
      // Clock trees
      for (int i = 0; i < ctx.clockbus.shapes().size(); i++) {
        String clkTree = ClockHDLGenerator.CLK_TREE_NET + i;
        map.add(clkTree, clkTree);
			}

      // Dynamic clock
      NetlistComponent dynClock = _circNets.dynamicClock();
      if (dynClock != null)
        map.add("LOGISIM_DYNAMIC_CLOCK_OUT", "s_LOGISIM_DYNAMIC_CLOCK");

      // Hidden ports
      // note: Toplevel has direct connection and inversions for InOut ports
      Netlist.Int3 h = _circNets.numHiddenBits();
			if (h.in > 0)
        map.add("LOGISIM_HIDDEN_FPGA_INPUT", "s_LOGISIM_HIDDEN_FPGA_INPUT", h.in-1, 0);
			if (h.inout > 0)
        map.add("LOGISIM_HIDDEN_FPGA_INOUT", "LOGISIM_HIDDEN_FPGA_INOUT", h.inout-1, 0);
			if (h.out > 0)
        map.add("LOGISIM_HIDDEN_FPGA_OUTPUT", "s_LOGISIM_HIDDEN_FPGA_OUTPUT", h.out-1, 0);
      
      // Normal ports
      for (NetlistComponent pin : _circNets.inpins)
        map.add(pin.label(), "s_" + pin.label());
      for (NetlistComponent pin : _circNets.outpins)
        map.add(pin.label(), "s_" + pin.label());
		}
    return map;
	}

  // Second-level entry point: recursively write all HDL files for the project.
  public boolean writeAllHDLFiles(String rootDir) {
		if (!rootDir.endsWith(File.separator))
			rootDir += File.separator;
    return writeAllHDLFiles(rootDir, new HashSet<String>(), new Path(circ));
  }

	private boolean writeAllHDLFiles(String rootDir, HashSet<String> writtenComponents, Path path) {
		_circNets.currentPath = path;
    try {
      // Generate this circuit first
      String name = CorrectLabel.getCorrectLabel(circ.getName());
      if (writtenComponents.contains(name))
        return true;
      if (!writeHDLFiles(rootDir)) {
        _err.AddError("Error writing HDL files for " + name);
        return false;
      }
      writtenComponents.add(name);

      // Generate this circuit's normal (non-subcircuit, non-inlined) components next
      for (NetlistComponent comp : _circNets.components) {
        if (comp.original.getFactory() instanceof SubcircuitFactory)
          continue;
        HDLSupport g = comp.hdlSupport;
        if (g == null)
          continue;
        String cname = g.getComponentName();
        if (!writtenComponents.contains(cname) && !g.inlined) {
          if (!g.writeHDLFiles(rootDir)) {
            _err.AddError(name+": error writing HDL files for " + comp.original.getFactory());
            return false;
          }
          writtenComponents.add(cname);
        }
      }

      // Recurse for subcircuits last
      for (NetlistComponent subcirc : _circNets.subcircuits) {
        CircuitHDLGenerator g = (CircuitHDLGenerator)subcirc.hdlSupport;
        if (g == null) {
          _err.AddError(name+": missing subcircuit HDL support for " + subcirc.original);
          return false;
        }
        Path subpath = path.extend(subcirc);
        if (!g.writeAllHDLFiles(rootDir, writtenComponents, subpath))
          return false;
      }

      return true;
    } finally {
      _circNets.currentPath = null;
    }
	}

  @Override
  public boolean writeArchitecture(String rootDir) {
    // Instead of using the circuit as defined within logisim, we can substitute
    // an external user-defined VHDL implementation.
    if (circ.getStaticAttributes().getValue(CircuitAttributes.CIRCUIT_IS_VHDL_BOX)) {
      String name = CorrectLabel.getCorrectLabel(circ.getName());
      return FileWriter.CopyArchitecture(
						circ.getStaticAttributes().getValue(CircuitAttributes.CIRCUIT_VHDL_PATH),
            rootDir + subdir, name, _err, _lang);
    } else {
      return super.writeArchitecture(rootDir);
    }
  }

	@Override
	protected void declareNeededComponents(Hdl out) {
    declareNeededComponents(out, _circNets.components);
  }

  private void declareNeededComponents(Hdl out, ArrayList<NetlistComponent> components) {
		HashSet<String> done = new HashSet<>();
		for (NetlistComponent comp: components) {
      HDLSupport g = comp.hdlSupport;
      if (g == null)
        continue;
			String name = g.getComponentName();
			if (done.contains(name) || g.inlined)
        continue;
      ((HDLGenerator)g).generateComponentDeclaration(out);
      done.add(name);
		}
	}
    
  private void generatePortAssignments(Hdl out, NetlistComponent pin, boolean isOutput) {
    Net net = getPortMappingForPin(pin.original);
    if (net == null && isOutput)
      _err.AddSevereWarning("INTERNAL ERROR: Output pin '%s' of circuit '%s' has "
          + " no driving signal.", pin.label(), circ.getName());
    else if (net == null)
      return; // input pin not driving anything, no error
    else if (isOutput)
      out.assign(pin.label(), net.name);
    else
      out.assign(net.name, pin.label());
  }

	@Override
  protected void generateBehavior(Hdl out) {

		// Connect each HDL port to the net connected to the corresponding circuit pin.
    // Note: This is almost exactly the same as getPortMappings(), but
    // creating signal assignments instead of port mappings, and also with
    // different logic for handling unconnected pins.
    for (NetlistComponent pin : _circNets.inpins)
      generatePortAssignments(out, pin, false);
    for (NetlistComponent pin : _circNets.outpins)
      generatePortAssignments(out, pin, true);
    
    generateWiring(out);
    
    // Dynamic clock - DynamicClockHDLGenerator will connected directly to output port

    // Handle normal components and subcircuits.
		HashMap<String, Long> ids = new HashMap<>();
		for (NetlistComponent comp: _circNets.components) {
      HDLSupport g = comp.hdlSupport;
      if (g == null)
        continue;
      if (g.inlined) {
        g.generateInlinedCode(out, comp);
      } else {
        // Path cpath = path.extend(comp);
        String prefix = g.getInstanceNamePrefix();
        long id = ids.getOrDefault(prefix, 0L) + 1L;
        ids.put(prefix, id);
        g.generateComponentInstance(out, id, comp /*, cpath*/);
      }
    }
	}

  private boolean sequential(Net.IndirectSource prev, Net.IndirectSource cur) {
    return prev != null && cur != null && prev.net == cur.net && prev.bit+1 == cur.bit;
  }

  private void emit(Hdl out, Net dest, int b, Net.IndirectSource last, int n) {
    if (last != null && n > 0)
      out.assign(dest.slice(out, b, b-n+1), last.net.slice(out, last.bit, last.bit-n+1));
  }

	private void generateWiring(Hdl out) {
		for (Net net : _circNets.nets) {
      Net.IndirectSource prev = null;
      int w = net.bitWidth();
      int n = 0;
      for (int b = 0; b < w; b++) {
        Net.IndirectSource src = net.getIndirectSourceForBit(b);
        if (!sequential(prev, src)) {
          emit(out, net, b-1, prev, n);
          n = 0;
        }
        prev = src;
        n++;
      }
      emit(out, net, w-1, prev, n);
    }
  }

  @Override
  public boolean requiresUniqueLabel() {
    // Special case: HDL for a circuit does not depend on the specific instance,
    // so the name of the HDL does not contains ${LABEL}. But we still want
    // subcircuits to be labeled for the sake of being able to create unique
    // Path identifiers.
    return true;
  }

  // @Override
  // protected boolean hdlDependsOnCircuitState() { // for NVRAM
  //   for (NetlistComponent comp : _circNets.components) {
  //     HDLSupport g = comp.hdlSupport;
  //     if (g != null && g.hdlDependsOnCircuitState())
  //       return true;
  //   }
  //   return false;
  // }

  // @Override
  // public boolean writeAllHDLThatDependsOn(CircuitState cs, NetlistComponent shadow,
  //     Path path, String rootDir) { // for NVRAM
  //   for (NetlistComponent comp : _circNets.components) {
  //     HDLSupport g = comp.hdlSupport;
  //     if (g == null)
  //       continue;
  //     Path subpath = path.extend(comp);
  //     CircuitState substate = (CircuitState)cs.getData(comp.original);
  //     if (!g.writeAllHDLThatDependsOn(cs, comp, subpath, rootDir))
  //       return true;
  //   }
  // }
}
