package com.satergo;

import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class UpdateChecker {

	private static final URI latestVersionInfoURI = URI.create("https://raw.githubusercontent.com/Satergo/satergo.com/master/latest.json");

	public record VersionInfo(String version, long versionCode, LocalDate dateReleased, String changelog) {}

	public static VersionInfo fetchLatestInfo() {
		HttpRequest request = Utils.httpRequestBuilder().uri(latestVersionInfoURI).build();
		try {
			JsonObject body = JsonParser.object().from(HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString()).body());
			return new VersionInfo(body.getString("version"), body.getLong("versionCode"), LocalDate.parse(body.getString("dateReleased")), body.getString("changelog"));
		} catch (JsonParserException | IOException | InterruptedException e) {
			throw new RuntimeException(e);
		}
	}

	public static boolean isNewer(long versionCode) {
		return Main.VERSION_CODE < versionCode;
	}

	public static void showUpdatePopup(VersionInfo versionInfo) {
		if (!isNewer(versionInfo.versionCode)) throw new IllegalArgumentException();
		if (Main.programData().skippedUpdate.get() == versionInfo.versionCode) return;
		Alert alert = new Alert(Alert.AlertType.NONE);
		alert.initOwner(Main.get().stage());
		ButtonType update = new ButtonType(Main.lang("update"), ButtonBar.ButtonData.YES);
		ButtonType skip = new ButtonType(Main.lang("skip"), ButtonBar.ButtonData.NO);
		ButtonType notNow = new ButtonType(Main.lang("notNow"), ButtonBar.ButtonData.CANCEL_CLOSE);
		alert.getButtonTypes().addAll(update, skip, notNow);
		alert.setHeaderText(Main.lang("aNewUpdateHasBeenFound"));
		alert.setContentText(Main.lang("updateDescription").formatted(
				versionInfo.versionCode,
				versionInfo.dateReleased.format(DateTimeFormatter.ofPattern("MMM dd, yyyy")),
				versionInfo.changelog));
		alert.showAndWait().ifPresent(t -> {
			if (t == update) {
				// TODO implement updating & verifying functionality into the program
				Main.get().getHostServices().showDocument("https://satergo.com/#downloads");
			} else if (t == skip) {
				Main.programData().skippedUpdate.set(versionInfo.versionCode);
			}
		});
	}
}
