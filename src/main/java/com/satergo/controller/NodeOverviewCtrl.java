package com.satergo.controller;

import com.satergo.Main;
import com.satergo.ergo.EmbeddedFullNode;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ResourceBundle;

public class NodeOverviewCtrl implements Initializable, WalletTab {
	private static final int LOG_LENGTH_LIMIT = 1_000_000;

	@FXML private Label networkType;
	@FXML private ProgressBar progress;
	@FXML private Label blocksNodeNetwork;
	@FXML private ComboBox<EmbeddedFullNode.LogLevel> logLevel;
	@FXML private TextArea log;
	@FXML private Label logLevelNote;
	@FXML private CheckBox pauseLog;

	private void transferLog() {
		new Thread(() -> {
			try {
				InputStream inputStream = Main.node.getStandardOutput();
				byte[] buffer = new byte[8192];
				int read;
				while ((read = inputStream.read(buffer, 0, 8192)) >= 0) {
					String s = new String(buffer, 0, read, StandardCharsets.UTF_8);
					Platform.runLater(() -> appendText(s));
					Thread.sleep(10);
				}
			} catch (IOException | InterruptedException e) {
				if (!e.getMessage().contains("Stream closed"))
					e.printStackTrace();
				else System.out.println("[info] Node log stream closed");
			}
		}, "Node log transfer").start();
	}

	@FXML
	public void restart(ActionEvent e) {
		Main.node.logLevel = logLevel.getValue();
		Main.node.stop();
		Main.node.waitForExit();
		appendText("\n-------- " + Main.lang("nodeWasRestartedLog") + " --------\n\n");
		Main.node.start();
		logLevelNote.setVisible(false);
		transferLog();
	}

	@FXML
	public void clearLog(ActionEvent e) {
		log.setText("");
	}

	@Override
	public void initialize(URL location, ResourceBundle resources) {
		transferLog();
		networkType.textProperty().bind(Main.programData().nodeNetworkType.asString());
		logLevel.setValue(Main.node.logLevel);
		logLevel.getItems().addAll(EmbeddedFullNode.LogLevel.values());
		logLevel.valueProperty().addListener((observable, oldValue, newValue) -> {
			try {
				Main.node.info = Main.node.info.withLogLevel(newValue);
				Files.writeString(Main.node.infoFile.toPath(), Main.node.info.toJson());
			} catch (IOException ex) {
				throw new RuntimeException(ex);
			}
			logLevelNote.setVisible(newValue != Main.node.logLevel);
		});
		pauseLog.selectedProperty().addListener((observable, oldValue, newValue) -> {
			if (!newValue && !queuedLogContent.isEmpty()) {
				appendText(queuedLogContent.toString());
			}
			queuedLogContent = new StringBuilder();
		});
		log.textProperty().addListener((observable, oldValue, newValue) -> {
			if (newValue.length() > LOG_LENGTH_LIMIT) {
				log.deleteText(0, newValue.length() - LOG_LENGTH_LIMIT);
			}
		});
		log.setWrapText(true);
		progress.progressProperty().bind(Main.node.nodeSyncProgress);
		blocksNodeNetwork.textProperty().bind(Bindings.concat(Main.node.nodeBlockHeight, "/", Main.node.networkBlockHeight));
	}

	public void logVersionUpdate(String latestVersion) {
		appendText("\n-------- " + Main.lang("nodeWasUpdatedToVersion_s_log").formatted(latestVersion) + " --------\n\n");
	}

	private StringBuilder queuedLogContent = new StringBuilder();

	private void appendText(String text) {
		if (pauseLog.isSelected()) {
			queuedLogContent.append(text);
			if (queuedLogContent.length() > LOG_LENGTH_LIMIT) {
				queuedLogContent.replace(0, queuedLogContent.length() - LOG_LENGTH_LIMIT, "");
			}
		} else {
			log.appendText(text);
		}
	}
}
