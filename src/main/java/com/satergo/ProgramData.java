package com.satergo;

import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;
import com.grack.nanojson.JsonWriter;
import com.satergo.extra.*;
import com.satergo.extra.market.PriceCurrency;
import com.satergo.extra.market.PriceSource;
import javafx.beans.property.ReadOnlyProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ObservableValue;
import org.ergoplatform.appkit.NetworkType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class ProgramData {

	private static final String DEFAULT_LANGUAGE = "en";
	private final Path path;

	public enum NodeKind {
		EMBEDDED_FULL_NODE(true), EMBEDDED_LIGHT_NODE(true), REMOTE_NODE(false);
		public final boolean embedded;
		NodeKind(boolean embedded) {
			this.embedded = embedded;
		}
	}

	private final long formatVersion = 1;

	// data
	public final SimpleEnumProperty<NodeKind>
			blockchainNodeKind = new SimpleEnumProperty<>(NodeKind.class, null, "blockchainNodeKind", null);
	public final SimpleStringProperty
			nodeAddress = new SimpleStringProperty(null, "nodeAddress", null);
	public final SimplePathProperty
			lastWallet = new SimplePathProperty(null, "lastWallet", null),
			embeddedNodeInfo = new SimplePathProperty(null, "embeddedNodeInfo", null);
	public final SimpleEnumProperty<NetworkType>
			nodeNetworkType = new SimpleEnumProperty<>(NetworkType.class, null, "nodeNetworkType", NetworkType.MAINNET);
	public final SimpleBooleanProperty
			nodeLogAutoScroll = new SimpleBooleanProperty(null, "nodeLogAutoScroll", true);
	public final SimpleLongProperty
			skippedUpdate = new SimpleLongProperty(null, "skippedUpdate", -1);
	/** @since 1.7.0 */
	public final SimplePathProperty
			lastWalletDirectory = new SimplePathProperty(null, "lastWalletDirectory", null);

	// settings
	public final SimpleStringProperty
			language = new SimpleStringProperty(null, "language", DEFAULT_LANGUAGE);
	public final SimpleBooleanProperty
			showPrice = new SimpleBooleanProperty(null, "showPrice", true);
	public final SimpleEnumProperty<PriceSource>
			priceSource = new SimpleEnumProperty<>(PriceSource.class, null, "priceSource", PriceSource.DEFAULT);
	public final SimpleEnumLikeProperty<PriceCurrency>
			priceCurrency = new SimpleEnumLikeProperty<>(null, "priceCurrency", PriceCurrency.USD) {
		@Override public PriceCurrency valueOf(String s) { return PriceCurrency.get(s); }
		@Override public String nameOf(PriceCurrency value) { return value.uc(); }
	};
	public final SimpleBooleanProperty
			lightTheme = new SimpleBooleanProperty(null, "lightTheme", false),
			requirePasswordForSending = new SimpleBooleanProperty(null, "requirePasswordForSending", true);

	private final List<ObservableValue<?>> allSettings = List.of(
			blockchainNodeKind, nodeAddress, lastWallet, embeddedNodeInfo, nodeNetworkType, nodeLogAutoScroll, skippedUpdate,
			language, showPrice, priceSource, priceCurrency, lightTheme, requirePasswordForSending, lastWalletDirectory);

	public ProgramData(Path path) {
		this.path = path;
		// auto-save
		allSettings.forEach(s -> s.addListener((observable, oldValue, newValue) -> save()));
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private void save() {
		JsonObject jo = new JsonObject();
		jo.put("formatVersion", formatVersion);
		allSettings.forEach(setting -> {
			String name = ((ReadOnlyProperty<?>) setting).getName();
			if (setting instanceof SimplePathProperty)
				jo.put(name, setting.getValue() == null ? null : String.valueOf(setting.getValue()));
			else if (setting instanceof SimpleEnumLikeProperty s)
				jo.put(name, setting.getValue() == null ? null : s.nameOf(setting.getValue()));
			else jo.put(name, setting.getValue());
		});
		try {
			Files.writeString(path, JsonWriter.string(jo));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	public static ProgramData load(Path path) {
		try {
			JsonObject jo = JsonParser.object().from(Files.readString(path));
			long loadedVersion = jo.getLong("formatVersion");
			ProgramData programData = new ProgramData(path);
			programData.allSettings.forEach(setting -> {
				String k = ((ReadOnlyProperty<?>) setting).getName();
				try {
					switch (setting) {
						case SimpleEnumLikeProperty s -> s.set(jo.isNull(k) ? null : s.valueOf(jo.getString(k)));
						case SimpleStringProperty s -> s.set(jo.getString(k));
						case SimpleBooleanProperty s -> s.set(jo.getBoolean(k));
						case SimplePathProperty s -> s.set(jo.isNull(k) ? null : Path.of(jo.getString(k)));
						case SimpleLongProperty s -> s.set(jo.getLong(k));
						default -> throw new IllegalArgumentException("type mismatch");
					}
				} catch (Exception e) {
					System.err.println("ProgramData field \"" + k + "\" is corrupted. (value=" + jo.get(k) + ")");
				}
			});
			// Version 0 migration:
			// It used ISO-639 alpha-3 codes for all languages, but that causes issues with date/time and number formatters because
			// they expect alpha-2 codes and fall back to default English locale when given an alpha-3 code for a language that has an alpha-2 code
			if (loadedVersion == 0) programData.language.set(DEFAULT_LANGUAGE);
			return programData;
		} catch (IOException | JsonParserException e) {
			throw new RuntimeException(e);
		}
	}
}
