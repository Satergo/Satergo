package com.satergo.controller;

import com.satergo.Main;
import com.satergo.Utils;
import com.satergo.ergo.TokenBalance;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import org.ergoplatform.appkit.ErgoId;

import java.math.BigDecimal;
import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

public class MyTokensCtrl implements Initializable, WalletTab {

	@FXML private GridPane root;

	private void update(List<TokenBalance> tokens) {
		root.getChildren().clear();
		for (int i = 0; i < 10; i++) {
			TokenBalance token = tokens.get(0);
			Label nameLabel = new Label(token.name() == null ? Main.lang("unnamed_parentheses") : token.name());
			ImageView icon = new ImageView(Utils.tokenIcon36x36(ErgoId.create(token.id())));
			// Give it a size so that even if there is no icon for this token, it will take the same amount of height as those with an icon
			icon.setFitWidth(36);
			icon.setFitHeight(36);
			root.add(icon, 0, i);
			root.add(nameLabel, 1, i);
			GridPane.setHgrow(nameLabel, Priority.ALWAYS);
			root.add(new Label(new BigDecimal(token.amount()).movePointLeft(token.decimals()).toPlainString()), 2, i);
			Button copy = new Button(Main.lang("copyTokenId"));
			copy.setOnAction(e -> Utils.copyStringToClipboard(token.id()));
			root.add(copy, 3, i);
		}
	}

	@Override
	public void initialize(URL location, ResourceBundle resources) {
		update(Main.get().getWallet().lastKnownBalance.get().confirmedTokens());
		Main.get().getWallet().lastKnownBalance.addListener((observable, oldValue, newValue) -> update(newValue.confirmedTokens()));
	}
}
