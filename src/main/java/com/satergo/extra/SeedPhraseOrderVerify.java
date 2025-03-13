package com.satergo.extra;

import com.satergo.Main;
import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Region;
import javafx.scene.layout.TilePane;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

public class SeedPhraseOrderVerify extends TilePane {

	private static final int COL_PER_ROW = 5;

	public final ArrayList<String> userOrder = new ArrayList<>();

	public SeedPhraseOrderVerify(List<String> words) {
		if (Platform.isAccessibilityActive()) {
			setPrefColumns(1);
			TextField accessibleInput = new TextField();
			accessibleInput.setPrefWidth(Region.USE_COMPUTED_SIZE);
			accessibleInput.setPromptText(Main.lang("enterSeedPhraseWordsInOrder"));
			accessibleInput.textProperty().addListener((observable, oldValue, newValue) -> {
				userOrder.clear();
				Collections.addAll(userOrder, newValue.strip().split("\\s+"));
				correct.set(userOrder.equals(words));
			});
			getChildren().add(accessibleInput);
			return;
		}
		getStyleClass().add("seed-phrase-tiles");
		ArrayList<String> shuffled = new ArrayList<>(words);
		Collections.shuffle(shuffled);
		for (String word : shuffled) {
			ToggleButton button = new ToggleButton(word);
			button.getStyleClass().add("seed-phrase-word");
			button.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
			button.selectedProperty().addListener((observable, oldValue, newValue) -> {
				if (newValue) {
					userOrder.add(word);
					onWordAdded.accept(word);
				} else {
					userOrder.remove(word);
					onWordRemoved.accept(word);
				}
				allSelected.set(words.size() == userOrder.size());
				correct.set(words.equals(userOrder));
			});
			getChildren().add(button);
		}
		setPrefColumns(COL_PER_ROW);
		addEventFilter(KeyEvent.KEY_PRESSED, e -> {
			int index = getChildren().indexOf((ToggleButton) e.getTarget());
			switch (e.getCode()) {
				case UP -> { if (index >= COL_PER_ROW) getChildren().get(index - COL_PER_ROW).requestFocus(); }
				case LEFT -> { if (index > 0) getChildren().get(index - 1).requestFocus(); }
				case DOWN -> { if (index < words.size() - COL_PER_ROW) getChildren().get(index + COL_PER_ROW).requestFocus(); }
				case RIGHT -> { if (index < words.size() - 1) getChildren().get(index + 1).requestFocus(); }
			}
		});
	}

	public Consumer<String> onWordAdded = w -> {}, onWordRemoved = w -> {};

	private final SimpleBooleanProperty allSelected = new SimpleBooleanProperty(false);
	private final SimpleBooleanProperty correct = new SimpleBooleanProperty(false);

	public SimpleBooleanProperty allSelectedProperty() {
		return allSelected;
	}

	public SimpleBooleanProperty correctProperty() {
		return correct;
	}

	public boolean isCorrect() {
		return correct.get();
	}
}
