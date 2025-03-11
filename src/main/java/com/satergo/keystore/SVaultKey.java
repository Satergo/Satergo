package com.satergo.keystore;

import com.satergo.Main;
import com.satergo.Utils;
import com.satergo.extra.EncryptedData;
import com.satergo.extra.Encryption;
import com.satergo.hw.svault.SVaultComm;
import com.satergo.hw.svault.SVaultPrompt;
import javafx.scene.control.Alert;
import org.ergoplatform.ErgoAddressEncoder;
import org.ergoplatform.P2PKAddress;
import org.ergoplatform.appkit.*;
import org.ergoplatform.sdk.wallet.secrets.ExtendedPublicKey;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Collection;

public class SVaultKey extends WalletKey {

	private static final int ID = 50;

	private static final int KEY_LENGTH = 33;

	private byte[] storedKeyBytes;

	private SVaultComm svaultComm;
	private ExtendedPublicKey parentExtPubKey;

	SVaultKey() {
		super(Type.SVAULT);
	}

	@Override
	public void initCaches(ByteBuffer data) throws WalletOpenException {
		storedKeyBytes = new byte[KEY_LENGTH];
		data.get(storedKeyBytes);

		SVaultPrompt.Connect connectionPrompt = new SVaultPrompt.Connect();
		connectionPrompt.onDisconnected = (sVaultComm, status) -> {
			// Disconnection handler
			if (logoutFromWallet()) {
				Utils.alert(Alert.AlertType.ERROR, Main.lang("svault.lostConnection"));
			}
		};
		Utils.initDialog(connectionPrompt, Main.get().stage());
		svaultComm = connectionPrompt.showForResult().orElse(null);
		if (svaultComm == null)
			throw new WalletOpenException(Main.lang("svault.failedToFindOrConnect"));
		SVaultPrompt.ExtPubKey keyPrompt = new SVaultPrompt.ExtPubKey(svaultComm);
		Utils.initDialog(keyPrompt, Main.get().stage());
		parentExtPubKey = keyPrompt.showForResult().orElse(null);
		if (parentExtPubKey == null) {
			connectionPrompt.onDisconnected = null;
			svaultComm.close(true);
			throw new WalletOpenException(Main.lang("svault.youDeniedTheRequest"));
		}
		if (!Arrays.equals(storedKeyBytes, parentExtPubKey.keyBytes())) {
			connectionPrompt.onDisconnected = null;
			svaultComm.close(true);
			throw new WalletOpenException(Main.lang("svault.walletDoesNotBelong"));
		}
	}

	private void initStoredKeyBytes(byte[] storedKeyBytes) {
		this.storedKeyBytes = storedKeyBytes;
	}

	public static SVaultKey create(ExtendedPublicKey parentExtPubKey, SVaultComm svaultComm, char[] password, Encryption encryption) {
		if (parentExtPubKey.keyBytes().length != KEY_LENGTH) throw new IllegalArgumentException("public key must be " + KEY_LENGTH + " bytes");
		if (parentExtPubKey.chainCode().length != 32) throw new IllegalArgumentException("chain code must be 32 bytes");
		SVaultKey key = new SVaultKey();
		key.parentExtPubKey = parentExtPubKey;
		key.svaultComm = svaultComm;
		try {
			ByteBuffer buffer = ByteBuffer.allocate(2 + KEY_LENGTH)
					.putShort((short) ID)
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
		SVaultPrompt.Sign prompt = new SVaultPrompt.Sign(svaultComm, unsignedTx, addressIndexes, changeAddress, ctx);
		Utils.initDialog(prompt, Main.get().stage());
		return prompt.showForResult().orElse(null);
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
	public WalletKey changedPassword(char[] currentPassword, char[] newPassword) {
		return create(parentExtPubKey, svaultComm, newPassword, encryption);
	}

	@Override
	public WalletKey changedEncryption(char[] currentPassword, Encryption encryption) throws Failure {
		return create(parentExtPubKey, svaultComm, currentPassword, encryption);
	}

	@Override
	public void close() {
		svaultComm.close(true);
	}
}
