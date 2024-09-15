package com.satergo.extra.hw.ledger;

import com.satergo.jledger.LedgerDevice;
import com.satergo.jledger.protocol.ergo.ErgoLedgerException;
import com.satergo.jledger.protocol.ergo.ErgoNetworkType;
import com.satergo.jledger.protocol.ergo.ErgoProtocol;
import com.satergo.jledger.protocol.ergo.ErgoResponse;
import com.satergo.jledger.protocol.ergo.ErgoResponse.AttestedBoxFrame;
import org.ergoplatform.ErgoTreePredef;
import org.ergoplatform.appkit.*;
import org.ergoplatform.sdk.ErgoToken;
import org.ergoplatform.sdk.JavaHelpers;
import org.ergoplatform.sdk.wallet.secrets.DerivationPath;
import org.ergoplatform.sdk.wallet.secrets.ExtendedPublicKey;
import scala.collection.JavaConverters;
import scala.jdk.CollectionConverters;
import sigmastate.Values.ErgoTree;
import sigmastate.interpreter.ContextExtension;
import sigmastate.serialization.SigmaSerializer;
import sigmastate.utils.SigmaByteWriter;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

public class ErgoLedgerAppkit {

	private final ErgoProtocol protocol;
	public final LedgerDevice device;

	public ErgoLedgerAppkit(ErgoProtocol protocol) {
		this.protocol = protocol;
		this.device = protocol.device;
	}

	private static final ErgoTree MINER_FEE_TREE = ErgoTreePredef.feeProposition(720);

	public static byte[] serializeContextExtension(ContextExtension contextExtension) {
		if (contextExtension.values().isEmpty()) return new byte[0];
		SigmaByteWriter sbw = SigmaSerializer.startWriter();
		// why are you like this scala, why don't you let me use ContentExtension.serializer.serialize()
		ContextExtension.serializer$.MODULE$.serialize(contextExtension, sbw);
		return sbw.toBytes();
	}

	public ExtendedPublicKey requestParentExtendedPublicKey() throws ErgoLedgerException {
		ErgoResponse.ExtendedPublicKey extPubKeyData = protocol.getExtendedPublicKey(new int[] { h(44), h(429), h(0) }, null);
		DerivationPath path = new DerivationPath(JavaHelpers.toIndexedSeq(List.of(h(44), h(429), h(0))), false);
		return new ExtendedPublicKey(extPubKeyData.compressedPublicKey(), extPubKeyData.chainCode(), path)
				.child(0);
	}

	public AttestedBoxFrame[] attestBox(InputBox box) throws ErgoLedgerException {
		byte[] boxTxId = HexFormat.of().parseHex(box.getTransactionId());
		byte[] ergoTree = box.getErgoTree().bytes();
		byte[] registers = serializeRegisters(box.getRegisters());
		List<ErgoToken> tokens = box.getTokens();
		int sessionId = protocol.attestBoxStart(boxTxId, box.getTransactionIndex(), box.getValue(), ergoTree.length, box.getCreationHeight(), tokens.size(), registers.length, null);
		int frameCount = -1;
		// Write ErgoTree
		frameCount = writeInChunksWithResult(protocol::attestAddErgoTreeChunk, sessionId, ergoTree).orElse(frameCount);
		if (!tokens.isEmpty()) {
			if (tokens.size() > 20) {
				throw new IllegalArgumentException("Max 20 tokens");
			}
			// max 6 tokens per exchange, so do it in chunks
			int cs = 6;
			for (int i = 0; i < Math.ceil(tokens.size() / (double) cs); i++) {
				if (frameCount != -1) throw new IllegalStateException("Box frame is identified as finished but it is not");
				frameCount = protocol.attestAddTokens(sessionId, tokens.stream()
						.skip((long) i * cs).limit(cs)
						.map(token -> new ErgoProtocol.TokenValue(token.getId().getBytes(), token.getValue()))
						.toList()).orElse(frameCount);
			}
		}
		if (registers.length > 0) {
			frameCount = writeInChunksWithResult(protocol::attestAddRegistersChunk, sessionId, registers).orElse(frameCount);
		}
		if (frameCount == -1) {
			throw new IllegalStateException();
		}
		AttestedBoxFrame[] frames = new AttestedBoxFrame[frameCount];
		for (int i = 0; i < frameCount; i++) {
			frames[i] = protocol.getAttestedBoxFrame(sessionId, i);
		}
		return frames;
	}

