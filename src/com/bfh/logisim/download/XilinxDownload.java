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

package com.bfh.logisim.download;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Rectangle;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JProgressBar;

import com.bfh.logisim.netlist.Netlist;
import com.bfh.logisim.fpga.Board;
import com.bfh.logisim.fpga.Chipset;
import com.bfh.logisim.fpga.IoStandard;
import com.bfh.logisim.fpga.PullBehavior;
import com.bfh.logisim.gui.FPGAReport;
import com.bfh.logisim.gui.PinBindings;
import com.bfh.logisim.hdlgenerator.FileWriter;
import com.bfh.logisim.hdlgenerator.TickComponentHDLGeneratorFactory;
import com.bfh.logisim.hdlgenerator.ToplevelHDLGeneratorFactory;
import com.bfh.logisim.settings.Settings;
import com.cburch.logisim.proj.Projects;

public class XilinxDownload {

  private static isCPLD(Chipset chip) {
    String part = chip.Part.toUpperCase();
    return part.startsWith("XC2C")
        || part.startsWith("XA2C")
        || part.startsWith("XCR3")
        || part.startsWith("XC9500")
        || part.startsWith("XA9500");
  }

	public static boolean Download(Settings MySettings,
			Board board, String scriptPath, String UcfPath,
			String ProjectPath, String SandboxPath, FPGAReport MyReporter) {
		boolean IsCPLD = isCPLD(board.fpga);
		String BitfileExt = (IsCPLD) ? "jed" : "bit";
		boolean BitFileExists = new File(SandboxPath
				+ ToplevelHDLGeneratorFactory.FPGAToplevelName + "."
				+ BitfileExt).exists();
		GridBagConstraints gbc = new GridBagConstraints();
		JFrame panel = new JFrame("Xilinx Downloading");
		panel.setResizable(false);
		panel.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		GridBagLayout thisLayout = new GridBagLayout();
		panel.setLayout(thisLayout);
		// PointerInfo mouseloc = MouseInfo.getPointerInfo();
		// Point mlocation = mouseloc.getLocation();
		// panel.setLocation(mlocation.x,mlocation.y);
		JLabel LocText = new JLabel(
				"Generating FPGA files and performing download; this may take a while");
		gbc.gridx = 0;
		gbc.gridy = 0;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		panel.add(LocText, gbc);
		JProgressBar progres = new JProgressBar(0,
				Settings.XilinxPrograms.length);
		progres.setValue(0);
		progres.setStringPainted(true);
		gbc.gridx = 0;
		gbc.gridy = 1;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		panel.add(progres, gbc);
		panel.pack();
		panel.setLocation(Projects.getCenteredLoc(panel.getWidth(),
				panel.getHeight() * 4));
		panel.setVisible(true);
		Rectangle labelRect = LocText.getBounds();
		labelRect.x = 0;
		labelRect.y = 0;
		LocText.paintImmediately(labelRect);
		List<String> command = new ArrayList<String>();
		if (!BitFileExists) {
			try {
				LocText.setText("Synthesizing Project");
				labelRect = LocText.getBounds();
				labelRect.x = 0;
				labelRect.y = 0;
				LocText.paintImmediately(labelRect);
				Rectangle ProgRect = progres.getBounds();
				ProgRect.x = 0;
				ProgRect.y = 0;
				progres.paintImmediately(ProgRect);
				command.clear();
				command.add(MySettings.GetXilinxToolPath() + File.separator
						+ Settings.XilinxPrograms[0]);
				command.add("-ifn");
				command.add(scriptPath.replace(ProjectPath, "../")
						+ File.separator + script_file);
				command.add("-ofn");
				command.add("logisim.log");
				ProcessBuilder Xilinx = new ProcessBuilder(command);
				Xilinx.directory(new File(SandboxPath));
				final Process CreateProject = Xilinx.start();
				InputStream is = CreateProject.getInputStream();
				InputStreamReader isr = new InputStreamReader(is);
				BufferedReader br = new BufferedReader(isr);
				String line;
				MyReporter.NewConsole("synthesize");
				while ((line = br.readLine()) != null) {
					MyReporter.print(line);
				}
				CreateProject.waitFor();
				if (CreateProject.exitValue() != 0) {
					MyReporter
							.AddFatalError("Failed to Synthesize Xilinx project; cannot download");
					panel.dispose();
					return false;
				}
			} catch (IOException e) {
				MyReporter
						.AddFatalError("Internal Error during Xilinx download");
				panel.dispose();
				return false;
			} catch (InterruptedException e) {
				MyReporter
						.AddFatalError("Internal Error during Xilinx download");
				panel.dispose();
				return false;
			}
		}
		if (!BitFileExists) {
			try {
				LocText.setText("Adding contraints");
				labelRect = LocText.getBounds();
				labelRect.x = 0;
				labelRect.y = 0;
				LocText.paintImmediately(labelRect);
				progres.setValue(1);
				Rectangle ProgRect = progres.getBounds();
				ProgRect.x = 0;
				ProgRect.y = 0;
				progres.paintImmediately(ProgRect);
				command.clear();
				command.add(MySettings.GetXilinxToolPath() + File.separator
						+ Settings.XilinxPrograms[1]);
				command.add("-intstyle");
				command.add("ise");
				command.add("-uc");
				command.add(UcfPath.replace(ProjectPath, "../")
						+ File.separator + ucf_file);
				command.add("logisim.ngc");
				command.add("logisim.ngd");
				ProcessBuilder Xilinx = new ProcessBuilder(command);
				Xilinx.directory(new File(SandboxPath));
				final Process CreateProject = Xilinx.start();
				InputStream is = CreateProject.getInputStream();
				InputStreamReader isr = new InputStreamReader(is);
				BufferedReader br = new BufferedReader(isr);
				String line;
				MyReporter.NewConsole("constrain");
				while ((line = br.readLine()) != null) {
					MyReporter.print(line);
				}
				CreateProject.waitFor();
				if (CreateProject.exitValue() != 0) {
					MyReporter
							.AddFatalError("Failed to add Xilinx constraints; cannot download");
					panel.dispose();
					return false;
				}
			} catch (IOException e) {
				MyReporter
						.AddFatalError("Internal Error during Xilinx download");
				panel.dispose();
				return false;
			} catch (InterruptedException e) {
				MyReporter
						.AddFatalError("Internal Error during Xilinx download");
				panel.dispose();
				return false;
			}
		}
		if (!BitFileExists && !IsCPLD) {
			try {
				LocText.setText("Mapping Design");
				labelRect = LocText.getBounds();
				labelRect.x = 0;
				labelRect.y = 0;
				LocText.paintImmediately(labelRect);
				progres.setValue(2);
				Rectangle ProgRect = progres.getBounds();
				ProgRect.x = 0;
				ProgRect.y = 0;
				progres.paintImmediately(ProgRect);
				command.clear();
				command.add(MySettings.GetXilinxToolPath() + File.separator
						+ Settings.XilinxPrograms[2]);
				command.add("-intstyle");
				command.add("ise");
				command.add("-o");
				command.add("logisim_map");
				command.add("logisim.ngd");
				ProcessBuilder Xilinx = new ProcessBuilder(command);
				Xilinx.directory(new File(SandboxPath));
				final Process CreateProject = Xilinx.start();
				InputStream is = CreateProject.getInputStream();
				InputStreamReader isr = new InputStreamReader(is);
				BufferedReader br = new BufferedReader(isr);
				String line;
				MyReporter.NewConsole("mapping");
				while ((line = br.readLine()) != null) {
					MyReporter.print(line);
				}
				CreateProject.waitFor();
				if (CreateProject.exitValue() != 0) {
					MyReporter
							.AddFatalError("Failed to map Xilinx design; cannot download");
					panel.dispose();
					return false;
				}
			} catch (IOException e) {
				MyReporter
						.AddFatalError("Internal Error during Xilinx download");
				panel.dispose();
				return false;
			} catch (InterruptedException e) {
				MyReporter
						.AddFatalError("Internal Error during Xilinx download");
				panel.dispose();
				return false;
			}
		}
		if (!BitFileExists) {
			try {
				LocText.setText("Place and routing Design");
				labelRect = LocText.getBounds();
				labelRect.x = 0;
				labelRect.y = 0;
				LocText.paintImmediately(labelRect);
				progres.setValue(3);
				Rectangle ProgRect = progres.getBounds();
				ProgRect.x = 0;
				ProgRect.y = 0;
				progres.paintImmediately(ProgRect);
				command.clear();
				if (!IsCPLD) {
					command.add(MySettings.GetXilinxToolPath() + File.separator
							+ Settings.XilinxPrograms[3]);
					command.add("-w");
					command.add("-intstyle");
					command.add("ise");
					command.add("-ol");
					command.add("high");
					command.add("logisim_map");
					command.add("logisim_par");
					command.add("logisim_map.pcf");
				} else {
					command.add(MySettings.GetXilinxToolPath() + File.separator
							+ Settings.XilinxPrograms[6]);
					command.add("-p");
					command.add(board.fpga.Part.toUpperCase() + "-"
							+ board.fpga.SpeedGrade + "-"
							+ board.fpga.Package.toUpperCase());
					command.add("-intstyle");
					command.add("ise");
					/* TODO: do correct termination type */
					command.add("-terminate");
          command.add(board.fpga.UnusedPinsBehavior.xilinx);
					command.add("-loc");
					command.add("on");
					command.add("-log");
					command.add("logisim_cpldfit.log");
					command.add("logisim.ngd");
				}
				ProcessBuilder Xilinx = new ProcessBuilder(command);
				Xilinx.directory(new File(SandboxPath));
				final Process CreateProject = Xilinx.start();
				InputStream is = CreateProject.getInputStream();
				InputStreamReader isr = new InputStreamReader(is);
				BufferedReader br = new BufferedReader(isr);
				String line;
				MyReporter.NewConsole("place & route");
				while ((line = br.readLine()) != null) {
					MyReporter.print(line);
				}
				CreateProject.waitFor();
				if (CreateProject.exitValue() != 0) {
					MyReporter
							.AddFatalError("Failed to P&R Xilinx design; cannot download");
					panel.dispose();
					return false;
				}
			} catch (IOException e) {
				MyReporter
						.AddFatalError("Internal Error during Xilinx download");
				panel.dispose();
				return false;
			} catch (InterruptedException e) {
				MyReporter
						.AddFatalError("Internal Error during Xilinx download");
				panel.dispose();
				return false;
			}
		}
		if (!BitFileExists) {
			try {
				LocText.setText("Generating Bitfile");
				labelRect = LocText.getBounds();
				labelRect.x = 0;
				labelRect.y = 0;
				LocText.paintImmediately(labelRect);
				progres.setValue(4);
				Rectangle ProgRect = progres.getBounds();
				ProgRect.x = 0;
				ProgRect.y = 0;
				progres.paintImmediately(ProgRect);
				command.clear();
				if (!IsCPLD) {
					command.add(MySettings.GetXilinxToolPath() + File.separator
							+ Settings.XilinxPrograms[4]);
					command.add("-w");
          PullBehavior dir = board.fpga.UnusedPinsBehavior;
					if (dir == PullBehavior.PULL_UP || dir == PullBehavior.PULL_DOWN) {
						command.add("-g");
						command.add("UnusedPin:"+dir.xilinx.toUpperCase());
					}
					command.add("-g");
					command.add("StartupClk:CCLK");
					command.add("logisim_par");
					command.add(ToplevelHDLGeneratorFactory.FPGAToplevelName
							+ ".bit");
				} else {
					command.add(MySettings.GetXilinxToolPath() + File.separator
							+ Settings.XilinxPrograms[7]);
					command.add("-i");
					command.add("logisim.vm6");
				}
				ProcessBuilder Xilinx = new ProcessBuilder(command);
				Xilinx.directory(new File(SandboxPath));
				final Process CreateProject = Xilinx.start();
				InputStream is = CreateProject.getInputStream();
				InputStreamReader isr = new InputStreamReader(is);
				BufferedReader br = new BufferedReader(isr);
				String line;
				MyReporter.NewConsole("generate");
				while ((line = br.readLine()) != null) {
					MyReporter.print(line);
				}
				CreateProject.waitFor();
				if (CreateProject.exitValue() != 0) {
					MyReporter
							.AddFatalError("Failed generate bitfile; cannot download");
					panel.dispose();
					return false;
				}
			} catch (IOException e) {
				MyReporter
						.AddFatalError("Internal Error during Xilinx download");
				panel.dispose();
				return false;
			} catch (InterruptedException e) {
				MyReporter
						.AddFatalError("Internal Error during Xilinx download");
				panel.dispose();
				return false;
			}
		}
		try {
			LocText.setText("Downloading Bitfile");
			labelRect = LocText.getBounds();
			labelRect.x = 0;
			labelRect.y = 0;
			LocText.paintImmediately(labelRect);
			progres.setValue(5);
			Rectangle ProgRect = progres.getBounds();
			ProgRect.x = 0;
			ProgRect.y = 0;
			progres.paintImmediately(ProgRect);
			Object[] options = { "Yes, download" };
			if (JOptionPane
					.showOptionDialog(
							progres,
							"Verify that your board is connected and you are ready to download.",
							"Ready to download ?", JOptionPane.YES_OPTION,
							JOptionPane.WARNING_MESSAGE, null, options,
							options[0]) == JOptionPane.CLOSED_OPTION) {
				MyReporter.AddSevereWarning("Download aborted.");
				panel.dispose();
				return false;
			}
			/* Until here update of status window */
			if (!board.fpga.USBTMCDownload) {
				command.clear();
				command.add(MySettings.GetXilinxToolPath() + File.separator
						+ Settings.XilinxPrograms[5]);
				command.add("-batch");
				command.add(scriptPath.replace(ProjectPath, "../")
						+ File.separator + download_file);
				ProcessBuilder Xilinx = new ProcessBuilder(command);
				Xilinx.directory(new File(SandboxPath));
				final Process CreateProject = Xilinx.start();
				InputStream is = CreateProject.getInputStream();
				InputStreamReader isr = new InputStreamReader(is);
				BufferedReader br = new BufferedReader(isr);
				String line;
				MyReporter.NewConsole("download");
				while ((line = br.readLine()) != null) {
					MyReporter.print(line);
				}
				CreateProject.waitFor();
				if (CreateProject.exitValue() != 0) {
					MyReporter.AddFatalError("Failed in downloading");
					panel.dispose();
					return false;
				}
				/* Until here is the standard download with programmer */
			} else {
				MyReporter.NewConsole("download");
				/* Here we do the USBTMC Download */
				boolean usbtmcdevice = new File("/dev/usbtmc0").exists();
				if (!usbtmcdevice) {
					MyReporter.AddFatalError("Could not find usbtmc device");
					panel.dispose();
					return false;
				}
				File bitfile = new File(SandboxPath
						+ ToplevelHDLGeneratorFactory.FPGAToplevelName + "."
						+ BitfileExt);
				byte[] bitfile_buffer = new byte[BUFFER_SIZE];
				int bitfile_buffer_size;
				BufferedInputStream bitfile_in = new BufferedInputStream(
						new FileInputStream(bitfile));
				File usbtmc = new File("/dev/usbtmc0");
				BufferedOutputStream usbtmc_out = new BufferedOutputStream(
						new FileOutputStream(usbtmc));
				usbtmc_out.write("FPGA ".getBytes());
				bitfile_buffer_size = bitfile_in.read(bitfile_buffer, 0,
						BUFFER_SIZE);
				while (bitfile_buffer_size > 0) {
					usbtmc_out.write(bitfile_buffer, 0, bitfile_buffer_size);
					bitfile_buffer_size = bitfile_in.read(bitfile_buffer, 0,
							BUFFER_SIZE);
				}
				usbtmc_out.close();
				bitfile_in.close();
			}
		} catch (IOException e) {
			MyReporter.AddFatalError("Internal Error during Xilinx download");
			panel.dispose();
			return false;
		} catch (InterruptedException e) {
			MyReporter.AddFatalError("Internal Error during Xilinx download");
			panel.dispose();
			return false;
		}

		panel.dispose();
		return true;
	}

