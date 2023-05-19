package com.satergo;

import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;
import com.satergo.ergo.ErgoInterface;
import com.satergo.extra.dialog.MoveStyle;
import com.satergo.extra.dialog.SatPasswordInputDialog;
import com.satergo.extra.dialog.SatVoidDialog;
import com.sun.management.OperatingSystemMXBean;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import org.ergoplatform.appkit.*;

import java.io.*;
import java.lang.management.ManagementFactory;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Objects;

public class Utils {

	public static final HttpClient HTTP = HttpClient.newHttpClient();

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
		try (InputStream inputStream = resourceStream(location)) {
			return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public static void alert(Alert.AlertType type, String headerText, String contentText) {
		SatVoidDialog alert = new SatVoidDialog();
		alert.initOwner(Main.get().stage());
		Main.get().applySameTheme(alert.getScene());
		alert.setTitle(Main.lang("programName"));
		alert.setHeaderText(headerText);
		alert.getDialogPane().setContent(new Label(contentText));
		if (type != Alert.AlertType.NONE) {
			alert.getDialogPane().getButtonTypes().add(ButtonType.OK);
		}
		alert.show();
	}

	public static SatVoidDialog alert(Alert.AlertType type, Node content) {
		SatVoidDialog alert = new SatVoidDialog();
		alert.initOwner(Main.get().stage());
		Main.get().applySameTheme(alert.getScene());
		alert.setTitle(Main.lang("programName"));
		alert.setHeaderText(null);
		alert.getDialogPane().setContent(content);
		if (type != Alert.AlertType.NONE) {
			alert.getDialogPane().getButtonTypes().add(ButtonType.OK);
		}
		alert.show();
		return alert;
	}

	public static SatVoidDialog alert(Alert.AlertType type, String contentText) {
		Label label = new Label(contentText);
		label.setWrapText(true);
		return alert(type, label);
	}

	public static void alertException(String title, String headerText, Throwable throwable) {
		try {
			Alert alert = new Alert(Alert.AlertType.ERROR);
			if (Main.get().stage() != null && Main.get().stage().isShowing()) {
				alert.initOwner(Main.get().stage());
			}
			Main.get().applySameTheme(alert.getDialogPane().getScene());
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
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static void textDialogWithCopy(String headerText, String contentText) {
		SatVoidDialog alert = new SatVoidDialog();
		alert.initOwner(Main.get().stage());
		alert.setMoveStyle(MoveStyle.FOLLOW_OWNER);
		Main.get().applySameTheme(alert.getScene());
		alert.setHeaderText(headerText);
		Label label = new Label(contentText);
		label.setWrapText(true);
		alert.getDialogPane().setContent(label);
		ButtonType copy = new ButtonType(Main.lang("copy"), ButtonBar.ButtonData.OK_DONE);
		alert.getDialogPane().getButtonTypes().add(copy);
		alert.showForResult().ifPresent(t -> {
			if (t == copy) copyStringToClipboard(contentText);
		});
	}

	public static void addCopyContextMenu(Labeled node) {
		MenuItem menuItem = new MenuItem(Main.lang("copy"));
		menuItem.setOnAction(e -> copyStringToClipboard(node.getText()));
		node.setContextMenu(new ContextMenu(menuItem));
	}

	private static ErgoClient ergoClient;
	private static NetworkType ecNetworkType;
	private static String ecNodeAddress;
	public static ErgoClient createErgoClient() {
		if (ergoClient == null || ecNetworkType != Main.programData().nodeNetworkType.get() || !Main.programData().nodeAddress.get().equals(ecNodeAddress))
			return ergoClient = ErgoInterface.newNodeApiClient(
					Main.programData().nodeNetworkType.get(),
					Main.programData().nodeAddress.get());
		return ergoClient;
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
			HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
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
		SatPasswordInputDialog dialog = new SatPasswordInputDialog();
		dialog.setHeaderText(dialogTitle);
		dialog.initOwner(Main.get().stage());
		Main.get().applySameTheme(dialog.getScene());
		return dialog.showForResult().orElse(null);
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

	private static final Comparator<int[]> VERSION_COMPARATOR = Comparator
			.<int[]>comparingInt(a -> a[0])
			.thenComparingInt(a -> a[1])
			.thenComparingInt(a -> a[2])
			.thenComparingInt(a -> a.length < 4 ? 0 : a[3]);

	public static int compareVersion(int[] a, int[] b) {
		return VERSION_COMPARATOR.compare(a, b);
	}

	public static Image tokenIcon32x32(ErgoId tokenId) {
		return new Image("https://raw.githubusercontent.com/Satergo/Resources/master/token-icons-32x32/" + tokenId + ".png", true);
	}

	public static Image tokenIcon36x36(ErgoId tokenId) {
		return new Image("https://raw.githubusercontent.com/Satergo/Resources/master/token-icons-36x36/" + tokenId + ".png", true);
	}

	public static long getTotalSystemMemory() {
		return ((OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean()).getTotalMemorySize();
	}

	public static void showTemporaryTooltip(Node node, Tooltip tooltip, long ms) {
		Bounds bounds = node.localToScreen(node.getLayoutBounds());
		tooltip.setOpacity(0);
		tooltip.setOnShown(event -> {
			tooltip.setOpacity(1);
			tooltip.setAnchorX(bounds.getCenterX() - tooltip.getWidth() / 2 + 10);
			Utils.fxRunDelayed(tooltip::hide, 600);
		});
		tooltip.show(node, bounds.getCenterX() - tooltip.getWidth() / 2, bounds.getMaxY());
	}

	public static void fxRunDelayed(Runnable runnable, long ms) {
		new Thread(() -> {
			try {
				Thread.sleep(ms);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			Platform.runLater(runnable);
		}).start();
	}

	private static final String os = System.getProperty("os.name");

	private static final boolean WINDOWS = os.startsWith("Windows");
	private static final boolean MAC = os.startsWith("Mac");
	private static final boolean LINUX = os.startsWith("Linux");

	public static boolean isMac() {
		return MAC;
	}
}
