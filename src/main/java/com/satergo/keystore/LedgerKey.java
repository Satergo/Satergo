package com.satergo.keystore;

import com.satergo.Main;
import com.satergo.Utils;
import com.satergo.extra.EncryptedData;
import com.satergo.extra.Encryption;
import com.satergo.extra.dialog.MoveStyle;
import com.satergo.hw.ledger.AttestedBox;
import com.satergo.hw.ledger.ErgoLedgerAppkit;
import com.satergo.hw.ledger.LedgerPrompt;
import com.satergo.hw.ledger.LedgerFinder;
import com.satergo.jledger.protocol.ergo.ErgoNetworkType;
import com.satergo.jledger.protocol.ergo.ErgoProtocol;
import com.satergo.jledger.transport.hid4java.Hid4javaLedgerDevice;
import com.satergo.jledger.transport.hid4java.InvalidChannelException;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import org.ergoplatform.ErgoAddressEncoder;
import org.ergoplatform.ErgoLikeTransaction;
import org.ergoplatform.P2PKAddress;
import org.ergoplatform.appkit.*;
import org.ergoplatform.appkit.impl.BlockchainContextBase;
import org.ergoplatform.appkit.impl.InputBoxImpl;
import org.ergoplatform.appkit.impl.SignedTransactionImpl;
import org.ergoplatform.appkit.impl.UnsignedTransactionImpl;
import org.ergoplatform.sdk.wallet.secrets.DerivationPath;
import org.ergoplatform.sdk.wallet.secrets.ExtendedPublicKey;
import org.hid4java.HidDevice;
import scala.collection.JavaConverters;
import sigmastate.interpreter.ProverResult;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * The public key is saved to the wallet file, the chain code is not so deriving solely from the wallet file + password is not possible
 * Each time a wallet of this type is opened, the Ledger must be connected and the user must accept ext pub key export
 * Using the pub key the wallet on the Ledger and the one that was used to create this key can be compared
 */
public class LedgerKey extends WalletKey {
	private static final int ID = 54;

	private static final int PUBLIC_KEY_LENGTH = 33;

	private ErgoLedgerAppkit ergoLedgerAppkit;
	private ExtendedPublicKey parentExtPubKey;

	private int productId;
	private byte[] storedPublicKeyBytes;

	LedgerKey() {
		super(Type.LEDGER);
	}

	@Override
	public void initCaches(ByteBuffer data) throws WalletOpenException {
		productId = Short.toUnsignedInt(data.getShort());
		storedPublicKeyBytes = new byte[PUBLIC_KEY_LENGTH];
		data.get(storedPublicKeyBytes);
		LedgerPrompt.Connect connectionPrompt = new LedgerPrompt.Connect(productId);
		Utils.initDialog(connectionPrompt, Main.get().stage(), MoveStyle.FOLLOW_OWNER);
		connectionPrompt.setOnShown(event -> {
			LedgerFinder ledgerFinder = new LedgerFinder() {
				@Override
				public void deviceFound(HidDevice hidDevice) {
					Platform.runLater(() -> {
						connectionPrompt.setResult(new ErgoLedgerAppkit(new ErgoProtocol(new Hid4javaLedgerDevice(hidDevice))));
						connectionPrompt.close();
					});
				}
				@Override
				public void deviceDetached(HidDevice hidDevice) {
					Platform.runLater(() -> {
						if (logoutFromWallet()) {
							Utils.alert(Alert.AlertType.ERROR, Main.lang("ledger.lostConnection"));
						}
					});
				}
			};
			ledgerFinder.startListener();
		});
		ergoLedgerAppkit = connectionPrompt.showForResult().orElse(null);
		if (ergoLedgerAppkit == null)
			throw new IllegalStateException();
		try {
			ergoLedgerAppkit.device.open();
		} catch (Exception e) {
			throw new WalletOpenException(Main.lang("ledger.failedToOpenConnection").formatted(e.getMessage()));
		}
		if (!ergoLedgerAppkit.isAppOpen()) {
			throw new WalletOpenException(Main.lang("ledger.openAppFirst"));
		}
		LedgerPrompt.ExtPubKey prompt = new LedgerPrompt.ExtPubKey(ergoLedgerAppkit);
		Utils.initDialog(prompt, Main.get().stage(), MoveStyle.FOLLOW_OWNER);
		ExtendedPublicKey parentExtPubKey = prompt.showForResult().orElse(null);
		if (parentExtPubKey == null)
			throw new WalletOpenException(Main.lang("ledger.youDeniedTheRequest"));
		if (!Arrays.equals(storedPublicKeyBytes, parentExtPubKey.keyBytes()))
			throw new WalletOpenException(Main.lang("ledger.walletDoesNotBelong"));
		this.parentExtPubKey = parentExtPubKey;
	}

	private void initStoredKeyBytes(byte[] storedKeyBytes) {
		this.storedPublicKeyBytes = storedKeyBytes;
	}

