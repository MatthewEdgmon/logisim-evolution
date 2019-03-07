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

package com.bfh.logisim.fpga;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.util.EnumSet;
import java.util.HashMap;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JTextField;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.proj.Projects;
import com.cburch.logisim.std.io.DipSwitch;
import com.cburch.logisim.std.io.PortIO;
import com.cburch.logisim.std.io.RGBLed;
// import com.cburch.logisim.std.io.SevenSegment;
import com.cburch.logisim.util.Errors;
import static com.bfh.logisim.netlist.Netlist.Int3;

// Each BoardIO represents one physical I/O resource, like an LED, button, or
// switch. Some I/O resources can only be used as inputs (e.g. a button), some
// can only be used as outputs (e.g. an LED), some are inherently bidirectional
// (e.g. the PS2 keyboard connector), and some have no inherent direction at all
// (e.g. a debug or expansion header directly connected to a configurable pin on
// the FPGA). As a simplification, we assume all the bits in a multi-bit I/O
// resource has the same "direction", which can be one of:
//  in  - for things that can only be used as inputs, e.g. buttons and switches
//  out - for things that can only be used as outputs, e.g. LEDs
//  any - for things that can be used as inputs, outputs, or bidirectional inout
// Note: Things with direction "any", if misused, can cause physical short
// circuits. A keyboard connector would be classified as a 4-bit I/O resource
// with direction "any", but if you map an output driver to those bits, it will
// conflict when the keyboard tries to send scancodes. However, this is always a
// risk with any bidirectional port anyway. The only place we could do a little
// better here is allowing a multi-bit connection where the individual bits can
// have differrent directions (whereas now they'd all have to be promoted to
// "any", even if we knew some bits are input-only, or the connector would have
// to be split into separate BoardIO resources).
public class BoardIO {

  public static final EnumSet<Type> PhysicalTypes = EnumSet.range(Type.Button, Type.SevenSegment);
  public static final EnumSet<Type> InputTypes = EnumSet.range(Type.Button, Type.Ribbon);
  public static final EnumSet<Type> OutputTypes = EnumSet.range(Type.Pin, Type.LED);
  public static final EnumSet<Type> InOutTypes = EnumSet.of(Type.Pin, Type.Ribbon);
  public static final EnumSet<Type> OneBitTypes = EnumSet.of(Type.Button, Type.Pin, Type.LED);

	public static enum Type {
    // Note: The order here matters, because of the EnumSet ranges above.
                   // Physical and synthetic I/O resource characteristics:
    AllZeros,      // synth in  onebit/multibit
    AllOnes,       // synth in  onebit/multibit
    Constant,      // synth in  onebit/multibit
    Button,        // phys  in  onebit
    DIPSwitch,     // phys  in  multibit (degenerates to Button)
    Pin,           // phys  any onebit
    Ribbon,        // phys  any multibit (degenerates to Pin)
		LED,           // phys  out onebit
    RGBLED,        // phys  out multibit (degenerates to LED)
    SevenSegment,  // phys  out multibit (degenerates to LED)
    Unconnected,   // synth out onebit/multibit

    Unknown;

    // Note: The types above are used both to describe physical I/O resources
    // (with the characteristics as noted above). But Logisim components within
    // the circuit design under test also use the above types to describe
    // constraints on the I/O resources they are meant to be connected to. For
    // Pin and Ribbon types, the component will specific whether the bits are
    // meant to be treated as in, out, or bidirectional. For example, a PortIO
    // component (which declares type Ribbon) must connect to a bidirectional
    // pin, and would not make sense to connect to Buttons or LEDs. That is, all
    // logisim components have a specific direction: in, out, or inout.

		private static Type get(String str) {
			for (Type t : PhysicalTypes)
				if (t.name().equalsIgnoreCase(str))
					return t;
      if (str.equalsIgnoreCase("PortIO")) // old name for backwards compatibility
        return Ribbon;
			return Type.Unknown;
		}

    public int defaultWidth() {
      switch (this) {
      case LED:
      case Button:
      case Pin:
        return 1;
      case DIPSwitch:
        return (DipSwitch.MIN_SWITCH + DipSwitch.MAX_SWITCH)/2;
      case Ribbon:
        return (PortIO.MIN_IO + PortIO.MAX_IO)/2;
      case SevenSegment:
        return 8;
      case RGBLED:
        return 3;
      default:
        return 0;
      }
    }

