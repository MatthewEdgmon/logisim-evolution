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
package com.cburch.logisim.gui.chrono;
import static com.cburch.logisim.gui.chrono.Strings.S;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;
import java.awt.print.PageFormat;
import java.awt.print.Printable;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.Box;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JViewport;
import javax.swing.KeyStroke;
import javax.swing.ScrollPaneConstants;
import javax.swing.UIManager;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import com.cburch.draw.toolbar.Toolbar;
import com.cburch.logisim.circuit.Simulator;
import com.cburch.logisim.gui.log.LogFrame;
import com.cburch.logisim.gui.log.LogPanel;
import com.cburch.logisim.gui.log.Model;
import com.cburch.logisim.gui.log.Signal;
import com.cburch.logisim.gui.log.SignalInfo;
import com.cburch.logisim.gui.main.SimulationToolbarModel;
import com.cburch.logisim.gui.menu.EditHandler;
import com.cburch.logisim.gui.menu.LogisimMenuBar;
import com.cburch.logisim.gui.menu.PrintHandler;
import com.cburch.logisim.util.GraphicsUtil;

public class ChronoPanel extends LogPanel implements Model.Listener {

  private class MyListener implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent e) {
//       // load a chronogram from a file
//       if ("load".equals(e.getActionCommand())) {
//         final JFileChooser fc = new JFileChooser();
//         int returnVal = fc.showOpenDialog(ChronoPanel.this);
//         if (returnVal == JFileChooser.APPROVE_OPTION) {
//           loadFile(fc.getSelectedFile().getAbsolutePath());
//         }
// 
//         // export a chronogram to a file
//       } else if ("export".equals(e.getActionCommand())) {
//         final JFileChooser fc = new JFileChooser();
//         int returnVal = fc.showSaveDialog(ChronoPanel.this);
//         if (returnVal == JFileChooser.APPROVE_OPTION) {
//           exportFile(fc.getSelectedFile().getAbsolutePath());
//         }
// 
//       }
//       else if ("exportImg".equals(e.getActionCommand())) {
//         final JFileChooser fc = new JFileChooser();
//         int returnVal = fc.showSaveDialog(ChronoPanel.this);
//         if (returnVal == JFileChooser.APPROVE_OPTION) {
//           File file = fc.getSelectedFile();
// 
//           //add .png to the filename if the user forgot
//           if (!fc.getSelectedFile().getAbsolutePath().endsWith(".png")) {
//             file = new File(fc.getSelectedFile() + ".png");
//           }
//           exportImage(file);
//         }
// 
//       } else if ("play".equals(e.getActionCommand())) {
//         if (simulator.isRunning()) {
//           ((JButton) e.getSource()).setIcon(Icons
//           .getIcon("simplay.png"));
//         } else {
//           ((JButton) e.getSource()).setIcon(Icons
//           .getIcon("simstop.png"));
//         }
//         simulator.setIsRunning(!simulator.isRunning());
//       } else if ("step".equals(e.getActionCommand())) {
//         simulator.step();
//       } else if ("tplay".equals(e.getActionCommand())) {
//         if (simulator.isTicking()) {
//           ((JButton) e.getSource()).setIcon(Icons
//           .getIcon("simtplay.png"));
//         } else {
//           ((JButton) e.getSource()).setIcon(Icons
//           .getIcon("simtstop.png"));
//         }
//         simulator.setIsTicking(!simulator.isTicking());
//       } else if ("thalf".equals(e.getActionCommand())) {
//         simulator.tick(1);
//       } else if ("tfull".equals(e.getActionCommand())) {
//         simulator.tick(2);
//       }
    }

  }

  public static final int HEADER_HEIGHT = 20;
  public static final int SIGNAL_HEIGHT = 30;
  public static final int GAP = 2; // gap above and below each signal
  public static final int INITIAL_SPLIT = 150;

  // state
  private Simulator simulator;
  private Model model;

  // button bar
  private JPanel buttonBar = new JPanel();
  private JButton chooseFileButton = new JButton();
  private JButton exportDataInFile = new JButton();
  private JButton exportDataToImage = new JButton();

  // panels
  private RightPanel rightPanel;
  private LeftPanel leftPanel;
  private JScrollPane leftScroll, rightScroll;
  private JSplitPane splitPane;

  // listeners
  private MyListener myListener = new MyListener();

  public ChronoPanel(LogFrame logFrame) {
    super(logFrame);

    SELECT_BG = UIManager.getDefaults().getColor("List.selectionBackground");
    SELECT_HI = darker(SELECT_BG);
    SELECT = new Color[] { SELECT_BG, SELECT_HI, SELECT_LINE, SELECT_ERR, SELECT_ERRLINE, SELECT_UNK, SELECT_UNKLINE };

    simulator = getProject().getSimulator();

    setModel(logFrame.getModel());

    configure();
    resplit();

    editHandler.computeEnabled();
    // simulationHandler.computeEnabled();
  }

  private void configure() {
    setLayout(new BorderLayout());

    // button bar
    Dimension buttonSize = new Dimension(150, 25);
    buttonBar.setLayout(new FlowLayout(FlowLayout.LEFT));

    chooseFileButton.setActionCommand("load");
    chooseFileButton.addActionListener(myListener);
    chooseFileButton.setPreferredSize(buttonSize);
    chooseFileButton.setFocusable(false);

    exportDataInFile.setActionCommand("export");
    exportDataInFile.addActionListener(myListener);
    exportDataInFile.setPreferredSize(buttonSize);
    exportDataInFile.setFocusable(false);

    exportDataToImage.setActionCommand("exportImg");
    exportDataToImage.addActionListener(myListener);
    exportDataToImage.setPreferredSize(buttonSize);
    exportDataToImage.setFocusable(false);

    LogFrame logFrame = getLogFrame();
    SimulationToolbarModel simTools;
    simTools = new SimulationToolbarModel(getProject(), logFrame.getMenuListener());
    Toolbar toolbar = new Toolbar(simTools);

    JPanel toolpanel = new JPanel();
    GridBagLayout gb = new GridBagLayout();
    GridBagConstraints gc = new GridBagConstraints();
    toolpanel.setLayout(gb);
    gc.fill = GridBagConstraints.NONE;
    gc.weightx = gc.weighty = 0.0;
    gc.gridx = gc.gridy = 0;
    gb.setConstraints(toolbar, gc);
    toolpanel.add(toolbar);

    JButton b = logFrame.makeSelectionButton();
    b.setFont(b.getFont().deriveFont(10.0f));
    Insets insets = gc.insets;
    gc.insets = new Insets(2, 0, 2, 0);
    gc.gridx = 1;
    gb.setConstraints(b, gc);
    toolpanel.add(b);
    gc.insets = insets;

    Component filler = Box.createHorizontalGlue();
    gc.fill = GridBagConstraints.HORIZONTAL;
    gc.weightx = 1.0;
    gc.gridx = 2;
    gb.setConstraints(filler, gc);
    toolpanel.add(filler);
    add(toolpanel, BorderLayout.NORTH);

    // statusLabel = new JLabel();
    buttonBar.add(chooseFileButton);
    buttonBar.add(exportDataInFile);
    buttonBar.add(exportDataToImage);
    add(BorderLayout.SOUTH, buttonBar);

    // panels
    splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
    splitPane.setDividerSize(5);
    splitPane.setResizeWeight(0.0);
    add(BorderLayout.CENTER, splitPane);

    InputMap inputMap = getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
    ActionMap actionMap = getActionMap();
    inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "ClearSelection");
    actionMap.put("ClearSelection", new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        System.out.println("chrono clear");
        leftPanel.clearSelection();
      }
    });

  }

