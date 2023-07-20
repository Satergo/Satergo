package com.satergo.extra;

import com.satergo.controller.ledger.ErgoLedgerAppkit;
import com.satergo.extra.dialog.SatPromptDialog;
import com.satergo.extra.dialog.SatVoidDialog;
import com.satergo.jledger.protocol.ergo.ErgoLedgerException;
import javafx.event.ActionEvent;
import javafx.scene.control.ButtonType;
import org.ergoplatform.wallet.secrets.ExtendedPublicKey;

public interface LedgerPrompt {

	void setHeaderText(String text);
	void close();

	final class Connection extends SatVoidDialog implements LedgerPrompt {
		public Connection(int productId) {
			setHeaderText("Please connect a " + LedgerSelector.getModelName(productId) + " device.");
		}
	}

	final class ExtPubKey extends SatPromptDialog<ExtendedPublicKey> implements LedgerPrompt {
		private ButtonType askAgain = new ButtonType("Ask again"), exit = new ButtonType("Exit");
		private final ErgoLedgerAppkit ergoLedgerAppkit;

		public ExtPubKey(ErgoLedgerAppkit ergoLedgerAppkit) {
			this.ergoLedgerAppkit = ergoLedgerAppkit;
			setHeaderText("Please accept the request");
			setOnShown(event -> request());
		}

		private void request() {
			try {
				ExtendedPublicKey extendedPublicKey = ergoLedgerAppkit.requestParentExtendedPublicKey();
				setResult(extendedPublicKey);
			} catch (ErgoLedgerException e) {
				if (e.getSW() == 0x6985) {
					setHeaderText("You denied the request");
					getDialogPane().getButtonTypes().addAll(askAgain, exit);
					getDialogPane().lookupButton(askAgain).addEventFilter(ActionEvent.ACTION, event -> {
						request();
						event.consume();
					});
					getDialogPane().lookupButton(exit).addEventFilter(ActionEvent.ACTION, event -> {
						throw e;
					});
				}
				setHeaderText("Unknown error (" + e.getMessage() + ")");
				setResult(null);
				throw e;
			}

		}
	}

	final class Signing extends SatVoidDialog implements LedgerPrompt {
		public Signing() {
			setHeaderText("Please accept the request");
		}
	}

	final class UserRejectionException extends RuntimeException {}
}
