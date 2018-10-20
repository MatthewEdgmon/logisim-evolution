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

package com.cburch.logisim.file;
import static com.cburch.logisim.file.Strings.S;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Collection;
import java.util.ArrayList;

import org.w3c.dom.Element;

import com.cburch.draw.model.AbstractCanvasObject;
import com.cburch.logisim.Main;
import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitMutator;
import com.cburch.logisim.circuit.CircuitTransaction;
import com.cburch.logisim.circuit.appear.AppearanceSvgReader;
import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.comp.ComponentFactory;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.tools.AddTool;
import com.cburch.logisim.tools.Library;
import com.cburch.logisim.tools.Tool;

import javax.swing.JOptionPane;

public class XmlCircuitReader extends CircuitTransaction {

  static boolean trackercomp_warned = false;

  /**
   * Get a circuit's component from a read XML file. 
   *
   * @param elt
   *            XML element to parse
   * @param reader
   *            XML file reader
   * @return the component built from its XML description
   * @throws XmlReaderException
   */
  static Component getComponent(Element elt, XmlReader.ReadContext reader)
      throws XmlReaderException {

    // Someone (REDS-HEIG?) apparently has files containing this secret
    // value and only shows these components in special "tracker" versions
    // of logisim. 
    if (!trackercomp_warned && elt.getAttribute("trackercomp") != "") {
      trackercomp_warned = true;
      String msg =
          "WARNING: This file contains mysterious \"tracked\" components and may not\n"
          + "work properly in this version of Logisim-Evolution. The file will be opened\n"
          + "anyway, in the hope it might work. You may want to instead edit the \".circ\"\n"
          + "file by hand, in a text editor, to remove the offending components with the\n"
          + "\"trackercomp\" XML attrbute. Contact me at <kwalsh@holycross.edu> as\n"
          + "well, since I'm curious what this is all about.";
      if (Main.headless) {
        System.err.println(msg);
      } else {
        JOptionPane.showMessageDialog(null, msg);
      }
    }

    // Determine the factory that creates this element
    String name = elt.getAttribute("name");
    if (name == null || name.equals("")) {
      throw new XmlReaderException(S.get("compNameMissingError"));
    }

    String libName = elt.getAttribute("lib");
    Library lib = reader.findLibrary(libName);
    if (lib == null) {
      throw new XmlReaderException(S.fmt("compUnknownError", "no-lib"));
    }

    Tool tool = lib.getTool(name);
    if (tool == null || !(tool instanceof AddTool)) {
      if (libName == null || libName.equals("")) {
        throw new XmlReaderException(S.fmt("compUnknownError", name));
      } else {
        throw new XmlReaderException(S.fmt("compAbsentError", name, libName));
      }
    }
    ComponentFactory source = ((AddTool) tool).getFactory();

    // Determine attributes
    String loc_str = elt.getAttribute("loc");
    AttributeSet attrs = source.createAttributeSet();
    reader.initAttributeSet(elt, attrs, source);

    // Create component if location known
    if (loc_str == null || loc_str.equals("")) {
      throw new XmlReaderException(S.fmt("compLocMissingError", source.getName()));
    } else {
      try {
        Location loc = Location.parse(loc_str);
        return source.createComponent(loc, attrs);
      } catch (NumberFormatException e) {
        throw new XmlReaderException(S.fmt("compLocInvalidError", source.getName(), loc_str));
      }
    }
  }

  private XmlReader.ReadContext reader;

  private List<XmlReader.CircuitData> circuitsData;

  public XmlCircuitReader(XmlReader.ReadContext reader,
      List<XmlReader.CircuitData> circDatas) {
    this.reader = reader;
    this.circuitsData = circDatas;
  }

  void addWire(Circuit dest, CircuitMutator mutator, Element elt)
      throws XmlReaderException {
    Location pt0;
    try {
      String str = elt.getAttribute("from");
      if (str == null || str.equals("")) {
        throw new XmlReaderException(S.get("wireStartMissingError"));
      }
      pt0 = Location.parse(str);
    } catch (NumberFormatException e) {
      throw new XmlReaderException(S.get("wireStartInvalidError"));
    }

    Location pt1;
    try {
      String str = elt.getAttribute("to");
      if (str == null || str.equals("")) {
        throw new XmlReaderException(S.get("wireEndMissingError"));
      }
      pt1 = Location.parse(str);
    } catch (NumberFormatException e) {
      throw new XmlReaderException(S.get("wireEndInvalidError"));
    }

    mutator.add(dest, Wire.create(pt0, pt1));
  }

