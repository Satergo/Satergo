package com.satergo;

import com.satergo.ergo.Balance;
import com.satergo.ergo.ErgoInterface;
import com.satergo.extra.AESEncryption;
import com.satergo.extra.IncorrectPasswordException;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.MapChangeListener;
import javafx.collections.ObservableMap;
import javafx.stage.FileChooser;
import org.ergoplatform.appkit.*;

import javax.crypto.AEADBadTagException;
import javax.crypto.SecretKey;
import java.io.*;
import java.net.ConnectException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

public final class Wallet {

	public static final String FILE_EXTENSION = "erg";

	public static FileChooser.ExtensionFilter extensionFilter() {
		return new FileChooser.ExtensionFilter(Main.lang("walletFile"), "*." + Wallet.FILE_EXTENSION);
	}

	public static final int MAGIC_NUMBER = 0x36003600;
	public static final long NEWEST_SUPPORTED_FORMAT = 1;

	@SuppressWarnings("FieldCanBeLocal")
	private final long formatVersion = NEWEST_SUPPORTED_FORMAT;

	public final Path path;

	public final SimpleStringProperty name;

	// Idea: local notes on transactions

	private final TreeMap<Integer, String> internalMyAddresses = new TreeMap<>();
	// index<->name; EIP3 addresses belonging to this wallet
	public final ObservableMap<Integer, String> myAddresses = FXCollections.observableMap(internalMyAddresses);
	// name<->address
	public final ObservableMap<String, Address> addressBook = FXCollections.observableMap(new HashMap<>());

	public int nextAddressIndex() {
		return internalMyAddresses.lastKey() + 1;
	}

	private Wallet(Path path, WalletKey key, String name, Map<Integer, String> myAddresses, byte[] detailsIv, SecretKey detailsSecretKey) {
		this.path = path;
		this.key = key;
		this.name = new SimpleStringProperty(name);
		this.detailsIv = detailsIv;
		this.detailsSecretKey = detailsSecretKey;

		this.myAddresses.putAll(myAddresses);
		this.myAddresses.addListener((MapChangeListener<Integer, String>) change -> saveToFile());
		this.name.addListener((observable, oldValue, newValue) -> saveToFile());
	}

	private WalletKey key;

	public WalletKey key() {
		return key;
	}

	public final SimpleObjectProperty<Balance> lastKnownBalance = new SimpleObjectProperty<>();

	public Address publicAddress(int index) {
		return key.derivePublicAddress(Main.programData().nodeNetworkType.get(), index);
	}

	public Stream<Address> addressStream() {
		return myAddresses.keySet().stream().map(this::publicAddress);
	}

	/**
	 * Returns the balance of all addresses combined (checking is done in parallel)
	 */
	public Balance totalBalance() throws ConnectException {
		try {
			return addressStream().parallel().map(address -> ErgoInterface.getBalance(Main.programData().nodeNetworkType.get(), address))
					.reduce(Balance::combine).orElseThrow();
		} catch (RuntimeException e) {
			if (e.getCause() instanceof ConnectException ce)
				throw ce;
			if (e.getCause().getCause() instanceof ConnectException ce)
				throw ce;
			throw e;
		}
	}

	public String transact(SignedTransaction signedTx) {
		return Utils.createErgoClient().execute(ctx -> {
			String quoted = ctx.sendTransaction(signedTx);
			return quoted.substring(1, quoted.length() - 1);
		});
	}

