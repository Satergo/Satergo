package com.satergo.controller;

import com.satergo.Main;
import com.satergo.Utils;
import com.satergo.ergo.EmbeddedFullNode;
import com.satergo.ergo.ErgoNodeAccess;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.util.Pair;
import scorex.crypto.hash.Blake2b256;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HexFormat;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NodeOverviewCtrl implements Initializable, WalletTab {
	private static final int LOG_LENGTH_LIMIT = 1_000_000;

	@FXML private Label networkType;
	@FXML private ProgressBar progress;
	@FXML private Label blocksNodeNetwork;
	@FXML private ComboBox<EmbeddedFullNode.LogLevel> logLevel;
	@FXML private TextArea log;
	@FXML private Label logLevelNote;
	@FXML private CheckBox pauseLog;

	@FXML private ContextMenu extra;

	public void transferLog() {
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
		blocksNodeNetwork.textProperty().bind(Bindings.concat(
				Bindings.when(Main.node.nodeBlockHeight.lessThan(0)).then("?").otherwise(Main.node.nodeBlockHeight.asString()),
				"/", Bindings.when(Main.node.networkBlockHeight.lessThan(0)).then("?").otherwise(Main.node.networkBlockHeight.asString())));
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

	@FXML
	public void showExtra(ActionEvent e) {
		if (!extra.isShowing())
			extra.show((Node) e.getTarget(), Side.BOTTOM, 0, 0);
		else extra.hide();
	}

	@FXML
	public void setApiKey(ActionEvent e) throws IOException {
		TextInputDialog dialog = new TextInputDialog();
		dialog.initOwner(Main.get().stage());
		dialog.setTitle(Main.lang("changeApiKey"));
		dialog.setHeaderText(Main.lang("willBeChangedOnNodeRestart"));
		dialog.getEditor().setPromptText(Main.lang("newApiKey"));
		String key = dialog.showAndWait().orElse(null);
		if (key != null) {
			updateConfApiKey(key);
		}
	}

	public void openConf(ActionEvent e) throws IOException {
		try {
			java.awt.Desktop.getDesktop().edit(Main.node.confFile);
		} catch (UnsupportedOperationException ex) {
			Main.get().getHostServices().showDocument(Main.node.confFile.getAbsolutePath());
		}
	}

	public void unlockServerWallet(ActionEvent e) {
		Dialog<Pair<String, String>> dialog = new Dialog<>();
		dialog.initOwner(Main.get().stage());
		dialog.setTitle(Main.lang("unlockServerWallet"));
		dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.APPLY);
		GridPane gridPane = new GridPane();
		gridPane.setHgap(4);
		PasswordField apiKey = new PasswordField();
		PasswordField serverWalletPassword = new PasswordField();
		gridPane.add(new Label(Main.lang("apiKey") + ":"), 0, 0);
		gridPane.add(apiKey, 1, 0);
		gridPane.add(new Label(Main.lang("password") + ":"), 0, 1);
		gridPane.add(serverWalletPassword, 1, 1);
		dialog.getDialogPane().setContent(gridPane);
		dialog.setResultConverter(t -> {
			if (t == ButtonType.APPLY) {
				return new Pair<>(apiKey.getText(), serverWalletPassword.getText());
			}
			return null;
		});
		serverWalletPassword.setOnAction(ae -> dialog.getDialogPane().lookupButton(ButtonType.APPLY).fireEvent(new ActionEvent()));
		dialog.showAndWait().ifPresent(result -> {
			ErgoNodeAccess.UnlockingResult unlockingResult = Main.node.nodeAccess.unlockWallet(result.getKey(), result.getValue());
			String message = Main.lang(Map.of(
					ErgoNodeAccess.UnlockingResult.INCORRECT_API_KEY, "incorrectApiKey",
					ErgoNodeAccess.UnlockingResult.INCORRECT_PASSWORD, "incorrectServerWalletPassword",
					ErgoNodeAccess.UnlockingResult.NOT_INITIALIZED, "serverWalletIsNotInitialized",
					ErgoNodeAccess.UnlockingResult.UNKNOWN, "unknownResult",
					ErgoNodeAccess.UnlockingResult.SUCCESS, "success").get(unlockingResult));
			Utils.alert(unlockingResult != ErgoNodeAccess.UnlockingResult.SUCCESS ? Alert.AlertType.ERROR : Alert.AlertType.INFORMATION, message);
		});
	}

	private void updateConfApiKey(String apiKey) throws IOException {
		byte[] hashBytes = (byte[]) Blake2b256.hash(apiKey);
		String hash = HexFormat.of().formatHex(hashBytes);
		Pattern confApiKeyHashPattern = Pattern.compile("apiKeyHash\\s*=\\s*\"[A-Za-z\\d]+\"");
		String conf = Files.readString(Main.node.confFile.toPath());
		Matcher m1 = confApiKeyHashPattern.matcher(conf);
		String newConf;
		if (m1.find()) {
			newConf = m1.replaceFirst("apiKeyHash = \"" + hash + "\"");
		} else {
			Pattern restApiPattern = Pattern.compile("restApi\\s*\\{");
			Matcher m2 = restApiPattern.matcher(conf);
			if (m2.find()) {
				newConf = m2.replaceFirst("$0\n\t\tapiKeyHash = \"" + hash + "\"");
			} else {
				Pattern scorexPattern = Pattern.compile("scorex\\s*\\{");
				Matcher m3 = scorexPattern.matcher(conf);
				if (m3.find()) {
					newConf = m3.replaceFirst("$0\n\trestApi {\n\t\tapiKeyHash = \"" + hash + "\"\n\t}");
				} else {
					newConf = conf + "\n\nscorex {\n\trestApi {\n\t\tapiKeyHash = \"" + hash + "\"\n\t}\n}";
				}
			}
		}
		Files.writeString(Main.node.confFile.toPath(), newConf);
	}
}
