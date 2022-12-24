package com.satergo.extra.dialog;

import javafx.scene.control.PasswordField;

public class SatPasswordInputDialog extends SatTextInputDialog {

	public SatPasswordInputDialog(String defaultValue) {
		super(defaultValue, new PasswordField());
	}

	public SatPasswordInputDialog() {
		super("", new PasswordField());
	}

}
