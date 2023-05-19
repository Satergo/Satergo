package com.satergo;

import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;
import javafx.util.StringConverter;

import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

public class Translations {

	private final String baseName;
	private final List<Entry> entries;

	private Locale locale;
	private ResourceBundle resourceBundle;

	public record Entry(String code, String name, String credit) {
		public static final StringConverter<Entry> TO_NAME_CONVERTER = new StringConverter<>() {
			@Override
			public String toString(Entry object) {
				return object.name();
			}

			@Override
			public Entry fromString(String string) {
				return null;
			}
		};

		public Locale locale() {
			return Locale.forLanguageTag(code);
		}
	}

	public Translations(String baseName) {
		this.baseName = baseName;
		try {
			JsonObject jo = JsonParser.object().from(Utils.resourceStringUTF8("/lang/index.json"));
			entries = jo.entrySet().stream().map(e -> new Entry(e.getKey(), ((JsonObject) e.getValue()).getString("name"), ((JsonObject) e.getValue()).getString("credit"))).toList();
		} catch (JsonParserException e) {
			throw new RuntimeException(e);
		}
	}

	public Entry getEntry(String code) {
		return entries.stream().filter(e -> e.code.equals(code)).findAny().orElseThrow();
	}

	public List<Entry> getEntries() {
		return entries;
	}

	public List<Locale> supportedLocales() {
		return entries.stream().map(Entry::locale).toList();
	}

	public void setLocale(Locale locale) {
		this.locale = locale;
		resourceBundle = ResourceBundle.getBundle(baseName);
		FormatNumber.update();
	}

	public Locale getLocale() {
		return locale;
	}

	public ResourceBundle getBundle() {
		if (resourceBundle == null) throw new NullPointerException();
		return resourceBundle;
	}

	public Entry getEntry() {
		return entries.stream().filter(e -> e.locale().equals(locale)).findAny().orElseThrow();
	}

	public String getString(String key) {
		return getBundle().getString(key);
	}
}
