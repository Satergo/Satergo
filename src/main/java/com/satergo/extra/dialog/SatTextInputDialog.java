package com.satergo.extra.dialog;

import javafx.beans.NamedArg;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.stage.WindowEvent;

public class SatTextInputDialog extends SatPromptDialog<String> {

	private final TextInputControl input;

	public SatTextInputDialog(@NamedArg("defaultValue") String defaultValue, @NamedArg("input") TextInputControl input) {
		this.input = input;
		getDialogPane().setContent(input);

		addEventFilter(WindowEvent.WINDOW_SHOWN, e -> input.requestFocus());

		getDialogPane().getStyleClass().add("text-input-dialog");
		getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

		setResultConverter((dialogButton) -> {
			ButtonBar.ButtonData data = dialogButton == null ? null : dialogButton.getButtonData();
			return data == ButtonBar.ButtonData.OK_DONE ? input.getText() : null;
		});
	}

	public SatTextInputDialog(@NamedArg("defaultValue") String defaultValue) {
		this(defaultValue, new TextField());
	}

	public SatTextInputDialog() {
		this("", new TextField());
	}

	public final TextInputControl getEditor() {
		return input;
	}
}
