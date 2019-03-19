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
package com.cburch.logisim.std.wiring;

import com.bfh.logisim.hdlgenerator.HDLGenerator;
import com.bfh.logisim.hdlgenerator.HDLInliner;
import com.bfh.logisim.hdlgenerator.TickHDLGenerator;
import com.bfh.logisim.netlist.Net;;
import com.bfh.logisim.netlist.NetlistComponent;
import com.bfh.logisim.netlist.ClockBus;
import com.cburch.logisim.hdl.Hdl;
import com.cburch.logisim.instance.StdAttr;

// Note: Clock HDL functionality is split into two parts. A counter part is
// lifted up to the toplevel where it can be duplicated, ensuring that even if
// the user uses many logisim clocks, all those ones with the same shape
// parameters will share a common generator and be reasonably well synchronized.
// A stub part remains within the circuit, but it does nothing other than select
// out from the hidden clock bus the generated clock signal, which is hopefully
// needed only in the cases where a logisim clock component output is fed into
// combinational logic.
public class ClockHDLGenerator {

  // Name used by circuit and top-level netslists for clock-related shadow buses.
  public static String CLK_TREE_NET = "LOGISIM_CLOCK_TREE"; // %d
  public static int CLK_TREE_WIDTH = 5; // bus contains the five signals below

  public static final int CLK_SLOW = 0; // Oscillates at user-chosen rate and shape
  public static final int CLK_INV = 1;  // Inverse of CLK_SLOW
  public static final int POS_EDGE = 2; // High pulse when CLK_SLOW rises
  public static final int NEG_EDGE = 3; // High pulse when CLK_SLOW falls
  public static final int CLK_RAW = 4;  // The underlying raw FPGA clock

  // See TickHDLGenerator for details on the rationale for these.
  public static String[] clkSignalFor(HDLGenerator downstream, int clkid) {
    String clkNet = CLK_TREE_NET + clkid + downstream._hdl.idx;
    String one = downstream._hdl.one;
    if (downstream._nets.getClockBus().RawFPGAClock) {
      // Raw mode: use ck=CLK_RAW en=1 or ck=~CLK_RAW en=1
      if (downstream._attrs.getValue(StdAttr.EDGE_TRIGGER) == StdAttr.TRIG_FALLING 
          || downstream._attrs.getValue(StdAttr.TRIGGER) == StdAttr.TRIG_FALLING
          || downstream._attrs.getValue(StdAttr.TRIGGER) == StdAttr.TRIG_LOW)
        return new String[] { String.format(clkNet, CLK_RAW), one };
      else
        return new String[] { String.format(clkNet, CLK_INV), one }; // == ~CLK_RAW
    } else if (downstream._attrs.getValue(StdAttr.EDGE_TRIGGER) == StdAttr.TRIG_FALLING 
        || downstream._attrs.getValue(StdAttr.TRIGGER) == StdAttr.TRIG_FALLING) {
      // Slow mode falling: use ck=CLK_RAW en=NEG_EDGE
      return new String[] {
        String.format(clkNet, CLK_RAW),
        String.format(clkNet, NEG_EDGE) };
    } else if (downstream._attrs.getValue(StdAttr.TRIGGER) == StdAttr.TRIG_HIGH) {
      // Slow mode active high: use ck=CL_SLOW en=1
      return new String[] { String.format(clkNet, CLK_SLOW), one };
    } else if (downstream._attrs.getValue(StdAttr.TRIGGER) == StdAttr.TRIG_LOW) {
      // Slow mode active high: use ck=~CLK_SLOW en=1
      return new String[] { String.format(clkNet, CLK_INV), one }; // == ~CLK_SLOW
    } else { // default: TRIG_RISING
      // Slow mode rising: use ck=CLK_RAW en=POS_EDGE
      return new String[] {
        String.format(clkNet, CLK_RAW),
        String.format(clkNet, POS_EDGE) };
    }
  }

  public static class StubPart extends HDLInliner {

    public StubPart(HDLCTX ctx) {
      super(ctx, "ClockStub");
    }

    @Override
    protected void generateInlinedCode(Hdl out, NetlistComponent comp) {
      Net net = comp.getConnection(0);
      if (net != null) {
        int clkid = _nets.getClockId(net);
        out.assign(net.name, CLK_TREE_NET + clkid, CLK_SLOW);
      }
    }

  }

