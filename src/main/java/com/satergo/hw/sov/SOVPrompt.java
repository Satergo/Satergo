package com.satergo.hw.sov;

import com.satergo.Utils;
import com.satergo.extra.dialog.SatPromptDialog;
import com.welie.blessed.BluetoothPeripheral;
import javafx.application.Platform;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import org.ergoplatform.ErgoLikeTransaction;
import org.ergoplatform.appkit.*;
import org.ergoplatform.appkit.impl.BlockchainContextBase;
import org.ergoplatform.appkit.impl.InputBoxImpl;
import org.ergoplatform.appkit.impl.SignedTransactionImpl;
import org.ergoplatform.appkit.impl.UnsignedTransactionImpl;
import org.ergoplatform.sdk.wallet.secrets.ExtendedPublicKey;
import scala.collection.JavaConverters;
import sigmastate.interpreter.ProverResult;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

@SuppressWarnings("FieldCanBeLocal")
public sealed interface SOVPrompt {


	final class Connect extends SatPromptDialog<SOVComm> implements SOVPrompt {

		public Connect() {
			setHeaderText("Initializing Bluetooth manager...");
			SOVFinder sovFinder = new SOVFinder() {
				@Override
				public void discovered(BluetoothPeripheral peripheral) {
					Platform.runLater(() -> setHeaderText("Discovered! " + peripheral.getName()));
					connectToDiscovered();
				}

				@Override
				public void connected(SOVComm sovComm) {
					Platform.runLater(() -> setResult(sovComm));
				}
			};
			setHeaderText("Starting scan");
			sovFinder.scan();
			setHeaderText("Scanning... Open the app on your mobile device");
		}
	}

	final class ExtPubKey extends SatPromptDialog<ExtendedPublicKey> implements SOVPrompt {

		public ExtPubKey(SOVComm sovComm) {
			setHeaderText("Waiting for action on the mobile app");
			getDialogPane().setContent(new Label("Get public key"));
			sovComm.extendedPublicKey().handle((extendedPublicKey, throwable) -> {
				Platform.runLater(() -> {
					if (throwable != null) {
						Utils.alertUnexpectedException(throwable);
					} else {
						setResult(extendedPublicKey);
					}
				});
				return null;
			});
		}
	}

	final class Sign extends SatPromptDialog<SignedTransaction> implements SOVPrompt {

		public Sign(SOVComm sovComm, UnsignedTransaction unsignedTx, BlockchainContext ctx) {
			setHeaderText("Waiting for action on the mobile app");
			getDialogPane().setContent(new Label("Sign transaction"));
			ReducedTransaction reducedTx = ctx.newProverBuilder().build().reduce(unsignedTx, 0);
			sovComm.sendSignRequest(reducedTx.toBytes());
			sovComm.getSignatures().handle((signatures, throwable) -> {
				if (throwable != null) {
					Utils.alertUnexpectedException(throwable);
					return null;
				}
				ArrayList<ProverResult> proofs = new ArrayList<>();
				List<InputBox> inputs = unsignedTx.getInputs();
				if (signatures.size() != inputs.size())
					throw new IllegalStateException();
				for (int i = 0; i < inputs.size(); i++) {
					proofs.add(new ProverResult(signatures.get(i), ((InputBoxImpl) inputs.get(i)).getExtension()));
				}
				ErgoLikeTransaction signed = ((UnsignedTransactionImpl) unsignedTx).getTx().toSigned(JavaConverters.asScalaBuffer(proofs).toIndexedSeq());
				setResult(new SignedTransactionImpl((BlockchainContextBase) ctx, signed, 0));
				return null;
			});
		}
	}
}
