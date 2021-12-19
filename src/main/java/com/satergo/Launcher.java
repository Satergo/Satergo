package com.satergo;

import com.satergo.ergouri.ErgoURIString;
import javafx.application.Application;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Using JavaFX portable runtime folders without modularity seems to require a separate class to launch the application.
 */
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
						To open an "ergo:" URI, use --uri and provide the URI after it.
						If the wallet is already open, it will be shown in that instance, otherwise a new instance with it will be started""");
				return;
			}
		}
		ipc = new IPC(Path.of(System.getProperty("java.io.tmpdir")).resolve("satergo-socket"));
		if (args.length == 2) {
			if (args[0].equals("--uri")) {
				if (ipc.exists()) {
					try {
						ipc.connectAndSend(1, args[1]);
					} catch (IOException e) {
						throw new RuntimeException(e);
					}
					return;
				} else {
					// open in this instance when ready
					Main.initErgoURI = ErgoURIString.parse(args[1]);
				}
			}
		}
		new Thread(() -> {
			try {
				ipc.listen();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}).start();
		Application.launch(Main.class, args);
	}
}
