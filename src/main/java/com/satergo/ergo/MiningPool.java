package com.satergo.ergo;

import com.satergo.Main;
import com.satergo.Utils;
import org.ergoplatform.appkit.Address;
import scorex.util.encode.Base58;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Properties;

public class MiningPool implements ExternalProgram {

	private final File nodeDirectory;
	private final Path jar;
	public final MiningPoolInfo info;
	private Process process;
	private long startedTime;

	public MiningPool(File nodeDirectory, MiningPoolInfo info) {
		this.nodeDirectory = nodeDirectory;
		this.jar = nodeDirectory.toPath().resolve(info.jarFileName());
		this.info = info;
	}

	@Override
	public void start() throws IOException {
		if (isRunning()) throw new IllegalStateException("this pool is already running");
		String s = info.address().toErgoContract().getErgoTree().bytesHex();
//		System.out.println(HexFormat.of().formatHex());
//		System.out.println(HexFormat.of().formatHex(info.address().asP2PK().pubkeyBytes()));
		System.out.println("s = " + s);
		if (!Arrays.equals(info.address().asP2PK().contentBytes(), Main.node.getConfValue("ergo.node.miningPubKeyHex").map(o -> HexFormat.of().parseHex((String) o)).orElse(null))) {
			throw new IllegalStateException("The node does not have the payout address configured");
		}
		Properties properties = new Properties();
		properties.setProperty("extraNonce1Size", "4");
		properties.setProperty("difficultyMultiplier", "256");
		properties.setProperty("connectionTimeout", "60000");
		properties.setProperty("blockRefreshInterval", "1000");
		properties.setProperty("nodeApiUrl", Main.node.localApiHttpAddress() + "/");
		properties.setProperty("port", String.valueOf(info.port()));
		Path propPath = nodeDirectory.toPath().resolve("mining-pool.properties");
		properties.store(Files.newBufferedWriter(propPath), null);

		String[] command = new String[4];
		command[0] = ExternalProgram.findJavaBinary().toString();
		command[1] = "-jar";
		command[2] = jar.toAbsolutePath().toString();
		command[3] = propPath.toAbsolutePath().toString();
		System.out.println("running pool with command: " + Arrays.toString(command));
		process = new ProcessBuilder().command(command).directory(Utils.settingsDir().toFile()).inheritIO().start();
		startedTime = System.currentTimeMillis();
	}

	@Override
	public InputStream standardOutput() {
		return process.getInputStream();
	}

	@Override
	public boolean isRunning() {
		return process != null && process.isAlive();
	}

	@Override
	public void stop() {
		process.destroy();
	}

	@Override
	public void waitForExit() {
		process.onExit().join();
	}

	@Override
	public long startedTime() {
		return startedTime;
	}
}
