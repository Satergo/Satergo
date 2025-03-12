package com.satergo.controller;

import com.satergo.Load;
import com.satergo.Main;
import com.satergo.Utils;
import com.satergo.extra.SetupOption;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Parent;

import java.net.URL;
import java.util.ResourceBundle;

public class HardwareWalletSetupCtrl implements SetupPage.WithoutExtra, Initializable {

	@FXML private Parent root;
	@FXML private SetupOption svault;

	@Override
	public void initialize(URL location, ResourceBundle resources) {
		if (!Utils.isLinux()) {
			svault.setDisable(true);
			svault.setDescription(Main.lang("svault.unsupportedOS").formatted("Linux"));
		} else {
			svault.setDescription(Main.lang("svault.turnOnBluetoothBefore"));
		}
	}

	@Override public Parent content() { return root; }

	@FXML
	public void ledger(ActionEvent e) {
		Main.get().displaySetupPage(Load.<LedgerSetupCtrl>fxmlController("/setup-page/hw-ledger.fxml"));
	}

	@FXML
	public void svault(ActionEvent e) {
		Main.get().displaySetupPage(Load.<SVaultSetupCtrl>fxmlController("/setup-page/hw-svault.fxml"));
	}
}
