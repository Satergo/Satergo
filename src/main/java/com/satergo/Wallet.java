package com.satergo;

import com.satergo.ergo.Balance;
import com.satergo.ergo.ErgoInterface;
import com.satergo.extra.*;
import com.satergo.keystore.WalletKey;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.*;
import javafx.stage.FileChooser;
import org.bouncycastle.crypto.params.Argon2Parameters;
import org.ergoplatform.appkit.Address;
import org.ergoplatform.appkit.Mnemonic;
import org.ergoplatform.appkit.SignedTransaction;

import javax.crypto.AEADBadTagException;
import javax.crypto.SecretKey;
import java.io.*;
import java.net.ConnectException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.*;
import java.util.stream.Stream;

public final class Wallet {

	public static final String FILE_EXTENSION = "erg";

	public static FileChooser.ExtensionFilter extensionFilter() {
		return new FileChooser.ExtensionFilter(Main.lang("walletFile"), "*." + Wallet.FILE_EXTENSION);
	}

	public static final int MAGIC_NUMBER = 0x36003600;
	public static final long NEWEST_SUPPORTED_FORMAT = 2;

	// This is independent of the format version
	private static final NewAESEncryption.Argon2Params ARGON2_PARAMS = new NewAESEncryption.Argon2Params(
			Argon2Parameters.ARGON2_id, Argon2Parameters.ARGON2_VERSION_13, 19456, 2, 1);

	public static final NewAESEncryption F2_ENCRYPTION = new NewAESEncryption(ARGON2_PARAMS);
	private static final int F2_IV_LENGTH = 12, F2_SALT_LENGTH = 16;

	@SuppressWarnings("FieldCanBeLocal")
	private final long formatVersion = NEWEST_SUPPORTED_FORMAT;

	public final Path path;
	public final SimpleStringProperty name;

	// Idea: local notes on transactions

	private final TreeMap<Integer, String> internalMyAddresses = new TreeMap<>();
	// index<->name; EIP3 addresses belonging to this wallet
	public final ObservableMap<Integer, String> myAddresses = FXCollections.observableMap(internalMyAddresses);
	public int nextAddressIndex() {
		return internalMyAddresses.lastKey() + 1;
	}
	public final ObservableList<ExtraField> extraFields = FXCollections.observableArrayList();

	public record ExtraField(int id, byte[] data) {
		public ExtraField {
			Objects.requireNonNull(data);
		}
	}

	private WalletKey key;
	public WalletKey key() { return key; }

