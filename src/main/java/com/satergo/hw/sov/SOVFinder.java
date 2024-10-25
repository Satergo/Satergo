package com.satergo.hw.sov;

import com.welie.blessed.BluetoothCentralManager;
import com.welie.blessed.BluetoothCentralManagerCallback;
import com.welie.blessed.BluetoothPeripheral;
import com.welie.blessed.ScanResult;

import java.util.Set;
import java.util.UUID;

@SuppressWarnings({"NullableProblems", "FieldCanBeLocal"})
public abstract class SOVFinder {

	private BluetoothPeripheral discovered;
	private final BluetoothCentralManager manager;
	private final BluetoothCentralManagerCallback managerCallback = new BluetoothCentralManagerCallback() {
		@Override
		public void onDiscoveredPeripheral(BluetoothPeripheral peripheral, ScanResult scanResult) {
			manager.stopScan();
			discovered = peripheral;
			discovered(peripheral);
		}
	};

	public SOVFinder() {
		manager = new BluetoothCentralManager(managerCallback, Set.of(BluetoothCentralManager.SCANOPTION_NO_NULL_NAMES));
	}

	public void scan() {
		manager.scanForPeripheralsWithServices(new UUID[] { SOVComm.SERVICE });
	}

	public final void connectToDiscovered() {
		SOVComm sovComm = new SOVComm();
		sovComm.onServicesDiscovered = (bluetoothPeripheral, bluetoothGattServices) -> {
			connected(sovComm);
		};
		manager.connectPeripheral(discovered, sovComm.peripheralCallback);
	}

	public abstract void discovered(BluetoothPeripheral peripheral);

	public abstract void connected(SOVComm sovComm);
}
