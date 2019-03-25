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

import com.bfh.logisim.gui.FPGAReport;
import com.bfh.logisim.netlist.CorrectLabel;
import com.bfh.logisim.netlist.Netlist;
import com.bfh.logisim.netlist.NetlistComponent;
import com.bfh.logisim.netlist.Path;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.AttributeSets;
import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.hdl.Hdl;
import com.cburch.logisim.instance.StdAttr;

public abstract class HDLSupport {

  // Parameters used for one specific component or (sub)circuit within a
  // generate-synthesis-download effort.
  public static class ComponentContext extends Netlist.Context {
    public final Netlist nets;
    public final AttributeSet attrs;
    public ComponentContext(Netlist.Context ctx /* for entire effort */,
        Netlist nets /* for circuit containing this component, if any */,
        AttributeSet attrs /* for this component, if any */) {
      super(ctx);
      this.nets = nets;
      this.attrs = attrs != null ? attrs : AttributeSets.EMPTY;
    }
  }

  public final ComponentContext ctx;
  public final String _projectName; // context - fixme
  // Name for HDL module (i.e. base name of HDL file for generated components,
  // and the GUI display name for both generated and inlined components). Must
  // be unique and distinct for each variation of generated HDL, e.g. "BitAdder"
  // (for a 1-bit HDL version using std_logic) versus "BusAdder" (for a
  // parameterized N-bit version using std_logic_vector). In some cases, this is
  // even globally unique (distinct per-circuit and per-instance), when the VHDL
  // essentially can't be shared between instances like for PLA, Rom, and
  // Non-Volatile Ram components.
  public final String hdlComponentName; // context - fixme
  public final String _lang; // context - fixme
  public final FPGAReport _err; // context - fixme
  public final Netlist _nets; // context - fixme // signals of the circuit in
  // which this component is embeded. For CircuitHDLGeneratorComponent, this is
  // the signals for the *parent* circuit (or null, if it is the top-level
  // circuit), not the signals within this subcircuit.
  public final AttributeSet _attrs; // context - fixme
  public final char _vendor; // context - fixme
  public final boolean inlined;
  public final Hdl _hdl;

  protected HDLSupport(ComponentContext ctx,
      String hdlComponentNameTemplate, boolean inlined) {
    this.ctx = ctx;
    this._projectName = ctx.err.getProjectName();
    this._lang = ctx.lang;
    this._err = ctx.err;
    this._nets = ctx.nets; // sometimes null, i.e. for top level circuit and also for quick checks
    this._attrs = ctx.attrs; // empty for Circuit, Ticker, maybe others?
    this._vendor = ctx.vendor;
    this.inlined = inlined;
    this.hdlComponentName = deriveHDLNameWithinCircuit(hdlComponentNameTemplate,
        _nets == null ? null : _nets.circ.getName());
    this._hdl = new Hdl(_lang, _err);
  }

  // Return the component name.
  public final String getComponentName() { return hdlComponentName; }

  // For non-inlined HDLGenerator classes.
  public boolean writeHDLFiles(String rootDir) { return true; }
	protected void generateComponentDeclaration(Hdl out) { }
	protected void generateComponentInstance(Hdl out, long id, NetlistComponent comp/*, Path path*/) { }
	protected String getInstanceNamePrefix() { return null; }
  // protected boolean hdlDependsOnCircuitState() { return false; } // for NVRAM
  // public boolean writeAllHDLThatDependsOn(CircuitState cs, NetlistComponent comp,
  //     Path path, String rootDir) { return true; } // for NVRAM

  // For HDLInliner classes.
	protected void generateInlinedCode(Hdl out, NetlistComponent comp) { }


  // Helpers for subclasses...

  protected int stdWidth() {
    return _attrs.getValueOrElse(StdAttr.WIDTH, BitWidth.ONE).getWidth();
  }

  protected boolean isBus() {
    return stdWidth() > 1;
  }

  protected boolean edgeTriggered() {
    return _attrs.containsAttribute(StdAttr.EDGE_TRIGGER)
        || (_attrs.getValue(StdAttr.TRIGGER) == StdAttr.TRIG_FALLING)
        || (_attrs.getValue(StdAttr.TRIGGER) == StdAttr.TRIG_RISING);
  }

