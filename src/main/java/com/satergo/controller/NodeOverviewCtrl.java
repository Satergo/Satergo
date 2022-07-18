package com.satergo.controller;

import com.satergo.Main;
import com.satergo.Utils;
import com.satergo.ergo.EmbeddedFullNode;
import com.satergo.ergo.ErgoNodeAccess;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigRenderOptions;
import com.typesafe.config.ConfigValueFactory;
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
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HexFormat;
import java.util.Map;
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
			byte[] hashBytes = (byte[]) Blake2b256.hash(key);
			String hash = HexFormat.of().formatHex(hashBytes);
			setConfValue("scorex.restApi.apiKeyHash", hash);
		}
	}

	@FXML
	public void openConf(ActionEvent e) throws IOException {
		try {
			java.awt.Desktop.getDesktop().edit(Main.node.confFile);
		} catch (UnsupportedOperationException ex) {
			Main.get().getHostServices().showDocument(Main.node.confFile.getAbsolutePath());
		}
	}

	@FXML
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

	@FXML
	public void setPublicAddress(ActionEvent e) throws IOException {
		Dialog<Pair<String, Integer>> dialog = new Dialog<>();
		dialog.initOwner(Main.get().stage());
		dialog.setTitle(Main.lang("setPublicAddress"));
		dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.APPLY);
		GridPane gridPane = new GridPane();
		gridPane.setHgap(4);
		TextField address = new TextField();
		TextField port = new TextField(switch(Main.programData().nodeNetworkType.get()) {
			case MAINNET -> "9030";
			case TESTNET -> "9020";
		});
		gridPane.add(new Label(Main.lang("addressIPC")), 0, 0);
		gridPane.add(address, 1, 0);
		Button fetch = new Button("Fetch");
		fetch.setOnAction(ae -> {
			try {
				HttpResponse<String> response = HttpClient.newHttpClient().send(Utils.httpRequestBuilder().uri(URI.create("https://icanhazip.com")).build(), HttpResponse.BodyHandlers.ofString());
				if (response.statusCode() == 200) {
					address.setText(response.body());
					return;
				}
			} catch (IOException | InterruptedException ignored) {
			}
			Utils.alert(Alert.AlertType.ERROR, Main.lang("failedToFetchIPAddress"));
		});
		gridPane.add(new Label(Main.lang("portC")), 0, 1);
		gridPane.add(port, 1, 1);
		dialog.getDialogPane().setContent(gridPane);
		Node applyButton = dialog.getDialogPane().lookupButton(ButtonType.APPLY);
		port.textProperty().addListener((observable, oldValue, newValue) -> {
			try {
				Integer.parseInt(newValue);
				applyButton.setDisable(false);
			} catch (NumberFormatException ex) {
				applyButton.setDisable(true);
			}
		});
		dialog.setResultConverter(t -> {
			if (t == ButtonType.APPLY) {
				return new Pair<>(address.getText(), Integer.parseInt(port.getText()));
			}
			return null;
		});
		Pair<String, Integer> result = dialog.showAndWait().orElse(null);
		if (result != null) {
			setConfValue("scorex.network.declaredAddress", result.getKey() + ":" + result.getValue());
		}
	}

	private void setConfValue(String propertyPath, Object value) throws IOException {
		Files.writeString(Main.node.confFile.toPath(), ConfigFactory.parseFile(Main.node.confFile)
				.withValue(propertyPath, ConfigValueFactory.fromAnyRef(propertyPath))
				.root().render(ConfigRenderOptions.defaults()
						.setOriginComments(false)
						.setJson(false)));
	}
}
