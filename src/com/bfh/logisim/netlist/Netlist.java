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

package com.bfh.logisim.netlist;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.bfh.logisim.library.DynamicClock;
import com.bfh.logisim.fpgagui.FPGAReport;
import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.Splitter;
import com.cburch.logisim.circuit.SplitterFactory;
import com.cburch.logisim.circuit.SubcircuitFactory;
import com.cburch.logisim.std.hdl.VhdlEntity;
import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.comp.EndData;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.std.wiring.Clock;
import com.cburch.logisim.std.wiring.Pin;
import com.cburch.logisim.std.wiring.Tunnel;

// Netlist holds info about the connectivity within a circuit based on the
// layout of wires, tunnels, splitters, and any component ports that touch each
// other. Netlist also holds other info about the circuit, for convenience:
// - inputs and outputs (based on Pin components within the circuit)
// - "normal" components (muxes, adders, vhdl entities, etc.)
// - subcircuit components
// - clock components within the circuit
// - dynamic clock control (if that component is present)
// - hidden nets (needed for Button, Tty, Keyboard, subcircuit, and other I/O components)
public class Netlist {

  // Circuit for which we store connectivity info.
	private final Circuit circ;

  // Signal nets, each defining a collection of connected points.
	private final ArrayList<Net> nets = new ArrayList<>();
  private final HashMap<Location, Net> netAt = new HashMap<>();

  // Shadow data for components within this circuit, arranged by factory type.
  private final ArrayList<NetlistComponent> components = new ArrayList<>(); // any factory
	private final ArrayList<NetlistComponent> subcircuits = new ArrayList<>(); // SubCircuitFactory
	private final ArrayList<NetlistComponent> clocks = new ArrayList<>(); // Clock
	private final ArrayList<NetlistComponent> inpins = new ArrayList<>(); // Pin (input)
	private final ArrayList<NetlistComponent> outpins = new ArrayList<>(); // Pin (output)
  private NetlistComponent dynClock; // DynamicClock

  // Reference to the single global clock bus used by all Netlists in the design.
	private final ClockBus clockbus = null;

  // Info about hidden networks, needed by certain I/O-related components.
	private Int3 numHiddenBits;
  
  // Path to one of the instantiations of this circuit. For a circuit that gets
  // instantiated several times, there is one Netlist for the circuit, and the
  // currentPath will rotate between the different instances.
	private Path currentPath;

  // Info about design rule checks.
	private int status;
	public static final int DRC_ERROR = -1;
	public static final int DRC_REQUIRED = 0;
	public static final int DRC_PASSED = 1;

	public Netlist(Circuit c) {
		circ = c;
		clear();
	}

	public void clear() {
		nets.clear();
		netAt.clear();
		components.clear();
		subcircuits.clear();
		clocks.clear();
		inpins.clear();
		outpins.clear();
    dynClock = null;
    if (clockbus != null) {
      clockbus.clear();
      clockbus = null;
    }
		numHiddenBits.clear();
    currentPath = null;
		status = DRC_REQUIRED;
	}

	private void recursiveClear() {
    clear();
		for (Component comp : circ.getNonWires()) {
			if (comp.getFactory() instanceof SubcircuitFactory) {
				SubcircuitFactory fac = (SubcircuitFactory) comp.getFactory();
				fac.getSubcircuit().getNetList().recursiveClear();
			}
		}
	}

  // Primary entry point for performing design-rule-check (DRC) validation on
  // the Netlist for the top-level circuit.
  public boolean validate(FPGAReport err, String lang, char vendor) {
    recursiveClear();

    // Create global hidden clock bus at the top level.
    clockbus = new ClockBus();

    // Recursively validate this Netlist and those of all subcircuits.
    if (!recursiveValidate(err, lang, vendor, new ArrayList<>(), true)) {
      recursiveClear();
      return false;
    }

    // Sanity check for effectively-blank designs at the top level.
    if (inpins.size() + outpins.size() + numHiddenBits.size() == 0) {
      err.AddFatalError("Top-level circuit '%s' has no input pins, output pins, "
          +" or I/O devices, so would produce no visible behavior.", circ.getName());
      recursiveClear();
      return false;
    }

    Path root = new Path(circ);

    // Recursively trace all clock nets to build the global clock bus.
    HashMap<Path, Netlist> netlists = new HashMap<>();
    recursiveEnumerateNetlists(root, netlists);
    recursiveTraceClockNets(root, netlists);

    // Recursively build other hidden nets.
    HashSet<Netlist> visited = new HashSet<>();
    recursiveAssignLocalHiddenNets(visited);
    recursiveAssignGlobalHiddenNets(root, new Int3());

    return true;
  }

