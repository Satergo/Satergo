package com.satergo.extra;

import com.satergo.Main;
import com.satergo.Utils;
import javafx.beans.NamedArg;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.MenuItem;

/**
 * A Hyperlink that opens a URI when clicked and also has a context menu for copying the link.
 */
public class LinkedHyperlink extends Hyperlink {

	private final SimpleStringProperty uri = new SimpleStringProperty();
	public void setUri(String uri) { this.uri.set(uri); }
	public String getUri() { return uri.get(); }
	public SimpleStringProperty uriProperty() { return uri; }

	public LinkedHyperlink() {
		ContextMenu contextMenu = new ContextMenu();
		MenuItem menuItem = new MenuItem(Main.lang("copyLink"));
		menuItem.setOnAction(e -> Utils.copyStringToClipboard(uri.get()));
		contextMenu.getItems().add(menuItem);
		setContextMenu(contextMenu);
	}

	public LinkedHyperlink(@NamedArg("uri") String uri) {
		this();
		setUri(uri);
	}

	@Override
	public void fire() {
		Utils.showDocument(uri.get());
	}
}
