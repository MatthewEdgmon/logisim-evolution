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
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import com.bfh.logisim.designrulecheck.ConnectionEnd;
import com.bfh.logisim.designrulecheck.ConnectionPoint;
import com.bfh.logisim.designrulecheck.CorrectLabel;
import com.bfh.logisim.designrulecheck.Net;
import com.bfh.logisim.designrulecheck.Netlist;
import com.bfh.logisim.designrulecheck.NetlistComponent;
import com.bfh.logisim.fpgaboardeditor.FPGAClass;
import com.bfh.logisim.fpgagui.FPGAReport;
import com.bfh.logisim.fpgagui.MappableResourcesContainer;
import com.bfh.logisim.library.DynamicClock;
import com.bfh.logisim.settings.Settings;
import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitAttributes;
import com.cburch.logisim.circuit.SubcircuitFactory;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.hdl.Hdl;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.std.io.Keyboard;
import com.cburch.logisim.std.io.PortIO;
import com.cburch.logisim.std.io.Tty;
import com.cburch.logisim.std.wiring.ClockHDLGeneratorFactory;

public class CircuitHDLGeneratorFactory extends AbstractHDLGeneratorFactory {

	private Circuit MyCircuit;

	public CircuitHDLGeneratorFactory(String lang, FPGAReport err, Circuit source, Netlist nets) {
    super(lang, err, nets);
		MyCircuit = source;
	}

  public boolean GenerateAllHDLDescriptions(String WorkingDir) {
		if (!WorkingDir.endsWith(File.separator))
			WorkingDir += File.separator;
    return GenerateAllHDLDescriptions(WorkingDir, new HashSet<String>(), new ArrayList<String>());
  }

	private boolean GenerateAllHDLDescriptions(String WorkingDir,
      Set<String> HandledComponents, ArrayList<String> Hierarchy) {
		if (MyCircuit == null) {
			return false;
		}
		Netlist MyNetList = MyCircuit.getNetList();
		if (MyNetList == null) {
			return false;
		}
		MyNetList.SetCurrentHierarchyLevel(Hierarchy);
		/* First we handle the normal components */
		for (NetlistComponent ThisComponent : MyNetList.GetNormalComponents()) {
			String ComponentName = ThisComponent.GetComponent().getFactory()
					.getHDLName(ThisComponent.GetComponent().getAttributeSet());
			if (!HandledComponents.contains(ComponentName)) {
				HDLGeneratorFactory Worker = ThisComponent
						.GetComponent()
						.getFactory()
						.getHDLGenerator(_lang, _err,
								ThisComponent.GetComponent().getAttributeSet(),
								FPGAClass.VendorUnknown);
				if (Worker == null) {
					_err.AddFatalError("INTERNAL ERROR: Cannot find the VHDL generator factory for component "
							+ ComponentName);
					return false;
				}
        if (!(Worker instanceof AbstractHDLGeneratorFactory))
          throw new IllegalStateException();
        ((AbstractHDLGeneratorFactory)Worker).initHDLGen(MyNetList); /* stateful hdl gen */
				if (!Worker.IsOnlyInlined(/*_lang*/)) {
					if (!WriteEntity(
							WorkingDir + Worker.GetRelativeDirectory(/*_lang*/),
							Worker.GetEntity(/*MyNetList,*/ ThisComponent
									.GetComponent().getAttributeSet(),
									ComponentName /*, _err, _lang*/),
							ComponentName, _err, _lang)) {
						return false;
					}
					Map<String, ArrayList<String>> memInitData =
						Worker.GetMemInitData(ThisComponent.GetComponent().getAttributeSet());
					Map<String, File> memInitFiles = null;
					if (memInitData != null) {
						memInitFiles = new HashMap<String, File>();
						for (String Mem : memInitData.keySet()) {
							ArrayList<String> initData = memInitData.get(Mem);
							File mif = WriteMemInitFile(
									WorkingDir + Worker.GetRelativeDirectory(/*_lang*/),
									initData,
									ComponentName, Mem, _err, _lang);
							if (mif == null)
								return false;
							memInitFiles.put(Mem, mif);
						}
					}
					if (!WriteArchitecture(
							WorkingDir + Worker.GetRelativeDirectory(/*_lang*/),
							Worker.GetArchitecture(/* MyNetList,*/
                ThisComponent
									.GetComponent().getAttributeSet(),
									memInitFiles,
									ComponentName /*, Reporter, HDLType*/),
							ComponentName, _err, _lang)) {
						return false;
					}
				}
				HandledComponents.add(ComponentName);
			}
		}
		/* Now we go down the hierarchy to get all other components */
		for (NetlistComponent ThisCircuit : MyNetList.GetSubCircuits()) {
			CircuitHDLGeneratorFactory Worker = (CircuitHDLGeneratorFactory)ThisCircuit
					.GetComponent()
					.getFactory()
					.getHDLGenerator(_lang, _err,
							ThisCircuit.GetComponent().getAttributeSet(),
							FPGAClass.VendorUnknown);
			if (Worker == null) {
				_err.AddFatalError("INTERNAL ERROR: Unable to get a subcircuit VHDL generator for '"
						+ ThisCircuit.GetComponent().getFactory().getName()
						+ "'");
				return false;
			}
			Hierarchy.add(CorrectLabel.getCorrectLabel(ThisCircuit
					.GetComponent().getAttributeSet().getValue(StdAttr.LABEL)));
			if (!Worker.GenerateAllHDLDescriptions(WorkingDir, HandledComponents, Hierarchy)) {
				return false;
			}
			Hierarchy.remove(Hierarchy.size() - 1);
		}
		/* I also have to generate myself */
		String ComponentName = CorrectLabel
				.getCorrectLabel(MyCircuit.getName());
		if (!HandledComponents.contains(ComponentName)) {
			if (!WriteEntity(
					WorkingDir + GetRelativeDirectory(/*_lang*/),
					GetEntityWithNetlist(MyNetList, null, ComponentName),
					ComponentName, _err, _lang)) {
				return false;
			}

			// is the current circuit an 'empty vhdl box' ?
			if (MyCircuit.getStaticAttributes().getValue(
					CircuitAttributes.CIRCUIT_IS_VHDL_BOX)) {
				if (!FileWriter.CopyArchitecture(
						MyCircuit.getStaticAttributes().getValue(
								CircuitAttributes.CIRCUIT_VHDL_PATH), WorkingDir
								+ GetRelativeDirectory(/*_lang*/), ComponentName,
						_err, _lang)) {
					return false;
				}
			} else {
				if (!WriteArchitecture(
						WorkingDir + GetRelativeDirectory(/*_lang*/),
						this.GetArchitectureWithNetlist(MyNetList, /* stateful hdl gen */
                /* We don't use _nets here, b/c that is the netlist for our
                 * parent (or null at the top level, b/c FPGACommanderGui
                 * doesn't configure it), which probably isn't what we want
                 * here... we want our own inner circuit netlist instead. Or
                 * maybe it doesn't matter? What is netlist used for here? Just
                 * the circuit name? */
              null, null, ComponentName /*, Reporter, HDLType*/), ComponentName, _err, _lang)) {
					return false;
				}
			}
			HandledComponents.add(ComponentName);
		}
		return true;
	}

