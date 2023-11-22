package com.satergo.extra.hw.ledger;

import com.satergo.controller.ledger.ErgoLedgerAppkit;
import com.satergo.extra.SimpleTask;
import com.satergo.extra.dialog.SatPromptDialog;
import com.satergo.extra.dialog.SatVoidDialog;
import com.satergo.jledger.protocol.ergo.ErgoLedgerException;
import javafx.event.ActionEvent;
import javafx.scene.control.ButtonType;
import org.ergoplatform.wallet.secrets.ExtendedPublicKey;

import java.util.concurrent.Callable;

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
					.onFail(t -> {
						if (t instanceof ErgoLedgerException e) {
							if (e.getSW() == ErgoLedgerException.SW_DENY) {
								setHeaderText("You denied the request");
								getDialogPane().getButtonTypes().addAll(askAgain, ButtonType.CANCEL);
								getDialogPane().lookupButton(askAgain).addEventFilter(ActionEvent.ACTION, event -> {
									request();
									event.consume();
								});
								getDialogPane().lookupButton(ButtonType.CANCEL).addEventFilter(ActionEvent.ACTION, event -> {
									throw e;
								});
							} else {
								setHeaderText("Unknown error (" + t.getMessage() + ")");
								setResult(null);
								throw e;
							}
						} else {
							throw new RuntimeException(t);
						}
					}).newThread();
		}
	}

	final class Signing extends SatPromptDialog<byte[]> implements LedgerPrompt {
		private final ButtonType askAgain = new ButtonType("Ask again");

		public Signing(Callable<byte[]> request) {
			setHeaderText("Please accept the request");
			setOnShown(event -> request(request));
		}

		private void request(Callable<byte[]> request) {
			new SimpleTask<>(request)
					.onSuccess(this::setResult)
					.onFail(t -> {
						if (t instanceof ErgoLedgerException e) {
							if (e.getSW() == ErgoLedgerException.SW_DENY) {
								setHeaderText("You denied the request");
								getDialogPane().getButtonTypes().addAll(askAgain, ButtonType.CANCEL);
								getDialogPane().lookupButton(askAgain).addEventFilter(ActionEvent.ACTION, event -> {
									request(request);
									event.consume();
								});
								getDialogPane().lookupButton(ButtonType.CANCEL).addEventFilter(ActionEvent.ACTION, event -> {
									throw e;
								});
							} else {
								setHeaderText("Unknown error (" + t.getMessage() + ")");
								setResult(null);
								throw e;
							}
						} else {
							throw new RuntimeException(t);
						}
					}).newThread();
		}
	}

	final class UserRejectionException extends RuntimeException {}
}