	private Wallet(Path path, WalletKey key, String name, Map<Integer, String> myAddresses, byte[] detailsSalt, SecretKey detailsSecretKey, List<ExtraField> extraFields) {
		this.path = path;
		this.key = key;
		this.name = new SimpleStringProperty(name);
		this.detailsSalt = detailsSalt;
		this.detailsSecretKey = detailsSecretKey;

		this.myAddresses.putAll(myAddresses);
		this.myAddresses.addListener((MapChangeListener<Integer, String>) change -> saveToFile());
		this.extraFields.addAll(extraFields);
		this.extraFields.addListener((ListChangeListener<ExtraField>) change -> {
			if (this.extraFields.stream().map(f -> f.id).distinct().count() < this.extraFields.size())
				throw new IllegalArgumentException("duplicate extra field type id");
			saveToFile();
		});
		this.name.addListener((observable, oldValue, newValue) -> saveToFile());
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
			detailsSalt = Encryption.secureRandom(16);
			detailsSecretKey = F2_ENCRYPTION.generateSecretKey(newPassword, detailsSalt);
		} catch (WalletKey.Failure e) {
			throw new IncorrectPasswordException();
		}
		saveToFile();
	}

	/**
	 * Creates a new wallet with a local key and a master address and saves it
	 */
	public static Wallet create(Path path, Mnemonic mnemonic, String name, char[] password, boolean nonstandardDerivation) {
		byte[] detailsSalt = Encryption.secureRandom(16);
		SecretKey detailsSecretKey;
		detailsSecretKey = F2_ENCRYPTION.generateSecretKey(password, detailsSalt);
		Wallet wallet = new Wallet(path, WalletKey.Local.create(nonstandardDerivation, mnemonic, password, F2_ENCRYPTION), name, Map.of(0, Main.lang("masterAddressLabel")), detailsSalt, detailsSecretKey, Collections.emptyList());
		wallet.saveToFile();
		return wallet;
	}

	public static Wallet create(Path path, Mnemonic mnemonic, String name, char[] password) {
		return create(path, mnemonic, name, password, false);
	}

	// ENCRYPTION, SERIALIZATION & STORING

	private byte[] detailsSalt;
	private SecretKey detailsSecretKey;

	public byte[] serializeEncrypted() throws IOException {
		try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			 DataOutputStream out = new DataOutputStream(bytes)) {
			out.writeInt(MAGIC_NUMBER);
			out.writeLong(formatVersion);
			out.writeInt(ARGON2_PARAMS.memory());
			out.writeByte(ARGON2_PARAMS.iterations());
			out.writeByte(ARGON2_PARAMS.parallelism());

			out.write(key.encrypted().salt);
			out.write(key.encrypted().iv);
			out.writeInt(key.encrypted().data.length);
			out.write(key.encrypted().data);

			byte[] rawDetailsData;
			try (ByteArrayOutputStream bytesInfo = new ByteArrayOutputStream();
				 DataOutputStream outInfo = new DataOutputStream(bytesInfo)) {
				outInfo.writeUTF(name.get());
				outInfo.writeInt(myAddresses.size());
				for (Map.Entry<Integer, String> entry : myAddresses.entrySet()) {
					outInfo.writeInt(entry.getKey());
					outInfo.writeUTF(entry.getValue());
				}
				outInfo.writeInt(extraFields.size());
				for (ExtraField extraField : extraFields) {
					out.writeInt(extraField.id);
					out.writeInt(extraField.data.length);
					out.write(extraField.data);
				}
				outInfo.flush();
				rawDetailsData = bytesInfo.toByteArray();
			}
			// contains the encrypted data
			byte[] detailsIv = Encryption.secureRandom(F2_IV_LENGTH);
			byte[] encryptedDetailsData = F2_ENCRYPTION.encryptData(detailsIv, detailsSecretKey, rawDetailsData);
			out.write(detailsSalt);
			out.write(detailsIv);
			out.writeInt(encryptedDetailsData.length);
			out.write(encryptedDetailsData);
			out.flush();
			return bytes.toByteArray();
		} catch (GeneralSecurityException e) {
			throw new RuntimeException(e);
		}
	}

	public synchronized void saveToFile() {
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
		if (formatVersion == 2) {
			int memory = in.readInt();
			int iterations = in.readUnsignedByte();
			int parallelism = in.readUnsignedByte();
			// This is by design only used for decryption. Reusing it for encryption would make old parameters be reused forever for old wallet files.
			Encryption decrypt = new NewAESEncryption(new NewAESEncryption.Argon2Params(
					Argon2Parameters.ARGON2_id, Argon2Parameters.ARGON2_VERSION_13,
					memory, iterations, parallelism));
			WalletKey key;
			byte[] decryptedDetails;
			byte[] detailsEncryptionSalt;
			SecretKey detailsEncryptionKey;
			try {
				byte[] keySalt = readNFully(in, F2_SALT_LENGTH);
				byte[] keyIv = readNFully(in, F2_IV_LENGTH);
				byte[] encryptedKey = readNFully(in, in.readInt());

				byte[] detailsSalt = readNFully(in, F2_SALT_LENGTH);
				byte[] detailsIv = readNFully(in, F2_IV_LENGTH);
				byte[] encryptedDetails = readNFully(in, in.readInt());

				SecretKey keySecretKey = decrypt.generateSecretKey(password, keySalt);
				key = WalletKey.deserialize(F2_ENCRYPTION, new EncryptedData(keySalt, keyIv, encryptedKey), ByteBuffer.wrap(decrypt.decryptData(keyIv, keySecretKey, encryptedKey)));

				SecretKey detailsSecretKey = decrypt.generateSecretKey(password, detailsSalt);
				decryptedDetails = decrypt.decryptData(detailsIv, detailsSecretKey, encryptedDetails);

				detailsEncryptionSalt = Encryption.secureRandom(16);
				detailsEncryptionKey = F2_ENCRYPTION.generateSecretKey(password, detailsEncryptionSalt);
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
				int numberOfExtraFields = din.readInt();
				ArrayList<ExtraField> extraFields = new ArrayList<>();
				for (int i = 0; i < numberOfExtraFields; i++) {
					int id = din.readInt();
					if (extraFields.stream().anyMatch(f -> f.id == id))
						throw new IllegalArgumentException("duplicate extra field type id " + id);
					int length = din.readInt();
					byte[] data = readNFully(din, length);
					extraFields.add(new ExtraField(id, data));
				}

				return new Wallet(path, key, name, myAddresses, detailsEncryptionSalt, detailsEncryptionKey, extraFields);
			}
		} else if (formatVersion == 1) {
			Encryption decrypt = OldAESEncryption.INSTANCE;
			WalletKey key;
			byte[] decryptedDetails;
			byte[] detailsSalt;
			SecretKey detailsEncryptionKey;
			try {
				int keyBytesLength = in.readInt();
				// Format version 1 used the same 12 bytes for IV and PBKDF2 salt
				byte[] keyIvAndSalt = readNFully(in, 12);
				byte[] encryptedKey = readNFully(in, keyBytesLength - keyIvAndSalt.length);

				SecretKey keySecretKey = decrypt.generateSecretKey(password, keyIvAndSalt);
				key = WalletKey.deserialize(decrypt, new EncryptedData(keyIvAndSalt, keyIvAndSalt, encryptedKey), ByteBuffer.wrap(decrypt.decryptData(keyIvAndSalt, keySecretKey, encryptedKey)));
				try {
					key = key.changedEncryption(password, F2_ENCRYPTION);
				} catch (WalletKey.Failure e) {
					throw new InternalError("Must never happen");
				}

				int detailsLength = in.readInt();
				byte[] detailsIvAndSalt = readNFully(in, 12);
				byte[] encryptedDetails = readNFully(in, detailsLength - detailsIvAndSalt.length);
				SecretKey detailsSecretKey = decrypt.generateSecretKey(password, detailsIvAndSalt);
				decryptedDetails = decrypt.decryptData(detailsIvAndSalt, detailsSecretKey, encryptedDetails);

				detailsSalt = Encryption.secureRandom(16);
				detailsEncryptionKey = F2_ENCRYPTION.generateSecretKey(password, detailsSalt);
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
				// it was for "address book size", but it was never used so it is 0 for all wallets
				din.readInt();

				return new Wallet(path, key, name, myAddresses, detailsSalt, detailsEncryptionKey, Collections.emptyList());
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
			return LegacyWalletFormat.decrypt(bytes, path, password);
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

	private static byte[] readNFully(DataInputStream in, int length) throws IOException {
		byte[] array = new byte[length];
		in.readFully(array);
		return array;
	}
}
