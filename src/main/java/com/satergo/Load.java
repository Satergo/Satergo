package com.satergo;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.image.Image;
import javafx.util.Pair;

import java.io.IOException;
import java.util.*;

/**
 * Utility class for loading FXML layouts with a default bundle
 */
public class Load {

	private Load() {}

	public static ResourceBundle resourceBundle;

	public static <T extends Parent>T fxml(String location) {
		try {
			return FXMLLoader.load(Utils.resource(location), resourceBundle);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public static Parent fxmlControllerFactory(String location, Object controller) {
		FXMLLoader fxmlLoader = new FXMLLoader(Utils.resource(location), resourceBundle);
		fxmlLoader.setControllerFactory(cls -> controller);
		try {
			return fxmlLoader.load();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public static <T extends Parent, CT>Pair<T, CT> fxmlNodeAndController(String location) {
		FXMLLoader fxmlLoader = new FXMLLoader(Utils.resource(location), resourceBundle);
		try {
			return new Pair<>(fxmlLoader.load(), fxmlLoader.getController());
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public static void thisFxml(Parent thisObject, String location) {
		FXMLLoader fxmlLoader = new FXMLLoader(Utils.resource(location), resourceBundle);
		fxmlLoader.setRoot(thisObject);
		fxmlLoader.setController(thisObject);
		try {
			fxmlLoader.load();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public static Image image(String location) {
		return new Image(Utils.resourcePath(location));
	}
}
