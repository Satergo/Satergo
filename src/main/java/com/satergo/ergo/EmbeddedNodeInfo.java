package com.satergo.ergo;

import com.grack.nanojson.*;
import org.ergoplatform.appkit.NetworkType;

import java.util.Collections;
import java.util.List;

/**
 * @param autoUpdate Added in 1.5.0. Missing means false.
 * @param vmArguments Added in 1.6.0. Missing means empty list.
 */
public record EmbeddedNodeInfo(NetworkType networkType, String jarFileName, EmbeddedNode.LogLevel logLevel, String confFileName, boolean autoUpdate, List<String> vmArguments) {

	public static final String FILE_NAME = "node-info.json";

	public EmbeddedNodeInfo(NetworkType networkType, String jarFileName, EmbeddedNode.LogLevel logLevel, String confFileName) {
		this(networkType, jarFileName, logLevel, confFileName, false, Collections.emptyList());
	}

	public EmbeddedNodeInfo withLogLevel(EmbeddedNode.LogLevel logLevel) {
		return new EmbeddedNodeInfo(this.networkType, this.jarFileName, logLevel, this.confFileName, this.autoUpdate, this.vmArguments);
	}

	public EmbeddedNodeInfo withJarFileName(String jarFileName) {
		return new EmbeddedNodeInfo(this.networkType, jarFileName, this.logLevel, this.confFileName, this.autoUpdate, this.vmArguments);
	}

	public EmbeddedNodeInfo withAutoUpdate(boolean autoUpdate) {
		return new EmbeddedNodeInfo(this.networkType, this.jarFileName, this.logLevel, this.confFileName, autoUpdate, this.vmArguments);
	}

	public EmbeddedNodeInfo withVMArguments(List<String> vmArguments) {
		return new EmbeddedNodeInfo(this.networkType, this.jarFileName, this.logLevel, this.confFileName, this.autoUpdate, vmArguments);
	}

	public String toJson() {
		return JsonWriter.string().object()
				.value("networkType", networkType.name())
				.value("jarFileName", jarFileName)
				.value("logLevel", logLevel.name())
				.value("confFileName", confFileName)
				.value("autoUpdate", autoUpdate)
				.value("vmArguments", vmArguments)
				.end().done();
	}

	public static EmbeddedNodeInfo fromJson(String json) {
		try {
			JsonObject jo = JsonParser.object().from(json);
			return new EmbeddedNodeInfo(
					NetworkType.valueOf(jo.getString("networkType")),
					jo.getString("jarFileName"),
					EmbeddedNode.LogLevel.valueOf(jo.getString("logLevel")),
					jo.getString("confFileName"),
					jo.getBoolean("autoUpdate", false),
					jo.getArray("vmArguments", new JsonArray()).stream().map(v -> (String) v).toList());
		} catch (JsonParserException e) {
			throw new RuntimeException(e);
		}
	}
}
