package com.satergo.controller.ledger;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

public class BIP32Path {
	private static final long HARDENED = 0x80000000L;
	private static final Pattern PATH_ENTRY_PATTERN = Pattern.compile("(\\d+)([hH'])?");

	private final long[] path;

	private BIP32Path(long[] path) {
		this.path = path.clone();
		if (path.length < 1) throw new IllegalArgumentException();
	}

	public static BIP32Path fromPathArray(long... path) {
		return new BIP32Path(path);
	}

	public static BIP32Path fromString(String s) {
		if (s.startsWith("m/")) s = s.substring(2);
		else throw new IllegalArgumentException("root element required");
		String[] path = s.split("/");
		long[] ret = new long[path.length];
		for (int i = 0; i < path.length; i++) {
			Matcher m = PATH_ENTRY_PATTERN.matcher(path[i]);
			if (!m.matches()) throw new IllegalArgumentException("invalid input");
			ret[i] = Long.parseLong(m.group(1));
			if (m.groupCount() == 2) ret[i] += HARDENED;
		}
		return new BIP32Path(ret);
	}

	public long[] toPathArray() {
		return path.clone();
	}

	@Override
	public String toString() {
		return "m/" + LongStream.of(path)
				.mapToObj(i -> ((i & HARDENED) != 0L) ? (i & ~HARDENED) + "'" : String.valueOf(i))
				.collect(Collectors.joining("/"));
	}
}