	/**
	 * @param changeAddress This is used to mark the change output box and make the Ledger not show the amount as part of the total outgoing amount. Can be null.
	 * @param changePath The derivation path of the change address. Can be null.
	 * @return The signature of this transaction
	 */
	public byte[] signTransaction(ErgoNetworkType networkType, List<AttestedBox> inputBoxes, List<InputBox> dataBoxes, List<OutBox> outputBoxes, Address changeAddress, DerivationPath changePath) throws ErgoLedgerException {
		int sessionId = protocol.startP2PKSigning(networkType, new int[] { h(44), h(429), h(0), 0, 0 }, null);
		int[] changePathInts = changePath == null ? null : CollectionConverters.seqAsJavaList(changePath.decodedPath()).stream().mapToInt(p -> (int) p).toArray();

		ArrayList<byte[]> distinctTokenIds = new ArrayList<>();
		for (OutBox outputBox : outputBoxes) {
			for (ErgoToken token : outputBox.getTokens()) {
				byte[] id = token.getId().getBytes();
				if (indexOf(distinctTokenIds, id) == -1)
					distinctTokenIds.add(id);
			}
		}

		System.out.printf("inputBoxes.size() = %d, dataBoxes.size() = %d, distinctTokenIds.size() = %d, outputBoxes.size() = %d%n", inputBoxes.size(), dataBoxes.size(), distinctTokenIds.size(), outputBoxes.size());
		protocol.startTransaction(sessionId, inputBoxes.size(), dataBoxes.size(), distinctTokenIds.size(), outputBoxes.size());

		if (!distinctTokenIds.isEmpty()) {
			if (distinctTokenIds.size() > 20) {
				throw new IllegalArgumentException("Max 20 tokens");
			}
			// max 7 tokens IDs per exchange, so do it in chunks
			int cs = 7;
			for (int i = 0; i < Math.ceil(distinctTokenIds.size() / (double) cs); i++) {
				protocol.addTokenIds(sessionId, distinctTokenIds.subList(i * cs, Math.min((i + 1) * cs, distinctTokenIds.size())));
			}
		}

		addInputs(sessionId, inputBoxes);
		if (!dataBoxes.isEmpty())
			addDataInputs(sessionId, dataBoxes);
		addOutputs(sessionId, outputBoxes, networkType, distinctTokenIds, changeAddress, changePathInts);

		return protocol.confirmAndSign(sessionId);
	}

	private void addInputs(int sessionId, List<AttestedBox> boxesToSpend) {
		for (AttestedBox attestedBox : boxesToSpend) {

			for (AttestedBoxFrame frame : attestedBox.frames()) {
				protocol.addInputBoxFrame(sessionId, frame, attestedBox.extension().length);
			}

			if (attestedBox.extension().length > 0) {
				writeInChunks(protocol::addInputBoxContextExtensionChunk, sessionId, attestedBox.extension());
			}
		}
	}

	private void addDataInputs(int sessionId, List<InputBox> dataBoxes) {
		// TODO chunk
		protocol.addDataInputs(sessionId, dataBoxes.stream().map(box -> box.getId().getBytes()).toList());
	}

	private void addOutputs(int sessionId, List<OutBox> boxes, ErgoNetworkType networkType, List<byte[]> distinctTokenIds, Address changeAddress, int[] changePath) {
		for (OutBox box : boxes) {
			ErgoTree ergoTree = box.getErgoTree();
			byte[] treeBytes = ergoTree.bytes();
			ArrayList<ErgoProtocol.TokenIndexValue> tokens = new ArrayList<>();
			for (ErgoToken token : box.getTokens()) {
				int tokenIndex = indexOf(distinctTokenIds, token.getId().getBytes());
				if (tokenIndex == -1) throw new IllegalStateException();
				tokens.add(new ErgoProtocol.TokenIndexValue(tokenIndex, token.getValue()));
			}
			byte[] registers = serializeRegisters(box.getRegisters());
			protocol.addOutputBoxStart(sessionId, box.getValue(), treeBytes.length, box.getCreationHeight(), tokens.size(), registers.length);
			if (ergoTree.equals(MINER_FEE_TREE)) {
				protocol.addOutputBoxMinerFeeTree(sessionId);
			} else if (Address.fromErgoTree(ergoTree, networkType == ErgoNetworkType.MAINNET ? NetworkType.MAINNET : NetworkType.TESTNET).equals(changeAddress)) {
				protocol.addOutputBoxChangeTree(sessionId, changePath);
			} else {
				writeInChunks(protocol::addOutputBoxErgoTreeChunk, sessionId, treeBytes);
			}
			if (!tokens.isEmpty())
				protocol.addOutputBoxTokens(sessionId, tokens);
			// write registers
			if (registers.length > 0) {
				writeInChunks(protocol::addOutputBoxRegistersChunk, sessionId, registers);
			}
		}
	}

	private void writeInChunks(BiConsumer<Integer, byte[]> function, int sessionId, byte[] bytes) {
		for (int i = 0; i < Math.ceil(bytes.length / 255.0); i++) {
			int start = i * 255;
			byte[] chunk = Arrays.copyOfRange(bytes, start, Math.min(start + 255, bytes.length));
			function.accept(sessionId, chunk);
		}
	}
	private <T>Optional<T> writeInChunksWithResult(BiFunction<Integer, byte[], Optional<T>> function, int sessionId, byte[] bytes) {
		int chunks = (int) Math.ceil(bytes.length / 255.0);
		for (int i = 0; i < chunks; i++) {
			byte[] chunk = Arrays.copyOfRange(bytes, i * 255, Math.min((i + 1) * 255, bytes.length));
			Optional<T> returnValue = function.apply(sessionId, chunk);
			if (returnValue.isPresent()) {
				if (i != chunks - 1)
					throw new IllegalStateException("Received return value before everything was written");
				return returnValue;
			}
		}
		return Optional.empty();
	}

	private static byte[] serializeRegisters(List<ErgoValue<?>> registers) {
		SigmaByteWriter sbw = SigmaSerializer.startWriter();
		for (int i = 0; i < registers.size(); i++) {
			// the first entry, R4, is index 0, so + 4
			int id = i + 4;
			sbw.putUByte(id);
			sbw.putValue(AppkitIso.isoErgoValueToSValue().to(registers.get(i)));
		}
		return sbw.toBytes();
	}

	private static int indexOf(List<byte[]> byteArrays, byte[] bytes) {
		for (int i = 0; i < byteArrays.size(); i++) {
			if (Arrays.equals(byteArrays.get(i), bytes))
				return i;
		}
		return -1;
	}

	/** turns a BIP44 path index into a hardened index */
	public static int h(int x) {
		return x + 0x80000000;
	}
}