//  public void exportFile(String file) {
//    ChronoDataWriter.export(file, timelineParam, data);
//  }
//  public void exportImage(File file) {
//    ImageExporter ie = new ImageExporter(this, data, HEADER_HEIGHT);
//    ie.createImage(file);
//  }

  private void resplit() {
    // todo: why replace panels here?
    leftPanel = new LeftPanel(this);
    leftScroll = new JScrollPane(leftPanel,
        ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS,
        ScrollPaneConstants.HORIZONTAL_SCROLLBAR_ALWAYS);

    int p = rightScroll == null ? 0 : rightScroll.getHorizontalScrollBar().getValue();
    if (rightPanel == null)
      rightPanel = new RightPanel(this, leftPanel.getSelectionModel());
    // else
    //  rightPanel = new RightPanel(rightPanel, leftPanel.getSelectionModel());

    rightScroll = new JScrollPane(rightPanel,
        ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS,
        ScrollPaneConstants.HORIZONTAL_SCROLLBAR_ALWAYS);

    // Synchronize the two vertical scrollbars (and make left one invisible)
    leftScroll.getVerticalScrollBar().setUI(null);
    leftScroll.getVerticalScrollBar().setModel(
        rightScroll.getVerticalScrollBar().getModel());

    // zoom on control+scrollwheel
    MouseAdapter zoomer = new MouseAdapter() {
      public void mouseWheelMoved(MouseWheelEvent e) {
        if (e.isControlDown()) {
          e.consume();
          rightPanel.zoom(e.getWheelRotation() > 0 ? -1 : +1, e.getPoint().x);
        }
        else
          e.getComponent().getParent().dispatchEvent(e);
      }
    };
    // We can't put it on the scroll pane, because ordering of listeners isn's
    // specified and we need to be first to prevent default scroll behavior
    // when control is down.
    // leftScroll.addMouseWheelListener(zoomer);
    // rightScroll.addMouseWheelListener(zoomer);
    leftPanel.addMouseWheelListener(zoomer);
    rightPanel.addMouseWheelListener(zoomer);
    leftPanel.getTableHeader().addMouseWheelListener(zoomer);
    rightPanel.getTimelineHeader().addMouseWheelListener(zoomer);

    splitPane.setLeftComponent(leftScroll);
    splitPane.setRightComponent(rightScroll);

    leftScroll.setWheelScrollingEnabled(true);
    rightScroll.setWheelScrollingEnabled(true);

		// setSignalCursorX(rightPanel.getSignalCursorX()); // sets cursor in both panels
    setSignalCursorX(Integer.MAX_VALUE);

    // put right scrollbar into same position
    rightScroll.getHorizontalScrollBar().setValue(p);

    // splitPane.setDividerLocation(INITIAL_SPLIT);
    
    leftPanel.getSelectionModel().addListSelectionListener(
        new ListSelectionListener() {
          public void valueChanged(ListSelectionEvent e) {
            editHandler.computeEnabled();
          }
        });
  }

  public LeftPanel getLeftPanel() {
    return leftPanel;
  }

  public RightPanel getRightPanel() {
    return rightPanel;
  }

  public JScrollBar getVerticalScrollBar() {
    return rightScroll == null ? null : rightScroll.getVerticalScrollBar();
  }

  public JScrollBar getHorizontalScrollBar() {
    return rightScroll == null ? null : rightScroll.getHorizontalScrollBar();
  }

  public JViewport getRightViewport() {
    return rightScroll == null ? null : rightScroll.getViewport();
  }

