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

package com.bfh.logisim.netlist;

import java.util.ArrayList;
import java.util.HashSet;

import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Location;

// Net holds info about a single contiguous network (1-bit signal or w-bit bus)
// within a single circuit.
// - Each net has a uniform width throughout.
// - Every output port is part of a Net (and is a direct source for that Net).
// - Every splitter end is part of a Net. The nets touching the split end are
//   typically indirect sources for some of the bits of the net touching the
//   combined end, or vice versa.
// - The two ends of a wire are part of the same Net.
// - Tunnels with the same name are part of the same Net.
// - Input ports that happen to be touching a wire, tunnel, splitter, or output
//   port are also part of the corresponding Net (and are a direct sink for that
//   Net).
// - Input ports that are not touching any such thing are "unconnected" and are
//   not part of any Net.
public class Net {

  static class Source {
    final Component comp;
    final int end;
    final int bit;
    Source(Component c, int e, int b) { comp = c; end = e; bit = b; }
    @Override
    public boolean equals(Object o) {
      if (!(o instanceof Source))
        return false;
      Source s = (Source)o;
      return (comp == s.comp && end == s.end && bit == s.bit);
    }
  }

  static class DirectSource {
    final Component comp;
    final int end;
    DirectSource(Component c, int e) { comp = c; end = e; }
  }

  static class IndirectSource {
    final Net net;
    final int bit;
    IndirectSource(Net n, int b) { net = n; bit = b; }
  }

  static class DirectSink {
    final Component comp;
    final int end;
    DirectSink(Component c, int e) { comp = c; end = e; }
  }

  static class IndirectSink {
    final Net net;
    final int bit;
    IndirectSink(Net n, int b) { net = n; bit = b; }
  }

	public int width = 0; // number of bits in this network, initially unknown
	private HashSet<Location> points = new HashSet<>(); // points this net touches

  // Sink can be any combination of these cases, or none of them:
  // - [direct sinks] All the bits of this Net together may drive directly ports
  //   from normal Components, like Adder or AndGate. We track this so that
  //   CircuitHDLGenerator can map each sink component port to this Net.
  private final ArrayList<DirectSink> directSinks = new ArrayList<>();
  // - [indirect sinks] Some bits of this Net may indirectly drive other bits in
  //   other Nets and ultimately some bits of some ports of normal Components.
  //   We don't track this, but even so we can still use the info below to warn
  //   about undriven inputs to components.

  // Source can be exactly one of these cases:
  // - [direct source] All the bits of this Net together may be driven directly
  //   by one port from one normal Component, like an Adder or AndGate, with the
  //   correct width. We track this so that CircuitHDLGenerator can map the
  //   source component port to this Net.
  private DirectSource directSource;
  // - [indirect sources] Some bits of this Net may be driven indirectly, though
  //   one or more Splitters, by bits from other Nets, which in turn are
  //   indirectly or directly driven by bits from some port of some normal
  //   Component. When there are multiple indirect sources, they must all
  //   eventually lead back to the same source component port and bit. If the
  //   entire Net is also directly driven, then the indirect and direct sources
  //   must be the same. We track one indirect source for each bit to warn about
  //   short-circuit errors.
  private IndirectSource[] indirectSource;
  // - [partial or no sources] All or some of the bits of this net may be
  //   entirely undriven, when there are no indirect drivers for those bits and
  //   no direct driver for the entire bus. This is fine as long as there are no
  //   direct sinks or indirect sinks for those bits.

	public Net(HashSet<Location> locs) {
    points.addAll(locs);
  }

	public void add(HashSet<Location> locs) {
    points.addAll(locs);
	}
	
  // public boolean isEmpty() { return points.isEmpty(); }
	public HashSet<Location> getPoints() { return points; }
	public boolean contains(Location point) { return points.contains(point); }
	// public void clearPoints() { points.clear(); }

  public int bitWidth() { return width; }
	public boolean isBus() { return width > 1; }
  public void setBitWidth(int w) {
    width = w;
    indirectSource = new IndirectSource[w];
  }

  public void addSink(Component c, int end) {
    directSinks.add(new DirectSink(c, end));
  }

  public Component getSourceComponent() {
    return directSource == null ? null : directSource.comp;
  }

  public int getSourceEnd() {
    return directSource == null ? -1 : directSource.end;
  }

  public void setSource(Component c, int end) {
    directSource = new DirectSource(c, end);
  }

  public Source getSourceForBit(int i) {
    if (directSource != null)
      return new Source(directSource.comp, directSource.end, i);
    IndirectSource isrc = indirectSource[i];
    if (isrc != null)
      return isrc.net.getSourceForBit(isrc.bit);
    return null;
  }

  public void setSourceForBit(int i, Net net, int bit) {
    indirectSource[i] = new IndirectSource(net, bit);
  }

  public ArrayList<DirectSink> getSinkComponents() {
    return directSinks;
  }


}