  public static class CounterPart extends HDLGenerator {

    public final long id;
    private final ClockBus clkbus;
    private final ClockBus.Shape shape;

    public CounterPart(HDLCTX ctx, ClockBus.Shape shape, ClockBus clkbus, long id) {
      // Note: Only one declaration is made at the top level, so we can only
      // have one HDL implementation version of the counter here. If we wanted
      // multiple HDL implmentation versions here, ToplevelHDLGenerator would to
      // make one declaration for each version used in the circuit under test.
      super(ctx, "base", "LogisimClock", "i_ClockGen");
      this.shape = shape;
      this.clkbus = clkbus;
      this.id = id;
      int hi = shape.hi;
      int lo = shape.lo;
      int ph = shape.ph;
      int raw = clkbus.RawFPGAClock ? 1 : 0;
      if (raw == 1 && hi != lo)
        _err.AddFatalError("Clock component detected with " +hi+":"+lo+ " hi:lo duty cycle,"
            + " but maximum clock speed was selected. Only 1:1 duty cycle is supported with "
            + " maximum clock speed.");
      ph = ph % (hi + lo);
      if (ph != 0) // todo: support phase offset
        _err.AddFatalError("Clock component detected with "+ph+" tick phase offset,"
            + " but currently only 0 tick phase offset is supported for FPGA synthesis.");
      int max = (hi > lo) ? hi : lo;
      int w = 0;
      while (max != 0) {
        w++;
        max /= 2;
      }
      parameters.add("HighTicks", hi);
      parameters.add("LowTicks", lo);
      parameters.add("Phase", ph);
      parameters.add("CtrWidth", w);
      parameters.add("Raw", raw);
      inPorts.add("FPGAClock", 1, -1, null); // see getPortMappings below
      inPorts.add("FPGATick", 1, -1, null); // see getPortMappings below
      outPorts.add("ClockBus", 5, -1, null); // see getPortMappings below

      registers.add("s_output_regs", 4);
      registers.add("s_counter_reg", "CtrWidth");
      registers.add("s_derived_clock_reg", 1);
      wires.add("s_counter_next", "CtrWidth");
      wires.add("s_counter_is_zero", 1);
    }

