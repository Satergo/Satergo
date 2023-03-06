package com.satergo.extra;

public class SimpleEnumProperty<T extends Enum<T>> extends SimpleEnumLikeProperty<T> {

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

	@Override
	public final T valueOf(String s) {
		return Enum.valueOf(enumClass, s);
	}

	@Override
	public final String nameOf(T value) {
		return value.name();
	}
}
