package com.satergo.extra;

import com.satergo.ergo.EmbeddedFullNode;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;
import com.grack.nanojson.JsonWriter;
import org.ergoplatform.appkit.NetworkType;

public record EmbeddedNodeInfo(NetworkType networkType, String jarFileName, EmbeddedFullNode.LogLevel logLevel, String confFileName) {

	public static final String FILE_NAME = "node-info.json";

	public EmbeddedNodeInfo withLogLevel(EmbeddedFullNode.LogLevel logLevel) {
		return new EmbeddedNodeInfo(this.networkType, this.jarFileName, logLevel, this.confFileName);
	}

	public String toJson() {
		return JsonWriter.string().object()
				.value("networkType", networkType.toString())
				.value("jarFileName", jarFileName)
				.value("logLevel", logLevel.toString())
				.value("confFileName", confFileName)
				.end().done();
	}

	public static EmbeddedNodeInfo fromJson(String json) {
		try {
			JsonObject jo = JsonParser.object().from(json);
			return new EmbeddedNodeInfo(NetworkType.valueOf(jo.getString("networkType")), jo.getString("jarFileName"), EmbeddedFullNode.LogLevel.valueOf(jo.getString("logLevel")), jo.getString("confFileName"));
		} catch (JsonParserException e) {
			throw new RuntimeException(e);
		}
	}
}
