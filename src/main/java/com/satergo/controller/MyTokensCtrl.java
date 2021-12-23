package com.satergo.controller;

import com.satergo.Main;
import com.satergo.Utils;
import com.satergo.ergo.TokenBalance;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;

import java.math.BigDecimal;
import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

public class MyTokensCtrl implements Initializable, WalletTab {

	@FXML private GridPane root;

	private void update(List<TokenBalance> tokens) {
		root.getChildren().clear();
		for (int i = 0; i < tokens.size(); i++) {
			TokenBalance token = tokens.get(i);
			Label nameLabel = new Label(token.name() == null ? Main.lang("unnamed_parentheses") : token.name());
			root.add(nameLabel, 0, i);
			GridPane.setHgrow(nameLabel, Priority.ALWAYS);
			root.add(new Label(new BigDecimal(token.amount()).movePointLeft(token.decimals()).toPlainString()), 1, i);
			Button copy = new Button(Main.lang("copyTokenId"));
			copy.setOnAction(e -> Utils.copyStringToClipboard(token.id()));
			root.add(copy, 2, i);
		}
	}

	@Override
	public void initialize(URL location, ResourceBundle resources) {
		update(Main.get().getWallet().lastKnownBalance.get().confirmedTokens());
		Main.get().getWallet().lastKnownBalance.addListener((observable, oldValue, newValue) -> update(newValue.confirmedTokens()));
	}
}