	public void changePassword(char[] currentPassword, char[] newPassword) throws IncorrectPasswordException {
		try {
			key = key.changedPassword(currentPassword, newPassword);
			detailsIv = AESEncryption.generateNonce12();
			detailsSecretKey = AESEncryption.generateSecretKey(newPassword, detailsIv);
		} catch (WalletKey.Failure e) {
			throw new IncorrectPasswordException();
		} catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
			throw new RuntimeException(e);
		}
		saveToFile();
	}

	/**
	 * creates a new wallet with local key and master address and saves it
	 */
	public static Wallet create(Path path, Mnemonic mnemonic, String name, char[] password, boolean nonstandardDerivation) {
		byte[] detailsIv = AESEncryption.generateNonce12();
		SecretKey detailsSecretKey;
		try {
			detailsSecretKey = AESEncryption.generateSecretKey(password, detailsIv);
		} catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
			throw new RuntimeException(e);
		}
		Wallet wallet = new Wallet(path, WalletKey.Local.create(nonstandardDerivation, mnemonic, password), name, Map.of(0, "Master"), detailsIv, detailsSecretKey);
		wallet.saveToFile();
		return wallet;
	}

	public static Wallet create(Path path, Mnemonic mnemonic, String name, char[] password) {
		return create(path, mnemonic, name, password, false);
	}

	// ENCRYPTION, SERIALIZATION & STORING

	private byte[] detailsIv;
	private SecretKey detailsSecretKey;

	public byte[] serializeEncrypted() throws IOException {
		try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			 DataOutputStream out = new DataOutputStream(bytes)) {
			out.writeInt(MAGIC_NUMBER);
			out.writeLong(formatVersion);

			out.writeInt(key.encrypted().length);
			out.write(key.encrypted());

			byte[] rawDetailsData;
			try (ByteArrayOutputStream bytesInfo = new ByteArrayOutputStream();
				 DataOutputStream outInfo = new DataOutputStream(bytesInfo)) {
				outInfo.writeUTF(name.get());
				outInfo.writeInt(myAddresses.size());
				for (Map.Entry<Integer, String> entry : myAddresses.entrySet()) {
					outInfo.writeInt(entry.getKey());
					outInfo.writeUTF(entry.getValue());
				}
				outInfo.writeInt(addressBook.size());
				for (Map.Entry<String, Address> entry : addressBook.entrySet()) {
					outInfo.writeUTF(entry.getKey());
					outInfo.writeUTF(entry.getValue().toString());
				}
				outInfo.flush();
				rawDetailsData = bytesInfo.toByteArray();
			}
			// contains the IV and the encrypted data
			byte[] encryptedDetailsData = AESEncryption.encryptData(detailsIv, detailsSecretKey, rawDetailsData);
			out.writeInt(encryptedDetailsData.length);
			out.write(encryptedDetailsData);
			out.flush();
			return bytes.toByteArray();
		} catch (GeneralSecurityException e) {
			throw new RuntimeException(e);
		}
	}

	public void saveToFile() {
		try {
			Files.write(path, serializeEncrypted());
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * @throws UnsupportedOperationException Cannot deserialize this formatVersion, it is too new
	 */
	private static Wallet deserialize(long formatVersion, DataInputStream in, Path path, char[] password) throws IncorrectPasswordException, UnsupportedOperationException, IOException {
		if (formatVersion == 1) {
			WalletKey key;
			byte[] decryptedDetails;
			byte[] detailsEncryptionIv;
			SecretKey detailsEncryptionKey;
			try {
				byte[] encryptedKey = in.readNBytes(in.readInt());
				byte[] encryptedDetails = in.readNBytes(in.readInt());

				key = WalletKey.deserialize(encryptedKey, ByteBuffer.wrap(AESEncryption.decryptData(password, ByteBuffer.wrap(encryptedKey))));
				decryptedDetails = AESEncryption.decryptData(password, ByteBuffer.wrap(encryptedDetails));

				detailsEncryptionIv = AESEncryption.generateNonce12();
				detailsEncryptionKey = AESEncryption.generateSecretKey(password, detailsEncryptionIv);
			} catch (AEADBadTagException e) {
				throw new IncorrectPasswordException();
			} catch (GeneralSecurityException e) {
				throw new RuntimeException(e);
			}

			try (DataInputStream din = new DataInputStream(new ByteArrayInputStream(decryptedDetails))) {
				String name = din.readUTF();
				int myAddressesSize = din.readInt();
				TreeMap<Integer, String> myAddresses = new TreeMap<>();
				for (int i = 0; i < myAddressesSize; i++) {
					myAddresses.put(din.readInt(), din.readUTF());
				}
				int addressBookSize = din.readInt();
				HashMap<String, Address> addressBook = new HashMap<>();
				for (int i = 0; i < addressBookSize; i++) {
					addressBook.put(din.readUTF(), Address.create(din.readUTF()));
				}
				Wallet wallet = new Wallet(path, key, name, myAddresses, detailsEncryptionIv, detailsEncryptionKey);
				wallet.addressBook.putAll(addressBook);
				return wallet;
			}
		} else throw new UnsupportedOperationException("Unsupported format version " + formatVersion + " (this release only supports " + NEWEST_SUPPORTED_FORMAT + " and older), the file is version " + formatVersion);
	}

	public static Wallet decrypt(byte[] bytes, Path path, char[] password) throws IncorrectPasswordException, IOException {
		try {
			try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
				if (in.readInt() == MAGIC_NUMBER) {
					return deserialize(in.readLong(), in, path, password);
				}
			}
			// since the magic number did not match, this might be the legacy format (version 0), which had no magic number
			ByteBuffer buffer = ByteBuffer.wrap(bytes);
			// skip the initialization vector length field which is always 12 (int)
			buffer.position(4);
			byte[] decrypted = AESEncryption.decryptData(password, buffer);
			try (ObjectInputStream old = new ObjectInputStream(new ByteArrayInputStream(decrypted))) {
				long formatVersion = old.readLong();
				return LegacyWalletFormat.deserializeDecryptedData(formatVersion, old.readAllBytes(), path, password);
			} catch (StreamCorruptedException | EOFException e) {
				throw new IllegalArgumentException("Invalid wallet data");
			}
		} catch (AEADBadTagException e) {
			throw new IncorrectPasswordException();
		} catch (GeneralSecurityException e) {
			throw new RuntimeException(e);
		}
	}

	public static Wallet load(Path path, String password) throws IncorrectPasswordException {
		try {
			Wallet wallet = decrypt(Files.readAllBytes(path), path, password.toCharArray());
			wallet.saveToFile();
			return wallet;
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
}
