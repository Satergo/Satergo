package com.satergo;

import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;
import com.satergo.ergo.ErgoInterface;
import com.satergo.extra.PasswordInputDialog;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import org.ergoplatform.appkit.ErgoClient;
import org.ergoplatform.appkit.ErgoId;

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

	public static Alert alert(Alert.AlertType type, String contentText) {
		Alert alert = new Alert(type);
		alert.initOwner(Main.get().stage());
		alert.setTitle(Main.lang("programName"));
		alert.setGraphic(null);
		alert.setHeaderText(null);
		alert.setContentText(contentText);
		alert.show();
		return alert;
	}

	public static void alertException(String title, String headerText, Throwable throwable) {
		Alert alert = new Alert(Alert.AlertType.ERROR);
		alert.initOwner(Main.get().stage());
		alert.setTitle(title);
		alert.setHeaderText(headerText);
		StringWriter stringWriter = new StringWriter();
		PrintWriter printWriter = new PrintWriter(stringWriter);
		throwable.printStackTrace(printWriter);
		String stackTrace = stringWriter.toString();
		System.err.println(stackTrace);
		TextArea textArea = new TextArea(stackTrace);
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
		Label label = new Label(contentText);
		label.setWrapText(true);
		alert.getDialogPane().setContent(label);
		ButtonType copy = new ButtonType(Main.lang("copy"), ButtonBar.ButtonData.OK_DONE);
		alert.getButtonTypes().add(copy);
		alert.showAndWait().ifPresent(t -> {
			if (t == copy) copyStringToClipboard(contentText);
		});
	}

	public static void addCopyContextMenu(Labeled node) {
		MenuItem menuItem = new MenuItem(Main.lang("copy"));
		menuItem.setOnAction(e -> copyStringToClipboard(node.getText()));
		node.setContextMenu(new ContextMenu(menuItem));
	}

	public static ErgoClient createErgoClient() {
		return ErgoInterface.newNodeApiClient(
				Main.programData().nodeNetworkType.get(),
				Main.programData().nodeAddress.get());
	}

	public record NodeVersion(String version, URI uri) {
		public String fileName() {
			return "ergo-" + version + ".jar";
		}
	}

	public static NodeVersion fetchLatestNodeVersion() {
		JsonObject latest = fetchLatestNodeData();
		return new NodeVersion(latest.getString("tag_name").substring(1), URI.create(latest.getArray("assets").getObject(0).getString("browser_download_url")));
	}

	public static JsonObject fetchLatestNodeData() {
		HttpRequest request = httpRequestBuilder().uri(URI.create("https://api.github.com/repos/ergoplatform/ergo/releases")).build();
		try {
			HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
			return JsonParser.array().from(response.body()).stream()
					.map(o -> (JsonObject) o)
					.filter(o -> !o.getBoolean("prerelease"))
					.findFirst().orElseThrow(); // todo better handling?
		} catch (IOException | InterruptedException | JsonParserException e) {
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

	public static String requestPassword(String dialogTitle) {
		PasswordInputDialog dialog = new PasswordInputDialog();
		// the "open last wallet" popup is opened before the program stage is shown
		if (Main.get().stage() != null && Main.get().stage().isShowing()) {
			dialog.initOwner(Main.get().stage());
		}
		Main.get().applySameTheme(dialog.getDialogPane().getScene());
		dialog.setTitle(dialogTitle);
		return dialog.showAndWait().orElse(null);
	}

	public static void alertIncorrectPassword() {
		Utils.alert(Alert.AlertType.ERROR, Main.lang("incorrectPassword"));
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

	public static int getNumberOfDecimalPlaces(BigDecimal bigDecimal) {
		return Math.max(0, bigDecimal.stripTrailingZeros().scale());
	}

	public static int toInt(byte[] bytes, int offset) {
		int ret = 0;
		for (int i = 0; i < 4 && i + offset < bytes.length; i++) {
			ret <<= 8;
			ret |= (int) bytes[i] & 0xFF;
		}
		return ret;
	}

	public static int versionToInt(String version) {
		String[] versionParts = version.split("\\.");
		byte[] versionDigits = new byte[versionParts.length];
		for (int i = 0; i < versionParts.length; i++)
			versionDigits[i] = Byte.parseByte(versionParts[i]);
		return toInt(pad(versionDigits, 4), 0);
	}

	public static byte[] pad(byte[] array, int length) {
		if (length < array.length) throw new IllegalArgumentException();
		byte[] padded = new byte[length];
		System.arraycopy(array, 0, padded, 0, padded.length);
		return padded;
	}

	public static Image tokenIcon32x32(ErgoId tokenId) {
		return new Image("https://raw.githubusercontent.com/Satergo/Resources/master/token-icons-32x32/" + tokenId + ".png", true);
	}

	public static Image tokenIcon36x36(ErgoId tokenId) {
		return new Image("https://raw.githubusercontent.com/Satergo/Resources/master/token-icons-36x36/" + tokenId + ".png", true);
	}
}
