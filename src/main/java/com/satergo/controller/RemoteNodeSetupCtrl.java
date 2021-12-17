package com.satergo.controller;

import com.satergo.Load;
import com.satergo.Main;
import com.satergo.ProgramData;
import com.satergo.Utils;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import org.ergoplatform.appkit.NetworkType;

import java.net.URL;
import java.util.ResourceBundle;

public class RemoteNodeSetupCtrl implements Initializable {
	@FXML private TextField address;
	@FXML private ComboBox<NetworkType> networkType;

	@FXML
	public void continueSetup(ActionEvent e) {
		if (address.getText().isBlank() || !address.getText().startsWith("http")) {
			Utils.alert(Alert.AlertType.ERROR, Main.lang("remoteNodeAddressInvalid"));
		} else {
			Main.programData().blockchainNodeKind.set(ProgramData.BlockchainNodeKind.REMOTE_NODE);
			Main.programData().nodeNetworkType.set(networkType.getValue());
			Main.programData().nodeAddress.set(address.getText());
			Main.get().displayPage(Load.fxml("/wallet-setup.fxml"));
		}
	}

	@Override
	public void initialize(URL location, ResourceBundle resources) {
		networkType.getItems().addAll(NetworkType.values());
		networkType.setValue(NetworkType.MAINNET);
	}
}
