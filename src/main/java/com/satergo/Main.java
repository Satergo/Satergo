package com.satergo;

import com.pixelduke.control.skin.FXSkins;
import com.satergo.controller.*;
import com.satergo.ergo.ErgoURI;
import com.satergo.ergopay.ErgoPayURI;
import com.satergo.extra.IncorrectPasswordException;
import com.satergo.extra.market.MarketData;
import com.satergo.extra.SimpleTask;
import com.satergo.node.EmbeddedNode;
import javafx.application.Application;
import javafx.application.ColorScheme;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.Group;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import javafx.util.Pair;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class Main extends Application {

	public static final String VERSION = "1.7.0";
	public static final int VERSION_CODE = 9;

	public static EmbeddedNode node;
	// from command line
	static Path initWalletFile;
	static ErgoURI initErgoURI;
	static ErgoPayURI initErgoPayURI;

	private static Main INSTANCE;

	public final Translations translations = new Translations("lang.Lang");

	public final MarketData market = new MarketData();

	public static Main get() {
		return INSTANCE;
	}

	private Stage stage;
	private Scene scene;

	private final SimpleObjectProperty<ColorScheme> colorScheme = new SimpleObjectProperty<>();
	private static final Path CUSTOM_STYLESHEET = Utils.settingsDir().resolve("custom.css");

	private Wallet wallet;
	private ProgramData programData;

	public void applySameTheme(Scene scene) {
		scene.getStylesheets().add(FXSkins.getStylesheetURL());
		scene.getStylesheets().add(Utils.resourcePath("/global.css"));
		scene.getStylesheets().add(colorScheme.get() == ColorScheme.DARK ? "/dark.css" : "/light.css");
		if (Files.isRegularFile(CUSTOM_STYLESHEET)) {
			try {
				scene.getStylesheets().add(CUSTOM_STYLESHEET.toUri().toURL().toExternalForm());
			} catch (MalformedURLException e) {
				throw new RuntimeException(e);
			}
		}
	}

	public ObjectProperty<ColorScheme> colorSchemeProperty() {
		return colorScheme;
	}

	@Override
	public void start(Stage primaryStage) {
		try {
			startInternal(primaryStage);
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(1);
		}
	}

	private void startInternal(Stage primaryStage) {
		INSTANCE = this;
		stage = primaryStage;

		{
			Path programDataPath = Utils.settingsDir().resolve("program-data.json");
			programData = Files.isRegularFile(programDataPath) ? ProgramData.load(programDataPath) : new ProgramData(programDataPath);
		}

		Locale.setDefault(Locale.forLanguageTag(programData.language.get()));
		translations.setLocale(Locale.forLanguageTag(programData.language.get()));

		Load.resourceBundle = translations.getBundle();
		primaryStage.setTitle(lang("programName"));

		scene = new Scene(new Group(), 1030, 600);
		primaryStage.setScene(scene);
		primaryStage.setMinWidth(304);

		colorScheme.addListener((observable, oldValue, newValue) -> {
			scene.getStylesheets().clear();
			applySameTheme(scene);
		});
		colorScheme.set(programData.lightTheme.get() ? ColorScheme.LIGHT : ColorScheme.DARK);

		Icon.icons = ResourceBundle.getBundle("icons");
		Icon.defaultHeight = 16;

		Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
			try {
				Platform.runLater(() -> Utils.alertUnexpectedException(throwable));
			} catch (Exception e) {
				e.printStackTrace();
			}
		});

		primaryStage.getIcons().add(new Image(Utils.resourcePath("/images/window-logo.png")));
		primaryStage.show();

		if (programData.blockchainNodeKind.get() == null ||
				(programData.blockchainNodeKind.get().embedded && !Files.isRegularFile(programData.embeddedNodeInfo.get()))) {
			displayTopSetupPage(Load.<BlockchainSetupCtrl>fxmlController("/setup-page/blockchain.fxml"));
		} else {
			if (programData.blockchainNodeKind.get().embedded) {
				node = nodeFromInfo();
			}

			displayTopSetupPage(Load.<WalletSetupCtrl>fxmlController("/setup-page/wallet.fxml"));
			if (initWalletFile != null || (programData.lastWallet.get() != null && Files.isRegularFile(programData.lastWallet.get()))) {
				Path path = initWalletFile != null ? initWalletFile : programData.lastWallet.get();
				String password = Utils.requestPassword(Main.lang("passwordOf_s").formatted(path.getFileName()));
				if (password == null) {
					if (initWalletFile == null)
						programData.lastWallet.set(null);
					displayTopSetupPage(Load.<WalletSetupCtrl>fxmlController("/setup-page/wallet.fxml"));
				} else try {
					setWallet(Wallet.load(path, password));
					Pair<Parent, WalletCtrl> load = Load.fxmlNodeAndController("/wallet.fxml");
					walletPage = load.getValue();
					if (initErgoURI != null) {
						load.getValue().openErgoURI(initErgoURI);
					}
					displayWalletPage(load);
				} catch (IncorrectPasswordException e) {
					Utils.alertIncorrectPassword();
					displayTopSetupPage(Load.<WalletSetupCtrl>fxmlController("/setup-page/wallet.fxml"));
				}
			}
		}

		new SimpleTask<>(UpdateChecker::fetchLatestInfo)
				.onSuccess(latest -> {
					if (UpdateChecker.isNewer(latest.versionCode())) {
						UpdateChecker.showUpdatePopup(latest);
					}
				}).newThread();

		programData.lightTheme.addListener((observable, oldValue, newValue) -> colorScheme.set(newValue ? ColorScheme.LIGHT : ColorScheme.DARK));
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
		// Unfortunately, the appkit dependency uses an HTTP library that remains active for 5 minutes after last use,
		// which prevents the application process from shutting down, so explicitly exiting is required.
		System.exit(0);
	}

	public EmbeddedNode nodeFromInfo() {
		return EmbeddedNode.fromLocalNodeInfo(programData.embeddedNodeInfo.get().toFile());
	}

	private final LinkedList<Parent> pages = new LinkedList<>();

	public void displaySetupPage(SetupPage setupPage) {
		Parent holder = Load.fxmlControllerFactory("/setup-page/holder.fxml", new SetupPageHolderCtrl(setupPage));
		walletPage = null;
		pages.add(holder);
		scene.setRoot(holder);
	}

	public void displayTopSetupPage(SetupPage setupPage) {
		pages.clear();
		walletPage = null;
		Parent parent = Load.fxmlControllerFactory("/setup-page/holder.fxml", new SetupPageHolderCtrl(setupPage));
		pages.add(parent);
		scene.setRoot(parent);
	}

	public void displayNewTopPage(Parent parent) {
		pages.clear();
		walletPage = null;
		pages.add(parent);
		scene.setRoot(parent);
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

	public List<Parent> getAllPages() {
		return Collections.unmodifiableList(pages);
	}

	public void setLanguage(Translations.Entry entry) {
		Locale.setDefault(entry.locale());
		programData.language.set(entry.code());
		translations.setLocale(entry.locale());
		Load.resourceBundle = translations.getBundle();
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

	public void setWalletPageInternal(WalletCtrl walletPage) {
		this.walletPage = walletPage;
	}

	public void handleErgoURI(String uri) {
		ErgoURI ergoURI;
		try {
			ergoURI = ErgoURI.parse(new URI(uri));
		} catch (Exception e) {
			Utils.alert(Alert.AlertType.ERROR, lang("theUrlYouTriedToOpenIsInvalid"));
			return;
		}
		if (walletPage == null) {
			Utils.alert(Alert.AlertType.ERROR, lang("noWalletIsOpen"));
		} else {
			walletPage.openErgoURI(ergoURI);
			stage.toFront();
		}
	}

	public void handleErgoPayURI(String uri) {
		if (walletPage == null) {
			Utils.alert(Alert.AlertType.ERROR, lang("noWalletIsOpen"));
		} else {
			walletPage.handleErgoPayURI(new ErgoPayURI(uri));
			stage.toFront();
		}
	}
}
