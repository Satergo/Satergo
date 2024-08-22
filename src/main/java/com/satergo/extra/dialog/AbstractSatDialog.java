package com.satergo.extra.dialog;

import com.satergo.Utils;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.event.Event;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ButtonType;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.paint.Color;
import javafx.stage.*;

import java.util.Optional;

public abstract class AbstractSatDialog<PaneType extends SatDialogPane, ResultType> extends Stage {

	private final PaneType dialogPane;

	private final ObjectProperty<MoveStyle> moveStyle = new SimpleObjectProperty<>(MoveStyle.MOVABLE);
	public ObjectProperty<MoveStyle> moveStyleProperty() { return moveStyle; }
	public MoveStyle getMoveStyle() { return moveStyle.get(); }
	public void setMoveStyle(MoveStyle moveStyle) { this.moveStyle.set(moveStyle); }

	private final ObjectProperty<ResultType> resultProperty = new SimpleObjectProperty<>() {
		protected void invalidated() {
			close();
		}
	};
	public final ObjectProperty<ResultType> resultProperty() { return resultProperty; }
	public final ResultType getResult() { return resultProperty().get(); }
	public final void setResult(ResultType value) { this.resultProperty().set(value); }

	public AbstractSatDialog() {
		dialogPane = createDialogPane();
		Scene scene = new Scene(dialogPane);
		scene.getStylesheets().add(Utils.resourcePath("/sat-dialog.css"));
		scene.setFill(Color.TRANSPARENT);
		initStyle(StageStyle.TRANSPARENT);
		initModality(Modality.WINDOW_MODAL);
		setScene(scene);
		addEventFilter(KeyEvent.KEY_PRESSED, e -> {
			if (e.getCode() == KeyCode.ESCAPE)
				Event.fireEvent(getScene().getWindow(), new WindowEvent(getScene().getWindow(), WindowEvent.WINDOW_CLOSE_REQUEST));
		});
	}

	public AbstractSatDialog(Node content) {
		this();
		dialogPane.setContent(content);
	}

	protected abstract PaneType createDialogPane();

	public PaneType getDialogPane() {
		return dialogPane;
	}

	public final void setHeaderText(String text) {
		getDialogPane().setHeaderText(text);
	}

	public abstract void setResultAndClose(ButtonType cmd, boolean close);

	public Optional<ResultType> showForResult() {
		super.showAndWait();
		return Optional.ofNullable(getResult());
	}

	@Override
	public final void centerOnScreen() {
		Window owner = getOwner();
		if (owner != null) {
			positionStage();
		} else {
			if (getWidth() > 0 && getHeight() > 0) {
				super.centerOnScreen();
			}
		}
	}

	private double prefX = Double.NaN, prefY = Double.NaN;

	private void positionStage() {
		double x = getX();
		double y = getY();

		// if the user has specified an x/y location, use it
		if (!Double.isNaN(x) && !Double.isNaN(y) &&
				Double.compare(x, prefX) != 0 && Double.compare(y, prefY) != 0) {
			setX(x);
			setY(y);
			return;
		}

		// Firstly we need to force CSS and layout to happen, as the dialogPane
		// may not have been shown yet (so it has no dimensions)
		dialogPane.applyCss();
		dialogPane.layout();

		final Window owner = getOwner();
		final Scene ownerScene = owner.getScene();

		final double titleBarHeight = ownerScene.getY();

		// because Stage does not seem to center itself over its owner, we
		// do it here.

		// then we can get the dimensions and position the dialog appropriately.
		final double dialogWidth = dialogPane.prefWidth(-1);
		final double dialogHeight = dialogPane.prefHeight(dialogWidth);

		x = owner.getX() + (ownerScene.getWidth() / 2.0) - (dialogWidth / 2.0);
		y = owner.getY() + titleBarHeight / 2.0 + (ownerScene.getHeight() / 2.0) - (dialogHeight / 2.0);

		prefX = x;
		prefY = y;

		setX(x);
		setY(y);
	}
}
