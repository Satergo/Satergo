package com.satergo.ergo;

import com.satergo.Utils;
import com.pty4j.PtyProcess;
import com.pty4j.PtyProcessBuilder;
import javafx.application.Platform;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import org.ergoplatform.appkit.NetworkType;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

public class EmbeddedFullNode {

	public enum LogLevel { ERROR, WARN, INFO, DEBUG, TRACE, OFF }

	public final File nodeDirectory;
	private PtyProcess process;

	private final NetworkType networkType;
	public LogLevel logLevel;
	public final File nodeJar;
	private final File confFile;
	public final File infoFile;
	public EmbeddedNodeInfo info;

	private EmbeddedFullNode(File nodeDirectory, NetworkType networkType, LogLevel logLevel, File nodeJar, File confFile, EmbeddedNodeInfo info) {
		this.nodeDirectory = nodeDirectory;
		this.networkType = networkType;
		this.logLevel = logLevel;
		this.nodeJar = nodeJar;
		this.confFile = confFile;
		infoFile = new File(nodeDirectory, EmbeddedNodeInfo.FILE_NAME);
		this.info = info;

		nodeSyncProgress.bind(nodeBlockHeight.divide(networkBlockHeight));
		nodeBlocksLeft.bind(networkBlockHeight.subtract(nodeBlockHeight));
	}

	public static EmbeddedFullNode fromLocalNodeInfo(File infoFile) {
		try {
			File root = infoFile.getParentFile();
			EmbeddedNodeInfo embeddedNodeInfo = EmbeddedNodeInfo.fromJson(Files.readString(infoFile.toPath()));
			return new EmbeddedFullNode(root, embeddedNodeInfo.networkType(), embeddedNodeInfo.logLevel(), new File(root, embeddedNodeInfo.jarFileName()), new File(root, embeddedNodeInfo.confFileName()), embeddedNodeInfo);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public final SimpleIntegerProperty nodeBlockHeight = new SimpleIntegerProperty(0);
	public final SimpleIntegerProperty networkBlockHeight = new SimpleIntegerProperty(1);
	public final SimpleDoubleProperty nodeSyncProgress = new SimpleDoubleProperty(0);
	public final SimpleIntegerProperty nodeBlocksLeft = new SimpleIntegerProperty(1);

	private Timer pollerTimer;
	private TimerTask pollerTask;

	public int port() {
		return networkType == NetworkType.MAINNET ? 9053 : 9052;
	}

	public String localHttpAddress() {
		return "http://127.0.0.1:" + port();
	}

//	private static String readVersion(File file) throws IOException {
//		JarFile jar = new JarFile(file);
//		// doesn't use application.conf (4.0.10) because once that was outdated but mainnet.conf (4.0.13) was not
//		String mainnetConf = new String(new DataInputStream(jar.getInputStream(jar.getEntry("mainnet.conf"))).readAllBytes(), StandardCharsets.UTF_8);
//		Pattern pattern = Pattern.compile("nodeName\\s+=\\s+\"ergo-mainnet-([\\d.]+)\"");
//		Matcher m = pattern.matcher(mainnetConf);
//		if (!m.find()) throw new IllegalArgumentException();
//		return m.group(1);
//	}

//	public String readVersion() {
//		try {
//			return readVersion(nodeJar);
//		} catch (IOException e) {
//			throw new RuntimeException(e);
//		}
//	}

	public void firstTimeSetup() {
		try {
			// create .conf file
			Files.writeString(nodeDirectory.toPath().resolve("ergo.conf"), Utils.resourceStringUTF8("/conf-template.conf"));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private static Path findJavaBinary() {
		Path javaInstallation = Path.of(System.getProperty("java.home"));
		Path binDirectory = javaInstallation.resolve("bin");
		if (Files.exists(binDirectory.resolve("java.exe"))) return binDirectory.resolve("java.exe");
		return binDirectory.resolve("java");
	}

	private void startHeightPolling() {
		if (pollerTimer != null) {
			pollerTimer.cancel();
		}
		pollerTimer = new Timer();
		pollerTask = new TimerTask() {
			@Override
			public void run() {
				try {
					int nodeHeight = ErgoInterface.getNodeBlockHeight(localHttpAddress());
					int networkHeight = ErgoInterface.getNetworkBlockHeight(networkType);
					// not sure if needed
					Platform.runLater(() -> {
						nodeBlockHeight.set(nodeHeight);
						networkBlockHeight.set(networkHeight);
					});
				} catch (Exception ignored) {} // todo
			}
		};
		pollerTimer.scheduleAtFixedRate(pollerTask, 10000, 20000);
	}

	public void start() {
		try {
			if (isRunning()) throw new IllegalStateException("this node is already running");
			String[] command = new String[7];
			command[0] = findJavaBinary().toString();
			command[1] = "-jar";
			command[2] = "-Dlogback.stdout.level=" + logLevel;
			command[3] = nodeJar.getAbsolutePath();
			command[4] = "--" + networkType.toString().toLowerCase(Locale.ROOT);
			command[5] = "-c";
			command[6] = confFile.getName();
			System.out.println("running node with command: " + Arrays.toString(command));
			process = new PtyProcessBuilder().setCommand(command).setDirectory(nodeDirectory.getAbsolutePath()).start();
			startHeightPolling();
		} catch (IOException ex) {
			throw new RuntimeException(ex);
		}
	}

	public InputStream getStandardOutput() {
		return process.getInputStream();
	}

	public boolean isRunning() {
		return process != null && process.isAlive();
	}

	public void stop() {
		process.destroy();
		pollerTask.cancel();
		pollerTimer.cancel();
	}
	
	public void waitForExit() {
		process.onExit().join();
	}
}