//   /**
//    * Load the chronogram from the log file
//    */
//   public void loadFile(String logFile) {
//     try {
//       ChronoData tmp = new ChronoData(logFile, this);
//       if (tmp != null) {
//         realTimeMode = false;
//         data = tmp;
//         resplit();
//         // statusLabel.setText(S.get("InputFileLoaded") + logFile);
//         System.out.println("imported file");
//       }
//     } catch (NoSysclkException ex) {
//       errorMessage(S.get("InputFileNoSysclk"));
//     } catch (Exception ex) {
//       errorMessage(ex.toString());
//     }
//   }

//  public void repaintAll(boolean force) {
//    rightPanel.repaintAll();
//
//    SwingUtilities.invokeLater(new Runnable() {
//      @Override
//      public void run() {
//        // scroll right to follow most recent data
//        int x = rightPanel.getSignalWidth();
//        rightScroll.getHorizontalScrollBar().setValue(x);
//        // SwingUtilities.updateComponentTreeUI(ChronoPanel.this);
//      }
//    });
//    //if (force)
//    //  SwingUtilities.updateComponentTreeUI(this);
//  }

  // todo
//   public void toggleBusExpand(SignalDataBus choosenBus, boolean expand) {
//     if (expand) {
//       data.expandBus(choosenBus);
//     } else {
//       data.contractBus(choosenBus);
//     }
//     resplit();
//   }

  @Override
  public String getTitle() {
    return S.get("ChronoTitle");
  }

  @Override
  public String getHelpText() {
    return S.get("ChronoTitle");
  }

  @Override
  public void localeChanged() {
    chooseFileButton.setText(S.get("ButtonLoad"));
    exportDataInFile.setText(S.get("ButtonExport"));
    exportDataToImage.setText(S.get("Export as image"));
  }

  @Override
  public void modelChanged(Model oldModel, Model newModel) {
    setModel(newModel);
    rightPanel.setModel(newModel);
    leftPanel.setModel(newModel);
    editHandler.computeEnabled();
  }

