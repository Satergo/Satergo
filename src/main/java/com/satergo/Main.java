package com.satergo;

import com.satergo.controller.WalletCtrl;
import com.satergo.ergo.EmbeddedFullNode;
import com.satergo.ergouri.ErgoURIString;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ObjectProperty;
import javafx.scene.Group;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.util.Pair;
import jfxtras.styles.jmetro.JMetro;
import jfxtras.styles.jmetro.Style;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.Locale;
import java.util.ResourceBundle;

public class Main extends Application {

	public static final String VERSION = "0.0.2";
	public static final int VERSION_CODE = 2;

	public static EmbeddedFullNode node;
	// from command line
	static ErgoURIString initErgoURI;

	private static Main INSTANCE;

	public final Translations translations = new Translations("lang.Lang");

	public static Main get() {
		return INSTANCE;
	}

	private Stage stage;
	private Scene scene;

	private final JMetro jMetro = new JMetro();

	private Wallet wallet;
	private ProgramData programData;

	public void applySameTheme(Scene scene) {
		JMetro copy = new JMetro();
		copy.setScene(scene);
		copy.styleProperty().bind(jMetro.styleProperty());
		copy.getOverridingStylesheets().addAll(jMetro.getOverridingStylesheets());
	}

	public ObjectProperty<Style> themeStyleProperty() {
		return jMetro.styleProperty();
	}

