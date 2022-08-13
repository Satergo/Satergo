package com.satergo.controller;

import com.satergo.*;
import com.satergo.ergo.ErgoInterface;
import com.satergo.extra.IncorrectPasswordException;
import javafx.application.Platform;
import javafx.collections.MapChangeListener;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.Pair;
import org.ergoplatform.appkit.Address;

import java.net.URL;
import java.text.DecimalFormat;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class AccountCtrl implements Initializable, WalletTab {

	@FXML private Label totalBalanceLabel, totalBalance;
	@FXML private Node mnemonicPhraseLabel, mnemonicPhraseButton, mnemonicPasswordLabel, mnemonicPasswordButton;
	@FXML private VBox addresses;

	@FXML
	public void retrieveMnemonicPhrase(ActionEvent e) {
		WalletKey.Local key = (WalletKey.Local) Main.get().getWallet().key();
		try {
			Utils.textDialogWithCopy(Main.lang("yourMnemonicPhrase"), key.getMnemonic().getPhrase().toStringUnsecure());
		} catch (WalletKey.Failure ignored) {
			// user already informed
		}
	}

	@FXML
	public void retrieveMnemonicPassword(ActionEvent e) {
		WalletKey.Local key = (WalletKey.Local) Main.get().getWallet().key();
		try {
			Utils.textDialogWithCopy(Main.lang("yourMnemonicPassword"), key.getMnemonic().getPassword().toStringUnsecure());
		} catch (WalletKey.Failure ignored) {
			// user already informed
		}
	}

	@FXML
	public void changeWalletName(ActionEvent e) {
		TextInputDialog dialog = new TextInputDialog();
		dialog.initOwner(Main.get().stage());
		dialog.setTitle(Main.lang("renameWallet"));
		dialog.setHeaderText(null);
		dialog.getEditor().setPromptText(Main.lang("walletName"));
		dialog.showAndWait().ifPresent(newName -> Main.get().getWallet().name.set(newName));
	}

	@FXML
	public void changeWalletPassword(ActionEvent e) {
		// Create the custom dialog.
		Dialog<Pair<String, String>> dialog = new Dialog<>();
		dialog.initOwner(Main.get().stage());
		dialog.setTitle(Main.lang("programName"));
		dialog.setHeaderText(Main.lang("changePassword"));

		// Set the button types.
		ButtonType changeType = new ButtonType(Main.lang("change"), ButtonBar.ButtonData.OK_DONE);
		dialog.getDialogPane().getButtonTypes().addAll(changeType, ButtonType.CANCEL);

		// Create the currentPassword and newPassword labels and fields.
		GridPane grid = new GridPane();
		grid.setHgap(10);
		grid.setVgap(10);
		grid.setPadding(new Insets(20, 150, 10, 10));

		PasswordField currentPassword = new PasswordField();
		PasswordField newPassword = new PasswordField();

		grid.add(new Label(Main.lang("currentC")), 0, 0);
		grid.add(currentPassword, 1, 0);
		grid.add(new Label(Main.lang("newC")), 0, 1);
		grid.add(newPassword, 1, 1);

		Node changeButton = dialog.getDialogPane().lookupButton(changeType);
		changeButton.setDisable(true);
		currentPassword.textProperty().addListener((observable, oldValue, newValue) -> {
			changeButton.setDisable(newValue.trim().isEmpty());
		});
		dialog.getDialogPane().setContent(grid);
		Platform.runLater(currentPassword::requestFocus);
		dialog.setResultConverter(dialogButton -> {
			if (dialogButton == changeType) {
				return new Pair<>(currentPassword.getText(), newPassword.getText());
			}
			return null;
		});

		Pair<String, String> result = dialog.showAndWait().orElse(null);
		if (result == null) return;
		try {
			Main.get().getWallet().changePassword(result.getKey().toCharArray(), result.getValue().toCharArray());
		} catch (IncorrectPasswordException ex) {
			Utils.alertIncorrectPassword();
		}
	}

	@FXML
	public void addAddress(ActionEvent e) {
		int nextIndex = Main.get().getWallet().nextAddressIndex();
		Dialog<Pair<Integer, String>> dialog = new Dialog<>();
		dialog.initOwner(Main.get().stage());
		dialog.setTitle(Main.lang("addAddress"));
		dialog.setHeaderText(null);
		var content = new HBox() {
			{ Load.thisFxml(this, "/dialog/add-address.fxml"); }
			@FXML TextField index, name;
		};
		content.index.setText(String.valueOf(nextIndex));
		dialog.getDialogPane().setContent(content);
		dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
		dialog.setResultConverter(t -> {
			if (t == ButtonType.OK) {
				return new Pair<>(content.index.getText().isBlank() ? null : Integer.parseInt(content.index.getText()), content.name.getText());
			}
			return null;
		});
		Pair<Integer, String> result = dialog.showAndWait().orElse(null);
		if (result == null) return;
		if (Main.get().getWallet().myAddresses.containsKey(result.getKey())) {
			Utils.alert(Alert.AlertType.ERROR, Main.lang("anAddressWithThisIndexAlreadyExists"));
			return;
		}
		if (result.getKey() < 0) {
			Utils.alert(Alert.AlertType.ERROR, Main.lang("invalidAddressIndex"));
			return;
		}
		int index = Objects.requireNonNullElse(result.getKey(), nextIndex);
		Main.get().getWallet().myAddresses.put(index, result.getValue());
	}

	@FXML
	public void logout(ActionEvent e) {
		Main.get().getWalletPage().cancelRepeatingTasks();
		Main.get().setWallet(null);
		Main.get().displayTopSetupPage(Load.<WalletSetupCtrl>fxmlController("/setup-page/wallet.fxml"));
		if (Main.programData().blockchainNodeKind.get() == ProgramData.BlockchainNodeKind.EMBEDDED_FULL_NODE)
			Main.node.stop();
	}

	private static class AddressLine extends BorderPane {
		@SuppressWarnings("unused")
		@FXML private Label index, name, address;
		@SuppressWarnings("unused")
		@FXML private Button copy, rename, remove;

		public AddressLine(int index, String name, Address address, Runnable removed, Consumer<String> renamed, boolean removable) {
			Load.thisFxml(this, "/account-address-line.fxml");
			this.index.setText("#" + index);
			this.name.setText(name);
			this.address.setText(address.toString());
			this.copy.setOnAction(e -> Utils.copyStringToClipboard(address.toString()));
			this.rename.setOnAction(e -> {
				TextInputDialog dialog = new TextInputDialog();
				dialog.initOwner(Main.get().stage());
				dialog.setTitle(Main.lang("renameAddress"));
				dialog.setHeaderText(null);
				dialog.getEditor().setPromptText(Main.lang("addressName"));
				String newName = dialog.showAndWait().orElse(null);
				if (newName == null) return;
				this.name.setText(newName);
				renamed.accept(newName);
			});
			if (removable) {
				this.remove.setOnAction(e -> removed.run());
			} else {
				this.remove.setDisable(true);
			}
		}
	}

	private void updateAddresses() {
		addresses.getChildren().clear();
		Main.get().getWallet().myAddresses.forEach((index, name) -> {
			try {
				addresses.getChildren().add(new AddressLine(index, name, Main.get().getWallet().publicAddress(index),
						() -> Main.get().getWallet().myAddresses.remove(index),
						newName -> Main.get().getWallet().myAddresses.put(index, newName), index != 0));
			} catch (WalletKey.Failure e) {
				throw new RuntimeException(e);
			}
		});
	}

	@Override
	public void initialize(URL location, ResourceBundle resources) {
		totalBalanceLabel.visibleProperty().bind(Main.get().getWalletPage().offlineMode.not());
		totalBalance.visibleProperty().bind(Main.get().getWalletPage().offlineMode.not());
		DecimalFormat lossless = new DecimalFormat("0");
		lossless.setMinimumFractionDigits(9);
		lossless.setMaximumFractionDigits(9);
		if (Main.get().getWallet().lastKnownBalance.get() != null)
			totalBalance.setText(lossless.format(ErgoInterface.toFullErg(Main.get().getWallet().lastKnownBalance.get().confirmed())) + " ERG");
		// binding with a converter could be used here
		Main.get().getWallet().lastKnownBalance.addListener((observable, oldValue, newValue) -> {
			totalBalance.setText(lossless.format(ErgoInterface.toFullErg(newValue.confirmed())) + " ERG");
		});
		updateAddresses();
		Main.get().getWallet().myAddresses.addListener((MapChangeListener<Integer, String>) change -> updateAddresses());
		boolean isLocalWallet = Main.get().getWallet().key() instanceof WalletKey.Local;
		Stream.of(mnemonicPhraseLabel, mnemonicPhraseButton, mnemonicPasswordLabel, mnemonicPasswordLabel).forEach(node -> {
			node.setVisible(isLocalWallet);
			node.setManaged(isLocalWallet);
		});
	}
}
