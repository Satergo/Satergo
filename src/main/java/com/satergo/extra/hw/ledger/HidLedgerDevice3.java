package com.satergo.extra.hw.ledger;

import com.satergo.jledger.APDUCommand;
import com.satergo.jledger.APDUResponse;
import com.satergo.jledger.LedgerDevice;
import org.hid4java.HidDevice;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @see https://github.com/LedgerHQ/ledger-live/blob/develop/libs/ledgerjs/packages/react-native-hid/android/src/main/java/com/ledgerwallet/hid/LedgerHelper.java
 */
public class HidLedgerDevice3 implements LedgerDevice {

	private static final int PACKET_SIZE = 64;
	private static final byte TAG = 0x05;

	private final HidDevice hidDevice;
	private final int channel;

	public HidLedgerDevice3(HidDevice hidDevice) {
		this.hidDevice = hidDevice;
		this.channel = (int) Math.floor(Math.random() * 0xffff);
	}

	@Override public int getProductId() { return hidDevice.getProductId(); }
	@Override public boolean open() { return hidDevice.open(); }
	@Override public void close() { hidDevice.close(); }

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

	@Override
	public void writeAPDU(APDUCommand command) {
		byte[] payload = command.getBytes();
		// Offset inside the payload
		int offset = 0;
		ByteBuffer packetBuffer = ByteBuffer.allocate(PACKET_SIZE);
		int seq = 0;
		while (offset < payload.length) {
			packetBuffer.position(0);
			// Write header
			packetBuffer.putShort((short) this.channel);
			packetBuffer.put(TAG);
			packetBuffer.putShort((short) seq);
			// The header of the first packet in a sequence contains the length of the entire payload
			if (seq == 0) packetBuffer.putShort((short) payload.length);
			// Copy bytes from the payload into the packetBuffer
			int payloadChunkLength = Math.min(packetBuffer.remaining(), payload.length - offset);
			packetBuffer.put(payload, offset, payloadChunkLength);
			offset += payloadChunkLength;
			// Only relevant in the last packet. As all packets must be PACKET_SIZE, fill the rest of the buffer with
			// zeroes if the end has not been reached. (since the same buffer is reused it needs to be zeroed)
			while (packetBuffer.hasRemaining())
				packetBuffer.put((byte) 0);
			// Write the data to the device, with the report id 0
			hidDevice.write(packetBuffer.array(), PACKET_SIZE, (byte) 0);
			seq++;
		}
	}

	private void readHeader(ByteBuffer byteBuf, int expectedSequenceIndex) {
		int channel = Short.toUnsignedInt(byteBuf.getShort());
		if (channel != this.channel)
			throw new InvalidChannelException("Invalid channel", channel);
		if (byteBuf.get() != TAG)
			throw new IllegalArgumentException("Invalid tag");
		if (Short.toUnsignedInt(byteBuf.getShort()) != expectedSequenceIndex)
			throw new IllegalArgumentException("Invalid sequence index");
	}

	@Override
	public APDUResponse readAPDU() {
		byte[] readBuffer = new byte[PACKET_SIZE];
		hidDevice.read(readBuffer);
		ByteBuffer firstBuf = ByteBuffer.wrap(readBuffer);
		int sequenceIndex = 0;
		readHeader(firstBuf, sequenceIndex++);
		int responseLength = Short.toUnsignedInt(firstBuf.getShort());
		// Resize it to not include the empty data at the end because 64 bytes are returned regardless of how much actual data there is
		if (responseLength + firstBuf.position() < PACKET_SIZE)
			firstBuf.limit(firstBuf.position() + responseLength);
		// Allocate the result buffer
		ByteBuffer result = ByteBuffer.allocate(responseLength);
		// Copy the first packet's data
		result.put(firstBuf);
		while (result.hasRemaining()) {
			hidDevice.read(readBuffer);
			ByteBuffer byteBuf = ByteBuffer.wrap(readBuffer);
			readHeader(byteBuf, sequenceIndex++);
			byteBuf.limit(Math.min(PACKET_SIZE, byteBuf.position() + result.remaining()));
			result.put(byteBuf);
		}
		return new APDUResponse(result.array());
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
}
