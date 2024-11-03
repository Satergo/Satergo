package com.satergo.hw.sov;

import com.welie.blessed.*;

import java.io.Closeable;
import java.io.IOException;
import java.util.Set;
import java.util.UUID;

@SuppressWarnings({"NullableProblems", "FieldCanBeLocal"})
public abstract class SOVFinder implements Closeable {

	private boolean closed = false;

	private SOVComm sovComm;
	private BluetoothPeripheral discovered;
	private final BluetoothCentralManager manager;
	private final BluetoothCentralManagerCallback managerCallback = new BluetoothCentralManagerCallback() {
		@Override
		public void onDiscoveredPeripheral(BluetoothPeripheral peripheral, ScanResult scanResult) {
			manager.stopScan();
			discovered = peripheral;
			discovered(peripheral);
		}

		@Override
		public void onDisconnectedPeripheral(BluetoothPeripheral peripheral, BluetoothCommandStatus status) {
			if (!closed)
				disconnected(sovComm, status);
		}
	};

	public SOVFinder() {
		manager = new BluetoothCentralManager(managerCallback, Set.of(BluetoothCentralManager.SCANOPTION_NO_NULL_NAMES));
	}

	public void scan() {
		manager.scanForPeripheralsWithServices(new UUID[] { SOVComm.SERVICE });
	}

	public final void connectToDiscovered() {
		sovComm = new SOVComm(shutdown -> {
			if (shutdown) closed = true;
			manager.cancelConnection(sovComm.peripheral());
		});
		sovComm.onServicesDiscovered = (bluetoothPeripheral, bluetoothGattServices) -> {
			connected(sovComm);
		};
		manager.connectPeripheral(discovered, sovComm.peripheralCallback);
	}

	public abstract void discovered(BluetoothPeripheral peripheral);

	public abstract void connected(SOVComm sovComm);

	public abstract void disconnected(SOVComm sovComm, BluetoothCommandStatus status);

	/**
	 * Only makes any future disconnection events not happen
	 */
	@Override
	public void close() {
		closed = true;
		manager.cancelConnection(sovComm.peripheral());
	}
}