    public String[] pinLabels(int width) {
      switch (this) {
      case SevenSegment:
        return com.cburch.logisim.std.io.SevenSegment.pinLabels();
      case RGBLED:
        return RGBLed.pinLabels();
      default:
        return genericPinLabels(width);
      }
    }

    private String[] genericPinLabels(int width) {
      String[] labels = new String[width];
      if (width == 1)
        labels[0] = "Pin";
      else
        for (int i = 0; i < width; i++)
          labels[0] = "Pin_" + i;
      return labels;
    }

	}

	public final Type type;
	public final int width;
  public final String label;
	public final Bounds rect; // only for physical types
	public final IoStandard standard; // only for physical types
	public final PullBehavior pull; // only for physical types
	public final PinActivity activity; // only for physical types; set to ACTIVE_HIGH for synthetic
	public final DriveStrength strength; // only for physical types
  public final int syntheticValue; // only for synthetic types

	public final String[] pins;

  // constructor for synthetic I/O resources
  private BoardIO(Type t, int w, int val) {
    type = t;
    width = w;
    syntheticValue = val;
    label = t == Type.Constant ? String.format("0x%x", val) : t.toString();
    activity = PinActivity.ACTIVE_HIGH;
    // rest are defaults/empty
    rect = null;
    standard = IoStandard.UNKNOWN;
    pull = PullBehavior.UNKNOWN;
    strength = DriveStrength.UNKNOWN;
    pins = null;
  }

  public static BoardIO makeSynthetic(Type t, int w, int val) {
    if (PhysicalTypes.contains(t))
      throw new IllegalArgumentException("BoardIO type "+t+" is not meant for synthetic I/O resources");
    return new BoardIO(t, w, val);
  }

  // public static BoardIO makeConstant(int w, int val) { return new BoardIO(Type.Constant, w, val); }
  // public static BoardIO makeAllZeros(int w) { return new BoardIO(Type.AllZeros, w, 0); }
  // public static BoardIO makeAllOnes(int w) { return new BoardIO(Type.AllOnes, w, -1); }
  // public static BoardIO makeUnconnected(int w) { return new BoardIO(Type.Unconnected, w, 0); }

  // constructor for physical I/O resources
  private BoardIO(Type t, int w, String l, Bounds r,
      IoStandard s, PullBehavior p, PinActivity a, DriveStrength g, String[] x) {
    if (!PhysicalTypes.contains(t))
      throw new IllegalArgumentException("BoardIO type "+t+" is not meant for physical I/O resources");
    type = t;
    width = w;
    label = l;
    rect = r;
    standard = s;
    pull = p;
    activity = a;
    strength = g;
    pins = x;
    // rest are defaults/empty
    syntheticValue = 0;
  }

  public static BoardIO parseXml(Node node) throws Exception {
    Type t = Type.get(node.getNodeName());
    if (t == Type.Unknown)
      throw new Exception("unrecognized I/O resource type: " + node.getNodeName());

    HashMap<String, String> params = new HashMap<>();
    NamedNodeMap attrs = node.getAttributes();
    for (int i = 0; i < attrs.getLength(); i++) {
      Node attr = attrs.item(i);
      String tag = attr.getNodeName();
      String val = attr.getNodeValue();
      params.put(tag, val);
    }

    String label = params.get("Label");
    int x = Integer.parseInt(params.getOrDefault("LocationX", "-1"));
    int y = Integer.parseInt(params.getOrDefault("LocationY", "-1"));
    int w = Integer.parseInt(params.getOrDefault("Width", "-1"));
    int h = Integer.parseInt(params.getOrDefault("Height", "-1"));
		if (x < 0 || y < 0 || w < 1 || h < 1)
      throw new Exception("invalid coordinates or size for I/O resource");
		Bounds r = Bounds.create(x, y, w, h);

    PullBehavior p = PullBehavior.get(params.get("FPGAPinPullBehavior"));
    PinActivity a = (t == Type.Pin) ? PinActivity.ACTIVE_HIGH :
        PinActivity.get(params.get("ActivityLevel"));
    IoStandard s = IoStandard.get(params.get("FPGAPinIOStandard"));
    DriveStrength g = DriveStrength.get(params.get("FPGAPinDriveStrength"));

    String[] pins;
		int width;
    if (params.containsKey("FPGAPinName")) {
      width = 1;
      pins = new String[] { params.get("FPGAPinName") };
      if (pins[0] == null)
        throw new Exception("missing pin label for " + t + " " + label);
    } else {
      width = Integer.parseInt(params.get("NrOfPins"));
      if (width < 0)
        throw new Exception("invalid pin count for " + t + " " + label);
      pins = new String[width];
      for (int i = 0; i < width; i++) {
        pins[i] = params.get("FPGAPin_" + i);
        if (pins[i] == null)
          throw new Exception("missing pin label " + i + " for " + t + " " + label);
      }
    }

    return new BoardIO(t, width, label, r, s, p, a, g, pins);
	}

