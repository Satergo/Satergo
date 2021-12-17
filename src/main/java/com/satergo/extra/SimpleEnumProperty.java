package com.satergo.extra;

import javafx.beans.property.SimpleObjectProperty;

public class SimpleEnumProperty<T extends Enum<T>> extends SimpleObjectProperty<T> {

	public final Class<T> enumClass;

	public SimpleEnumProperty(Class<T> enumClass) {
		this.enumClass = enumClass;
	}

	public SimpleEnumProperty(Class<T> enumClass, T initialValue) {
		super(initialValue);
		this.enumClass = enumClass;
	}

	public SimpleEnumProperty(Class<T> enumClass, Object bean, String name) {
		super(bean, name);
		this.enumClass = enumClass;
	}

	public SimpleEnumProperty(Class<T> enumClass, Object bean, String name, T initialValue) {
		super(bean, name, initialValue);
		this.enumClass = enumClass;
	}
}
