package com.satergo.extra;

import com.satergo.Main;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Optional;

public class VMArguments {

	public final ObservableList<String> arguments = FXCollections.observableArrayList();

	public VMArguments(List<String> arguments) {
		this.arguments.addAll(arguments);
	}

	public VMArguments() {
	}

	public Optional<String> getMaxRam() {
		return arguments.stream().filter(a -> a.startsWith("-Xmx")).findAny().map(a -> a.substring(4));
	}

	public void setMaxRam(String value) {
		Iterator<Integer> iter = null;
		for (ListIterator<String> iterator = arguments.listIterator(); iterator.hasNext();) {
			if (iterator.next().startsWith("-Xmx")) {
				if (value != null && !value.isBlank())
					iterator.set("-Xmx" + value);
				else iterator.remove();
				return;
			}
		}
		if (value != null && !value.isBlank())
			arguments.add("-Xmx" + value);
	}

	public void validate(long totalSystemRam) throws IllegalArgumentException {
		if (getMaxRam().isPresent()) {
			String r = getMaxRam().get();
			long bytes;
			try {
				if (r.endsWith("G") || r.endsWith("g")) {
					bytes = (long) (Integer.parseInt(r.substring(0, r.length() - 1)) * Math.pow(1024, 3));
				} else if (r.endsWith("M") || r.endsWith("m")) {
					bytes = (long) (Integer.parseInt(r.substring(0, r.length() - 1)) * Math.pow(1024, 2));
				} else throw new IllegalArgumentException(Main.lang("maxRamValueSuffix"));
				if (bytes > totalSystemRam) {
					throw new IllegalArgumentException(Main.lang("maxRamExceedsSystem"));
				}
			} catch (NumberFormatException e) {
				throw new IllegalArgumentException(Main.lang("couldNotParseRamInteger"));
			}
		}
	}

	@Override
	public String toString() {
		return String.join(" ", arguments);
	}
}