	public static boolean GenerateISEScripts(FPGAReport MyReporter,
			String ProjectPath, String ScriptPath, String UcfPath,
			Netlist RootNetlist, PinBindings MapInfo,
			Board board, ArrayList<String> Entities,
			ArrayList<String> Architectures, String HDLType,
			boolean writeToFlash) {
		boolean IsCPLD = isCPLD(board.fpga);
		String JTAGPos = board.fpga.JTAGPos;
		String BitfileExt = (IsCPLD) ? "jed" : "bit";
		File ScriptFile = FileWriter.GetFilePointer(ScriptPath, script_file,
				MyReporter);
		File VhdlListFile = FileWriter.GetFilePointer(ScriptPath,
				vhdl_list_file, MyReporter);
		File UcfFile = FileWriter.GetFilePointer(UcfPath, ucf_file, MyReporter);
		File DownloadFile = FileWriter.GetFilePointer(ScriptPath,
				download_file, MyReporter);
		if (ScriptFile == null || VhdlListFile == null || UcfFile == null
				|| DownloadFile == null) {
			ScriptFile = new File(ScriptPath + script_file);
			VhdlListFile = new File(ScriptPath + vhdl_list_file);
			UcfFile = new File(UcfPath + ucf_file);
			DownloadFile = new File(ScriptPath + download_file);
			return ScriptFile.exists() && VhdlListFile.exists()
					&& UcfFile.exists() && DownloadFile.exists();
		}
		ArrayList<String> Contents = new ArrayList<String>();
		for (int i = 0; i < Entities.size(); i++) {
			Contents.add(HDLType.toUpperCase() + " work \"" + Entities.get(i)
					+ "\"");
		}
		for (int i = 0; i < Architectures.size(); i++) {
			Contents.add(HDLType.toUpperCase() + " work \""
					+ Architectures.get(i) + "\"");
		}
		if (!FileWriter.WriteContents(VhdlListFile, Contents, MyReporter))
			return false;
		Contents.clear();
		Contents.add("run -top " + ToplevelHDLGeneratorFactory.FPGAToplevelName
				+ " -ofn logisim.ngc -ofmt NGC -ifn "
				+ ScriptPath.replace(ProjectPath, "../") + vhdl_list_file
				+ " -ifmt mixed -p " + GetFPGADeviceString(board));
		if (!FileWriter.WriteContents(ScriptFile, Contents, MyReporter))
			return false;
		Contents.clear();
		Contents.add("setmode -bscan");
		if (writeToFlash && board.fpga.FlashDefined) {
			if (board.fpga.FlashName == null) {
				MyReporter.AddFatalError("Unable to find the flash on " + board.name);
			}
			String FlashPos = String.valueOf(board.fpga.FlashPos);
			String McsFile = ScriptPath + File.separator + mcs_file;
			Contents.add("setmode -pff");
			Contents.add("setSubMode -pffserial");
			Contents.add("addPromDevice -p " + JTAGPos + " -size 0 -name " + board.fpga.FlashName);
			Contents.add("addDesign -version 0 -name \"0\"");
			Contents.add("addDeviceChain -index 0");
			Contents.add("addDevice -p " + JTAGPos + " -file " + ToplevelHDLGeneratorFactory.FPGAToplevelName + "." + BitfileExt);
			Contents.add("generate -format mcs -fillvalue FF -output " + McsFile);
			Contents.add("setMode -bs");
			Contents.add("setCable -port auto");
			Contents.add("identify");
			Contents.add("assignFile -p " + FlashPos + " -file " + McsFile);
			Contents.add("program -p " + FlashPos + " -e -v");
		} else {
			Contents.add("setcable -p auto");
			Contents.add("identify");
			if (!IsCPLD) {
				Contents.add("assignFile -p " + JTAGPos + " -file "
						+ ToplevelHDLGeneratorFactory.FPGAToplevelName + "."
						+ BitfileExt);
				Contents.add("program -p " + JTAGPos + " -onlyFpga");
			} else {
				Contents.add("assignFile -p " + JTAGPos + " -file logisim."
						+ BitfileExt);
				Contents.add("program -p " + JTAGPos + " -e");
			}
		}
		Contents.add("quit");
		if (!FileWriter.WriteContents(DownloadFile, Contents, MyReporter))
			return false;
		Contents.clear();
		if (RootNetlist.NumberOfClockTrees() > 0) {
			Contents.add("NET \"" + TickComponentHDLGeneratorFactory.FPGAClock
					+ "\" " + GetXilinxClockPin(board) + " ;");
			Contents.add("NET \"" + TickComponentHDLGeneratorFactory.FPGAClock
					+ "\" TNM_NET = \""
					+ TickComponentHDLGeneratorFactory.FPGAClock + "\" ;");
			Contents.add("TIMESPEC \"TS_"
					+ TickComponentHDLGeneratorFactory.FPGAClock
					+ "\" = PERIOD \""
					+ TickComponentHDLGeneratorFactory.FPGAClock + "\" "
					+ board.fpga.Speed + " HIGH 50 % ;");
			Contents.add("");
		}
		Contents.addAll(MapInfo.GetFPGAPinLocs(Chipset.XILINX));
		return FileWriter.WriteContents(UcfFile, Contents, MyReporter);
	}

	private static String GetFPGADeviceString(Chipset chip) {
    return String.format("%s-%s-%s", chip.Part, chip.Package, chip.SpeedGrade);
	}

	private static String GetXilinxClockPin(Board CurrentBoard) {
		StringBuffer result = new StringBuffer();
		result.append("LOC = \"" + CurrentBoard.fpga.ClockPinLocation + "\"");
    PullBehavior dir = CurrentBoard.fpga.ClockPullBehavior;
		if (dir == PullBehavior.PULL_UP || dir == PullBehavior.PULL_DOWN)
			result.append(" | " + dir.xilinx.toUpperCase());
    IoStandard std = CurrentBoard.fpga.ClockStandard;
		if (std != IoStandard.DEFAULT && std != IoStandard.UNKNOWN)
			result.append(" | IOSTANDARD = " + std);
		return result.toString();
	}

	private final static String vhdl_list_file = "XilinxVHDLList.prj";

	private final static String script_file = "XilinxScript.cmd";

	private final static String ucf_file = "XilinxConstraints.ucf";

	private final static String download_file = "XilinxDownload";

	private final static String mcs_file = "XilinxProm.mcs";

	private final static Integer BUFFER_SIZE = 16 * 1024;

}
