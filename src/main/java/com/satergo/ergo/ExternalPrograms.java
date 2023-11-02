package com.satergo.ergo;

public class ExternalPrograms {

	public MiningPool miningPool;

	public void shutdownAll(boolean wait) {
		if (miningPool != null && miningPool.isRunning()) {
			miningPool.stop();
			if (wait) miningPool.waitForExit();
		}
	}
}
