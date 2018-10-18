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

package com.cburch.logisim.gui.start;
import static com.cburch.logisim.gui.start.Strings.S;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.swing.JOptionPane;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cburch.logisim.LogisimVersion;
import com.cburch.logisim.Main;
import com.cburch.logisim.file.LoadFailedException;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.gui.main.Print;
import com.cburch.logisim.gui.menu.LogisimMenuBar;
import com.cburch.logisim.gui.menu.WindowManagers;
import com.cburch.logisim.prefs.AppPreferences;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.proj.ProjectActions;
import com.cburch.logisim.util.ArgonXML;
import com.cburch.logisim.util.LocaleManager;
import com.cburch.logisim.util.MacCompatibility;

public class Startup {

  static void doOpen(File file) {
    if (startupTemp != null) {
      startupTemp.doOpenFile(file);
    }
  }

  static void doPrint(File file) {
    if (startupTemp != null) {
      startupTemp.doPrintFile(file);
    }
  }

  public static Startup parseArgs(String[] args) {
    // see whether we'll be using any graphics
    boolean isClearPreferences = false;
    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-tty")
          || args[i].equals("-list")
          || args[i].equals("-png")) {
        Main.headless = true;
      } else if (args[i].equals("-clearprefs")
          || args[i].equals("-clearprops")) {
        isClearPreferences = true;
      }
    }

    if (!Main.headless) {
      // we're using the GUI: Set up the Look&Feel to match the platform
      System.setProperty(
          "com.apple.mrj.application.apple.menu.about.name",
          "Logisim-evolution");
      System.setProperty(
          "apple.awt.application.name",
          "Logisim-evolution");
      System.setProperty("apple.laf.useScreenMenuBar", "true");

      LocaleManager.setReplaceAccents(false);

      // Initialize graphics acceleration if appropriate
      AppPreferences.handleGraphicsAcceleration();
    }

    Startup ret = new Startup();
    startupTemp = ret;
    if (!Main.headless) {
      registerHandler();
    }

    if (isClearPreferences) {
      AppPreferences.clear();
    }

    // parse arguments
    for (int i = 0; i < args.length; i++) {
      String arg = args[i];
      if (arg.equals("-tty")) {
        ret.headlessTty = true;
        if (i + 1 < args.length) {
          i++;
          String[] fmts = args[i].split(",");
          if (fmts.length == 0) {
            logger.error("{}", S.get("ttyFormatError"));
          }
          for (int j = 0; j < fmts.length; j++) {
            String fmt = fmts[j].trim();
            if (fmt.equals("table")) {
              ret.ttyFormat |= TtyInterface.FORMAT_TABLE;
            } else if (fmt.equals("speed")) {
              ret.ttyFormat |= TtyInterface.FORMAT_SPEED;
            } else if (fmt.equals("tty")) {
              ret.ttyFormat |= TtyInterface.FORMAT_TTY;
            } else if (fmt.equals("halt")) {
              ret.ttyFormat |= TtyInterface.FORMAT_HALT;
            } else if (fmt.equals("stats")) {
              ret.ttyFormat |= TtyInterface.FORMAT_STATISTICS;
            } else if (fmt.equals("binary")) {
              ret.ttyFormat |= TtyInterface.FORMAT_TABLE_BIN;
            } else if (fmt.equals("hex")) {
              ret.ttyFormat |= TtyInterface.FORMAT_TABLE_HEX;
            } else if (fmt.equals("csv")) {
              ret.ttyFormat |= TtyInterface.FORMAT_TABLE_CSV;
            } else if (fmt.equals("tabs")) {
              ret.ttyFormat |= TtyInterface.FORMAT_TABLE_TABBED;
            } else {
              logger.error("{}", S.get("ttyFormatError"));
            }
          }
        } else {
          logger.error("{}", S.get("ttyFormatError"));
          return null;
        }
      } else if (arg.equals("-png")) {
        ret.headlessPng = true;
        if (i + 1 < args.length) {
          i++;
          String[] circuits = args[i].split(",");
          if (circuits.length == 0) {
            logger.error("{}", S.get("pngArgError"));
          }
          ret.headlessPngCircuits = circuits;
        } else {
          logger.error("{}", S.get("pngArgError"));
          return null;
        }
      } else if (arg.equals("-list")) {
        ret.headlessList = true;
      } else if (arg.equals("-sub")) {
        if (i + 2 < args.length) {
          File a = new File(args[i + 1]);
          File b = new File(args[i + 2]);
          if (ret.substitutions.containsKey(a)) {
            logger.error("{}",
                S.get("argDuplicateSubstitutionError"));
            return null;
          } else {
            ret.substitutions.put(a, b);
            i += 2;
          }
        } else {
          logger.error("{}", S.get("argTwoSubstitutionError"));
          return null;
        }
      } else if (arg.equals("-load")) {
        if (i + 1 < args.length) {
          i++;
          if (ret.loadFile != null) {
            logger.error("{}", S.get("loadMultipleError"));
          }
          File f = new File(args[i]);
          ret.loadFile = f;
        } else {
          logger.error("{}", S.get("loadNeedsFileError"));
          return null;
        }
      } else if (arg.equals("-empty")) {
        if (ret.templFile != null || ret.templEmpty || ret.templPlain) {
          logger.error("{}", S.get("argOneTemplateError"));
          return null;
        }
        ret.templEmpty = true;
      } else if (arg.equals("-plain")) {
        if (ret.templFile != null || ret.templEmpty || ret.templPlain) {
          logger.error("{}", S.get("argOneTemplateError"));
          return null;
        }
        ret.templPlain = true;
      } else if (arg.equals("-version")) {
        System.out.println(Main.VERSION_NAME); // OK
        return null;
      } else if (arg.equals("-gates")) {
        i++;
        if (i >= args.length) {
          printUsage();
        }
        String a = args[i];
        if (a.equals("shaped")) {
          AppPreferences.GATE_SHAPE.set(AppPreferences.SHAPE_SHAPED);
        } else if (a.equals("rectangular")) {
          AppPreferences.GATE_SHAPE
              .set(AppPreferences.SHAPE_RECTANGULAR);
        } else {
          logger.error("{}", S.get("argGatesOptionError"));
          System.exit(-1);
        }
      } else if (arg.equals("-geom")) {
        i++;
        if (i >= args.length) {
          printUsage();
        }
        String wxh[] = args[i].split("[xX]");
        if (wxh.length != 2 || wxh[0].length() < 1 || wxh[1].length() < 1) {
          logger.error("{}", S.get("argGeometryError"));
          System.exit(1);
        }
        int p = wxh[1].indexOf('+', 1);
        String loc = null;
        int x = 0, y = 0;
        if (p >= 0) {
          loc = wxh[1].substring(p+1);
          wxh[1] = wxh[1].substring(0, p);
          String xy[] = loc.split("\\+");
          if (xy.length != 2 || xy[0].length() < 1 || xy[0].length() < 1) {
            logger.error("{}", S.get("argGeometryError"));
            System.exit(1);
          }
          try {
            x = Integer.parseInt(xy[0]);
            y = Integer.parseInt(xy[1]);
          } catch (NumberFormatException e) {
            logger.error("{}", S.get("argGeometryError"));
            System.exit(1);
          }
        }
        int w = 0, h = 0;
        try {
          w = Integer.parseInt(wxh[0]);
          h = Integer.parseInt(wxh[1]);
        } catch (NumberFormatException e) {
          logger.error("{}", S.get("argGeometryError"));
          System.exit(1);
        }
        if (w <= 0 || h <= 0) {
          logger.error("{}", S.get("argGeometryError"));
          System.exit(1);
        }
        AppPreferences.WINDOW_WIDTH.set(w);
        AppPreferences.WINDOW_HEIGHT.set(h);
        if (loc != null)
          AppPreferences.WINDOW_LOCATION.set(x+","+y);
      } else if (arg.equals("-locale")) {
        i++;
        if (i >= args.length) {
          printUsage();
        }
        setLocale(args[i]);
      } else if (arg.equals("-accents")) {
        i++;
        if (i >= args.length) {
          printUsage();
        }
        String a = args[i];
        if (a.equals("yes")) {
          AppPreferences.ACCENTS_REPLACE.setBoolean(false);
        } else if (a.equals("no")) {
          AppPreferences.ACCENTS_REPLACE.setBoolean(true);
        } else {
          logger.error("{}", S.get("argAccentsOptionError"));
          System.exit(-1);
        }
      } else if (arg.equals("-template")) {
        if (ret.templFile != null || ret.templEmpty || ret.templPlain) {
          logger.error("{}", S.get("argOneTemplateError"));
          return null;
        }
        i++;
        if (i >= args.length) {
          printUsage();
        }
        ret.templFile = new File(args[i]);
        if (!ret.templFile.exists()) {
          logger.error("{}", S.fmt("templateMissingError", args[i]));
        } else if (!ret.templFile.canRead()) {
          logger.error("{}", S.fmt("templateCannotReadError", args[i]));
        }
      } else if (arg.equals("-nosplash")) {
        ret.showSplash = false;
      } else if (arg.equals("-test")) {
        i++;
        if (i >= args.length)
          printUsage();
        ret.circuitToTest = args[i];
        i++;
        if (i >= args.length)
          printUsage();
        ret.testVector = args[i];
        ret.showSplash = false;
        ret.exitAfterStartup = true;
      } else if (arg.equals("-circuit")) {
        i++;
        if (i >= args.length)
          printUsage();
        ret.circuitToTest = args[i];
      } else if (arg.equals("-clearprefs") || arg.equals("-clearprops")) {
        // already handled above
      } else if (arg.equals("-analyze")) {
        Main.ANALYZE = true;
      } else if (arg.equals("-noupdates")) {
        Main.UPDATE = false;
      } else if (arg.equals("-questa")) {
        i++;
        if (i >= args.length) {
          printUsage();
        }
        String a = args[i];
        if (a.equals("yes")) {
          AppPreferences.QUESTA_VALIDATION.setBoolean(true);
        } else if (a.equals("no")) {
          AppPreferences.QUESTA_VALIDATION.setBoolean(false);
        } else {
          logger.error("{}", S.get("argQuestaOptionError"));
          System.exit(-1);
        }
      } else if (arg.charAt(0) == '-') {
        printUsage();
        return null;
      } else {
        ret.filesToOpen.add(new File(arg));
      }
    }

    if (ret.exitAfterStartup && ret.filesToOpen.isEmpty()) {
      printUsage();
    }
    if (Main.headless && ret.filesToOpen.isEmpty()) {
      logger.error("{}", S.get("ttyNeedsFileError"));
      return null;
    }
    if (ret.loadFile != null && !Main.headless) {
      logger.error("{}", S.get("loadNeedsTtyError"));
      return null;
    }

    return ret;
  }

  private static void printUsage() {
    System.err.println(S.fmt("argUsage", Startup.class.getName())); // OK
    System.err.println(); // OK
    System.err.println(S.get("argOptionHeader")); // OK
    System.err.println("   " + S.get("argNoUpdatesOption")); // OK
    System.err.println("   " + S.get("argGeometryOption")); // OK
    System.err.println("   " + S.get("argAccentsOption")); // OK
    System.err.println("   " + S.get("argClearOption")); // OK
    System.err.println("   " + S.get("argEmptyOption")); // OK
    System.err.println("   " + S.get("argAnalyzeOption")); // OK
    System.err.println("   " + S.get("argTestOption")); // OK
    System.err.println("   " + S.get("argGatesOption")); // OK
    System.err.println("   " + S.get("argHelpOption")); // OK
    System.err.println("   " + S.get("argLoadOption")); // OK
    System.err.println("   " + S.get("argLocaleOption")); // OK
    System.err.println("   " + S.get("argNoSplashOption")); // OK
    System.err.println("   " + S.get("argPlainOption")); // OK
    System.err.println("   " + S.get("argSubOption")); // OK
    System.err.println("   " + S.get("argTemplateOption")); // OK
    System.err.println("   " + S.get("argTtyOption")); // OK
    System.err.println("   " + S.get("argCircuitOption")); // OK
    System.err.println("   " + S.get("argListOption")); // OK
    System.err.println("   " + S.get("argPngOption")); // OK
    System.err.println("   " + S.get("argPngsOption")); // OK
    System.err.println("   " + S.get("argQuestaOption")); // OK
    System.err.println("   " + S.get("argVersionOption")); // OK
    System.exit(-1);
  }

  private static void registerHandler() {
    String prop = System.getProperty("os.name");
    if (prop != null && prop.toLowerCase().contains("os x") && System.getProperty("mrj.version") == null) {
      // MRJ is no longer present on OS X, but we want MRJAdapter to keep working for now.
      System.setProperty("mrj.version", "99.0.0");
    }
    try {
      Class<?> needed1 = Class.forName("com.apple.eawt.Application");
      if (needed1 == null) {
        return;
      }
      Class<?> needed2 = Class
          .forName("com.apple.eawt.ApplicationAdapter");
      if (needed2 == null) {
        return;
      }
      MacOsAdapter.register();
      MacOsAdapter.addListeners(true);
    } catch (ClassNotFoundException e) {
      return;
    } catch (Exception t) {
      try {
        MacOsAdapter.addListeners(false);
      } catch (Exception t2) {
      }
    }
  }

  private static void setLocale(String lang) {
    Locale[] opts = S.getLocaleOptions();
    for (int i = 0; i < opts.length; i++) {
      if (lang.equals(opts[i].toString())) {
        LocaleManager.setLocale(opts[i]);
        return;
      }
    }
    logger.warn("{}", S.get("invalidLocaleError"));
    logger.warn("{}", S.get("invalidLocaleOptionsHeader"));

    for (int i = 0; i < opts.length; i++) {
      logger.warn("   {}", opts[i].toString());
    }
    System.exit(-1);
  }

  final static Logger logger = LoggerFactory.getLogger(Startup.class);

  private static Startup startupTemp = null;
  // based on command line
  boolean headlessTty, headlessPng, headlessList;
  String headlessPngCircuits[];
  private File templFile = null;
  private boolean templEmpty = false;
  private boolean templPlain = false;
  private ArrayList<File> filesToOpen = new ArrayList<File>();
  private String testVector = null;
  private String circuitToTest = null;
  private boolean exitAfterStartup = false;
  private boolean showSplash;
  private File loadFile;
  private HashMap<File, File> substitutions = new HashMap<File, File>();
  private int ttyFormat = 0;
  // from other sources
  private boolean initialized = false;
  private SplashScreen monitor = null;

  private ArrayList<File> filesToPrint = new ArrayList<File>();

  private Startup() {
    this.showSplash = !Main.headless;
  }

  /**
   * Auto-update Logisim-evolution if a new version is available
   *
   * Original idea taken from Jupar:
   * http://masterex.github.io/archive/2011/12/25/jupar.html by Periklis
   * Master_ex Ntanasis <pntanasis@gmail.com>
   *
   * @return true if the code has been updated, and therefore the execution
   *         has to be stopped, false otherwise
   */
  public boolean autoUpdate() {
    if (!Main.UPDATE || !networkConnectionAvailable()) {
      // Auto-update disabled from command line, or network connection not
      // available
      return (false);
    }

    // Get the remote XML file containing the current version
    URL xmlURL;
    try {
      xmlURL = new URL(Main.UPDATE_URL);
    } catch (MalformedURLException e) {
      logger.error("The URL of the XML file for the auto-updater is malformed.\n"
          + "Please report this error to the software maintainer\n"
          + "-- AUTO-UPDATE ABORTED --");
      return (false);
    }
    URLConnection conn;
    try {
      conn = xmlURL.openConnection();
    } catch (IOException e) {
      logger.error("Although an Internet connection should be available, the system couldn't connect "
          + "to the URL requested by the auto-updater\nIf the error persist, please "
          + " contact the software maintainer\n"
          + "-- AUTO-UPDATE ABORTED --");
      return (false);
    }
    InputStream in;
    try {
      in = conn.getInputStream();
    } catch (IOException e) {
      logger.error("Although an Internet connection should be available, the system couldn't retrieve "
          + "the data requested by the auto-updater.\n"
          + "If the error persist, please contact the software maintainer\n"
          + "-- AUTO-UPDATE ABORTED --");
      return (false);
    }
    ArgonXML logisimData = new ArgonXML(in, "logisim-evolution");

    // Get the appropriate remote version number
    LogisimVersion remoteVersion = LogisimVersion.parse(Main.VERSION
        .hasTracker() ? logisimData.child("tracked_version").content()
        : logisimData.child("untracked_version").content());

    // If the remote version is newer, perform the update
    if (remoteVersion.compareTo(Main.VERSION) > 0) {
      int answer = JOptionPane.showConfirmDialog(null,
          "A new Logisim-evolution version (" + remoteVersion
          + ") is available!\nWould you like to update?",
          "Update", JOptionPane.YES_NO_OPTION,
          JOptionPane.INFORMATION_MESSAGE);

      if (answer == 1) {
        // User refused to update -- we just hope he gets sufficiently
        // annoyed by the message that he finally updates!
        return (false);
      }

      // Obtain the base directory of the jar archive
      CodeSource codeSource = Startup.class.getProtectionDomain()
          .getCodeSource();
      File jarFile = null;
      try {
        jarFile = new File(codeSource.getLocation().toURI().getPath());
      } catch (URISyntaxException e) {
        logger.error("Error in the syntax of the URI for the path of the executed Logisim-evolution JAR file!");
        e.printStackTrace();
        JOptionPane
            .showMessageDialog(
                null,
                "An error occurred while updating to the new Logisim-evolution version.\nPlease check the console for log information.",
                "Update failed", JOptionPane.ERROR_MESSAGE);
        return (false);
      }

      // Get the appropriate remote filename to download
      String remoteJar = Main.VERSION.hasTracker() ? logisimData.child(
          "tracked_file").content() : logisimData.child(
            "untracked_file").content();

      boolean updateOk = downloadInstallUpdatedVersion(remoteJar,
          jarFile.getAbsolutePath());

      if (updateOk) {
        JOptionPane
            .showMessageDialog(
                null,
                "The new Logisim-evolution version ("
                + remoteVersion
                + ") has been correctly installed.\nPlease restart Logisim-evolution for the changes to take effect.",
                "Update succeeded",
                JOptionPane.INFORMATION_MESSAGE);
        return (true);
      } else {
        JOptionPane
            .showMessageDialog(
                null,
                "An error occurred while updating to the new Logisim-evolution version.\nPlease check the console for log information.",
                "Update failed", JOptionPane.ERROR_MESSAGE);
        return (false);
      }
    }
    return (false);
  }

  private void doOpenFile(File file) {
    if (initialized) {
      ProjectActions.doOpen(null, null, file);
    } else {
      filesToOpen.add(file);
    }
  }

  private void doPrintFile(File file) {
    if (initialized) {
      Project toPrint = ProjectActions.doOpen(null, null, file);
      Print.doPrint(toPrint);
      toPrint.getFrame().dispose();
    } else {
      filesToPrint.add(file);
    }
  }

  /**
   * Download a new version of Logisim, according to the instructions received
   * from autoUpdate(), and install it at the specified location
   *
   * Original idea taken from:
   * http://baptiste-wicht.developpez.com/tutoriels/java/update/ by Baptiste
   * Wicht
   *
   * @param filePath
   *            remote file URL
   * @param destination
   *            local destination for the updated Jar file
   * @return true if the new version has been downloaded and installed, false
   *         otherwise
   * @throws IOException
   */
  private boolean downloadInstallUpdatedVersion(String filePath,
      String destination) {
    URL fileURL;
    try {
      fileURL = new URL(filePath);
    } catch (MalformedURLException e) {
      logger.error("The URL of the requested update file is malformed.\n"
          + "Please report this error to the software maintainer.\n"
          + "-- AUTO-UPDATE ABORTED --");
      return (false);
    }
    URLConnection conn;
    try {
      conn = fileURL.openConnection();
    } catch (IOException e) {
      logger.error("Although an Internet connection should be available, the system couldn't connect "
          + " to the URL of the updated file requested by the auto-updater.\n"
          + " If the error persist, please contact the software maintainer\n"
          + "-- AUTO-UPDATE ABORTED --");
      return (false);
    }

    // Get remote file size
    int length = conn.getContentLength();
    if (length == -1) {
      logger.error("Cannot retrieve the file containing the updated version.\n"
          + " If the error persist, please contact the software maintainer\n"
          + "-- AUTO-UPDATE ABORTED --");
      return (false);
    }

    // Get remote file stream
    InputStream is;
    try {
      is = new BufferedInputStream(conn.getInputStream());
    } catch (IOException e) {
      logger.error("Cannot get remote file stream.\n"
          + "If the error persist, please contact the software maintainer\n"
          + "-- AUTO-UPDATE ABORTED --");
      return (false);
    }

    // Local file buffer
    byte[] data = new byte[length];

    // Helper variables for marking the current position in the downloaded
    // file
    int currentBit = 0;
    int deplacement = 0;

    // Download remote content
    try {
      while (deplacement < length) {
        currentBit = is.read(data, deplacement, data.length
            - deplacement);

        if (currentBit == -1) {
          // Reached EOF
          break;
        }
        deplacement += currentBit;
      }
    } catch (IOException e) {
      logger.error("An error occured while retrieving remote file (remote peer hung up).\n"
          + "If the error persist, please contact the software maintainer\n"
          + "-- AUTO-UPDATE ABORTED --");
      return (false);
    }
    // Close remote stream
    try {
      is.close();
    } catch (IOException e) {
      logger.error("Error encountered while closing the remote stream!");
      e.printStackTrace();
    }

    // If not all the bytes have been retrieved, abort update
    if (deplacement != length) {
      logger.error("An error occured while retrieving remote file (local size != remote size), download corrupted.\n"
          + "If the error persist, please contact the software maintainer\n"
          + "-- AUTO-UPDATE ABORTED --");
      return (false);
    }

    // Open stream for local Jar and write data
    FileOutputStream destinationFile;
    try {
      destinationFile = new FileOutputStream(destination);
    } catch (FileNotFoundException e) {
      logger.error("An error occured while opening the local Jar file.\n"
          + "-- AUTO-UPDATE ABORTED --");
      return (false);
    }
    try {
      destinationFile.write(data);
      destinationFile.flush();
    } catch (IOException e) {
      logger.error("An error occured while writing to the local Jar file.\n"
          + "-- AUTO-UPDATE ABORTED --\n"
          + "The local file might be corrupted. If this is the case, please "
          + "download a new copy of Logisim.");
    } finally {
      try {
        destinationFile.close();
      } catch (IOException e) {
        logger.error("Error encountered while closing the local destination file!\n"
            + "The local file might be corrupted. If this is the case, please "
            + "download a new copy of Logisim.");
        return (false);
      }
    }

    return (true);
  }

  List<File> getFilesToOpen() {
    return filesToOpen;
  }

  File getLoadFile() {
    return loadFile;
  }

  String getCircuitToTest() {
    return circuitToTest;
  }

  Map<File, File> getSubstitutions() {
    return Collections.unmodifiableMap(substitutions);
  }

  int getTtyFormat() {
    return ttyFormat;
  }

  private void loadTemplate(Loader loader, File templFile, boolean templEmpty) {
    if (showSplash) {
      monitor.setProgress(SplashScreen.TEMPLATE_OPEN);
    }
    if (templFile != null) {
      AppPreferences.setTemplateFile(templFile);
      AppPreferences.setTemplateType(AppPreferences.TEMPLATE_CUSTOM);
    } else if (templEmpty) {
      AppPreferences.setTemplateType(AppPreferences.TEMPLATE_EMPTY);
    } else if (templPlain) {
      AppPreferences.setTemplateType(AppPreferences.TEMPLATE_PLAIN);
    }
  }

  /**
   * Check if network connection is available.
   *
   * This function tries to connect to google in order to test the
   * availability of a network connection. This step is needed before
   * attempting to perform an auto-update. It assumes that google is
   * accessible -- usually this is the case, and it should also provide a
   * quick reply to the connection attempt, reducing the lag.
   *
   * @return true if the connection is available, false otherwise
   */
  private boolean networkConnectionAvailable() {
    try {
      URL url = new URL("http://www.google.com");
      URLConnection uC = url.openConnection();
      uC.connect();
      return (true);
    } catch (MalformedURLException e) {
      logger.error("The URL used to check the connectivity is malformed -- no Google?");
      e.printStackTrace();
    } catch (IOException e) {
      // If we get here, the connection somehow failed
      return (false);
    }
    return (false);
  }

  public void run() {
    if (Main.headless) {
      try {
        TtyInterface.run(this);
      } catch (Exception t) {
        t.printStackTrace();
        System.exit(-1);
      }
    }

    // kick off the progress monitor
    // (The values used for progress values are based on a single run where
    // I loaded a large file.)
    if (showSplash) {
      try {
        monitor = new SplashScreen();
        monitor.setVisible(true);
      } catch (Exception t) {
        monitor = null;
        showSplash = false;
      }
    }

    // pre-load the two basic component libraries, just so that the time
    // taken is shown separately in the progress bar.
    if (showSplash) {
      monitor.setProgress(SplashScreen.LIBRARIES);
    }
    Loader templLoader = new Loader(monitor);
    int count = templLoader.getBuiltin().getLibrary("Base").getTools()
        .size()
        + templLoader.getBuiltin().getLibrary("Gates").getTools()
        .size();
    if (count < 0) {
      // this will never happen, but the optimizer doesn't know that...
      logger.error("FATAL ERROR - no components"); // OK
      System.exit(-1);
    }

    // load in template
    loadTemplate(templLoader, templFile, templEmpty);

    // now that the splash screen is almost gone, we do some last-minute
    // interface initialization
    if (showSplash) {
      monitor.setProgress(SplashScreen.GUI_INIT);
    }
    WindowManagers.initialize();
    if (MacCompatibility.isSwingUsingScreenMenuBar()) {
      MacCompatibility
          .setFramelessJMenuBar(new LogisimMenuBar(null, null));
    } else {
      new LogisimMenuBar(null, null);
      // most of the time occupied here will be in loading menus, which
      // will occur eventually anyway; we might as well do it when the
      // monitor says we are
    }

    // if user has double-clicked a file to open, we'll
    // use that as the file to open now.
    initialized = true;

    // load file
    if (filesToOpen.isEmpty()) {
      Project proj = ProjectActions.doNew(monitor);
      proj.setStartupScreen(true);
      if (showSplash) {
        monitor.close();
      }
    } else {
      int numOpened = 0;
      boolean first = true;
      for (File fileToOpen : filesToOpen) {
        try {
          if (testVector != null) {
            Project proj = ProjectActions.doOpenNoWindow(monitor,
                fileToOpen);
            proj.doTestVector(testVector, circuitToTest);
          } else {
            ProjectActions.doOpen(monitor, fileToOpen,
                substitutions);
          }
          numOpened++;
        } catch (LoadFailedException ex) {
          logger.error("{} : {}", fileToOpen.getName(),
              ex.getMessage());
        }
        if (first) {
          first = false;
          if (showSplash) {
            monitor.close();
          }
          monitor = null;
        }
      }
      if (numOpened == 0)
        System.exit(-1);
    }

    for (File fileToPrint : filesToPrint) {
      doPrintFile(fileToPrint);
    }

    if (exitAfterStartup) {
      System.exit(0);
    }
  }
}
