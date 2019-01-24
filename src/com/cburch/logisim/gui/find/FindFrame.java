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

package com.cburch.logisim.gui.find;
import static com.cburch.logisim.gui.find.Strings.S;

import java.awt.Font;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.HashMap;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.Map;
import java.util.List;

import javax.swing.AbstractListModel;
import javax.swing.UIManager;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListCellRenderer;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.ListSelectionModel;
import javax.swing.JButton;
import javax.swing.BoxLayout;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.border.Border;
import javax.swing.border.TitledBorder;
import javax.swing.JRadioButton;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.ButtonGroup;
import javax.swing.JList;

import com.cburch.logisim.circuit.SubcircuitFactory;
import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.proj.Projects;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.comp.ComponentFactory;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.tools.AddTool;
import com.cburch.logisim.tools.Tool;
import com.cburch.logisim.tools.Library;
import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.std.hdl.VhdlEntity;
import com.cburch.logisim.std.base.Text;
import com.cburch.hdl.HdlModel;
import com.cburch.logisim.std.hdl.VhdlContent;
import com.cburch.logisim.circuit.Simulator;
import com.cburch.logisim.file.LibraryEvent;
import com.cburch.logisim.file.LibraryListener;
import com.cburch.logisim.gui.chrono.ChronoPanel;
import com.cburch.logisim.gui.generic.LFrame;
import com.cburch.logisim.gui.menu.LogisimMenuBar;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.proj.ProjectEvent;
import com.cburch.logisim.proj.ProjectListener;
import com.cburch.logisim.util.LocaleListener;
import com.cburch.logisim.util.LocaleManager;
import com.cburch.logisim.gui.generic.WrapLayout;

public class FindFrame extends LFrame.Dialog implements LocaleListener {
  // maybe use LFrame.SubWindow instead?

  private class TopPanel extends JPanel {
    TitledBorder findBorder = BorderFactory.createTitledBorder("");
    JTextField field = new JTextField();
    TitledBorder whereBorder = BorderFactory.createTitledBorder("");
    JRadioButton inSheet = new JRadioButton();
    JRadioButton inCircuit = new JRadioButton();
    JRadioButton inProject = new JRadioButton();
    JRadioButton inAll = new JRadioButton();
    JButton go = new JButton();

    TopPanel() {
      setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
      setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));

      JPanel input = new JPanel(new BorderLayout());
      input.setBorder(findBorder);
      input.add(field, BorderLayout.CENTER);
      add(input);

      JPanel where = new JPanel(new WrapLayout());
      where.setBorder(whereBorder);
      where.add(inSheet);
      where.add(inCircuit);
      where.add(inProject);
      where.add(inAll);
      add(where);

      JPanel cmd = new JPanel(new WrapLayout());
      cmd.add(go);
      add(cmd);

