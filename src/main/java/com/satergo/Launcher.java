package com.satergo;

import com.satergo.ergo.ErgoURI;
import com.satergo.ergopay.ErgoPayURI;
import javafx.application.Application;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;

public class Launcher {

	private static IPC ipc;

	public static IPC getIPC() {
		return ipc;
	}

	public static void main(String[] args) {
		if (args.length == 1) {
			if (args[0].equals("--help")) {
				System.out.println("""
						Satergo can be started by providing no arguments.
						To specify a wallet file to open, provide it as the first argument.
						To open an "ergo:" URI, use --uri and provide the URI after it.
						To handle an "ergopay:" URI, use --ergopay and provide the URI after it.
						If the wallet is already open, it will be shown in that instance, otherwise a new instance with it will be started""");
				return;
			}
		}
		ipc = new IPC(Path.of(System.getProperty("java.io.tmpdir")).resolve("satergo-socket"));
		LinkedHashMap<String, String> valuedArgs = new LinkedHashMap<>();
		ArrayList<String> freeArgs = new ArrayList<>();
		String key = null;
		for (String arg : args) {
			if (arg.equals("--uri") || arg.equals("--ergopay")) {
				key = arg;
				continue;
			}
			if (key != null) {
				valuedArgs.put(key, arg);
				key = null;
			} else {
				freeArgs.add(arg);
			}
		}
		if (valuedArgs.containsKey("--uri") && valuedArgs.containsKey("--ergopay"))
			throw new IllegalArgumentException("cannot have both --uri and --ergopay");
		if (freeArgs.size() > 1)
			throw new IllegalArgumentException("cannot have more than 1 free argument");
		if (valuedArgs.containsKey("--uri")) {
			String uri = valuedArgs.get("--uri");
			if (ipc.exists()) {
				try {
					ipc.connectAndSend(1, uri);
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
				return;
			} else {
				// open in this instance when ready
				Main.initErgoURI = ErgoURI.parse(URI.create(uri));
			}
		} else if (valuedArgs.containsKey("--ergopay")) {
			String uri = valuedArgs.get("--ergopay");
			if (ipc.exists()) {
				try {
					ipc.connectAndSend(2, uri);
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
				return;
			} else {
				// open in this instance when ready
				Main.initErgoPayURI = new ErgoPayURI(uri);
			}
		}
		if (!freeArgs.isEmpty())
			Main.initWalletFile = Path.of(freeArgs.getFirst());
		new Thread(() -> {
			try {
				ipc.listen();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}, "IPC thread").start();
		Application.launch(Main.class, args);
	}
}
