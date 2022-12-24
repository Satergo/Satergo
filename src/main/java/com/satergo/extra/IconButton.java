package com.satergo.extra;

import com.satergo.Icon;
import javafx.beans.NamedArg;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Tooltip;

public class IconButton extends Button {

	private IconButton() {
		getStyleClass().addAll("borderless", "less-wide", "iconbutton");
		setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
	}

	public IconButton(@NamedArg("icon") String icon) {
		this(new Icon(icon));
	}

	public IconButton(@NamedArg("icon") String icon, @NamedArg("tooltip") String tooltip) {
		this(new Icon(icon));
		setTooltip(new Tooltip(tooltip));
	}

	public IconButton(@NamedArg("graphic") Icon graphic) {
		this();
		setGraphic(graphic);
	}
}
