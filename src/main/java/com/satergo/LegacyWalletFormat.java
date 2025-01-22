package com.satergo;

import com.satergo.extra.OldAESEncryption;
import org.ergoplatform.appkit.Address;
import org.ergoplatform.appkit.Mnemonic;
import org.ergoplatform.sdk.SecretString;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.TreeMap;

/**
 * The version 0 of the wallet format. This version had no magic number.
 * It was dropped in version 0.0.3, which was released in 22 June 2022.
 */
class LegacyWalletFormat {
	private LegacyWalletFormat() {}

	static Wallet decrypt(byte[] encrypted, Path path, char[] password) throws IOException, GeneralSecurityException {
		ByteBuffer buffer = ByteBuffer.wrap(encrypted);
		// skip the "initialization vector length" field which is always 12 (int)
		buffer.position(4);
		// Format version 0 used the same 12 bytes for IV and PBKDF2 salt
		byte[] ivAndSalt = new byte[12];
		buffer.get(ivAndSalt);
		byte[] encryptedBytes = new byte[buffer.remaining()];
		buffer.get(encryptedBytes);
		byte[] decrypted = OldAESEncryption.INSTANCE.decryptData(ivAndSalt, OldAESEncryption.INSTANCE.generateSecretKey(password, ivAndSalt), encryptedBytes);
		try (DataInputStream old = new DataInputStream(new ByteArrayInputStream(decrypted))) {
			old.skipNBytes(6);
			long formatVersion = old.readLong();
			return deserializeDecryptedData(formatVersion, old.readAllBytes(), path, password);
		} catch (StreamCorruptedException | EOFException e) {
			throw new IllegalArgumentException("Invalid wallet data");
		}
	}

	private static Wallet deserializeDecryptedData(long formatVersion, byte[] bytes, Path path, char[] password) throws UnsupportedOperationException, IOException {
		if (formatVersion != 0)
			throw new IllegalArgumentException("The legacy wallet format was only version 0");
		try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
			String name = in.readUTF();
			SecretString seedPhrase = SecretString.create(in.readUTF());
			SecretString mnemonicPassword = SecretString.create(in.readUTF());
			int myAddressesSize = in.readInt();
			TreeMap<Integer, String> myAddresses = new TreeMap<>();
			for (int i = 0; i < myAddressesSize; i++) {
				myAddresses.put(in.readInt(), in.readUTF());
			}
			// It was for "address book size", but it was never used so it is 0 for all wallets
			in.readInt();
			// nonstandardDerivation is always true because the bug in the ergo-wallet cryptography library
			// was not yet discovered when formatVersion 0 was made
			Wallet wallet = Wallet.create(path, Mnemonic.create(seedPhrase, mnemonicPassword), name, password, true);
			wallet.myAddresses.putAll(myAddresses);
			return wallet;
		}
	}
}
