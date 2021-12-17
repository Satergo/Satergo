package com.satergo.extra;

import javafx.beans.property.SimpleObjectProperty;

import java.nio.file.Path;

public class SimplePathProperty extends SimpleObjectProperty<Path> {

	public SimplePathProperty() {
	}

	public SimplePathProperty(Path initialValue) {
		super(initialValue);
	}

	public SimplePathProperty(Object bean, String name) {
		super(bean, name);
	}

	public SimplePathProperty(Object bean, String name, Path initialValue) {
		super(bean, name, initialValue);
	}
}
