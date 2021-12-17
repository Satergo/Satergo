/*
 * Copyright (c) 2014, 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.satergo.extra;

import javafx.application.Platform;
import javafx.beans.NamedArg;
import javafx.geometry.Pos;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

/**
 * A dialog that shows a password input control to the user.
 *
 * Edited version of {@link TextInputDialog}
 */
public class PasswordInputDialog extends Dialog<String> {

	/* ************************************************************************
	 *
	 * Fields
	 *
	 **************************************************************************/

	private final GridPane grid;
	private final Label label;
	private final PasswordField passwordField;
	private final String defaultValue;



	/* ************************************************************************
	 *
	 * Constructors
	 *
	 **************************************************************************/

	/**
	 * Creates a new TextInputDialog without a default value entered into the
	 * dialog {@link TextField}.
	 */
	public PasswordInputDialog() {
		this("");
	}

	/**
	 * Creates a new PasswordInputDialog with the default value entered into the
	 * dialog {@link PasswordField}.
	 * @param defaultValue the default value entered into the dialog
	 */
	public PasswordInputDialog(@NamedArg("defaultValue") String defaultValue) {
		final DialogPane dialogPane = getDialogPane();

		// -- passwordfield
		this.passwordField = new PasswordField();
		this.passwordField.setText(defaultValue);
		this.passwordField.setMaxWidth(Double.MAX_VALUE);
		GridPane.setHgrow(passwordField, Priority.ALWAYS);
		GridPane.setFillWidth(passwordField, true);

		// -- label
		label = new Label(dialogPane.getContentText());
		label.setMaxWidth(Double.MAX_VALUE);
		label.setMaxHeight(Double.MAX_VALUE);
		label.getStyleClass().add("content");
		label.setWrapText(true);
		label.setPrefWidth(360);
		label.setPrefWidth(Region.USE_COMPUTED_SIZE);
		label.textProperty().bind(dialogPane.contentTextProperty());

		this.defaultValue = defaultValue;

		this.grid = new GridPane();
		this.grid.setHgap(10);
		this.grid.setMaxWidth(Double.MAX_VALUE);
		this.grid.setAlignment(Pos.CENTER_LEFT);

		dialogPane.contentTextProperty().addListener(o -> updateGrid());

//		setTitle(ControlResources.getString("Dialog.confirm.title"));
//		dialogPane.setHeaderText(ControlResources.getString("Dialog.confirm.header"));
		dialogPane.getStyleClass().add("text-input-dialog");
		dialogPane.getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

		updateGrid();

		setResultConverter((dialogButton) -> {
			ButtonData data = dialogButton == null ? null : dialogButton.getButtonData();
			return data == ButtonData.OK_DONE ? passwordField.getText() : null;
		});
	}



	/* ************************************************************************
	 *
	 * Public API
	 *
	 **************************************************************************/

	/**
	 * Returns the {@link TextField} used within this dialog.
	 * @return the {@link TextField} used within this dialog
	 */
	public final TextField getEditor() {
		return passwordField;
	}

	/**
	 * Returns the default value that was specified in the constructor.
	 * @return the default value that was specified in the constructor
	 */
	public final String getDefaultValue() {
		return defaultValue;
	}



	/* ************************************************************************
	 *
	 * Private Implementation
	 *
	 **************************************************************************/

	private void updateGrid() {
		grid.getChildren().clear();

		grid.add(label, 0, 0);
		grid.add(passwordField, 1, 0);
		getDialogPane().setContent(grid);

		Platform.runLater(() -> passwordField.requestFocus());
	}
}