//  class ChronoMenuListener extends MenuListener {
//
//    protected class FileListener implements ActionListener {
//      public void actionPerformed(ActionEvent event) {
//        if (printer != null)
//          printer.actionPerformed(event);
//      }
//      boolean registered;
//      public void register(boolean en) {
//        if (registered == en)
//          return;
//        registered = en;
//        if (en) {
//          menubar.addActionListener(LogisimMenuBar.EXPORT_IMAGE, this);
//          menubar.addActionListener(LogisimMenuBar.PRINT, this);
//        } else {
//          menubar.removeActionListener(LogisimMenuBar.EXPORT_IMAGE, this);
//          menubar.removeActionListener(LogisimMenuBar.PRINT, this);
//        }
//      }
//    }
//
//    private FileListener fileListener = new FileListener();
//    private PrintHandler printer;
//
//    public ChronoMenuListener(LogisimMenuBar menubar) {
//      super(menubar);
//      fileListener.register(false);
//      editListener.register();
//    }
//
//    public void setPrintHandler(PrintHandler printer) {
//      this.printer = printer;
//      fileListener.register(printer != null);
//    }
//  }

  public void changeSpotlight(Signal s) {
    Signal old = model.setSpotlight(s);
    if (old == s)
      return;
    rightPanel.changeSpotlight(old, s);
    leftPanel.changeSpotlight(old, s);
  }