    @Override
    protected void generateBehavior(Hdl out) {
      if (out.isVhdl) {
        out.stmt("ClockBus <= FPGACLock & '1' & '1' & NOT(FPGACLock) & FPGACLock");
        out.stmt("            WHEN (Raw = 1) ELSE");
        out.stmt("            FPGACLock & s_output_regs;");
        out.stmt("makeOutputs : PROCESS( FPGACLock )");
        out.stmt("BEGIN");
        out.stmt("   IF (FPGACLock'event AND (FPGACLock = '1')) THEN");
        out.stmt("      s_output_regs(0)  <= s_derived_clock_reg;");
        out.stmt("      s_output_regs(1)  <= NOT(s_derived_clock_reg);");
        out.stmt("      s_output_regs(2)  <= NOT(s_derived_clock_reg) AND --rising edge tick");
        out.stmt("                           FPGATick AND");
        out.stmt("                           s_counter_is_zero;");
        out.stmt("      s_output_regs(3)  <= s_derived_clock_reg AND --falling edge tick");
        out.stmt("                           FPGATick AND");
        out.stmt("                           s_counter_is_zero;");
        out.stmt("   END IF;");
        out.stmt("END PROCESS makeOutputs;");
        out.stmt("");
        out.stmt("s_counter_is_zero <= '1' WHEN s_counter_reg = std_logic_vector(to_unsigned(0,CtrWidth)) ELSE '0';");
        out.stmt("s_counter_next    <= std_logic_vector(unsigned(s_counter_reg) - 1)");
        out.stmt("                        WHEN s_counter_is_zero = '0' ELSE");
        out.stmt("                     std_logic_vector(to_unsigned((LowTicks-1),CtrWidth))");
        out.stmt("                        WHEN s_derived_clock_reg = '1' ELSE");
        out.stmt("                     std_logic_vector(to_unsigned((HighTicks-1),CtrWidth));");
        out.stmt("");
        out.stmt("makeDerivedClock : PROCESS( FPGACLock , FPGATick , s_counter_is_zero ,");
        out.stmt("                            s_derived_clock_reg)");
        out.stmt("BEGIN");
        out.stmt("   IF (FPGACLock'event AND (FPGACLock = '1')) THEN");
        out.stmt("      IF (s_derived_clock_reg /= '0' AND s_derived_clock_reg /= '1') THEN --For simulation only");
        out.stmt("         s_derived_clock_reg <= '0';");
        out.stmt("      ELSIF (s_counter_is_zero = '1' AND FPGATick = '1') THEN");
        out.stmt("         s_derived_clock_reg <= NOT(s_derived_clock_reg);");
        out.stmt("      END IF;");
        out.stmt("   END IF;");
        out.stmt("END PROCESS makeDerivedClock;");
        out.stmt("");
        out.stmt("makeCounter : PROCESS( FPGACLock , FPGATick , s_counter_next ,");
        out.stmt("                       s_derived_clock_reg )");
        out.stmt("BEGIN");
        out.stmt("   IF (FPGACLock'event AND (FPGACLock = '1')) THEN");
        out.stmt("      IF (s_derived_clock_reg /= '0' AND s_derived_clock_reg /= '1') THEN --For simulation only");
        out.stmt("         s_counter_reg <= (OTHERS => '0');");
        out.stmt("      ELSIF (FPGATick = '1') THEN");
        out.stmt("         s_counter_reg <= s_counter_next;");
        out.stmt("      END IF;");
        out.stmt("   END IF;");
        out.stmt("END PROCESS makeCounter;");
      } else {
        out.stmt("assign ClockBus = (Raw == 1)");
        out.stmt("                  ? {FPGACLock, 1'b1, 1'b1, ~FPGACLock, FPGACLock};");
        out.stmt("                  : {FPGACLock,s_output_regs};");
        out.stmt("always @(posedge FPGACLock)");
        out.stmt("begin");
        out.stmt("   s_output_regs[0] <= s_derived_clock_reg;");
        out.stmt("   s_output_regs[1] <= ~s_derived_clock_reg;");
        out.stmt("   s_output_regs[2] <= ~s_derived_clock_reg & FPGATick & s_counter_is_zero;");
        out.stmt("   s_output_regs[3] <= s_derived_clock_reg & FPGATick & s_counter_is_zero;");
        out.stmt("end");
        out.stmt("");
        out.stmt("assign s_counter_is_zero = (s_counter_reg == 0) ? 1'b1 : 1'b0;");
        out.stmt("assign s_counter_next = (s_counter_is_zero == 1'b0) ? s_counter_reg - 1 :");
        out.stmt("                        (s_derived_clock_reg == 1'b1) ? LowTicks - 1 :");
        out.stmt("                                                        HighTicks - 1;");
        out.stmt("");
        out.stmt("initial");
        out.stmt("begin");
        out.stmt("   s_output_regs = 0;");
        out.stmt("   s_derived_clock_reg = 0;");
        out.stmt("   s_counter_reg = 0;");
        out.stmt("end");
        out.stmt("");
        out.stmt("always @(posedge FPGACLock)");
        out.stmt("begin");
        out.stmt("   if (s_counter_is_zero & FPGATick)");
        out.stmt("   begin");
        out.stmt("      s_derived_clock_reg <= ~s_derived_clock_reg;");
        out.stmt("   end");
        out.stmt("end");
        out.stmt("");
        out.stmt("always @(posedge FPGACLock)");
        out.stmt("begin");
        out.stmt("   if (FPGATick)");
        out.stmt("   begin");
        out.stmt("      s_counter_reg <= s_counter_next;");
        out.stmt("   end");
        out.stmt("end");
      }
    }

    @Override
    protected void getPortMappings(Hdl.Map map, NetlistComponent compUnused, PortInfo p) {
      if (p.name.equals("FPGAClock")) {
        map.add(p.name, TickHDLGenerator.FPGA_CLK_NET);
      } else if (p.name.equals("FPGATick")) {
        map.add(p.name, TickHDLGenerator.FPGA_TICK_NET);
      } else if (p.name.equals("ClockBus")) {
        map.add(p.name, CLK_TREE_NET + id);
      }
    }

  }
}