  private boolean recursiveValidate(FPGAReport err, String lang, char vendor,
      ArrayList<String> sheets, boolean isTop) {

    // Avoid re-validating when there are multiple instances of this circuit.
    if (status == DRC_PASSED)
      return true;
    else if (status == DRC_ERROR)
      return false;

    // Sanity check for identically named circuits or infinite recursion.
    String name = circ.getName();
    if (sheets.contains(name)) {
			err.AddFatalError("Multiple circuits in your design are named '%s'."
          + " Each circuit must have a unique name.", name);
      status = DRC_ERROR;
      return false;
    }
    sheets.add(name);

    // Perform the actual DRC and cache the result.
    if (!drc(err, lang, vendor, sheets, isTop)) {
      status = DRC_ERROR;
      return false;
    }

    return true;
  }

  private boolean drc(FPGAReport err, String lang, char vendor,
      ArrayList<String> sheets, boolean isTop) {

    String circName = circ.getName();

    // DRC Step 1: Check for presence of tri-state devices or bidirectional
    // ports, neither of which are typically supported for HDL synthesis.
		err.AddInfo("Checking for tri-state drivers or bidirectional ports in circuit '%s'.", circName);
		for (Component comp : circ.getNonWires()) {
      // Note: Splitters and Tunnels can be bidirectional, as those cases are
      // handled as special cases in buildNets().
      if (comp.getFactory() instanceof SplitterFactory)
        continue;
      if (comp.getFactory() instanceof Tunnel) 
        continue;
      if (comp.getFactory().HDLIgnore())
        continue;
			if (comp.getFactory().HasThreeStateDrivers(comp.getAttributeSet()))
        return drcFail(err, comp, "component has tri-state output drivers or is configured "
            "to allow floating outputs, features typically not supported for FPGA synthesis.");
      for (EndData end : comp.getEnds())
        if (end.getWidth() > 0 && (end.isInput() && end.isOutput()))
          return drcFail(err, comp, "component has a bidirectional port, a feature not yet supported.");
    }

    // DRC Step 2: Validate names and labels for a few basic component types.
    if (!CorrectLabel.IsCorrectLabel(circName, lang, "Circuit has illegal name."))
      return DRC_ERROR;
    HashSet<String> pinNames = new HashSet<>();
		for (Component comp : circ.getNonWires()) {
      if (comp.getFactory().HDLIgnore())
        continue; // Text, Probe, and other similarly irrelevant components
      if (comp.getFactory() instanceof Pin) {
				String label = NetlistComponent.labelOf(comp);
				if (!CorrectLabel.IsCorrectLabel(label,
						lang, "Bad label for pin '"+nameOf(comp)+"' in circuit '"+circName+"'", err))
					return DRC_ERROR;
        if (pinNames.contains(label))
          return drcFail(err, comp, "pin has the same label as another pin in same circuit.");
        pinNames.add(label);
      } else if (comp.getFactory() instanceof SubcircuitFactory
          || comp.getFactory() instanceof VhdlEntity) {
        String compName = comp.getFactory().getName();
				if (!CorrectLabel.IsCorrectLabel(compName,
						lang, "Bad name for component '"+compName+"' in circuit '"+circName+"'", err))
					return DRC_ERROR;
			}
    }

    // DRC Step 3: Check connectivity (e.g. splitters, tunnels, wires) and build nets.
		err.AddInfo("Checking wire, tunnel, and splitter connectivity in circuit '%s'.", circName);
		if (!buildNets(err, lang, vendor))
			return DRC_ERROR;
    printNetlistStats(err);

    // DRC Step 4: Create NetlistComponent shadow objects for each Component,
    // and perform sanity checks.
    HDLCTX ctx = new HDLCTX(lang, err, this, null /* attrs */, vendor);
		for (Component comp : circ.getNonWires()) {
      if (comp.getFactory() instanceof SplitterFactory)
        continue;
      if (comp.getFactory() instanceof Tunnel) 
        continue;
      if (comp.getFactory().HDLIgnore())
        continue;
      NetlistComponent shadow = new NetlistComponent(comp, ctx);
      if (shadow.hdlSupport == null)
        return drcFail(err, comp, "component does not support HDL generation.");
      components.add(shadow);
      if (comp.getFactory() instanceof SubcircuitFactory) 
        subcircuits.add(shadow);
      else if (comp.getFactory() instanceof Clock) 
        clocks.add(shadow);
      else if (comp.getFactory() instanceof Pin && comp.getEnd(0).isInput())
        inpins.add(shadow);
      else if (comp.getFactory() instanceof Pin && comp.getEnd(0).isOutput())
        outpins.add(shadow);
      else if (comp.getFactory() instanceof DynamicClock) {
        if (isTop && dynClock == null)
          dynClock = shadow;
        else if (isTop)
          return drcFail(err, comp, "multiple dynamic clock controls found, but at most one allowed.");
        else
          return drcFail(err, comp, "dynamic clock control found in sub-circuit, but this is allowed only in main top-level circuit.");
      }
    }

    // DRC Step 5: Ensure labels are sufficiently unique. Components like LED,
    // DipSwitch, etc., need unique labels because they show up in the mapping
    // gui, or because the HDL they generate is specific to each instance.
    HashMap<String, Component> namedComponents = new HashMap<>();
    for (NetlistComponent shadow : components) {
      Component comp = shadow.getComponent();
      if (comp.getFactory() instanceof Pin)
        continue; // handled above
      if (!shadow.hdlSupport.requiresUniqueLabel())
        continue;
      String label = shadow.label();
      if (label.isEmpty())
        return drcFail(err, comp, "a unique label is required for this component. "
            + "Run annotate, or manually add labels.");
      if (!CorrectLabel.IsCorrectLabel(label,
            lang, "Bad label for component '"+nameOf(comp)+"' in circuit '"+circName+"'", err))
        return DRC_ERROR;
      String name = g.getHDLNameWithinCircuit(circName);
      Component clash = namedComponents.get(name);
      if (clash != null)
        return drcFail(err, comp, "component '%s' has same the same name, "
            + "but labels must be unique within a circuit.", nameOf(clash));
      namedComponents.put(name, comp);
    }

    // DRC Step 6: Recurse to validate subcircuits.
    for (NetlistComponent shadow : subcircuits) {
      Component comp = shadow.getComponent();
      SubcircuitFactory sub = (SubcircuitFactory)comp.getFactory();
      Circuit subcirc = sub.getSubcircuit();
      Netlist subnets = subcirc.getNetList();
      subnets.clockbus = clockbus;
      if (!subnets.recursiveValidate(err, lang, vendor, sheets, false))
        return DRC_ERROR;
    }

		err.AddInfo("Circuit '%s' passed all design rule checks.", circName);
    return true;
	}

