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
	@FXML private ProgressBar progress;
	@FXML private Label blocksNodeNetwork;
	@FXML private ComboBox<EmbeddedFullNode.LogLevel> logLevel;
	@FXML private TextArea log;
	@FXML private Label logLevelNote;
	@FXML private CheckBox autoScroll;
	private double lastScrollTop;
	
	private void transferLog() {
		new Thread(() -> {
			try {
				InputStream inputStream = Main.node.getStandardOutput();
				long logSize = 0;
				byte[] buffer = new byte[8192];
				int read;
				while ((read = inputStream.read(buffer, 0, 8192)) >= 0) {
					logSize += read;
					String s = new String(buffer, 0, read, StandardCharsets.UTF_8);
					Platform.runLater(() -> {
						lastScrollTop = log.getScrollTop();
						log.setText(log.getText() + s);
					});
				}
			} catch (IOException e) {
				if (!e.getMessage().contains("Stream closed"))
					e.printStackTrace();
			}
		}).start();
	}

	@FXML
	public void restart(ActionEvent e) {
		Main.node.logLevel = logLevel.getValue();
		Main.node.stop();
		Main.node.waitForExit();
		lastScrollTop = log.getScrollTop();
		log.setText(log.getText() + "\n-------- " + Main.lang("nodeWasRestartedLog") + " --------\n\n");
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
		autoScroll.selectedProperty().bindBidirectional(Main.programData().nodeLogAutoScroll);
		log.textProperty().addListener((observable, oldValue, newValue) -> {
			if (autoScroll.isSelected()) Platform.runLater(() -> log.setScrollTop(Double.POSITIVE_INFINITY));
			else Platform.runLater(() -> log.setScrollTop(lastScrollTop));
		});
		progress.progressProperty().bind(Main.node.nodeSyncProgress);
		blocksNodeNetwork.textProperty().bind(Bindings.concat(Main.node.nodeBlockHeight, "/", Main.node.networkBlockHeight));
	}
}
