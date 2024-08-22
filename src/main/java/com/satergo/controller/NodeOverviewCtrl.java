package com.satergo.controller;

import com.satergo.Icon;
import com.satergo.Load;
import com.satergo.Main;
import com.satergo.Utils;
import com.satergo.node.EmbeddedNode;
import com.satergo.node.ErgoNodeAccess;
import com.satergo.node.VMArguments;
import com.satergo.extra.dialog.MoveStyle;
import com.satergo.extra.dialog.SatPromptDialog;
import com.satergo.extra.dialog.SatTextInputDialog;
import com.satergo.extra.dialog.SatVoidDialog;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.ListChangeListener;
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
import java.net.*;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static com.satergo.Utils.HTTP;

public class NodeOverviewCtrl implements Initializable, WalletTab {
	private static final int LOG_LENGTH_LIMIT = 1_000_000;

	@FXML private Label networkType;
	@FXML private ProgressBar progress;
	@FXML private Label headersNote;
	@FXML private Label heightNodeAndNetwork;
	@FXML private TextArea log;
	@FXML private Label restartNeededNote;
	@FXML private Button toggleLogPaused;
	@FXML private Label peers;
	@FXML private CheckBox autoUpdateOption;

	private final SimpleBooleanProperty logPaused = new SimpleBooleanProperty(false);

	@FXML private ContextMenu extra;
	@FXML private Menu logLevelMenu;

