package com.satergo.controller;

import com.satergo.Load;
import com.satergo.Main;
import com.satergo.Translations;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.control.ComboBox;

public class BlockchainSetupCtrl implements SetupPage.WithExtra {

	@FXML private Parent root;
	@FXML private ComboBox<Translations.Entry> language;

	@FXML
	public void localFullNode(ActionEvent e) {
		Main.get().displaySetupPage(Load.<FullNodeSourceCtrl>fxmlController("/setup-page/full-node-source.fxml"));
	}

	@FXML
	public void remoteNode(ActionEvent e) {
		Main.get().displaySetupPage(Load.<RemoteNodeSetupCtrl>fxmlController("/setup-page/remote-node.fxml"));
	}

	@Override
	public Parent content() {
		return root;
	}

	@Override
	public Parent recreate() {
		return Load.fxml("/setup-page/blockchain.fxml");
	}
}
