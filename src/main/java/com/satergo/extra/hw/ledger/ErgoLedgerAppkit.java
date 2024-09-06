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
		// why are you like this scala, why don't you let me use ContentExtension.serializer.serialize()???
		ContextExtension.serializer$.MODULE$.serialize(contextExtension, sbw);
		return sbw.toBytes();
	}

	public ExtendedPublicKey requestParentExtendedPublicKey() throws ErgoLedgerException {
		ErgoResponse.ExtendedPublicKey extPubKeyData = protocol.getExtendedPublicKey(new int[] { h(44), h(429), h(0) }, null);
		DerivationPath path = new DerivationPath(JavaHelpers.toIndexedSeq(List.of(h(44), h(429), h(0))), false);
		return new ExtendedPublicKey(extPubKeyData.compressedPublicKey(), extPubKeyData.chainCode(), path)
				.child(0);
	}

	public AttestedBoxFrame[] getAttestedBoxFrames(InputBox box) throws ErgoLedgerException {
		byte[] txId = HexFormat.of().parseHex(box.getTransactionId());
		byte[] ergoTree = box.getErgoTree().bytes();
		byte[] registers = serializeRegisters(box.getRegisters());
		List<ErgoToken> boxTokens = box.getTokens();
		System.out.println("Attest box start");
		int sessionId = protocol.attestBoxStart(txId, box.getTransactionIndex(), box.getValue(), ergoTree.length, box.getCreationHeight(), boxTokens.size(), registers.length, null);
		int frameCount = -1;
		System.out.println("ergoTree[" + ergoTree.length + "] = " + HexFormat.ofDelimiter(" ").formatHex(ergoTree));
		double chunkCount = Math.ceil(ergoTree.length / 255.0);
		System.out.println("chunkCount = " + chunkCount);
		for (int i = 0; i < chunkCount; i++) {
			if (frameCount != -1) throw new IllegalStateException("Box frame is identified as finished but it is not");
			int start = i * 255;
			byte[] chunk = Arrays.copyOfRange(ergoTree, start, Math.min(start + 255, ergoTree.length));
			System.out.println("Attest add ergo tree chunk");
			Optional<Integer> result = protocol.attestAddErgoTreeChunk(sessionId, chunk);
			if (result.isPresent()) {
				frameCount = result.get();
			}
		}
		if (!boxTokens.isEmpty()) {
			if (boxTokens.size() > 20) {
				throw new IllegalArgumentException("Max 20 tokens");
			}
			ArrayList<Map.Entry<byte[], Long>> tokens = new ArrayList<>();
			for (ErgoToken boxToken : boxTokens) {
				tokens.add(Map.entry(boxToken.getId().getBytes(), boxToken.getValue()));
			}
			// max 6 tokens per exchange, so do it in chunks
			int perChunk = 6;
			for (int i = 0; i < Math.ceil(tokens.size() / (double) perChunk); i++) {
				if (frameCount != -1) throw new IllegalStateException("Box frame is identified as finished but it is not");
				LinkedHashMap<byte[], Long> chunk = new LinkedHashMap<>();
				for (int j = i; j < Math.min(i + perChunk, tokens.size()); j++) {
					chunk.put(tokens.get(j).getKey(), tokens.get(j).getValue());
				}
				System.out.println("Attest add tokens");
				Optional<Integer> result = protocol.attestAddTokens(sessionId, chunk);
				if (result.isPresent()) {
					frameCount = result.get();
				}
			}
		}
		if (!box.getRegisters().isEmpty()) {
			System.out.println("Attest add registers chunk");
			for (int i = 0; i < Math.ceil(registers.length / 255.0); i++) {
				if (frameCount != -1) throw new IllegalStateException("Box frame is identified as finished but it is not");
				int start = i * 255;
				byte[] chunk = Arrays.copyOfRange(registers, start, Math.min(start + 255, registers.length));
				Optional<Integer> result = protocol.attestAddRegistersChunk(sessionId, chunk);
				if (result.isPresent()) {
					frameCount = result.get();
				}
			}
		}
		if (frameCount == -1) {
			throw new IllegalStateException();
		}
		AttestedBoxFrame[] frames = new AttestedBoxFrame[frameCount];
		for (int i = 0; i < frameCount; i++) {
			System.out.println("Attest get box frame");
			AttestedBoxFrame frame = protocol.getAttestedBoxFrame(sessionId, i);
			frames[i] = frame;
		}
		System.out.println("RETURN");
		return frames;
	}

	/**
	 * @param changeAddress This is used to mark the change output box and make the Ledger not show the amount as part of the total outgoing amount. Can be null.
	 * @param changePath The derivation path of the change address. Can be null.
	 * @return The signature of this transaction
	 */
	public byte[] signTransaction(ErgoNetworkType networkType, List<AttestedBox> inputBoxes, List<InputBox> dataBoxes, List<OutBox> outputBoxes, Address changeAddress, int[] changePath) throws ErgoLedgerException {
		int sessionId = protocol.startP2PKSigning(networkType, new int[] { h(44), h(429), h(0), 0, 0 }, null);

		ArrayList<byte[]> distinctTokenIds = new ArrayList<>();
		for (OutBox outputBox : outputBoxes) {
			for (ErgoToken token : outputBox.getTokens()) {
				byte[] id = token.getId().getBytes();
				if (distinctTokenIds.stream().noneMatch(a -> Arrays.equals(a, id)))
					distinctTokenIds.add(id);
			}
		}

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
		addOutputs(sessionId, outputBoxes, networkType, distinctTokenIds, changeAddress, changePath);

		return protocol.confirmAndSign(sessionId);
	}

	private void addInputs(int sessionId, List<AttestedBox> boxesToSpend) {
		for (AttestedBox attestedBox : boxesToSpend) {

			for (AttestedBoxFrame frame : attestedBox.frames()) {
				LinkedHashMap<byte[], Long> tokens = new LinkedHashMap<>();
				frame.tokens().forEach((tokenId, value) -> tokens.put(tokenId.bytes(), value));
				protocol.addInputBoxFrame(sessionId, frame.boxId(), frame.framesCount(), frame.frameIndex(), frame.amount(), tokens, frame.attestation(),
						attestedBox.extension().length);
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
			byte[] registers = serializeRegisters(box.getRegisters());
			protocol.addOutputBoxStart(sessionId, box.getValue(), treeBytes.length, box.getCreationHeight(), distinctTokenIds.size(), registers.length);
			if (ergoTree.equals(MINER_FEE_TREE)) {
				protocol.addOutputBoxMinersFeeTree(sessionId);
			} else if (Address.fromErgoTree(ergoTree, networkType == ErgoNetworkType.MAINNET ? NetworkType.MAINNET : NetworkType.TESTNET).equals(changeAddress)) {
				protocol.addOutputBoxChangeTree(sessionId, changePath);
			} else {
				writeInChunks(protocol::addOutputBoxErgoTreeChunk, sessionId, treeBytes);
			}
			LinkedHashMap<Integer, Long> tokens = new LinkedHashMap<>();
			for (ErgoToken token : box.getTokens()) {
				int tokenIndex = -1;
				for (int i = 0; i < distinctTokenIds.size(); i++) {
					if (Arrays.equals(distinctTokenIds.get(i), token.getId().getBytes())) {
						tokenIndex = i;
						break;
					}
				}
				if (tokenIndex == -1) throw new IllegalStateException();
				tokens.put(tokenIndex, token.getValue());
			}
			if (!tokens.isEmpty())
				protocol.addOutputBoxTokens(sessionId, tokens);
			// write registers
			if (registers.length > 0)
				writeInChunks(protocol::addOutputBoxRegistersChunk, sessionId, registers);
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
			int start = i * 255;
			byte[] chunk = Arrays.copyOfRange(bytes, start, Math.min(start + 255, bytes.length));
			Optional<T> returnValue = function.apply(sessionId, chunk);
			if (returnValue.isPresent()) {
				if (i < chunks - 1)
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

	private static final int HARDENED = 0x80000000;

	public static int h(int x) {
		return x + HARDENED;
	}
}