	@Override
	public void start(Stage primaryStage) {
		INSTANCE = this;
		stage = primaryStage;

		{
			Path programDataPath = Path.of("program-data.json");
			programData = Files.isRegularFile(programDataPath) ? ProgramData.load(programDataPath) : new ProgramData(programDataPath);
		}

		translations.setLocale(Locale.forLanguageTag(programData.language.get()));
		Locale.setDefault(Locale.forLanguageTag(programData.language.get()));

		Load.resourceBundle = translations.getBundle();
		primaryStage.setTitle(lang("programName"));

		scene = new Scene(new Group(), 1000, 600);
		primaryStage.setScene(scene);
		primaryStage.setMinWidth(304);
		jMetro.setScene(scene);
		jMetro.setStyle(programData.lightTheme.get() ? Style.LIGHT : Style.DARK);
		Runnable updateOverrides = () -> {
			if (jMetro.getStyle() == Style.DARK) jMetro.getOverridingStylesheets().add(Utils.resourcePath("/dark.css"));
			else jMetro.getOverridingStylesheets().clear();
			jMetro.getOverridingStylesheets().add(Utils.resourcePath("/global.css"));
		};
		updateOverrides.run();
		jMetro.styleProperty().addListener((observable, oldValue, newValue) -> updateOverrides.run());

		Icon.icons = ResourceBundle.getBundle("icons");
		Icon.defaultColor.bind(Bindings.when(themeStyleProperty().isEqualTo(Style.DARK))
				.then(Color.rgb(255, 255, 255))
				.otherwise(Color.rgb(41, 41, 41)));
		Icon.defaultHeight = 16;

		Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
			StringWriter stringWriter = new StringWriter();
			PrintWriter printWriter = new PrintWriter(stringWriter);
			throwable.printStackTrace(printWriter);
			String stackTrace = stringWriter.toString();
			System.err.println(stackTrace);
			Utils.alertException("Unexpected error", "An unexpected error occurred", stackTrace);
		});

		Parent root;
		if (programData.blockchainNodeKind.get() == null ||
				(programData.blockchainNodeKind.get() == ProgramData.BlockchainNodeKind.EMBEDDED_FULL_NODE && !Files.isRegularFile(programData.embeddedNodeInfo.get()))) {
			root = Load.fxml("/blockchain-setup.fxml");
		} else {
			if (programData.blockchainNodeKind.get() == ProgramData.BlockchainNodeKind.EMBEDDED_FULL_NODE) {
				node = nodeFromInfo();
			}

			if (programData.lastWallet.get() == null || !Files.isRegularFile(programData.lastWallet.get())) {
				root = Load.fxml("/wallet-setup.fxml");
			} else {
				Utils.PasswordRequestResult passwordResult = Utils.requestPassword(Main.lang("passwordOf_s").formatted(programData.lastWallet.get().getFileName()), password -> {
					setWallet(Wallet.fromFile(programData.lastWallet.get(), password));
				});
				root = switch (passwordResult) {
					case NOT_GIVEN -> {
						programData.lastWallet.set(null);
						yield Load.fxml("/wallet-setup.fxml");
					}
					case CORRECT -> {
						Pair<Parent, WalletCtrl> load = Load.fxmlNodeAndController("/wallet.fxml");
						walletPage = load.getValue();
						if (initErgoURI != null) {
							load.getValue().openSendWithErgoURI(initErgoURI);
						}
						yield load.getKey();
					}
					case INCORRECT -> Load.fxml("/wallet-setup.fxml");
				};
			}
		}
		scene.setRoot(root);
		WalletCtrl walletCtrl = walletPage;
		displayNewTopPage(root);
		walletPage = walletCtrl;
		primaryStage.getIcons().add(new Image(Utils.resourcePath("/images/logo.jpg")));
		primaryStage.show();

		new Thread(() -> {
			UpdateChecker.VersionInfo latest = UpdateChecker.fetchLatestInfo();
			if (UpdateChecker.isNewer(latest.versionCode())) {
				Platform.runLater(() -> UpdateChecker.showUpdatePopup(latest));
			}
		}).start();

		primaryStage.addEventHandler(KeyEvent.KEY_PRESSED, e -> {
			if (e.getCode() == KeyCode.ESCAPE)
				previousPage();
		});

		programData.lightTheme.addListener((observable, oldValue, newValue) -> jMetro.setStyle(newValue ? Style.LIGHT : Style.DARK));
	}

	@Override
	public void stop() {
		if (node != null && node.isRunning())
			node.stop();
		if (walletPage != null) {
			walletPage.cancelRepeatingTasks();
		}
		try {
			Launcher.getIPC().stopListening();
			Files.deleteIfExists(Launcher.getIPC().path);
		} catch (IOException ignored) {}
	}

	public EmbeddedFullNode nodeFromInfo() {
		return EmbeddedFullNode.fromLocalNodeInfo(programData.embeddedNodeInfo.get().toFile());
	}

	private final LinkedList<Parent> pages = new LinkedList<>();

	public void displayPage(Parent parent) {
		walletPage = null;
		pages.add(parent);
		scene.setRoot(parent);
		Label versionLabel = (Label) parent.lookup("#_version");
		if (versionLabel != null) {
			versionLabel.setText("v" + VERSION);
		}
	}

	public void displayNewTopPage(Parent parent) {
		pages.clear();
		displayPage(parent);
	}

	private WalletCtrl walletPage;

	public void displayWalletPage(Pair<Parent, WalletCtrl> pair) {
		displayNewTopPage(pair.getKey());
		walletPage = pair.getValue();
	}

	public void displayWalletPage() {
		displayWalletPage(Load.fxmlNodeAndController("/wallet.fxml"));
	}

	public void previousPage() {
		if (pages.size() == 1) return;
		pages.removeLast();
		scene.setRoot(pages.getLast());
	}

	public void setLanguage(Translations.Entry entry) {
		programData.language.set(entry.code());
		translations.setLocale(entry.locale());
		Load.resourceBundle = translations.getBundle();
		Locale.setDefault(entry.locale());
	}

	public Stage stage() { return stage; }

	public static ProgramData programData() {
		return get().programData;
	}
	public static String lang(String key) { return get().translations.getString(key); }

	public Wallet getWallet() {
		return wallet;
	}

	public void setWallet(Wallet wallet) {
		this.wallet = wallet;
		programData.lastWallet.set(wallet == null ? null : wallet.path);
	}

	public WalletCtrl getWalletPage() {
		return walletPage;
	}

	public void handleErgoURI(ErgoURIString ergoURI) {
		System.out.println("walletPage = " + walletPage);
		if (walletPage == null) return;
		walletPage.openSendWithErgoURI(ergoURI);
		stage.toFront();
	}
}
