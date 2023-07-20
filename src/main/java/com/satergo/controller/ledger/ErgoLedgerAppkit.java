package com.satergo.controller.ledger;

import com.satergo.jledger.LedgerDevice;
import com.satergo.jledger.protocol.ergo.ErgoLedgerException;
import com.satergo.jledger.protocol.ergo.ErgoNetworkType;
import com.satergo.jledger.protocol.ergo.ErgoProtocol;
import com.satergo.jledger.protocol.ergo.ErgoResponse;
import com.satergo.jledger.protocol.ergo.ErgoResponse.AttestedBoxFrame;
import org.ergoplatform.ErgoBox;
import org.ergoplatform.ErgoScriptPredef;
import org.ergoplatform.appkit.Address;
import org.ergoplatform.appkit.JavaHelpers;
import org.ergoplatform.appkit.NetworkType;
import org.ergoplatform.wallet.secrets.DerivationPath;
import org.ergoplatform.wallet.secrets.ExtendedPublicKey;
import scala.collection.JavaConverters;
import sigmastate.SType;
import sigmastate.Values;
import sigmastate.interpreter.ContextExtension;
import sigmastate.serialization.SigmaSerializer;
import sigmastate.utils.SigmaByteWriter;

import java.util.*;
import java.util.function.BiConsumer;

public class ErgoLedgerAppkit {

	private final ErgoProtocol protocol;
	public final LedgerDevice device;

	public ErgoLedgerAppkit(ErgoProtocol protocol) {
		this.protocol = protocol;
		this.device = protocol.device;
	}

	private static final Values.ErgoTree MINER_FEE_TREE = ErgoScriptPredef.feeProposition(720);

	public static byte[] serializeContextExtension(ContextExtension contextExtension) {
		if (contextExtension.values().isEmpty()) return new byte[0];
		SigmaByteWriter sbw = SigmaSerializer.startWriter();
		// why are you like this scala, why don't you let me use ContentExtension.serializer.serialize()???
		ContextExtension.serializer$.MODULE$.serialize(contextExtension, sbw);
		return sbw.toBytes();
	}

	public ExtendedPublicKey requestParentExtendedPublicKey() throws ErgoLedgerException {
		ErgoResponse.ExtendedPublicKey extPubKeyData = protocol.getExtendedPublicKey(new long[] { h(44), h(429), h(0) }, null);
		DerivationPath path = new DerivationPath(JavaHelpers.toIndexedSeq(List.of((int) h(44), (int) h(429), (int) h(0))), false);
		return new ExtendedPublicKey(extPubKeyData.compressedPublicKey(), extPubKeyData.chainCode(), path)
				.child(0);
	}

	public AttestedBoxFrame[] getAttestedBoxFrames(ErgoBox box) throws ErgoLedgerException {
		byte[] txId = HexFormat.of().parseHex((String) box.transactionId());
		byte[] ergoTree = box.ergoTree().bytes();
		byte[] registers = serializeRegisters(box.additionalRegisters());
		int sessionId = protocol.attestBoxStart(txId, Short.toUnsignedInt(box.index()), box.value(), ergoTree.length, box.creationHeight(), box.tokens().size(), registers.length, null);
		int frameCount = 0;
		double chunkCount = Math.ceil(ergoTree.length / 255.0);
		for (int i = 0; i < chunkCount; i++) {
			int start = i * 255;
			byte[] chunk = Arrays.copyOfRange(ergoTree, start, Math.min(start + 255, ergoTree.length));
			Optional<Integer> result = protocol.attestAddErgoTreeChunk(sessionId, chunk);
			if (result.isPresent()) frameCount = result.get();
		}
		if (!box.tokens().isEmpty()) {
			@SuppressWarnings({"unchecked", "rawtypes"})
			Map<String, Long> map = (Map<String, Long>) (Map) JavaConverters.mapAsJavaMap(box.tokens());
			LinkedHashMap<byte[], Long> tokens = new LinkedHashMap<>();
			map.forEach((id, amount) -> tokens.put(HexFormat.of().parseHex(id), amount));
			protocol.attestAddTokens(sessionId, tokens);
		}
		if (box.additionalRegisters().size() > 0) {
			writeInChunks(protocol::attestAddRegistersChunk, sessionId, registers);
		}
		AttestedBoxFrame[] attestedBoxFrames = new AttestedBoxFrame[frameCount];
		for (int i = 0; i < frameCount; i++) {
			AttestedBoxFrame attestedBoxFrame = protocol.getAttestedBoxFrame(sessionId, i);
			attestedBoxFrames[i] = attestedBoxFrame;
		}
		return attestedBoxFrames;
	}

