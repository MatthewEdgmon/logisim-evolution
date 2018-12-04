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

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;;
import java.util.List;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitEvent;
import com.cburch.logisim.circuit.CircuitListener;
import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.circuit.RadixOption;
import com.cburch.logisim.circuit.ReplacementMap;
import com.cburch.logisim.circuit.SubcircuitFactory;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.std.wiring.Clock;
import com.cburch.logisim.util.EventSourceWeakSupport;

public class Model implements CircuitListener, SignalInfo.Listener {

  public static final int STEP = 10;
  public static final int REAL = 20;
  public static final int CLOCK = 30;

  public static final int COARSE = 1;
  public static final int FINE = 2;

  public static class Event { } // not used

  public interface Listener { // event is always null for now
    public void signalsReset(Event event);
    public void signalsExtended(Event event);
    public void filePropertyChanged(Event event);
    public void selectionChanged(Event event);
    public void modeChanged(Event event);
    public void historyLimitChanged(Event event);
  }

  CircuitState circuitState;

  private ArrayList<SignalInfo> info = new ArrayList<>();
  private ArrayList<Signal> signals = new ArrayList<>();
  private long tEnd = -1; // signals go from 0 <= t < tEnd
  private Signal spotlight;
  private SignalInfo clockSource;

  private EventSourceWeakSupport<Listener> listeners = new EventSourceWeakSupport<>();
  private boolean fileEnabled = false;
  private File file = null;
  private boolean fileHeader = true;
  private boolean selected = false;
  private LogThread logger = null;
  private int mode = STEP, granularity = COARSE;
  private long timeScale = 5000, gateDelay = 10;
  private int cycleLength = 2;
  private int historyLimit = 400;

  public Model(CircuitState root) {
    circuitState = root;

    // Add top-level pins, clocks, etc.
    Circuit circ = circuitState.getCircuit();
    for (Component comp : circ.getNonWires()) {
      SignalInfo item = makeIfDefaultComponent(comp);
      if (item == null)
        continue;
      info.add(item);
    }
    
    // sort: inputs before outputs, then by location 
    // inputs are things like Button, Clock, Pin(input), and Random
    Location.sortHorizontal(info);
    int n = info.size();
    for (int i = 0; i < n; i++) {
      SignalInfo item = info.get(i);
      if (!item.isInput(null)) {
        info.add(info.remove(i));
        i--;
        n--;
      }
    }

    ArrayList<SignalInfo> clocks = ComponentSelector.findClocks(circ);
    if (clocks != null && clocks.size() == 1) {
      // If one clock is present, we use CLOCK mode with that as the source.
      clockSource = clocks.get(0);
    } else if (clocks != null && clocks.size() > 1) {
      // If multiple are present, ask user to select, with STEP as fallback.
      clockSource = ClockSource.doClockMultipleObserverDialog(circ);
    }
    if (clockSource != null) {
      if (!info.contains(clockSource))
        info.add(0, clockSource); // put it at the top of the list
      mode = CLOCK;
    }

    // set up initial signal values (after sorting and adding clock source)
    long duration = isFine() ? gateDelay : timeScale;
    for (int i = 0; i < info.size(); i++) {
      SignalInfo item = info.get(i);
      signals.add(new Signal(i, item, item.fetchValue(circuitState),
            duration, 0, historyLimit));
    }
    tEnd = duration;

    // Listen for new pins, clocks, etc., and changes to Signals
    for (SignalInfo item : info)
      item.setListener(this); // includes clock source
    circ.addCircuitListener(this);
  }
  
  private void renumberSignals() {
    for (int i = 0; i < signals.size(); i++)
      signals.get(i).idx = i;
  }

  public void addOrMove(List<SignalInfo> items, int idx) {
    int changed = items.size();
    for (SignalInfo item : items) {
      int i = info.indexOf(item);
      if (i < 0) {
        info.add(idx, item); // put new item at idx
        signals.add(idx,
            new Signal(idx, item, item.fetchValue(circuitState),
              1, tEnd - 1, historyLimit));
        idx++;
        item.setListener(this);
      } else if (i > idx) {
        info.add(idx, info.remove(i)); // move later item up
        signals.add(idx, signals.remove(i));
        idx++;
      } else if (i < idx) {
        info.add(idx-1, info.remove(i)); // move earlier item down
        signals.add(idx-1, signals.remove(i));
      } else {
        changed--;
      }
    }
    if (changed > 0) {
      renumberSignals();
      fireSelectionChanged(null);
    }
  }

