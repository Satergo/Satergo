package com.satergo;

import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;
import com.satergo.ergo.ErgoInterface;
import com.satergo.extra.dialog.AbstractSatDialog;
import com.satergo.extra.dialog.MoveStyle;
import com.satergo.extra.dialog.SatPasswordInputDialog;
import com.satergo.extra.dialog.SatVoidDialog;
import com.sun.management.OperatingSystemMXBean;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.binding.IntegerBinding;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.control.skin.ScrollPaneSkin;
import javafx.scene.image.Image;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.ScrollEvent;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import javafx.util.Duration;
import org.ergoplatform.appkit.*;
import org.ergoplatform.sdk.ErgoId;
import org.ergoplatform.sdk.ErgoToken;
import org.fxmisc.flowless.Cell;
import org.fxmisc.flowless.VirtualFlow;
import org.fxmisc.flowless.VirtualizedScrollPane;

import java.io.*;
import java.lang.management.ManagementFactory;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

public class Utils {

	public static final HttpClient HTTP = HttpClient.newHttpClient();

	public static final long COPIED_TOOLTIP_MS = 600;

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
		initDialog(alert, Main.get().stage());
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
		initDialog(alert, Main.get().stage());
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

	public static void alertUnexpectedException(Throwable throwable) {
		alertException(Main.lang("unexpectedError"), Main.lang("anUnexpectedErrorOccurred"), throwable);
	}

	public static void textDialogWithCopy(String headerText, String contentText) {
		SatVoidDialog alert = new SatVoidDialog();
		initDialog(alert, Main.get().stage(), MoveStyle.FOLLOW_OWNER);
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

	public static ErgoClient offlineErgoClient() {
		return new ColdErgoClient(null, Parameters.ColdClientMaxBlockCost, Parameters.ColdClientBlockVersion);
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
		initDialog(dialog, Main.get().stage());
		dialog.setHeaderText(dialogTitle);
		return dialog.showForResult().orElse(null);
	}

	public static void alertIncorrectPassword() {
		alert(Alert.AlertType.ERROR, Main.lang("incorrectPassword"));
	}

	public static Path fileChooserSave(Window owner, String title, Path initialDirectory, String initialFileName, FileChooser.ExtensionFilter... extensionFilters) {
		FileChooser fileChooser = new FileChooser();
		fileChooser.setTitle(title);
		if (initialDirectory != null)
			fileChooser.setInitialDirectory(initialDirectory.toFile());
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

	private static final HashMap<ErgoId, Image> token32Cache = new HashMap<>();
	public static Image tokenIcon32x32(ErgoId tokenId) {
		if (token32Cache.containsKey(tokenId))
			return token32Cache.get(tokenId);
		Image image = new Image("https://raw.githubusercontent.com/Satergo/Resources/master/token-icons-32x32/" + tokenId + ".png", true);
		token32Cache.put(tokenId, image);
		return image;
	}

	private static final HashMap<ErgoId, Image> token36Cache = new HashMap<>();
	public static Image tokenIcon36x36(ErgoId tokenId) {
		if (token36Cache.containsKey(tokenId))
			return token36Cache.get(tokenId);
		Image image = new Image("https://raw.githubusercontent.com/Satergo/Resources/master/token-icons-36x36/" + tokenId + ".png", true);
		token36Cache.put(tokenId, image);
		return image;
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
			fxRunDelayed(tooltip::hide, ms);
		});
		tooltip.show(node, bounds.getCenterX() - tooltip.getWidth() / 2, bounds.getMaxY());
	}

	public static void fxRunDelayed(Runnable runnable, long ms) {
		KeyFrame keyFrame = new KeyFrame(Duration.millis(ms), e -> runnable.run());
		Timeline timeline = new Timeline(keyFrame);
		timeline.play();
	}

	public static String explorerTransactionUrl(String transactionId) {
		return "https://" + (Main.programData().nodeNetworkType.get() == NetworkType.MAINNET ? "explorer" : "testnet") + ".ergoplatform.com/en/transactions/" + transactionId;
	}

	private static final String os = System.getProperty("os.name");

	private static final boolean WINDOWS = os.startsWith("Windows");
	private static final boolean MAC = os.startsWith("Mac");
	private static final boolean LINUX = os.startsWith("Linux");

	public static boolean isWindows() { return WINDOWS; }
	public static boolean isMac() { return MAC; }
	public static boolean isLinux() { return LINUX; }

	public static Path settingsDir() {
		return switch (SystemProperties.packageType()) {
			case INSTALLATION -> {
				if (isWindows()) {
					Path path = Path.of(System.getProperty("user.home")).resolve("AppData/Roaming/Satergo");
					try {
						Files.createDirectories(path);
					} catch (IOException e) {
						throw new RuntimeException(e);
					}
					yield path;
				} else throw new UnsupportedOperationException(); // no installers are made for other operating systems yet
			}
			case PORTABLE -> Path.of(System.getProperty("user.dir"));
		};
	}

