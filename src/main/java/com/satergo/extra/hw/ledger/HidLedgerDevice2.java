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

	private final int channel = (int) Math.floor(Math.random() * 0xffff);

	private final HidDevice hidDevice;

	public HidLedgerDevice2(HidDevice hidDevice) {
		this.hidDevice = hidDevice;
		System.out.println(channel);
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
				head.put((byte) i);
				byte[] chunk = Arrays.copyOfRange(data, i * blockSize, (i + 1) * blockSize);
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

			if (Short.toUnsignedInt(chunk.getShort()) != channel)
				throw new RuntimeException("Invalid channel");

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
	public int write(byte[] bytes) {
		List<byte[]> blocks = new HidFraming(channel, PACKET_SIZE).makeBlocks(bytes);
		for (byte[] block : blocks) {
			hidDevice.write(block, block.length, (byte) 0);
		}
		return 0;
	}

	/** Reads a packet with a maximum size of 64 bytes */
	private HidFraming.ResponseAcc readSinglePacket(boolean isFirst) {
		HidFraming framing = new HidFraming(channel, PACKET_SIZE);
		HidFraming.ResponseAcc acc = null;
//		hidDevice.setNonBlocking(true);
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
//		hidDevice.setNonBlocking(false);
		return acc;
	}

	/** Reads all available packets into one response */
	private byte[] readResponse() {
		ArrayList<HidFraming.ResponseAcc> packets = new ArrayList<>();
//		int length = 0;
//		// each apdu can only contain 255 bytes of data so this is probably too high
//		// but the content of each packet can vary so there is no exact length to know beforehand
//		byte[] buffer = new byte[1000];
//		hidDevice.read(buffer);
//		int index = 0;
//		while (true) {
//			ByteBuffer chunk = ByteBuffer.wrap(buffer, index * PACKET_SIZE, Math.min((index + 1) * PACKET_SIZE, buffer.length));
//
//			index++;
//		}
		boolean first = true;
		HidFraming.ResponseAcc last = null;
		while (true) {
			HidFraming.ResponseAcc responseAcc = readSinglePacket(first);
			if (responseAcc == null)
				break;
//			length += responseAcc.dataLength;
			packets.add(responseAcc);
			System.out.println("got a single-packet " + responseAcc.dataLength);
			first = false;
			last = responseAcc;
		}
//		byte[] full = new byte[length];
//		System.out.println("full length = " + length);
//		for (HidFraming.ResponseAcc packet : packets) {
//			System.out.println("packet sequence = " + packet.sequence + ", data length = " + packet.data.length + " (" + packet.dataLength + "). writing to " + ((packet.sequence - 1) * PACKET_SIZE));
//			System.arraycopy(packet.data, 0, full, (packet.sequence - 1) * PACKET_SIZE, packet.dataLength);
//		}
		return last == null ? null : last.data;
	}

	@Override
	public int read(byte[] bytes) {
		throw new UnsupportedOperationException();
//		HidFraming framing = new HidFraming(channel, PACKET_SIZE);
//		byte[] result;
//		HidFraming.ResponseAcc acc = null;
//		int read = -1;
//		while ((result = framing.getReducedResult(acc)) == null) {
//			byte[] buffer = new byte[bytes.length];
//			read = hidDevice.read(buffer);
//			System.out.println("read (count " + read + ") = " + toStringUnsigned(buffer));
//			acc = framing.reduceResponse(acc, ByteBuffer.wrap(buffer));
//		}
//		System.arraycopy(result, 0, bytes, 0, result.length);
//		return read;
	}

	@Override
	public APDUResponse readAPDU(int dataSize) {
		return new APDUResponse(readResponse());
	}

	private final ReentrantLock lock = new ReentrantLock();

	@Override
	public APDUResponse exchange(APDUCommand apdu, int responseSize) {
		lock.lock();
		try {
			writeAPDU(apdu);
			return readAPDU(responseSize);
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
