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

package com.bfh.logisim.fpgagui;

import java.awt.Color;
import java.awt.Dialog.ModalityType;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.io.File;
import java.util.ArrayList;
import java.util.Set;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JToggleButton;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.filechooser.FileFilter;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.bfh.logisim.designrulecheck.CorrectLabel;
import com.bfh.logisim.fpgaboardeditor.BoardInformation;
import com.bfh.logisim.fpgaboardeditor.BoardRectangle;
import com.bfh.logisim.fpgaboardeditor.FPGAIOInformationContainer;
import com.bfh.logisim.fpgaboardeditor.Strings;
import com.bfh.logisim.fpgaboardeditor.Strings;
import com.bfh.logisim.hdlgenerator.IOComponentInformationContainer;

public class ComponentMapDialog implements ActionListener,
       ListSelectionListener {

  private class MappedComponentIdContainer {

    private String key;
    private BoardRectangle rect;

    public MappedComponentIdContainer(String key, BoardRectangle SelectedItem) {
      rect = SelectedItem;
      String label = rect.GetLabel();
      if (label != null && label.length() != 0) {
        if (key == null || key.length() == 0)
          key = "[" + label + "]";
        else
          key = "[" + label + "] " + key;
      }
      this.key = key;
    }

    public BoardRectangle getRectangle() {
      return rect;
    }

    public void Paint(Graphics g) {
      FontMetrics metrics = g.getFontMetrics(g.getFont());
      Color Yellow = new Color(250, 250, 0, 180);
      Color Blue = new Color(0, 0, 250, 180);
      int hgt = metrics.getHeight();
      int adv = metrics.stringWidth(key);
      int real_xpos = rect.getXpos() - adv / 2 - 2;
      if (real_xpos < 0) {
        real_xpos = 0;
      } else if (real_xpos + adv + 4 > image_width) {
        real_xpos = image_width - adv - 4;
      }
      int real_ypos = rect.getYpos();
      if (real_ypos - hgt - 4 < 0) {
        real_ypos = hgt + 4;
      }
      g.setColor(Yellow);
      g.fillRect(real_xpos, real_ypos - hgt - 4, adv + 4, hgt + 4);
      g.drawRect(rect.getXpos() + 1, rect.getYpos(), rect.getWidth() - 2,
          rect.getHeight() - 1);
      g.setColor(Blue);
      g.drawString(key, real_xpos + 2,
          real_ypos - 2 - metrics.getMaxDescent());
    }
  }

  @SuppressWarnings("serial")
  private class SelectionWindow extends JPanel implements MouseListener,
          MouseMotionListener {

    public SelectionWindow() {
      this.addMouseListener(this);
      this.addMouseMotionListener(this);
    }

    public int getHeight() {
      return image_height;
    }

    public int getWidth() {
      return image_width;
    }

    private void HandleSelect(MouseEvent e) {
      if (!SelectableItems.isEmpty()) {
        if (HighlightItem != null) {
          if (HighlightItem.PointInside(e.getX(), e.getY())) {
            return;
          }
        }
        BoardRectangle NewItem = null;
        /* TODO: Optimize, SLOW! */
        for (BoardRectangle Item : SelectableItems) {
          if (Item.PointInside(e.getX(), e.getY())) {
            NewItem = Item;
            break;
          }
        }
        if ((NewItem == null && HighlightItem != null)
            || (NewItem != HighlightItem)) {
          HighlightItem = NewItem;
          this.paintImmediately(0, 0, this.getWidth(),
              this.getHeight());
        }
      }
    }

    public void mouseClicked(MouseEvent e) {
    }

    public void mouseDragged(MouseEvent e) {
    }

    public void mouseEntered(MouseEvent e) {
    }

    public void mouseExited(MouseEvent e) {
      Note = null;
      paintImmediately(getBounds());
    }

    public void mouseMoved(MouseEvent e) {
      HandleSelect(e);
      if (MappableComponents.hasMappedComponents() || !SelectableItems.isEmpty()) {
        if (Note != null) {
          if (Note.getRectangle().PointInside(e.getX(), e.getY())) {
            return;
          }
        }
        BoardRectangle NewItem = null;
        String newKey = "";
        /* TODO: This makes the things very slow optimize! */
        for (BoardRectangle ThisItem : MappableComponents.GetMappedRectangles()) {
          if (ThisItem.PointInside(e.getX(), e.getY())) {
            NewItem = ThisItem;
            newKey = MappableComponents.GetDisplayName(ThisItem);
            break;
          }
        }
        if (NewItem == null) {
          for (BoardRectangle Item : SelectableItems) {
            if (Item.PointInside(e.getX(), e.getY()) && Item.GetLabel() != null && Item.GetLabel().length() != 0) {
              NewItem = Item;
              break;
            }
          }
        }
        if (Note == null) {
          if (NewItem != null) {
            Note = new MappedComponentIdContainer(newKey, NewItem);
            this.paintImmediately(0, 0, this.getWidth(),
                this.getHeight());
          }
        } else {
          if (!Note.getRectangle().equals(NewItem)) {
            if (NewItem != null) {
              Note = new MappedComponentIdContainer(newKey, NewItem);
              this.paintImmediately(0, 0, this.getWidth(), this.getHeight());
            } else {
              Note = null;
              this.paintImmediately(0, 0, this.getWidth(), this.getHeight());
            }
          }
        }
      }
    }

    public void mousePressed(MouseEvent e) {
    }

    public void mouseReleased(MouseEvent e) {
      if (HighlightItem != null) {
        MapOne();
      }
    }

    public void paint(Graphics g) {
      super.paint(g);
      Color Black = new Color(0, 0, 0, 150);
      Image image = BoardInfo.GetImage().getScaledInstance(image_width,
          image_height, Image.SCALE_SMOOTH);
      if (image != null) {
        g.drawImage(image, 0, 0, null);
      }
      for (BoardRectangle rect : MappableComponents.GetMappedRectangles()) {
        if (rect.getWidth() == 0 && rect.getHeight() == 0)
          continue;
        boolean cadre = false;
        if (MappedHighlightItem != null) {
          if (MappedHighlightItem.equals(rect)) {
            g.setColor(Color.RED);
            cadre = true;
          } else {
            g.setColor(Black);
          }
        } else {
          g.setColor(Black);
        }
        g.fillRect(rect.getXpos(), rect.getYpos(), rect.getWidth(),
            rect.getHeight());
        if (cadre) {
          g.setColor(Black);
          g.drawRect(rect.getXpos(), rect.getYpos(), rect.getWidth(),
              rect.getHeight());
          if ((rect.getWidth() >= 4) && (rect.getHeight() >= 4)) {
            g.drawRect(rect.getXpos() + 1, rect.getYpos() + 1,
                rect.getWidth() - 2, rect.getHeight() - 2);
          }
        }
      }
      Color test = new Color(255, 0, 0, 100);
      for (BoardRectangle rect : SelectableItems) {
        if (rect.getWidth() == 0 && rect.getHeight() == 0)
          continue;
        g.setColor(test);
        g.fillRect(rect.getXpos(), rect.getYpos(), rect.getWidth(),
            rect.getHeight());
      }
      if (HighlightItem != null && ComponentSelectionMode) {
        if (!(HighlightItem.getWidth() == 0 && HighlightItem.getHeight() == 0)) {
          g.setColor(Color.RED);
          g.fillRect(HighlightItem.getXpos(), HighlightItem.getYpos(),
              HighlightItem.getWidth(), HighlightItem.getHeight());
        }
      }
      if (Note != null) {
        Note.Paint(g);
      }
    }
  }

  private static class XMLFileFilter extends FileFilter {

    @Override
    public boolean accept(File f) {
      return f.isDirectory() || f.getName().endsWith(".xml")
          || f.getName().endsWith(".XML");
    }

    @Override
    public String getDescription() {
      return Strings.get("XMLFileFilter"); // TODO: language adaptation
    }
  }

  private JDialog panel;
  private boolean doneAssignment = false;
  private JToggleButton ones = new JToggleButton();
  private JToggleButton zeros = new JToggleButton();
  private JToggleButton constants = new JToggleButton();
  // private JToggleButton undef = new JToggleButton();
  private JToggleButton bitbucket = new JToggleButton();
  private ArrayList<JToggleButton> constbuttons = new ArrayList<>();
  private JButton UnMapButton = new JButton();
  private JButton UnMapAllButton = new JButton();
  private JButton DoneButton = new JButton();
  private JButton SaveButton = new JButton();
  private JButton CancelButton = new JButton();
  private JButton LoadButton = new JButton();
  private JLabel MessageLine = new JLabel();
  @SuppressWarnings("rawtypes")
  private JList UnmappedList;
  @SuppressWarnings("rawtypes")
  private JList MappedList;
  private SelectionWindow BoardPic;
  private boolean ComponentSelectionMode;
  private BoardRectangle HighlightItem = null;
  private BoardRectangle MappedHighlightItem = null;
  private int image_width = 740;
  private int image_height = 400;
  private BoardInformation BoardInfo;
  private ArrayList<BoardRectangle> SelectableItems = new ArrayList<BoardRectangle>();
  private String OldDirectory = "";
  private String[] MapSectionStrings = { "Key", "LocationX", "LocationY",
    "Width", "Height", "Kind", "Value" };

  private MappedComponentIdContainer Note;

  private MappableResourcesContainer MappableComponents;

  private MouseListener mouseListener = new MouseListener() {
    @Override
    public void mouseClicked(MouseEvent e) {
      if ((e.getClickCount() == 2)
          && (e.getButton() == MouseEvent.BUTTON1)
          && (UnmappedList.getSelectedValue() != null)) {
        int idx = UnmappedList.getSelectedIndex();
        String item = UnmappedList.getSelectedValue().toString();
        MappableComponents.ToggleAlternateMapping(item);
        RebuildSelectionLists();
        UnmappedList.setSelectedIndex(idx);
      }
    }

    @Override
    public void mouseEntered(MouseEvent e) {
    }

    @Override
    public void mouseExited(MouseEvent e) {
    }

    @Override
    public void mousePressed(MouseEvent e) {
    }

    @Override
    public void mouseReleased(MouseEvent e) {
    }
  };

  static ImageIcon getIcon(String name) {
    String path ="resources/logisim/icons/" + name;
    java.net.URL url = ComponentMapDialog.class.getClassLoader().getResource(path);
    return url == null ? null : new ImageIcon(url);
  }

  @SuppressWarnings("rawtypes")
  public ComponentMapDialog(JFrame parrentFrame, String projectPath) {

    OldDirectory = new File(projectPath).getParent();
    if (OldDirectory == null)
      OldDirectory = "";
    else if (OldDirectory.length() != 0 && !OldDirectory.endsWith(File.separator))
      OldDirectory += File.separator;

    panel = new JDialog(parrentFrame, ModalityType.APPLICATION_MODAL);
    panel.setTitle("Component to FPGA board mapping");
    panel.setResizable(false);
    panel.setAlwaysOnTop(false);
    panel.setDefaultCloseOperation(JDialog.HIDE_ON_CLOSE);

    GridBagLayout thisLayout = new GridBagLayout();
    GridBagConstraints c = new GridBagConstraints();
    panel.setLayout(thisLayout);
    // PointerInfo mouseloc = MouseInfo.getPointerInfo();
    // Point mlocation = mouseloc.getLocation();
    // panel.setLocation(mlocation.x, mlocation.y);

    /* Add the board Picture */
    BoardPic = new SelectionWindow();
    BoardPic.setPreferredSize(new Dimension(BoardPic.getWidth(), BoardPic.getHeight()));
    c.gridx = 0;
    c.gridy = 0;
    c.gridwidth = 3;
    c.fill = GridBagConstraints.HORIZONTAL;
    panel.add(BoardPic, c);

    /* Buttons for constants and disconnected pins */
    ones = new JToggleButton("Ones", getIcon("ones.gif"));
    zeros = new JToggleButton("Zeros", getIcon("zeros.gif"));
    constants = new JToggleButton("Constant", getIcon("constants.gif"));
    // undef = new JToggleButton("Undefined", getIcon("undefined.gif"));
    bitbucket = new JToggleButton("Disconnected", getIcon("disconnected.gif"));
    ones.setToolTipText("Use constant 1 (or all ones) for selected input signal.");
    zeros.setToolTipText("Use constant 0 (or all zeros) for selected input signal.");
    constants.setToolTipText("Define a constant to use for selected input signal.");
    // undef.setToolTipText("Leave the selcted input signal undefined (a bad idea).");
    bitbucket.setToolTipText("Leave the selected output signal disconnected.");
    constbuttons.clear();
    constbuttons.add(ones);
    constbuttons.add(zeros);
    constbuttons.add(constants);
    // constbuttons.add(undef);
    constbuttons.add(bitbucket);
    for (JToggleButton b : constbuttons) {
      b.setEnabled(false);
      b.addItemListener(new ItemListener() {
        Color oldBg = b.getBackground();
        public void itemStateChanged(ItemEvent ev) {
          if (oldBg == null)
            oldBg = b.getBackground();
          if(ev.getStateChange() == ItemEvent.SELECTED)
            b.setBackground(new Color(200, 0, 0));
          else if (ev.getStateChange() == ItemEvent.DESELECTED)
            b.setBackground(oldBg);
        }
      });
      b.addActionListener(this);
    }
    JPanel buttonpanel = new JPanel();
    for (JToggleButton b : constbuttons)
      buttonpanel.add(b);
    c.gridy++;
    panel.add(buttonpanel, c);

    /* Add some text */
    JLabel UnmappedText = new JLabel();
    UnmappedText.setText("Unmapped Components: ");
    UnmappedText.setHorizontalTextPosition(JLabel.CENTER);
    UnmappedText.setPreferredSize(new Dimension((BoardPic.getWidth() * 2) / 5, 25));
    UnmappedText.setToolTipText("<html>Select component and place it on the board.<br>"
        + "Double click to expand component (PortIO, DIP, ...) or<br>"
        + "or change component type (Button, Pin, ...).</html>");
    c.gridx = 0;
    c.gridy++;
    c.gridwidth = 1;
    panel.add(UnmappedText, c);
    JLabel MappedText = new JLabel();
    MappedText.setText("Mapped Components:");
    MappedText.setHorizontalTextPosition(JLabel.CENTER);
    MappedText.setPreferredSize(new Dimension((BoardPic.getWidth() * 2) / 5, 25));
    c.gridx = 1;
    panel.add(MappedText, c);

    JLabel CommandText = new JLabel();
    CommandText.setText("");
    CommandText.setHorizontalTextPosition(JLabel.CENTER);
    CommandText.setPreferredSize(new Dimension((BoardPic.getWidth()) / 5, 25));
    c.gridx = 2;
    panel.add(CommandText, c);

    /* Add the unmapped list */
    UnmappedList = new JList();
    UnmappedList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    UnmappedList.addListSelectionListener(this);
    UnmappedList.addMouseListener(mouseListener);
    JScrollPane UnMappedPane = new JScrollPane(UnmappedList);
    UnMappedPane.setPreferredSize(new Dimension((BoardPic.getWidth() * 2) / 5, 150));
    c.gridx = 0;
    c.gridy++;
    c.gridheight = 9;
    panel.add(UnMappedPane, c);
    ComponentSelectionMode = false;

    /* Add the mapped list */
    MappedList = new JList();
    MappedList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    MappedList.addListSelectionListener(this);
    JScrollPane MappedPane = new JScrollPane(MappedList);
    MappedPane.setPreferredSize(new Dimension((BoardPic.getWidth() * 2) / 5, 150));
    c.gridx = 1;
    c.gridheight = 9;
    panel.add(MappedPane, c);

    c.gridheight = 1;

    /* Add the UnMap button */
    UnMapButton.setText("Release component");
    UnMapButton.setActionCommand("UnMap");
    UnMapButton.addActionListener(this);
    UnMapButton.setEnabled(false);
    c.gridx = 2;
    c.gridy++;
    panel.add(UnMapButton, c);

    /* Add the UnMapAll button */
    UnMapAllButton.setText("Release all components");
    UnMapAllButton.setActionCommand("UnMapAll");
    UnMapAllButton.addActionListener(this);
    UnMapAllButton.setEnabled(false);
    c.gridy++;
    panel.add(UnMapAllButton, c);

    /* Add the Load button */
    LoadButton.setText("Load Map");
    LoadButton.setActionCommand("Load");
    LoadButton.addActionListener(this);
    LoadButton.setEnabled(true);
    c.gridy++;
    panel.add(LoadButton, c);

    /* Add the Save button */
    SaveButton.setText("Save Map");
    SaveButton.setActionCommand("Save");
    SaveButton.addActionListener(this);
    SaveButton.setEnabled(false);
    c.gridy++;
    panel.add(SaveButton, c);

    /* Add the Cancel button */
    CancelButton.setText("Cancel");
    CancelButton.setActionCommand("Cancel");
    CancelButton.addActionListener(this);
    CancelButton.setEnabled(true);
    c.gridy++;
    panel.add(CancelButton, c);

    /* Add the Done button */
    DoneButton.setText("Done");
    DoneButton.setActionCommand("Done");
    DoneButton.addActionListener(this);
    DoneButton.setEnabled(false);
    c.gridy++;
    panel.add(DoneButton, c);

    /* Add the message line */
    MessageLine.setForeground(Color.BLUE);
    MessageLine.setText("No messages");
    MessageLine.setEnabled(true);
    c.gridx = 0;
    c.gridy++;
    c.gridwidth = 3;
    panel.add(MessageLine, c);

    panel.pack();
    /*
     * panel.setLocation(Projects.getCenteredLoc(panel.getWidth(),
     * panel.getHeight()));
     */
    panel.setLocationRelativeTo(parrentFrame);
    panel.setVisible(false);
  }

  public void actionPerformed(ActionEvent e) {
    if (e.getActionCommand().equals("Done")) {
      doneAssignment = true;
      panel.setVisible(false);
    } else if (e.getActionCommand().equals("UnMapAll")) {
      doneAssignment = false;
      UnMapAll();
    } else if (e.getActionCommand().equals("UnMap")) {
      doneAssignment = false;
      UnMapOne();
    } else if (e.getActionCommand().equals("Save")) {
      Save();
    } else if (e.getActionCommand().equals("Load")) {
      Load();
    } else if (e.getActionCommand().equals("Cancel")) {
      doneAssignment = false;
      panel.dispose();
    } else if (e.getSource() == ones) {
      if (ones.isSelected())
        MapOne(BoardRectangle.ones());
      else
        UnMapOne();
    } else if (e.getSource() == zeros) {
      if (zeros.isSelected())
        MapOne(BoardRectangle.zeros());
      else
        UnMapOne();
    } else if (e.getSource() == constants) {
      if (constants.isSelected()) {
        int val = 0;
        while (true) {
          Object sel = JOptionPane.showInputDialog(panel,
              "Enter a constant integer value (signed decimal, hex, or octal):", "Define Constant",
              JOptionPane.QUESTION_MESSAGE, null, null, "0x00000000");
          if (sel == null || sel.equals(""))
            return;
          try {
            val = Integer.decode(""+sel);
            break;
          } catch (NumberFormatException ex) { }
        }
        MapOne(BoardRectangle.constant(val));
      } else {
        UnMapOne();
      }
    // } else if (e.getSource() == undef) {
    //   if (undef.isSelected())
    //     MapOne(BoardRectangle.undefined());
    //   else
    //     UnMapOne();
    } else if (e.getSource() == bitbucket) {
      if (bitbucket.isSelected())
        MapOne(BoardRectangle.disconnected());
      else
        UnMapOne();
    }
  }

  private void ClearSelections() {
    MappedHighlightItem = null;
    HighlightItem = null;
    UnMapButton.setEnabled(false);
    MappedList.clearSelection();
    UnmappedList.clearSelection();
    ComponentSelectionMode = false;
    SelectableItems.clear();
    for (JToggleButton b : constbuttons) {
      b.setEnabled(false);
      b.setSelected(false);
    }
  }

  private String getFileName(String window_name, String suggested_name) {
    JFileChooser fc = new JFileChooser(OldDirectory);
    fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
    fc.setDialogTitle(window_name);
    File SelFile = new File(OldDirectory + suggested_name);
    fc.setSelectedFile(SelFile);
    FileFilter ff = new FileFilter() {
      @Override
      public boolean accept(File f) {
        return true; // f.isDirectory();
      }

      @Override
      public String getDescription() {
        return "Select Filename";
      }
    };
    fc.setFileFilter(ff);
    fc.setAcceptAllFileFilterUsed(false);
    int retval = fc.showSaveDialog(null);
    if (retval == JFileChooser.APPROVE_OPTION) {
      File file = fc.getSelectedFile();
      if (file.getParent() != null) {
        OldDirectory = file.getParent();
        if (OldDirectory == null)
          OldDirectory = "";
        else if (OldDirectory.length() != 0 && !OldDirectory.endsWith(File.separator))
          OldDirectory += File.separator;
      }
      return file.getPath();
    } else {
      return "";
    }
  }

  public boolean isDoneAssignment() {
    return doneAssignment;
  }

  public void LoadDefaultSaved() {
    String suggestedName =
        CorrectLabel.getCorrectLabel(MappableComponents.GetToplevelName())
        + "-" + BoardInfo.getBoardName() + "-MAP.xml";

  }

  private void Load() {
    JFileChooser fc = new JFileChooser(OldDirectory);
    fc.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
    fc.setDialogTitle("Choose XML board description file to use");
    FileFilter XML_FILTER = new XMLFileFilter();
    fc.setFileFilter(XML_FILTER);
    fc.setAcceptAllFileFilterUsed(false);
    panel.setVisible(false);
    int retval = fc.showOpenDialog(null);
    if (retval == JFileChooser.APPROVE_OPTION) {
      File file = fc.getSelectedFile();
      String FileName = file.getName();
      String AbsoluteFileName = file.getPath();
      OldDirectory = AbsoluteFileName.substring(0,
          AbsoluteFileName.length() - FileName.length());
      try {
        // Create instance of DocumentBuilderFactory
        DocumentBuilderFactory factory = DocumentBuilderFactory
            .newInstance();
        // Get the DocumentBuilder
        DocumentBuilder parser = factory.newDocumentBuilder();
        // Create blank DOM Document
        File xml = new File(AbsoluteFileName);
        Document MapDoc = parser.parse(xml);
        NodeList Elements = MapDoc
            .getElementsByTagName("LogisimGoesFPGABoardMapInformation");
        Node CircuitInfo = Elements.item(0);
        NodeList CircuitInfoDetails = CircuitInfo.getChildNodes();
        for (int i = 0; i < CircuitInfoDetails.getLength(); i++) {
          if (CircuitInfoDetails.item(i).getNodeName().equals("GlobalMapInformation")) {
            NamedNodeMap Attrs = CircuitInfoDetails.item(i)
                .getAttributes();
            for (int j = 0; j < Attrs.getLength(); j++) {
              if (Attrs.item(j).getNodeName().equals("BoardName")) {
                if (!BoardInfo.getBoardName().equals(Attrs.item(j).getNodeValue())) {
                  MessageLine.setForeground(Color.RED);
                  MessageLine
                      .setText("LOAD ERROR: The selected Map file is not for the selected target board!");
                  panel.setVisible(true);
                  return;
                }
              } else if (Attrs.item(j).getNodeName().equals("ToplevelCircuitName")) {
                if (!MappableComponents.GetToplevelName().equals(Attrs.item(j).getNodeValue())) {
                  MessageLine.setForeground(Color.RED);
                  MessageLine
                      .setText("LOAD ERROR: The selected Map file is not for the selected toplevel circuit!");
                  panel.setVisible(true);
                  return;
                }
              }
            }
            break;
          }
        }
        /* cleanup the current map */
        UnMapAll();
        for (int i = 0; i < CircuitInfoDetails.getLength(); i++) {
          if (CircuitInfoDetails.item(i).getNodeName().startsWith("MAPPEDCOMPONENT")) {
            int x = -1, y = -1, width = -1, height = -1, constval = -1;
            String key = "", kind = "";
            NamedNodeMap Attrs = CircuitInfoDetails.item(i).getAttributes();
            for (int j = 0; j < Attrs.getLength(); j++) {
              if (Attrs.item(j).getNodeName().equals(MapSectionStrings[0]))
                key = Attrs.item(j).getNodeValue();
              if (Attrs.item(j).getNodeName().equals(MapSectionStrings[1]))
                x = Integer.parseInt(Attrs.item(j).getNodeValue());
              if (Attrs.item(j).getNodeName().equals(MapSectionStrings[2]))
                y = Integer.parseInt(Attrs.item(j).getNodeValue());
              if (Attrs.item(j).getNodeName().equals(MapSectionStrings[3]))
                width = Integer.parseInt(Attrs.item(j).getNodeValue());
              if (Attrs.item(j).getNodeName().equals(MapSectionStrings[4]))
                height = Integer.parseInt(Attrs.item(j).getNodeValue());
              if (Attrs.item(j).getNodeName().equals(MapSectionStrings[5]))
                kind = Attrs.item(j).getNodeValue();
              if (Attrs.item(j).getNodeName().equals(MapSectionStrings[6]))
                constval = Integer.parseInt(Attrs.item(j).getNodeValue());
            }
            BoardRectangle rect = null;
            if (key.isEmpty()) {
              rect = null;
            } else if ("constant".equals(kind)) {
              rect = BoardRectangle.constant(constval);
            } else if ("device".equals(kind) || "".equals(kind)) {
              if ((x > 0) && (y > 0) && (width > 0) && (height > 0)) {
                for (FPGAIOInformationContainer comp : BoardInfo.GetAllComponents()) {
                  if ((comp.GetRectangle().getXpos() == x)
                      && (comp.GetRectangle().getYpos() == y)
                      && (comp.GetRectangle().getWidth() == width)
                      && (comp.GetRectangle().getHeight() == height)) {
                    rect = comp.GetRectangle();
                    break;
                  }
                }
              }
            } else if ("ones".equals(kind)) {
              rect = BoardRectangle.ones();
            } else if ("zeros".equals(kind)) {
              rect = BoardRectangle.zeros();
            // } else if ("undefined".equals(kind)) {
            //   rect = BoardRectangle.undefined();
            } else if ("disconnected".equals(kind)) {
              rect = BoardRectangle.disconnected();
            } 
            if (rect != null)
              MappableComponents.TryMap(key, rect/* , BoardInfo.GetComponentType(rect) */);
          }
        }
        ClearSelections();
        RebuildSelectionLists();
        BoardPic.paintImmediately(0, 0, BoardPic.getWidth(),
            BoardPic.getHeight());
      } catch (Exception e) {
        /* TODO: handle exceptions */
        System.err.printf(
            "Exceptions not handled yet in Load(), but got an exception: %s\n",
            e.getMessage());
      }
    }
    panel.setVisible(true);
  }

  private void MapOne(BoardRectangle r) {
    HighlightItem = r;
    MapOne();
  }

  private void MapOne() {
    if (UnmappedList.getSelectedIndex() >= 0) {
      String key = UnmappedList.getSelectedValue().toString();
      if (HighlightItem != null) {
        MappableComponents.Map(key, HighlightItem /*, BoardInfo.GetComponentType(HighlightItem)*/);
        RebuildSelectionLists();
      }
    } else if (MappedList.getSelectedIndex() >= 0) {
      String key = MappedList.getSelectedValue().toString();
      if (HighlightItem != null) {
        MappableComponents.Map(key, HighlightItem /*, BoardInfo.GetComponentType(HighlightItem)*/);
      }
    }
    ClearSelections();
    BoardPic.paintImmediately(0, 0, BoardPic.getWidth(),
        BoardPic.getHeight());
    UnmappedList.setSelectedIndex(0);
  }

  @SuppressWarnings({ "unchecked", "rawtypes" })
  private void RebuildSelectionLists() {
    UnmappedList.clearSelection();
    MappedList.clearSelection();
    Set<String> Unmapped = MappableComponents.UnmappedList();
    Set<String> Mapped = MappableComponents.MappedList();
    JList unmapped = new JList(Unmapped.toArray());
    UnmappedList.setModel(unmapped.getModel());
    JList mapped = new JList(Mapped.toArray());
    MappedList.setModel(mapped.getModel());
    UnmappedList.paintImmediately(0, 0, UnmappedList.getBounds().width,
        UnmappedList.getBounds().height);
    MappedList.paintImmediately(0, 0, MappedList.getBounds().width,
        MappedList.getBounds().height);
    UnMapAllButton.setEnabled(!Mapped.isEmpty());
    CancelButton.setEnabled(true);
    DoneButton.setEnabled(Unmapped.isEmpty());
    SaveButton.setEnabled(!Mapped.isEmpty());
  }

  private void Save() {
    panel.setVisible(false);
    String suggestedName =
        CorrectLabel.getCorrectLabel(MappableComponents.GetToplevelName())
        + "-" + BoardInfo.getBoardName() + "-MAP.xml";
    String SaveFileName = getFileName("Select filename to save the current map", suggestedName);
    if (!SaveFileName.isEmpty()) {
      try {
        // Create instance of DocumentBuilderFactory
        DocumentBuilderFactory factory = DocumentBuilderFactory
            .newInstance();
        // Get the DocumentBuilder
        DocumentBuilder parser = factory.newDocumentBuilder();
        // Create blank DOM Document
        Document MapInfo = parser.newDocument();

        Element root = MapInfo
            .createElement("LogisimGoesFPGABoardMapInformation");
        MapInfo.appendChild(root);
        Element CircuitInfo = MapInfo
            .createElement("GlobalMapInformation");
        CircuitInfo.setAttribute("BoardName", BoardInfo.getBoardName());
        Attr circ = MapInfo.createAttribute("ToplevelCircuitName");
        circ.setNodeValue(MappableComponents.GetToplevelName());
        CircuitInfo.setAttributeNode(circ);
        root.appendChild(CircuitInfo);
        int count = 1;
        for (String key : MappableComponents.MappedList()) {
          Element Map = MapInfo.createElement("MAPPEDCOMPONENT_"
              + Integer.toHexString(count++));
          BoardRectangle rect = MappableComponents.GetMap(key);
          Map.setAttribute(MapSectionStrings[0], key);
          Attr k = MapInfo.createAttribute(MapSectionStrings[5]);
          k.setValue("constant");
          Map.setAttributeNode(k);
          if (rect.isConstantInput()) {
            Attr v = MapInfo.createAttribute(MapSectionStrings[6]);
            v.setValue(Integer.toString(rect.getSyntheticInputValue()));
            Map.setAttributeNode(v);
          } else if (!rect.isDeviceSignal()) {
            Attr xpos = MapInfo.createAttribute(MapSectionStrings[1]);
            xpos.setValue(Integer.toString(rect.getXpos()));
            Map.setAttributeNode(xpos);
            Attr ypos = MapInfo.createAttribute(MapSectionStrings[2]);
            ypos.setValue(Integer.toString(rect.getYpos()));
            Map.setAttributeNode(ypos);
            Attr width = MapInfo.createAttribute(MapSectionStrings[3]);
            width.setValue(Integer.toString(rect.getWidth()));
            Map.setAttributeNode(width);
            Attr height = MapInfo.createAttribute(MapSectionStrings[4]);
            height.setValue(Integer.toString(rect.getHeight()));
            Map.setAttributeNode(height);
          }
          root.appendChild(Map);
        }
        TransformerFactory tranFactory = TransformerFactory.newInstance();
        tranFactory.setAttribute("indent-number", 3);
        Transformer aTransformer = tranFactory.newTransformer();
        aTransformer.setOutputProperty(OutputKeys.INDENT, "yes");
        Source src = new DOMSource(MapInfo);
        File file = new File(SaveFileName);
        Result dest = new StreamResult(file);
        aTransformer.transform(src, dest);
      } catch (Exception e) {
        /* TODO: handle exceptions */
        System.err.printf(
            "Exceptions not handled yet in Save(), but got an exception: %s\n",
            e.getMessage());
      }
    }
    panel.setVisible(true);
  }

  public void SetBoardInformation(BoardInformation Board) {
    BoardInfo = Board;
  }

  public void SetMappebleComponents(MappableResourcesContainer mappable) {
    MappableComponents = mappable;
    RebuildSelectionLists();
    ClearSelections();
  }

  public void SetVisible(boolean selection) {
    MessageLine.setForeground(Color.BLUE);
    MessageLine.setText("No messages");
    panel.setVisible(selection);
  }

  private void UnMapAll() {
    ClearSelections();
    MappableComponents.UnmapAll();
    MappableComponents.rebuildMappedLists();
    BoardPic.paintImmediately(0, 0, BoardPic.getWidth(),
        BoardPic.getHeight());
    RebuildSelectionLists();
  }

  private void UnMapOne() {
    if (MappedList.getSelectedIndex() >= 0) {
      String key = MappedList.getSelectedValue().toString();
      MappableComponents.UnMap(key);
      ClearSelections();
      RebuildSelectionLists();
      BoardPic.paintImmediately(0, 0, BoardPic.getWidth(),
          BoardPic.getHeight());
    }
  }

  @Override
  public void valueChanged(ListSelectionEvent e) {
    if (e.getSource() == MappedList) {
      if (MappedList.getSelectedIndex() >= 0) {
        UnmappedList.clearSelection();
        UnMapButton.setEnabled(true);
        MappedHighlightItem = MappableComponents.GetMap(MappedList.getSelectedValue().toString());
        BoardPic.paintImmediately(0, 0, BoardPic.getWidth(), BoardPic.getHeight());
        ComponentSelectionMode = true;
        SelectableItems.clear();
        String DisplayName = MappedList.getSelectedValue().toString();
        SelectableItems = MappableComponents.GetSelectableItemsList(DisplayName, BoardInfo);
        IOComponentInformationContainer info = MappableComponents.getTypeFor(DisplayName);
        ones.setEnabled(info.CanBeAllOnesInput());
        zeros.setEnabled(info.CanBeAllZerosInput());
        constants.setEnabled(info.CanBeConstantInput());
        // undef.setEnabled(info.CanBeUndefinedInput());
        bitbucket.setEnabled(info.CanBeDisconnectedOutput());
        ones.setSelected(MappedHighlightItem.isAllOnesInput());
        zeros.setSelected(MappedHighlightItem.isAllZerosInput());
        constants.setSelected(MappedHighlightItem.isConstantInput());
        // undef.setSelected(MappedHighlightItem.isUndefinedInput());
        bitbucket.setSelected(MappedHighlightItem.isDisconnectedOutput());
        BoardPic.paintImmediately(BoardPic.getBounds());
      } else {
        ComponentSelectionMode = false;
        SelectableItems.clear();
        Note = null;
        MappedHighlightItem = null;
        HighlightItem = null;
        for (JToggleButton b : constbuttons) {
          b.setEnabled(false);
          b.setSelected(false);
        }
      }
    } else if (e.getSource() == UnmappedList) {
      if (UnmappedList.getSelectedIndex() < 0) {
        ComponentSelectionMode = false;
        SelectableItems.clear();
        Note = null;
        MappedHighlightItem = null;
        HighlightItem = null;
        for (JToggleButton b : constbuttons) {
          b.setEnabled(false);
          b.setSelected(false);
        }
      } else {
        MappedList.clearSelection();
        ComponentSelectionMode = true;
        SelectableItems.clear();
        String DisplayName = UnmappedList.getSelectedValue().toString();
        SelectableItems = MappableComponents.GetSelectableItemsList(DisplayName, BoardInfo);
        IOComponentInformationContainer info = MappableComponents.getTypeFor(DisplayName);
        ones.setEnabled(info.CanBeAllOnesInput());
        zeros.setEnabled(info.CanBeAllZerosInput());
        constants.setEnabled(info.CanBeConstantInput());
        // undef.setEnabled(info.CanBeUndefinedInput());
        bitbucket.setEnabled(info.CanBeDisconnectedOutput());
        for (JToggleButton b : constbuttons)
          b.setSelected(false);
      }
      MappedHighlightItem = null;
      UnMapButton.setEnabled(false);
      CancelButton.setEnabled(true);
      BoardPic.paintImmediately(BoardPic.getBounds());
      Color test = new Color(255, 0, 0, 100);
    }
  }

}
