package com.satergo.extra.dialog;

import javafx.scene.Node;
import javafx.scene.control.ButtonType;

public class SatVoidDialog extends AbstractSatDialog<SatDialogPane, ButtonType> {
	public SatVoidDialog() {
	}

	public SatVoidDialog(Node content) {
		super(content);
	}

	@Override
	protected SatDialogPane createDialogPane() {
		return new SatDialogPane(this);
	}

	@Override
	public void setResultAndClose(ButtonType cmd, boolean close) {
		ButtonType priorResultValue = getResult();

		setResult(cmd);

		// fix for the case where we set the same result as what
		// was already set. We should still close the dialog, but
		// we need to special-case it here, as the result property
		// won't fire any event if the value won't change.
		if (close && priorResultValue == cmd) {
			close();
		}
	}
}