	public void transferLog() {
		new Thread(() -> {
			try {
				InputStream inputStream = Main.node.standardOutput();
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
		Main.node.stop();
		Main.node.waitForExit();
		appendText("\n-------- " + Main.lang("nodeWasRestartedLog") + " --------\n\n");
		Main.node.start();
		restartNeededNote.setVisible(false);
		bindToProperties();
		transferLog();
		Main.get().getWalletPage().bindToNodeProperties();
	}

	@FXML
	public void clearLog(ActionEvent e) {
		log.setText("");
	}

	@Override
	public void initialize(URL location, ResourceBundle resources) {
		transferLog();
		networkType.textProperty().bind(Main.programData().nodeNetworkType.asString());
		ToggleGroup logLevelGroup = new ToggleGroup();
		for (EmbeddedNode.LogLevel value : EmbeddedNode.LogLevel.values()) {
			RadioMenuItem item = new RadioMenuItem(value.toString());
			item.setToggleGroup(logLevelGroup);
			item.setSelected(value == Main.node.logLevel());
			item.setUserData(value);
			logLevelMenu.getItems().add(item);
		}
		logLevelGroup.selectedToggleProperty().addListener((observable, oldValue, newValue) -> {
			try {
				EmbeddedNode.LogLevel level = (EmbeddedNode.LogLevel) newValue.getUserData();
				restartNeededNote.setVisible(level != Main.node.logLevel());
				Main.node.info = Main.node.info.withLogLevel(level);
				Main.node.writeInfo();
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		});
		toggleLogPaused.graphicProperty().bind(Bindings.when(logPaused).then(new Icon("resume")).otherwise(new Icon("pause", 12)));
		toggleLogPaused.textProperty().bind(Bindings.when(logPaused).then(Main.lang("resumeLog")).otherwise(Main.lang("pauseLog")));
		logPaused.addListener((observable, oldValue, newValue) -> {
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
		bindToProperties();
		autoUpdateOption.setSelected(Main.node.info.autoUpdate());
	}

	public void bindToProperties() {
		headersNote.visibleProperty().bind(Main.node.headersSynced.not());
		progress.progressProperty().bind(Bindings.when(Main.node.headersSynced)
				.then(Main.node.nodeSyncProgress)
				.otherwise(Main.node.nodeHeaderSyncProgress));
		heightNodeAndNetwork.visibleProperty().bind(Main.node.headersSynced);
		heightNodeAndNetwork.textProperty().bind(Bindings.concat(
				Bindings.when(Main.node.nodeBlockHeight.lessThan(0)).then("?").otherwise(Main.node.nodeBlockHeight.asString()),
				"/", Bindings.when(Main.node.networkBlockHeight.lessThan(0)).then("?").otherwise(Main.node.networkBlockHeight.asString())));
		peers.textProperty().bind(Bindings.format(Main.lang("peers_d"), Main.node.peerCount));
	}

	public void logVersionUpdate(String latestVersion) {
		appendText("\n-------- " + Main.lang("nodeWasUpdatedToVersion_s_log").formatted(latestVersion) + " --------\n\n");
	}

	private StringBuilder queuedLogContent = new StringBuilder();

	private void appendText(String text) {
		if (logPaused.get()) {
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
		SatTextInputDialog dialog = new SatTextInputDialog();
		Utils.initDialog(dialog, Main.get().stage(), MoveStyle.FOLLOW_OWNER);		dialog.setTitle(Main.lang("changeApiKey"));
		dialog.setHeaderText(Main.lang("willBeChangedOnNodeRestart"));
		dialog.getEditor().setPromptText(Main.lang("newApiKey"));
		String key = dialog.showForResult().orElse(null);
		if (key != null) {
			byte[] hashBytes = (byte[]) Blake2b256.hash(key);
			String hash = HexFormat.of().formatHex(hashBytes);
			Main.node.setConfValue("scorex.restApi.apiKeyHash", hash);
			restartNeededNote.setVisible(true);
		}
	}

	@FXML
	public void openConf(ActionEvent e) {
		Utils.showDocument(Main.node.confFile.getAbsolutePath());
	}

	@FXML
	public void unlockServerWallet(ActionEvent e) {
		SatPromptDialog<Pair<String, String>> dialog = new SatPromptDialog<>();
		Utils.initDialog(dialog, Main.get().stage(), MoveStyle.FOLLOW_OWNER);		dialog.setTitle(Main.lang("unlockServerWallet"));
		dialog.setHeaderText(Main.lang("unlockServerWallet"));
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
		dialog.showForResult().ifPresent(result -> {
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
		SatPromptDialog<Pair<String, Integer>> dialog = new SatPromptDialog<>();
		Utils.initDialog(dialog, Main.get().stage(), MoveStyle.FOLLOW_OWNER);		dialog.setTitle(Main.lang("setPublicAddress"));
		dialog.setHeaderText(Main.lang("setPublicAddress"));
		dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.APPLY);
		GridPane gridPane = new GridPane();
		gridPane.setHgap(4);
		TextField host = new TextField();
		TextField port = new TextField(switch(Main.programData().nodeNetworkType.get()) {
			case MAINNET -> "9030";
			case TESTNET -> "9020";
		});
		gridPane.add(new Label(Main.lang("addressIPC")), 0, 0);
		gridPane.add(host, 1, 0);
		Button fetch = new Button(Main.lang("fetch"));
		fetch.setOnAction(ae -> {
			try {
				HttpResponse<String> response = HTTP.send(Utils.httpRequestBuilder().uri(URI.create("https://icanhazip.com")).build(), HttpResponse.BodyHandlers.ofString());
				if (response.statusCode() == 200) {
					host.setText(response.body());
					return;
				}
			} catch (IOException | InterruptedException ignored) {
			}
			Utils.alert(Alert.AlertType.ERROR, Main.lang("failedToFetchIPAddress"));
		});
		gridPane.add(fetch, 2, 0);
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
				return new Pair<>(host.getText(), Integer.parseInt(port.getText()));
			}
			return null;
		});
		Pair<String, Integer> result = dialog.showForResult().orElse(null);
		if (result != null) {
			Main.node.setConfValue("scorex.network.declaredAddress", toSocketAddress(result.getKey(), result.getValue()));
			restartNeededNote.setVisible(true);
		}
	}

	@FXML
	public void changeAutoUpdate(ActionEvent e) {
		Main.node.info = Main.node.info.withAutoUpdate(autoUpdateOption.isSelected());
		boolean sel = autoUpdateOption.isSelected();
		new Thread(() -> {
			try {
				Main.node.writeInfo();
			} catch (IOException ex) {
				throw new RuntimeException(ex);
			}
			if (sel) Main.node.checkForUpdate();
		}).start();
	}

	@FXML
	public void toggleLogPaused(ActionEvent e) {
		logPaused.set(!logPaused.get());
	}

	@FXML
	public void vmArguments(ActionEvent e) {
		var d = new GridPane() {
			{ Load.thisFxml(this, "/dialog/vm-arguments.fxml"); }

			@FXML CheckBox limitRam;
			@FXML TextField maxRam, free;
		};

		VMArguments args = new VMArguments(Main.node.info.vmArguments());

		d.limitRam.setSelected(args.getMaxRam().isPresent());
		d.free.setText(args.toString());

		args.arguments.addListener((ListChangeListener<String>) c -> {
			if (!d.free.isFocused()) {
				d.free.setText(args.toString());
			}
		});
		d.free.textProperty().addListener((observable, oldValue, newValue) -> {
			if (d.free.isFocused()) {
				args.arguments.setAll(newValue.split(" "));
				args.getMaxRam().ifPresent(d.maxRam::setText);
			}
		});

		d.limitRam.selectedProperty().addListener((observable, oldValue, newValue) -> {
			if (!newValue) args.setMaxRam(null);
			else args.setMaxRam(d.maxRam.getText());
		});
		double maxSystemRamG = Utils.getTotalSystemMemory() / Math.pow(1024, 3);
		d.maxRam.setPromptText(Main.lang("systemTotalRam").formatted((int) maxSystemRamG));
		args.getMaxRam().ifPresent(d.maxRam::setText);
		d.maxRam.textProperty().addListener((observable, oldValue, newValue) -> args.setMaxRam(newValue));

		d.free.setPromptText(Main.lang("fullyCustomArguments"));

		SatPromptDialog<String> dialog = new SatPromptDialog<>();
		Utils.initDialog(dialog, Main.get().stage(), MoveStyle.FOLLOW_OWNER);		dialog.getDialogPane().setContent(d);

		ButtonType reset = new ButtonType(Main.lang("reset"), ButtonBar.ButtonData.NO);
		dialog.getDialogPane().getButtonTypes().addAll(reset, ButtonType.APPLY, ButtonType.CANCEL);
		dialog.setResultConverter(t -> {
			if (t == reset) return "";
			else if (t == ButtonType.APPLY) return args.toString();
			return null;
		});

		while (true) {
			try {
				String value = dialog.showForResult().orElse(null);
				if (value == null) break;
				if (value.isBlank()) {
					Main.node.info = Main.node.info.withVMArguments(Collections.emptyList());
				} else {
					args.validate(Utils.getTotalSystemMemory());
					Main.node.info = Main.node.info.withVMArguments(List.copyOf(args.arguments));
					Main.node.writeInfo();
				}
				restartNeededNote.setVisible(true);
				break;
			} catch (IllegalArgumentException ex) {
				SatVoidDialog alert = Utils.alert(Alert.AlertType.ERROR, ex.getMessage());
				alert.hide();
				alert.showAndWait();
			} catch (IOException ex) {
				throw new RuntimeException(ex);
			}
		}
	}

	private static String toSocketAddress(String host, int port) {
		try {
			InetAddress inetAddress = InetAddress.getByName(host);
			// If an IP address (not a domain) was supplied
			// getHostName() cannot be used because it gets the host from the name service
			// which converts 127.0.0.1 to localhost which is unwanted
			// toString() uses the method that gives the host name without using the name service
			if (inetAddress.toString().startsWith("/")) {
				if (inetAddress instanceof Inet6Address) {
					return "[" + inetAddress.getHostAddress() + "]:" + port;
				} else return inetAddress.getHostAddress() + ":" + port;
			} else return host + ":" + port;
		} catch (UnknownHostException ex) {
			// If this exception happens, the address is almost certainly not reachable, but the node can worry about that
			return host + ":" + port;
		}
	}
}
