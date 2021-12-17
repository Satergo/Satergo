package com.satergo.controller;

import com.satergo.Load;
import com.satergo.Main;
import com.satergo.Translations;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.input.MouseButton;

import java.net.URL;
import java.util.ResourceBundle;

public class BlockchainSetupCtrl implements Initializable {

	@FXML private ComboBox<Translations.Entry> language;
	@FXML private Node localFullNode, remoteNode;

	@Override
	public void initialize(URL location, ResourceBundle resources) {
		language.setConverter(Translations.Entry.TO_NAME_CONVERTER);
		language.getItems().addAll(Main.get().translations.getEntries());
		language.setValue(Main.get().translations.getEntry(Main.programData().language.get()));
		language.valueProperty().addListener((observable, oldValue, newValue) -> {
			Main.get().setLanguage(newValue);
			Main.get().displayNewTopPage(Load.fxml("/blockchain-setup.fxml"));
		});
		localFullNode.setOnMouseClicked(e -> {
			if (e.getButton() == MouseButton.PRIMARY) {
				Main.get().displayPage(Load.fxml("/full-node-source.fxml"));
			}
		});
		remoteNode.setOnMouseClicked(e -> {
			if (e.getButton() == MouseButton.PRIMARY) {
				Main.get().displayPage(Load.fxml("/remote-node-setup.fxml"));
			}
		});
	}
}
