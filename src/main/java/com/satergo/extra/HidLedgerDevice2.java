package com.satergo.extra;

import com.satergo.jledger.LedgerDevice;
import org.hid4java.HidDevice;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class HidLedgerDevice2 implements LedgerDevice {

	private final int channel = (int) Math.floor(Math.random() * 0xffff);
	private static final int packetSize = 64;
	private static final byte TAG = 0x05;

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
			System.out.println(chunk + " = " + Arrays.toString(chunk.array()));
			ResponseAcc temp = acc == null ? ResponseAcc.INITIAL : acc;
			byte[] data = temp.data;
			int dataLength = temp.dataLength,
					sequence = temp.sequence;

			short sh = chunk.getShort();
			System.out.println(sh + " -- " + Short.toUnsignedInt(sh) + " -- " + channel);
			if (Short.toUnsignedInt(sh) != channel) {
//				throw new RuntimeException("Invalid channel");
			}

			if (chunk.get() != TAG) {
				throw new RuntimeException("Invalid tag");
			}

			if (Short.toUnsignedInt(chunk.getShort()) != sequence) {
				throw new RuntimeException("Invalid sequence");
			}

			if (acc == null) {
				dataLength = Short.toUnsignedInt(chunk.getShort());
			}

			sequence++;
			byte[] chunkData = chunk.slice(acc != null ? 5 : 7, chunk.capacity()).array();
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
		List<byte[]> blocks = new HidFraming(channel, packetSize).makeBlocks(bytes);
		for (byte[] block : blocks) {
			hidDevice.sendFeatureReport(block, (byte) 0);
		}
		return hidDevice.write(bytes, bytes.length, (byte) 0);
	}

	@Override
	public int read(byte[] bytes) {
		HidFraming framing = new HidFraming(channel, packetSize);
		byte[] result;
		HidFraming.ResponseAcc acc = null;
		int read = -1;
		while ((result = framing.getReducedResult(acc)) == null) {
			byte[] buffer = new byte[bytes.length];
			read = hidDevice.getFeatureReport(buffer, (byte) 0);
			acc = framing.reduceResponse(acc, ByteBuffer.wrap(buffer));
		}
		System.arraycopy(result, 0, bytes, 0, result.length);
		return read;
	}

}
