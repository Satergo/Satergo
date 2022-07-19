package com.satergo.ergo;

import com.grack.nanojson.JsonObject;
import com.pty4j.PtyProcess;
import com.pty4j.PtyProcessBuilder;
import com.satergo.Main;
import com.satergo.Utils;
import com.satergo.controller.NodeOverviewCtrl;
import javafx.application.Platform;
import javafx.beans.binding.BooleanExpression;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import org.ergoplatform.appkit.NetworkType;

import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EmbeddedFullNode {

	public enum LogLevel { ERROR, WARN, INFO, DEBUG, TRACE, OFF }

	public final File nodeDirectory;
	private PtyProcess process;
	private long startedTime;

	private final NetworkType networkType;
	public LogLevel logLevel;
	public final File nodeJar;
	public final File confFile;
	public final File infoFile;
	public EmbeddedNodeInfo info;
	public final ErgoNodeAccess nodeAccess;

	private EmbeddedFullNode(File nodeDirectory, NetworkType networkType, LogLevel logLevel, File nodeJar, File confFile, EmbeddedNodeInfo info) {
		this.nodeDirectory = nodeDirectory;
		this.networkType = networkType;
		this.logLevel = logLevel;
		this.nodeJar = nodeJar;
		this.confFile = confFile;
		infoFile = new File(nodeDirectory, EmbeddedNodeInfo.FILE_NAME);
		this.info = info;
		this.nodeAccess = new ErgoNodeAccess(URI.create(localApiHttpAddress()));

//		nodeSyncProgress.bind(nodeBlockHeight.divide(networkBlockHeight));
		// This just doesn't work. It stays as 0 even though header and network heights change.
		// It is probably not an integer division issue because nodeSyncProgress works...
		// Well, that one no longer works either.
//		nodeHeaderSyncProgress.bind(nodeHeaderHeight.divide(networkBlockHeight));
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

	public final SimpleIntegerProperty nodeHeaderHeight = new SimpleIntegerProperty(-1);
	public final SimpleIntegerProperty nodeBlockHeight = new SimpleIntegerProperty(-1);
	public final SimpleIntegerProperty networkBlockHeight = new SimpleIntegerProperty(-2);
	public final SimpleIntegerProperty peerCount = new SimpleIntegerProperty(0);
	public final SimpleDoubleProperty nodeHeaderSyncProgress = new SimpleDoubleProperty(0);
	public final SimpleDoubleProperty nodeSyncProgress = new SimpleDoubleProperty(0);
	public final SimpleIntegerProperty nodeBlocksLeft = new SimpleIntegerProperty(1);

	public final BooleanExpression headersSynced = nodeHeaderHeight.isEqualTo(networkBlockHeight);

	private ScheduledExecutorService scheduler;

	private int[] lastVersionUpdateAlert = null;

	public int apiPort() {
		return networkType == NetworkType.MAINNET ? 9053 : 9052;
	}

	public String localApiHttpAddress() {
		return "http://127.0.0.1:" + apiPort();
	}

	private static String readVersion(File file) throws IOException {
		try (JarFile jar = new JarFile(file)) {
			// doesn't use application.conf (4.0.10) because once that was outdated but mainnet.conf (4.0.13) was not
			String mainnetConf = new String(new DataInputStream(jar.getInputStream(jar.getEntry("mainnet.conf"))).readAllBytes(), StandardCharsets.UTF_8);
			Pattern pattern = Pattern.compile("nodeName\\s+=\\s+\"ergo-mainnet-([\\d.]+)\"");
			Matcher m = pattern.matcher(mainnetConf);
			if (!m.find()) throw new IllegalArgumentException();
			return m.group(1);
		}
	}

	public String readVersion() {
		// Unfortunately many ergo node releases don't update the version in the relevant files, so we have to use the filename...
		Matcher matcher = Pattern.compile(".*?(\\d\\.+\\d+\\.\\d+(?:\\.\\d+)?).*?").matcher(nodeJar.getName());
		// Fallback to the probably wrong version in the conf file
		if (!matcher.matches()) {
			try {
				return readVersion(nodeJar);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
		return matcher.group(1);
	}

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
		if (Files.exists(binDirectory.resolve("java.exe")))
			return binDirectory.resolve("java.exe");
		return binDirectory.resolve("java");
	}

	private void scheduleRepeatingTasks() {
		scheduler = Executors.newScheduledThreadPool(0);
		scheduler.scheduleAtFixedRate(() -> {
			ErgoNodeAccess.Status status = nodeAccess.getStatus();
			int networkHeight = status.networkHeight() == 0
					? ErgoInterface.getNetworkBlockHeight(networkType)
					: status.networkHeight();
			Platform.runLater(() -> {
				nodeHeaderHeight.set(status.headerHeight());
				nodeBlockHeight.set(status.blockHeight());
				networkBlockHeight.set(networkHeight);
				peerCount.set(status.peerCount());
				nodeSyncProgress.set((double) status.blockHeight() / (double) networkHeight);
				nodeHeaderSyncProgress.set((double) status.headerHeight() / (double) networkHeight);
			});
		}, 10, 2, TimeUnit.SECONDS);
		int[] version = Arrays.stream(readVersion().split("\\.")).mapToInt(Integer::parseInt).toArray();
		scheduler.scheduleAtFixedRate(() -> {
			JsonObject latestNodeData = Utils.fetchLatestNodeData();
			String latestVersionString = latestNodeData.getString("tag_name").substring(1);
			int[] latestVersion = Arrays.stream(latestVersionString.split("\\.")).mapToInt(Integer::parseInt).toArray();

			if ((lastVersionUpdateAlert == null || !Arrays.equals(lastVersionUpdateAlert, latestVersion))
					&& Utils.compareVersion(version, latestVersion) < 0) {
				Platform.runLater(() -> {
					Alert alert = new Alert(Alert.AlertType.NONE);
					alert.initOwner(Main.get().stage());
					alert.setTitle(Main.lang("programName"));
					alert.setHeaderText(Main.lang("aNewErgoNodeVersionHasBeenFound"));
					alert.setContentText(Main.lang("nodeUpdateDescription").formatted(
							latestVersionString,
							LocalDateTime.parse(latestNodeData.getString("published_at"), DateTimeFormatter.ISO_DATE_TIME).format(DateTimeFormatter.ofPattern("MMM dd, yyyy")),
							latestNodeData.getString("body")));
					ButtonType update = new ButtonType(Main.lang("update"), ButtonBar.ButtonData.YES);
					ButtonType notNow = new ButtonType(Main.lang("notNow"), ButtonBar.ButtonData.CANCEL_CLOSE);
					alert.getButtonTypes().addAll(update, notNow);
					lastVersionUpdateAlert = latestVersion;
					alert.showAndWait().ifPresent(t -> {
						if (t == update) {
							Alert updatingAlert = Utils.alert(Alert.AlertType.NONE, Main.lang("updatingErgoNode..."));
							new Thread(() -> {
								downloadUpdate(nodeDirectory.toPath(), latestVersionString, URI.create(latestNodeData.getArray("assets").getObject(0).getString("browser_download_url")));
								try {
									Files.delete(nodeJar.toPath());
								} catch (IOException e) {
									throw new RuntimeException(e);
								}
								Platform.runLater(() -> {
									updatingAlert.getDialogPane().getButtonTypes().add(ButtonType.OK); // a window cannot be closed unless it has a button
									updatingAlert.close();
									try {
										((NodeOverviewCtrl) Main.get().getWalletPage().getTab("node")).logVersionUpdate(latestVersionString);
									} catch (Exception ignored) {
									} // could happen if user somehow logs out while it is updating, so wallet page becomes null
									stop();
									waitForExit();
									Main.node = Main.get().nodeFromInfo();
									Main.node.start();
									try {
										((NodeOverviewCtrl) Main.get().getWalletPage().getTab("node")).transferLog();
									} catch (Exception ignored) {
									}
									Utils.alert(Alert.AlertType.INFORMATION, Main.lang("updatedErgoNode"));
								});
							}).start();
						}
					});
				});
			}
		}, 5, Duration.ofHours(4).toSeconds(), TimeUnit.SECONDS);
	}

	/**
	 * downloads the jar and updates the node info file
	 */
	private static void downloadUpdate(Path dir, String version, URI uri) {
		HttpClient httpClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build();
		HttpRequest request = Utils.httpRequestBuilder().uri(uri).build();
		String jarName = "ergo-" + version + ".jar";
		Path jar = dir.resolve(jarName);
		try {
			httpClient.send(request, HttpResponse.BodyHandlers.ofFile(jar));
			EmbeddedNodeInfo newInfo = EmbeddedNodeInfo.fromJson(Files.readString(dir.resolve(EmbeddedNodeInfo.FILE_NAME)))
					.withJarFileName(jarName);
			Files.writeString(dir.resolve(EmbeddedNodeInfo.FILE_NAME), newInfo.toJson());
		} catch (IOException | InterruptedException e) {
			throw new RuntimeException(e);
		}
	}

	public void start() {
		if (isRunning()) throw new IllegalStateException("this node is already running");
		try {
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
			scheduleRepeatingTasks();
			startedTime = System.currentTimeMillis();
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
		scheduler.shutdown();
	}
	
	public void waitForExit() {
		process.onExit().join();
	}

	public long getStartedTime() {
		return startedTime;
	}
}
