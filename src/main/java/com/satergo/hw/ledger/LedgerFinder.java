package com.satergo.hw.ledger;

import com.satergo.hidapi4j.HidApi;
import com.satergo.hidapi4j.HidDevice;
import com.satergo.hidapi4j.HidScanner;
import com.satergo.jledger.LedgerDevice;

public abstract class LedgerFinder {

	private final HidScanner hidScanner;
	private HidScanner.Listener listener;
	private HidDevice device;

	/**
	 * Constructs a new LedgerFinder and loops through all already-connected devices
	 */
	public LedgerFinder() {
		HidApi.loadLibrary();
		hidScanner = new HidScanner();
		hidScanner.start();
		rescanConnected();
	}

	public static String getModelName(int productId) {
		return LedgerDevice.PRODUCT_IDS.get(productId);
	}

	public void rescanConnected() {
		for (HidDevice attachedHidDevice : HidApi.getAllDevices()) {
			if (handleDevice(attachedHidDevice))
				break;
		}
	}

	public final void startListener() {
		listener = new HidScanner.Listener() {
			@Override
			public void hidDeviceAttached(HidDevice device) {
				if (LedgerFinder.this.device == null)
					handleDevice(device);
			}

			@Override
			public void hidDeviceDetached(HidDevice device) {
				deviceDetached(device);
			}
		};
		hidScanner.addListener(listener);
	}

	/**
	 * Stops scanning, which also means device detaches will not be noticed.
	 */
	public final void stopScanning() {
		hidScanner.stop();
	}

	public final void setDevice(HidDevice device) {
		this.device = device;
	}

	public final HidDevice getDevice() {
		return device;
	}

	/**
	 * @implNote Should call {@link #setDevice(HidDevice)) if it returns true
	 * @return This is a Ledger device, stop calling this method
	 */
	protected boolean handleDevice(HidDevice hidDevice) {
		if (hidDevice.info().vendorId() == LedgerDevice.VENDOR_ID) {
			setDevice(hidDevice);
			deviceFound(hidDevice);
			return true;
		}
		return false;
	}

	public abstract void deviceFound(HidDevice hidDevice);
	public abstract void deviceDetached(HidDevice hidDevice);
}
