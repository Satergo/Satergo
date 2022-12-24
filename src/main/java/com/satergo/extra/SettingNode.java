package com.satergo.extra;

import javafx.beans.NamedArg;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.VPos;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;

public class SettingNode<T extends Control> extends Pane {

	private static final double HEIGHT = 70;

	private final ImageView imageView = new ImageView();
	private final Label label = new Label();

	private final SimpleObjectProperty<Image> image = new SimpleObjectProperty<>();
	private final T controlN;

	public Image getImage() { return image.get(); }
	public void setImage(Image image) { this.image.set(image); }
	public SimpleObjectProperty<Image> imageProperty() { return image; }

	private final SimpleStringProperty text = new SimpleStringProperty();
	public String getText() { return text.get(); }
	public void setText(String text) { this.text.set(text); }
	public SimpleStringProperty textProperty() { return text; }

	private final SimpleObjectProperty<T> control = new SimpleObjectProperty<>();
	public T getControl() { return control.get(); }
	public SimpleObjectProperty<T> controlProperty() { return control; }
	public void setControl(T control) { this.control.set(control); }

	public SettingNode(@NamedArg("image") Image image, @NamedArg("text") String text, @NamedArg("control") T control) {
		controlN = control;
		getStyleClass().add("setting");

		imageView.getStyleClass().add("setting-image-view");
		imageView.setPreserveRatio(true);
		imageView.setFitHeight(36);
		imageView.imageProperty().bind(this.image);
		imageView.imageProperty().addListener((observable, oldValue, newValue) -> layoutChildren());
		label.getStyleClass().add("setting-label");

		this.image.set(image);
		this.text.set(text);

		label.textProperty().bind(this.text);

		getChildren().addAll(imageView, label, control);

		setMinHeight(HEIGHT);
		setPrefHeight(HEIGHT);
		setMaxHeight(HEIGHT);
	}

	@Override
	protected void layoutChildren() {
		/*
		Explanation:
		1. Layouts the image according to the left padding. Reserves 52 pixels for it and centers it within those.
		2. Layouts the label according to the left padding plus 52 (image reserved width) plus 16 (distance from image area)
		3. Layouts the control to the right edge.
		*/
		Insets insets = getInsets();
		double verticalArea = HEIGHT - insets.getTop() - insets.getBottom();
		layoutInArea(imageView, insets.getLeft(), insets.getTop(), 52, verticalArea, 0, HPos.CENTER, VPos.CENTER);
		layoutInArea(label, insets.getLeft() + 52 + 16, insets.getTop(), label.prefWidth(-1), verticalArea, 0, HPos.LEFT, VPos.CENTER);
		layoutInArea(controlN, getWidth() - insets.getRight() - controlN.prefWidth(-1), insets.getTop(), controlN.prefWidth(-1), verticalArea, 0, HPos.RIGHT, VPos.CENTER);
	}
}
