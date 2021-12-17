package com.satergo.controller;

import com.satergo.Load;
import com.satergo.Main;
import com.satergo.ProgramData;
import com.satergo.Utils;
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
import javafx.scene.layout.VBox;
import javafx.util.Pair;
import org.ergoplatform.appkit.Address;
import org.ergoplatform.appkit.SecretString;

import java.net.URL;
import java.text.DecimalFormat;
import java.util.ResourceBundle;
import java.util.function.Consumer;

public class AccountCtrl implements Initializable, WalletTab {
	@FXML private Label totalBalance;
	@FXML private VBox addresses;

	@FXML
	public void retrieveMnemonicPhrase(ActionEvent e) {
		Utils.requestPassword(Main.lang("walletPassword"), password -> {
			Utils.textDialogWithCopy(Main.lang("yourMnemonicPhrase"), Main.get().getWallet().getMnemonic(SecretString.create(password)).getPhrase().toStringUnsecure());
		});
	}

	@FXML
	public void retrieveMnemonicPassword(ActionEvent e) {
		Utils.requestPassword(Main.lang("walletPassword"), password -> {
			Utils.textDialogWithCopy(Main.lang("yourMnemonicPassword"), Main.get().getWallet().getMnemonic(SecretString.create(password)).getPassword().toStringUnsecure());
		});
	}

	@FXML
	public void changeWalletName(ActionEvent e) {
		TextInputDialog dialog = new TextInputDialog();
		dialog.initOwner(Main.get().stage());
		// todo title etc
		String newName = dialog.showAndWait().orElse(null);
		if (newName == null) return;
		Main.get().getWallet().setName(newName);
	}

	@FXML
	public void changeWalletPassword(ActionEvent e) {
		// Create the custom dialog.
		Dialog<Pair<String, String>> dialog = new Dialog<>();
		Main.get().applySameTheme(dialog.getDialogPane().getScene());
		dialog.setTitle(Main.lang("programName"));
		dialog.setHeaderText("Change password");

		// Set the button types.
		ButtonType loginButtonType = new ButtonType(Main.lang("change"), ButtonBar.ButtonData.OK_DONE);
		dialog.getDialogPane().getButtonTypes().addAll(loginButtonType, ButtonType.CANCEL);

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

		Node changeButton = dialog.getDialogPane().lookupButton(loginButtonType);
		changeButton.setDisable(true);
		currentPassword.textProperty().addListener((observable, oldValue, newValue) -> {
			changeButton.setDisable(newValue.trim().isEmpty());
		});
		dialog.getDialogPane().setContent(grid);
		Platform.runLater(currentPassword::requestFocus);
		dialog.setResultConverter(dialogButton -> {
			if (dialogButton == loginButtonType) {
				return new Pair<>(currentPassword.getText(), newPassword.getText());
			}
			return null;
		});

		Pair<String, String> result = dialog.showAndWait().orElse(null);
		if (result == null) return;
		try {
			Main.get().getWallet().changePassword(SecretString.create(result.getKey()),	SecretString.create(result.getValue()));
		} catch (IncorrectPasswordException ex) {
			Utils.alert(Alert.AlertType.ERROR, Main.lang("incorrectPassword"));
		}
	}

	@FXML
	public void addAddress(ActionEvent e) {
		int index = Main.get().getWallet().nextAddressIndex();
		TextInputDialog dialog = new TextInputDialog();
		dialog.initOwner(Main.get().stage());
		dialog.setTitle(Main.lang("addressName"));
		dialog.setHeaderText(null);
		String name = dialog.showAndWait().orElse(null);
		if (name == null) return;
		Main.get().getWallet().myAddresses.put(index, name);
	}

	@FXML
	public void logout(ActionEvent e) {
		WalletCtrl.current.cancelTimer();
		WalletCtrl.current = null;
		Main.get().setWallet(null);
		Main.get().displayNewTopPage(Load.fxml("/wallet-setup.fxml"));
		if (Main.programData().blockchainNodeKind.get() == ProgramData.BlockchainNodeKind.EMBEDDED_FULL_NODE)
			Main.node.stop();
	}

	private static class AddressLine extends BorderPane {
		@SuppressWarnings("unused")
		@FXML private Label index, name, address;
		@SuppressWarnings("unused")
		@FXML private Button copy, remove;

		public AddressLine(int index, String name, Address address, Runnable removed, Consumer<String> renamed) {
			Load.thisFxml(this, "/account-address-line.fxml");
			this.index.setText("#" + index);
			this.name.setText(name);
			this.address.setText(address.toString());
			this.copy.setOnAction(e -> Utils.copyStringToClipboard(address.toString()));
			this.remove.setOnAction(e -> removed.run());
			setOnContextMenuRequested(e -> {
				ContextMenu contextMenu = new ContextMenu();
				MenuItem menuItem = new MenuItem("rename");
				menuItem.setOnAction(ae -> {
					TextInputDialog dialog = new TextInputDialog();
					String newName = dialog.showAndWait().orElse(null);
					if (newName == null) return;
					this.name.setText(newName);
					renamed.accept(newName);
				});
				contextMenu.getItems().add(menuItem);
				contextMenu.show(this, e.getScreenX(), e.getScreenY());
			});
		}
	}

	private void updateAddresses() {
		addresses.getChildren().clear();
		Main.get().getWallet().myAddresses.forEach((index, name) -> {
			addresses.getChildren().add(new AddressLine(index, name, Main.get().getWallet().publicAddress(index), () -> Main.get().getWallet().myAddresses.remove(index), newName -> Main.get().getWallet().myAddresses.put(index, newName)));
		});
	}

	@Override
	public void initialize(URL location, ResourceBundle resources) {
		DecimalFormat lossless = new DecimalFormat("0");
		lossless.setMinimumFractionDigits(9);
		lossless.setMaximumFractionDigits(9);
		totalBalance.setText(lossless.format(ErgoInterface.toFullErg(Main.get().getWallet().balance().confirmed())) + " ERG");
		updateAddresses();
		Main.get().getWallet().myAddresses.addListener((MapChangeListener<Integer, String>) change -> updateAddresses());
	}
}
