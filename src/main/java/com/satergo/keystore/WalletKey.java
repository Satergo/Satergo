package com.satergo.keystore;

import com.satergo.Main;
import com.satergo.Utils;
import com.satergo.ergo.ErgoInterface;
import com.satergo.extra.EncryptedData;
import com.satergo.extra.Encryption;
import com.satergo.extra.OldAESEncryption;
import javafx.scene.control.Alert;
import org.ergoplatform.ErgoAddressEncoder;
import org.ergoplatform.P2PKAddress;
import org.ergoplatform.appkit.*;
import org.ergoplatform.sdk.wallet.secrets.DerivationPath;
import org.ergoplatform.sdk.wallet.secrets.ExtendedPublicKey;
import org.ergoplatform.sdk.wallet.secrets.ExtendedSecretKey;

import javax.crypto.AEADBadTagException;
import javax.crypto.SecretKey;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * This API is open for extending by third-parties, but consult the wallet-format.md and apply for an ID first.
 */
public abstract class WalletKey {

	public static class Failure extends Exception {}

	private static final HashMap<Integer, Type<?>> types = new HashMap<>();

	static {
		try {
			Class.forName(Type.class.getName());
		} catch (ClassNotFoundException ignored) {
		}
	}

	public static WalletKey deserialize(Encryption encryption, EncryptedData encrypted, ByteBuffer decrypted) {
		Type<?> type = types.get(decrypted.getShort() & 0xFFFF);
		if (type == null) throw new IllegalArgumentException("Unknown wallet type with ID " + decrypted.getShort(0));
		WalletKey key = type.construct();
		key.initEncryptedData(encryption, encrypted);
		key.initCaches(decrypted);
		return key;
	}

	public static class Type<T extends WalletKey> {
		public static final Type<Local> LOCAL = registerType("LOCAL", 0, Local::new);
//		public static final Type<ViewOnly> VIEW_ONLY = registerType("VIEW_ONLY", 1, ViewOnly::new);
//		public static final Type<Ledger> LEDGER = registerType("LEDGER", 10, Ledger::new);

		private final String name;
		private final Supplier<T> constructor;

		private Type(String name, Supplier<T> constructor) {
			if (!name.toUpperCase(Locale.ROOT).equals(name))
				throw new IllegalArgumentException("Name must be all uppercase.");
			this.name = name;
			this.constructor = Objects.requireNonNull(constructor, "constructor");
		}

		public String name() { return name; }
		public T construct() {
			return constructor.get();
		}

		@Override public boolean equals(Object obj) { return obj instanceof Type<?> t && name.equals(t.name); }
		@Override public int hashCode() { return Objects.hash(name); }
		@Override public String toString() { return name; }
	}

	public static <T extends WalletKey>Type<T> registerType(String name, int id, Supplier<T> constructor) {
		Type<T> type = new Type<>(name, constructor);
		if (types.containsKey(id)) throw new IllegalArgumentException("Type ID " + id + " is already used by " + types.get(id));
		if (types.values().stream().map(Type::name).anyMatch(n -> n.equals(name)))
			throw new IllegalArgumentException("Type name " + name + " is already used");
		types.put(id, type);
		return type;
	}

	protected Encryption encryption;
	private EncryptedData encrypted;
	protected final Type<?> type;

	WalletKey(Type<?> type) {
		this.type = type;
	}

	protected void initEncryptedData(Encryption encryption, EncryptedData encryptedData) {
		if (this.encryption != null || this.encrypted != null)
			throw new IllegalStateException("already initialized");
		this.encryption = Objects.requireNonNull(encryption);
		this.encrypted = Objects.requireNonNull(encryptedData);
	}

	/**
	 * @implNote The data begins at index 4. Index 0-4 is the wallet type ID stored as int.
	 * 	It must only be used for preparing things like caches (for example, extended public key).
	 * 	The ByteBuffer is already positioned at the beginning of the data.
	 */
	public void initCaches(ByteBuffer data) {}

