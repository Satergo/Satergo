package com.satergo.extra;

import javafx.beans.DefaultProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.value.WritableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.css.*;
import javafx.css.converter.SizeConverter;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Control;
import javafx.scene.control.Skin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A flexible tile pane contains a list of tiles that can vary in how many columns they span
 */
@DefaultProperty("nodes")
public class FlexibleTilePane extends Control {

	private final ObservableList<Node> nodes = FXCollections.observableArrayList();

	public FlexibleTilePane() {
		getStyleClass().add("flexible-tile-pane");
	}

	public FlexibleTilePane(Node... nodes) {
		this();
		this.nodes.setAll(nodes);
	}

	public FlexibleTilePane(int maxColumns, Node... nodes) {
		this(nodes);
		setMaxColumns(maxColumns);
	}

	@Override
	protected Skin<?> createDefaultSkin() {
		return new FlexibleTilePaneSkin(this);
	}

	/**
	 * The width of the horizontal gaps between columns.
	 */
	public final DoubleProperty hgapProperty() {
		if (hgap == null) {
			hgap = new StyleableDoubleProperty(0) {
				@Override  public void invalidated() { requestLayout(); }
				@Override public CssMetaData<FlexibleTilePane, Number> getCssMetaData() {
					return FlexibleTilePane.StyleableProperties.HGAP;
				}
				@Override public Object getBean() { return FlexibleTilePane.this; }
				@Override public String getName() { return "hgap"; }
			};
		}
		return hgap;
	}
	private DoubleProperty hgap;
	public final void setHgap(double value) { hgapProperty().set(value); }
	public final double getHgap() { return hgap == null ? 0 : hgap.get(); }

	/**
	 * The height of the vertical gaps between rows.
	 */
	public final DoubleProperty vgapProperty() {
		if (vgap == null) {
			vgap = new StyleableDoubleProperty(0) {
				@Override public void invalidated() { requestLayout(); }
				@Override public CssMetaData<FlexibleTilePane, Number> getCssMetaData() {
					return FlexibleTilePane.StyleableProperties.VGAP;
				}
				@Override public Object getBean() { return FlexibleTilePane.this; }
				@Override public String getName() { return "vgap"; }
			};
		}
		return vgap;
	}
	private DoubleProperty vgap;
	public final void setVgap(double value) { vgapProperty().set(value); }
	public final double getVgap() { return vgap == null ? 0 : vgap.get(); }

	/**
	 * The maximum amount of columns per row.
	 */
	public final IntegerProperty maxColumnsProperty() {
		if (maxColumns == null) {
			maxColumns = new StyleableIntegerProperty(0) {
				@Override  public void invalidated() { requestLayout(); }
				@Override public CssMetaData<FlexibleTilePane, Number> getCssMetaData() {
					return StyleableProperties.MAX_COLUMNS;
				}
				@Override public Object getBean() { return FlexibleTilePane.this; }
				@Override public String getName() { return "maxColumns"; }
			};
		}
		return maxColumns;
	}
	private IntegerProperty maxColumns;
	public int getMaxColumns() { return maxColumns == null ? 0 : maxColumns.get(); }
	public void setMaxColumns(int maxColumns) { maxColumnsProperty().set(maxColumns); }

	private static final String
			ROW_SPAN_CONSTRAINT = "flexible-tile-pane-row-span",
			COLUMN_SPAN_CONSTRAINT = "flexible-tile-pane-column-span",
			MARGIN_CONSTRAINT = "flexible-tile-pane-margin";

	public static void setRowSpan(Node node, int rowSpan) { setConstraint(node, ROW_SPAN_CONSTRAINT, rowSpan); }
	public static void setColumnSpan(Node node, int columnSpan) { setConstraint(node, COLUMN_SPAN_CONSTRAINT, columnSpan); }
	public static Integer getRowSpan(Node node) { return (Integer) getConstraint(node, ROW_SPAN_CONSTRAINT); }
	public static Integer getColumnSpan(Node node) { return (Integer) getConstraint(node, COLUMN_SPAN_CONSTRAINT); }
	public static Insets getMargin(Node node) { return (Insets) getConstraint(node, MARGIN_CONSTRAINT); }
	public static void setMargin(Node node, Insets margin) { setConstraint(node, MARGIN_CONSTRAINT, margin); }

	@SuppressWarnings("RedundantCast")
	private static class StyleableProperties {

		private static final CssMetaData<FlexibleTilePane, Number> HGAP = new CssMetaData<>("-fx-hgap", SizeConverter.getInstance(), 0.0) {
			@Override
			public boolean isSettable(FlexibleTilePane node) {
				return node.hgap == null || !node.hgap.isBound();
			}
			@Override
			public StyleableProperty<Number> getStyleableProperty(FlexibleTilePane node) {
				return (StyleableProperty<Number>) (WritableValue<Number>) node.hgapProperty();
			}
		};

		private static final CssMetaData<FlexibleTilePane, Number> VGAP = new CssMetaData<>("-fx-vgap", SizeConverter.getInstance(), 0.0) {
			@Override
			public boolean isSettable(FlexibleTilePane node) {
				return node.vgap == null || !node.vgap.isBound();
			}
			@Override
			public StyleableProperty<Number> getStyleableProperty(FlexibleTilePane node) {
				return (StyleableProperty<Number>) (WritableValue<Number>) node.vgapProperty();
			}
		};

		private static final CssMetaData<FlexibleTilePane, Number> MAX_COLUMNS = new CssMetaData<>("-fx-max-columns", SizeConverter.getInstance(), 0) {
			@Override
			public boolean isSettable(FlexibleTilePane node) {
				return node.vgap == null || !node.vgap.isBound();
			}
			@Override
			public StyleableProperty<Number> getStyleableProperty(FlexibleTilePane node) {
				return (StyleableProperty<Number>) (WritableValue<Number>) node.maxColumnsProperty();
			}
		};

		private static final List<CssMetaData<? extends Styleable, ?>> STYLEABLES;

		static {
			List<CssMetaData<? extends Styleable, ?>> styleables = new ArrayList<>(Control.getClassCssMetaData());
			styleables.add(HGAP);
			styleables.add(VGAP);

			STYLEABLES = Collections.unmodifiableList(styleables);
		}
	}

	public static List<CssMetaData<? extends Styleable, ?>> getClassCssMetaData() {
		return FlexibleTilePane.StyleableProperties.STYLEABLES;
	}

	public List<CssMetaData<? extends Styleable, ?>> getControlCssMetaData() {
		return getClassCssMetaData();
	}


	static void setConstraint(Node node, Object key, Object value) {
		if (value == null) {
			node.getProperties().remove(key);
		} else {
			node.getProperties().put(key, value);
		}
		if (node.getParent() != null) {
			node.getParent().requestLayout();
		}
	}

	static Object getConstraint(Node node, Object key) {
		if (node.hasProperties()) {
			return node.getProperties().get(key);
		}
		return null;
	}

	public ObservableList<Node> getNodes() {
		return nodes;
	}
}
