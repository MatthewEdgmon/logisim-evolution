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

package com.cburch.hex;

public interface HexModel {
	/** Registers a listener for changes to the values. */
	public void addHexModelWeakListener(Object owner, HexModelListener l);

	/** Fills a series of values with the same value. */
	public void fill(long start, long length, int value);

	/** Returns the value at the given address. */
	public int get(long address);

	/** Returns the offset of the initial value to be displayed. */
	public long getFirstOffset();

	/** Returns the number of values to be displayed. */
	public long getLastOffset();

	/** Returns number of bits in each value. */
	public int getValueWidth();

	/** Unregisters a listener for changes to the values. */
	public void removeHexModelWeakListener(Object owner, HexModelListener l);

	/** Changes the value at the given address. */
	public void set(long address, int value);

	/** Changes a series of values at the given addresses. */
	public void set(long start, int[] values);
}
