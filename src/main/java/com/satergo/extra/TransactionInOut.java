package com.satergo.extra;

import com.satergo.Main;
import com.satergo.Utils;
import javafx.geometry.Pos;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import org.ergoplatform.appkit.Address;

public final class TransactionInOut extends HBox {

	public enum Type {
		INPUT, OUTPUT
	}

	public TransactionInOut(Type type, Address address, String amount, boolean hasTokens, Runnable showTokens, boolean isMyAddress) {
		// Layout
		setSpacing(8);
		setAlignment(Pos.CENTER_LEFT);
		Label addressLabel = new Label();
		addressLabel.setMaxWidth(Double.POSITIVE_INFINITY);
		addressLabel.setTextOverrun(OverrunStyle.CENTER_ELLIPSIS);
		HBox.setHgrow(addressLabel, Priority.ALWAYS);
		HBox box = new HBox();
		box.setMinWidth(Region.USE_PREF_SIZE);
		box.setAlignment(Pos.CENTER_LEFT);
		Hyperlink tokens = new Hyperlink(Main.lang("[tokens]"));
		tokens.getStyleClass().add("text-color");
		tokens.managedProperty().bind(tokens.visibleProperty());
		Label space = new Label(" ");
		space.visibleProperty().bind(tokens.visibleProperty());
		space.managedProperty().bind(tokens.managedProperty());
		Label amountLabel = new Label();
		amountLabel.setOnContextMenuRequested(e -> {
			Utils.copyStringToClipboard(amount);
			Utils.showTemporaryTooltip(amountLabel, new Tooltip(Main.lang("copied")), Utils.COPIED_TOOLTIP_MS);
		});
		Label erg = new Label(" ERG");
		erg.setOnContextMenuRequested(amountLabel.getOnContextMenuRequested());
		box.getChildren().addAll(tokens, space, amountLabel, erg);
		Label arrow = new Label("\u27F6");
		arrow.setMinWidth(Region.USE_PREF_SIZE);
		if (type == Type.INPUT)
			getChildren().add(arrow);
		getChildren().addAll(addressLabel, box);
		if (type == Type.OUTPUT)
			getChildren().add(arrow);

		// Data
		addressLabel.setText(address.toString());
		Utils.addCopyContextMenu(addressLabel);
		if (isMyAddress) addressLabel.setUnderline(true);
		amountLabel.setText(amount);
		tokens.setVisible(hasTokens);
		tokens.setOnAction(e -> showTokens.run());
	}
}
