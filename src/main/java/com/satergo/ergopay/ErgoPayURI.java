package com.satergo.ergopay;

import org.ergoplatform.appkit.Address;

import java.net.URI;
import java.util.Base64;
import java.util.Objects;

public final class ErgoPayURI {
	private final String uri;

	/**
	 * @param uri Unfortunately, the substring #P2PK_ADDRESS# was picked for the address placeholder in ergopay URIs, but that is an invalid substring for a URI to contain so String must be taken.
	 */
	public ErgoPayURI(String uri) {
		if (!uri.startsWith("ergopay:"))
			throw new IllegalArgumentException("not an ergopay URI");
		this.uri = uri;
	}

	public enum Type {
		SIGN_REQUEST, TRANSACT_REQUEST
	}

	public String getHost() {
		if (!needsNetworkRequest()) throw new IllegalStateException("this URI does not need a network request");
		int schemePart = "ergopay://".length();
		int slash = uri.indexOf('/', schemePart), qMark = uri.indexOf('?', schemePart);
		String authority = uri.substring(schemePart, slash == -1 ? qMark : qMark != -1 ? Math.min(slash, qMark) : slash);
		int colon = authority.lastIndexOf(':');
		if (colon == -1) return authority;
		return authority.substring(0, colon);
	}

	public boolean needsAddress() {
		return needsNetworkRequest() && uri.contains("#P2PK_ADDRESS#");
	}

	public ErgoPayURI withAddress(Address address) {
		if (!needsAddress()) throw new IllegalStateException("this URI does not need an address");
		return new ErgoPayURI(uri.replace("#P2PK_ADDRESS#", address.toString()));
	}

	public URI finish(String protocol) {
		if (!needsNetworkRequest()) throw new IllegalStateException("this URI does not need a network request");
		if (needsAddress()) throw new IllegalStateException("this URI needs an address");
		return URI.create(protocol + uri.substring("ergopay".length()));
	}

	public boolean needsNetworkRequest() {
		return uri.startsWith("ergopay://");
	}

	/**
	 * For URIs that contain the reduced transaction (no network request)
	 */
	public byte[] reducedTxBytes() {
		if (needsNetworkRequest()) throw new IllegalStateException("this URI needs a network request");
		return Base64.getUrlDecoder().decode(uri.substring("ergopay:".length()));
	}

	@Override
	public String toString() {
		return uri;
	}

	@Override
	public boolean equals(Object o) {
		return this == o || o instanceof ErgoPayURI other && uri.equals(other.uri);
	}

	@Override
	public int hashCode() {
		return Objects.hash(uri);
	}
}
