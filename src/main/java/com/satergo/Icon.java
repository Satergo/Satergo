package com.satergo;

import javafx.beans.NamedArg;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;

import java.util.ResourceBundle;

public class Icon extends Region {

	public static final ObjectProperty<Color> defaultColor = new SimpleObjectProperty<>();
	public static double defaultHeight;
	public static ResourceBundle icons;

	private final ObjectProperty<Color> fill = new SimpleObjectProperty<>();
	public ObjectProperty<Color> fillProperty() { return fill; }
	public void setFill(Color fill) { fillProperty().set(fill); }
	public Color getFill() { return fillProperty().get(); }

	public Icon(@NamedArg(value="icon") String icon, @NamedArg("height") double height) {
		ObjectProperty<Color> internalColor = new SimpleObjectProperty<>();
		internalColor.addListener((observable, oldValue, newValue) -> {
			setBackground(new Background(new BackgroundFill(newValue, null, null)));
		});
		internalColor.bind(Bindings.when(fill.isNull()).then(defaultColor).otherwise(fill));

		SVGPath svgPath = new SVGPath();
		svgPath.setContent(icons.getString(icon));
		setShape(svgPath);
		setPrefWidth(svgPath.getLayoutBounds().getWidth() / svgPath.getLayoutBounds().getHeight() * height);
		setPrefHeight(height);
	}

	public Icon(@NamedArg(value="icon") String icon) {
		this(icon, defaultHeight);
	}
}