  // Return a suitable HDL name for this component, e.g. "BitAdder" or
  // "BusAdder". This becomes the name of the vhdl/verilog file, and becomes
  // the name of the vhdl/verilog entity for this component. The name must be
  // unique for each different generated vhdl/verilog code. If any attributes
  // are used to customize the generated vhdl/verilog code, that attribute must
  // also be used as part of the HDL name. 
  //
  // As a convenience, some simple string replacements are made:
  //   ${CIRCUIT} - replaced with name of circuit containing component
  //   ${LABEL}   - replaced with StdAttr.LABEL
  //   ${WIDTH}   - replaced with StdAttr.WIDTH
  //   ${BUS}     - replaced with "Bit" (StdAttr.WIDTH == 1) or "Bus" (otherwise)
  //   ${TRIGGER} - replaced with "LevelSensitive", "EdgeTriggered", or "Asynchronous"
  //                depending on StdAttr.TRIGGER and/or StdAttr.EDGE_TRIGGER.
  //
  // Note: For ROM components, the generated HDL code depends on the contents of
  // the memory, which is too large of an attribute for getComponentName() to
  // simply include in the name, as is done for other HDL-relevant attributes.
  // We could include a concise unique hash, perhaps. But instead, we require
  // each ROM to have a non-zero label unique within the circuit, and also have
  // getComponentName() in this case produce a name that is a function of both
  // the circuit name (which is globally unique) and the label.
  private String deriveHDLNameWithinCircuit(String nameTemplate, String circuitName) {
    String s = nameTemplate;
    int w = stdWidth();
    if (s.contains("${CIRCUIT}") && circuitName == null)
      throw new IllegalArgumentException("%s can't appear in top-level circuit");
    if (s.contains("${CIRCUIT}"))
        s = s.replace("${CIRCUIT}", circuitName);
    if (s.contains("${WIDTH}"))
        s = s.replace("${WIDTH}", ""+w);
    if (s.contains("${BUS}"))
        s = s.replace("${BUS}", w == 1 ? "Bit" : "Bus");
    if (s.contains("${TRIGGER}")) {
      if (edgeTriggered())
        s = s.replace("${TRIGGER}", "EdgeTriggered");
      else if (_attrs.containsAttribute(StdAttr.TRIGGER))
        s = s.replace("${TRIGGER}", "LevelSensitive");
      else
        s = s.replace("${TRIGGER}", "Asynchronous");
    }
    if (s.contains("${LABEL}")) {
      String label = _attrs.getValueOrElse(StdAttr.LABEL, "");
      if (label.isEmpty()) {
        if (_err != null)
          _err.AddSevereWarning("Missing a required label for component within circuit \"%s\". "
              + " Name template is: %s.", circuitName, s);
        label = "ANONYMOUS";
      }
      s = s.replace("${LABEL}", label);
    }
    return CorrectLabel.getCorrectLabel(s);
  }

  // For some components, the name returned by getComponentName() depends on a
  // (preferable friendly) label that is unique within the circuit, because it
  // shows up in the component-mapping GUI dialog where the user assigns FPGA
  // resources. This is the case for Pin, PortIO, LED, BUtton, and similar
  // components. These components should include "${LABEL}" in their name. ROM
  // and volatile RAM components also do the same (see note above). Also,
  // Subcircuit and VhdlEntity need labels for the same reason, and override
  // this method to return true.
  public boolean requiresUniqueLabel() {
    return hdlComponentName.contains("${LABEL}");
  }
  
  // Some components can have hidden connections to FPGA board resource, e.g. an
  // LED component has a regular logisim input, but it also has a hidden FPGA
  // output that needs to routed up to the top-level HDL circuit and eventually
  // be connected to an FPGA "LED" or "Pin" resource. Similarly, a Button
  // component has a regular logisim output, but also a separate hidden FPGA
  // input that gets routed up to the top level and can be connected to an FPGA
  // "Button", "DipSwitch", "Pin", or other compatable FPGA resource. The
  // hiddenPorts object, if not null, holds info about what type of FPGA
  // resource is most suitable, alternate resource types, how many in/out/inout
  // pins are involved, names for the signals, etc.
  protected HiddenPort hiddenPort = null;
  public HiddenPort hiddenPort() { return hiddenPort; }

}
