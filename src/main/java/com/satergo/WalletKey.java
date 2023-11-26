package com.satergo;

import com.satergo.extra.hw.ledger.AttestedBox;
import com.satergo.extra.hw.ledger.ErgoLedgerAppkit;
import com.satergo.ergo.ErgoInterface;
import com.satergo.extra.AESEncryption;
import com.satergo.extra.dialog.MoveStyle;
import com.satergo.extra.hw.ledger.HidLedgerDevice2;
import com.satergo.extra.hw.ledger.LedgerPrompt;
import com.satergo.extra.hw.ledger.LedgerSelector;
import com.satergo.jledger.protocol.ergo.ErgoNetworkType;
import com.satergo.jledger.protocol.ergo.ErgoProtocol;
import com.satergo.jledger.protocol.ergo.ErgoResponse;
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
import org.ergoplatform.wallet.secrets.DerivationPath;
import org.ergoplatform.wallet.secrets.ExtendedPublicKey;
import org.ergoplatform.wallet.secrets.ExtendedSecretKey;
import org.hid4java.HidDevice;
import scala.collection.JavaConverters;
import sigmastate.interpreter.ContextExtension;
import sigmastate.interpreter.ProverResult;

import javax.crypto.AEADBadTagException;
import javax.crypto.SecretKey;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
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

	public static WalletKey deserialize(byte[] encrypted, ByteBuffer decrypted) {
		Type<?> type = types.get(decrypted.getShort() & 0xFFFF);
		if (type == null) throw new IllegalArgumentException("Unknown wallet type with ID " + decrypted.getShort(0));
		WalletKey key = type.construct();
		key.encrypted = encrypted;
		key.initCaches(decrypted);
		return key;
	}

	public static class Type<T extends WalletKey> {
		public static final Type<Local> LOCAL = registerType("LOCAL", 0, Set.of(Property.SUPPORTS_REDUCED_TX), Local::new);
//		public static final Type<ViewOnly> VIEW_ONLY = registerType("VIEW_ONLY", 1, ViewOnly::new);
		public static final Type<Ledger> LEDGER = registerType("LEDGER", 50, Set.of(), Ledger::new);

		private final String name;
		private final Supplier<T> constructor;
		public final Set<Property> properties;

		public enum Property {
			SUPPORTS_REDUCED_TX
		}

		private Type(String name, Set<Property> properties, Supplier<T> constructor) {
			this.properties = properties;
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

	public static <T extends WalletKey>Type<T> registerType(String name, int id, Set<Type.Property> properties, Supplier<T> constructor) {
		Type<T> type = new Type<>(name, properties, constructor);
		if (types.containsKey(id)) throw new IllegalArgumentException("Type ID " + id + " is already used by " + types.get(id));
		if (types.values().stream().map(Type::name).anyMatch(n -> n.equals(name)))
			throw new IllegalArgumentException("Type name " + name + " is already used");
		types.put(id, type);
		return type;
	}

	private byte[] encrypted;
	protected final Type<?> type;

	private WalletKey(Type<?> type) {
		this.type = type;
	}

	protected void initEncryptedData(byte[] encryptedData) {
		if (this.encrypted != null) throw new IllegalStateException();
		this.encrypted = encryptedData;
	}

	/**
	 * @implNote The data begins at index 4. Index 0-4 is the wallet type ID stored as int.
	 * 	It must only be used for preparing things like caches (for example, extended public key).
	 * 	The ByteBuffer is already positioned at the data beginning.
	 */
	public void initCaches(ByteBuffer data) {}

	public abstract SignedTransaction sign(BlockchainContext ctx, UnsignedTransaction unsignedTx, Collection<Integer> addressIndexes) throws Failure;
	public abstract SignedTransaction signReduced(BlockchainContext ctx, ReducedTransaction reducedTx, int baseCost, Collection<Integer> addressIndexes) throws Failure;
	public abstract Address derivePublicAddress(NetworkType networkType, int index) throws Failure;
	public abstract WalletKey changedPassword(char[] currentPassword, char[] newPassword) throws Failure; // it would be cool to call this "recrypt" :)

	public byte[] copyIv() { return Arrays.copyOf(encrypted, 12); }
	public byte[] encrypted() { return encrypted; }

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

		public static Local create(boolean nonstandard, Mnemonic mnemonic, char[] password) {
			try {
				checkFormat(mnemonic);
				Local key = new Local();
				key.nonstandard = nonstandard;
				byte[] iv = AESEncryption.generateNonce12();
				// StandardCharsets.UTF_8.encode(CharBuffer.wrap(mnemonic.getPhrase().getData())) is not used because
				// for some reason it adds multiple null characters at the end
				byte[] mnPhraseBytes = mnemonic.getPhrase().toStringUnsecure().getBytes(StandardCharsets.UTF_8);
				byte[] mnPasswordBytes = mnemonic.getPassword().toStringUnsecure().getBytes(StandardCharsets.UTF_8);
				ByteBuffer buffer = ByteBuffer.allocate(2 + 1 + 2 + mnPhraseBytes.length + 2 + mnPasswordBytes.length)
						.putShort((short) ID)
						.put((byte) (nonstandard ? 1 : 0))
						.putShort((short) mnPhraseBytes.length)
						.put(mnPhraseBytes)
						.putShort((short) mnPasswordBytes.length)
						.put(mnPasswordBytes);
				key.initEncryptedData(AESEncryption.encryptData(iv, AESEncryption.generateSecretKey(password, iv), buffer.array()));
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
					secretKey = AESEncryption.generateSecretKey(password.toCharArray(), copyIv());
				} catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
					throw new RuntimeException(e);
				}
			} else {
				secretKey = cachedKey;
			}
			try {
				byte[] decrypted = AESEncryption.decryptData(secretKey, ByteBuffer.wrap(encrypted()));
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
			return create(nonstandard, getMnemonic(() -> new String(currentPassword)), newPassword);
		}

		private void restartCacheTimeout() {
			if (scheduler != null)
				scheduler.shutdown();
			scheduler = Executors.newSingleThreadScheduledExecutor();
			scheduler.schedule(() -> cachedKey = null, cacheDuration.toMillis(), TimeUnit.MILLISECONDS);
		}
	}

	/**
	 * The public key is saved to the wallet file, the chain code is not so deriving solely from the wallet file + password is not possible
	 * Each time a wallet of this type is opened, the Ledger must be connected and the user must accept ext pub key export
	 * Using the pub key the wallet on the Ledger and the one that was used to create this key can be compared
	 */
	public static class Ledger extends WalletKey {
		private static final int ID = 50;

		private static final int KEY_LENGTH = 33;

		private ErgoLedgerAppkit ergoLedgerAppkit;
		private ExtendedPublicKey parentExtPubKey;

		private int productId;
		private byte[] storedKeyBytes;

		private Ledger() {
			super(Type.LEDGER);
		}

		@Override
		public void initCaches(ByteBuffer data) {
			System.out.println("INIT CACHES");
			productId = data.getInt();
			storedKeyBytes = new byte[KEY_LENGTH];
			data.get(storedKeyBytes);
			LedgerPrompt.Connection connectionPrompt = new LedgerPrompt.Connection(productId);
			connectionPrompt.initOwner(Main.get().stage());
			connectionPrompt.setMoveStyle(MoveStyle.FOLLOW_OWNER);
			Main.get().applySameTheme(connectionPrompt.getDialogPane().getScene());
			connectionPrompt.setOnShown(event -> {
				LedgerSelector ledgerSelector = new LedgerSelector() {
					@Override
					public void deviceFound(HidDevice hidDevice) {
						System.out.println("DEVICE FOUND");
						Platform.runLater(() -> {
							connectionPrompt.setResult(new ErgoLedgerAppkit(new ErgoProtocol(new HidLedgerDevice2(hidDevice))));
							connectionPrompt.close();
						});
						stop();
					}
				};
				System.out.println("Start listener");
				ledgerSelector.startListener();
			});
			connectionPrompt.close();
			ergoLedgerAppkit = connectionPrompt.showForResult().orElse(null);
			ergoLedgerAppkit.device.open();
			System.out.println("SHOWING EXTPUBKEY PROMPT");
			LedgerPrompt.ExtPubKey prompt = new LedgerPrompt.ExtPubKey(ergoLedgerAppkit);
			prompt.initOwner(Main.get().stage());
			prompt.setMoveStyle(MoveStyle.FOLLOW_OWNER);
			Main.get().applySameTheme(prompt.getDialogPane().getScene());
			ExtendedPublicKey parentExtPubKey = prompt.showForResult().orElse(null);
			// not sure if this occurs
			if (parentExtPubKey == null) throw new RuntimeException();
			if (!Arrays.equals(storedKeyBytes, parentExtPubKey.keyBytes()))
				throw new IllegalStateException("This wallet does not belong to this device");
			this.parentExtPubKey = parentExtPubKey;
			System.out.println("SHOWED CONNECTION PROMPT");
		}

		private void initStoredKeyBytes(byte[] storedKeyBytes) {
			this.storedKeyBytes = storedKeyBytes;
		}

		public static Ledger create(ExtendedPublicKey parentExtPubKey, ErgoLedgerAppkit ergoLedgerAppkit, char[] password) {
			if (parentExtPubKey.keyBytes().length != KEY_LENGTH) throw new IllegalArgumentException("public key must be " + KEY_LENGTH + " bytes");
			if (parentExtPubKey.chainCode().length != 32) throw new IllegalArgumentException("chain code must be 32 bytes");
			Ledger key = new Ledger();
			key.parentExtPubKey = parentExtPubKey;
			key.ergoLedgerAppkit = ergoLedgerAppkit;
			try {
				byte[] iv = AESEncryption.generateNonce12();
				ByteBuffer buffer = ByteBuffer.allocate(2 + 4 + KEY_LENGTH)
						.putShort((short) ID)
						.putInt(ergoLedgerAppkit.device.getProductId())
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
			List<InputBox> inputs = unsignedTx.getInputs();
			List<AttestedBox> inputBoxes = inputs.stream()
					.filter(box -> inputs.stream().anyMatch(in -> Arrays.equals(in.getId().getBytes(), box.getId().getBytes())))
					.map(inputBox -> {
						ErgoResponse.AttestedBoxFrame[] attestedBoxFrames = ergoLedgerAppkit.getAttestedBoxFrames(inputBox);
						return new AttestedBox(inputBox, attestedBoxFrames, ErgoLedgerAppkit.serializeContextExtension(((InputBoxImpl) inputBox).getExtension()));
					}).toList();

			LedgerPrompt.Signing prompt = new LedgerPrompt.Signing(() ->
					ergoLedgerAppkit.signTransaction(switch (Main.programData().nodeNetworkType.get()) {
						case MAINNET -> ErgoNetworkType.MAINNET;
						case TESTNET -> ErgoNetworkType.TESTNET;
					}, inputBoxes, unsignedTx.getDataInputs(), unsignedTx.getOutputs(), null, null));
			prompt.initOwner(Main.get().stage());
			prompt.setMoveStyle(MoveStyle.FOLLOW_OWNER);
			Main.get().applySameTheme(prompt.getDialogPane().getScene());
			// not sure if this occurs
			byte[] bytes = prompt.showForResult().orElse(null);

			ErgoLikeTransaction signed = ((UnsignedTransactionImpl) unsignedTx).getTx().toSigned(JavaConverters.asScalaBuffer(List.of(new ProverResult(bytes, ContextExtension.empty()))).toIndexedSeq());
			return new SignedTransactionImpl((BlockchainContextBase) ctx, signed, 0);
		}

		@Override
		public SignedTransaction signReduced(BlockchainContext ctx, ReducedTransaction reducedTx, int baseCost, Collection<Integer> addressIndexes) throws Failure {
			throw new UnsupportedOperationException();
		}

		@Override
		public Address derivePublicAddress(NetworkType networkType, int index) throws Failure {
			System.out.println("LEDGER DERIVE FROM CACHE");
			return new Address(P2PKAddress.apply(parentExtPubKey.child(index).key(), new ErgoAddressEncoder(networkType.networkPrefix)));
		}

		@Override
		public WalletKey changedPassword(char[] currentPassword, char[] newPassword) throws Failure {
			return create(parentExtPubKey, ergoLedgerAppkit, newPassword);
		}
	}
}
