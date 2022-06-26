package com.satergo.controller;

import com.satergo.Load;
import com.satergo.Main;
import com.satergo.ProgramData;
import com.satergo.Utils;
import com.satergo.ergo.EmbeddedFullNode;
import com.satergo.ergo.EmbeddedNodeInfo;
import com.satergo.extra.DownloadTask;
import com.satergo.extra.SimpleTask;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.stage.DirectoryChooser;
import org.ergoplatform.appkit.NetworkType;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.util.ResourceBundle;

public class FullNodeDownloaderCtrl implements SetupPage.WithoutLanguage, Initializable {
	private Utils.NodeVersion version;

	@FXML private Parent root;
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
		try {
			File file = new File(nodeDirectory, version.fileName());
			if (!file.exists()) {
				DownloadTask downloadTask = new DownloadTask(
						HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build(),
						Utils.httpRequestBuilder().uri(version.uri()).build(),
						new FileOutputStream(new File(nodeDirectory, version.fileName()))
				);
				progressBar.progressProperty().bind(downloadTask.progressProperty());
				downloadTask.setOnSucceeded(se -> {
					nodeJar = file;
					continueSetup.setDisable(false);
				});
				download.setDisable(true);
				new Thread(downloadTask).start();
			} else {
				nodeJar = file;
				download.setDisable(true);
				continueSetup.setDisable(false);
			}
		} catch (IOException ex) {
			throw new RuntimeException(ex);
		}
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
		Main.programData().nodeAddress.set(Main.node.localApiHttpAddress());
		Main.node.firstTimeSetup();
		Main.get().displaySetupPage(Load.<WalletSetupCtrl>fxmlController("/wallet-setup.fxml"));
	}

	@Override
	public void initialize(URL location, ResourceBundle resources) {
		networkType.getItems().addAll(NetworkType.values());
		networkType.setValue(NetworkType.MAINNET);
		networkType.valueProperty().bindBidirectional(Main.programData().nodeNetworkType);
		new SimpleTask<>(Utils::fetchLatestNodeVersion)
				.onSuccess(v -> {
					version = v;
					nodeVersion.setText(v.version());
					download.setDisable(false);
				}).newThread();
		if (new File("node").exists()) {
			nodeDirectory = null;
			customFolderLocation.setText(Main.lang("currentNone"));
		} else nodeDirectory = new File("node");
	}

	@Override
	public Parent content() {
		return root;
	}
}
