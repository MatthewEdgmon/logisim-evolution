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

public class IoStandards {

	public static final char DEFAULT = 0;
	public static final char LVCMOS12 = 1;
	public static final char LVCMOS15 = 2;
	public static final char LVCMOS18 = 3;
	public static final char LVCMOS25 = 4;
	public static final char LVCMOS33 = 5;
	public static final char LVTTL = 6;
	public static final char UNKNOWN = 255;

	public static final String[] DESC = { "Default", "LVCMOS12",
			"LVCMOS15", "LVCMOS18", "LVCMOS25", "LVCMOS33", "LVTTL" };
	public static final String ATTR = "FPGAPinIOStandard";

	public static char get(String desc) {
    for (char i = 0; i < DESC.length; i++)
      if (DESC[i].equals(desc))
        return i;
    return UNKNOWN;
	}

}