  @Override
  public void signalInfoNameChanged(SignalInfo s) {
    fireSelectionChanged(null);
  }

  @Override
  public void signalInfoObsoleted(SignalInfo s) {
    if (s == clockSource) {
      clockSource.setListener(null); // redundant if info contains s
      clockSource = null;
      if (mode == CLOCK)
        setStepMode(timeScale, gateDelay);
    }
    remove(s);
  }

  public int remove(List<SignalInfo> items) {
    int count = 0;
    for (SignalInfo item : items) {
      int idx = info.indexOf(item);
      if (idx < 0)
        continue;
      info.remove(idx);
      signals.remove(idx);
      count++;
      item.setListener(null);
    }
    if (count > 0) {
      if (spotlight != null && items.contains(spotlight))
        spotlight = null;
      renumberSignals();
      fireSelectionChanged(null);
    }
    return count;
  }

  public void move(int[] fromIndex, int toIndex) {
    int n = fromIndex.length;
    if (n == 0)
      return;
    Arrays.sort(fromIndex);
    int a = fromIndex[0];
    int b = fromIndex[n-1];
    if (a < 0 || b > info.size())
      return; // invalid selection
    if (a <= toIndex && toIndex <= b && b-a+1 == n)
      return; // no-op
    ArrayList<SignalInfo> items = new ArrayList<>();
    ArrayList<Signal> vals = new ArrayList<>();
    for (int i = n-1; i >= 0; i--) {
      if (fromIndex[i] < toIndex)
        toIndex--;
      items.add(info.remove(fromIndex[i]));
      vals.add(signals.remove(fromIndex[i]));
    }
    for (int i = n-1; i >= 0; i--) {
      info.add(toIndex, items.get(i));
      signals.add(toIndex, vals.get(i));
      toIndex++;
    }
    renumberSignals();
    fireSelectionChanged(null);
  }

  public void remove(int idx) {
    if (spotlight != null && signals.get(idx) == spotlight)
      spotlight = null;
    info.remove(idx).setListener(null);
    signals.remove(idx);
    renumberSignals();
    fireSelectionChanged(null);
  }

  public void remove(SignalInfo item) {
    remove(info.indexOf(item));
  }

  public long getTimeScale() {
    return timeScale;
  }

  public long getGateDelay() {
    return gateDelay;
  }

  public int getCycleLength() {
    return cycleLength;
  }

  public boolean isStepMode() { return mode == STEP; }
  public boolean isRealMode() { return mode == REAL; }
  public boolean isClockMode() { return mode == CLOCK; }
  public boolean isFine() { return granularity == FINE; }

  public int getHistoryLimit() {
    return historyLimit;
  }
  public void setHistoryLimit(int limit) {
    if (historyLimit == limit)
      return;
    historyLimit = limit;
    for (Signal s : signals)
      s.resize(historyLimit);
    fireHistoryLimitChanged(null);
  }

  public void setStepMode(long t, long d) {
    int g = d > 0 ? FINE : COARSE;
    if (mode == STEP && granularity == g && timeScale == t && gateDelay == d)
      return;
    timeScale = t;
    gateDelay = d;
    setMode(STEP, g);
  }

  public void setRealMode(long t, boolean fine) {
    int g = fine ? FINE : COARSE;
    if (mode == REAL && granularity == g && timeScale == t)
      return;
    timeScale = t;
    setMode(REAL, g);
  }

