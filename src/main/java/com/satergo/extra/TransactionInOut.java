package com.satergo.extra;

import com.satergo.Main;
import com.satergo.Utils;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import org.ergoplatform.appkit.Address;

public final class TransactionInOut extends HBox {
	private final Label address;
	private final Hyperlink tokens;
	private final Label amount;

	public enum Type {
		INPUT, OUTPUT;
	}

	public TransactionInOut(Type type, Address address, String amount, boolean hasTokens, Runnable showTokens, boolean isMyAddress) {
		// Layout
		setSpacing(8);
		setAlignment(Pos.CENTER_LEFT);
		this.address = new Label();
		this.address.setMaxWidth(Double.POSITIVE_INFINITY);
		this.address.setTextOverrun(OverrunStyle.CENTER_ELLIPSIS);
		HBox.setHgrow(this.address, Priority.ALWAYS);
		HBox box = new HBox();
		box.setMinWidth(Region.USE_PREF_SIZE);
		box.setAlignment(Pos.CENTER_LEFT);
		this.tokens = new Hyperlink(Main.lang("[tokens]"));
		this.tokens.getStyleClass().add("text-color");
		this.tokens.managedProperty().bind(this.tokens.visibleProperty());
		Label space = new Label(" ");
		space.visibleProperty().bind(tokens.visibleProperty());
		space.managedProperty().bind(tokens.managedProperty());
		this.amount = new Label();
		Label erg = new Label(type == Type.OUTPUT ? " ERG " : " ERG");
		box.getChildren().addAll(this.tokens, space, this.amount, erg);
		Label arrow = new Label("\u27F6");
		arrow.setMinWidth(Region.USE_PREF_SIZE);
		if (type == Type.INPUT)
			getChildren().add(arrow);
		getChildren().addAll(this.address, box);
		if (type == Type.OUTPUT)
			getChildren().add(arrow);

		// Data
		this.address.setText(address.toString());
		Utils.addCopyContextMenu(this.address);
		if (isMyAddress) this.address.setUnderline(true);
		this.amount.setText(amount);
		this.tokens.setVisible(hasTokens);
		this.tokens.setOnAction(e -> showTokens.run());
	}
}
