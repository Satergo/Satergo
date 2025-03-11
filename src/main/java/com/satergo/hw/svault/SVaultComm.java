package com.satergo.hw.svault;

import com.welie.blessed.*;
import com.welie.blessed.BluetoothGattCharacteristic.WriteType;
import org.ergoplatform.sdk.wallet.secrets.ExtendedPublicKey;
import org.ergoplatform.sdk.wallet.secrets.ExtendedPublicKeySerializer;
import sigmastate.serialization.SigmaSerializer;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Stream;

@SuppressWarnings("NullableProblems")
public class SVaultComm {

	public static final int PROTOCOL_VERSION = 0;

	static final UUID SERVICE = UUID.fromString("fb5c5415-fa44-4c3c-a9bb-9f913f2de7dc");

	public BiConsumer<BluetoothPeripheral, List<BluetoothGattService>> onServicesDiscovered;

	private final Consumer<Boolean> closeConnection;

	/**
	 * @param closeConnection boolean: whether to shut down manager
	 */
	public SVaultComm(Consumer<Boolean> closeConnection) {
		this.closeConnection = closeConnection;
	}

	public void close(boolean shutdownManager) {
		closeConnection.accept(shutdownManager);
	}

	private static class TaskFuture<T extends Task<V>, V> extends CompletableFuture<V> {
		private final T task;

		public TaskFuture(T task) {
			this.task = task;
		}
	}

	private static class Task<V> {
		public static Task<AppInfo> APP_INFO = new Task<>("7fb8924e-baf8-4227-a0d9-52e34aef6c4a");
		public static Task<ExtendedPublicKey> EXT_PUB_KEY = new Task<>("3cd9898b-c684-4407-a830-08f71a40303a");
		public static Task<Void> SIGN_REQUEST = new Task<>("07ccb789-fdcc-4d77-ba0a-7ed711dc3a6d");
		public static Task<List<byte[]>> SIGNATURES = new Task<>("408e8d2a-bf45-4c7b-a890-ff82a9840cb3");

		public final UUID chUuid;
		private Task(String chUuid) {
			this.chUuid = UUID.fromString(chUuid);
		}
	}

	public record AppInfo(int protocolVersion, int appVersionCode, String appVersion, String appId, String appName) {}

	private BluetoothPeripheral peripheral;

	private final ArrayList<TaskFuture<?, ?>> pendingReads = new ArrayList<>();

	private static class ChunkedProcess {
		CompletableFuture<Void> future;
		UUID characteristic;
		byte[] data;
		int offset;
		public int perChunk;
	}

	private ChunkedProcess chunkedRead;
	private ChunkedProcess chunkedWrite;

	@SuppressWarnings("unchecked")
	private <T>Optional<TaskFuture<Task<T>, T>> ptf(Task<T> task) {
		return pendingReads.stream().filter(t -> t.task == task).findAny().map(t -> (TaskFuture<Task<T>, T>) t);
	}
	
