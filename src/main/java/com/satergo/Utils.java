package com.satergo;

import com.satergo.extra.IncorrectPasswordException;
import com.satergo.extra.PasswordInputDialog;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextArea;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.GridPane;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.*;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Consumer;

public class Utils {

	public static URL resource(String location) {
		return Objects.requireNonNull(Utils.class.getResource(location), "resource not found");
	}

	public static String resourcePath(String location) {
		return resource(location).toExternalForm();
	}

	public static InputStream resourceStream(String location) {
		return Objects.requireNonNull(Utils.class.getResourceAsStream(location), "resource not found");
	}

	public static String resourceStringUTF8(String location) {
		try {
			return new String(resourceStream(location).readAllBytes(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public static void alert(Alert.AlertType type, String headerText, String contentText) {
		Alert alert = new Alert(type);
		alert.initOwner(Main.get().stage());
		alert.setTitle(Main.lang("programName"));
		alert.setHeaderText(headerText);
		alert.setContentText(contentText);
		alert.show();
	}

	public static void alert(Alert.AlertType type, String contentText) {
		Alert alert = new Alert(type);
		alert.initOwner(Main.get().stage());
		alert.setTitle(Main.lang("programName"));
		alert.setGraphic(null);
		alert.setHeaderText(null);
		alert.setContentText(contentText);
		alert.show();
	}

	public static void alertException(String title, String headerText, String exceptionText) {
		Alert alert = new Alert(Alert.AlertType.ERROR);
		alert.initOwner(Main.get().stage());
		alert.setTitle(title);
		alert.setHeaderText(headerText);
		TextArea textArea = new TextArea(exceptionText);
		textArea.setEditable(false);
		textArea.setWrapText(true);
		textArea.setMaxWidth(Double.MAX_VALUE);
		textArea.setMaxHeight(Double.MAX_VALUE);
		alert.getDialogPane().setExpandableContent(textArea);
		alert.getDialogPane().setExpanded(true);
		alert.show();
	}

	public static void textDialogWithCopy(String headerText, String contentText) {
		Alert alert = new Alert(Alert.AlertType.NONE);
		alert.initOwner(Main.get().stage());
		alert.setHeaderText(headerText);
		alert.setContentText(contentText);
		ButtonType copy = new ButtonType("Copy", ButtonBar.ButtonData.OK_DONE);
		alert.getButtonTypes().add(copy);
		alert.showAndWait().ifPresent(t -> {
			if (t == copy) copyStringToClipboard(contentText);
		});
	}

	public record NodeVersion(String version, URI uri) {
		public String fileName() {
			return "ergo-" + version + ".jar";
		}
	}

	public static NodeVersion fetchLatestNode() {
		HttpClient httpClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build();
		HttpRequest request = httpRequestBuilder().uri(URI.create("https://github.com/ergoplatform/ergo/releases/latest"))
				.method("HEAD", HttpRequest.BodyPublishers.noBody()).build();
		try {
			HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
			String link = response.uri().toString();
			String version = link.substring(link.lastIndexOf('/') + 2);
			return new NodeVersion(version, URI.create("https://github.com/ergoplatform/ergo/releases/download/v" + version + "/ergo-" + version + ".jar"));
		} catch (IOException | InterruptedException e) {
			throw new RuntimeException(e);
		}
	}

	public static void copyStringToClipboard(String string) {
		Clipboard.getSystemClipboard().clear();
		ClipboardContent content = new ClipboardContent();
		content.putString(string);
		Clipboard.getSystemClipboard().setContent(content);
	}

	public static boolean isValidBigDecimal(String s) {
		try {
			new BigDecimal(s);
			return true;
		} catch (NumberFormatException e) {
			return false;
		}
	}

	public interface PasswordRequestHandler {
		void handle(String password) throws IncorrectPasswordException;
	}

	public enum PasswordRequestResult {
		CORRECT, INCORRECT, NOT_GIVEN
	}

	/**
	 * The password is considered correct if {@link IncorrectPasswordException} is not thrown from the handler
	 * @param handler Not executed if result is NOT_GIVEN
	 */
	public static PasswordRequestResult requestPassword(String dialogTitle, PasswordRequestHandler handler) {
		PasswordInputDialog dialog = new PasswordInputDialog();
		// the "open last wallet" popup is opened before the program stage is shown
		if (Main.get().stage() != null && Main.get().stage().isShowing()) {
			dialog.initOwner(Main.get().stage());
		}
		Main.get().applySameTheme(dialog.getDialogPane().getScene());
		dialog.setTitle(dialogTitle);
		String password = dialog.showAndWait().orElse(null);
		if (password == null) return PasswordRequestResult.NOT_GIVEN;
		try {
			handler.handle(password);
			return PasswordRequestResult.CORRECT;
		} catch (IncorrectPasswordException e) {
			Utils.alert(Alert.AlertType.ERROR, Main.lang("incorrectPassword"));
			return PasswordRequestResult.INCORRECT;
		}
	}

	public static Path fileChooserSave(Window owner, String title, String initialFileName, FileChooser.ExtensionFilter... extensionFilters) {
		FileChooser fileChooser = new FileChooser();
		fileChooser.setTitle(title);
		fileChooser.setInitialFileName(initialFileName);
		fileChooser.getExtensionFilters().addAll(extensionFilters);
		File file = fileChooser.showSaveDialog(owner);
		return file == null ? null : file.toPath();
	}

	public static HttpRequest.Builder httpRequestBuilder() {
		return HttpRequest.newBuilder().setHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:94.0) Gecko/20100101 Firefox/94.0");
	}

	public static void transferWithMeter(InputStream inputStream, OutputStream outputStream, Consumer<Long> bytes) throws IOException {
		long downloaded = 0;
		byte[] buffer = new byte[8192];
		int read;
		while ((read = inputStream.read(buffer, 0, 8192)) >= 0) {
			outputStream.write(buffer, 0, read);
			downloaded += read;
			bytes.accept(downloaded);
		}
		inputStream.close();
		outputStream.close();
	}

	public static int getNumberOfDecimalPlaces(BigDecimal bigDecimal) {
		return Math.max(0, bigDecimal.stripTrailingZeros().scale());
	}
}
