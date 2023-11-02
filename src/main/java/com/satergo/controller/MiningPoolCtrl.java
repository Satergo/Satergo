package com.satergo.controller;

import com.satergo.Main;
import com.satergo.ergo.MiningPool;
import com.satergo.extra.dialog.MoveStyle;
import com.satergo.extra.dialog.SatTextInputDialog;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import org.ergoplatform.appkit.Address;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

public class MiningPoolCtrl implements WalletTab, Initializable {

	@FXML private Label port, address;
	@FXML private Button changeAddress;
	private final MiningPool pool;

	public MiningPoolCtrl(MiningPool pool) {
		this.pool = pool;
	}

	@Override
	public void initialize(URL location, ResourceBundle resources) {
		port.setText(String.valueOf(pool.info.port()));
		address.setText(pool.info.address().toString());
	}

	@FXML
	public void changeAddress(ActionEvent e) throws IOException {
		SatTextInputDialog dialog = new SatTextInputDialog();
		dialog.initOwner(Main.get().stage());
		dialog.setMoveStyle(MoveStyle.FOLLOW_OWNER);
		Main.get().applySameTheme(dialog.getScene());
		dialog.setTitle("Mining payout address");

		String s = dialog.showForResult().orElse(null);
		if (s == null) return;
		Address a = Address.create(s);
		address.getStyleClass().add("strikethrough");
		changeAddress.setDisable(true);
		Main.node.configureMining(a, true);
		// could be null if user somehow logs out while it is updating, so wallet page becomes null
		NodeOverviewCtrl nodeTab = Main.get().getWalletPage() == null ? null : Main.get().getWalletPage().getTab("node");
		Main.node.stop();
		Main.node.waitForExit();
		Main.node = Main.get().nodeFromInfo();
		Main.node.start();
		if (nodeTab != null) {
			nodeTab.bindToProperties();
			nodeTab.transferLog();
			Main.get().getWalletPage().bindToNodeProperties();
		}
		address.getStyleClass().remove("strikethrough");
		changeAddress.setDisable(false);
	}
}
