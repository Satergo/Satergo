package com.satergo.controller;

import com.satergo.Load;
import com.satergo.Main;
import com.satergo.ProgramData;
import com.satergo.Utils;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.util.Pair;
import org.ergoplatform.appkit.NetworkType;

import java.net.URL;
import java.util.ResourceBundle;

public class RemoteNodeSetupCtrl implements SetupPage.WithExtra, Initializable {
	@FXML private Pane root;
	// Container of address and networkType
	@FXML private VBox vbox;
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
			Main.get().displaySetupPage(Load.<WalletSetupCtrl>fxmlController("/setup-page/wallet.fxml"));
		}
	}

	@Override
	public void initialize(URL location, ResourceBundle resources) {
		networkType.getItems().addAll(NetworkType.values());
		networkType.setValue(NetworkType.MAINNET);
		vbox.maxWidthProperty().bind(root.widthProperty().divide(2));
	}

	@FXML
	public void useKnownPublicNode(ActionEvent e) {
		// TODO node discovery or at the very least a list of nodes MUST be used to prevent centralization issues
		address.setText("http://213.239.193.208:9053");
		continueSetup(null);
	}

	@Override
	public Parent content() {
		return root;
	}

	@Override
	public Parent recreate() {
		Pair<Parent, RemoteNodeSetupCtrl> load = Load.fxmlNodeAndController("/setup-page/remote-node.fxml");
		load.getValue().address.setText(address.getText());
		load.getValue().networkType.setValue(networkType.getValue());
		return load.getKey();
	}
}
