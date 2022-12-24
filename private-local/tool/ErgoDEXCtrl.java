package com.satergo.controller;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;

import java.net.URL;
import java.util.ResourceBundle;

public class ErgoDEXCtrl implements Initializable {
	@FXML private BorderPane root;
	@FXML private VBox centerBox;

	@Override
	public void initialize(URL location, ResourceBundle resources) {
		centerBox.maxWidthProperty().bind(root.widthProperty().divide(2));
		centerBox.maxHeightProperty().bind(centerBox.minHeightProperty());
	}
}
