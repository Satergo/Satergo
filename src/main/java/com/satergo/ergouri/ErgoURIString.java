package com.satergo.ergouri;

import org.ergoplatform.appkit.ErgoId;

import java.math.BigDecimal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ErgoURIString {

	private static final Pattern PATTERN = Pattern.compile("^ergo:([a-z\\d]+)(?:\\?(.*))?$", Pattern.CASE_INSENSITIVE);

	public final String address;
	// nullable
	public final BigDecimal amount;
	public final Map<ErgoId, BigDecimal> tokens;

	/**
	 * @param address Receiving address
	 * @param amount Amount requested (can be changed by sender, can be null)
	 * @param tokens Tokens requested
	 */
	public ErgoURIString(String address, BigDecimal amount, Map<ErgoId, BigDecimal> tokens) {
		this.address = Objects.requireNonNull(address, "address");
		if (amount != null && amount.compareTo(BigDecimal.ZERO) < 0)
			throw new IllegalArgumentException("negative amount");
		this.amount = amount;
		this.tokens = Collections.unmodifiableMap(tokens);
	}

	/**
	 * @param address Receiving address
	 * @param amount Amount requested (can be changed by sender, can be null)
	 */
	public ErgoURIString(String address, BigDecimal amount) {
		this(address, amount, Collections.emptyMap());
	}

	/**
	 * Parses a string of the format "ergo:address?amount=x", where ?amount=x is not required
	 */
	public static ErgoURIString parse(String string) {
		Matcher matcher = PATTERN.matcher(string);
		if (!matcher.matches()) throw new IllegalArgumentException("not an ergo URI");
		String address = matcher.group(1);
		BigDecimal amount = null;
		LinkedHashMap<ErgoId, BigDecimal> tokens = new LinkedHashMap<>();
		if (matcher.group(2) != null) {
			String queryParams = matcher.group(2);
			String[] params = queryParams.split("&");
			for (String param : params) {
				String[] pair = param.split("=");
				String key = URLDecoder.decode(pair[0], StandardCharsets.UTF_8),
						value = URLDecoder.decode(pair[1], StandardCharsets.UTF_8);
				if (key.equalsIgnoreCase("amount")) {
					amount = new BigDecimal(value);
				} else if (key.startsWith("token-")) {
					ErgoId tokenId = ErgoId.create(key.substring(6));
					tokens.put(tokenId, new BigDecimal(value));
				}
			}
		}
		return new ErgoURIString(address, amount, tokens);
	}

	/**
	 * Renders the object as an ergo URI
	 */
	@Override
	public String toString() {
		String uri = "ergo:" + address;
		if (amount != null) uri += "?amount=" + amount;
		if (!tokens.isEmpty()) {
			uri += amount == null ? "?" : "&";
			uri += tokens.entrySet().stream().map(t -> "token-" + t.getKey() + "=" + t.getValue()).collect(Collectors.joining("&"));
		}
		return uri;
	}
}
