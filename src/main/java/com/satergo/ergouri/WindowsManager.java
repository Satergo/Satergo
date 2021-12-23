package com.satergo.ergouri;

import com.sun.jna.platform.win32.Advapi32Util;
import com.sun.jna.platform.win32.Shell32;
import com.sun.jna.platform.win32.WinReg;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

class WindowsManager implements ErgoURIManager {
	@Override
	public void register() throws IOException {
		if (ErgoURIManager.getExecutablePath() == null) return;
		Path regFilePath = Path.of(System.getProperty("java.io.tmpdir")).resolve("satergo_ergo_uri_register.reg");
		Path absoluteExecutablePath = ErgoURIManager.getExecutablePath().toAbsolutePath();
		String pathDoubleBackslash = absoluteExecutablePath.toString().replace("\\", "\\\\");
		Files.writeString(regFilePath, """
				Windows Registry Editor Version 5.00
				
				[HKEY_CLASSES_ROOT\\ergo]
				@="URL:Ergo Protocol"
				"URL Protocol"=""
				
				[HKEY_CLASSES_ROOT\\ergo\\DefaultIcon]
				@="\\"{path}\\",1"
				
				[HKEY_CLASSES_ROOT\\ergo\\shell]
				
				[HKEY_CLASSES_ROOT\\ergo\\shell\\open]
				
				[HKEY_CLASSES_ROOT\\ergo\\shell\\open\\command]
				@="\\"{path}\\" \\"%1\\""
				""".replace("{path}", pathDoubleBackslash));
		Shell32.INSTANCE.ShellExecute(null, "runas", "reg", "IMPORT satergo_ergo_uri_register.reg", System.getProperty("java.io.tmpdir"), 0);
	}

	@Override
	public void unregister() throws IOException {
		Path regFilePath = Path.of(System.getProperty("java.io.tmpdir")).resolve("satergo_ergo_uri_unregister.reg");
		Files.writeString(regFilePath, """
				Windows Registry Editor Version 5.00
				
				-[HKEY_CLASSES_ROOT\\ergo\\shell\\open\\command]
				-[HKEY_CLASSES_ROOT\\ergo\\shell\\open]
				-[HKEY_CLASSES_ROOT\\ergo\\shell]
				-[HKEY_CLASSES_ROOT\\ergo\\DefaultIcon]
				-[HKEY_CLASSES_ROOT\\ergo]
				""");
		Shell32.INSTANCE.ShellExecute(null, "runas", "reg", "IMPORT satergo_ergo_uri_unregister.reg", System.getProperty("java.io.tmpdir"), 0);
	}

	@Override
	public boolean isRegistered() {
		return Advapi32Util.registryKeyExists(WinReg.HKEY_CLASSES_ROOT, "ergo");
	}
}