	/**
	 * @param changeAddress This is used to mark the change output box and make the Ledger not show the amount as part of the total outgoing amount. Can be null.
	 * @param changePath The derivation path of the change address. Can be null.
	 * @return The signature of this transaction
	 */
	public byte[] signTransaction(ErgoNetworkType networkType, List<AttestedBox> inputBoxes, List<ErgoBox> dataBoxes, List<ErgoBox> outputBoxes, Address changeAddress, long[] changePath) throws ErgoLedgerException {
		int sessionId = protocol.startP2PKSigning(networkType, new long[] { h(44), h(429), h(0), 0, 0 }, null);

		ArrayList<byte[]> distinctTokenIds = new ArrayList<>();
		for (ErgoBox outputBox : outputBoxes) {
			outputBox.tokens().foreach(v1 -> {
				byte[] id = HexFormat.of().parseHex((String) v1._1());
				if (distinctTokenIds.stream().noneMatch(a -> Arrays.equals(a, id)))
					distinctTokenIds.add(id);
				return null;
			});
		}
		if (!distinctTokenIds.isEmpty())
			protocol.addTokenIds(sessionId, distinctTokenIds.toArray(new byte[0][0]));

		protocol.startTransaction(sessionId, inputBoxes.size(), dataBoxes.size(), distinctTokenIds.size(), outputBoxes.size());

		addInputs(sessionId, inputBoxes);
		if (!dataBoxes.isEmpty())
			addDataInputs(sessionId, dataBoxes);
		addOutputs(sessionId, outputBoxes, networkType, distinctTokenIds, changeAddress, changePath);

		return protocol.confirmAndSign(sessionId);
	}

	private void addInputs(int sessionId, List<AttestedBox> boxesToSpend) {
		for (AttestedBox attestedBox : boxesToSpend) {

			for (AttestedBoxFrame frame : attestedBox.frames()) {
				protocol.addInputBoxFrame(sessionId, frame.boxId(), frame.framesCount(), frame.frameIndex(), frame.amount(), new LinkedHashMap<>(), frame.attestation(),
						attestedBox.extension().length);
			}

			if (attestedBox.extension().length > 0) {
				writeInChunks(protocol::addInputBoxContextExtensionChunk, sessionId, attestedBox.extension());
			}
		}
	}

	private void addDataInputs(int sessionId, List<ErgoBox> dataBoxes) {
		protocol.addDataInputs(sessionId, dataBoxes.stream().map(box -> (byte[]) box.id()).toArray(byte[][]::new));
	}

	private void addOutputs(int sessionId, List<ErgoBox> boxes, ErgoNetworkType networkType, ArrayList<byte[]> distinctTokenIds, Address changeAddress, long[] changePath) {
		for (ErgoBox box : boxes) {
			Values.ErgoTree ergoTree = box.ergoTree();
			byte[] treeBytes = ergoTree.bytes();
			if (treeBytes.length == 0) throw new IllegalArgumentException("unsupported route"); // todo can it be empty?
			byte[] registers = serializeRegisters(box.additionalRegisters());
			protocol.addOutputBoxStart(sessionId, box.value(), treeBytes.length, box.creationHeight(), distinctTokenIds.size(), registers.length);
			if (ergoTree.equals(MINER_FEE_TREE)) {
				protocol.addOutputBoxMinersFeeTree(sessionId);
			} else if (Address.fromErgoTree(ergoTree, networkType == ErgoNetworkType.MAINNET ? NetworkType.MAINNET : NetworkType.TESTNET).equals(changeAddress)) {
				protocol.addOutputBoxChangeTree(sessionId, changePath);
			} else {
				writeInChunks(protocol::addOutputBoxErgoTreeChunk, sessionId, treeBytes);
			}
			LinkedHashMap<Integer, Long> tokens = new LinkedHashMap<>();
			box.tokens().foreach(v1 -> {
				tokens.put(indexOf(distinctTokenIds, HexFormat.of().parseHex((String) v1._1())), (long) v1._2());
				return null;
			});
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

	private static byte[] serializeRegisters(scala.collection.immutable.Map<ErgoBox.NonMandatoryRegisterId, ? extends Values.EvaluatedValue<? extends SType>> registers) {
		SigmaByteWriter sbw = SigmaSerializer.startWriter();
		registers.foreach(entry -> {
			sbw.putUByte(entry._1().asIndex());
			sbw.putValue(entry._2());
			return null;
		});
		return sbw.toBytes();
	}

	private static int indexOf(List<byte[]> byteArrays, byte[] byteArray) {
		for (int i = 0; i < byteArrays.size(); i++) {
			if (Arrays.equals(byteArrays.get(i), byteArray))
				return i;
		}
		return -1;
	}

	private static final long HARDENED = 0x80000000L;

	public static long h(long x) {
		return x + HARDENED;
	}
}
