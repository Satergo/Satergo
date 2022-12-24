package com.satergo.extra;

import javafx.beans.NamedArg;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ObjectPropertyBase;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.AccessibleAction;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.VBox;

public class SetupOption extends VBox {

	public SetupOption(@NamedArg("title") String title, @NamedArg("description") String description) {
		setFocusTraversable(true);

		getStyleClass().add("welcome-option");
		Label titleLabel = new Label(title.toUpperCase());
		titleLabel.getStyleClass().add("title");
		getChildren().add(titleLabel);
		if (description != null) {
			Label descriptionLabel = new Label(description);
			descriptionLabel.getStyleClass().add("description");
			getChildren().add(descriptionLabel);
		}

		setOnMouseClicked(e -> {
			if (e.getButton() == MouseButton.PRIMARY)
				fire();
		});

		setOnKeyReleased(e -> {
			if (e.getCode() == KeyCode.SPACE && isFocusVisible())
				fire();
		});
	}

	public SetupOption(@NamedArg("title") String title) {
		this(title, null);
	}

	public void fire() {
		if (!isDisabled()) {
			fireEvent(new ActionEvent());
		}
	}

	public ObjectProperty<EventHandler<ActionEvent>> onActionProperty() { return onAction; }
	public void setOnAction(EventHandler<ActionEvent> onAction) { this.onAction.set(onAction); }
	public EventHandler<ActionEvent> getOnAction() { return onAction.get(); }
	private final ObjectProperty<EventHandler<ActionEvent>> onAction = new ObjectPropertyBase<>() {
		@Override protected void invalidated() {
			setEventHandler(ActionEvent.ACTION, get());
		}
		@Override public Object getBean() { return SetupOption.this; }
		@Override public String getName() { return null; }
	};

	@Override public void executeAccessibleAction(AccessibleAction action, Object... parameters) {
		switch (action) {
			case FIRE -> fire();
			default -> super.executeAccessibleAction(action);
		}
	}
}
