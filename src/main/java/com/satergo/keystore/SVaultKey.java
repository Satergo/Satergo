package com.satergo.keystore;

import com.satergo.Main;
import com.satergo.Utils;
import com.satergo.hw.sov.SOVComm;
import com.satergo.hw.sov.SOVPrompt;
import com.satergo.extra.AESEncryption;
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

	private SOVComm sovComm;
	private ExtendedPublicKey parentExtPubKey;

	SVaultKey() {
		super(Type.SAT_OFFLINE_VAULT);
	}

	@Override
	public void initCaches(ByteBuffer data) {
		storedKeyBytes = new byte[KEY_LENGTH];
		data.get(storedKeyBytes);

		SOVPrompt.Connect connectionPrompt = new SOVPrompt.Connect();
		Utils.initDialog(connectionPrompt, Main.get().stage());
		connectionPrompt.setOnShown(event -> {

		});
		sovComm = connectionPrompt.showForResult().orElse(null);
		if (sovComm == null)
			throw new IllegalStateException("Failed");
		SOVPrompt.ExtPubKey keyPrompt = new SOVPrompt.ExtPubKey(sovComm);
		Utils.initDialog(keyPrompt, Main.get().stage());
		parentExtPubKey = keyPrompt.showForResult().orElse(null);
		if (parentExtPubKey == null)
			throw new IllegalStateException("Failed");
		if (!Arrays.equals(storedKeyBytes, parentExtPubKey.keyBytes()))
			throw new IllegalStateException("This wallet does not belong to this device");
	}

	private void initStoredKeyBytes(byte[] storedKeyBytes) {
		this.storedKeyBytes = storedKeyBytes;
	}

	public static SVaultKey create(ExtendedPublicKey parentExtPubKey, SOVComm sovComm, char[] password) {
		if (parentExtPubKey.keyBytes().length != KEY_LENGTH) throw new IllegalArgumentException("public key must be " + KEY_LENGTH + " bytes");
		if (parentExtPubKey.chainCode().length != 32) throw new IllegalArgumentException("chain code must be 32 bytes");
		SVaultKey key = new SVaultKey();
		key.parentExtPubKey = parentExtPubKey;
		key.sovComm = sovComm;
		try {
			byte[] iv = AESEncryption.generateNonce12();
			ByteBuffer buffer = ByteBuffer.allocate(2 + KEY_LENGTH)
					.putShort((short) ID)
					.put(parentExtPubKey.keyBytes());
			key.initEncryptedData(AESEncryption.encryptData(iv, AESEncryption.generateSecretKey(password, iv), buffer.array()));
			key.initStoredKeyBytes(parentExtPubKey.keyBytes());
			return key;
		} catch (GeneralSecurityException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public SignedTransaction sign(BlockchainContext ctx, UnsignedTransaction unsignedTx, Collection<Integer> addressIndexes) throws Failure {
		SOVPrompt.Sign prompt = new SOVPrompt.Sign(sovComm, unsignedTx, ctx);
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
	public WalletKey changedPassword(char[] currentPassword, char[] newPassword) throws Failure {
		return create(parentExtPubKey, sovComm, newPassword);
	}
}
