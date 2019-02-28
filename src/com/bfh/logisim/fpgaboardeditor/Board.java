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

import java.awt.image.BufferedImage;
import java.util.AbstractList;
import java.util.ArrayList;

import com.bfh.logisim.fpgagui.FPGAReport;

import com.bfh.logisim.fpgaboardeditor.BoardIO.IOComponentTypes;

// Board describes an fpga-based platform (i.e. "demo board") that can be the
// target of the FPGA synthesis. Each Board has a name, chipset, image, and list
// of I/O resources.
public class Board extends AbstractList<BoardIO> {

	public String name;
	public final Chipset fpga;
	public final BufferedImage image;

	private final ArrayList<BoardIO> components = new ArrayList<>();

	public Board(Chipset fpga, BoardPicture image) {
    this.fpga = fpga;
    this.image = image;
	}

  @Override
  public BoardIO get(int i) {
    return components.get(i);
  }

  @Override
  public int size() {
    return components.size();
  }

	public void addComponent(BoardIO comp) {
		comp.SetId(components.size() + 1);
		components.add(comp);
	}

  public void printStats(FPGAReport out) {
		out.AddInfo("Board '%s' contains the following I/O resources:", name);
		for (IOComponentTypes type : IOComponentTypes.KnownComponentSet) {
      int count = 0, bits = 0;
			for (BoardIO comp : components) {
				if (comp.GetType().equals(type)) {
					count++;
					bits += comp.getNrOfPins();
        }
      }
      out.AddInfo("   %s: %d components (%d bits)", type, count, bits);
		}
	}

	public ArrayList<BoardRectangle> compatableRects(IOComponentTypes type, int bits) {
		ArrayList<BoardRectangle> result = new ArrayList<>();
		for (BoardIO comp : components)
      if (comp.GetType().equals(type) && bits <= comp.getNrOfPins())
        result.add(comp.GetRectangle());
		return result;
	}

  public BoardIO getComponent(BoardRectangle r) {
		for (BoardIO comp : components)
      if (comp.GetRectangle().equals(r))
        return comp;
    return null;
  }

}
