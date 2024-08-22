package com.satergo;

import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;
import com.satergo.extra.dialog.SatPromptDialog;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;

import static com.satergo.Utils.HTTP;

public class UpdateChecker {

	private static final URI latestVersionInfoURI = URI.create("https://satergo.com/latest.json");

	public record VersionInfo(String version, long versionCode, LocalDate dateReleased, String changelog) {}

	public static VersionInfo fetchLatestInfo() throws IOException {
		HttpRequest request = Utils.httpRequestBuilder().uri(latestVersionInfoURI).build();
		try {
			JsonObject body = JsonParser.object().from(HTTP.send(request, HttpResponse.BodyHandlers.ofString()).body());
			return new VersionInfo(body.getString("version"), body.getLong("versionCode"), LocalDate.parse(body.getString("dateReleased")), body.getString("changelog"));
		} catch (JsonParserException | InterruptedException e) {
			throw new RuntimeException(e);
		}
	}

	public static boolean isNewer(long versionCode) {
		return Main.VERSION_CODE < versionCode;
	}

	public static void showUpdatePopup(VersionInfo versionInfo) {
		if (!isNewer(versionInfo.versionCode)) throw new IllegalArgumentException();
		if (Main.programData().skippedUpdate.get() == versionInfo.versionCode) return;
		SatPromptDialog<ButtonType> dialog = new SatPromptDialog<>();
		Utils.initDialog(dialog, Main.get().stage());
		ButtonType update = new ButtonType(Main.lang("update"), ButtonBar.ButtonData.YES);
		ButtonType skip = new ButtonType(Main.lang("skip"), ButtonBar.ButtonData.NO);
		ButtonType notNow = new ButtonType(Main.lang("notNow"), ButtonBar.ButtonData.CANCEL_CLOSE);
		dialog.getDialogPane().getButtonTypes().addAll(update, skip, notNow);
		dialog.setHeaderText(Main.lang("aNewUpdateHasBeenFound"));
		Label label = new Label(Main.lang("updateDescription").formatted(
				versionInfo.version,
				versionInfo.dateReleased.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)),
				versionInfo.changelog));
		label.setWrapText(true);
		dialog.getDialogPane().setContent(label);
		dialog.showForResult().ifPresent(t -> {
			if (t == update) {
				// TODO implement updating & verifying functionality into the program
				Utils.showDocument("https://satergo.com/#downloads");
			} else if (t == skip) {
				Main.programData().skippedUpdate.set(versionInfo.versionCode);
			}
		});
	}
}
