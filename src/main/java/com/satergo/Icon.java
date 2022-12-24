package com.satergo;

import javafx.beans.NamedArg;
import javafx.beans.binding.Bindings;
import javafx.css.*;
import javafx.css.converter.PaintConverter;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.Region;
import javafx.scene.paint.Paint;
import javafx.scene.shape.SVGPath;

import java.util.List;
import java.util.ResourceBundle;

public class Icon extends Region {

	public static double defaultHeight;
	public static ResourceBundle icons;

	private final StyleablePropertyFactory<Icon> PROPERTY_FACTORY = new StyleablePropertyFactory<>(getCssMetaData());

	private final StyleableObjectProperty<Paint> fill = (StyleableObjectProperty<Paint>) PROPERTY_FACTORY.createStyleablePaintProperty(this, "fill", "-fill", i -> i.fill);
	public StyleableObjectProperty<Paint> fillProperty() { return fill; }
	public void setFill(Paint fill) { fillProperty().set(fill); }
	public Paint getFill() { return fillProperty().get(); }

	public Icon(@NamedArg("icon") String icon, @NamedArg("height") double height) {
		getStyleClass().add("icon");

		backgroundProperty().bind(Bindings.createObjectBinding(() ->
				new Background(new BackgroundFill(getFill(), null, null)), fill));

		SVGPath svgPath = new SVGPath();
		svgPath.setContent(icons.getString(icon));
		setShape(svgPath);
		setPrefWidth((svgPath.getLayoutBounds().getWidth() / svgPath.getLayoutBounds().getHeight()) * height);
		setPrefHeight(height);
	}

	public Icon(@NamedArg("icon") String icon) {
		this(icon, defaultHeight);
	}

	private static final CssMetaData<Icon, Paint> FILL =
			new CssMetaData<>("-fill",
					PaintConverter.getInstance()) {

				@Override
				public boolean isSettable(Icon icon) {
					return !icon.fill.isBound();
				}

				@Override
				public StyleableProperty<Paint> getStyleableProperty(Icon icon) {
					return icon.fill;
				}
			};

	private static final List<CssMetaData<? extends Styleable, ?>> STYLEABLES = List.of(FILL);

	@Override
	public List<CssMetaData<? extends Styleable, ?>> getCssMetaData() {
		return STYLEABLES;
	}
}
