package com.satergo;

import javafx.application.Application;

/**
 * Using JavaFX portable runtime folders without modularity seems to require a separate class to launch the application.
 */
public class Launcher {

	public static void main(String[] args) {
		Application.launch(Main.class, args);
	}
}