  public void setClockMode(long t, int n, long d) {
    int g = d > 0 ? FINE : COARSE;
    if (mode == CLOCK && granularity == g && timeScale == t && cycleLength == n && gateDelay == d)
      return;
    if (clockSource == null) {
      Circuit circ = circuitState.getCircuit();
      // select a clock source now
      ArrayList<SignalInfo> clocks = ComponentSelector.findClocks(circ);
      if (clocks != null && clocks.size() == 1) {
        // If one clock is present, just use that.
        clockSource = clocks.get(0);
      } else if (clocks != null && clocks.size() > 1) {
        // If multiple are present, ask user to select
        clockSource = ClockSource.doClockMultipleObserverDialog(circ);
      } else if (clocks != null) {
        // No clocks, but other suitable things, ask user to select
        clockSource = ClockSource.doClockMissingObserverDialog(circ);
      }
      if (clockSource == null) {
        // go back to current mode
        setMode(mode, granularity);
        return;
      }
      // Add the clock as a courtesy, though it is optional.
      if (!info.contains(clockSource)) {
        info.add(0, clockSource); // put it at the top of the list
        signals.add(0,
            new Signal(0, clockSource, clockSource.fetchValue(circuitState),
              1, tEnd - 1, historyLimit));
        clockSource.setListener(this);
        fireSelectionChanged(null);
      }
    }
    timeScale = t;
    cycleLength = n;
    gateDelay = d;
    setMode(CLOCK, g);
  }

  private void setMode(int m, int g) {
    mode = m;
    granularity = g;
    fireModeChanged(null);
  }

  @Override
  public void circuitChanged(CircuitEvent event) {
    int action = event.getAction();
    if (action == CircuitEvent.TRANSACTION_DONE) {
      Circuit circ = circuitState.getCircuit();
      ReplacementMap repl = event.getResult().getReplacementMap(circ);
      if (repl == null || repl.isEmpty())
        return;
      // look for new pins, etc., that are not simply replacing old pins
      for (Component comp : repl.getAdditions()) {
        if (!repl.getReplacedBy(comp).isEmpty())
          continue;
        // if (mode == STEP && containsAnyClock(comp))
        //   setMode(CLOCK, granularity);
        SignalInfo item = makeIfDefaultComponent(comp);
        if (item == null)
          continue;
        addAndInitialize(item, true);
      }
    }
  }

  // Make top-level pins, clocks, and any other loggable component that doesn't
  // have options (so we exclude Ram/Rom) and isn't a subcircuit.
  private SignalInfo makeIfDefaultComponent(Component comp) {
    if (comp.getFactory() instanceof SubcircuitFactory)
      return null;
    Loggable log = (Loggable)comp.getFeature(Loggable.class);
    if (log == null)
      return null;
    Object[] opts = log.getLogOptions();
    if (opts != null && opts.length > 0) // exclude Ram, Rom, etc.
      return null;
    Component[] path = new Component[] { comp };
    return new SignalInfo(circuitState.getCircuit(), path, null);
  }

  private Signal addAndInitialize(SignalInfo item, boolean fireUpdate) {
    int idx = info.indexOf(item);
    if (idx >= 0)
      return signals.get(idx);
    idx = info.size();
    info.add(item);
    Signal s = new Signal(idx, item, item.fetchValue(circuitState),
        1, tEnd - 1, historyLimit);
    signals.add(idx, s);
    item.setListener(this);
    if (fireUpdate)
      fireSelectionChanged(null);
    return s;
  }

	public void addSignalValues(Value[] vals, long duration) {
    for (int i = 0; i < signals.size() && i < vals.length; i++)
      signals.get(i).extend(vals[i], duration);
    tEnd += duration;
	}

  public void addModelListener(Listener l) {
    listeners.add(l);
  }

  public void removeModelListener(Listener l) {
    listeners.remove(l);
  }

  private void fireSignalsReset(Event e) {
    for (Listener l : listeners)
      l.signalsReset(e);
  }

  private void fireSignalsExtended(Event e) {
    for (Listener l : listeners)
      l.signalsExtended(e);
  }

  private void fireFilePropertyChanged(Event e) {
    for (Listener l : listeners)
      l.filePropertyChanged(e);
  }

  private void fireModeChanged(Event e) {
    for (Listener l : listeners)
      l.modeChanged(e);
  }

  private void fireHistoryLimitChanged(Event e) {
    for (Listener l : listeners)
      l.historyLimitChanged(e);
  }

  void fireSelectionChanged(Event e) {
    for (Listener l : listeners)
      l.selectionChanged(e);
  }

  public CircuitState getCircuitState() {
    return circuitState;
  }

  public Circuit getCircuit() {
    return circuitState == null ? null : circuitState.getCircuit();
  }

  public File getFile() {
    return file;
  }

  public boolean getFileHeader() {
    return fileHeader;
  }

