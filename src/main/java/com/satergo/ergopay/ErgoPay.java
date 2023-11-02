package com.satergo.ergopay;

import com.google.gson.*;
import com.google.gson.annotations.SerializedName;
import com.satergo.Utils;
import org.ergoplatform.appkit.Address;
import org.ergoplatform.appkit.ReducedTransaction;

import java.io.IOException;
import java.lang.reflect.Type;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import java.util.function.Supplier;

import static com.satergo.Utils.HTTP;

public class ErgoPay {

	public sealed interface Request permits SignRequest, TransactRequest {
		ReducedTransaction reducedTx();
	}

	public record SignRequest(@SerializedName("reducedTx") ReducedTransaction reducedTx,
							  @SerializedName("address") String address,
							  @SerializedName("message") String message,
							  @SerializedName("messageSeverity") MessageSeverity messageSeverity,
							  @SerializedName("replyTo") String replyTo) implements Request {
		public enum MessageSeverity {
			INFORMATION, SECURITY, ERROR
		}
	}

	public record TransactRequest(ReducedTransaction reducedTx) implements Request {
	}


	private static final Gson GSON = new GsonBuilder()
			.registerTypeAdapter(Address.class, new Utils.AddressCnv())
			.registerTypeAdapter(ReducedTransaction.class, new ReducedTransactionDeser())
			.registerTypeHierarchyAdapter(byte[].class, new ByteArrayURLBase64Cnv())
			.create();

	/**
	 * This method may send a network request.
	 */
	public static Request getRequest(ErgoPayURI uri, Supplier<Address> addressSupplier) throws IOException, InterruptedException {
		if (uri.needsNetworkRequest()) {
			// Need to obtain a signing request from that URL
			InetAddress inetAddress = InetAddress.getByName(uri.getHost());
			String protocol;
			if (inetAddress.toString().startsWith("/") && (inetAddress.isLoopbackAddress() || inetAddress.isAnyLocalAddress() || inetAddress.isSiteLocalAddress())) {
				// If an IP address (not a domain) was supplied, and it is in the local network
				protocol = "http";
			} else protocol = "https";

			if (uri.needsAddress())
				uri = uri.withAddress(addressSupplier.get());

			URI target = uri.finish(protocol);

			HttpRequest request = Utils.httpRequestBuilder().uri(target).build();
			HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
			return GSON.fromJson(response.body(), SignRequest.class);
		} else {
			byte[] bytes = uri.reducedTxBytes();
			return new TransactRequest(Utils.offlineErgoClient().execute(ctx -> ctx.parseReducedTransaction(bytes)));
		}
	}

	private static class ByteArrayURLBase64Cnv implements JsonSerializer<byte[]>, JsonDeserializer<byte[]> {
		@Override public JsonElement serialize(byte[] src, Type typeOfSrc, JsonSerializationContext context) {
			return new JsonPrimitive(Base64.getUrlEncoder().encodeToString(src));
		}
		@Override public byte[] deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
			return Base64.getUrlDecoder().decode(json.getAsString());
		}
	}
	private static class ReducedTransactionDeser implements JsonDeserializer<ReducedTransaction> {
		@Override public ReducedTransaction deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
			return Utils.offlineErgoClient().execute(ctx -> ctx.parseReducedTransaction(GSON.fromJson(json, byte[].class)));
		}
	}
}
