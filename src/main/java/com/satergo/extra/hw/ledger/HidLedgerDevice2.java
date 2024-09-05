package com.satergo.extra.hw.ledger;

import com.satergo.jledger.APDUCommand;
import com.satergo.jledger.APDUResponse;
import com.satergo.jledger.LedgerDevice;
import org.hid4java.HidDevice;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

public class HidLedgerDevice2 implements LedgerDevice {

	private static final int PACKET_SIZE = 64;
	private static final byte TAG = 0x05;

	private final HidDevice hidDevice;
	private final HidFraming framing;

	public HidLedgerDevice2(HidDevice hidDevice) {
		this.hidDevice = hidDevice;
		int channel = (int) Math.floor(Math.random() * 0xffff);
		this.framing = new HidFraming(channel, PACKET_SIZE);
	}

	@Override
	public int getProductId() {
		return hidDevice.getProductId();
	}

	@Override
	public boolean open() {
		return hidDevice.open();
	}

	@Override
	public void close() {
		hidDevice.close();
	}

	/**
	 * Thrown when an incorrect channel is received from the Ledger device,
	 * it is known to be 0 when the device is locked.
	 */
	public static class InvalidChannelException extends IllegalStateException {
		public final int received;

		public InvalidChannelException(String s, int received) {
			super(s);
			this.received = received;
		}
	}

	private static class HidFraming {
		private final int channel, packetSize;

		public HidFraming(int channel, int packetSize) {
			this.channel = channel;
			this.packetSize = packetSize;
		}

		private static byte[] concat(byte[] a, byte[] b) {
			byte[] c = new byte[a.length + b.length];
			System.arraycopy(a, 0, c, 0, a.length);
			System.arraycopy(b, 0, c, a.length, b.length);
			return c;
		}

		public List<byte[]> makeBlocks(byte[] apdu) {
			byte[] data = ByteBuffer.allocate(2 + apdu.length).putShort((short) apdu.length).put(apdu).array();
			int blockSize = packetSize - 5;
			int nbBlocks = (int) Math.ceil((double) data.length / (double) blockSize);
			data = Arrays.copyOf(data, data.length + nbBlocks * blockSize - data.length + 1);
			ArrayList<byte[]> blocks = new ArrayList<>();

			for (int i = 0; i < nbBlocks; i++) {
				ByteBuffer head = ByteBuffer.allocate(5);
				head.putShort((short) channel);
				head.put(TAG);
				head.putShort((short) i);
				byte[] chunk = Arrays.copyOfRange(data, i * blockSize, Math.min(data.length, (i + 1) * blockSize));
				blocks.add(concat(head.array(), chunk));
			}

			return blocks;
		}

		public record ResponseAcc(byte[] data, int dataLength, int sequence) {
			public static final ResponseAcc INITIAL = new ResponseAcc(new byte[0], 0, 0);
		}
		public ResponseAcc reduceResponse(ResponseAcc acc, ByteBuffer chunk) {
			ResponseAcc temp = acc == null ? ResponseAcc.INITIAL : acc;
			byte[] data = temp.data;
			int dataLength = temp.dataLength,
					sequence = temp.sequence;

			int ch = Short.toUnsignedInt(chunk.getShort());
			if (ch != channel)
				throw new InvalidChannelException("Invalid channel", ch);

			if (chunk.get() != TAG)
				throw new RuntimeException("Invalid tag");

			if (Short.toUnsignedInt(chunk.getShort()) != sequence)
				throw new RuntimeException("Invalid sequence");

			if (acc == null) {
				dataLength = Short.toUnsignedInt(chunk.getShort());
			}

			sequence++;
			byte[] chunkData = new byte[chunk.remaining()];
			chunk.get(chunkData);
			data = concat(data, chunkData);

			if (data.length > dataLength) {
				data = Arrays.copyOfRange(data, 0, dataLength);
			}

			return new ResponseAcc(data, dataLength, sequence);
		}

		// nullable
		public byte[] getReducedResult(ResponseAcc acc) {
			if (acc != null && acc.dataLength == acc.data.length) {
				return acc.data;
			}
			return null;
		}
	}

	@Override
	public void writeAPDU(APDUCommand command) {
		List<byte[]> blocks = framing.makeBlocks(command.getBytes());
		System.out.println("Writing APDU of length " + command.getBytes().length + " partitioned into " + blocks.size() + " blocks");
		for (byte[] block : blocks) {
			hidDevice.write(block, block.length, (byte) 0);
		}
	}

	/** Reads a packet with a maximum size of 64 bytes */
	private HidFraming.ResponseAcc readSinglePacket(boolean isFirst) {
		HidFraming.ResponseAcc acc = null;
		while (framing.getReducedResult(acc) == null) {
			byte[] buffer = new byte[PACKET_SIZE];
			System.out.println("reading " + PACKET_SIZE + " bytes");
			// if this is the first packet of a complete response, we need to wait for it to be received
			// this can take time because the user might have to confirm an action
			// but if it isn't the first packet of the response, we cannot wait because if the response
			// has ended then it would wait forever
			int bytesRead = isFirst ? hidDevice.read(buffer) : hidDevice.read(buffer, 1000);
			System.out.println("got " + bytesRead + " bytes");
			if (bytesRead == 0) break;
			acc = framing.reduceResponse(acc, ByteBuffer.wrap(buffer));
			System.out.println("acc = " + acc + ", acc.data.length = " + acc.data.length);
		}
		return acc;
	}

	/** Reads all available packets into one response */
	private byte[] readResponse() {
		boolean first = true;
		HidFraming.ResponseAcc last = null;
		while (true) {
			HidFraming.ResponseAcc responseAcc = readSinglePacket(first);
			if (responseAcc == null)
				break;
			System.out.println("got a single-packet " + responseAcc.dataLength);
			first = false;
			last = responseAcc;
		}
		return last == null ? null : last.data;
	}

	@Override
	public APDUResponse readAPDU() {
		return new APDUResponse(readResponse());
	}

	private final ReentrantLock lock = new ReentrantLock();

	@Override
	public APDUResponse exchange(APDUCommand apdu) {
		lock.lock();
		try {
			writeAPDU(apdu);
			return readAPDU();
		} finally {
			lock.unlock();
		}
	}

	private static String toStringUnsigned(byte[] bytes) {
		StringBuilder s = new StringBuilder("[");
		for (int i = 0; i < bytes.length; i++) {
			s.append(bytes[i] & 0xFF);
			if (i != bytes.length - 1)
				s.append(", ");
		}
		s.append("]");
		return s.toString();
	}
}