  public int getSignalCount() {
    return signals.size();
  }

  public ArrayList<Signal> getSignals() {
    return signals;
  }

  public long getStartTime() {
    // if any signals are full (due to history limit), don't show
    // earlier data (it looks funny in histogram and table)
    long t = 0;
    for (Signal s: signals)
      t = Math.max(t, s.omittedDataTime());
    return t;
  }

  public long getEndTime() {
    return tEnd;
  }

  public SignalInfo getItem(int idx) {
    return info.get(idx);
  }

  public Signal getSignal(int idx) {
    return signals.get(idx);
  }

  public Signal getSignal(SignalInfo item) {
    return signals.get(info.indexOf(item));
  }

  public int indexOf(SignalInfo item) {
    return info.indexOf(item);
  }

  public boolean isFileEnabled() {
    return fileEnabled;
  }

  public boolean isSelected() {
    return selected;
  }

  public void propagationCompleted(boolean ticked, boolean stepped, boolean propagated) {
    if (isClockMode() && !isFine()) {
      long duration = timeScale;
      if (ticked) {
        for (Signal s : signals) {
          Value v = s.info.fetchValue(circuitState);
          s.extend(v, duration);
        }
        tEnd += duration;
        fireSignalsExtended(null);
      } else {
        // back-date transient changes to the start of the most recent tick
        for (Signal s : signals) {
          Value v = s.info.fetchValue(circuitState);
          s.replaceRecent(v, duration);
        }
        fireSignalsExtended(null); // not really "extended", but works fine
      }
      return;
    }
    long duration;
    if (ticked || propagated)
      duration = timeScale;
    else if (stepped && isFine())
      duration = gateDelay;
    else
      return;

    for (Signal s : signals) {
      Value v = s.info.fetchValue(circuitState);
      s.extend(v, duration);
    }
    tEnd += duration;
    fireSignalsExtended(null);
  }

  public void simulatorReset() {
    long duration = isFine() ? gateDelay : timeScale;
    // long duration = 1;
    for (Signal s: signals) {
      Value v = s.info.fetchValue(circuitState);
      s.reset(v, duration);
    }
    tEnd = duration;
	}

  public void setFile(File value) {
    if (file == null ? value == null : file.equals(value))
      return;
    file = value;
    fileEnabled = file != null;
    fireFilePropertyChanged(null);
  }

  public void setFileEnabled(boolean value) {
    if (fileEnabled == value)
      return;
    fileEnabled = value;
    fireFilePropertyChanged(null);
  }

  public void setFileHeader(boolean value) {
    if (fileHeader == value)
      return;
    fileHeader = value;
    fireFilePropertyChanged(null);
  }

  public void setSelected(boolean value) {
    if (selected == value)
      return;
    selected = value;
    if (selected) {
      logger = new LogThread(this);
      logger.start();
    } else {
      if (logger != null)
        logger.cancel();
      logger = null;
      fileEnabled = false;
    }
    fireFilePropertyChanged(null);
  }

  // In the ChronoPanel, the (at most one) signal under the mouse is
  // highlighted. Multiple other rows can be selected by clicking. This
  // code maybe should be put elsewhere.
  public Signal getSpotlight() {
    return spotlight;
  }

  public Signal setSpotlight(Signal s) {
    Signal old = spotlight;
    spotlight = s;
    return old;
  }

  public SignalInfo getClockSourceInfo() {
    return clockSource;
  }

  public void setClockSourceInfo(SignalInfo item) {
    if (clockSource == item)
      return;
    clockSource = item;
    fireModeChanged(null);
  }

  public static String formatDuration(long t) {
    if (t < 1000 || (t % 100) != 0)
      return S.fmt("nsFormat", String.format("%d", t));
    else if (t < 1000000 || (t % 100000) != 0)
      return S.fmt("usFormat", String.format("%.1f", t/1000.0));
    else if (t < 100000000 || (t % 100000000) != 0)
      return S.fmt("msFormat", String.format("%.1f", t/1000000.0));
    else
      return S.fmt("sFormat", String.format("%.1f", t/1000000000.0));
  }

  public void setRadix(SignalInfo s, RadixOption value) {
    if (s.setRadix(value))
      fireSelectionChanged(null);
  }

}