	/* here the private handles are defined */
	private String GetBubbleIndex(NetlistComponent comp, String HDLType,
			boolean inputBubbles) {
		String BracketOpen = (HDLType.equals(Settings.VHDL)) ? "( " : "[";
		String BracketClose = (HDLType.equals(Settings.VHDL)) ? " )" : "]";
		String RangeKeyword = (HDLType.equals(Settings.VHDL)) ? " DOWNTO "
				: ":";
		if (inputBubbles) {
			return BracketOpen
					+ Integer.toString(comp.GetLocalBubbleInputEndId())
					+ RangeKeyword
					+ Integer.toString(comp.GetLocalBubbleInputStartId())
					+ BracketClose;
		} else {
			return BracketOpen
					+ Integer.toString(comp.GetLocalBubbleOutputEndId())
					+ RangeKeyword
					+ Integer.toString(comp.GetLocalBubbleOutputStartId())
					+ BracketClose;
		}
	}

	@Override
	public ArrayList<String> GetComponentDeclarationSection(Netlist TheNetlist,
			AttributeSet attrs) {
		ArrayList<String> Components = new ArrayList<String>();
		Set<String> InstantiatedComponents = new HashSet<String>();
		for (NetlistComponent Gate : TheNetlist.GetNormalComponents()) {
			String CompName = Gate.GetComponent().getFactory()
					.getHDLName(Gate.GetComponent().getAttributeSet());
			if (!InstantiatedComponents.contains(CompName)) {
				InstantiatedComponents.add(CompName);
				HDLGeneratorFactory Worker = Gate
						.GetComponent()
						.getFactory()
						.getHDLGenerator(Settings.VHDL, null /* reporter */,
								Gate.GetComponent().getAttributeSet(),
								FPGAClass.VendorUnknown);
				if (Worker != null) {
          if (!(Worker instanceof AbstractHDLGeneratorFactory))
            throw new IllegalStateException();
          ((AbstractHDLGeneratorFactory)Worker).initHDLGen(TheNetlist); /* stateful hdl gen */
					if (!Worker.IsOnlyInlined(/*Settings.VHDL*/)) {
						Components.addAll(Worker.GetComponentInstantiation(
								/*TheNetlist,*/
                  Gate.GetComponent().getAttributeSet(), CompName/*, Settings.VHDL*/ /* , false */));
					}
				}
			}
		}
		InstantiatedComponents.clear();
		for (NetlistComponent Gate : TheNetlist.GetSubCircuits()) {
			String CompName = Gate.GetComponent().getFactory()
					.getHDLName(Gate.GetComponent().getAttributeSet());
			if (!InstantiatedComponents.contains(CompName)) {
				InstantiatedComponents.add(CompName);
				HDLGeneratorFactory Worker = Gate
						.GetComponent()
						.getFactory()
						.getHDLGenerator(Settings.VHDL, null /* reporter */,
								Gate.GetComponent().getAttributeSet(),
								FPGAClass.VendorUnknown);
				SubcircuitFactory sub = (SubcircuitFactory) Gate.GetComponent()
						.getFactory();
				if (Worker != null) {
          if (!(Worker instanceof AbstractHDLGeneratorFactory))
            throw new IllegalStateException();
          ((AbstractHDLGeneratorFactory)Worker).initHDLGen(sub.getSubcircuit().getNetList()); /* stateful hdl gen */
					Components.addAll(Worker.GetComponentInstantiation(/*sub
							.getSubcircuit().getNetList(),*/
                Gate.GetComponent().getAttributeSet(), CompName /*, Settings.VHDL*/ /*,  false */));
				}
			}
		}
		return Components;
	}

	@Override
	public String getComponentStringIdentifier() {
		return CorrectLabel.getCorrectLabel(MyCircuit.getName());
	}

