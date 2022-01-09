package com.satergo.controller;

import javafx.scene.Parent;

public interface SetupPage {

	interface CustomLeft {
		boolean hasLeft();
		void left();
	}

	interface WithoutLanguage extends SetupPage {
		default Parent recreate() { return null; }
		default boolean showLanguageSelector() { return false; }
	}

	interface WithLanguage extends SetupPage {
		default boolean showLanguageSelector() { return true; }
	}

	Parent recreate();
	Parent content();
	boolean showLanguageSelector();
}