      ButtonGroup g = new ButtonGroup();
      g.add(inSheet);
      g.add(inCircuit);
      g.add(inProject);
      g.add(inAll);
      inSheet.setSelected(true);
    }
  }

  private class ResultPanel extends JList<Result> {
    ResultPanel() {
      setModel(model);
      setCellRenderer(new ResultRenderer());
      setFont(getFont().deriveFont(Font.PLAIN));
      setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
    }
  }

  private TopPanel top;
  private ResultPanel results;
  private Model model = new Model();

  public FindFrame() {
    super(null);
    top = new TopPanel();
    results = new ResultPanel();

    Container contents = getContentPane();

    GridBagLayout gb = new GridBagLayout();
    GridBagConstraints gc = new GridBagConstraints();

    JPanel boxes = new JPanel();
    boxes.setLayout(gb);
    gc.anchor = GridBagConstraints.NORTHWEST;
    gc.weightx = 1;
    gc.weighty = 0;
    gc.fill = GridBagConstraints.HORIZONTAL;
    gc.gridx = gc.gridy = 0;

    boxes.add(top, gc);

    gc.gridx = 0;
    gc.gridy++;
    gc.fill = GridBagConstraints.BOTH;
    gc.weighty = 1;
    JScrollPane scrollPane = new JScrollPane();
    scrollPane.setViewportView(results);
    boxes.add(scrollPane, gc);

    contents.add(boxes, BorderLayout.CENTER);

    setPreferredSize(new Dimension(350, 600));

    top.go.addActionListener(e -> model.update());

    results.addListSelectionListener(e -> gotoResult(results.getSelectedValue()));

    LocaleManager.addLocaleListener(this);
    localeChanged();
  }

  @Override
  public void localeChanged() {
    setTitle(S.get("findFrameTitle"));
    top.findBorder.setTitle(S.get("findTextLabel"));
    top.whereBorder.setTitle(S.get("whereLabel"));
    top.inSheet.setText(S.get("inSheet"));
    top.inCircuit.setText(S.get("inCircuit"));
    top.inProject.setText(S.get("inProject"));
    top.inAll.setText(S.get("inAll"));
    top.go.setText(S.get("findButtonLabel"));
  }

  private class Model extends AbstractListModel {
    ArrayList<Result> data = new ArrayList<>();

    @Override
    public Object getElementAt(int index) {
      return data.get(index);
    }

    @Override
    public int getSize() {
      return data.size();
    }

    void update() {
      int n = data.size();
      if (n > 0) {
        data.clear();
        fireIntervalRemoved(this, 0, n);
      }
      List<Project> projects = Projects.getOpenProjects();
      if (projects.isEmpty())
        return;
      Project proj = projects.get(0);
      String text = top.field.getText();
      // todo: regexp here?
      if (text.equals(""))
        return;
      if (top.inSheet.isSelected() || top.inCircuit.isSelected()) {
        Circuit circ = proj.getCurrentCircuit();
        HdlModel hdl = proj.getCurrentHdl();
        if (circ != null) {
          String source = proj.getLogisimFile().getName() + ", " + circ.getName();
          if (top.inCircuit.isSelected()) {
            searchAttributes(text, circ.getStaticAttributes(), source);
            searchCircuit(text, circ, source, new HashSet<Object>());
          } else {
            searchCircuit(text, circ, source, null); // non recursive
          }
        } else if (hdl != null) {
          String source = proj.getLogisimFile().getName() + ", " + hdl.getName();
          searchHdl(text, hdl, source);
        }
      } else if (top.inProject.isSelected()) {
        HashSet<Library> searched = new HashSet<>();
        searchLibrary(text, proj.getLogisimFile(), proj.getLogisimFile().getName(), searched);
      } else {
        HashSet<Library> searched = new HashSet<>();
        for (Project p : projects)
          searchLibrary(text, p.getLogisimFile(), p.getLogisimFile().getName(), searched);
      }
    }

    void searchLibrary(String text, Library lib, String source, HashSet<Library> searched) {
      if (searched.contains(lib))
        return;
      searched.add(lib);
      searchText(text, lib.getDisplayName(), source, S.get("matchLibraryName"));
      for (Tool tool : lib.getTools()) {
        String subsource = source + ", " + tool.getDisplayName();
        searchAttributes(text, tool.getAttributeSet(), subsource);
        if (!(tool instanceof AddTool))
          continue;
        AddTool t = (AddTool)tool;
        if (t.getFactory() instanceof SubcircuitFactory) {
          Circuit circ = ((SubcircuitFactory)t.getFactory()).getSubcircuit();
          searchCircuit(text, circ, subsource, null); // non-recursive
        } else if (t.getFactory() instanceof VhdlEntity) {
          VhdlContent vhdl = ((VhdlEntity)t.getFactory()).getContent();
          searchHdl(text, vhdl, subsource);
        }
      }
      for (Library sublib : lib.getLibraries()) {
        String subsource = source + ", " + lib.getDisplayName();
        searchLibrary(text, sublib, subsource, searched);
      }
    }

    void searchCircuit(String text, Circuit circ, String source, HashSet<Object> searched) {
      if (searched != null && searched.contains(circ))
        return;
      for (Component comp : circ.getNonWires()) {
        String subsource = source + "/" + comp.getDisplayName();
        searchAttributes(text, comp.getAttributeSet(), subsource);
      }
      if (searched == null)
        return; // non-recursive
      searched.add(circ);
      for (Component comp : circ.getNonWires()) {
        ComponentFactory factory = comp.getFactory();
        if (factory instanceof VhdlEntity) {
          VhdlContent vhdl = ((VhdlEntity)factory).getContent();
          if (searched.contains(vhdl))
            continue;
          searched.add(vhdl);
          String subsource = source + "/" + comp.getDisplayName();
          searchHdl(text, vhdl, subsource);
        } else if (factory instanceof SubcircuitFactory) {
          String subsource = source + "/" + comp.getDisplayName();
          searchCircuit(text, ((SubcircuitFactory)factory).getSubcircuit(), subsource, searched);
        }
      }
    }

    void searchHdl(String text, HdlModel hdl, String source) {
      String code = hdl.getContent();
      searchText(text, code, source, null /*use line number*/);
    }

    void searchAttributes(String text, AttributeSet as, String source) {
      // if (as.contains(StdAttr.LABEL))
      //   search(text, as.getValue(StdAttr.LABEL), name + " label");
      // if (as.contains(CircuitAttributes.NAME_ATTR))
      //   search(text, as.getValue(CircuitAttributes.NAME_ATTR), name + " circuit name");
      // if (as.contains(CircuitAttributes.CIRCUIT_LABEL_ATTR))
      //   search(text, as.getValue(CircuitAttributes.CIRCUIT_LABEL_ATTR), name + " circuit shared label");
      // if (as.contains(CircuitAttributes.CIRCUIT_VHDL_PATH))
      //   search(text, as.getValue(CircuitAttributes.CIRCUIT_VHDL_PATH), name + " circuit vhdl path");
      if (as == null)
        return;
      for (Attribute<?> a : as.getAttributes()) {
        Object o = as.getValue(a);
        if (o instanceof String && a == Text.ATTR_TEXT)
          searchText(text, (String)o, source, null /* use line number */);
        else if (o instanceof String)
          searchText(text, (String)o, source, a.getDisplayName());
      }
    }

    void searchText(String text, String content, String source, String context) {
      int n = text.length();
      int s = content.indexOf(text);
      while (s >= 0) {
        String c = context;
        if (c == null) { // use line number
          int p = lineNumber(content, s);
          c = p > 0 ? S.fmt("matchTextLine", (p+1)) : S.get("matchTextContent");
        }
        add(new Result(content, s, s+n, source, c));
        s = content.indexOf(text, s+1);
      }
    }

    void add(Result result) {
      data.add(result);
      fireIntervalAdded(this, data.size()-1, data.size());
    }

  }

  static int lineNumber(String t, int s) {
    int i = t.indexOf('\n');
    if (i < 0)
      return -1;
    int n = 1;
    while (i >= 0 && i < s) {
      n++;
      i = t.indexOf('\n', i+1);
    }
    return n;
  }


  static String escapeHtml(String s) {
    StringBuilder out = new StringBuilder(Math.max(16, s.length()));
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (c > 127 || c == '"' || c == '<' || c == '>' || c == '&') {
        out.append("&#");
        out.append((int) c);
        out.append(';');
      } else {
        out.append(c);
      }
    }
    return out.toString();
  }

  private static class Result {
    String match;
    int s, e;
    String source, context;
    Result(String match, int s, int e, String source, String context) {
      this.match = match;
      this.s = s;
      this.e = e;
      this.source = source;
      this.context = context;
    }

    static final String b = "<font color=\"#820a0a\"><b>";
    static final String d = "</b></font>";

    String toHtml() {
      int n = match.length();
      if (s == 0 && e == n)
        return b + escapeHtml(match) + d;
      else if (n < 40 || n < (e-s)+10)
        return escapeHtml(match.substring(0, s))
            + b + escapeHtml(match.substring(s, e)) + d
            + escapeHtml(match.substring(e, n));
      int c = Math.min(n+10, 40);
      if (s < c/2)
        return escapeHtml(match.substring(0, s))
            + b + escapeHtml(match.substring(s, e)) + d
            + escapeHtml(match.substring(e, e+c/2)) + "...";
      else if (e >= n-c/2)
        return "..." + escapeHtml(match.substring(s-c/2, s))
            + b + escapeHtml(match.substring(s, e)) + d
            + escapeHtml(match.substring(e, n));
      else
        return "..." + escapeHtml(match.substring(s-c/2, s))
            + b + escapeHtml(match.substring(s, e)) + d
            + escapeHtml(match.substring(e, e+c/2)) + "...";
    }
  }

  private static class ResultRenderer extends DefaultListCellRenderer {
    static final String b = "<font color=\"#1a128e\">";
    static final String d = "</font>";
    static final Color color = UIManager.getDefaults().getColor("Table.gridColor");
    static final Border matteBorder = BorderFactory.createMatteBorder(0, 0, 1, 0, color);

    @Override
    public java.awt.Component getListCellRendererComponent(JList list, Object value,
        int index, boolean isSelected, boolean cellHasFocus) {
      if (value instanceof Result) {
        Result r = (Result)value;
        String src = S.fmt("matchSource", b + escapeHtml(r.source) + d);
        value = String.format("<html><small>%s:</small><br><div style=\"padding-left: 15px;\">%s: %s</div></html>",
            src, escapeHtml(r.context), r.toHtml());
      }
      java.awt.Component c
          = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
      if (c instanceof JComponent)
        ((JComponent)c).setBorder(matteBorder);
      return c;
    }
  }

  private void gotoResult(Result r) {
    if (r == null)
      return;

  }

  // Search for matching text
  // - Find on current circuit / current vhdl, vs global search
  // - Replace?
  // Things to search:
  // - vhdl entity contents (entity.getContents...)
  // - circuit contents (circ.nonWires...)
  // - circuit name CircuitAttributes.NAME_ATTR
  // - vhdl name VhdlEntity.NAME_ATTR
  // - toolbar contents (layout toolbar getItems)
  // - standard label attributes on various components (except Pin, Tunnel) StdAttr.LABEL
  // - circuit shared label attributes CircuitAttributes.CIRCUIT_LABEL_ATTR
  // - text components Text.ATTR_TEXT
  // - tunnel names StdAttr.LABEL
  // - pin names StdAttr.LABEL
  // - later: memory data ? as bytes? as strings? as hex?
  
  // Search for matching components and attributes (e.g. find all 8-wide multiplexors)
  // - Find on current circuit vs global search
  // Things to search:
  // - circuits
  // - toolbar
  // - mouse mappings

  public static void main(String[] args) throws Exception {
    FindFrame frame = new FindFrame();
    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    frame.pack();
    frame.setVisible(true);
  }
}
