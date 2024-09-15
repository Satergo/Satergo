package com.satergo.extra.hw.ledger;

import com.satergo.Main;
import com.satergo.extra.SimpleTask;
import com.satergo.extra.dialog.SatPromptDialog;
import com.satergo.jledger.protocol.ergo.ErgoLedgerException;
import com.satergo.jledger.transport.hid4java.InvalidChannelException;
import javafx.event.ActionEvent;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import org.ergoplatform.appkit.InputBox;
import org.ergoplatform.appkit.impl.InputBoxImpl;
import org.ergoplatform.sdk.wallet.secrets.ExtendedPublicKey;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

public sealed interface LedgerPrompt {

	void setHeaderText(String text);
	void close();

	final class Connect extends SatPromptDialog<ErgoLedgerAppkit> implements LedgerPrompt {
		public Connect(int productId) {
			setHeaderText(Main.lang("ledger.pleaseConnectA_deviceName_device").formatted(LedgerSelector.getModelName(productId)));
		}
	}

	abstract sealed class WithRetry<T> extends SatPromptDialog<T> implements LedgerPrompt {
		private final ButtonType askAgain = new ButtonType(Main.lang("ledger.askAgain"), ButtonBar.ButtonData.BACK_PREVIOUS);

		protected WithRetry() {
			setResultConverter(buttonType -> {
				if (buttonType == ButtonType.CANCEL)
					return null;
				return getResult();
			});
		}

		private void displayAskAgain(RuntimeException e) {
			getDialogPane().getButtonTypes().setAll(askAgain, ButtonType.CANCEL);
			getDialogPane().lookupButton(askAgain).addEventFilter(ActionEvent.ACTION, event -> {
				resetState();
				request();
				event.consume();
			});
		}

		protected final void handleException(Throwable t) {
			if (t instanceof ErgoLedgerException e) {
				if (e.getSW() == ErgoLedgerException.SW_DENY) {
					setHeaderText(Main.lang("ledger.youDeniedTheRequest"));
					displayAskAgain(e);
				} else {
					setHeaderText(Main.lang("ledger.unknownError").formatted(t.getMessage()));
					setResult(null);
					throw e;
				}
			} else if (t instanceof InvalidChannelException e) {
				if (e.received == 0) {
					setHeaderText(Main.lang("ledger.deviceIsLocked"));
					displayAskAgain(e);
				}
			} else {
				setHeaderText(Main.lang("ledger.unknownError").formatted(t.getMessage()));
				throw t instanceof RuntimeException r ? r : new RuntimeException(t);
			}
		}

		protected abstract void resetState();
		protected abstract void request();
	}

	final class ExtPubKey extends WithRetry<ExtendedPublicKey> implements LedgerPrompt {
		private final ErgoLedgerAppkit ergoLedgerAppkit;

		public ExtPubKey(ErgoLedgerAppkit ergoLedgerAppkit) {
			this.ergoLedgerAppkit = ergoLedgerAppkit;
			resetState();
			setOnShown(event -> request());
		}

		@Override
		protected void resetState() {
			setHeaderText("Please approve the request on your Ledger device");
			getDialogPane().getButtonTypes().clear();
		}

		@Override
		protected void request() {
			new SimpleTask<>(ergoLedgerAppkit::requestParentExtendedPublicKey)
					.onSuccess(this::setResult)
					.onFail(this::handleException)
					.newThread();
		}
	}

	final class Attest extends WithRetry<List<AttestedBox>> implements LedgerPrompt {
		private final ErgoLedgerAppkit ergoLedgerAppkit;
		private final List<InputBox> inputBoxes;

		public Attest(ErgoLedgerAppkit ergoLedgerAppkit, List<InputBox> inputBoxes) {
			this.ergoLedgerAppkit = ergoLedgerAppkit;
			this.inputBoxes = inputBoxes;
			resetState();
			setOnShown(event -> request());
		}

		@Override
		protected void resetState() {
			setHeaderText("Please approve the request on your Ledger device");
			getDialogPane().getButtonTypes().clear();
		}

		@Override
		protected void request() {
			new SimpleTask<>(() -> {
				ArrayList<AttestedBox> attestedBoxes = new ArrayList<>();
				for (int i = 0; i < inputBoxes.size(); i++) {
					InputBox inputBox = inputBoxes.get(i);
					attestedBoxes.add(new AttestedBox(
							ergoLedgerAppkit.attestBox(inputBox),
							ErgoLedgerAppkit.serializeContextExtension(((InputBoxImpl) inputBox).getExtension())));
				}
				return attestedBoxes;
			}).onSuccess(this::setResult)
					.onFail(this::handleException)
					.newThread();
		}
	}

	final class Sign extends WithRetry<byte[]> implements LedgerPrompt {
		private final Callable<byte[]> request;

		public Sign(Callable<byte[]> request) {
			this.request = request;
			resetState();
			setOnShown(event -> request());
		}

		@Override
		protected void resetState() {
			setHeaderText("Please approve the signing request on your Ledger device");
			getDialogPane().getButtonTypes().clear();
		}

		@Override
		protected void request() {
			new SimpleTask<>(request)
					.onSuccess(this::setResult)
					.onFail(this::handleException)
					.newThread();
		}
	}
}
