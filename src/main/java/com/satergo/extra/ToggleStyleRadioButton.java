package com.satergo.extra;

import javafx.scene.control.RadioButton;

public class ToggleStyleRadioButton extends RadioButton {

	public ToggleStyleRadioButton() {
		getStyleClass().setAll("toggle-button");
	}

	public ToggleStyleRadioButton(String text) {
		super(text);
		getStyleClass().setAll("toggle-button");
	}
}
