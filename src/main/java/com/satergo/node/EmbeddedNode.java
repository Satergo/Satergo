package com.satergo.node;

import com.grack.nanojson.JsonObject;
import com.satergo.Main;
import com.satergo.Utils;
import com.satergo.controller.NodeOverviewCtrl;
import com.satergo.ergo.ErgoInterface;
import com.satergo.extra.DownloadTask;
import com.satergo.extra.dialog.SatVoidDialog;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigRenderOptions;
import com.typesafe.config.ConfigValueFactory;
import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import org.ergoplatform.appkit.NetworkType;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarFile;

public class EmbeddedNode {

	public enum LogLevel { ERROR, WARN, INFO, DEBUG, TRACE, OFF }

	public static final LogLevel DEFAULT_LOG_LEVEL = LogLevel.ERROR;

	public final File nodeDirectory;
	private Process process;
	private long startedTime;

	public final File nodeJar;
	public final File confFile;
	public final File infoFile;
	public EmbeddedNodeInfo info;
	public final ErgoNodeAccess nodeAccess;

	private EmbeddedNode(File nodeDirectory, File infoFile, EmbeddedNodeInfo info) {
		this.nodeDirectory = nodeDirectory;
		this.nodeJar = new File(nodeDirectory, info.jarFileName());
		this.confFile = new File(nodeDirectory, info.confFileName());
		this.infoFile = infoFile;
		this.info = info;
		this.nodeAccess = new ErgoNodeAccess(URI.create(localApiHttpAddress()));

//		nodeSyncProgress.bind(nodeBlockHeight.divide(networkBlockHeight));
		// This just doesn't work. It stays as 0 even though header and network heights change.
		// It is probably not an integer division issue because nodeSyncProgress works...
		// Well, that one no longer works either.
//		nodeHeaderSyncProgress.bind(nodeHeaderHeight.divide(networkBlockHeight));
		nodeBlocksLeft.bind(networkBlockHeight.subtract(nodeBlockHeight));
	}

	public LogLevel logLevel() {
		return info.logLevel();
	}

