package com.satergo.extra.hw.ledger;

import com.satergo.jledger.APDUCommand;
import com.satergo.jledger.APDUResponse;
import com.satergo.jledger.LedgerDevice;
import org.hid4java.HidDevice;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

public class HidLedgerDevice3 implements LedgerDevice {

	private static final int PACKET_SIZE = 64;
	private static final byte TAG = 0x05;

	private final HidDevice hidDevice;
	private final HidFraming framing;

	public HidLedgerDevice3(HidDevice hidDevice) {
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
			byte[] payload;
			{
				ByteArrayOutputStream output = new ByteArrayOutputStream();
				if (packetSize < 3) throw new IllegalArgumentException();
				int sequenceIdx = 0;
				int offset = 0;
				output.write(channel >> 8);
				output.write(channel);
				output.write(TAG);
				output.write(sequenceIdx >> 8);
				output.write(sequenceIdx);
				sequenceIdx++;
				output.write(apdu.length >> 8);
				output.write(apdu.length);
				int blockSize = Math.min(apdu.length, packetSize - 7);
				output.write(apdu, offset, blockSize);
				offset += blockSize;
				while (offset != apdu.length) {
					output.write(channel >> 8);
					output.write(channel);
					output.write(TAG);
					output.write(sequenceIdx >> 8);
					output.write(sequenceIdx);
					sequenceIdx++;
					blockSize = Math.min(apdu.length - offset, packetSize - 5);
					output.write(apdu, offset, blockSize);
					offset += blockSize;
				}
				if ((output.size() % packetSize) != 0) {
					byte[] padding = new byte[packetSize - (output.size() % packetSize)];
					output.write(padding, 0, padding.length);
				}
				payload = output.toByteArray();
			}
			ArrayList<byte[]> byteArrays = new ArrayList<>();
			int offset = 0;
			while (offset != payload.length) {
				byte[] transferBuffer = new byte[PACKET_SIZE];
				int blockSize = (Math.min(payload.length - offset, PACKET_SIZE));
				System.arraycopy(payload, offset, transferBuffer, 0, blockSize);
				byteArrays.add(transferBuffer);
				offset += blockSize;
			}
			return byteArrays;
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