  private void printNetlistStats(FPGAReport err) {
    int n = 0, b = 0;
		for (Net net : nets) {
			if (net.isBus())
        b++;
      else
				n++;
    }
    err.AddInfo("Circuit '%s' contains %d signal nets and %d bus nets.", circ.getName(), n, b);
  }

  private String nameOf(Component comp) {
    String name = comp.getFactory().getName();
    String label = comp.getAttributeSet().getValue(StdAttr.LABEL).toString();
    if (label.isEmpty())
      return String.format("'%s' at %s", name, comp.getLocation().toString());
    else
      return String.format("'%s' with label '%s'", name, label);
  }
 
  private boolean drcFail(FPGAReport err, Component comp, String msg, String ...args) {
    String prefix = String.format("Component %s in circuit '%s': ",
        nameOf(comp), circ.getName());
    err.AddSevereError(prefix + String.format(msg, args));
    return false;
  }

  static class Int3 {
    int in, inout, out;
    Int3() { }
    void clear() { in = inout = out = 0; }
    Int3 copy() {
      Int3 o = new Int3();
      o.in = in;
      o.inout = inout;
      o.out = out;
      return o;
    }
    void increment(Int3 amt) {
      in += amt.in;
      inout += amt.inout
      out += amt.out;
    }
    int size() {
      return in + inout + out;
    }
  }

	private void recursiveAssignLocalHiddenNets(HashSet<Netlist> visited) {
    visited.add(this);

    Int3 start = numHiddenBits;
		for (NetlistComponent shadow : subcircuits) {
      Component comp = shadow.GetComponent();
			SubcircuitFactory sub = (SubcircuitFactory)comp.getFactory();
      Circuit subcirc = sub.getSubcircuit();
      Netlist subnets = subcirc.getNetList();

      if (!visited.contains(subnets))
        subnets.recursiveBuildHiddenNets(visited);

      Int3 count = subnets.numHiddenBits;
			shadow.setLocalHiddenPortIndices(start.copy(), count);
      start.increment(count);
    }

		for (NetlistComponent shadow : components) {
			if (shadow.hiddenPort == null)
        continue;
      Int3 count = shadow.hiddenPort.size();
      shadow.setLocalHiddenPortIndices(start.copy(), count);
      start.increment(count);
    }
  }

	private void recursiveAssignGlobalHiddenNets(Path path, Int3 start) {
		for (NetlistComponent shadow : subcircuits) {
      Component comp = shadow.GetComponent();
			SubcircuitFactory sub = (SubcircuitFactory)comp.getFactory();
      Circuit subcirc = sub.getSubcircuit();
      Netlist subnets = subcirc.getNetList();
      Path subpath = path.extend(shadow);

      Int3 count = subnets.numHiddenBits;
      shadow.setGlobalHiddenPortIndices(subpath, start.copy(), count);
      subnets.recursiveAssignGlobalHiddenNets(subpath, start);
    }

		for (NetlistComponent shadow : components) {
			if (shadow.hiddenPort == null)
        continue;
      Path subpath = path.extend(shadow);
      Int3 count = shadow.hiddenPort.size();
      shadow.setGlobalHiddenPortIndices(subpath, start.copy(), count);
    }
  }

  private static class CopperTrace extends HashSet<Location> {
    CopperTrace(Location pt) { add(pt); }
    CopperTrace(Wire w) { add(w.getEndLocation(0)); add(w.getEndLocation(1)); }
  }

