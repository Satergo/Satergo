package com.satergo.controller;

import com.satergo.Main;
import com.satergo.Utils;
import com.satergo.Wallet;
import info.debatty.java.stringsimilarity.NormalizedLevenshtein;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.FlowPane;
import org.ergoplatform.appkit.Mnemonic;
import org.ergoplatform.appkit.SecretString;
import org.ergoplatform.wallet.mnemonic.WordList;
import scala.collection.JavaConverters;

import java.net.URL;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

public class RestoreFromSeedCtrl implements SetupPage.WithoutLanguage, Initializable {
	@FXML private Parent root;
	@FXML private TextArea mnemonicPhrase;
	@FXML private FlowPane suggestionContainer;
	@FXML private TextField walletName;
	@FXML private PasswordField mnemonicPassword;
	@FXML private PasswordField walletPassword;

	private List<String> allMnemonicWords;

	@Override
	public void initialize(URL location, ResourceBundle resources) {
		WordList wordList = WordList.load("english").get();
		allMnemonicWords = JavaConverters.seqAsJavaList(wordList.words());
		mnemonicPhrase.textProperty().addListener((observable, oldValue, newValue) -> {
			String[] words = newValue.split(" ", -1);
			if (words.length == 0) return;
			String last = words[words.length - 1];
			suggestionContainer.getChildren().clear();
			if (last.length() < 2) return;
			List<String> matches = allMnemonicWords.stream().filter(w -> w.startsWith(last)).sorted(Comparator.comparingInt(String::length)).collect(Collectors.toList());
			matches.stream().limit(20).forEach(w -> {
				Button button = new Button(w);
				button.setOnAction(e -> {
					String textWithoutLast = newValue.substring(0, Math.max(newValue.lastIndexOf(' '), 0));
					if (textWithoutLast.isEmpty()) mnemonicPhrase.setText(w + " ");
					else mnemonicPhrase.setText(textWithoutLast + " " + w + " ");
					mnemonicPhrase.requestFocus();
					mnemonicPhrase.positionCaret(mnemonicPhrase.getText().length());
				});
				suggestionContainer.getChildren().add(button);
			});
			if (matches.size() > 20) suggestionContainer.getChildren().add(new Label(Main.lang("dddAndMore")));
		});
		showMnemonicPassword.managedProperty().bind(showMnemonicPassword.visibleProperty());
		mnemonicPassword.managedProperty().bind(mnemonicPassword.visibleProperty());
		showMnemonicPassword.visibleProperty().bind(mnemonicPassword.visibleProperty().not());
		mnemonicPassword.focusedProperty().addListener((observable, oldValue, newValue) -> {
			if (!newValue && mnemonicPassword.getText().isEmpty())
				mnemonicPassword.setVisible(false);
		});
	}

	@FXML
	public void restore(ActionEvent e) {
		String[] words = mnemonicPhrase.getText().split(" ");
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
		Mnemonic mnemonic = Mnemonic.create(SecretString.create(mnemonicPhrase.getText()), SecretString.create(mnemonicPassword.getText()));
		Path path = Utils.fileChooserSave(Main.get().stage(), Main.lang("locationToSaveTo"), walletName.getText() + "." + Wallet.FILE_EXTENSION, Wallet.extensionFilter());
		if (path == null) return;
		Main.get().setWallet(Wallet.create(path, mnemonic, walletName.getText(), SecretString.create(walletPassword.getText())));
		Main.get().displayWalletPage();
	}

	@FXML private Hyperlink showMnemonicPassword;

	@FXML
	public void showMnemonicPassword(ActionEvent actionEvent) {
		mnemonicPassword.setVisible(true);
	}

	@Override
	public Parent content() {
		return root;
	}
}
