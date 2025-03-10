package com.satergo.controller;

import com.satergo.Load;
import com.satergo.Main;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Parent;

public class HardwareWalletSetupCtrl implements SetupPage.WithoutExtra {

	@FXML private Parent root;

	@Override public Parent content() { return root; }

	@FXML
	public void ledger(ActionEvent e) {
		Main.get().displaySetupPage(Load.<LedgerSetupCtrl>fxmlController("/setup-page/hw-ledger.fxml"));
	}
}
