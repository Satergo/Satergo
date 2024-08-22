package com.satergo.controller;

import com.satergo.*;
import com.satergo.ergo.ErgoInterface;
import com.satergo.extra.dialog.MoveStyle;
import com.satergo.extra.dialog.SatPromptDialog;
import com.satergo.extra.dialog.SatVoidDialog;
import info.debatty.java.stringsimilarity.NormalizedLevenshtein;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Screen;
import org.ergoplatform.appkit.Address;
import org.ergoplatform.appkit.Mnemonic;
import org.ergoplatform.explorer.client.DefaultApi;
import org.ergoplatform.sdk.SecretString;
import org.ergoplatform.wallet.mnemonic.WordList;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import scala.collection.JavaConverters;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

public class RestoreFromSeedCtrl implements SetupPage.WithoutExtra, Initializable {
	@FXML private Parent root;
	@FXML private TextArea seedPhrase;
	@FXML private FlowPane suggestionContainer;
	@FXML private TextField walletName;
	@FXML private PasswordField extendedSeedPassphrase;
	@FXML private PasswordField walletPassword;

	private List<String> allMnemonicWords;

	@Override
	public void initialize(URL location, ResourceBundle resources) {
		WordList wordList = WordList.load("english").get();
		allMnemonicWords = JavaConverters.seqAsJavaList(wordList.words());
		seedPhrase.textProperty().addListener((observable, oldValue, newValue) -> {
			String[] words = newValue.split(" ", -1);
			if (words.length == 0) return;
			String last = words[words.length - 1];
			suggestionContainer.getChildren().clear();
			suggestionContainer.setVisible(false);
			if (last.length() < 2) return;
			List<String> matches = allMnemonicWords.stream().filter(w -> w.startsWith(last)).sorted(Comparator.comparingInt(String::length)).toList();
			if (!matches.isEmpty()) suggestionContainer.setVisible(true);
			matches.stream().limit(20).forEach(w -> {
				Button button = new Button(w);
				button.setOnAction(e -> {
					String textWithoutLastWord = newValue.substring(0, Math.max(newValue.lastIndexOf(' '), 0));
					if (textWithoutLastWord.isEmpty()) seedPhrase.setText(w + " ");
					else seedPhrase.setText(textWithoutLastWord + " " + w + " ");
					seedPhrase.requestFocus();
					seedPhrase.positionCaret(seedPhrase.getText().length());
				});
				suggestionContainer.getChildren().add(button);
			});
			if (matches.size() > 20) suggestionContainer.getChildren().add(new Label(Main.lang("dddAndMore")));
		});
		showExtendedSeedPassphrase.visibleProperty().bind(extendedSeedPassphrase.visibleProperty().not());
		extendedSeedPassphrase.setOnKeyPressed(e -> {
			if (e.getCode() == KeyCode.ESCAPE) {
				root.requestFocus();
				e.consume();
			}
		});
		extendedSeedPassphrase.focusedProperty().addListener((observable, oldValue, newValue) -> {
			if (!newValue && extendedSeedPassphrase.getText().isEmpty())
				extendedSeedPassphrase.setVisible(false);
		});
	}

