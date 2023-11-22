package com.satergo.extra.hw.ledger;

import com.satergo.controller.ledger.ErgoLedgerAppkit;
import com.satergo.extra.SimpleTask;
import com.satergo.extra.dialog.SatPromptDialog;
import com.satergo.extra.dialog.SatVoidDialog;
import com.satergo.jledger.protocol.ergo.ErgoLedgerException;
import javafx.event.ActionEvent;
import javafx.scene.control.ButtonType;
import org.ergoplatform.wallet.secrets.ExtendedPublicKey;

public sealed interface LedgerPrompt {

	void setHeaderText(String text);
	void close();

	final class Connection extends SatVoidDialog implements LedgerPrompt {
		public Connection(int productId) {
			setHeaderText("Please connect a " + LedgerSelector.getModelName(productId) + " device.");
		}
	}

	final class ExtPubKey extends SatPromptDialog<ExtendedPublicKey> implements LedgerPrompt {
		private final ButtonType askAgain = new ButtonType("Ask again");
		private final ErgoLedgerAppkit ergoLedgerAppkit;

		public ExtPubKey(ErgoLedgerAppkit ergoLedgerAppkit) {
			this.ergoLedgerAppkit = ergoLedgerAppkit;
			setHeaderText("Please accept the request");
			setOnShown(event -> request());
		}

		private void request() {
			new SimpleTask<>(ergoLedgerAppkit::requestParentExtendedPublicKey)
					.onSuccess(this::setResult)
					.onFail(e -> {
						if (e instanceof ErgoLedgerException el) {
							if (el.getSW() == ErgoLedgerException.SW_DENY) {
								setHeaderText("You denied the request");
								getDialogPane().getButtonTypes().addAll(askAgain, ButtonType.CLOSE);
								getDialogPane().lookupButton(askAgain).addEventFilter(ActionEvent.ACTION, event -> {
									request();
									event.consume();
								});
								getDialogPane().lookupButton(ButtonType.CLOSE).addEventFilter(ActionEvent.ACTION, event -> {
									throw el;
								});
							} else {
								setHeaderText("Unknown error (" + e.getMessage() + ")");
								setResult(null);
								throw el;
							}
						} else {
							throw new RuntimeException(e);
						}
					}).newThread();
		}
	}

	final class Signing extends SatVoidDialog implements LedgerPrompt {
		public Signing() {
			setHeaderText("Please accept the request");
		}
	}

	final class UserRejectionException extends RuntimeException {}
}