	public abstract SignedTransaction sign(BlockchainContext ctx, UnsignedTransaction unsignedTx, Collection<Integer> addressIndexes) throws Failure;
	public abstract SignedTransaction signReduced(BlockchainContext ctx, ReducedTransaction reducedTx, int baseCost, Collection<Integer> addressIndexes) throws Failure;
	public abstract Address derivePublicAddress(NetworkType networkType, int index);
	public abstract WalletKey changedPassword(char[] currentPassword, char[] newPassword) throws Failure; // it would be cool to call this "recrypt" :)

	/**
	 * Creates a new WalletKey of the same type which contains the same data and has the same password but is encrypted using the new provided encryption specification
	 */
	public abstract WalletKey changedEncryption(char[] currentPassword, Encryption encryption) throws Failure;

	public final EncryptedData encrypted() { return encrypted; }

	/** Useful for WalletKeys that connect to things */
	public void close() {}

	/** Users of this WalletKey can set this field to react to when the key requests that the wallet be closed (for example, due to a lost connection) */
	public Function<Void, Boolean> logoutFromWallet;
	/**
	 * Implementations can call this when they for instance lose the connection to a device that handles the key operations
	 * @return true if it logged out
	 */
	protected final boolean logoutFromWallet() {
		if (logoutFromWallet != null) return logoutFromWallet.apply(null);
		return false;
	}

	/**
	 * The key is encrypted and embedded into the wallet file
	 * The default behavior of this class is:
	 * - keep extended public key in memory for address derivations (as such, never throws Failure for derivePublicAddress)
	 * - ask user for password when needed
	 * - keep the secret key cached in memory for 1 minute since last use
	 */
	public static class Local extends WalletKey {
		private static final int ID = 0;

		public enum Caching { PERMANENT, OFF, TIMED }

		/**
		 * Non-standard (legacy incorrect address derivation implementation in ergo-wallet)
		 */
		private boolean nonstandard;

		private ExtendedPublicKey parentExtPubKey;
		private SecretKey cachedKey;

		private Local() {
			super(Type.LOCAL);
		}

		private static void checkFormat(Mnemonic mnemonic) {
			if (!String.join(" ", mnemonic.getPhrase().toStringUnsecure().split(" ")).equals(mnemonic.getPhrase().toStringUnsecure())) {
				Utils.alert(Alert.AlertType.ERROR, Main.lang("invalidSeedFormatAlert"));
			}
		}

		@Override
		public void initCaches(ByteBuffer data) {
			nonstandard = data.get() == 1;
			Mnemonic mnemonic = readMnemonic(data);
			checkFormat(mnemonic);
			initParentExtPubKey(mnemonic);
		}

		private void initParentExtPubKey(Mnemonic mnemonic) {
			ExtendedSecretKey rootSecret = ExtendedSecretKey.deriveMasterKey(mnemonic.toSeed(), nonstandard);
			parentExtPubKey = ((ExtendedSecretKey) rootSecret.derive(DerivationPath.fromEncoded("m/44'/429'/0'/0").get())).publicKey();
		}

		public static Local create(boolean nonstandard, Mnemonic mnemonic, char[] password, Encryption encryption) {
			try {
				checkFormat(mnemonic);
				Local key = new Local();
				key.nonstandard = nonstandard;
				// StandardCharsets.UTF_8.encode(CharBuffer.wrap(mnemonic.getPhrase().getData())) is not used because
				// for some reason it adds multiple null characters at the end
				byte[] mnPhraseBytes = mnemonic.getPhrase().toStringUnsecure().getBytes(StandardCharsets.UTF_8);
				byte[] mnPasswordBytes = mnemonic.getPassword().toStringUnsecure().getBytes(StandardCharsets.UTF_8);
				SecureRandom secureRandom = new SecureRandom();
				int padding = secureRandom.nextInt(0, 20);
				ByteBuffer buffer = ByteBuffer.allocate(2 + 1 + 2 + mnPhraseBytes.length + 2 + mnPasswordBytes.length + padding)
						.putShort((short) ID)
						.put((byte) (nonstandard ? 1 : 0))
						.putShort((short) mnPhraseBytes.length)
						.put(mnPhraseBytes)
						.putShort((short) mnPasswordBytes.length)
						.put(mnPasswordBytes);
				byte[] salt = Encryption.secureRandom(16);
				byte[] iv = Encryption.secureRandom(12);
				byte[] encrBytes = encryption.encryptData(iv, encryption.generateSecretKey(password, salt), buffer.array());
				key.initEncryptedData(encryption, new EncryptedData(salt, iv, encrBytes));
				key.initParentExtPubKey(mnemonic);
				return key;
			} catch (GeneralSecurityException e) {
				throw new RuntimeException(e);
			}
		}

