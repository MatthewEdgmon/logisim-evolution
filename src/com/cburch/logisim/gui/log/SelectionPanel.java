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
package com.cburch.logisim.gui.log;
import static com.cburch.logisim.gui.log.Strings.S;

import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;

import com.cburch.logisim.util.Icons;
import com.cburch.logisim.util.JDialogOk;

class SelectionPanel extends LogPanel {

  private ComponentSelector selector;
  private SelectionList list;
  private JLabel selectDesc, exploreLabel, listLabel;

  public SelectionPanel(LogFrame window) {
    super(window);
    selector = new ComponentSelector(getModel());
    list = new SelectionList();
    list.setLogModel(getModel());

    JScrollPane explorerPane = new JScrollPane(selector,
        ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS,
        ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
    JScrollPane listPane = new JScrollPane(list,
        ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS,
        ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);

    GridBagLayout gridbag = new GridBagLayout();
    GridBagConstraints gbc = new GridBagConstraints();
    setLayout(gridbag);

    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.weightx = gbc.weighty = 0.0;
    gbc.insets = new Insets(15, 10, 0, 10);

    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.gridwidth = 3;
    selectDesc = new JLabel();
    gridbag.setConstraints(selectDesc, gbc);
    add(selectDesc);
    gbc.gridwidth = 1;

    gbc.gridx = 0;
    gbc.gridy = 1;
    exploreLabel = new JLabel();
    gridbag.setConstraints(exploreLabel, gbc);
    add(exploreLabel);

    gbc.gridx = 2;
    gbc.gridy = 1;
    listLabel = new JLabel();
    gridbag.setConstraints(listLabel, gbc);
    add(listLabel);

    gbc.fill = GridBagConstraints.BOTH;
    gbc.weightx = gbc.weighty = 1.0;
    gbc.insets = new Insets(10, 10, 10, 10);
    gbc.gridx = 0;
    gbc.gridy = 2;
    gridbag.setConstraints(explorerPane, gbc);
    add(explorerPane);
    explorerPane.setPreferredSize(new Dimension(120, 200));

    gbc.fill = GridBagConstraints.NONE;
    gbc.weightx = gbc.weighty = 0.0;
    gbc.insets = new Insets(0, 0, 0, 0);
    gbc.gridx = 1;
    gbc.gridy = 2;
    JLabel arrow = new JLabel(Icons.getIcon("rightarrow.png"));
    gridbag.setConstraints(arrow, gbc);
    add(arrow);

    gbc.fill = GridBagConstraints.BOTH;
    gbc.weightx = gbc.weighty = 1.0;
    gbc.insets = new Insets(10, 10, 10, 10);
    gbc.gridx = 2;
    gbc.gridy = 2;
    gridbag.setConstraints(listPane, gbc);
    add(listPane);
    listPane.setPreferredSize(new Dimension(180, 200));
  }

  @Override
  public String getHelpText() {
    return S.get("selectionHelp");
  }

  @Override
  public String getTitle() {
    return S.get("selectionTab");
  }

  @Override
  public void localeChanged() {
    selectDesc.setText(S.get("selectionDesc"));
    exploreLabel.setText(S.get("exploreLabel"));
    listLabel.setText(S.get("listLabel"));
    selector.localeChanged();
    list.localeChanged();
  }

  @Override
  public void modelChanged(Model oldModel, Model newModel) {
    selector.setLogModel(newModel);
    list.setLogModel(newModel);
  }

  static class SelectionDialog extends JDialogOk {
    SelectionPanel selPanel;
    SelectionDialog(LogFrame logFrame) {
      super("Signal Selection", false);
      selPanel = new SelectionPanel(logFrame);
      selPanel.localeChanged();
      getContentPane().add(selPanel);
      setMinimumSize(new Dimension(350, 300));
      setSize(400, 400);
      pack();
    }
    public void cancelClicked() { okClicked(); }
    public void okClicked() { }
  }

  public static void doDialog(LogFrame logFrame) {
    SelectionDialog d = new SelectionDialog(logFrame);
    d.setVisible(true);
  }
}
