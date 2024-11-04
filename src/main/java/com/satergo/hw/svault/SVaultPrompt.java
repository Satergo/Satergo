package com.satergo.hw.svault;

import com.satergo.Main;
import com.satergo.Utils;
import com.satergo.controller.WalletCtrl;
import com.satergo.extra.dialog.SatPromptDialog;
import com.welie.blessed.BluetoothCommandStatus;
import com.welie.blessed.BluetoothPeripheral;
import javafx.application.Platform;
import javafx.scene.control.Alert;
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
import java.util.Collection;
import java.util.List;
import java.util.function.BiConsumer;

@SuppressWarnings("FieldCanBeLocal")
public sealed interface SVaultPrompt {


	final class Connect extends SatPromptDialog<SVaultComm> implements SVaultPrompt {

		public Connect(BiConsumer<SVaultComm, BluetoothCommandStatus> disconnectionHandler) {
			setHeaderText(Main.lang("svault.initializingBluetoothManager"));
			SVaultFinder svaultFinder = new SVaultFinder() {
				@Override
				public void discovered(BluetoothPeripheral peripheral) {
					Platform.runLater(() -> setHeaderText(Main.lang("svault.discoveredDevice").formatted(peripheral.getName())));
					connectToDiscovered();
				}

				@Override
				public void connected(SVaultComm svaultComm) {
					Platform.runLater(() -> setResult(svaultComm));
				}

				@Override
				public void disconnected(SVaultComm svaultComm, BluetoothCommandStatus status) {
					if (disconnectionHandler != null) disconnectionHandler.accept(svaultComm, status);
				}
			};
			setHeaderText(Main.lang("svault.startingScan"));
			svaultFinder.scan();
			setHeaderText(Main.lang("svault.scanning"));
		}
	}

	final class ExtPubKey extends SatPromptDialog<ExtendedPublicKey> implements SVaultPrompt {

		public ExtPubKey(SVaultComm svaultComm) {
			setHeaderText(Main.lang("svault.waitingForAction"));
			getDialogPane().setContent(new Label(Main.lang("svault.getPublicKey")));
			svaultComm.extendedPublicKey().handle((extendedPublicKey, throwable) -> {
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

	final class Sign extends SatPromptDialog<SignedTransaction> implements SVaultPrompt {

		public Sign(SVaultComm svaultComm, UnsignedTransaction unsignedTx, Collection<Integer> inputAddresses, Integer changeAddress, BlockchainContext ctx) {
			setHeaderText(Main.lang("svault.waitingForAction"));
			getDialogPane().setContent(new Label(Main.lang("svault.signTransaction")));
			ReducedTransaction reducedTx = ctx.newProverBuilder().build().reduce(unsignedTx, 0);
			svaultComm.sendSignRequest(reducedTx.toBytes(), inputAddresses, changeAddress).handle((unused, throwable) -> {
				if (throwable != null) Platform.runLater(() -> Utils.alertUnexpectedException(throwable));
				else svaultComm.getSignatures().handle((signatures, signThrowable) -> {
					Platform.runLater(() -> {
						if (signThrowable != null) {
							Utils.alertUnexpectedException(signThrowable);
							return;
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
					});
					return null;
				});
				return null;
			});
		}
	}
}
