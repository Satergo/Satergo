package com.satergo.controller;

import javafx.scene.Parent;

public interface SetupPage {

	interface CustomLeft {
		boolean hasLeft();
		void left();
	}

	interface WithoutExtra extends SetupPage {
		default Parent recreate() { return null; }
		default boolean showExtra() { return false; }
	}

	interface WithExtra extends SetupPage {
		default boolean showExtra() { return true; }
	}

	Parent recreate();
	Parent content();
	boolean showExtra();
	default void cleanup() {}
}
