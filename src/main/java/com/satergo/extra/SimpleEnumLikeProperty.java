package com.satergo.extra;

import javafx.beans.property.SimpleObjectProperty;

public abstract class SimpleEnumLikeProperty<T> extends SimpleObjectProperty<T> {

	public SimpleEnumLikeProperty() {}

	public SimpleEnumLikeProperty(T initialValue) {
		super(initialValue);
	}

	public SimpleEnumLikeProperty(Object bean, String name) {
		super(bean, name);
	}

	public SimpleEnumLikeProperty(Object bean, String name, T initialValue) {
		super(bean, name, initialValue);
	}

	public abstract T valueOf(String s);

	public abstract String nameOf(T value);
}
