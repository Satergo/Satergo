package com.satergo.tool;

import com.satergo.Main;
import com.satergo.Utils;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.event.ActionEvent;
import javafx.scene.AccessibleRole;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.Label;
import javafx.scene.control.Skin;
import javafx.scene.control.SkinBase;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.text.TextAlignment;

public interface Tool {

	String name();
	Tile tile();
	default int tileColumnSpan() { return 1; }

	class Tile extends ButtonBase {

		public final int colSpan, rowSpan;
		public final SimpleBooleanProperty clickable = new SimpleBooleanProperty(true);

		public Tile(int colSpan, int rowSpan) {
			this.colSpan = colSpan;
			this.rowSpan = rowSpan;
			getStyleClass().add("tool-tile");
			setPrefWidth(100 * colSpan);
			setPrefHeight(100 * rowSpan);
			setMinSize(0, 0);

			focusTraversableProperty().bind(clickable);

			setOnMouseClicked(e -> {
				if (e.getButton() == MouseButton.PRIMARY)
					fire();
			});

			setOnKeyReleased(e -> {
				if (e.getCode() == KeyCode.SPACE && isFocusVisible())
					fire();
				else if (!Utils.isMac() && e.getCode() == KeyCode.ENTER && isFocusVisible())
					fire();
			});

			setAccessibleRole(AccessibleRole.BUTTON);
			Main.programData().lightTheme.subscribe(light -> {
				getStyleClass().add(light ? "light" : "dark");
				getStyleClass().remove(light ? "dark" : "light");
			});
		}

		public Tile(int colSpan, int rowSpan, Node child) {
			this(colSpan, rowSpan);
			getChildren().add(child);
		}

		public Tile(int colSpan, int rowSpan, String label) {
			this(colSpan, rowSpan);
			getChildren().add(label(label));
			setAccessibleText(label);
		}

		@Override
		public void fire() {
			if (!isDisabled() && clickable.get()) {
				fireEvent(new ActionEvent());
			}
		}

		@Override
		protected Skin<?> createDefaultSkin() {
			return new SkinBase<>(this) {
				{
					cursorProperty().bind(Bindings.when(getSkinnable().clickable).then(Cursor.HAND).otherwise(Cursor.DEFAULT));
				}
			};
		}

		protected static Label label(String text) {
			Label label = new Label(text);
			label.setWrapText(true);
			label.setTextAlignment(TextAlignment.CENTER);
			return label;
		}
	}
}