	public ArrayList<String> GetHDLWiring(String HDLType, Netlist TheNets) {
		ArrayList<String> Contents = new ArrayList<String>();
		StringBuffer OneLine = new StringBuffer();
		String BracketOpen = (HDLType.equals(Settings.VHDL)) ? "(" : "[";
		String BracketClose = (HDLType.equals(Settings.VHDL)) ? ")" : "]";
		/* we cycle through all nets with a forcedrootnet annotation */
		for (Net ThisNet : TheNets.GetAllNets()) {
			if (ThisNet.IsForcedRootNet()) {
				/* now we cycle through all the bits */
				for (int bit = 0; bit < ThisNet.BitWidth(); bit++) {
					/* First we perform all source connections */
					for (ConnectionPoint Source : ThisNet.GetSourceNets(bit)) {
						OneLine.setLength(0);
						if (ThisNet.isBus()) {
							OneLine.append(BusName
									+ Integer.toString(TheNets
											.GetNetId(ThisNet)) + BracketOpen
									+ bit + BracketClose);
						} else {
							OneLine.append(NetName
									+ Integer.toString(TheNets
											.GetNetId(ThisNet)));
						}
						while (OneLine.length() < SallignmentSize) {
							OneLine.append(" ");
						}
						if (HDLType.equals(Settings.VHDL)) {
							Contents.add("   "
									+ OneLine.toString()
									+ "<= "
									+ BusName
									+ Integer.toString(TheNets.GetNetId(Source
											.GetParrentNet())) + BracketOpen
									+ Source.GetParrentNetBitIndex()
									+ BracketClose + ";");
						} else {
							Contents.add("   assign "
									+ OneLine.toString()
									+ "= "
									+ BusName
									+ Integer.toString(TheNets.GetNetId(Source
											.GetParrentNet())) + BracketOpen
									+ Source.GetParrentNetBitIndex()
									+ BracketClose + ";");
						}
					}
					/* Next we perform all sink connections */
					for (ConnectionPoint Source : ThisNet.GetSinkNets(bit)) {
						OneLine.setLength(0);
						OneLine.append(BusName
								+ Integer.toString(TheNets.GetNetId(Source
										.GetParrentNet())) + BracketOpen
								+ Source.GetParrentNetBitIndex() + BracketClose);
						while (OneLine.length() < SallignmentSize) {
							OneLine.append(" ");
						}
						if (HDLType.equals(Settings.VHDL)) {
							OneLine.append("<= ");
						} else {
							OneLine.append("= ");
						}
						if (ThisNet.isBus()) {
							OneLine.append(BusName
									+ Integer.toString(TheNets
											.GetNetId(ThisNet)) + BracketOpen
									+ bit + BracketClose);
						} else {
							OneLine.append(NetName
									+ Integer.toString(TheNets
											.GetNetId(ThisNet)));
						}
						if (HDLType.equals(Settings.VHDL)) {
							Contents.add("   " + OneLine.toString() + ";");
						} else {
							Contents.add("   assign " + OneLine.toString()
									+ ";");
						}
					}
				}
			}
		}
		return Contents;
	}

	@Override
	public SortedMap<String, Integer> GetInOutList(Netlist MyNetList,
			AttributeSet attrs) {
		SortedMap<String, Integer> InOuts = new TreeMap<String, Integer>();
		// for (int i = 0; i < MyNetList.NumberOfClockTrees(); i++) {
		// InOuts.put(ClockTreeName + Integer.toString(i),
		// ClockHDLGeneratorFactory.NrOfClockBits);
		// }
		// if (MyNetList.RequiresGlobalClockConnection()) {
		// InOuts.put(TickComponentHDLGeneratorFactory.FPGAClock, 1);
		// }
		int InOutBubbles = MyNetList.NumberOfInOutBubbles();
		if (InOutBubbles > 0) {
			if (InOutBubbles > 1) {
				InOuts.put(HDLGeneratorFactory.LocalInOutBubbleBusname,
						InOutBubbles);
			} else {
				InOuts.put(HDLGeneratorFactory.LocalInOutBubbleBusname, 0);
			}
		}
		// for (int i = 0; i < MyNetList.NumberOfInOutPorts(); i++) {
		// NetlistComponent selected = MyNetList.GetInputPin(i);
		// if (selected != null) {
		// InOuts.put(CorrectLabel.getCorrectLabel(selected.GetComponent().getAttributeSet().getValue(StdAttr.LABEL)),
		// selected.GetComponent().getAttributeSet().getValue(StdAttr.WIDTH).getWidth());
		// }
		// }
		return InOuts;
	}

	@Override
	public SortedMap<String, Integer> GetInputList(Netlist MyNetList,
			AttributeSet attrs) {
		SortedMap<String, Integer> Inputs = new TreeMap<String, Integer>();
		for (int i = 0; i < MyNetList.NumberOfClockTrees(); i++) {
			Inputs.put(ClockTreeName + Integer.toString(i),
					ClockHDLGeneratorFactory.NrOfClockBits);
		}
		if (MyNetList.RequiresGlobalClockConnection()) {
			Inputs.put(TickComponentHDLGeneratorFactory.FPGAClock, 1);
		}
		int InputBubbles = MyNetList.NumberOfInputBubbles();
		if (InputBubbles > 0) {
			if (InputBubbles > 1) {
				Inputs.put(HDLGeneratorFactory.LocalInputBubbleBusname,
						InputBubbles);
			} else {
				Inputs.put(HDLGeneratorFactory.LocalInputBubbleBusname, 0);
			}
		}
		for (int i = 0; i < MyNetList.NumberOfInputPorts(); i++) {
			NetlistComponent selected = MyNetList.GetInputPin(i);
			if (selected != null) {
        Inputs.put(
            CorrectLabel.getCorrectLabel(selected
              .GetComponent().getAttributeSet()
              .getValue(StdAttr.LABEL)),
            selected.GetComponent().getAttributeSet()
            .getValue(StdAttr.WIDTH).getWidth());
			}
		}
		return Inputs;
	}

	@Override
	public String GetInstanceIdentifier(NetlistComponent ComponentInfo,
			Long ComponentId) {
		if (ComponentInfo != null) {
			String CompId = CorrectLabel.getCorrectLabel(ComponentInfo
					.GetComponent().getAttributeSet().getValue(StdAttr.LABEL));
			if (!CompId.isEmpty()) {
				return CompId;
			}
		}
		return getComponentStringIdentifier() + "_" + ComponentId.toString();
	}