	public Element encodeXml(Document doc) throws Exception {
    Element elt = doc.createElement(type.toString());
    elt.setAttribute("LocationX", ""+rect.x);
    elt.setAttribute("LocationY", ""+rect.y);
    elt.setAttribute("Width", ""+rect.width);
    elt.setAttribute("Height", ""+rect.height);
    if (label != null)
      elt.setAttribute("Label", label);
    if (width == 1) {
      elt.setAttribute("FPGAPinName", pins[0]);
    } else {
      elt.setAttribute("NrOfPins", ""+width);
      for (int i = 0; i < width; i++)
        elt.setAttribute("FPGAPin_"+i, pins[i]);
    }
    if (strength != DriveStrength.UNKNOWN)
      elt.setAttribute("FPGAPinDriveStrength", ""+strength);
    if (activity != PinActivity.UNKNOWN && type != Type.Pin) // skip Pin
      elt.setAttribute("ActivityLevel", ""+activity);
    if (pull != PullBehavior.UNKNOWN)
      elt.setAttribute("FPGAPinPullBehavior", ""+pull);
    if (standard != IoStandard.UNKNOWN && standard != IoStandard.DEFAULT)
      elt.setAttribute("FPGAPinIOStandard", ""+standard);
    return elt;
  }

	public static BoardIO makeUserDefined(Type t, Bounds r, BoardEditor parent) {
    int w = t.defaultWidth();
    BoardIO template = new BoardIO(t, w, null, r,
        defaultStandard, defaultPull, defaultActivity, defaultStrength, null);
    if (t == Type.DIPSwitch || t == Type.Ribbon)
      template = doSizeDialog(template, parent);
    if (template == null)
      return null;
    return doInfoDialog(template, parent, false);
  }

  public static BoardIO redoUserDefined(BoardIO io, BoardEditor parent) {
    BoardIO template = io;
    if (template.type == Type.DIPSwitch || template.type == Type.Ribbon)
      template = doSizeDialog(template, parent);
    if (template == null)
      return io; // user cancelled before getting to config dialog
    return doInfoDialog(template, parent, true);
  }

  private static BoardIO doSizeDialog(BoardIO t, BoardEditor parent) {
    int min = t.type == Type.DIPSwitch ? DipSwitch.MIN_SWITCH : PortIO.MIN_IO;
    int max = t.type == Type.DIPSwitch ? DipSwitch.MAX_SWITCH : PortIO.MAX_IO;
    final int[] width = new int[] { t.width };

    final JDialog dlg = new JDialog(parent, t.type + " Size");
    dlg.setLayout(new GridBagLayout());
    GridBagConstraints c = new GridBagConstraints();
    c.fill = GridBagConstraints.HORIZONTAL;

    String things = t.type == Type.DIPSwitch ? "switches" : "pins";
    JLabel question = new JLabel("Specify number of " + things + " in " + t.type + ":");

    JComboBox<Integer> size = new JComboBox<>();
    for (int i = min; i <= max; i++)
      size.addItem(i);
    size.setSelectedItem(width[0]);

    JButton next = new JButton("Next");
    next.addActionListener(e -> {
      width[0] = (Integer)size.getSelectedItem();
      dlg.setVisible(false);
    });

    c.gridx = 0;
    c.gridy = 0;
    dlg.add(question, c);

    c.gridx = 1;
    dlg.add(size, c);

    c.gridy++;
    dlg.add(next, c);

    dlg.pack();
    dlg.setLocation(Projects.getCenteredLoc(dlg.getWidth(), dlg.getHeight()));
    dlg.setModal(true);
    dlg.setResizable(false);
    dlg.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
    dlg.setAlwaysOnTop(true);
    dlg.setVisible(true);

    if (t.width == width[0])
      return t; // no change
    return new BoardIO(t.type, width[0], t.label,
        t.rect, t.standard, t.pull, t.activity, t.strength, t.pins);
  }

  private static DriveStrength defaultStrength = DriveStrength.DEFAULT;
  private static PinActivity defaultActivity = PinActivity.ACTIVE_HIGH;
  private static IoStandard defaultStandard = IoStandard.DEFAULT;
  private static PullBehavior defaultPull = PullBehavior.FLOAT;

