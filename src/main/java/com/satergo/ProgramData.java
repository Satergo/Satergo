package com.satergo;

import com.satergo.extra.CommonCurrency;
import com.satergo.extra.PriceSource;
import com.satergo.extra.SimpleEnumProperty;
import com.satergo.extra.SimplePathProperty;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;
import com.grack.nanojson.JsonWriter;
import javafx.beans.property.*;
import javafx.beans.value.ObservableValue;
import org.ergoplatform.appkit.NetworkType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class ProgramData {

	private final Path path;

	public enum BlockchainNodeKind { EMBEDDED_FULL_NODE, REMOTE_NODE }

	private final long formatVersion = 0;

	// data
	public final SimpleEnumProperty<BlockchainNodeKind>
			blockchainNodeKind = new SimpleEnumProperty<>(BlockchainNodeKind.class, null, "blockchainNodeKind", null);
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

	// settings
	public final SimpleStringProperty
			language = new SimpleStringProperty(null, "language", "eng");
	public final SimpleBooleanProperty
			showPrice = new SimpleBooleanProperty(null, "showPrice", true);
	public final SimpleEnumProperty<PriceSource>
			priceSource = new SimpleEnumProperty<>(PriceSource.class, null, "priceSource", PriceSource.values()[0]);
	public final SimpleEnumProperty<CommonCurrency>
			priceCurrency = new SimpleEnumProperty<>(CommonCurrency.class, null, "priceCurrency", CommonCurrency.values()[0]);
	public final SimpleBooleanProperty
			lightTheme = new SimpleBooleanProperty(null, "lightTheme", false),
			requirePasswordForSending = new SimpleBooleanProperty(null, "requirePasswordForSending", true);

	private final List<ObservableValue<?>> allSettings = List.of(
			blockchainNodeKind, nodeAddress, lastWallet, embeddedNodeInfo, nodeNetworkType, nodeLogAutoScroll, skippedUpdate,
			language, showPrice, priceSource, priceCurrency, lightTheme, requirePasswordForSending);

	public ProgramData(Path path) {
		this.path = path;
		// auto-save
		allSettings.forEach(s -> s.addListener((observable, oldValue, newValue) -> save()));
	}

	private void save() {
		JsonObject jo = new JsonObject();
		jo.put("formatVersion", formatVersion);
		allSettings.forEach(setting -> {
			String name = ((ReadOnlyProperty<?>) setting).getName();
			if (setting instanceof SimpleEnumProperty || setting instanceof SimplePathProperty)
				jo.put(name, setting.getValue() == null ? null : String.valueOf(setting.getValue()));
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
			ProgramData programData = new ProgramData(path);
			programData.allSettings.forEach(setting -> {
				String k = ((ReadOnlyProperty<?>) setting).getName();
				try {
					if (setting instanceof SimpleEnumProperty s)
						s.set(jo.isNull(k) ? null : Enum.valueOf(s.enumClass, jo.getString(k)));
					else if (setting instanceof SimpleStringProperty s) s.set(jo.getString(k));
					else if (setting instanceof SimpleBooleanProperty s) s.set(jo.getBoolean(k));
					else if (setting instanceof SimplePathProperty s) s.set(jo.isNull(k) ? null : Path.of(jo.getString(k)));
					else if (setting instanceof SimpleLongProperty s) s.set(jo.getLong(k));
					else throw new IllegalArgumentException("type mismatch");
				} catch (Exception e) {
					System.err.println("ProgramData field \"" + k + "\" is corrupted. (value=" + jo.get(k) + ")");
				}
			});
			return programData;
		} catch (IOException | JsonParserException e) {
			throw new RuntimeException(e);
		}
	}
}
