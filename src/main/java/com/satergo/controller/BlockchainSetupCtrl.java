package com.satergo.controller;

import com.satergo.Load;
import com.satergo.Main;
import com.satergo.Translations;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.ComboBox;
import javafx.scene.input.MouseButton;

import java.net.URL;
import java.util.ResourceBundle;

public class BlockchainSetupCtrl implements Initializable, SetupPage.WithLanguage {

	@FXML private Parent root;
	@FXML private ComboBox<Translations.Entry> language;
	@FXML private Node localFullNode, remoteNode;

	@Override
	public void initialize(URL location, ResourceBundle resources) {
		localFullNode.setOnMouseClicked(e -> {
			if (e.getButton() == MouseButton.PRIMARY) {
				Main.get().displaySetupPage(Load.<FullNodeSourceCtrl>fxmlController("/setup-page/full-node-source.fxml"));
			}
		});
		remoteNode.setOnMouseClicked(e -> {
			if (e.getButton() == MouseButton.PRIMARY) {
				Main.get().displaySetupPage(Load.<RemoteNodeSetupCtrl>fxmlController("/setup-page/remote-node.fxml"));
			}
		});
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
