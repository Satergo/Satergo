package com.satergo.extra.dialog;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.Node;
import javafx.scene.control.ButtonType;
import javafx.util.Callback;

/**
 * A dialog that contains inputs and returns a result value
 * @param <R> result type
 */
public class SatPromptDialog<R> extends AbstractSatDialog<SatDialogPane, R> {

	public SatPromptDialog() {
	}

	public SatPromptDialog(Node content) {
		super(content);
	}

	private final ObjectProperty<Callback<ButtonType, R>> resultConverterProperty
			= new SimpleObjectProperty<>(this, "resultConverter");
	public final ObjectProperty<Callback<ButtonType, R>> resultConverterProperty() {
		return resultConverterProperty;
	}
	public final Callback<ButtonType, R> getResultConverter() {
		return resultConverterProperty().get();
	}
	public final void setResultConverter(Callback<ButtonType, R> value) {
		this.resultConverterProperty().set(value);
	}

	@SuppressWarnings("unchecked")
	@Override
	public void setResultAndClose(ButtonType cmd, boolean close) {
		Callback<ButtonType, R> resultConverter = getResultConverter();

		R priorResultValue = getResult();
		R newResultValue;

		if (resultConverter == null) {
			// The choice to cast cmd to R here was a conscious decision, taking
			// into account the choices available to us. Firstly, to summarise the
			// issue, at this point here we have a null result converter, and no
			// idea how to convert the given ButtonType to R. Our options are:
			//
			// 1) We could throw an exception here, but this requires that all
			// developers who create a dialog set a result converter (at least
			// setResultConverter(buttonType -> (R) buttonType)). This is
			// non-intuitive and depends on the developer reading documentation.
			//
			// 2) We could set a default result converter in the resultConverter
			// property that does the identity conversion. This saves people from
			// having to set a default result converter, but it is a little odd
			// that the result converter is non-null by default.
			//
			// 3) We can cast the button type here, which is what we do. This means
			// that the result converter is null by default.
			//
			// In the case of option 1), developers will receive a NPE when the
			// dialog is closed, regardless of how it was closed. In the case of
			// option 2) and 3), the user unfortunately receives a ClassCastException
			// in their code. This is unfortunate as it is not immediately obvious
			// why the ClassCastException occurred, and how to resolve it. However,
			// we decided to take this later approach as it prevents the issue of
			// requiring all custom dialog developers from having to supply their
			// own result converters.
			newResultValue = (R) cmd;
		} else {
			newResultValue = resultConverter.call(cmd);
		}

		setResult(newResultValue);

		// fix for the case where we set the same result as what
		// was already set. We should still close the dialog, but
		// we need to special-case it here, as the result property
		// won't fire any event if the value won't change.
		if (close && priorResultValue == newResultValue) {
			close();
		}
	}

	@Override
	protected SatDialogPane createDialogPane() {
		return new SatDialogPane(this);
	}

}
