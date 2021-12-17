package com.satergo.extra;

import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.TilePane;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

public class MnemonicPhraseOrderVerify extends TilePane {

	public final ArrayList<String> userOrder = new ArrayList<>();

	public MnemonicPhraseOrderVerify(String[] words) {
		ArrayList<String> shuffled = new ArrayList<>(List.of(words));
		Collections.shuffle(shuffled);
		for (String word : shuffled) {
			ToggleButton button = new ToggleButton(word);
			button.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
			button.selectedProperty().addListener((observable, oldValue, newValue) -> {
				if (newValue) {
					userOrder.add(word);
					onWordAdded.accept(word);
				} else {
					userOrder.remove(word);
					onWordRemoved.accept(word);
				}
				allSelected.set(words.length == userOrder.size());
				correct.set(Arrays.asList(words).equals(userOrder));
			});
			getChildren().add(button);
		}
		setHgap(4);
		setVgap(4);
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
