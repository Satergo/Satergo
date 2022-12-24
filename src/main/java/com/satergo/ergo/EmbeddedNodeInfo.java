package com.satergo.ergo;

import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;
import com.grack.nanojson.JsonWriter;
import org.ergoplatform.appkit.NetworkType;

/**
 * @param autoUpdate Added in 1.5.0. Missing means false.
 */
public record EmbeddedNodeInfo(NetworkType networkType, String jarFileName, EmbeddedFullNode.LogLevel logLevel, String confFileName, boolean autoUpdate) {

	public static final String FILE_NAME = "node-info.json";

	public EmbeddedNodeInfo(NetworkType networkType, String jarFileName, EmbeddedFullNode.LogLevel logLevel, String confFileName) {
		this(networkType, jarFileName, logLevel, confFileName, false);
	}

	public EmbeddedNodeInfo withLogLevel(EmbeddedFullNode.LogLevel logLevel) {
		return new EmbeddedNodeInfo(this.networkType, this.jarFileName, logLevel, this.confFileName, this.autoUpdate);
	}

	public EmbeddedNodeInfo withJarFileName(String jarFileName) {
		return new EmbeddedNodeInfo(this.networkType, jarFileName, this.logLevel, this.confFileName, this.autoUpdate);
	}

	public EmbeddedNodeInfo withAutoUpdate(boolean autoUpdate) {
		return new EmbeddedNodeInfo(this.networkType, this.jarFileName, this.logLevel, this.confFileName, autoUpdate);
	}

	public String toJson() {
		return JsonWriter.string().object()
				.value("networkType", networkType.toString())
				.value("jarFileName", jarFileName)
				.value("logLevel", logLevel.toString())
				.value("confFileName", confFileName)
				.value("autoUpdate", autoUpdate)
				.end().done();
	}

	public static EmbeddedNodeInfo fromJson(String json) {
		try {
			JsonObject jo = JsonParser.object().from(json);
			return new EmbeddedNodeInfo(
					NetworkType.valueOf(jo.getString("networkType")),
					jo.getString("jarFileName"),
					EmbeddedFullNode.LogLevel.valueOf(jo.getString("logLevel")),
					jo.getString("confFileName"),
					jo.getBoolean("autoUpdate", false));
		} catch (JsonParserException e) {
			throw new RuntimeException(e);
		}
	}
}