	@Override
	public ArrayList<String> GetModuleFunctionality(Netlist TheNetlist,
			AttributeSet attrs, FPGAReport Reporter, String HDLType) {
		ArrayList<String> Contents = new ArrayList<String>();
		String Preamble = (HDLType.equals(Settings.VHDL)) ? "" : "assign ";
		String AssignmentOperator = (HDLType.equals(Settings.VHDL)) ? "<= "
				: "= ";
		String OpenBracket = (HDLType.equals(Settings.VHDL)) ? "(" : "[";
		String CloseBracket = (HDLType.equals(Settings.VHDL)) ? ")" : "]";
		boolean FirstLine = true;
		StringBuffer Temp = new StringBuffer();
		Map<String, Long> CompIds = new HashMap<String, Long>();
		/* we start with the connection of the clock sources */
		for (NetlistComponent ClockSource : TheNetlist.GetClockSources()) {
			if (FirstLine) {
				Contents.add("");
        Hdl out = new Hdl(HDLType, Reporter);
        out.comment("definitions for clock generator connections");
				Contents.addAll(out);
				FirstLine = false;
			}
			if (!ClockSource.EndIsConnected(0)) {
				if (ClockSource.GetComponent().getAttributeSet()
						.getValue(StdAttr.LABEL).equals("sysclk")) {
					Reporter.AddInfo("Clock component found with no connection, skipping: '"
							+ ClockSource.GetComponent().getAttributeSet()
									.getValue(StdAttr.LABEL) + "'");
				} else {
					Reporter.AddWarning("Clock component found with no connection, skipping: '"
							+ ClockSource.GetComponent().getAttributeSet()
									.getValue(StdAttr.LABEL) + "'");
				}
				continue;
			}
			String ClockNet = GetClockNetName(ClockSource, 0, TheNetlist);
			if (ClockNet.isEmpty()) {
				Reporter.AddFatalError("INTERNAL ERROR: Cannot find clocknet!");
			}
			String ConnectedNet = GetNetName(ClockSource, 0, true, HDLType,
					TheNetlist);
			Temp.setLength(0);
			Temp.append(ConnectedNet);
			while (Temp.length() < SallignmentSize) {
				Temp.append(" ");
			}
			if (!TheNetlist.RequiresGlobalClockConnection()) {
				Contents.add("   "
						+ Preamble
						+ Temp.toString()
						+ AssignmentOperator
						+ ClockNet
						+ OpenBracket
						+ Integer
								.toString(ClockHDLGeneratorFactory.DerivedClockIndex)
						+ CloseBracket + ";");
			} else {
				Contents.add("   " + Preamble + Temp.toString()
						+ AssignmentOperator
						+ TickComponentHDLGeneratorFactory.FPGAClock + ";");
			}
		}
		/* Here we define all wiring; hence all complex splitter connections */
		ArrayList<String> Wiring = GetHDLWiring(HDLType, TheNetlist);
		if (!Wiring.isEmpty()) {
			Contents.add("");
      Hdl out = new Hdl(HDLType, Reporter);
      out.comment("definitions for wiring");
      Contents.addAll(out);
			Contents.addAll(Wiring);
		}
		/* Now we define all input signals; hence Input port -> Internal Net */
		FirstLine = true;
		for (int i = 0; i < TheNetlist.NumberOfInputPorts(); i++) {
			if (FirstLine) {
				Contents.add("");
        Hdl out = new Hdl(HDLType, Reporter);
        out.comment("definitions for input connections");
        Contents.addAll(out);
				FirstLine = false;
			}
			NetlistComponent MyInput = TheNetlist.GetInputPin(i);
      Contents.add(GetSignalMap(
            CorrectLabel.getCorrectLabel(MyInput.GetComponent()
              .getAttributeSet().getValue(StdAttr.LABEL)),
            MyInput, 0, 3, Reporter, HDLType, TheNetlist));
		}
		// /* Now we define all inout signals; hence InOut port -> Internal Net
		// */
		// FirstLine = true;
		// for (int i = 0; i < TheNetlist.NumberOfInOutPorts(); i++) {
		// if (FirstLine) {
		// Contents.add("");
		// Contents.addAll(MakeRemarkBlock("Here all inout connections are defined",
		// 3, HDLType));
		// FirstLine = false;
		// }
		// NetlistComponent MyInOut = TheNetlist.GetInOutPin(i);
		// Contents.add(GetSignalMap(CorrectLabel.getCorrectLabel(MyInOut.GetComponent().getAttributeSet().getValue(StdAttr.LABEL)),
		// MyInOut, 0, 3, Reporter, HDLType, TheNetlist));
		// }
		/* Now we define all output signals; hence Internal Net -> Input port */
		FirstLine = true;
    NetlistComponent dynClock = TheNetlist.GetDynamicClock();
    if (dynClock != null) {
      Contents.add("");
      Hdl out = new Hdl(HDLType, Reporter);
      out.comment("definitions for dynamic clock output connections");
      Contents.addAll(out);
      FirstLine = false;
      Contents.add(GetSignalMap(
            "LOGISIM_DYNAMIC_CLOCK_OUT",
            dynClock, 0, 3, Reporter, HDLType, TheNetlist));
    }
		for (int i = 0; i < TheNetlist.NumberOfOutputPorts(); i++) {
			if (FirstLine) {
				Contents.add("");
        Hdl out = new Hdl(HDLType, Reporter);
        out.comment("definitions for output connections");
        Contents.addAll(out);
				FirstLine = false;
			}
			NetlistComponent MyOutput = TheNetlist.GetOutputPin(i);
      Contents.add(GetSignalMap(
            CorrectLabel.getCorrectLabel(MyOutput.GetComponent()
              .getAttributeSet().getValue(StdAttr.LABEL)),
            MyOutput, 0, 3, Reporter, HDLType, TheNetlist));
		}
		/* Here all in-lined components are generated */
		FirstLine = true;
		for (NetlistComponent comp : TheNetlist.GetNormalComponents()) {
			HDLGeneratorFactory Worker = comp
					.GetComponent()
					.getFactory()
					.getHDLGenerator(HDLType, Reporter,
							comp.GetComponent().getAttributeSet(),
							FPGAClass.VendorUnknown);
      if (!(Worker instanceof AbstractHDLGeneratorFactory))
        throw new IllegalStateException();
      ((AbstractHDLGeneratorFactory)Worker).initHDLGen(TheNetlist); /* stateful hdl gen */
			if (Worker != null) {
				if (Worker.IsOnlyInlined(/*HDLType*/)) {
					String InlinedName = comp.GetComponent().getFactory()
							.getHDLName(comp.GetComponent().getAttributeSet());
					String InlinedId = Worker.getComponentStringIdentifier();
					Long id;
					if (CompIds.containsKey(InlinedId)) {
						id = CompIds.get(InlinedId);
					} else {
						id = (long) 1;
					}
					if (FirstLine) {
						Contents.add("");
            Hdl out = new Hdl(HDLType, Reporter);
            out.comment("definitions for in-lined components");
            Contents.addAll(out);
						FirstLine = false;
					}
					Contents.addAll(Worker.GetInlinedCode(/*TheNetlist,*/ id++,
							comp/*, Reporter*/, InlinedName/*, HDLType*/));
					if (CompIds.containsKey(InlinedId)) {
						CompIds.remove(InlinedId);
					}
					CompIds.put(InlinedId, id);
				}
			}
		}
		/* Here all "normal" components are generated */
		FirstLine = true;
		for (NetlistComponent comp : TheNetlist.GetNormalComponents()) {
			HDLGeneratorFactory Worker = comp
					.GetComponent()
					.getFactory()
					.getHDLGenerator(HDLType, Reporter,
							comp.GetComponent().getAttributeSet(),
							FPGAClass.VendorUnknown);
			if (Worker != null) {
        if (!(Worker instanceof AbstractHDLGeneratorFactory))
          throw new IllegalStateException();
        ((AbstractHDLGeneratorFactory)Worker).initHDLGen(TheNetlist); /* stateful hdl gen */
				if (!Worker.IsOnlyInlined(/*HDLType*/)) {
					String CompName = comp.GetComponent().getFactory()
							.getHDLName(comp.GetComponent().getAttributeSet());
					String CompId = Worker.getComponentStringIdentifier();
					Long id;
					if (CompIds.containsKey(CompId)) {
						id = CompIds.get(CompId);
					} else {
						id = (long) 1;
					}
					if (FirstLine) {
						Contents.add("");
            Hdl out = new Hdl(HDLType, Reporter);
            out.comment("definitions for normal components");
            Contents.addAll(out);
						FirstLine = false;
					}
					Contents.addAll(Worker.GetComponentMap(/*TheNetlist,*/ id++,
							comp, /*Reporter,*/ CompName /*, HDLType*/));
					if (CompIds.containsKey(CompId)) {
						CompIds.remove(CompId);
					}
					CompIds.put(CompId, id);
				}
			}
		}
		/* Finally we instantiate all sub-circuits */
		FirstLine = true;
		for (NetlistComponent comp : TheNetlist.GetSubCircuits()) {
			HDLGeneratorFactory Worker = comp
					.GetComponent()
					.getFactory()
					.getHDLGenerator(HDLType, Reporter,
							comp.GetComponent().getAttributeSet(),
							FPGAClass.VendorUnknown);
			if (Worker != null) {
        if (!(Worker instanceof AbstractHDLGeneratorFactory))
          throw new IllegalStateException();
        ((AbstractHDLGeneratorFactory)Worker).initHDLGen(TheNetlist); /* stateful hdl gen */
				String CompName = comp.GetComponent().getFactory()
						.getHDLName(comp.GetComponent().getAttributeSet());
				String CompId = Worker.getComponentStringIdentifier();
				Long id;
				if (CompIds.containsKey(CompId)) {
					id = CompIds.get(CompId);
				} else {
					id = (long) 1;
				}
				ArrayList<String> CompMap = Worker.GetComponentMap(/*TheNetlist,*/
						id++, comp, /*Reporter,*/ CompName /*, HDLType*/);
				if (!CompMap.isEmpty()) {
					if (FirstLine) {
						Contents.add("");
            Hdl out = new Hdl(HDLType, Reporter);
            out.comment("definitions for all sub-circuits");
            Contents.addAll(out);
						FirstLine = false;
					}
					if (CompIds.containsKey(CompId)) {
						CompIds.remove(CompId);
					}
					CompIds.put(CompId, id);
					Contents.addAll(CompMap);
				}
			}
		}
		Contents.add("");
		return Contents;
	}

