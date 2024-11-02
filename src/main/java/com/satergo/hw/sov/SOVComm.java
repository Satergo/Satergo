package com.satergo.hw.sov;

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
import java.util.stream.Stream;

@SuppressWarnings("NullableProblems")
public class SOVComm {

	static final UUID SERVICE = UUID.fromString("fb5c5415-fa44-4c3c-a9bb-9f913f2de7dc");
	public BiConsumer<BluetoothPeripheral, List<BluetoothGattService>> onServicesDiscovered;

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

	public record AppInfo(int versionCode, String version) {}

	private BluetoothPeripheral peripheral;

	private final ArrayList<TaskFuture<?, ?>> pendingReads = new ArrayList<>();

	private CompletableFuture<Void> pendingChunkedWrite;
	private UUID chunkedWriteUuid;
	private byte[] chunkedWrite;
	private int chunkedWriteOffset = 0;
	private int perChunk = 512;

	@SuppressWarnings("unchecked")
	private <T>Optional<TaskFuture<Task<T>, T>> ptf(Task<T> task) {
		return pendingReads.stream().filter(t -> t.task == task).findAny().map(t -> (TaskFuture<Task<T>, T>) t);
	}
	
	public final BluetoothPeripheralCallback peripheralCallback = new BluetoothPeripheralCallback() {
		@Override
		public void onServicesDiscovered(BluetoothPeripheral peripheral, List<BluetoothGattService> services) {
			SOVComm.this.peripheral = peripheral;
			if (onServicesDiscovered != null)
				onServicesDiscovered.accept(peripheral, services);
		}

		@Override
		public void onCharacteristicUpdate(BluetoothPeripheral peripheral, byte[] value, BluetoothGattCharacteristic characteristic, BluetoothCommandStatus status) {
			Task<?> task = Stream.of(Task.APP_INFO, Task.EXT_PUB_KEY, Task.SIGNATURES).filter(t -> t.chUuid.equals(characteristic.getUuid())).findAny().orElse(null);
			if (status == BluetoothCommandStatus.COMMAND_SUCCESS) {
				try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(value))) {
					if (task == Task.APP_INFO) {
						var ptf = ptf(Task.APP_INFO).orElseThrow();
						pendingReads.remove(ptf);
						ptf.complete(new AppInfo(in.readInt(), in.readUTF()));
					} else if (task == Task.EXT_PUB_KEY) {
						var ptf = ptf(Task.EXT_PUB_KEY).orElseThrow();
						pendingReads.remove(ptf);
						ptf.complete(ExtendedPublicKeySerializer.parse(SigmaSerializer.startReader(value, 0)));
					} else if (task == Task.SIGNATURES) {
						System.out.println("SIGNATURES status = " + status);
						System.out.println("SIGNATURES value = " + Arrays.toString(value));
						ArrayList<byte[]> signatures = new ArrayList<>();
						while (in.available() > 0) {
							int signatureLength = Short.toUnsignedInt(in.readShort());
							byte[] signature = new byte[signatureLength];
							in.readFully(signature);
							signatures.add(signature);
						}
						var ptf = ptf(Task.SIGNATURES).orElseThrow();
						pendingReads.remove(ptf);
						ptf.complete(Collections.unmodifiableList(signatures));
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
			if (ch.getUuid().equals(chunkedWriteUuid) && chunkedWrite != null) {
				if (status != BluetoothCommandStatus.COMMAND_SUCCESS) {
					pendingChunkedWrite.completeExceptionally(new IllegalStateException("Illegal status " + status));
				} else {
					int to = Math.min(chunkedWrite.length, chunkedWriteOffset + perChunk);
					peripheral.writeCharacteristic(ch, Arrays.copyOfRange(chunkedWrite, chunkedWriteOffset, to), WriteType.WITHOUT_RESPONSE);
					if (to == chunkedWrite.length) {
						pendingChunkedWrite.complete(null);
						chunkedWriteUuid = null;
						chunkedWrite = null;
					} else {
						chunkedWriteOffset += perChunk;
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

	public CompletableFuture<Void> sendSignRequest(byte[] data) {
		if (ptf(Task.SIGN_REQUEST).isPresent())
			throw new IllegalStateException();
		// The limit is 512 bytes, so we might need to do it in chunks.
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
			pendingChunkedWrite = new CompletableFuture<>();
			chunkedWriteUuid = Task.SIGN_REQUEST.chUuid;
			chunkedWrite = data;
			chunkedWriteOffset = 510;
			perChunk = 512;
			peripheral.writeCharacteristic(SERVICE, Task.SIGN_REQUEST.chUuid, firstChunk, WriteType.WITHOUT_RESPONSE);
			return pendingChunkedWrite;
		}
	}
}
