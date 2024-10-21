package com.satergo.build;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

public class WinInstallerBuildExt {

	public enum InstallerType {
		MSI
	}
	public InstallerType installerType;

	public String appName;
	public String appVersion;
	public Path icoIconPath;
	public String vendor;
	public String description;

	public boolean startMenuEntry = true;
	public boolean startMenuEntryInGroup = false;
	public String startMenuGroup = null;

	public boolean shortcut = true;
	public boolean shortcutPrompt = false;

	public String aboutUrl;
	public String updateUrl;

	public String upgradeUUID;

	public List<Path> fileAssociations = Collections.emptyList();

	public List<String> extraArgs = Collections.emptyList();

	public Path outputDir;
}