	private boolean buildNets(FPGAReport err, String lang, char vendor) {
		LinkedList<CopperTrace> traces = new LinkedList<>();
   
    // Make a CopperTrace for every wire.
    for (Wire w : circ.getWires())
      traces.add(new CopperTrace(w));

    // Make a CopperTrace for every group of tunnels with the same name.
		HashMap<String, CopperTrace> tunnels = new HashMap<>();
    for (Component comp : circ.getNonWires()) {
      if (!(comp.getFactory() instanceof Tunnel))
        continue;
      String name = comp.getAttributeSet().getValue(StdAttr.LABEL);
      Location pt = comp.getEnd(0).getLocation();
      CopperTrace c = tunnels.get(name);
      if (c == null) {
        c = new CopperTrace(pt);
        traces.add(c);
        tunnels.put(name, c);
      } else {
        c.add(pt);
      }
    }

    // Make a CopperTrace for every component output port.
    for (Component comp : circ.getNonWires())
      for (EndData end : comp.getEnds())
        if (end.isOutput())
          traces.add(new CopperTrace(end.getLocation()));

    // Make a Net for each set of touching CopperTraces.
		while (traces.size() != 0) {
      CopperTrace c = traces.pop();
			Net net = new Net(c);
      LinkedList<CopperTrace> workingset = new LinkedList<>();
      workingset.add(c);
      while (!workingset.isEmpty()) {
        c = workingset.pop();
        Iterator<CopperTrace> it = traces.iterator();
        while (it.hasNext()) {
          CopperTrace c2 = it.next();
          if (c2.touches(c)) {
            workingset.add(c2);
            net.add(c2);
            it.remove();
          }
        }
      }
      nets.add(net);
		}

    // Create index mapping Location to Net containing that location.
    for (Net net : nets)
      for (Location pt : net.getPoints())
        netAt.put(pt, net);

    // Annotate each Net with appropriate bit width.
		for (Component comp : circ.getNonWires()) {
      for (EndData end : comp.getEnds()) {
        Location pt = end.getLocation();
        int w = end.getWidth().getWidth();
        if (w == 0)
          continue; // splitters can have zero-width ends, safe to ignore.
        Net net = netAt.get(pt);
        if (net == null)
          continue; // input ports can be unconnected, safe to ignore.
        int nw = net.bitWidth();
        if (nw == 0)
          net.setBitWidth(w);
        else if (nw != w)
          return drcFail(err, comp, "%d-bit port is connected to a %d-bit bus.", w, nw);
      }
    }

    // If a Net has zero width, it must be entirely unconnected (or perhaps
    // connected only to splitter ends with no connections), so remove it.
    for (Net net : nets)
      if (net.bitWidth() == 0)
        for (Location pt : net.getPoints())
          netAt.remove(pt);
    nets.removeIf(net -> net.bitWidth() == 0);

    // Every single-bit signal of every Net should be driven by zero or one
    // component ports and should drive zero or more component ports. First
    // identify all source and sink ports belonging to normal components like
    // Adder or AndGate (anything other than Splitter or Tunnel).
		for (Component comp : circ.getNonWires()) {
      if (comp.getFactory() instanceof SplitterFactory)
        continue;
      if (comp.getFactory() instanceof Tunnel)
        continue;
      if (comp.getFactory().HDLIgnore())
        continue;
      int idx = -1;
      for (EndData end : comp.getEnds()) {
        idx++;
        if (end.getWidth() == 0)
          continue;
        Location pt = end.getLocation();
        Net net = netAt.get(pt);
        if (end.isInput()) {
          net.addSink(comp, idx);
        } else {
          Component c = net.getSourceComponent();
          if (c == null) {
            net.setSourceComponent(comp, end);
          } else {
            if (c != comp)
              return drcFail(err, comp, "component is driving a bus that is also "
                  + "driven by component %s.", nameOf(c));
            int end = net.getSourceEnd();
            if (end != idx)
              return drcFail(err, comp, "port %d and port %d are both "
                  + "driving the same bus.", idx, end);
          }
        }
      }
    }

    // Next identify indirect sources and sinks due to splitters. End 0 of each
    // splitter is the "combined end", and will be connected to some Net with
    // matching width. Each bit of the combined end maps to one or none of the
    // other "split ends". Each split end can be the target for zero, one, or
    // multiple bits from the combined end. Example 5-bit, 4-way Splitter:
    //
    // End0{bit4,bit3,bit2,bit1,bit0}  ====\-- End1{bit4,bit0}
    //                                     |-- End2{bit1}
    //                                     |-- End3{(empty)}
    //                                     |-- End4{bit3}
    // Note: bit2 of the combined end does not map to any of the split ends, and
    // End3 is not the target of any bits from the combined end.
    //
    // If a bit is mapped to a split end, and the bit on one side (either the
    // combined or split end) already has a known source, then that source
    // becomes the source for the other side's bit (either the split or combined
    // end).
    //
    // If a bit is mapped to a split end, and the bit on *both* sides already
    // have a known source, then those sources better be identical otherwise we
    // have found a short circuit.
    //
    // If a bit is not mapped to a split end, then we can ignore the splitter
    // entirely for that bit, as it won't affect the bit.
   
    // Annotate each Net with the list of splitters the Net touches.
    for (Component comp : circ.getNonWires()) {
			if (comp.getFactory() instanceof SplitterFactory) {
        int idx = 0;
        for (EndData end : comp.getEnds()) {
          Net net = netAt.get(end.getLocation());
          net.splitters.add(new Net.SplitterPort((Splitter)comp, idx++));
        }
      }
    }

    // Propagate sources from Net, through Splitter, to other Nets.
    LinkedList<Net> workingset = new LinkedList<>();
    for (Net net : nets)
      if (net.getSourceComponent() != null)
        workingset.add(net);
    while (!workingset.isEmpty()) {
      Net net = workingset.pop();
      for (Net.SplitterPort sp : net.splitters) {
        int n = sp.splitter.getEnds().size();
        byte[] dest = sp.splitter.getEndpoints();
        if (sp.end == 0) {
          // net is the big combined end of splitter, and drives every bit of
          // small split ends (except those small split ends with width = 0,
          // but that gets ignored in the loop here, and except for bits in
          // net that aren't actually driven).
          for (int split = 1; split < n; i++) {
            Net small = netAt.get(sp.splitter.getEnd(split).getLocation());
            int s = -1;
            for (int b = 0; b < dest.length; b++) {
              if (dest[b] != split)
                continue; // net[b] does not map to this split end
              s++;
              // net[b] drives small[s]
              Net.Source src = net.getSourceForBit(b);
              if (src == null)
                continue; // actually, net[b] isn't driven, so nevermind
              Net.Source sink = small.getSourceForBit(s);
              if (sink == null) {
                small.setSourceForBit(s, net, b);
                workingset.add(small);
              } else if (!sink.equals(src)) {
                return drcFail(err, sp.splitter,
                    "splitter bit %d is being driven by both "
                    + " %s (port %d bit %d) and %s (port %d bit %d).",
                    b,
                    nameOf(src.comp), src.end, src.bit,
                    nameOf(sink.comp), sink.end, sink.bit);
              }
            } // for each bit in splitter
          } // for each small split end
        } else {
          // net is the small split end of splitter, and every bit of net is
          // being driven by some bit of big combined end (unless this split
          // end has width = 0, but that is filtered out above).
          Net big = netAt.get(sp.splitter.getEnd(0).getLocation());
          int s = -1;
          for (int b = 0; b < dest.length; b++) {
            if (dest[b] != sp.end)
              continue; // big[b] does not map to this small split end
            s++;
            // big[b] is driven by net[s]
            Net.Source src = net.getSourceForBit(s);
            if (src == null)
              continue; // actually, net[s] isn't driven, so nevermind
            Net.Source sink = big.getSourceForBit(b);
            if (sink == null) {
              big.setSourceForBit(b, net, s);
              workingset.add(big);
            } else if (!sink.equals(src)) {
              return drcFail(err, sp.splitter,
                  "splitter bit %d is being driven by both %s (port %d bit %d) "
                  + " and %s (port %d bit %d).",
                  b,
                  nameOf(src.comp), src.end, src.bit,
                  nameOf(sink.comp), sink.end, sink.bit);
            }
          } // for each bit in splitter
        }
      } // for each splitter touching this Net
    }

    // For each Net that has direct sinks, ensure that every bit of the Net is
    // driven by something, either directly (by a component on the same Net) or
    // indirectly (through one or more splitters and eventually by some
    // component on some other Net).
    for (Net net : nets) {
      if (!net.getSinks().isEmpty())
        continue; // no direct sinks, ignore
      if (net.getSourceComponent() != null)
        continue; // net has a direct source, done
      for (int b = 0; b < net.bitWidth()) {
        if (net.getSourceForBit(b) == null) {
          Net.DirectSink sink = net.getSinks().get(0);
          return drcFail(err, sink.comp,
              "component port %d is connected to a %d-bit bus with some "
              + "undriven signals (e.g. bit %d)",
              sink.end, net.bitWidth(), b);
        }
      }
    }

    // If there are nets with all bits undriven, they could removed now. But we
    // don't bother, since these will cause synthesis warnings but not errors.
    return true;
  }

