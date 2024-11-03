package com.satergo.hw.svault;

import com.welie.blessed.*;

import java.util.Set;
import java.util.UUID;

@SuppressWarnings({"NullableProblems", "FieldCanBeLocal"})
public abstract class SVaultFinder {

	private boolean closed = false;

	private SVaultComm svaultComm;
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
				disconnected(svaultComm, status);
		}
	};

	public SVaultFinder() {
		manager = new BluetoothCentralManager(managerCallback, Set.of(BluetoothCentralManager.SCANOPTION_NO_NULL_NAMES));
	}

	public void scan() {
		manager.scanForPeripheralsWithServices(new UUID[] { SVaultComm.SERVICE });
	}

	public final void connectToDiscovered() {
		svaultComm = new SVaultComm(shutdown -> {
			if (shutdown) closed = true;
			manager.cancelConnection(svaultComm.peripheral());
		});
		svaultComm.onServicesDiscovered = (bluetoothPeripheral, bluetoothGattServices) -> {
			connected(svaultComm);
		};
		manager.connectPeripheral(discovered, svaultComm.peripheralCallback);
	}

	public abstract void discovered(BluetoothPeripheral peripheral);

	public abstract void connected(SVaultComm svaultComm);

	public abstract void disconnected(SVaultComm svaultComm, BluetoothCommandStatus status);

	/**
	 * Only makes any future disconnection events not happen
	 */
	public void close() {
		closed = true;
		manager.cancelConnection(svaultComm.peripheral());
	}
}