	public static String makeNodeConfig(boolean nipopow, boolean utxoSetSnapshot) {
		String template = resourceStringUTF8("/conf-template.conf");
		String[] lines = template.split("\n");
		StringBuilder config = new StringBuilder();
		boolean inNipopow = false, inUtxo = false;
		for (int i = 0; i < lines.length; i++) {
			String line = lines[i];
			switch (line.strip()) {
				case "// nipopow_start" -> inNipopow = true;
				case "// nipopow_end" -> inNipopow = false;
				case "// utxo_snapshot_start" -> inUtxo = true;
				case "// utxo_snapshot_end" -> inUtxo = false;
				default -> {
					if ((inNipopow && nipopow) || (inUtxo && utxoSetSnapshot) || (!inNipopow && !inUtxo)) {
						config.append(line);
						if (i != lines.length - 1)
							config.append("\n");
					}
				}
			}
		}
		return config.toString();
	}

	public static void runLaterOrNow(Runnable runnable) {
		if (!Platform.isFxApplicationThread())
			Platform.runLater(runnable);
		else runnable.run();
	}

	/**
	 * Opens a website or a file
	 */
	public static void showDocument(String uri) {
		Main.get().getHostServices().showDocument(uri);
	}

	public static Path getLastWalletDir() {
		Path path = Main.programData().lastWalletDirectory.get();
		if (path == null || !Files.isDirectory(path)) return null;
		return path;
	}

	public static void accessibleLabel(Label... labels) {
		for (Label label : labels) {
			label.focusTraversableProperty().bind(Platform.accessibilityActiveProperty());
		}
	}

	public static Label accessibleLabel(Label label) {
		label.focusTraversableProperty().bind(Platform.accessibilityActiveProperty());
		return label;
	}

	public static void alertTxBuildException(Throwable t, long amountNanoErg, Collection<ErgoToken> tokens, Function<ErgoId, String> getTokenName) {
		if (t instanceof InputBoxesSelectionException.NotEnoughErgsException ex) {
			alert(Alert.AlertType.ERROR, Main.lang("youDoNotHaveEnoughErg_s_moreNeeded").formatted(FormatNumber.ergExact(ErgoInterface.toFullErg(amountNanoErg - ex.balanceFound))));
		} else if (t instanceof InputBoxesSelectionException.NotEnoughTokensException ex) {
			String tokensString = tokens.stream()
					.filter(token -> token.getValue() > ex.tokenBalances.get(token.getId().toString()))
					.map(ErgoToken::getId).map(getTokenName).map(name -> '"' + name + '"').collect(Collectors.joining(", "));
			alert(Alert.AlertType.ERROR, Main.lang("youDoNotHaveEnoughOf_s").formatted(tokensString));
		} else if (t != null) {
			alertUnexpectedException(t);
		}
	}

	@SuppressWarnings("unchecked")
	public static <T extends Parent>T findParent(Node node, Class<T> parentClass) {
		Parent parent = node.getParent();
		while (true) {
			if (parent == null) return null;
			if (parentClass.isInstance(parent))
				return (T) parent;
			parent = parent.getParent();
		}
	}

	/**
	 * Makes it so that if the user scrolls toward the end while already at the end in a VirtualizedScrollPane,
	 * it will try to find a parent ScrollPane and scroll it instead. The same applies for scrolling toward the start.
	 */
	public static <T, C extends Cell<T, ?>>void overscrollToParent(VirtualizedScrollPane<VirtualFlow<T, C>> scrollPane) {
		VirtualFlow<T, C> flow = scrollPane.getContent();
		flow.addEventHandler(ScrollEvent.SCROLL, e -> {
			ScrollPane parent = findParent(scrollPane, ScrollPane.class);
			if (parent == null) return;
			ScrollBar scrollBar = ((ScrollPaneSkin) parent.getSkin()).getVerticalScrollBar();
			if (scrollBar == null) return;
			if (e.getDeltaY() < 0) {
				if (flow.getEstimatedScrollY() + flow.getHeight() >= flow.getTotalHeightEstimate()-1 || !scrollPane.lookup(".scroll-bar:vertical").isVisible()) {
					scrollBar.increment();
				}
			} else if (e.getDeltaY() > 0) {
				if (flow.getEstimatedScrollY() == 0 || !scrollPane.lookup(".scroll-bar:vertical").isVisible()) {
					scrollBar.decrement();
				}
			}
		});
	}

	public static <T> IntegerBinding indexBinding(ObservableList<T> list, T object) {
		Objects.requireNonNull(list, "list");
		return new IntegerBinding() {
			{ super.bind(list); }
			@Override public void dispose() { super.unbind(list); }
			@Override protected int computeValue() {
				return list.indexOf(object);
			}
			@Override public ObservableList<?> getDependencies() {
				return FXCollections.singletonObservableList(list);
			}
		};
	}

	public static void initDialog(AbstractSatDialog<?, ?> dialog, Window owner) {
		dialog.initOwner(owner);
		Main.get().applySameTheme(dialog.getScene());
	}

	public static void initDialog(AbstractSatDialog<?, ?> dialog, Window owner, MoveStyle moveStyle) {
		initDialog(dialog, owner);
		dialog.setMoveStyle(moveStyle);
	}
}