	public final BluetoothPeripheralCallback peripheralCallback = new BluetoothPeripheralCallback() {
		@Override
		public void onServicesDiscovered(BluetoothPeripheral peripheral, List<BluetoothGattService> services) {
			SVaultComm.this.peripheral = peripheral;
			if (onServicesDiscovered != null)
				onServicesDiscovered.accept(peripheral, services);
		}

		@Override
		public void onCharacteristicUpdate(BluetoothPeripheral peripheral, byte[] value, BluetoothGattCharacteristic characteristic, BluetoothCommandStatus status) {
			Task<?> task = Stream.of(Task.APP_INFO, Task.EXT_PUB_KEY, Task.SIGNATURES).filter(t -> t.chUuid.equals(characteristic.getUuid())).findAny().orElse(null);
			if (status == BluetoothCommandStatus.COMMAND_SUCCESS) {
				if (chunkedRead != null && chunkedRead.characteristic.equals(characteristic.getUuid())) {
					System.arraycopy(value, 0, chunkedRead.data, chunkedRead.offset, value.length);
					chunkedRead.offset += value.length;
					if (chunkedRead.offset == chunkedRead.data.length) {
						chunkedRead.future.complete(null);
						chunkedRead = null;
					}
					return;
				}
				try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(value))) {
					if (task == Task.APP_INFO) {
						var ptf = ptf(Task.APP_INFO).orElseThrow();
						pendingReads.remove(ptf);
						ptf.complete(new AppInfo(in.readInt(), in.readInt(), in.readUTF(), in.readUTF(), in.readUTF()));
					} else if (task == Task.EXT_PUB_KEY) {
						var ptf = ptf(Task.EXT_PUB_KEY).orElseThrow();
						pendingReads.remove(ptf);
						ptf.complete(ExtendedPublicKeySerializer.parse(SigmaSerializer.startReader(value, 0)));
					} else if (task == Task.SIGNATURES) {
						int length = in.readUnsignedShort();
						if (length <= 510) {
							var ptf = ptf(Task.SIGNATURES).orElseThrow();
							pendingReads.remove(ptf);
							ptf.complete(deserializeSignatures(in));
						} else {
							ChunkedProcess cr = new ChunkedProcess();
							cr.future = new CompletableFuture<>();
							cr.future.handle((unused, throwable) -> {
								var ptf = ptf(Task.SIGNATURES).orElseThrow();
								pendingReads.remove(ptf);
								if (throwable != null) ptf.completeExceptionally(throwable);
								else {
									try {
										ptf.complete(deserializeSignatures(new DataInputStream(new ByteArrayInputStream(cr.data))));
									} catch (IOException e) {
										throw new RuntimeException(e);
									}
								}
								return null;
							});
							cr.characteristic = Task.SIGNATURES.chUuid;
							cr.data = new byte[length];
							in.readFully(cr.data);
							cr.offset = 510;
							cr.perChunk = 512;
							chunkedRead = cr;
						}
					}
				} catch (IOException ignore) {
				}
			} else {
				if (task != null) {
					ptf(task).orElseThrow().completeExceptionally(new IllegalStateException("Illegal status " + status));
				} else {
					throw new IllegalStateException("Illegal state " + status);
				}
			}
		}

		@Override
		public void onCharacteristicWrite(BluetoothPeripheral peripheral, byte[] value, BluetoothGattCharacteristic ch, BluetoothCommandStatus status) {
			System.out.println("Written " + ch);
			if (chunkedWrite != null && ch.getUuid().equals(chunkedWrite.characteristic)) {
				if (status != BluetoothCommandStatus.COMMAND_SUCCESS) {
					chunkedWrite.future.completeExceptionally(new IllegalStateException("Illegal status " + status));
				} else {
					int to = Math.min(chunkedWrite.data.length, chunkedWrite.offset + chunkedWrite.perChunk);
					peripheral.writeCharacteristic(ch, Arrays.copyOfRange(chunkedWrite.data, chunkedWrite.offset, to), WriteType.WITHOUT_RESPONSE);
					if (to == chunkedWrite.data.length) {
						chunkedWrite.future.complete(null);
						chunkedWrite = null;
					} else {
						chunkedWrite.offset += chunkedWrite.perChunk;
					}
				}
			} else {
				if (status != BluetoothCommandStatus.COMMAND_SUCCESS) {
					System.out.println("FAILED TO WRITE!");
					throw new IllegalStateException("Failed to write");
				}
			}
		}
	};

	public BluetoothPeripheral peripheral() {
		return peripheral;
	}

	private <T>TaskFuture<Task<T>, T> startReadTask(TaskFuture<Task<T>, T> taskFuture) {
		pendingReads.add(taskFuture);
		peripheral.readCharacteristic(SERVICE, taskFuture.task.chUuid);
		return taskFuture;
	}

	// Getting data

	public CompletableFuture<AppInfo> appInfo() {
		if (ptf(Task.APP_INFO).isPresent())
			throw new IllegalStateException();
		return startReadTask(new TaskFuture<>(Task.APP_INFO));
	}

	public CompletableFuture<ExtendedPublicKey> extendedPublicKey() {
		if (ptf(Task.EXT_PUB_KEY).isPresent())
			throw new IllegalStateException();
		return startReadTask(new TaskFuture<>(Task.EXT_PUB_KEY));
	}

	public CompletableFuture<List<byte[]>> getSignatures() {
		if (ptf(Task.SIGNATURES).isPresent())
			throw new IllegalStateException();
		return startReadTask(new TaskFuture<>(Task.SIGNATURES));
	}

	// Sending data

	public CompletableFuture<Void> sendSignRequest(byte[] txData, Collection<Integer> inputAddresses, Integer changeAddress) {
		if (ptf(Task.SIGN_REQUEST).isPresent())
			throw new IllegalStateException();
		// The limit is 512 bytes, so we might need to do it in chunks.
		ByteBuffer buffer = ByteBuffer.allocate(2 + inputAddresses.size() * 4 + 1 + (changeAddress != null ? 4 : 0) + txData.length)
				.putShort((short) inputAddresses.size());
		inputAddresses.forEach(buffer::putInt);
		if (changeAddress != null) {
			buffer.put((byte) 1);
			buffer.putInt(changeAddress);
		} else {
			buffer.put((byte) 0);
		}
		buffer.put(txData);
		byte[] data = buffer.array();
		System.out.println("Writing " + data.length + " tx bytes");
		if (data.length <= 510) {
			byte[] fullData = ByteBuffer.allocate(2 + data.length)
					.putShort((short) data.length)
					.put(data)
					.array();
			peripheral.writeCharacteristic(SERVICE, Task.SIGN_REQUEST.chUuid, fullData, WriteType.WITHOUT_RESPONSE);
			return CompletableFuture.completedFuture(null);
		} else {
			byte[] firstChunk = ByteBuffer.allocate(512)
					.putShort((short) data.length)
					.put(data, 0, 510).array();
			ChunkedProcess cw = new ChunkedProcess();
			cw.future = new CompletableFuture<>();
			cw.characteristic = Task.SIGN_REQUEST.chUuid;
			cw.data = data;
			cw.offset = 510;
			cw.perChunk = 512;
			chunkedWrite = cw;
			peripheral.writeCharacteristic(SERVICE, Task.SIGN_REQUEST.chUuid, firstChunk, WriteType.WITHOUT_RESPONSE);
			return cw.future;
		}
	}




	private static List<byte[]> deserializeSignatures(DataInputStream in) throws IOException {
		ArrayList<byte[]> signatures = new ArrayList<>();
		while (in.available() > 0) {
			int signatureLength = Short.toUnsignedInt(in.readShort());
			byte[] signature = new byte[signatureLength];
			in.readFully(signature);
			signatures.add(signature);
		}
		return Collections.unmodifiableList(signatures);
	}
}
