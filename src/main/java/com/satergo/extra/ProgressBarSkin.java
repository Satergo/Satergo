package com.satergo.extra;

import javafx.css.PseudoClass;
import javafx.scene.control.ProgressBar;

/**
 * Adds a :zero pseudo-class to progress bars.
 */
@SuppressWarnings("unused")
public class ProgressBarSkin extends javafx.scene.control.skin.ProgressBarSkin {

	private static final PseudoClass ZERO = PseudoClass.getPseudoClass("zero");

	public ProgressBarSkin(ProgressBar control) {
		super(control);
		getNode().pseudoClassStateChanged(ZERO, control.getProgress() == 0);
		control.progressProperty().addListener((observable, old, value) ->
				getNode().pseudoClassStateChanged(ZERO, (double) value == 0));
	}
}
