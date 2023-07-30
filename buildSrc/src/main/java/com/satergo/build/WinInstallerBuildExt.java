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

	public String aboutUrl;
	public String updateUrl;

	public String windowsUpgradeUUID;

	public List<Path> fileAssociations = Collections.emptyList();

	public List<String> extraArgs = Collections.emptyList();

	public Path outputDir;
}
