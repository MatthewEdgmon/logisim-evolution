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

package com.bfh.logisim.fpgaboardeditor;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.cburch.logisim.proj.Projects;

public class BoardReader {

	private static final String ERR_ICON = "/resources/logisim/error.png";
	private static final String WANR_ICON = "/resources/logisim/warning.png";

	public static Board read(String path) {
		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			DocumentBuilder parser = factory.newDocumentBuilder();
      Document doc;
			if (path.startsWith("url:"))
				doc = parser.parse(getClass().getResourceAsStream("/" + path.substring(4)));
			else if (path.startsWith("file:"))
				doc = parser.parse(new File(path.substring(5)));
			else
				doc = parser.parse(new File(path));

			Board b = new Board(parseChipset(doc), parsePicture(doc));
      parseComponents(doc, "PinsInformation", b); // backwards compatability	
			parseComponents(doc, "ButtonsInformation", b); // backwards compatability	
			parseComponents(doc, "LEDsInformation", b); // backwards compatability	
			parseComponents(doc, "IOComponents", b); // new format
			return b;
		} catch (Exception e) {
      showError("The selected xml file was invalid: " + e.getMessage());
      return null;
		}
	}

  private static NodeList getSection(Document doc, String name) {
		NodeList sections = doc.getElementsByTagName(name);
		if (sections.getLength() != 1)
			return null;
		return section.item(0).getChildNodes();
  }

  private static BufferedImage parsePicture(Document doc) throws Exception {
    NodeList xml = getSection(doc, "BoardPicture");
    if (xml == null)
      return null;
    HashMap<String, String> params = xmlToMap(xml);

    int w = Integer.parseInt(params.getOrDefault("PictureDimension/Width", "0"));
    int h = Integer.parseInt(params.getOrDefault("PictureDimension/Height", "0"));
    String pixels = params.get("PixelData/PixelRGB");
    String codes = params.get("CompressionCodeTable/TableData");

    if (w == 0 || h == 0)
      throw new Exception("invalid or missing image dimensions");
    if (codes == null)
      throw new Exception("missing image compression code table");
    if (pixels == null)
      throw new Exception("missing image data");

    ImageXmlFactory reader = new ImageXmlFactory();
    reader.SetCodeTable(codes.split(" "));
    reader.SetCompressedString(pixels);
    BufferedImage result = reader.GetPicture(w, h);
    return result;
  }

  private static HashMap<String, String> xmlToMap(NodeList xml) {
    HashMap<String, String> params = new HashMap<>();
    for (int i = 0; i < xml.getLength(); i++) {
      Node node = xml.item(i);
      String name = node.getNodeName();
      NamedNodeMap attrs = node.getAttributes();
      for (int j = 0; j < attrs.getLength(); j++) {
        Node attr = attrs.item(j);
        String tag = attr.getNodeName();
        String val = attr.getNodeValue();
        params.put(name+"/"+tag, val);
      }
    }
    return params;
  }

  private static Chipset parseChipset(Document doc) throws Exception {
    NodeList xml = getSection(doc, "BoardInformation");
    if (xml == null)
      return null;
    return new Chipset(xmlToMap(xml));
  }


  private static void parseComponents(Document doc, String section, Board board) {
    NodeList xml = getSection(doc, section);
    if (xml == null)
      return;
    for (int i = 0; i < xml.getLength(); i++) {
      BoardIO c;
      c = new BoardIO(xml.item(i));
      if (c.IsKnownComponent())
        board.addComponent(c);
    }
  }

  public static void showError(String msg) { showMessage(ERR_ICON, msg); }
  public static void showWarning(String msg) { showMessage(WARN_ICON, msg); }
  private static void showMessage(String icon, String msg) {
    final JFrame dialog = new JFrame(type);
    JLabel pic = new JLabel();
    pic.setIcon(new ImageIcon(getClass().getResource(icon)));
    GridBagLayout dialogLayout = new GridBagLayout();
    dialog.setLayout(dialogLayout);
    GridBagConstraints c = new GridBagConstraints();
    JLabel message = new JLabel(msg);
    JButton close = new JButton("close");
    ActionListener actionListener = new ActionListener() {
      public void actionPerformed(ActionEvent e) { dialog.dispose(); }
    };
    close.addActionListener(actionListener);

    c.gridx = 0;
    c.gridy = 0;
    c.ipadx = 20;
    dialog.add(pic, c);

    c.gridx = 1;
    c.gridy = 0;
    dialog.add(message, c);

    c.gridx = 1;
    c.gridy = 1;
    dialog.add(close, c);
    dialog.pack();
    dialog.setLocation(Projects.getCenteredLoc(dialog.getWidth(), dialog.getHeight()));
    dialog.setAlwaysOnTop(true);
    dialog.setVisible(true);
  }

}
