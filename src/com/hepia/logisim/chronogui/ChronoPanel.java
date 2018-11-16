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
package com.hepia.logisim.chronogui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.util.prefs.Preferences;

import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JToolBar;
import javax.swing.SwingUtilities;

import com.cburch.logisim.circuit.Simulator;
import com.cburch.logisim.gui.log.LogFrame;
import com.cburch.logisim.gui.log.Model;
import com.cburch.logisim.gui.log.LogPanel;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.util.Icons;
import com.hepia.logisim.chronodata.ChronoData;
import com.hepia.logisim.chronodata.ChronoDataWriter;
import com.hepia.logisim.chronodata.ChronoModelEventHandler;
import com.hepia.logisim.chronodata.NoSysclkException;
import com.hepia.logisim.chronodata.SignalDataBus;
import com.hepia.logisim.chronodata.TimelineParam;
import java.io.File;

public class ChronoPanel extends LogPanel
  implements KeyListener, WindowListener {

  JFileChooser fc;

  /**
   * Listener to the scrollbars and splitPane divider
   */
  private class MyListener implements ActionListener, AdjustmentListener {

    private ChronoPanel chronoFrame;

    public MyListener(ChronoPanel cf) {
      chronoFrame = cf;
    }

    /**
     * Load or export button event handler
     */
    @Override
    public void actionPerformed(ActionEvent e) {
      // load a chronogram from a file
      if ("load".equals(e.getActionCommand())) {
        final JFileChooser fc = new JFileChooser();
        int returnVal = fc.showOpenDialog(chronoFrame);
        if (returnVal == JFileChooser.APPROVE_OPTION) {
          chronoFrame
              .loadFile(fc.getSelectedFile().getAbsolutePath());
        }

        // export a chronogram to a file
      } else if ("export".equals(e.getActionCommand())) {
        final JFileChooser fc = new JFileChooser();
        int returnVal = fc.showSaveDialog(chronoFrame);
        if (returnVal == JFileChooser.APPROVE_OPTION) {
          chronoFrame.exportFile(fc.getSelectedFile().getAbsolutePath());
        }

      }
      else if ("exportImg".equals(e.getActionCommand())) {
        fc = new JFileChooser();
        int returnVal = fc.showSaveDialog(ChronoPanel.this);
        if (returnVal == JFileChooser.APPROVE_OPTION) {
          File file = fc.getSelectedFile();

          //add .png to the filename if the user forgot
          if (!fc.getSelectedFile().getAbsolutePath().endsWith(".png")) {
            file = new File(fc.getSelectedFile() + ".png");
          }
          exportImage(file);
        }

      } else if ("play".equals(e.getActionCommand())) {
        if (simulator.isRunning()) {
          ((JButton) e.getSource()).setIcon(Icons
          .getIcon("simplay.png"));
        } else {
          ((JButton) e.getSource()).setIcon(Icons
          .getIcon("simstop.png"));
        }
        simulator.setIsRunning(!simulator.isRunning());
      } else if ("step".equals(e.getActionCommand())) {
        simulator.step();
      } else if ("tplay".equals(e.getActionCommand())) {
        if (simulator.isTicking()) {
          ((JButton) e.getSource()).setIcon(Icons
          .getIcon("simtplay.png"));
        } else {
          ((JButton) e.getSource()).setIcon(Icons
          .getIcon("simtstop.png"));
        }
        simulator.setIsTicking(!simulator.isTicking());
      } else if ("thalf".equals(e.getActionCommand())) {
        simulator.tick(1);
      } else if ("tfull".equals(e.getActionCommand())) {
        simulator.tick(2);
      }
    }


    /**
     * rightScroll horizontal movement
     */
    @Override
    public void adjustmentValueChanged(AdjustmentEvent e) {
      if (rightPanel != null) {
        rightPanel.adjustmentValueChanged(e.getValue());
      }
    }
  }

  private static final long serialVersionUID = 1L;
  private Simulator simulator;
  private ChronoData chronogramData;
  private Preferences prefs;
  // top bar
  private JPanel topBar;
  private JButton chooseFileButton;
  private JButton exportDataInFile;
  private JButton exportDataToImage;
  private JLabel statusLabel;
  // split pane
  private RightPanel rightPanel;
  private LeftPanel leftPanel;
  private CommonPanelParam commonPanelParam;
  private JScrollPane leftScroll;
  private JScrollPane rightScroll;
  private JSplitPane mainSplitPane;
  private TimelineParam timelineParam;
  // event managers
  private MyListener myListener = new MyListener(this);
  private DrawAreaEventManager mDrawAreaEventManager;
  private DrawAreaManager mDrawAreaManager;
  // graphical
  private int dividerLocation = 353;
  // mode
  private boolean realTimeMode;
  private ChronoModelEventHandler chronoModelEventHandler;
  // menu
  private JMenuBar winMenuBar;
  // private JCheckBoxMenuItem ontopItem;

  private JMenuItem close;

  /**
   * Offline mode ChronoPanel constructor
   */
  // public ChronoPanel(Project prj) {
  //   super(false, prj);
  //   realTimeMode = false;
  //   commonPanelParam = new CommonPanelParam(20, 38);
  //   createMainStructure();
  // }

  /**
   * Real time mode ChronoPanel constructor
   */
  public ChronoPanel(LogFrame logFrame) {
    super(logFrame);
    realTimeMode = true;

    timelineParam = logFrame.getTimelineParam();
    commonPanelParam = new CommonPanelParam(20, 38);
    Project project = getProject();
    simulator = project.getSimulator();
    chronogramData = new ChronoData();
    try {
      chronoModelEventHandler = new ChronoModelEventHandler(this,
          logFrame.getModel(), project);
      createMainStructure();
      fillMainSplitPane();

      if (chronogramData.size() == 0) {
        statusLabel.setText(Strings.get("SimStatusNoSignal"));
      } else {
        statusLabel.setText(Strings.get("SimStatusCurrentScheme"));
      }

    } catch (NoSysclkException ex) {
      createMainStructure();
      statusLabel.setText(Strings.get("SimStatusNoSysclk"));
    }
  }

  // @Override
  // public void actionPerformed(ActionEvent ae) {
  //   Object src = ae.getSource();
  //   if (src == ontopItem) {
  //     ae.paramString();
  //     setAlwaysOnTop(ontopItem.getState());
  //   } else if (src == close) {
  //     setVisible(false);
  //   }
  // }

  /**
   * Creates the main panels for the chronogram
   */
  private void createMainStructure() {
    mDrawAreaEventManager = new DrawAreaEventManager();
    mDrawAreaManager = new DrawAreaManager(this);
    mDrawAreaEventManager.addDrawAreaListener(mDrawAreaManager);

    setLayout(new BorderLayout());
    setFocusable(true);
    requestFocus();
    addKeyListener(this);
    // addWindowListener(this);

    // menu bar
    // winMenuBar = new JMenuBar();
    // JMenu windowMenu = new JMenu("Window");
    // windowMenu.setMnemonic('W');
    // ontopItem = new JCheckBoxMenuItem("Set on top", true);
    // ontopItem.addActionListener(this);
    // close = new JMenuItem("Close");
    // close.addActionListener(this);
    // windowMenu.add(ontopItem);
    // windowMenu.addSeparator();
    // windowMenu.add(close);
    // winMenuBar.add(windowMenu);
    // winMenuBar.setFocusable(false);
    // setJMenuBar(winMenuBar);
    //setAlwaysOnTop(true);

    // top bar
    Dimension buttonSize = new Dimension(150, 25);
    topBar = new JPanel();
    topBar.setLayout(new FlowLayout(FlowLayout.LEFT));

    // external file
    chooseFileButton = new JButton(Strings.get("ButtonLoad"));
    chooseFileButton.setActionCommand("load");
    chooseFileButton.addActionListener(myListener);
    chooseFileButton.setPreferredSize(buttonSize);
    chooseFileButton.setFocusable(false);

    // export
    exportDataInFile = new JButton(Strings.get("ButtonExport"));
    exportDataInFile.setActionCommand("export");
    exportDataInFile.addActionListener(myListener);
    exportDataInFile.setPreferredSize(buttonSize);
    exportDataInFile.setFocusable(false);

    exportDataToImage = new JButton(Strings.get("Export as image"));
    exportDataToImage.setActionCommand("exportImg");
    exportDataToImage.addActionListener(myListener);
    exportDataToImage.setPreferredSize(buttonSize);
    exportDataToImage.setFocusable(false);

    // Toolbar
    JToolBar bar = new JToolBar();
    bar.setFocusable(false);
    JButton playButton;
    if (simulator != null && simulator.isRunning()) {
      playButton = new JButton(Icons.getIcon("simstop.png"));
    } else {
      playButton = new JButton(Icons.getIcon("simplay.png"));
    }
    playButton.setActionCommand("play");
    playButton.addActionListener(myListener);
    playButton.setToolTipText("Start/Stop simulation");
    playButton.setFocusable(false);
    bar.add(playButton);
    JButton stepButton = new JButton(Icons.getIcon("simstep.png"));
    stepButton.setActionCommand("step");
    stepButton.addActionListener(myListener);
    stepButton.setToolTipText("Simulate one step");
    stepButton.setFocusable(false);
    bar.add(stepButton);
    JButton tplayButton;
    if (simulator != null && simulator.isTicking()) {
      tplayButton = new JButton(Icons.getIcon("simtstop.png"));
    } else {
      tplayButton = new JButton(Icons.getIcon("simtplay.png"));
    }
    tplayButton.setActionCommand("tplay");
    tplayButton.addActionListener(myListener);
    tplayButton.setToolTipText("Start/Stop 'sysclk' tick");
    tplayButton.setFocusable(false);
    bar.add(tplayButton);
    JButton thalfButton = new JButton(Icons.getIcon("tickhalf.png"));
    thalfButton.setActionCommand("thalf");
    thalfButton.addActionListener(myListener);
    thalfButton.setToolTipText("Tick half clock cycle");
    thalfButton.setFocusable(false);
    bar.add(thalfButton);
    JButton tfullButton = new JButton(Icons.getIcon("tickfull.gif"));
    tfullButton.setActionCommand("tfull");
    tfullButton.addActionListener(myListener);
    tfullButton.setToolTipText("Tick full clock cycle");
    tfullButton.setFocusable(false);
    bar.add(tfullButton);

    add(BorderLayout.NORTH, bar);

    statusLabel = new JLabel();
    topBar.add(chooseFileButton);
    topBar.add(exportDataInFile);
    topBar.add(exportDataToImage);
    topBar.add(new JLabel(Strings.get("SimStatusName")));
    topBar.add(statusLabel);
    add(BorderLayout.SOUTH, topBar);

    // split pane
    mainSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
    add(BorderLayout.CENTER, mainSplitPane);

    // setTitle(Strings.get("ChronoTitle") + ": "
    //     + project.getLogisimFile().getDisplayName());
    // setResizable(true);
    // setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
    // setSize(new Dimension(1024, 768));

    // setContentPane(mainPanel);

//     prefs = Preferences.userRoot().node(this.getClass().getName());
//     setLocation(prefs.getInt("X", 0), prefs.getInt("Y", 0));
//     setSize(prefs.getInt("W", getSize().width),
//         prefs.getInt("H", getSize().height));
// 
//     setVisible(true);
  }

  /**
   * Popup an error message
   *
   * @param err
   *            Error message
   */
  public void errorMessage(String err) {
    JOptionPane.showMessageDialog(null, err);
  }

  /**
   * Export the current chronogram to file
   */
  public void exportFile(String file) {
    ChronoDataWriter.export(file, timelineParam, chronogramData);
  }
  public void exportImage(File file) {
    ImageExporter ie = new ImageExporter(this, chronogramData, this.getCommonPanelParam().getHeaderHeight());
    ie.createImage(file);
  }

  /**
   * Fill the splitPane with the two panels (SignalName and SignalDraw)
   */
  private void fillMainSplitPane() {
    mainSplitPane.setDividerSize(5);

    // ===Left Side===
    leftPanel = new LeftPanel(this, mDrawAreaEventManager);
    leftScroll = new JScrollPane(leftPanel);

    // ===Right Side===
    // keep scroolbar position
    int scrollBarCursorPos = rightScroll == null ? 0 : rightScroll
        .getHorizontalScrollBar().getValue();
    if (rightPanel == null) {
      rightPanel = new RightPanel(this, mDrawAreaEventManager);
    } else {
      rightPanel = new RightPanel(rightPanel);
    }

    rightScroll = new JScrollPane(rightPanel);
    rightScroll.getHorizontalScrollBar().addAdjustmentListener(myListener);

    // Synchronize the two scrollbars
    leftScroll.getVerticalScrollBar().setModel(
        rightScroll.getVerticalScrollBar().getModel());

    mainSplitPane.setLeftComponent(leftScroll);
    mainSplitPane.setRightComponent(rightScroll);

    // right scrollbar position
    rightScroll.getHorizontalScrollBar().setValue(scrollBarCursorPos);
    mDrawAreaManager.drawVerticalMouseClicked();
    rightScroll.getHorizontalScrollBar().setValue(scrollBarCursorPos);

    // keep leftpanel signal value up to date
    mDrawAreaManager.refreshSignalsValues();

    mainSplitPane.setDividerLocation(dividerLocation);
  }

  // accessors
  public ChronoData getChronoData() {
    return chronogramData;
  }

  public CommonPanelParam getCommonPanelParam() {
    return commonPanelParam;
  }

  public int getDividerLocation() {
    return dividerLocation;
  }

  public LeftPanel getLeftPanel() {
    return leftPanel;
  }

  public RightPanel getRightPanel() {
    return rightPanel;
  }

  public TimelineParam getTimelineParam() {
    return timelineParam;
  }

  public int getVisibleSignalsWidth() {
    return mainSplitPane.getRightComponent().getWidth();
  }

  public boolean isRealTimeMode() {
    return realTimeMode;
  }

  @Override
  public void keyPressed(KeyEvent ke) {
    int keyCode = ke.getKeyCode();
    if (keyCode == KeyEvent.VK_F2) {
      simulator.tick(2);
    }
  }

  @Override
  public void keyReleased(KeyEvent ke) {
    // throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public void keyTyped(KeyEvent ke) {
    // throw new UnsupportedOperationException("Not supported yet.");
  }

  /**
   * Load the chronogram from the log file
   */
  public void loadFile(String logFile) {
    try {
      ChronoData tmp = new ChronoData(logFile, this);
      if (tmp != null) {
        realTimeMode = false;
        chronogramData = tmp;
        fillMainSplitPane();
        statusLabel.setText(Strings.get("InputFileLoaded") + logFile);
      }
    } catch (NoSysclkException ex) {
      errorMessage(Strings.get("InputFileNoSysclk"));
    } catch (Exception ex) {
      errorMessage(ex.toString());
    }
  }

  /**
   * Repaint all signals
   *
   * @param force
   *            If true, calls SwingUtilities.updateComponentTreeUI(this);
   */
  public void repaintAll(boolean force) {
    rightPanel.repaintAll();

    if (this.isRealTimeMode()) {
      Runnable refreshScroll = new Runnable() {
        @Override
        public void run() {
          // keeps the scrolling bar at the top right, to follow the
          // last added data
          rightScroll.getHorizontalScrollBar().setValue(
              rightPanel.getSignalWidth());
          SwingUtilities.updateComponentTreeUI(ChronoPanel.this);
        }
      };
      SwingUtilities.invokeLater(refreshScroll);
    }
    if (force) {
      SwingUtilities.updateComponentTreeUI(this);
    }
  }

  public void setScrollbarPosition(int pos) {
    rightScroll.getHorizontalScrollBar().setValue(pos);
  }

  public void setTimelineParam(TimelineParam timelineParam) {
    this.timelineParam = timelineParam;
  }

  /**
   * Switch bus between signle bus view or detailed signals view
   *
   * @param choosenBus
   *            Bus to expand or contract
   * @param expand
   *            true: expand, false:contract
   */
  public void toggleBusExpand(SignalDataBus choosenBus, boolean expand) {
    if (expand) {
      chronogramData.expandBus(choosenBus);
    } else {
      chronogramData.contractBus(choosenBus);
    }
    fillMainSplitPane();
  }

  @Override
  public void windowActivated(WindowEvent we) {
  }

  @Override
  public void windowClosed(WindowEvent we) {
  }

  @Override
  public void windowClosing(WindowEvent we) {
  }

  @Override
  public void windowDeactivated(WindowEvent e) {
//     prefs.putInt("X", getX());
//     prefs.putInt("Y", getY());
//     prefs.putInt("W", getWidth());
//     prefs.putInt("H", getHeight());
  }

  @Override
  public void windowDeiconified(WindowEvent we) {
  }

  @Override
  public void windowIconified(WindowEvent we) {
  }

  @Override
  public void windowOpened(WindowEvent we) {
  }

  @Override
  public String getTitle() {
    return Strings.get("ChronoTitle");
  }

  @Override
  public String getHelpText() {
    return Strings.get("ChronoTitle");
  }

  @Override
  public void localeChanged() {
  }

  @Override
  public void modelChanged(Model oldModel, Model newModel) {
    if (oldModel != null)
      oldModel.removeModelListener(chronoModelEventHandler);
    if (newModel != null)
      newModel.addModelListener(chronoModelEventHandler);
  }
}
