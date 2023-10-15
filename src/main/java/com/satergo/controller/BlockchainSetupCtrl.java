package com.satergo.controller;

import com.satergo.Load;
import com.satergo.Main;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Parent;

public class BlockchainSetupCtrl implements SetupPage.WithExtra {

	@FXML private Parent root;

	@FXML
	public void localFullNode(ActionEvent e) {
		Main.get().displaySetupPage(Load.<FullNodeSourceCtrl>fxmlController("/setup-page/full-node-source.fxml"));
	}

	@FXML
	public void lightNode(ActionEvent e) {
		Main.get().displaySetupPage(Load.<LightNodeSourceCtrl>fxmlController("/setup-page/light-node-source.fxml"));
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