  private static void add(JDialog dlg, GridBagConstraints c,
      String caption, JComponent input) {
      dlg.add(new JLabel(caption + " "), c);
      c.gridx++;
      dlg.add(input, c);
      c.gridx--;
      c.gridy++;
  }

  private static BoardIO doInfoDialog(BoardIO t, BoardEditor parent, boolean removable) {
    final JDialog dlg = new JDialog(parent, t.type + " Properties");
    dlg.setLayout(new GridBagLayout());
    GridBagConstraints c = new GridBagConstraints();
    c.fill = GridBagConstraints.HORIZONTAL;

    JComboBox<IoStandard> standard = new JComboBox<>(IoStandard.OPTIONS);
    JComboBox<DriveStrength> strength = new JComboBox<>(DriveStrength.OPTIONS);
    JComboBox<PinActivity> activity = new JComboBox<>(PinActivity.OPTIONS);
    JComboBox<PullBehavior> pull = new JComboBox<>(PullBehavior.OPTIONS);

    standard.setSelectedItem(t.standard);
    strength.setSelectedItem(t.strength);
    activity.setSelectedItem(t.activity);
    pull.setSelectedItem(t.pull);

    JTextField x = new JTextField(6);
    JTextField y = new JTextField(6);
    JTextField w = new JTextField(6);
    JTextField h = new JTextField(6);
    x.setText(""+t.rect.x);
    y.setText(""+t.rect.y);
    w.setText(""+t.rect.width);
    h.setText(""+t.rect.height);

    if (!OutputTypes.contains(t.type))
      strength = null;
    if (!InputTypes.contains(t.type) || t.type == Type.Pin)
      pull = null;
    if (InOutTypes.contains(t.type))
      activity = null;

    JTextField label = new JTextField(6);
    if (t.label != null && t.label.length() > 0)
      label.setText(t.label);

    String[] pinLabels = t.type.pinLabels(t.width);
    JTextField[] pinLocs = new JTextField[t.width];
    for (int i = 0; i < t.width; i++) {
      pinLocs[i] = new JTextField(6);
      if (t.pins[i] != null && t.pins[i].length() > 0)
        pinLocs[i].setText(t.pins[i]);
    }

    c.gridx = 0;
    c.gridy = 0;

    for (int i = 0; i < t.width; i++) {
      add(dlg, c, pinLabels[i] + " location:", pinLocs[i]);
      if (c.gridy == 32) {
        c.gridx += 2;
        c.gridy = 0;
      }
    }

    c.gridx = 0;
    c.gridy = Math.max(t.width, 32);

    add(dlg, c, "Label (optional):", label);
    add(dlg, c, "Geometry x coordinate:", x);
    add(dlg, c, "Geometry y coordinate:", y);
    add(dlg, c, "Geometry width:", w);
    add(dlg, c, "Geometry height:", h);
    add(dlg, c, "I/O standard:", standard);
    if (strength != null)
      add(dlg, c, "Drive strength:", strength);
    if (pull != null)
      add(dlg, c, "Pull behavior:", pull);
    if (activity != null)
      add(dlg, c, "Signal activity:", activity);

    final char[] result = new char[] { 'S' };

    if (removable) {
      JButton remove = new JButton("Delete Resource");
      remove.addActionListener(e -> {
        result[0] = 'R';
        dlg.setVisible(false);
      });
      dlg.add(remove, c);
      c.gridx++;
    }

    JButton cancel = new JButton("Cancel");
    cancel.addActionListener(e -> {
      result[0] = 'C';
      dlg.setVisible(false);
    });
    dlg.add(cancel, c);
    c.gridx++;

    JButton ok = new JButton("Save");
    ok.addActionListener(e -> dlg.setVisible(false));
    dlg.add(ok, c);
    c.gridx++;

    dlg.pack();
    dlg.setLocation(Projects.getCenteredLoc(dlg.getWidth(), dlg.getHeight()));
    dlg.setModal(true);
    dlg.setResizable(false);
    dlg.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
    dlg.setAlwaysOnTop(true);

    String[] pins = new String[t.width];

    int xx, yy, ww, hh;
    for (;;) {
      dlg.setVisible(true);
      if (result[0] == 'R')
        return null; // user removed resource
      if (result[0] == 'C' && removable)
        return t; // cancelled, but explicitly not removed, keep same one
      if (result[0] == 'C')
        return null; // user cancelled, we were adding a new one, so don't add it
      // ensure all locations are specified
      boolean missing = false;
      for (int i = 0; i < t.width && !missing; i++) {
        pins[i] = pinLocs[i].getText();
        missing = pins[i] == null || pins[i].isEmpty();
      }
      if (missing) {
        Errors.title("Error").show("Please specify a location for all pins.");
        continue;
      }
      try {
        xx = Integer.parseInt(x.getText());
        yy = Integer.parseInt(y.getText());
        ww = Integer.parseInt(w.getText());
        hh = Integer.parseInt(h.getText());
      } catch (NumberFormatException ex) {
        Errors.title("Error").show("Error parsing geometry.", ex);
        continue;
      }
      if (xx < 0 || yy < 0 || ww <= 0 || hh <= 0
          || xx+ww >= Board.IMG_WIDTH || yy+hh >= Board.IMG_HEIGHT) {
        Errors.title("Error").show("Invalid geometry.");
        continue;
      }
      break;
    }

    String txt = label.getText();
    if (txt != null && txt.length() == 0)
      txt = null;

    defaultStandard = (IoStandard)standard.getSelectedItem();
    if (pull != null)
      defaultPull = (PullBehavior)pull.getSelectedItem();
    if (activity != null)
      defaultActivity = (PinActivity)activity.getSelectedItem();
    if (strength != null)
      defaultStrength = (DriveStrength)strength.getSelectedItem();

    PinActivity a = activity != null ? defaultActivity : PinActivity.UNKNOWN;
    if (t.type == Type.Pin)
      a = PinActivity.ACTIVE_HIGH; // special case: Pin is always active high

    Bounds rect = Bounds.create(xx, yy, ww, hh);

    return new BoardIO(t.type, t.width, txt, rect, defaultStandard,
        pull != null ? defaultPull : PullBehavior.UNKNOWN,
        a,
        strength != null ? defaultStrength : DriveStrength.UNKNOWN,
        pins);
  }

