package com.satergo;

import org.ergoplatform.appkit.Address;
import org.ergoplatform.appkit.Mnemonic;
import org.ergoplatform.sdk.SecretString;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.TreeMap;

/**
 * The version 0 of the wallet format. This version had no magic number.
 * It was dropped in version 0.0.3, which was released in 22 June 2022.
 */
class LegacyWalletFormat {
	private LegacyWalletFormat() {}

	static Wallet deserializeDecryptedData(long formatVersion, byte[] bytes, Path path, char[] password) throws UnsupportedOperationException, IOException {
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
			int addressBookSize = in.readInt();
			HashMap<String, Address> addressBook = new HashMap<>();
			for (int i = 0; i < addressBookSize; i++) {
				addressBook.put(in.readUTF(), Address.create(in.readUTF()));
			}
			// nonstandardDerivation is always true because the bug in the ergo-wallet cryptography library
			// was not yet discovered when formatVersion 0 was made
			Wallet wallet = Wallet.create(path, Mnemonic.create(seedPhrase, mnemonicPassword), name, password, true);
			wallet.myAddresses.putAll(myAddresses);
			wallet.addressBook.putAll(addressBook);
			return wallet;
		}
	}
}
