package com.satergo.hw.svault;

import com.welie.blessed.*;

import java.util.Set;
import java.util.UUID;

@SuppressWarnings("NullableProblems")
public abstract class SVaultFinder {

	private boolean closed = false;

	private SVaultComm svaultComm;
	private BluetoothPeripheral discovered;
	private final BluetoothCentralManager manager;

	public SVaultFinder() {
		BluetoothCentralManagerCallback managerCallback = new BluetoothCentralManagerCallback() {
			@Override
			public void onDiscoveredPeripheral(BluetoothPeripheral peripheral, ScanResult scanResult) {
				manager.stopScan();
				discovered = peripheral;
				discovered(peripheral);
			}

			@Override
			public void onDisconnectedPeripheral(BluetoothPeripheral peripheral, BluetoothCommandStatus status) {
				if (closed)
					manager.shutdown();
				else
					disconnected(svaultComm, status);
			}
		};
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
	 * Disconnects from the peripheral and shuts down the manager
	 */
	public void close() {
		closed = true;
		manager.cancelConnection(svaultComm.peripheral());
	}
}
