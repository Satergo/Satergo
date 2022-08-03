package com.satergo;

import com.satergo.controller.*;
import com.satergo.ergo.EmbeddedFullNode;
import com.satergo.ergouri.ErgoURIString;
import com.satergo.extra.IncorrectPasswordException;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ObjectProperty;
import javafx.scene.Group;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.util.Pair;
import jfxtras.styles.jmetro.JMetro;
import jfxtras.styles.jmetro.Style;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class Main extends Application {

	public static final String VERSION = "1.4.1";
	public static final int VERSION_CODE = 5;

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

		Locale.setDefault(Locale.forLanguageTag(programData.language.get()));
		translations.setLocale(Locale.forLanguageTag(programData.language.get()));

		Load.resourceBundle = translations.getBundle();
		primaryStage.setTitle(lang("programName"));

		scene = new Scene(new Group(), 1000, 600);
		primaryStage.setScene(scene);
		primaryStage.setMinWidth(304);
		jMetro.setScene(scene);
		jMetro.setStyle(programData.lightTheme.get() ? Style.LIGHT : Style.DARK);
		Runnable updateOverrides = () -> {
			jMetro.getOverridingStylesheets().clear();
			jMetro.getOverridingStylesheets().add(Utils.resourcePath(jMetro.getStyle() == Style.DARK ? "/dark.css" : "/light.css"));
			jMetro.getOverridingStylesheets().add(Utils.resourcePath("/global.css"));
		};
		updateOverrides.run();
		jMetro.styleProperty().addListener((observable, oldValue, newValue) -> updateOverrides.run());

		Icon.icons = ResourceBundle.getBundle("icons");
		Icon.defaultColor.bind(Bindings.when(themeStyleProperty().isEqualTo(Style.DARK))
				.then(Color.rgb(255, 255, 255))
				.otherwise(Color.rgb(41, 41, 41)));
		Icon.defaultHeight = 16;

		// For Pty4J
		System.setProperty(org.slf4j.impl.SimpleLogger.DEFAULT_LOG_LEVEL_KEY, "WARN");

		Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
			try {
				Platform.runLater(() -> Utils.alertException(Main.lang("unexpectedError"), Main.lang("anUnexpectedErrorOccurred"), throwable));
			} catch (Exception e) {
				e.printStackTrace();
			}
		});

		if (programData.blockchainNodeKind.get() == null ||
				(programData.blockchainNodeKind.get() == ProgramData.BlockchainNodeKind.EMBEDDED_FULL_NODE && !Files.isRegularFile(programData.embeddedNodeInfo.get()))) {
			displayTopSetupPage(Load.<BlockchainSetupCtrl>fxmlController("/setup-page/blockchain.fxml"));
		} else {
			if (programData.blockchainNodeKind.get() == ProgramData.BlockchainNodeKind.EMBEDDED_FULL_NODE) {
				node = nodeFromInfo();
			}

			if (programData.lastWallet.get() == null || !Files.isRegularFile(programData.lastWallet.get())) {
				displayTopSetupPage(Load.<WalletSetupCtrl>fxmlController("/setup-page/wallet.fxml"));
			} else {
				String password = Utils.requestPassword(Main.lang("passwordOf_s").formatted(programData.lastWallet.get().getFileName()));
				if (password == null) {
					programData.lastWallet.set(null);
					displayTopSetupPage(Load.<WalletSetupCtrl>fxmlController("/setup-page/wallet.fxml"));
				} else try {
					setWallet(Wallet.load(programData.lastWallet.get(), password));
					Pair<Parent, WalletCtrl> load = Load.fxmlNodeAndController("/wallet.fxml");
					walletPage = load.getValue();
					if (initErgoURI != null) {
						load.getValue().openSendWithErgoURI(initErgoURI);
					}
					displayWalletPage(load);
				} catch (IncorrectPasswordException e) {
					Utils.alertIncorrectPassword();
					displayTopSetupPage(Load.<WalletSetupCtrl>fxmlController("/setup-page/wallet.fxml"));
				}
			}
		}
		primaryStage.getIcons().add(new Image(Utils.resourcePath("/images/logo.jpg")));
		primaryStage.show();

		new Thread(() -> {
			try {
				UpdateChecker.VersionInfo latest = UpdateChecker.fetchLatestInfo();
				if (UpdateChecker.isNewer(latest.versionCode())) {
					Platform.runLater(() -> UpdateChecker.showUpdatePopup(latest));
				}
			} catch (IOException ignored) {
			}
		}).start();

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

	public void handleErgoURI(ErgoURIString ergoURI) {
		if (walletPage == null) return;
		walletPage.openSendWithErgoURI(ergoURI);
		stage.toFront();
	}
}