	@Override
	public SortedMap<String, Integer> GetOutputList(Netlist MyNetList,
			AttributeSet attrs) {
		SortedMap<String, Integer> Outputs = new TreeMap<String, Integer>();
                NetlistComponent dynClock = MyNetList.GetDynamicClock();
                if (dynClock != null) {
                    Outputs.put("LOGISIM_DYNAMIC_CLOCK_OUT", dynClock.GetComponent().getAttributeSet().getValue(DynamicClock.WIDTH_ATTR).getWidth());
                }
		int OutputBubbles = MyNetList.NumberOfOutputBubbles();
		if (OutputBubbles > 0) {
			if (OutputBubbles > 1) {
				Outputs.put(HDLGeneratorFactory.LocalOutputBubbleBusname,
						OutputBubbles);
			} else {
				Outputs.put(HDLGeneratorFactory.LocalOutputBubbleBusname, 0);
			}
		}
		for (int i = 0; i < MyNetList.NumberOfOutputPorts(); i++) {
			NetlistComponent selected = MyNetList.GetOutputPin(i);
			if (selected != null) {
				if (selected.GetComponent().getFactory() instanceof Tty) {
					// nothing to do
				} else if (selected.GetComponent().getFactory() instanceof Keyboard) {
					// nothing to do
				} else {
					Outputs.put(
							CorrectLabel.getCorrectLabel(selected
									.GetComponent().getAttributeSet()
									.getValue(StdAttr.LABEL)),
							selected.GetComponent().getAttributeSet()
									.getValue(StdAttr.WIDTH).getWidth());
				}
			}
		}
		return Outputs;
	}