	public static LedgerKey create(ExtendedPublicKey parentExtPubKey, ErgoLedgerAppkit ergoLedgerAppkit, char[] password, Encryption encryption) {
		if (parentExtPubKey.keyBytes().length != PUBLIC_KEY_LENGTH)
			throw new IllegalArgumentException("public key must be " + PUBLIC_KEY_LENGTH + " bytes");
		if (parentExtPubKey.chainCode().length != 32) throw new IllegalArgumentException("chain code must be 32 bytes");
		LedgerKey key = new LedgerKey();
		key.parentExtPubKey = parentExtPubKey;
		key.ergoLedgerAppkit = ergoLedgerAppkit;
		try {
			ByteBuffer buffer = ByteBuffer.allocate(2 + 2 + PUBLIC_KEY_LENGTH)
					.putShort((short) ID)
					.putShort((short) ergoLedgerAppkit.device.getProductId())
					.put(parentExtPubKey.keyBytes());
			byte[] salt = Encryption.secureRandom(16);
			byte[] iv = Encryption.secureRandom(12);
			byte[] encrBytes = encryption.encryptData(iv, encryption.generateSecretKey(password, salt), buffer.array());
			key.initEncryptedData(encryption, new EncryptedData(salt, iv, encrBytes));
			key.initStoredKeyBytes(parentExtPubKey.keyBytes());
			return key;
		} catch (GeneralSecurityException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public SignedTransaction sign(BlockchainContext ctx, UnsignedTransaction unsignedTx, Collection<Integer> addressIndexes, Integer changeAddress) throws Failure {
		try {
			NetworkType networkType = Main.programData().nodeNetworkType.get();
			LedgerPrompt.Attest attestPrompt = new LedgerPrompt.Attest(ergoLedgerAppkit, unsignedTx.getInputs());
			Utils.initDialog(attestPrompt, Main.get().stage(), MoveStyle.FOLLOW_OWNER);
			List<AttestedBox> inputBoxes = attestPrompt.showForResult().orElse(null);
			// not approved
			if (inputBoxes == null)
				throw new Failure();

			List<DerivationPath> inputPaths = unsignedTx.getInputs().stream().map(input -> {
				Address address = Address.fromErgoTree(input.getErgoTree(), networkType);
				int index = addressIndexes.stream().filter(i -> derivePublicAddress(networkType, i).equals(address)).findAny().orElseThrow();
				return parentExtPubKey.child(index).path();
			}).toList();
			LedgerPrompt.Sign prompt = new LedgerPrompt.Sign(() ->
					ergoLedgerAppkit.signTransaction(switch (networkType) {
								case MAINNET -> ErgoNetworkType.MAINNET;
								case TESTNET -> ErgoNetworkType.TESTNET;
							}, inputBoxes, inputPaths, unsignedTx.getDataInputs(), unsignedTx.getOutputs(),
							changeAddress == null ? null : derivePublicAddress(networkType, changeAddress),
							changeAddress == null ? null : parentExtPubKey.child(changeAddress).path()));
			Utils.initDialog(prompt, Main.get().stage(), MoveStyle.FOLLOW_OWNER);
			List<byte[]> signatures = prompt.showForResult().orElse(null);
			// not approved
			if (signatures == null)
				throw new Failure();

			ArrayList<ProverResult> proverResults = new ArrayList<>();
			List<InputBox> inputs = unsignedTx.getInputs();
			for (int i = 0; i < inputs.size(); i++) {
				proverResults.add(new ProverResult(signatures.get(i), ((InputBoxImpl) inputs.get(i)).getExtension()));
			}
			ErgoLikeTransaction signed = ((UnsignedTransactionImpl) unsignedTx).getTx()
					.toSigned(JavaConverters.asScalaBuffer(proverResults).toIndexedSeq());
			return new SignedTransactionImpl((BlockchainContextBase) ctx, signed, 0);
		} catch (InvalidChannelException e) {
			if (e.received == 0) {
				Utils.alert(Alert.AlertType.ERROR, Main.lang("ledger.deviceIsLocked"));
				throw new Failure();
			} else throw e;
		}
	}

	@Override
	public SignedTransaction signReduced(BlockchainContext ctx, ReducedTransaction reducedTx, int baseCost, Collection<Integer> addressIndexes) throws Failure {
		throw new UnsupportedOperationException();
	}

	@Override
	public Address derivePublicAddress(NetworkType networkType, int index) {
		return new Address(P2PKAddress.apply(parentExtPubKey.child(index).key(), new ErgoAddressEncoder(networkType.networkPrefix)));
	}

	@Override
	public WalletKey changedPassword(char[] currentPassword, char[] newPassword) throws Failure {
		return create(parentExtPubKey, ergoLedgerAppkit, newPassword, encryption);
	}

	@Override
	public WalletKey changedEncryption(char[] currentPassword, Encryption encryption) throws Failure {
		return create(parentExtPubKey, ergoLedgerAppkit, currentPassword, encryption);
	}
}