	@FXML
	public void restore(ActionEvent e) {
		String[] words = seedPhrase.getText().strip().split(" ");
		// todo check word amount
		for (String word : words) {
			if (!allMnemonicWords.contains(word)) {
				NormalizedLevenshtein nl = new NormalizedLevenshtein();
				String similarWords = allMnemonicWords.stream().filter(w -> nl.distance(word, w) <= 0.3).collect(Collectors.joining(", "));
				String contentText = Main.lang("unknownWord_s").formatted(word);
				if (!similarWords.isEmpty()) {
					if (similarWords.indexOf(',') == -1) contentText += "\n" + Main.lang("similarWord_s").formatted(similarWords);
					else contentText += "\n" + Main.lang("similarWords_s").formatted(similarWords);
				}
				Utils.alert(Alert.AlertType.ERROR, contentText);
				return;
			}
		}
		SecretString phrase = SecretString.create(SystemProperties.rawSeedPhrase() ? seedPhrase.getText() : String.join(" ", words));
		Mnemonic mnemonic = Mnemonic.create(phrase, SecretString.create(extendedSeedPassphrase.getText()));
		Path path = Utils.fileChooserSave(Main.get().stage(), Main.lang("locationToSaveTo"), Utils.getLastWalletDir(), walletName.getText() + "." + Wallet.FILE_EXTENSION, Wallet.extensionFilter());
		if (path == null) return;

		Address masterStandard = ErgoInterface.getPublicEip3Address(Main.programData().nodeNetworkType.get(), false, mnemonic, 0);
		Address masterNonstandard = ErgoInterface.getPublicEip3Address(Main.programData().nodeNetworkType.get(), true, mnemonic, 0);

		boolean nonstandardDerivation = false;

		DefaultApi api = new Retrofit.Builder()
				.baseUrl(ErgoInterface.getExplorerUrl(Main.programData().nodeNetworkType.get()))
				.addConverterFactory(GsonConverterFactory.create())
				.build().create(DefaultApi.class);

		if (SystemProperties.alwaysNonstandardDerivation()) {
			nonstandardDerivation = true;
		} else if (!masterStandard.equals(masterNonstandard)) {
			try {
				boolean standardHas = api.getApiV1AddressesP1Transactions(masterStandard.toString(), 0, 1, true).execute().body().getTotal() > 0;
				boolean nonstandardHas = api.getApiV1AddressesP1Transactions(masterNonstandard.toString(), 0, 1, true).execute().body().getTotal() > 0;
				if (standardHas && nonstandardHas) {
					SatPromptDialog<Boolean> prompt = new SatPromptDialog<>();
					Utils.initDialog(prompt, root.getScene().getWindow(), MoveStyle.FOLLOW_OWNER);
					prompt.setHeaderText(Main.lang("differentWalletsFound"));
					Label sAddr0Label = new Label(masterStandard.toString());
					Label nsAddr0Label = new Label(masterNonstandard.toString());
					Utils.addCopyContextMenu(sAddr0Label);
					Utils.addCopyContextMenu(nsAddr0Label);
					prompt.getDialogPane().setContent(new VBox(
							new HBox(new Label(Main.lang("wallet_d_masterAddressC").formatted(1) + " "), sAddr0Label),
							new HBox(new Label(Main.lang("wallet_d_masterAddressC").formatted(2) + " "), nsAddr0Label)
					));
					ButtonType standard = new ButtonType(Main.lang("wallet_d").formatted(1));
					ButtonType nonstandard = new ButtonType(Main.lang("wallet_d").formatted(2));
					prompt.getDialogPane().getButtonTypes().addAll(standard, nonstandard);
					prompt.setResultConverter(t -> {
						if (t == standard) return false;
						if (t == nonstandard) return true;
						return null;
					});
					Boolean isNonstandard = prompt.showForResult().orElse(null);
					if (isNonstandard == null) return;
					nonstandardDerivation = isNonstandard;
				} else if (!standardHas && nonstandardHas) {
					SatVoidDialog info = new SatVoidDialog();
					Utils.initDialog(info, root.getScene().getWindow(), MoveStyle.FOLLOW_OWNER);
					Label label = new Label(Main.lang("nonstandardDerivationNotice"));
					label.setMaxWidth(Screen.getPrimary().getBounds().getWidth() * 0.3);
					label.setWrapText(true);
					info.getDialogPane().setContent(label);
					info.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
					info.show();
					nonstandardDerivation = true;
				} else { // if there are no transactions in the nonstandard one. it does not matter whether the standard one has been used or not.
					nonstandardDerivation = false;
				}
			} catch (IOException ex) {
				throw new RuntimeException(ex);
			}
		}

		Wallet wallet = Wallet.create(path, mnemonic, walletName.getText(), walletPassword.getText().toCharArray(), nonstandardDerivation);

		// Search public addresses for transactions. If 2 unused addresses are found in a row, the search is cancelled.
		// This differs from the BIP44 standard which defines the maximum gap size as 20, but that could take too much time with the current setup.
		// TODO could be parallelized
		ArrayList<Integer> foundAddresses = new ArrayList<>();
		for (int index = 1, unused = 0;; index++) {
			try {
				Address address = wallet.publicAddress(index);
				boolean exists = api.getApiV1AddressesP1Transactions(address.toString(), 0, 1, true).execute().body().getTotal() > 0;
				if (!exists) {
					unused++;
					if (unused == 2) break;
				} else foundAddresses.add(index);
			} catch (IOException ex) {
				throw new RuntimeException(ex);
			}
		}
		for (int foundAddress : foundAddresses) {
			wallet.myAddresses.put(foundAddress, Main.lang("restoredAddress_d").formatted(foundAddress));
		}

		Main.get().setWallet(wallet);
		Main.get().displayWalletPage();
	}

	@FXML private Hyperlink showExtendedSeedPassphrase;

	@FXML
	public void showExtendedSeedPassphrase(ActionEvent actionEvent) {
		extendedSeedPassphrase.setVisible(true);
	}

	@Override
	public Parent content() {
		return root;
	}
}
