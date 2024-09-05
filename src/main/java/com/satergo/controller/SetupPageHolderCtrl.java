package com.satergo.controller;

import com.satergo.Main;
import com.satergo.Translations;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;

import java.net.URL;
import java.util.ResourceBundle;

public class SetupPageHolderCtrl implements Initializable {

	private final SetupPage page;

	@FXML private Pane setupPageHolder; // root
	@FXML private Pane content;
	@FXML private ComboBox<Translations.Entry> language;
	@FXML private Button left;
	@FXML private Label version;

	public SetupPageHolderCtrl(SetupPage page) {
		this.page = page;
	}

	@Override
	public void initialize(URL location, ResourceBundle resources) {
		language.setVisible(page.showExtra());
		left.setVisible(page instanceof SetupPage.CustomLeft c ? c.hasLeft() : !Main.get().getAllPages().isEmpty());
		language.setConverter(Translations.Entry.TO_NAME_CONVERTER);
		language.getItems().addAll(Main.get().translations.getEntries());
		language.setValue(Main.get().translations.getEntry(Main.programData().language.get()));
		language.valueProperty().addListener((observable, oldValue, newValue) -> {
			Main.get().setLanguage(newValue);
			left.setText(Main.lang("back"));
			content.getChildren().set(0, configureContent(page.recreate()));
		});
		content.getChildren().add(configureContent(page.content()));
		left.setDisable(page instanceof SetupPage.CustomLeft c ? !c.hasLeft() : Main.get().getAllPages().isEmpty());
		setupPageHolder.addEventHandler(KeyEvent.KEY_PRESSED, e -> {
			if (e.getCode() == KeyCode.ESCAPE && !left.isDisable())
				left(null);
		});
		version.setVisible(page.showExtra());
		version.setText("v" + Main.VERSION);
	}

	private Parent configureContent(Parent content) {
		VBox.setVgrow(content, Priority.ALWAYS);
		return content;
	}

	@FXML
	public void left(ActionEvent e) {
		if (page instanceof SetupPage.CustomLeft c) c.left();
		else {
			page.cleanup();
			Main.get().previousPage();
		}
	}
}