	public static EmbeddedNode fromLocalNodeInfo(File infoFile) {
		try {
			File root = infoFile.getParentFile();
			return new EmbeddedNode(root, infoFile, EmbeddedNodeInfo.fromJson(Files.readString(infoFile.toPath())));
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

	public final SimpleBooleanProperty headersSynced = new SimpleBooleanProperty(false);

	private ScheduledExecutorService scheduler;

	public int apiPort() {
		return info.networkType() == NetworkType.MAINNET ? 9053 : 9052;
	}

	public String localApiHttpAddress() {
		return "http://127.0.0.1:" + apiPort();
	}

	public String readVersion() {
		try (JarFile jar = new JarFile(nodeJar)) {
			Config nodeAppConf = ConfigFactory.parseString(new String(jar.getInputStream(jar.getEntry("application.conf")).readAllBytes(), StandardCharsets.UTF_8));
			return nodeAppConf.getString("scorex.network.appVersion");
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public void firstTimeSetup(boolean nipopow, boolean utxoSetSnapshot) {
		try {
			// create .conf file
			Files.writeString(nodeDirectory.toPath().resolve("ergo.conf"), Utils.makeNodeConfig(nipopow, utxoSetSnapshot));
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
					? ErgoInterface.getNetworkBlockHeight(info.networkType())
					: status.networkHeight();
			Platform.runLater(() -> {
				nodeHeaderHeight.set(status.headerHeight());
				nodeBlockHeight.set(status.blockHeight());
				networkBlockHeight.set(networkHeight);
				peerCount.set(status.peerCount());
				nodeSyncProgress.set((double) status.blockHeight() / (double) networkHeight);
				nodeHeaderSyncProgress.set((double) status.headerHeight() / (double) networkHeight);
				headersSynced.set(Math.abs(status.networkHeight() - status.headerHeight()) <= 5);
			});
		}, 10, 2, TimeUnit.SECONDS);
		scheduler.scheduleAtFixedRate(this::checkForUpdate, 5, Duration.ofHours(4).toSeconds(), TimeUnit.SECONDS);
	}

	private int[] lastVersionUpdateAlert = null;

	public void checkForUpdate() {
		int[] version = Arrays.stream(readVersion().split("\\.")).mapToInt(Integer::parseInt).toArray();
		JsonObject latestNodeData = Utils.fetchLatestNodeData();
		String latestVersionString = latestNodeData.getString("tag_name").substring(1);
		int[] latestVersion = Arrays.stream(latestVersionString.split("\\.")).mapToInt(Integer::parseInt).toArray();

		if ((lastVersionUpdateAlert == null || !Arrays.equals(lastVersionUpdateAlert, latestVersion))
				&& Utils.compareVersion(version, latestVersion) < 0) {
			Platform.runLater(() -> {
				if (!info.autoUpdate()) {
					Alert alert = new Alert(Alert.AlertType.NONE);
					alert.initOwner(Main.get().stage());
					alert.setTitle(Main.lang("programName"));
					alert.setHeaderText(Main.lang("aNewErgoNodeVersionHasBeenFound"));
					alert.setContentText(Main.lang("nodeUpdateDescription").formatted(
							latestVersionString,
							LocalDateTime.parse(latestNodeData.getString("published_at"), DateTimeFormatter.ISO_DATE_TIME).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)),
							latestNodeData.getString("body")));
					ButtonType update = new ButtonType(Main.lang("update"), ButtonBar.ButtonData.YES);
					ButtonType notNow = new ButtonType(Main.lang("notNow"), ButtonBar.ButtonData.CANCEL_CLOSE);
					alert.getButtonTypes().addAll(update, notNow);
					lastVersionUpdateAlert = latestVersion;
					ButtonType result = alert.showAndWait().orElse(null);
					if (result != update) {
						return;
					}
				}
				ProgressBar progress = new ProgressBar();
				SatVoidDialog updatingAlert = Utils.alert(Alert.AlertType.NONE, new VBox(
						4,
						new Label(Main.lang("updatingErgoNode...")),
						progress
				));
				DownloadTask task = createDownloadTask(nodeDirectory, latestVersionString, URI.create(latestNodeData.getArray("assets").getObject(0).getString("browser_download_url")));
				progress.progressProperty().bind(task.progressProperty());
				task.setOnSucceeded(e -> {
					updatingAlert.close();
					// could be null if user somehow logs out while it is updating, so wallet page becomes null
					NodeOverviewCtrl nodeTab = Main.get().getWalletPage() == null ? null : Main.get().getWalletPage().getTab("node");
					if (nodeTab != null)
						nodeTab.logVersionUpdate(latestVersionString);
					stop();
					waitForExit();
					try {
						Files.delete(nodeJar.toPath());
					} catch (IOException ex) {
						throw new RuntimeException(ex);
					}
					Main.node = Main.get().nodeFromInfo();
					Main.node.start();
					if (nodeTab != null) {
						nodeTab.bindToProperties();
						nodeTab.transferLog();
						Main.get().getWalletPage().bindToNodeProperties();
					}
					Utils.alert(Alert.AlertType.INFORMATION, Main.lang("updatedErgoNode"));
				});
				task.setOnFailed(e -> Utils.alertUnexpectedException(task.getException()));
				new Thread(task).start();

			});
		}
	}

	/**
	 * downloads the jar and updates the node info file
	 */
	private static DownloadTask createDownloadTask(File dir, String version, URI uri) {
		try {
			String jarName = "ergo-" + version + ".jar";
			return new DownloadTask(
					HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build(),
					Utils.httpRequestBuilder().uri(uri).build(),
					new FileOutputStream(new File(dir, jarName))
			) {
				@Override
				protected Void call() throws Exception {
					super.call();
					Path infoPath = dir.toPath().resolve(EmbeddedNodeInfo.FILE_NAME);
					EmbeddedNodeInfo newInfo = EmbeddedNodeInfo.fromJson(Files.readString(infoPath))
							.withJarFileName(jarName);
					Files.writeString(infoPath, newInfo.toJson());
					return null;
				}
			};
		} catch (FileNotFoundException e) {
			throw new RuntimeException(e);
		}
	}

	public void start() {
		if (isRunning()) throw new IllegalStateException("this node is already running");
		try {
			Optional<Object> prop = getConfValue("scorex.logging.level");
			if (prop.isEmpty() || !prop.get().equals(logLevel().toString())) {
				setConfValue("scorex.logging.level", logLevel().toString());
			}
			String[] command = new String[6 + info.vmArguments().size()];
			command[0] = findJavaBinary().toString();
			int i = 1;
			for (; (i - 1) < info.vmArguments().size(); i++) {
				command[i] = info.vmArguments().get(i - 1);
			}
			command[i++] = "-jar";
			command[i++] = nodeJar.getAbsolutePath();
			command[i++] = "--" + info.networkType().toString().toLowerCase(Locale.ROOT);
			command[i++] = "-c";
			command[i] = confFile.getName();
			System.out.println("running node with command: " + Arrays.toString(command));
			process = new ProcessBuilder().command(command).directory(nodeDirectory).start();
			scheduleRepeatingTasks();
			startedTime = System.currentTimeMillis();
		} catch (IOException ex) {
			throw new RuntimeException(ex);
		}
	}

	public boolean isConfigLightNode() {
		Optional<Object> utxoBootstrap = getConfValue("ergo.node.utxo.utxoBootstrap");
		Optional<Object> nipopowBootstrap = getConfValue("ergo.node.nipopow.nipopowBootstrap");
		return (utxoBootstrap.isPresent() && (boolean) utxoBootstrap.get()) || (nipopowBootstrap.isPresent() && (boolean) nipopowBootstrap.get());
	}

	public Optional<Object> getConfValue(String propertyPath) {
		Config config = ConfigFactory.parseFile(confFile);
		if (!config.hasPath(propertyPath))
			return Optional.empty();
		return Optional.of(config.getValue(propertyPath).unwrapped());
	}

	public void setConfValue(String propertyPath, Object value) throws IOException {
		Files.writeString(confFile.toPath(), ConfigFactory.parseFile(confFile)
				.withValue(propertyPath, ConfigValueFactory.fromAnyRef(value))
				.root().render(ConfigRenderOptions.defaults()
						.setOriginComments(false)
						.setJson(false)));
	}

	public void writeInfo() throws IOException {
		Files.writeString(infoFile.toPath(), info.toJson());
	}

	public InputStream standardOutput() {
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

	public long startedTime() {
		return startedTime;
	}
}