  public boolean isInput() {
    return InputTypes.contains(type);
  }

  public boolean isInputOutput() {
    return InOutTypes.contains(type);
  }

  public boolean isOutput() {
    return OutputTypes.contains(type);
  }
  
  @Override
  public String toString() {
    if (type == Type.DIPSwitch || type == Type.Ribbon) // types with variable size
      return String.format("%s[%d bits]", type, width);
    else
      return type.toString(); // single-bit and other fixed-width types
  }

  // Postcondition: of the counts returned, at least two will be zero.
  public Int3 getPinCounts() {
    Int3 num = new Int3();
    switch (type) {
    case Button:
    case DIPSwitch:
      num.in = width;
      break;
    case Pin:
    case Ribbon:
      num.inout = width;
      break;
    case LED:
    case SevenSegment:
    case RGBLED:
      num.out = width;
      break;
    }
    return num;
  }

  // Precondition: of the counts in compWidth, at least two are zero.
  public boolean isCompatible(Int3 compWidth, Type compType) {
    if (compWidth.size() > 1) {
      // Component is multi-bit, such as PortIO, DipSwitch, Keyboard, Tty,
      // RGBLed, SevenSegment, or a multi-bit top-level input or output Ribbon.
      // Ribbon can connect to anything (so long as the directions are
      // compatible), but others must connect to the exactly matching type.
      if (compType != Type.Ribbon && compType != type)
        return false;
      // Widths must match exactly, directions must be compatible.
      Int3 rsrc = getPinCounts();
      return (compWidth.in > 0 && compWidth.in == (rsrc.in + rsrc.inout))
          || (compWidth.out > 0 && compWidth.out == (rsrc.out + rsrc.inout))
          || (compWidth.inout > 0 && compWidth.inout == rsrc.inout);
    } else {
      // Component is single-bit, such as Button, LED, or single-bit top-level
      // input or output Pin. Pin can connect to anything (so long as the
      // directions are compatible), but others must connect to the exactly
      // matching type.
      if (compType != Type.Pin && compType != type)
        return false;
      // Widths must be sufficient, directions must be compatible.
      Int3 rsrc = getPinCounts();
      return (compWidth.in == 1 && 1 <= (rsrc.in + rsrc.inout))
          || (compWidth.out == 1 && 1 <= (rsrc.out + rsrc.inout))
          || (compWidth.inout == 1 && 1 <= rsrc.inout);
    }
  }

  public String[] pinLabels() {
    return type.pinLabels(width);
  }

}

