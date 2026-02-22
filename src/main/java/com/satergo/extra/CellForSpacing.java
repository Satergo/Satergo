package com.satergo.extra;

import javafx.beans.property.SimpleIntegerProperty;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import org.fxmisc.flowless.Cell;

/**
 * Cell implementation that takes a spacing that will be between the cells.
 * @param <T> Data type
 * @param <N> Content node type
 */
public class CellForSpacing<T, N> extends StackPane implements Cell<T, CellForSpacing<T, N>> {
	private final SimpleIntegerProperty index = new SimpleIntegerProperty();

	public CellForSpacing(Node content, int spacing, Orientation orientation) {
		getChildren().add(content);
		paddingProperty().bind(index.map(i -> new Insets(
				(int) i > 0 && orientation == Orientation.VERTICAL ? spacing : 0,
				0, 0,
				(int) i > 0 && orientation == Orientation.HORIZONTAL ? spacing : 0)));
	}

	@Override
	public void updateIndex(int index) {
		this.index.set(index);
	}

	@Override
	public CellForSpacing<T, N> getNode() {
		return this;
	}
}
