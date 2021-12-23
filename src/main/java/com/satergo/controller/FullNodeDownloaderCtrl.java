package com.satergo.controller;

import com.satergo.*;
import com.satergo.ergo.EmbeddedFullNode;
import com.satergo.ergo.EmbeddedNodeInfo;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.stage.DirectoryChooser;
import org.ergoplatform.appkit.NetworkType;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.util.ResourceBundle;

public class FullNodeDownloaderCtrl implements Initializable {
	private Utils.NodeVersion version;

	@FXML private ProgressBar progressBar;
	@FXML private Label nodeVersion;
	@FXML private Button download, continueSetup;
	@FXML private Label customFolderLocation;
	@FXML private ComboBox<NetworkType> networkType;

	private File nodeDirectory;
	private File nodeJar;

	@FXML
	public void selectCustomFolder(ActionEvent e) {
		DirectoryChooser directoryChooser = new DirectoryChooser();
		directoryChooser.setInitialDirectory(new File(System.getProperty("user.dir")));
		directoryChooser.setTitle(Main.lang("nodeDirectory"));
		File nodeDirectory = directoryChooser.showDialog(Main.get().stage());
		if (nodeDirectory == null) return;
		File[] fileList = nodeDirectory.listFiles();
		if (fileList != null && fileList.length != 0) {
			Utils.alert(Alert.AlertType.ERROR, Main.lang("directoryNotEmpty"));
			return;
		}
		this.nodeDirectory = nodeDirectory;
		customFolderLocation.setText(Main.lang("current_s").formatted(nodeDirectory.getAbsolutePath()));
	}

	@FXML
	public void download(ActionEvent e) {
		if (nodeDirectory == null) {
			Utils.alert(Alert.AlertType.ERROR, Main.lang("noDirectorySelected"));
			return;
		}
		if (nodeDirectory.equals(new File("node")) && !nodeDirectory.exists())
			nodeDirectory.mkdir();
		new Thread(() -> {
			try {
				HttpClient httpClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build();
				HttpResponse<InputStream> dl = httpClient.send(Utils.httpRequestBuilder().uri(version.uri()).build(), HttpResponse.BodyHandlers.ofInputStream());
				long totalBytes = Long.parseLong(dl.headers().firstValue("Content-Length").orElseThrow());
				File file = new File(nodeDirectory, version.fileName());
				if (!file.exists()) {
					FileOutputStream outputStream = new FileOutputStream(file);
					Utils.transferWithMeter(dl.body(), outputStream, bytes -> {
						double progress = (double) bytes / (double) totalBytes;
						Platform.runLater(() -> progressBar.setProgress(progress));
					});
				}
				nodeJar = file;
				Platform.runLater(() -> {
					download.setDisable(true);
					continueSetup.setDisable(false);
				});
			} catch (IOException | InterruptedException ex) {
				throw new RuntimeException(ex);
			}
		}).start();
	}

	@FXML
	public void continueSetup(ActionEvent e) {
		if (Main.node != null && Main.node.isRunning()) return;
		Main.programData().blockchainNodeKind.set(ProgramData.BlockchainNodeKind.EMBEDDED_FULL_NODE);
		EmbeddedNodeInfo info = new EmbeddedNodeInfo(networkType.getValue(), nodeJar.getName(), EmbeddedFullNode.LogLevel.WARN, "ergo.conf");
		Main.programData().embeddedNodeInfo.set(nodeDirectory.toPath().resolve(EmbeddedNodeInfo.FILE_NAME));
		try {
			Files.writeString(Main.programData().embeddedNodeInfo.get(), info.toJson());
		} catch (IOException ex) {
			throw new RuntimeException(ex);
		}
		Main.node = Main.get().nodeFromInfo();
		Main.programData().nodeAddress.set(Main.node.localHttpAddress());
		Main.node.firstTimeSetup();
		Main.get().displayPage(Load.fxml("/wallet-setup.fxml"));
	}

	@Override
	public void initialize(URL location, ResourceBundle resources) {
		networkType.getItems().addAll(NetworkType.values());
		networkType.setValue(NetworkType.MAINNET);
		networkType.valueProperty().bindBidirectional(Main.programData().nodeNetworkType);
		version = Utils.fetchLatestNode();
		nodeVersion.setText(version.version());
		if (new File("node").exists()) {
			nodeDirectory = null;
			customFolderLocation.setText(Main.lang("currentNone"));
		} else nodeDirectory = new File("node");
	}
}