  private void buildCircuit(XmlReader.CircuitData circData, CircuitMutator mutator) {
    Element elt = circData.circuitElement;
    Circuit dest = circData.circuit;
    Map<Element, Component> knownComponents = circData.knownComponents;
    if (knownComponents == null)
      knownComponents = Collections.emptyMap();
    try {
      reader.initAttributeSet(circData.circuitElement,
          dest.getStaticAttributes(), null);
    } catch (XmlReaderException e) {
      reader.addErrors(e, circData.circuit.getName() + ".static");
    }

    for (Element sub_elt : XmlIterator.forChildElements(elt)) {
      String sub_elt_name = sub_elt.getTagName();
      if (sub_elt_name.equals("comp")) {
        try {
          Component comp = knownComponents.get(sub_elt);
          if (comp == null) {
            comp = getComponent(sub_elt, reader);
          }
          if (comp != null) {
            mutator.add(dest, comp);
          }
        } catch (XmlReaderException e) {
          reader.addErrors(e, circData.circuit.getName() + "."
              + toComponentString(sub_elt));
        }
      } else if (sub_elt_name.equals("wire")) {
        try {
          addWire(dest, mutator, sub_elt);
        } catch (XmlReaderException e) {
          reader.addErrors(e, circData.circuit.getName() + "."
              + toWireString(sub_elt));
        }
      }
    }
  }

  private void buildDynamicAppearance(XmlReader.CircuitData circData, CircuitMutator mutator) {
    Circuit dest = circData.circuit;
    Collection<Component> components = circData.knownComponents.values();
    List<AbstractCanvasObject> shapes = new ArrayList<AbstractCanvasObject>();
    for (Element appearElt : XmlIterator.forChildElements(circData.circuitElement, "appear")) {
      for (Element sub : XmlIterator.forChildElements(appearElt)) {
        // Dynamic shapes are handled here. Static shapes are already done.
        if (!sub.getTagName().startsWith("visible-"))
          continue;
        try {
          AbstractCanvasObject m = AppearanceSvgReader.createShape(sub, null, dest);
          if (m == null) {
            reader.addError(S.fmt("fileAppearanceNotFound", sub.getTagName()),
                circData.circuit.getName() + "." + sub.getTagName());
          } else {
            shapes.add(m);
          }
        } catch (RuntimeException e) {
          reader.addError(S.fmt("fileAppearanceError", sub.getTagName()),
              circData.circuit.getName() + "." + sub.getTagName());
        }
      }
    }
    if (!shapes.isEmpty()) {
      if (circData.appearance == null) {
        circData.appearance = shapes;
      } else {
        circData.appearance.addAll(shapes);
      }
    }
    if (circData.appearance != null && !circData.appearance.isEmpty()) {
      dest.getAppearance().setObjectsForce(circData.appearance);
      dest.getAppearance().setDefaultAppearance(false);
    }
  }

  @Override
  protected Map<Circuit, Integer> getAccessedCircuits() {
    HashMap<Circuit, Integer> access = new HashMap<Circuit, Integer>();
    for (XmlReader.CircuitData data : circuitsData) {
      access.put(data.circuit, READ_WRITE);
    }
    return access;
  }

  @Override
  protected void run(CircuitMutator mutator) {
    for (XmlReader.CircuitData circuitData : circuitsData) {
      buildCircuit(circuitData, mutator);
    }
    for (XmlReader.CircuitData circuitData : circuitsData) {
      buildDynamicAppearance(circuitData, mutator);
    }
  }

  private String toComponentString(Element elt) {
    String name = elt.getAttribute("name");
    String loc = elt.getAttribute("loc");
    return name + "(" + loc + ")";
  }

  private String toWireString(Element elt) {
    String from = elt.getAttribute("from");
    String to = elt.getAttribute("to");
    return "w" + from + "-" + to;
  }
}
