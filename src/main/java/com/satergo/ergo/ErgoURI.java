package com.satergo.ergo;

import org.ergoplatform.sdk.ErgoId;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class ErgoURI {

	public final String address;
	// nullable
	public final BigDecimal amount;
	public final Map<ErgoId, BigDecimal> tokens;
	// nullable
	public final String description;

	/**
	 * @param address Receiving address
	 * @param amount Amount requested (can be changed by sender, can be null)
	 * @param tokens Tokens requested
	 */
	public ErgoURI(String address, BigDecimal amount, Map<ErgoId, BigDecimal> tokens, String description) {
		this.address = Objects.requireNonNull(address, "address");
		if (amount != null && amount.compareTo(BigDecimal.ZERO) < 0)
			throw new IllegalArgumentException("negative amount");
		this.amount = amount;
		this.tokens = Collections.unmodifiableMap(tokens);
		this.description = description;
	}

	/**
	 * @param address Receiving address
	 * @param amount Amount requested (can be changed by sender, can be null)
	 */
	public ErgoURI(String address, BigDecimal amount) {
		this(address, amount, Collections.emptyMap(), null);
	}

	/**
	 * Parses an ergo URI like "ergo:address?amount=x"
	 */
	public static ErgoURI parse(URI uri) {
		if (!"ergo".equals(uri.getScheme()))
			throw new IllegalArgumentException("not an ergo URI");
		String content = uri.getRawSchemeSpecificPart();
		int qMark = content.indexOf('?');
		String address = content.substring(0, qMark == -1 ? content.length() : qMark);
		BigDecimal amount = null;
		LinkedHashMap<ErgoId, BigDecimal> tokens = new LinkedHashMap<>();
		String description = null;
		if (qMark != -1) {
			String queryParams = content.substring(qMark);
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
				} else if (key.equals("description")) {
					description = value;
				}
			}
		}
		return new ErgoURI(address, amount, tokens, description);
	}

	/**
	 * Renders the object as an ergo URI
	 */
	@Override
	public String toString() {
		ArrayList<String> params = new ArrayList<>();
		if (amount != null) params.add("amount=" + amount);
		if (!tokens.isEmpty()) {
			tokens.forEach((id, value) -> params.add("token-" + id + "=" + value));
		}
		if (description != null) {
			params.add("description=" + URLEncoder.encode(description, StandardCharsets.UTF_8)
							.replace("+", "%20"));
		}
		return "ergo:" + address + (params.isEmpty() ? "" : "?" + String.join("&", params));
	}
}
