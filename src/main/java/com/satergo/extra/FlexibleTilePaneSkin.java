package com.satergo.extra;

import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.SkinBase;
import javafx.scene.layout.*;

import java.util.Objects;

import static com.satergo.extra.FlexibleTilePane.*;

public class FlexibleTilePaneSkin extends SkinBase<FlexibleTilePane> {

	private final GridPane gridPane = new GridPane();

	public FlexibleTilePaneSkin(FlexibleTilePane pane) {
		super(pane);

		gridPane.getStyleClass().add("grid-pane");
		gridPane.hgapProperty().bind(pane.hgapProperty());
		gridPane.vgapProperty().bind(pane.vgapProperty());

		InvalidationListener updateListener = (Observable it) -> updateView();
		pane.getChildrenUnmodifiable().addListener(updateListener);
		pane.maxColumnsProperty().addListener(updateListener);

		updateView();
		getChildren().add(gridPane);
	}

	private void updateView() {
		gridPane.getChildren().clear();
		gridPane.getColumnConstraints().clear();
		gridPane.getRowConstraints().clear();

		ObservableList<Node> nodes = getSkinnable().getNodes();

		int maxCol = getSkinnable().getMaxColumns();

		int x = 0, y = 0, currentMaxRowSpan = 0;
		for (Node node : nodes) {
			if (x + Objects.requireNonNullElse(getColumnSpan(node), 1) > maxCol) {
				y += currentMaxRowSpan;
				currentMaxRowSpan = 0;
				x = 0;
			}
			currentMaxRowSpan = Math.max(currentMaxRowSpan, Objects.requireNonNullElse(getRowSpan(node), 1));

			if (node instanceof Region) {
				((Region) node).setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
			}
			gridPane.add(node, x, y);

			GridPane.setColumnSpan(node, getColumnSpan(node));
			GridPane.setRowSpan(node, getRowSpan(node));
			GridPane.setMargin(node, getMargin(node));

			x += Objects.requireNonNullElse(getColumnSpan(node), 1);
		}

//		for (int col = 0; col < gridPane.getColumnCount(); col++) {
//			ColumnConstraints constraints = new ColumnConstraints();
//			constraints.setHgrow(Priority.ALWAYS);
//			constraints.setFillWidth(true);
//			gridPane.getColumnConstraints().add(constraints);
//		}

//		for (int row = 0; row < gridPane.getRowCount(); row++) {
//			RowConstraints constraints = new RowConstraints();
//			constraints.setVgrow(Priority.ALWAYS);
//			constraints.setFillHeight(true);
//			gridPane.getRowConstraints().add(constraints);
//		}
	}
}