  private void recursiveEnumerateNetlists(Path path, HashMap<Path, Netlist> netlists) {
    netlists.put(path, this);
		for (NetlistComponent shadow : subcircuits) {
      Component comp = shadow.getComponent();
      SubcircuitFactory sub = (SubcircuitFactory)comp.getFactory();
      Circuit subcirc = sub.getSubcircuit();
      Netlist subnets = subcirc.getNetList();
      Path subpath = path.extend(shadow);
      subnets.recursiveEnumerateNetlists(subpath, netlists);
    }
  }

	private void recursiveTraceClockNets(Path path, HashMap<Path, Netlist> netlists) {
    traceClockNets(path, netlists);
		for (NetlistComponent shadow : subcircuits) {
      Component comp = shadow.getComponent();
      SubcircuitFactory sub = (SubcircuitFactory)comp.getFactory();
      Circuit subcirc = sub.getSubcircuit();
      Netlist subnets = subcirc.getNetList();
      Path subpath = path.extend(shadow);
      subnets.recursiveTraceClockNets(subpath, netlists);
    }
  }

  // Associate id for each clock source with all downstream signals.
	private void traceClockNets(Path path, HashMap<Path, Netlist> netlists) {
    for (NetlistComponent shadow : clocks) {
      Component clk = shadow.getComponent();
      int clkid = clockbus.id(clk);
      Net clknet = netAt.get(clk.getEnd(0).getLocation());
      traceClockNet(clkid, path, clknet, 0, netlists);
    }
  }