//	public void mouseEntered(Signal s) {
//    changeSpotlight(s);
//	}
//
//	public void mousePressed(Signal s, int posX) {
//		setSignalCursor(posX);
//	}
//
//	public void mouseDragged(Signal s, int posX) {
//		setSignalCursor(posX);
//	}
//
//	public void mouseExited(Signal s) {
//    changeSpotlight(null);
//	}

  public void setSignalCursorX(int posX) {
		rightPanel.setSignalCursorX(posX);
    leftPanel.updateSignalValues();
  }

	@Override
	public void modeChanged(Model.Event event) {
    // nothing to do, signals will be reset anyway
  }

	@Override
	public void signalsExtended(Model.Event event) {
    leftPanel.updateSignalValues();
    rightPanel.updateWaveforms(true);
  }

	@Override
	public void signalsReset(Model.Event event) {
    setSignalCursorX(Integer.MAX_VALUE);
    rightPanel.updateWaveforms(true);
  }

	@Override
	public void filePropertyChanged(Model.Event event) {
	}

	@Override
	public void historyLimitChanged(Model.Event event) {
    setSignalCursorX(Integer.MAX_VALUE);
    rightPanel.updateWaveforms(false);
	}

	@Override
	public void selectionChanged(Model.Event event) {
    leftPanel.updateSignals();
    rightPanel.updateSignals();
    editHandler.computeEnabled();
	}

	public void toggleBusExpand(Signal s, boolean expand) {
    System.out.println("toggle bus");
    // todo: later
		// mChronoPanel.toggleBusExpand(signalDataSource, expand);
	}

  public Model getModel() {
    return model;
  }

  public void setModel(Model newModel) {
    if (model != null)
      model.removeModelListener(this);
    model = newModel;
    if (model == null)
      return;
		model.addModelListener(this);
	}

	private static final Color PLAIN_BG = new Color(0xbb, 0xbb, 0xbb);
	private static final Color PLAIN_HI = darker(PLAIN_BG);
	private static final Color PLAIN_LINE = Color.BLACK;
	private static final Color PLAIN_ERR = new Color(0xdb, 0x9d, 0x9d);
	private static final Color PLAIN_ERRLINE = Color.BLACK;
	private static final Color PLAIN_UNK = new Color(0xea, 0xaa, 0x6c);
	private static final Color PLAIN_UNKLINE = Color.BLACK;

	private static final Color SPOT_BG = new Color(0xaa, 0xff, 0xaa);
	private static final Color SPOT_HI = darker(SPOT_BG);
	private static final Color SPOT_LINE = Color.BLACK;
	private static final Color SPOT_ERR = new Color(0xf9, 0x76, 0x76);
	private static final Color SPOT_ERRLINE = Color.BLACK;
	private static final Color SPOT_UNK = new Color(0xea, 0x98, 0x49);
	private static final Color SPOT_UNKLINE = Color.BLACK;

  private final Color SELECT_BG; // set in constructor
  private final Color SELECT_HI; // set in constructor
	private static final Color SELECT_LINE = Color.BLACK;
	private static final Color SELECT_ERR = new Color(0xe5, 0x80, 0x80);
	private static final Color SELECT_ERRLINE = Color.BLACK;
	private static final Color SELECT_UNK = new Color(0xee, 0x99, 0x44);
	private static final Color SELECT_UNKLINE = Color.BLACK;

  private static final Color[] SPOT = { SPOT_BG, SPOT_HI, SPOT_LINE, SPOT_ERR, SPOT_ERRLINE, SPOT_UNK, SPOT_UNKLINE };
  private static final Color[] PLAIN = { PLAIN_BG, PLAIN_HI, PLAIN_LINE, PLAIN_ERR, PLAIN_ERRLINE, PLAIN_UNK, PLAIN_UNKLINE };
  private final Color[] SELECT; // set in constructor

  public Color[] rowColors(SignalInfo item, boolean isSelected) {
    if (isSelected)
      return SELECT;
    Signal spotlight = model.getSpotlight();
    if (spotlight != null && spotlight.info == item)
      return SPOT;
    return PLAIN;
  }

  private static Color darker(Color c) {
    float[] hsb = Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), null); 
    float s = 0.8f;
    if (hsb[1] == 0.0)
      return Color.getHSBColor(hsb[0], hsb[1] + hsb[1], hsb[2]*s);
    else
      return Color.getHSBColor(hsb[0], 1.0f - (1.0f - hsb[1])*s, hsb[2]);
  }

  // @Override
  // SimulationHandler getSimulationHandler() {
  //   return simulationHandler;
  // }

  @Override
  public EditHandler getEditHandler() {
    return editHandler;
  }

  EditHandler editHandler = new EditHandler() {
    @Override
    public void computeEnabled() {
      boolean empty = model.getSignalCount() == 0;
      boolean sel = !empty && !leftPanel.getSelectionModel().isSelectionEmpty();
      setEnabled(LogisimMenuBar.CUT, sel);
      setEnabled(LogisimMenuBar.COPY, sel);
      setEnabled(LogisimMenuBar.PASTE, true);
      setEnabled(LogisimMenuBar.DELETE, sel);
      setEnabled(LogisimMenuBar.DUPLICATE, false);
      setEnabled(LogisimMenuBar.SELECT_ALL, !empty);
      setEnabled(LogisimMenuBar.RAISE, sel);
      setEnabled(LogisimMenuBar.LOWER, sel);
      setEnabled(LogisimMenuBar.RAISE_TOP, sel);
      setEnabled(LogisimMenuBar.LOWER_BOTTOM, sel);
      setEnabled(LogisimMenuBar.ADD_CONTROL, false);
      setEnabled(LogisimMenuBar.REMOVE_CONTROL, false);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      Object action = e.getSource();
      leftPanel.getActionMap().get(action).actionPerformed(e);
    }

  };

  @Override
  public PrintHandler getPrintHandler() {
    return printHandler;
  }

  PrintHandler printHandler = new PrintHandler() {
    @Override
    public Dimension getExportImageSize() {
      Dimension l = leftPanel.getPreferredSize();
      Dimension r = rightPanel.getPreferredSize();
      int width = l.width + 3 + r.width;
      int height = HEADER_HEIGHT + l.height;
      return new Dimension(width, height);
    }

    @Override
    public void paintExportImage(BufferedImage img, Graphics2D g) {
      Dimension l = leftPanel.getPreferredSize();
      Dimension r = rightPanel.getPreferredSize();

      g.setClip(0, 0, l.width, HEADER_HEIGHT);
      leftPanel.getTableHeader().print(g); 

      g.setClip(l.width + 3, 0, r.width, HEADER_HEIGHT);
      g.translate(l.width + 3, 0);
      rightPanel.getTimelineHeader().print(g);
      g.translate(-(l.width + 3), 0);

      g.setClip(0, HEADER_HEIGHT, l.width, l.height);
      g.translate(0, HEADER_HEIGHT);
      leftPanel.print(g);
      g.translate(0, -HEADER_HEIGHT);

      g.setClip(l.width + 3, HEADER_HEIGHT, r.width, l.height);
      g.translate(l.width + 3, HEADER_HEIGHT);
      rightPanel.print(g);
      g.translate(-(l.width + 3), -HEADER_HEIGHT);
    }

    @Override
    public int print(Graphics2D g, PageFormat pf, int pageNum, double w, double h) {
      if (pageNum != 0)
        return Printable.NO_SUCH_PAGE;

      // shrink horizontally to fit
      FontMetrics fm = g.getFontMetrics();
      Dimension d = getExportImageSize();
      double headerHeight = fm.getHeight() * 1.5;
      double scale = 1.0;
      if (d.width > w || d.height > (h-headerHeight))
        scale = Math.min(w / d.width, (h-headerHeight) / d.height);

      GraphicsUtil.drawText(g,
          S.fmt("ChronoPrintTitle",
              model.getCircuit().getName(),
              getProject().getLogisimFile().getDisplayName()),
          (int)(w/2), 0, GraphicsUtil.H_CENTER, GraphicsUtil.V_TOP);

      g.translate(0, fm.getHeight() * 1.5);
      g.scale(scale, scale);
      paintExportImage(null, g);

      return Printable.PAGE_EXISTS;
    }
  };
}
