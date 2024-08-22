package com.satergo.extra.dialog;

import com.satergo.Icon;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.AccessibleRole;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Window;
import javafx.stage.WindowEvent;

import java.util.Map;
import java.util.WeakHashMap;

public class SatDialogPane extends StackPane {

	private final GridPane container;
	private final Button x;
	private Label headerLabel;
	private Node content;
	private final ButtonBar buttonBar;
	private final AbstractSatDialog<?, ?> dialog;

	private final ObservableList<ButtonType> buttons = FXCollections.observableArrayList();
	private final Map<ButtonType, Node> buttonNodes = new WeakHashMap<>();

	protected static final int
			TITLE_BAR_ROW = 0,
			HEADER_ROW = 1,
			CONTENT_ROW = 2,
			BUTTON_BAR_ROW = 3;

	private double xPos, yPos;
	private double xShift, yShift;
	private boolean dragging = false;

	private ChangeListener<Number> xHandler, yHandler;

	public SatDialogPane(AbstractSatDialog<?, ?> dialog) {
		this.dialog = dialog;
		setAccessibleRole(AccessibleRole.DIALOG);
		getStyleClass().add("sat-dialog-root");
		Region background = new Region();
		background.getStyleClass().add("sat-dialog-background");
		getChildren().add(background);

		container = new GridPane();
		container.getStyleClass().add("sat-dialog-container");
		x = new Button();
		x.setFocusTraversable(false);
		x.getStyleClass().add("sat-dialog-x");
		x.setOnAction(e -> {
			dialog.setResult(null);
			Event.fireEvent(getScene().getWindow(), new WindowEvent(getScene().getWindow(), WindowEvent.WINDOW_CLOSE_REQUEST));
		});
		x.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
		Icon icon = new Icon("times");
		x.setGraphic(icon);
		x.getStyleClass().addAll("transparent", "borderless", "less-wide");
		GridPane.setHalignment(x, HPos.RIGHT);
		ColumnConstraints columnConstraints = new ColumnConstraints();
		columnConstraints.setPercentWidth(100);
		container.getColumnConstraints().add(columnConstraints);

		installMoveSupport();

		RowConstraints contentConstraints = new RowConstraints();
		contentConstraints.setValignment(VPos.TOP);
		contentConstraints.setVgrow(Priority.ALWAYS);

		container.getRowConstraints().addAll(
				new RowConstraints(), // title bar
				new RowConstraints(), // header
				contentConstraints // content
		);

		buttons.addListener((ListChangeListener<ButtonType>) c -> {
			while (c.next()) {
				if (c.wasRemoved()) {
					for (ButtonType cmd : c.getRemoved()) {
						buttonNodes.remove(cmd);
					}
				}
				if (c.wasAdded()) {
					for (ButtonType cmd : c.getAddedSubList()) {
						if (!buttonNodes.containsKey(cmd)) {
							buttonNodes.put(cmd, createButton(cmd));
						}
					}
				}
			}
		});

		buttonBar = new ButtonBar();
		buttonBar.setMaxWidth(Double.MAX_VALUE);

		updateButtons(buttonBar);
		getButtonTypes().addListener((ListChangeListener<ButtonType>) c -> updateButtons(buttonBar));

		HBox buttonBarContainer = new HBox(buttonBar);
		GridPane.setMargin(buttonBarContainer, new Insets(20, 0, 0, 0));
		buttonBarContainer.setAlignment(Pos.CENTER);
		container.add(buttonBarContainer, 0, BUTTON_BAR_ROW);
		container.add(x, 0, TITLE_BAR_ROW);

		getChildren().add(container);
	}

	protected Node createButton(ButtonType buttonType) {
		final Button button = new Button(buttonType.getText());
		final ButtonBar.ButtonData buttonData = buttonType.getButtonData();
		ButtonBar.setButtonData(button, buttonData);
		button.setDefaultButton(buttonData.isDefaultButton());
		button.setCancelButton(buttonData.isCancelButton());
		button.addEventHandler(ActionEvent.ACTION, ae -> {
			if (ae.isConsumed()) return;
			if (dialog != null) {
				dialog.setResultAndClose(buttonType, true);
			}
		});

		return button;
	}

	private void updateButtons(ButtonBar buttonBar) {
		buttonBar.getButtons().clear();

		boolean hasDefault = false;
		for (ButtonType cmd : getButtonTypes()) {
			Node button = buttonNodes.get(cmd);

			// keep only first default button
			if (button instanceof Button) {
				ButtonBar.ButtonData buttonType = cmd.getButtonData();

				((Button)button).setDefaultButton(!hasDefault && buttonType != null && buttonType.isDefaultButton());
				((Button)button).setCancelButton(buttonType != null && buttonType.isCancelButton());

				hasDefault |= buttonType != null && buttonType.isDefaultButton();
			}
			buttonBar.getButtons().add(button);
		}
	}

	public ObservableList<ButtonType> getButtonTypes() {
		return buttons;
	}

	public final Node lookupButton(ButtonType buttonType) {
		return buttonNodes.get(buttonType);
	}

	public void setHeaderText(String text) {
		if (headerLabel != null)
			container.getChildren().remove(headerLabel);
		if (text == null) return;
		headerLabel = new Label(text);
		headerLabel.getStyleClass().add("sat-dialog-header");
		GridPane.setMargin(headerLabel, new Insets(10, 0, 10, 0));
		container.add(headerLabel, 0, HEADER_ROW);
	}

	public void setContent(Node node) {
		if (this.content != null)
			container.getChildren().remove(this.content);
		this.content = node;
		container.add(node, 0, CONTENT_ROW);
	}

	public Node getContent() {
		return content;
	}

	private void installMoveSupport() {
		setOnMousePressed(e -> {
			if (e.getY() > x.getBoundsInParent().getMaxY()) return;
			if (dialog.getMoveStyle() == MoveStyle.MOVABLE) {
				xPos = dialog.getX() - e.getScreenX();
				yPos = dialog.getY() - e.getScreenY();
				dragging = true;
			}
		});

		setOnMouseDragged(e -> {
			if (e.getY() > x.getBoundsInParent().getMaxY() && !dragging) return;
			if (dialog.getMoveStyle() == MoveStyle.MOVABLE) {
				dialog.setX(e.getScreenX() + xPos);
				dialog.setY(e.getScreenY() + yPos);
			}
		});

		setOnMouseReleased(e -> dragging = false);

		dialog.moveStyleProperty().addListener((observable, oldValue, mv) -> {
			if (mv == MoveStyle.FOLLOW_OWNER) {
				Window owner = dialog.getOwner();
				if (owner == null)
					throw new IllegalStateException("FOLLOW_OWNER cannot be used before initOwner has been called.");
				owner.xProperty().addListener(xHandler = (obs, old, value) -> dialog.setX((double) value + xShift));
				owner.yProperty().addListener(yHandler = (obs, old, value) -> dialog.setY((double) value + yShift));
			} else {
				if (xHandler != null && yHandler != null) {
					dialog.getOwner().xProperty().removeListener(xHandler);
					dialog.getOwner().yProperty().removeListener(yHandler);
					xHandler = yHandler = null;
				}
			}
		});

		dialog.addEventHandler(WindowEvent.WINDOW_SHOWN, e -> {
			if (dialog.getMoveStyle() == MoveStyle.FOLLOW_OWNER) {
				xShift = dialog.getX() - dialog.getOwner().getX();
				yShift = dialog.getY() - dialog.getOwner().getY();
			}
		});
	}
}
