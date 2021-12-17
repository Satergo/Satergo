package com.satergo;

import com.satergo.ergo.Balance;
import com.satergo.ergo.ErgoInterface;
import com.satergo.extra.AESEncryption;
import com.satergo.extra.IncorrectPasswordException;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.MapChangeListener;
import javafx.collections.ObservableMap;
import javafx.stage.FileChooser;
import org.ergoplatform.appkit.Address;
import org.ergoplatform.appkit.Mnemonic;
import org.ergoplatform.appkit.SecretString;

import javax.crypto.AEADBadTagException;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.Objects;
import java.util.TreeMap;

public final class Wallet {

	public static final String FILE_EXTENSION = "erg";

	public static FileChooser.ExtensionFilter extensionFilter() {
		return new FileChooser.ExtensionFilter(Main.lang("walletFile"), "*." + Wallet.FILE_EXTENSION);
	}

	public static final long NEWEST_SUPPORTED_FORMAT = 0;

	@SuppressWarnings("FieldCanBeLocal")
	private final long formatVersion = NEWEST_SUPPORTED_FORMAT;

	public final Path path;

	public final SimpleStringProperty name;
	private final Mnemonic mnemonic;
	private SecretString password;

	// Idea: local notes on transactions

	private final TreeMap<Integer, String> internalMyAddressesDoNotUse = new TreeMap<>();
	// index<->name; EIP3 addresses belonging to this wallet
	public final ObservableMap<Integer, String> myAddresses = FXCollections.observableMap(internalMyAddressesDoNotUse);
	// name<->address
	public final ObservableMap<String, Address> addressBook = FXCollections.observableMap(new HashMap<>());

	public int nextAddressIndex() {
		return internalMyAddressesDoNotUse.lastKey() + 1;
	}

	private Wallet(Path path, Mnemonic mnemonic, String name, SecretString password) {
		this.path = path;
		this.mnemonic = Objects.requireNonNull(mnemonic, "mnemonic");
		this.name = new SimpleStringProperty(name);
		this.password = password;

		this.myAddresses.addListener((MapChangeListener<Integer, String>) change -> saveToFile());
		this.name.addListener((observable, oldValue, newValue) -> saveToFile());
	}

	public String getName() {
		return name.get();
	}

	public void setName(String name) {
		this.name.set(name);
	}

	public Address masterPublicAddress() {
		return publicAddress(0);
	}

	public Address publicAddress(int index) {
		return ErgoInterface.getPublicEip3Address(Main.programData().nodeNetworkType.get(), mnemonic, index);
	}

	public Balance balance() {
		return ErgoInterface.getBalance(Main.programData().nodeNetworkType.get(), masterPublicAddress());
	}

	public String transact(Address recipient, long amountToSend, long feeAmount) {
		return ErgoInterface.transact(ErgoInterface.newNodeApiClient(Main.programData().nodeNetworkType.get(), Main.programData().nodeAddress.get()), ctx -> ErgoInterface.newWithMnemonicProver(ctx, mnemonic), recipient, amountToSend, feeAmount);
	}

	public void changePassword(SecretString currentPassword, SecretString newPassword) throws IncorrectPasswordException {
		if (!password.equals(currentPassword))
			throw new IncorrectPasswordException();
		password = newPassword;
		saveToFile();
	}

	public boolean isPassword(SecretString password) {
		return this.password.equals(password);
	}

	public Mnemonic getMnemonic(SecretString password) throws IncorrectPasswordException {
		if (!isPassword(password)) throw new IncorrectPasswordException();
		return mnemonic;
	}

	/**
	 * creates a new wallet with only master address and saves it
	 */
	public static Wallet create(Path path, Mnemonic mnemonic, String name, SecretString password) {
		Wallet wallet = new Wallet(path, mnemonic, name, password);
		wallet.myAddresses.put(0, "Master");
		wallet.saveToFile();
		return wallet;
	}


	// SERIALIZATION & STORAGE

	private byte[] serializeUnencrypted() {
		try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			 ObjectOutputStream out = new ObjectOutputStream(bytes)) {
			out.writeLong(formatVersion);
			out.writeUTF(name.get());
			out.writeUTF(mnemonic.getPhrase().toStringUnsecure());
			out.writeUTF(mnemonic.getPassword().toStringUnsecure());
			out.writeInt(myAddresses.size());
			myAddresses.forEach((index, name) -> {
				try {
					out.writeInt(index);
					out.writeUTF(name);
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			});
			out.writeInt(addressBook.size());
			addressBook.forEach((name, address) -> {
				try {
					out.writeUTF(name);
					out.writeUTF(address.toString());
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			});
			out.flush();
			return bytes.toByteArray();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public byte[] serializeEncrypted() {
		try {
			return AESEncryption.encryptData(password.getData(), serializeUnencrypted());
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
	 * @param password This is not used for decrypting. It is added to the wallet object, used for saving.
	 */
	private static Wallet deserializeUnencrypted(byte[] bytes, Path path, String password) {
		try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
			long formatVersion = in.readLong();
			if (formatVersion == 0) {
				String name = in.readUTF();
				SecretString mnemonicPhrase = SecretString.create(in.readUTF());
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
				Wallet wallet = new Wallet(path, Mnemonic.create(mnemonicPhrase, mnemonicPassword), name, SecretString.create(password));
				wallet.myAddresses.putAll(myAddresses);
				wallet.addressBook.putAll(addressBook);
				return wallet;
			} else throw new IllegalArgumentException("Unsupported format version " + formatVersion + " (this wallet only supports " + NEWEST_SUPPORTED_FORMAT + " and older)");
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public static Wallet deserializeEncrypted(byte[] bytes, Path path, String password) throws IncorrectPasswordException {
		try {
			return deserializeUnencrypted(AESEncryption.decryptData(password.toCharArray(), bytes), path, password);
		} catch (AEADBadTagException e) {
			throw new IncorrectPasswordException();
		} catch (GeneralSecurityException e) {
			throw new RuntimeException(e);
		}
	}

	public static Wallet fromFile(Path path, String password) throws IncorrectPasswordException {
		try {
			return deserializeEncrypted(Files.readAllBytes(path), path, password);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
}
