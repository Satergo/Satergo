package com.satergo.extra;

import javafx.concurrent.Task;

import java.util.concurrent.Callable;
import java.util.function.Consumer;

public class SimpleTask<T> extends Task<T> {

	private final Callable<T> callable;

	public SimpleTask(Callable<T> callable) {
		this.callable = callable;
	}

	public SimpleTask<T> onSuccess(Consumer<T> consumer) {
		setOnSucceeded(e -> consumer.accept(getValue()));
		return this;
	}

	public SimpleTask<T> onFail(Consumer<Throwable> consumer) {
		setOnFailed(e -> consumer.accept(getException()));
		return this;
	}

	public SimpleTask<T> onRunning(Runnable runnable) {
		setOnRunning(e -> runnable.run());
		return this;
	}

	@Override
	protected T call() throws Exception {
		return callable.call();
	}

	public void newThread() {
		new Thread(this).start();
	}
}
