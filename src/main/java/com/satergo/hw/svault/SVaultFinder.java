package com.satergo.hw.svault;

import com.satergo.Utils;
import com.satergo.extra.dialog.SatTextInputDialog;
import com.welie.blessed.*;
import javafx.application.Platform;
import javafx.scene.control.Alert;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;

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

			@Override
			public String onPinRequest(BluetoothPeripheral peripheral) {
				CompletableFuture<String> pinFuture = new CompletableFuture<>();
				Platform.runLater(() -> {
					SatTextInputDialog dialog = new SatTextInputDialog();
					dialog.setHeaderText("Enter PIN code");
					String pin = dialog.showForResult().orElse(null);
					if (pin == null) {
						Utils.alert(Alert.AlertType.ERROR, "Pin code required, cancelling connection.");
						pinFuture.complete("");
					} else {
						pinFuture.complete(pin);
					}
				});
				try {
					return pinFuture.get();
				} catch (InterruptedException | ExecutionException e) {
					throw new RuntimeException(e);
				}
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
			ready(svaultComm);
		};
		manager.connectPeripheral(discovered, svaultComm.peripheralCallback);
	}

	public abstract void discovered(BluetoothPeripheral peripheral);

	public abstract void ready(SVaultComm svaultComm);

	public abstract void disconnected(SVaultComm svaultComm, BluetoothCommandStatus status);

	/**
	 * Disconnects from the peripheral and shuts down the manager
	 */
	public void close() {
		closed = true;
		manager.cancelConnection(svaultComm.peripheral());
	}
}