  // Associate the signal Path:net:bit, and all downstream signals, with the
  // given clock id. We trace the signal down into subcircuits, up through
  // output pins to parent circuits, and across through tunnels (for free) and
  // splitters (below).
  private void traceClockNet(int clkid, Net net, int bit, Path path, HashMap<Path, Netlist> netlists) {

    // Start by marking Path:net:bit.
    if (!clockbus.associate(clkid, path, net, bit))
      return; // already marked this net and all downstream signals.

    // Then find and mark all downstream signals from net:bit.

    // Look for subcircuits and follow signal down into them.
    for (Net.DirectSink sink : net.getSinks()) {
      if (!(sink.comp.getFactory() instanceof SubcircuitFactory))
        continue;
      SubcircuitFactory sub = (SubcircuitFactory)sink.comp.getFactory();
      Circuit subcirc = sub.getSubcircuit();
      Netlist subnets = subcirc.getNetList();
      Path subpath = path.extend(shadow);
      NetlistComponent shadow = shadowForSubcirc(subpath);
      // within child, find the Pin that corresponds to this end
      CircuitHDLGenerator g = (CircuitHDLGenerator)shadow.hdlSupport;
      Net childNet = g.getPortMappingForEnd(sink.end);
      subnets.traceClockNet(clkid, childNet, bit, subpath, netlists);
    }

    // If in a subcircuit, look for output pins and follow them up to parent circuit.
    if (!path.equals(Path.ROOT)) {
      for (Net.DirectSink sink : net.getSinks()) {
        if (!(sink.comp.getFactory() instanceof Pin))
          continue;
        End end = sink.comp.getEnd(0);
        if (!end.isOutput())
          continue;
        Path parentpath = path.parent();
        Netlist parent = netlists.get(parentpath);
        // within parent, find the component that corresponds to this subcircuit
        NetlistComponent shadow = parent.shadowForSubcirc(path);
        // within that component, find the Net connecting to this Pin
        CircuitHDLGenerator g = (CircuitHDLGenerator)shadow.hdlSupport;
        Net parentNet = g.getPortMappingForPin(sink.comp);
        if (parentNet != null)
          parent.traceClockNet(clkid, parentNet, bit, parentpath, netlists);
      }
    }

    // Follow signal through splitters.
    for (Net.SplitterPort sp : net.splitters) {
      int n = sp.splitter.getEnds().size();
      byte[] dest = sp.splitter.getEndpoints();
      if (sp.end == 0) { // net goes into big combined end
        int end2 = dest[bit];
        if (end2 < 0)
          continue; // net:bit doesn't map to any of the small split ends
        Net net2 = netAt.get(sp.splitter.getEnd(end2).getLocation());
        int bit2 = 0;
        for (int b = 0; b < bit; b++) {
          if (dest[b] == end2)
              bit2++; // net:bit signal comes after any lower numbered net:b for same end
        }
        traceClockNet(clkid, net2, bit2, path, netlists);
      } else { // net goes into small split end
        Net net2 = netAt.get(sp.splitter.getEnd(0).getLocation());
        int bit1 = 0;
        for (int bit2 = 0; bit2 < dest.length; bit2++) {
          if (dest[bit2] == sp.end && bit1 == bit) {
            traceClockNet(clkid, net2, bit2, path, netlists);
            break;
          } else if (dest[bit2) == sp.end) {
            bit1++;
          }
        }
      }
    }

	}

  private NetlistComponent shadowForSubcirc(Path childPath) {
    String label = child.tail();
		for (NetlistComponent shadow : subcircuits) {
      if (shadow.label().equals(label))
        return shadow;
    }
    return null;
  }

	// public ArrayList<NetlistComponent> GetInputPorts() { return inpins; }
	// public ArrayList<NetlistComponent> GetInOutPorts() { return MyInOutPorts; }
	// public ArrayList<NetlistComponent> GetOutputPorts() { return outpins; }

