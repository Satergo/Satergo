package com.satergo.node;

import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;
import com.grack.nanojson.JsonWriter;
import com.satergo.Utils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static com.satergo.Utils.HTTP;
import static java.net.http.HttpResponse.BodyHandlers.ofString;

public class ErgoNodeAccess {

	private final URI apiAddress;

	public ErgoNodeAccess(URI apiAddress) {
		this.apiAddress = apiAddress;
	}

	public record Status(int blockHeight, int headerHeight, int networkHeight, int peerCount) {}

	public Status getStatus() {
		HttpRequest request = Utils.httpRequestBuilder().uri(apiAddress.resolve("/info")).build();
		try {
			JsonObject o = JsonParser.object().from(HTTP.send(request, ofString()).body());
			return new Status(o.getInt("fullHeight"), o.getInt("headersHeight"), o.getInt("maxPeerHeight"), o.getInt("peersCount"));
		} catch (JsonParserException | IOException | InterruptedException e) {
			throw new RuntimeException(e);
		}
	}

	public enum UnlockingResult { INCORRECT_API_KEY, INCORRECT_PASSWORD, NOT_INITIALIZED, UNKNOWN, SUCCESS }

	public UnlockingResult unlockWallet(String apiKey, String password) {
		HttpRequest request = Utils.httpRequestBuilder().uri(apiAddress.resolve("/wallet/unlock"))
				.header("Content-Type", "application/json")
				.header("api_key", apiKey)
				.POST(HttpRequest.BodyPublishers.ofString(JsonWriter.string().object().value("pass", password).end().done())).build();
		try {
			HttpResponse<String> response = HTTP.send(request, ofString());
			if (response.statusCode() == 403) {
				return UnlockingResult.INCORRECT_API_KEY;
			} else if (response.statusCode() == 400) {
				if (JsonParser.object().from(response.body()).getString("detail").contains("not initialized")) {
					return UnlockingResult.NOT_INITIALIZED;
				} else return UnlockingResult.INCORRECT_PASSWORD;
			} else if (response.statusCode() == 200) {
				return UnlockingResult.SUCCESS;
			}
			return UnlockingResult.UNKNOWN;
		} catch (IOException | InterruptedException | JsonParserException e) {
			throw new RuntimeException(e);
		}
	}
}