	@Override
	public SortedMap<String, String> GetPortMap(Netlist Nets,
			NetlistComponent ComponentInfo, FPGAReport Reporter, String HDLType) {
		SortedMap<String, String> PortMap = new TreeMap<String, String>();
		if (ComponentInfo != null) {
			SubcircuitFactory sub = (SubcircuitFactory) ComponentInfo
					.GetComponent().getFactory();
			Netlist MyNetList = sub.getSubcircuit().getNetList();
			int NrOfClockTrees = MyNetList.NumberOfClockTrees();
			int NrOfInputBubbles = MyNetList.NumberOfInputBubbles();
			int NrOfOutputBubbles = MyNetList.NumberOfOutputBubbles();
			int NrOfInputPorts = MyNetList.NumberOfInputPorts();
			int NrOfInOutPorts = MyNetList.NumberOfInOutPorts();
			int NrOfOutputPorts = MyNetList.NumberOfOutputPorts();
			/* First we instantiate the Clock tree busses when present */
			for (int i = 0; i < NrOfClockTrees; i++) {
				PortMap.put(ClockTreeName + Integer.toString(i), ClockTreeName
						+ Integer.toString(i));
			}
			if (MyNetList.RequiresGlobalClockConnection()) {
				PortMap.put(TickComponentHDLGeneratorFactory.FPGAClock,
						TickComponentHDLGeneratorFactory.FPGAClock);
			}
			if (NrOfInputBubbles > 0) {
				PortMap.put(HDLGeneratorFactory.LocalInputBubbleBusname,
						HDLGeneratorFactory.LocalInputBubbleBusname
								+ GetBubbleIndex(ComponentInfo, HDLType, true));
			}
			if (NrOfOutputBubbles > 0) {
				PortMap.put(HDLGeneratorFactory.LocalOutputBubbleBusname,
						HDLGeneratorFactory.LocalOutputBubbleBusname
								+ GetBubbleIndex(ComponentInfo, HDLType, false));
			}
			if (NrOfInputPorts > 0) {
				for (int i = 0; i < NrOfInputPorts; i++) {
					NetlistComponent selected = MyNetList.GetInputPin(i);
					if (selected != null) {
						String PinLabel = CorrectLabel.getCorrectLabel(selected
								.GetComponent().getAttributeSet()
								.getValue(StdAttr.LABEL));
						int endid = Nets.GetEndIndex(ComponentInfo, PinLabel,
								false);
						if (endid < 0) {
							Reporter.AddFatalError("INTERNAL ERROR! Could not find the end-index of a sub-circuit component : '"
									+ PinLabel + "'");
						} else {
							PortMap.putAll(GetNetMap(PinLabel, true,
									ComponentInfo, endid, Reporter, HDLType,
									Nets));
						}
					}
				}
			}
			if (NrOfInOutPorts > 0) {
				for (int i = 0; i < NrOfInOutPorts; i++) {
					NetlistComponent selected = MyNetList.GetInOutPin(i);
					if (selected != null) {
						String PinLabel = CorrectLabel.getCorrectLabel(selected
								.GetComponent().getAttributeSet()
								.getValue(StdAttr.LABEL));
						int endid = Nets.GetEndIndex(ComponentInfo, PinLabel,
								false);
						if (endid < 0) {
							Reporter.AddFatalError("INTERNAL ERROR! Could not find the end-index of a sub-circuit component : '"
									+ PinLabel + "'");
						} else {
							PortMap.putAll(GetNetMap(PinLabel, true,
									ComponentInfo, endid, Reporter, HDLType,
									Nets));
						}
					}
				}
			}
			if (NrOfOutputPorts > 0) {
				for (int i = 0; i < NrOfOutputPorts; i++) {
					NetlistComponent selected = MyNetList.GetOutputPin(i);
					if (selected != null) {
						String PinLabel = CorrectLabel.getCorrectLabel(selected
								.GetComponent().getAttributeSet()
								.getValue(StdAttr.LABEL));
						int endid = Nets.GetEndIndex(ComponentInfo, PinLabel,
								true);
						if (endid < 0) {
							Reporter.AddFatalError("INTERNAL ERROR! Could not find the end-index of a sub-circuit component : '"
									+ PinLabel + "'");
						} else {
							PortMap.putAll(GetNetMap(PinLabel, true,
									ComponentInfo, endid, Reporter, HDLType,
									Nets));
						}
					}
				}
			}
		} else {
			int NrOfClockTrees = Nets.NumberOfClockTrees();
			int NrOfInputBubbles = Nets.NumberOfInputBubbles();
			int NrOfOutputBubbles = Nets.NumberOfOutputBubbles();
			int NrOfInputPorts = Nets.NumberOfInputPorts();
			int NrOfInOutPorts = Nets.NumberOfInOutPorts();
			int NrOfOutputPorts = Nets.NumberOfOutputPorts();
			for (int i = 0; i < NrOfClockTrees; i++) {
				PortMap.put(ClockTreeName + Integer.toString(i), "s_"
						+ ClockTreeName + Integer.toString(i));
			}
                        NetlistComponent dynClock = Nets.GetDynamicClock();
			if (Nets.RequiresGlobalClockConnection()) {
				PortMap.put(TickComponentHDLGeneratorFactory.FPGAClock,
						TickComponentHDLGeneratorFactory.FPGAClock);
			}
                        if (dynClock != null) {
                            PortMap.put("LOGISIM_DYNAMIC_CLOCK_OUT", "s_LOGISIM_DYNAMIC_CLOCK");
                        }
			if (NrOfInputBubbles > 0) {
				PortMap.put(HDLGeneratorFactory.LocalInputBubbleBusname,
						"s_LOGISIM_INPUT_BUBBLES");
			}
			if (NrOfOutputBubbles > 0) {
				PortMap.put(HDLGeneratorFactory.LocalOutputBubbleBusname,
						"s_LOGISIM_OUTPUT_BUBBLES");
			}
			if (NrOfInputPorts > 0) {
				for (int i = 0; i < NrOfInputPorts; i++) {
					NetlistComponent selected = Nets.GetInputPin(i);
					if (selected != null) {
            String PinLabel = CorrectLabel
                .getCorrectLabel(selected.GetComponent()
                    .getAttributeSet()
                    .getValue(StdAttr.LABEL));
            PortMap.put(PinLabel, "s_" + PinLabel);
					}
				}
			}
			if (NrOfInOutPorts > 0) {
				for (int i = 0; i < NrOfInOutPorts; i++) {
					NetlistComponent selected = Nets.GetInOutPin(i);
					if (selected != null) {
						if (selected.GetComponent().getFactory() instanceof PortIO) {
							ArrayList<String> name = new ArrayList<String>();
							MappableResourcesContainer mapInfo = ((PortIO) selected
									.GetComponent().getFactory()).getMapInfo();
							int start = mapInfo
									.GetFPGAInOutPinId(mapInfo.currentBoardName
											+ ":/"
											+ selected.GetComponent()
													.getAttributeSet()
													.getValue(StdAttr.LABEL));
							int k = 0;
							name.add(selected.GetComponent().getAttributeSet()
									.getValue(StdAttr.LABEL));
							for (int j = selected.GetGlobalBubbleId(name)
									.GetInOutStartIndex(); j <= selected
									.GetGlobalBubbleId(name).GetInOutEndIndex(); j++) {
								PortMap.put(LocalInOutBubbleBusname + "(" + j
										+ ")", FPGAInOutPinName + "_"
										+ (start + k));
								k++;
							}
						} else if (selected.GetComponent().getFactory() instanceof Tty) {
							ArrayList<String> name = new ArrayList<String>();
							MappableResourcesContainer mapInfo = ((Tty) selected
									.GetComponent().getFactory()).getMapInfo();
							int start = mapInfo
									.GetFPGAInOutPinId(mapInfo.currentBoardName
											+ ":/"
											+ selected.GetComponent()
													.getAttributeSet()
													.getValue(StdAttr.LABEL));
							int k = 0;
							name.add(selected.GetComponent().getAttributeSet()
									.getValue(StdAttr.LABEL));
							for (int j = selected.GetGlobalBubbleId(name)
									.GetInOutStartIndex(); j <= selected
									.GetGlobalBubbleId(name).GetInOutEndIndex(); j++) {
								PortMap.put(LocalInOutBubbleBusname + "(" + j
										+ ")", FPGAInOutPinName + "_"
										+ (start + k));
								k++;
							}
						} else if (selected.GetComponent().getFactory() instanceof Keyboard) {
							ArrayList<String> name = new ArrayList<String>();
							MappableResourcesContainer mapInfo = ((Keyboard) selected
									.GetComponent().getFactory()).getMapInfo();
							int start = mapInfo
									.GetFPGAInOutPinId(mapInfo.currentBoardName
											+ ":/"
											+ selected.GetComponent()
													.getAttributeSet()
													.getValue(StdAttr.LABEL));
							int k = 0;
							name.add(selected.GetComponent().getAttributeSet()
									.getValue(StdAttr.LABEL));
							for (int j = selected.GetGlobalBubbleId(name)
									.GetInOutStartIndex(); j <= selected
									.GetGlobalBubbleId(name).GetInOutEndIndex(); j++) {
								PortMap.put(LocalInOutBubbleBusname + "(" + j
										+ ")", FPGAInOutPinName + "_"
										+ (start + k));
								k++;
							}
						} else {
							String PinLabel = CorrectLabel
									.getCorrectLabel(selected.GetComponent()
											.getAttributeSet()
											.getValue(StdAttr.LABEL));
							PortMap.put(PinLabel, "s_" + PinLabel);
						}
					}
				}
			}
			if (NrOfOutputPorts > 0) {
				for (int i = 0; i < NrOfOutputPorts; i++) {
					NetlistComponent selected = Nets.GetOutputPin(i);
          if (selected != null) {
            String PinLabel = CorrectLabel
                .getCorrectLabel(selected.GetComponent()
                    .getAttributeSet()
                    .getValue(StdAttr.LABEL));
            PortMap.put(PinLabel, "s_" + PinLabel);
          }
				}
			}
		}
		return PortMap;
	}