  // FIXME: rewrite this main entry point...
	public Map<ArrayList<String>, NetlistComponent> GetMappableResources(
			ArrayList<String> Hierarchy, boolean toplevel) {
		Map<ArrayList<String>, NetlistComponent> Components = new HashMap<ArrayList<String>, NetlistComponent>();
		/* First we search through my sub-circuits and add those IO components */
		for (NetlistComponent comp : subcircuits) {
			SubcircuitFactory sub = (SubcircuitFactory) comp.GetComponent()
					.getFactory();
			ArrayList<String> MyHierarchyName = new ArrayList<String>();
			MyHierarchyName.addAll(Hierarchy);
			MyHierarchyName.add(CorrectLabel.getCorrectLabel(comp
					.GetComponent().getAttributeSet().getValue(StdAttr.LABEL)
					.toString()));
			Components.putAll(sub.getSubcircuit().getNetList()
					.GetMappableResources(MyHierarchyName, false));
		}
		/* Now we search for all local IO components */
		for (NetlistComponent comp : components) {
			if (comp.hiddenPort != null) {
				ArrayList<String> MyHierarchyName = new ArrayList<String>();
				MyHierarchyName.addAll(Hierarchy);
				MyHierarchyName.add(CorrectLabel.getCorrectLabel(comp
						.GetComponent().getAttributeSet()
						.getValue(StdAttr.LABEL).toString()));
				Components.put(MyHierarchyName, comp);
			}
		}
		/* On the toplevel we have to add the pins */
		if (toplevel) {
			for (NetlistComponent comp : inpins) {
				ArrayList<String> MyHierarchyName = new ArrayList<String>();
				MyHierarchyName.addAll(Hierarchy);
				MyHierarchyName.add(CorrectLabel.getCorrectLabel(comp
						.GetComponent().getAttributeSet()
						.getValue(StdAttr.LABEL).toString()));
				Components.put(MyHierarchyName, comp);
			}
			for (NetlistComponent comp : MyInOutPorts) {
				ArrayList<String> MyHierarchyName = new ArrayList<String>();
				MyHierarchyName.addAll(Hierarchy);
				MyHierarchyName.add(CorrectLabel.getCorrectLabel(comp
						.GetComponent().getAttributeSet()
						.getValue(StdAttr.LABEL).toString()));
				Components.put(MyHierarchyName, comp);
			}
			for (NetlistComponent comp : outpins) {
				ArrayList<String> MyHierarchyName = new ArrayList<String>();
				MyHierarchyName.addAll(Hierarchy);
				MyHierarchyName.add(CorrectLabel.getCorrectLabel(comp
						.GetComponent().getAttributeSet()
						.getValue(StdAttr.LABEL).toString()));
				Components.put(MyHierarchyName, comp);
			}
		}
		return Components;
	}

  ///////////////// FIXME: everything below here is a mess /////////////////////
  
  static final int BUS_UNCONNECTED = 0;     // all bits unconnected
  static final int BUS_SIMPLYCONNECTED = 1; // connected to contiguous slice of a single named bus
  static final int BUS_MULTICONNECTED = 2;  // all bits connected, but non-contiguous or multi-bus
  static final int BUS_MIXCONNECTED = -1;   // mix of connected and unconnected bits
  public int busConnectionStatus(NetlistComponent.PortConnection end) {
		int n = end.width;
		Net net0 = end.connections.get(0).net;
		byte idx = net0 == null ? -1 : end.connections.get(0).bit;
		for (int i = 1; i < n; i++) {
      Net neti = end.connections.get(i).net;
      if ((net0 == null) != (neti == null))
        return BUS_MIXCONNECTED: // This bit is connected when other wasn't, or vice versa
			if (net0 != neti)
				return BUS_MULTICONNECTED; // This bit is connected to different bus
			if (net0 != null && (idx + i) != end.connections.get(i).bit)
        return BUS_MULTICONNECTED; // This bit is connected to same bus, but indexes are not sequential
		}
    return net0 == null ? BUS_UNCONNECTED : BUS_SIMPLYCONNECTED;
	}
	

  // TODO here
  // Determine the single-bit HDL signal name that is connected to the given
  // "end" (aka component port). If the end is not connected to a signal, then
  // null is returned.
  // xxx either and hdl "open" is returned (for outputs) or the given default value
  // xxx is returned instead (for inputs). But, if the default is null and the end
  // xxx is an input, null is returned instead.
  private String signalForEndBit(NetlistComponent.PortConnection end, int bit, /*Boolean defaultValue,*/ Hdl hdl) {
    NetlistComponent.ConnectionPoint solderPoint = end.connections.get(bit);
    Net net = solderPoint.net;
    if (net == null) { // unconnected net
      // if (end.isOutput) {
      //   return hdl.unconnected;
      // } else if (defaultValue == null) {
      //   hdl.err.AddFatalError("INTERNAL ERROR: Invalid component end/port.");
      //   return "???";
      // } else {
      //   return hdl.bit(defaultValue);
      // }
      return null;
    } else if (net.BitWidth() == 1) { // connected to a single-bit net
      return "s_LOGISIM_NET_" + this.GetNetId(net);
    } else { // connected to one bit of a multi-bit bus
      return String.format("S_LOGISIM_BUS_%d"+hdl.idx,
          this.GetNetId(net),
          solderPoint.bit);
    }
  }

	public Integer GetNetId(Net selectedNet) {
		return nets.indexOf(selectedNet);
	}


  // Determine the single-bit HDL signal name that is connected to one of the
  // given component's ports (aka "ends"). If the end is not connected to a
  // signal, then either an HDL "open" is returned (for outputs) or the given
  // default value is returned instead (for inputs). But, if the default is null
  // and the end is an input, a fatal error is posted.
	public String signalForEnd1(NetlistComponent comp, int endIdx, Boolean defaultValue, Hdl hdl) {
    return signalForEndBit(comp, endIdx, 0, defaultValue, hdl);
  }