		private Mnemonic getMnemonic(Supplier<String> passwordSupplier) throws Failure {
			SecretKey secretKey;
			if (cachedKey == null) {
				String password = passwordSupplier.get();
				if (password == null) throw new Failure();
				try {
					secretKey = encryption.generateSecretKey(password.toCharArray(), encrypted().salt);
				} catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
					throw new RuntimeException(e);
				}
			} else {
				secretKey = cachedKey;
			}
			try {
				byte[] decrypted = encryption.decryptData(encrypted().iv, secretKey, encrypted().data);
				ByteBuffer buffer = ByteBuffer.wrap(decrypted).position(3);
				if (caching != Caching.OFF) {
					this.cachedKey = secretKey;
					if (caching == Caching.TIMED)
						restartCacheTimeout();
				}
				return readMnemonic(buffer);
			} catch (AEADBadTagException e) {
				Utils.alertIncorrectPassword();
				throw new Failure();
			} catch (GeneralSecurityException e) {
				throw new RuntimeException(e);
			}
		}

		public Mnemonic getMnemonic() throws Failure {
			return getMnemonic(() -> Utils.requestPassword(Main.lang("walletPassword")));
		}

		private static Mnemonic readMnemonic(ByteBuffer data) {
			byte[] mnPhraseBytes = new byte[data.getShort()];
			data.get(mnPhraseBytes);
			byte[] mnPasswordBytes = new byte[data.getShort()];
			data.get(mnPasswordBytes);
			char[] mnPhraseChars = StandardCharsets.UTF_8.decode(ByteBuffer.wrap(mnPhraseBytes)).array();
			char[] mnPasswordChars = StandardCharsets.UTF_8.decode(ByteBuffer.wrap(mnPasswordBytes)).array();
			return Mnemonic.create(mnPhraseChars, mnPasswordChars);
		}

		private ScheduledExecutorService scheduler;
		private Caching caching = Caching.TIMED;
		private final Duration cacheDuration = Duration.ofMinutes(1);

		public void setCaching(Caching caching) {
			if (this.caching == caching) return;
			if (caching == Caching.OFF || caching == Caching.TIMED) {
				if (scheduler != null)
					scheduler.shutdown();
				cachedKey = null;
			}
			this.caching = caching;
		}

		@Override
		public SignedTransaction sign(BlockchainContext ctx, UnsignedTransaction unsignedTx, Collection<Integer> addressIndexes) throws Failure {
			return ErgoInterface.newWithMnemonicProver(ctx, nonstandard, getMnemonic(), addressIndexes).sign(unsignedTx);
		}

		@Override
		public SignedTransaction signReduced(BlockchainContext ctx, ReducedTransaction reducedTx, int baseCost, Collection<Integer> addressIndexes) throws Failure {
			return ErgoInterface.newWithMnemonicProver(ctx, nonstandard, getMnemonic(), addressIndexes).signReduced(reducedTx, baseCost);
		}

		@Override
		public Address derivePublicAddress(NetworkType networkType, int index) {
			return new Address(P2PKAddress.apply(parentExtPubKey.child(index).key(), new ErgoAddressEncoder(networkType.networkPrefix)));
		}

		@Override
		public WalletKey changedPassword(char[] currentPassword, char[] newPassword) throws Failure {
			return create(nonstandard, getMnemonic(() -> new String(currentPassword)), newPassword, encryption);
		}

		@Override
		public WalletKey changedEncryption(char[] currentPassword, Encryption encryption) throws Failure {
			return create(nonstandard, getMnemonic(() -> new String(currentPassword)), currentPassword, encryption);
		}

		private void restartCacheTimeout() {
			if (scheduler != null)
				scheduler.shutdown();
			scheduler = Executors.newSingleThreadScheduledExecutor();
			scheduler.schedule(() -> cachedKey = null, cacheDuration.toMillis(), TimeUnit.MILLISECONDS);
		}
	}
}