	private String GetSignalMap(String PortName, NetlistComponent comp,
			int EndIndex, int TabSize, FPGAReport Reporter, String HDLType,
			Netlist TheNets) {
		StringBuffer Contents = new StringBuffer();
		StringBuffer Source = new StringBuffer();
		StringBuffer Destination = new StringBuffer();
		StringBuffer Tab = new StringBuffer();
		String AssignCommand = (HDLType.equals(Settings.VHDL)) ? "" : "assign ";
		String AssignOperator = (HDLType.equals(Settings.VHDL)) ? "<= " : "= ";
		String BracketOpen = (HDLType.equals(Settings.VHDL)) ? "(" : "[";
		String BracketClose = (HDLType.equals(Settings.VHDL)) ? ")" : "]";
		if ((EndIndex < 0) || (EndIndex >= comp.NrOfEnds())) {
			Reporter.AddFatalError("INTERNAL ERROR: Component tried to index non-existing SolderPoint: '"
					+ comp.GetComponent().getAttributeSet()
							.getValue(StdAttr.LABEL) + "'");
			return "";
		}
		for (int i = 0; i < TabSize; i++) {
			Tab.append(" ");
		}
		ConnectionEnd ConnectionInformation = comp.getEnd(EndIndex);
		boolean IsOutput = ConnectionInformation.IsOutputEnd();
		int NrOfBits = ConnectionInformation.NrOfBits();
		if (NrOfBits == 1) {
			/* Here we have the easy case, just a single bit net */
			if (IsOutput) {
				if (!comp.EndIsConnected(EndIndex)) {
					return " ";
				}
				Source.append(PortName);
				Destination.append(GetNetName(comp, EndIndex, true, HDLType,
						TheNets));
			} else {
				if (!comp.EndIsConnected(EndIndex)) {
                                        Reporter.AddSevereWarning("In circuit " 
                                                + "'" + MyCircuit.getName() + "', output pin "
                                                + "'" + PortName + "'"
                                                + " is not connected. The pin will be tied to ground!");
				}
				Source.append(GetNetName(comp, EndIndex, true, HDLType, TheNets));
				Destination.append(PortName);
				if (!comp.EndIsConnected(EndIndex)) {
					return Contents.toString();
				}
			}
			while (Destination.length() < SallignmentSize) {
				Destination.append(" ");
			}
			Contents.append(Tab.toString() + AssignCommand + Destination
					+ AssignOperator + Source + ";");
		} else {
			/*
			 * Here we have the more difficult case, it is a bus that needs to
			 * be mapped
			 */
			/* First we check if the bus has a connection */
			boolean Connected = false;
			for (int i = 0; i < NrOfBits; i++) {
				if (ConnectionInformation.GetConnection((byte) i)
						.GetParrentNet() != null) {
					Connected = true;
				}
			}
			if (!Connected) {
				/* Here is the easy case, the bus is unconnected */
				if (IsOutput) {
					return Contents.toString();
				} else {
                                        Reporter.AddSevereWarning("In circuit " 
                                                + "'" + MyCircuit.getName() + "', output bus "
                                                + "'" + PortName + "'"
                                                + " is not connected. All bus pins will be tied to ground!");
				}
				Destination.append(PortName);
				while (Destination.length() < SallignmentSize) {
					Destination.append(" ");
				}
        Hdl out = new Hdl(_lang, _err);
				Contents.append(Tab.toString() + AssignCommand
						+ Destination.toString() + AssignOperator
						+ out.zeros(NrOfBits) + ";");
			} else {
				/*
				 * There are connections, we detect if it is a continues bus
				 * connection
				 */
				if (TheNets.IsContinuesBus(comp, EndIndex)) {
					Destination.setLength(0);
					Source.setLength(0);
					/* Another easy case, the continues bus connection */
					if (IsOutput) {
						Source.append(PortName);
						Destination.append(GetBusNameContinues(comp, EndIndex,
								HDLType, TheNets));
					} else {
						Destination.append(PortName);
						Source.append(GetBusNameContinues(comp, EndIndex,
								HDLType, TheNets));
					}
					while (Destination.length() < SallignmentSize) {
						Destination.append(" ");
					}
					Contents.append(Tab.toString() + AssignCommand
							+ Destination + AssignOperator + Source + ";");
				} else {
					/* The last case, we have to enumerate through each bit */
					for (int bit = 0; bit < NrOfBits; bit++) {
						Source.setLength(0);
						Destination.setLength(0);
						if (IsOutput) {
							Source.append(PortName + BracketOpen
									+ Integer.toString(bit) + BracketClose);
						} else {
							Destination.append(PortName + BracketOpen
									+ Integer.toString(bit) + BracketClose);
						}
						ConnectionPoint SolderPoint = ConnectionInformation
								.GetConnection((byte) bit);
						if (SolderPoint.GetParrentNet() == null) {
							/* The net is not connected */
							if (IsOutput) {
								continue;
							} else {
                Hdl out = new Hdl(HDLType, Reporter);
                Reporter.AddSevereWarning("In circuit " 
                    + "'" + MyCircuit.getName() + "', pin " + bit + " of output bus "
                    + "'" + PortName + "'"
                    + " is not connected. This bus pin will be tied to ground!");
								Source.append(out.zero);
							}
						} else {
							/*
							 * The net is connected, we have to find out if the
							 * connection is to a bus or to a normal net
							 */
							if (SolderPoint.GetParrentNet().BitWidth() == 1) {
								/* The connection is to a Net */
								if (IsOutput) {
									Destination.append(NetName
											+ Integer.toString(TheNets
													.GetNetId(SolderPoint
															.GetParrentNet())));
								} else {
									Source.append(NetName
											+ Integer.toString(TheNets
													.GetNetId(SolderPoint
															.GetParrentNet())));
								}
							} else {
								/* The connection is to an entry of a bus */
								if (IsOutput) {
									Destination.append(BusName
											+ Integer.toString(TheNets
													.GetNetId(SolderPoint
															.GetParrentNet()))
											+ BracketOpen
											+ Integer.toString(SolderPoint
													.GetParrentNetBitIndex())
											+ BracketClose);
								} else {
									Source.append(BusName
											+ Integer.toString(TheNets
													.GetNetId(SolderPoint
															.GetParrentNet()))
											+ BracketOpen
											+ Integer.toString(SolderPoint
													.GetParrentNetBitIndex())
											+ BracketClose);
								}
							}
						}
						while (Destination.length() < SallignmentSize) {
							Destination.append(" ");
						}
						if (bit != 0) {
							Contents.append("\n");
						}
						Contents.append(Tab.toString() + AssignCommand
								+ Destination + AssignOperator + Source + ";");
					}
				}
			}
		}
		return Contents.toString();
	}

	@Override
	public String GetSubDir() {
		return "circuit";
	}

	@Override
	public SortedMap<String, Integer> GetWireList(AttributeSet attrs,
			Netlist Nets) {
		SortedMap<String, Integer> SignalMap = new TreeMap<String, Integer>();

		/* First we define the nets */
		for (Net ThisNet : Nets.GetAllNets()) {
			if (!ThisNet.isBus()) {
				SignalMap.put(
						NetName + Integer.toString(Nets.GetNetId(ThisNet)), 1);
			}
		}
		/* now we define the busses */
		for (Net ThisNet : Nets.GetAllNets()) {
			if (ThisNet.isBus()) {
				int NrOfBits = ThisNet.BitWidth();
				SignalMap.put(
						BusName + Integer.toString(Nets.GetNetId(ThisNet)),
						NrOfBits);
			}
		}
		return SignalMap;
	}

}
