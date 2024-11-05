package com.satergo.hw.ledger;

import com.satergo.jledger.LedgerDevice;
import org.hid4java.*;
import org.hid4java.event.HidServicesEvent;

import java.util.List;

public abstract class LedgerFinder {

	private final HidServices hidServices;
	private HidServicesListener servicesListener;
	private HidDevice device;

	/**
	 * Constructs a new LedgerFinder and loops through all already-connected devices
	 */
	public LedgerFinder() {
		HidServicesSpecification hidServicesSpecification = new HidServicesSpecification();
		hidServicesSpecification.setAutoStart(false);
		hidServices = HidManager.getHidServices(hidServicesSpecification);
		hidServices.start();
		rescanConnected();
	}

	public static String getModelName(int productId) {
		return LedgerDevice.PRODUCT_IDS.get(productId);
	}

	public final List<HidDevice> getAttachedHidDevices() {
		return hidServices.getAttachedHidDevices();
	}

	public void rescanConnected() {
		for (HidDevice attachedHidDevice : getAttachedHidDevices()) {
			if (handleDevice(attachedHidDevice))
				break;
		}
	}

	public final void startListener() {
		servicesListener = new HidServicesListener() {
			@Override public void hidDeviceAttached(HidServicesEvent event) {
				if (device == null)
					handleDevice(event.getHidDevice());
			}
			@Override public void hidDeviceDetached(HidServicesEvent event) {
				deviceDetached(event.getHidDevice());
			}

			public void hidDataReceived(HidServicesEvent event) {}
			@Override public void hidFailure(HidServicesEvent event) {}
		};
		hidServices.addHidServicesListener(servicesListener);
	}

	/**
	 * Stops scanning, which also means device detaches will not be noticed.
	 */
	public final void stopScanning() {
		hidServices.stop();
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
		if (hidDevice.getVendorId() == LedgerDevice.VENDOR_ID) {
			setDevice(hidDevice);
			deviceFound(hidDevice);
			return true;
		}
		return false;
	}

	public abstract void deviceFound(HidDevice hidDevice);
	public abstract void deviceDetached(HidDevice hidDevice);
}