	public String signalForEndBit(NetlistComponent comp, int endIdx, int bitIdx, Boolean defaultValue, Hdl hdl) {
    if (endIdx < 0 || endIdx >= comp.ports.size()) {
      if (defaultValue == null) {
        hdl.err.AddFatalError("INTERNAL ERROR: Invalid end/port '%d' for component '%s'", endIdx, comp);
        return "???";
      }
      // In some HDLGenerator subclasses, missing pins are sometimes expected
      // (e.g. an enable pin that is configured to be invisible). Returning the
      // default here allows those cases to work.
      return hdl.bit(defaultValue);
    }

    NetlistComponent.PortConnection end = comp.ports.get(endIdx);
    int n = end.width;
    if (n != 1) {
      hdl.err.AddFatalError("INTERNAL ERROR: Unexpected %d-bit end/port '%d' for component '%s'", n, endIdx, comp);
      return "???";
    }

    String s = signalForEndBit(end, bitIdx, hdl);
    if (s != null) {
      return s;
    } else if (end.isOutput) {
      return hdl.unconnected;
    } else if (defaultValue == null) {
      hdl.err.AddFatalError("INTERNAL ERROR: Unexpected unconnected end/port '%d' for component '%s'", endIdx, comp);
      return "???";
    } else {
      return hdl.bit(defaultValue);
    }

	}

  // Assuming isConnectedToSingleNamedBus(end)), returns the name (or slice) of
  // the bus signal this end is connected to, for example:
  //    VHDL:    s_LOGISIM_BUS_27(31 downto 0)
  //    Verilog: s_LOGISIM_BUS_27[31:0]
  public String signalForEndBus(NetlistComponent.PortConnection end, int bitHi, int bitLo, Hdl hdl) {
    Net net = end.getConnection((byte) bitLo).net;
    // if (net == null) {
    //   if (isOutput) {
    //     return hdl.unconnected;
    //   } else if (defaultValue == null) {
    //     hdl.err.AddFatalError("INTERNAL ERROR: Unexpected unconnected net to end/port '%d' for component '%s'", endIdx, comp);
    //     return "???";
    //   } else {
    //     int n = bitHi - bitLo + 1;
    //     return defaultValue ? hdl.ones(n) : hdl.zeros(n);
    //   }
    // }
    byte hi = end.connections.get(bitHi).bit;
    byte lo = end.connections.get(bitLo).bit;
    return String.format("s_LOGISIM_BUS_%d"+hdl.range, this.GetNetId(net), hi, lo);
  } 

  // TODO here - i don't like this interface...
  // Determine the HDL signal names (or name) that are connected to one of the
  // given component's ports (aka "ends"). If the end is not connected to a
  // signal, then given default value is returned instead. Or, if the default is
  // null, a fatal error is posted instead. For a 1-bit signal, a single mapping
  // from name to signal is returned. For a multi-bit signal, one or more
  // mappings are returned, as need.
	// public Map<String, String> signalForEnd(String portName,
  //     NetlistComponent comp, int endIdx, Boolean defaultValue, Hdl hdl) {

	// 	Map<String, String> result = new HashMap<String, String>();
  //   if (endIdx < 0 || endIdx >= comp.ports.size()) {
  //     hdl.err.AddFatalError("INTERNAL ERROR: Invalid end/port '%d' for component '%s'", endIdx, comp);
  //     return result;
  //   }

  //   NetlistComponent.PortConnection end = comp.ports.get(endIdx);
  //   int n = end.width;

  //   if (n == 1) {
  //     // example: somePort => s_LOGISIM_NET_5;
  //     result.put(portName, signalForEnd1(comp, endIdx, defaultValue, hdl));
  //     return result;
  //   }

	// 	boolean isOutput = end.isOutput;

  //   // boolean foundConnection = false;
  //   // for (int i = 0; i < n && !foundConnection; i++)
  //   //   foundConnection |= end.connections.get(i).net != null;

  // TODO here - i don't like this interface...
  // Determine the single-bit HDL signal name that is connected to a given bit
  // of the given component's ports (aka "ends"). If the end is not connected to
  // a signal, then either an HDL "open" is returned (for outputs) or the given
  // default value is returned instead (for inputs). But, if the default is null
  // and the end is an input, a fatal error is posted.
  public String signalForEndBit(NetlistComponent.PortConnection end, int bit, Boolean defaultValue, Hdl hdl) {
    String s = signalForEndBit(end, bit, hdl);
    if (s != null) {
      return s;
    } else if (isOutput) {
      return hdl.unconnected;
    } else if (defaultValue == null) {
      hdl.err.AddFatalError("INTERNAL ERROR: Unexpected unconnected bit %d of %d in end/port '%d' for component '%s'", i, n, endIdx, comp);
      return "???";
    } else {
      return hdl.bit(defaultValue);
    }
  }

}
