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
package com.cburch.logisim.std.io;

import java.util.ArrayList;

import com.bfh.logisim.designrulecheck.NetlistComponent;
import com.bfh.logisim.hdlgenerator.AbstractHDLGeneratorFactory;
import com.cburch.logisim.hdl.Hdl;

public class ButtonHDLGeneratorFactory extends AbstractHDLGeneratorFactory {

  public ButtonHDLGeneratorFactory(HDLCTX ctx, String name) { 
    super(ctx, name + "_${LABEL}", "BUTTON"); // names irrelevant, b/c always inlined ?
  }

  @Override
  public boolean IsOnlyInlined(/*String lang*/) { return true; }

  @Override
  public ArrayList<String> GetInlinedCode(NetlistComponent info) {
    if (_nets == null) throw new IllegalStateException();
    Hdl out = new Hdl(_lang, _err);
    int b = info.GetLocalBubbleInputStartId();
    for (int i = 0; i < info.NrOfEnds(); i++) {
      if (info.EndIsConnected(i)) {
        String name = GetNetName(info, i, true, _lang, _nets);
        out.assign(name, LocalInputBubbleBusname, b + i);
      }
    }
    return out;
  }
}
