package com.satergo;

import com.google.gson.*;
import com.satergo.extra.DownloadTask;
import com.satergo.extra.dialog.SatPromptDialog;
import com.satergo.extra.dialog.SatVoidDialog;
import javafx.application.Platform;
import javafx.scene.control.*;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static com.satergo.SystemProperties.PackageType.INSTALLATION;
import static com.satergo.SystemProperties.PackageType.PORTABLE;

public class UpdateHandler {

	private static final URI LATEST_VERSION_INFO_URI = URI.create("https://satergo.com/latest.json");

	public record VersionInfo(String version, long versionCode, LocalDate dateReleased, String changelog, Downloads downloads) {
		public record Downloads(Map<String, Map<String, String>> installer, Map<String, Map<String, String>> portable) {}
	}

	public static VersionInfo fetchLatestInfo() throws IOException, InterruptedException {
		HttpRequest request = Utils.httpRequestBuilder().uri(LATEST_VERSION_INFO_URI).build();
		Gson gson = new GsonBuilder()
				.registerTypeAdapter(LocalDate.class, (JsonDeserializer<LocalDate>) (json, typeOfT, context) -> LocalDate.parse(json.getAsString()))
				.create();
		String body = Utils.HTTP.send(request, HttpResponse.BodyHandlers.ofString()).body();
		return gson.fromJson(body, VersionInfo.class);
	}

	public static boolean isNewer(long versionCode) {
		return Main.VERSION_CODE < versionCode;
	}

	public static void showUpdateDialog(VersionInfo versionInfo) {
		if (!isNewer(versionInfo.versionCode)) throw new IllegalArgumentException();
		if (Main.programData().skippedUpdate.get() == versionInfo.versionCode) return;
		SatPromptDialog<ButtonType> dialog = new SatPromptDialog<>();
		Utils.initDialog(dialog, Main.get().stage());
		ButtonType update = new ButtonType(Main.lang("update"), ButtonBar.ButtonData.YES);
		ButtonType skip = new ButtonType(Main.lang("skip"), ButtonBar.ButtonData.NO);
		ButtonType notNow = new ButtonType(Main.lang("notNow"), ButtonBar.ButtonData.CANCEL_CLOSE);
		dialog.getDialogPane().getButtonTypes().addAll(update, skip, notNow);
		dialog.setHeaderText(Main.lang("aNewUpdateHasBeenFound"));
		Label label = new Label(Main.lang("updateDescription").formatted(
				versionInfo.version,
				versionInfo.dateReleased.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)),
				versionInfo.changelog));
		label.setWrapText(true);
		dialog.getDialogPane().setContent(label);
		dialog.showForResult().ifPresent(t -> {
			if (t == update) {
				if (SystemProperties.nullablePackageType() == null) {
					openDownloadsWebpage();
					return;
				}
				String plat;
				if (Utils.isWindows()) plat = "win";
				else if (Utils.isLinux()) plat = "linux";
				else if (Utils.isMac()) plat = "mac";
				else {
					openDownloadsWebpage();
					return;
				}
				String arch = System.getProperty("os.arch");
				SystemProperties.PackageType pkgType = SystemProperties.packageType();
				if (Utils.isWindows() && pkgType == INSTALLATION
						&& versionInfo.downloads.installer.containsKey(plat) && versionInfo.downloads.installer.get(plat).containsKey(arch)) {
					installWindowsUpdate(URI.create(versionInfo.downloads.installer.get(plat).get(arch)));
				} else if ((Utils.isLinux() || Utils.isMac()) && pkgType == PORTABLE
						&& versionInfo.downloads.portable.containsKey(plat) && versionInfo.downloads.portable.get(plat).containsKey(arch)) {
					installPortableLinuxMacUpdate(URI.create(versionInfo.downloads.portable.get(plat).get(arch)));
				} else {
					openDownloadsWebpage();
				}
			} else if (t == skip) {
				Main.programData().skippedUpdate.set(versionInfo.versionCode);
			}
		});
	}

	private static void openDownloadsWebpage() {
		Utils.showDocument("https://satergo.com/#downloads");
	}

	/**
	 * Downloads the specified version, closes this one, and installs that one
	 */
	public static void installWindowsUpdate(URI installerUri) {
		if (!Utils.isWindows() || SystemProperties.packageType() != INSTALLATION)
			throw new IllegalStateException("This method is only for Windows installations.");
		try {
			// Not using getProperty("java.io.tmpdir") because %TEMP% will be used in the script anyways
			Path tempDir = Path.of(System.getenv("TEMP"));
			String msiRandom = String.valueOf(ThreadLocalRandom.current().nextInt(0, Integer.MAX_VALUE));
			Path msiPath = tempDir.resolve("SatergoUpdate" + msiRandom + ".msi");
			DownloadTask downloadTask = download(installerUri, msiPath);
			downloadTask.setOnSucceeded(event -> {
				SatVoidDialog installDialog = new SatVoidDialog();
				Utils.initDialog(installDialog, Main.get().stage());
				installDialog.setHeaderText(Main.lang("closingAndInstalling..."));
				installDialog.show();
				// Add some delay so that the user can see the "Closing and installing" dialog
				Utils.fxRunDelayed(() -> {
					try {
						Path scriptPath = tempDir.resolve("satergoUpdateScript" + ThreadLocalRandom.current().nextInt(0, Integer.MAX_VALUE) + ".bat");
						// Installs the MSI, if the installation was successful it deletes the installer and starts the application.
						// If the installation failed, it deletes the installer. There is code duplication to
						// prevent the deletion from affecting the errorlevel.
						// Finally, it deletes the script itself.
						Files.writeString(scriptPath, """
								@echo off
								sleep 1
								msiexec /i "%TEMP%\\SatergoUpdate{random}.msi" /passive /norestart
								IF "%errorlevel%" == "0" (
									del "%TEMP%\\SatergoUpdate{random}.msi"
									start "" "%LocalAppData%\\Satergo\\Satergo.exe"
								) ELSE (
									del "%TEMP%\\SatergoUpdate{random}.msi"
								)
								(goto) 2>nul & del "%~f0"
								""".replace("{random}", msiRandom));
						installDialog.close();
						// Starts a cmd that runs the cmd start function, which starts a new cmd in a new process
						// This is done to prevent shutting down the script when closing the application
						new ProcessBuilder("cmd", "/c", "start", "/min", "", "cmd", "/c", scriptPath.toAbsolutePath().toString()).start();
						Platform.exit();
					} catch (IOException e) {
						throw new RuntimeException(e);
					}
				}, 400);
			});
			new Thread(downloadTask).start();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public static void installPortableLinuxMacUpdate(URI uri) {
		Path tempDir = Path.of(System.getProperty("java.io.tmpdir"));
		String random = String.valueOf(ThreadLocalRandom.current().nextInt(0, Integer.MAX_VALUE));
		Path zipPath = tempDir.resolve("SatergoUpdate" + random + ".zip");
		DownloadTask download;
		try {
			download = download(uri, zipPath);
		} catch (IOException e) {
			Utils.alertUnexpectedException(e);
			return;
		}
		download.setOnSucceeded(event -> {
			Path appHome = SystemProperties.appHome();
			List<Path> dirs = List.of(appHome.resolve("lib"), appHome.resolve("bin"), appHome.resolve("conf"));
			Pattern pattern = Pattern.compile("(?i)\\.erg");
			for (Path dir : dirs) {
				try (Stream<Path> stream = Files.walk(dir)) {
					if (stream.anyMatch(p -> pattern.matcher(p.toString()).find())) {
						Utils.alert(Alert.AlertType.ERROR, Main.lang("cannotUpdateBecauseThereIsAWalletFile"));
						return;
					}
				} catch (IOException e) {
					Utils.alertUnexpectedException(e);
					return;
				}
			}

			SatVoidDialog alert = new SatVoidDialog();
			Utils.initDialog(alert, Main.get().stage());
			alert.setTitle(Main.lang("programName"));
			alert.setHeaderText(Main.lang("updateHasBeenDownloaded"));
			alert.getDialogPane().setContent(new Label(Main.lang("portableUpdateInstallationInfo")));
			alert.getDialogPane().getButtonTypes().add(ButtonType.OK);
			alert.showAndWait();

			// Cannot use ZipFile here because it provides no way of accessing file permissions
			Path tempExtractDir = appHome.resolve("updateExtraction" + ThreadLocalRandom.current().nextInt(0, Integer.MAX_VALUE));
			try (FileSystem zipFile = FileSystems.newFileSystem(zipPath, Map.of("enablePosixFileAttributes", true))) {
				Files.createDirectory(tempExtractDir);
				copyZipContent(zipFile.getPath("/"), tempExtractDir);
			} catch (IOException e) {
				Utils.alertUnexpectedException(e);
			}
			try {
				Files.delete(zipPath);

				try {
					for (Path dir : dirs) {
						deleteDir(dir);
					}
					Files.deleteIfExists(appHome.resolve("run.sh"));
					Files.deleteIfExists(appHome.resolve("run.command"));
					try (Stream<Path> extractedDirs = Files.list(tempExtractDir)) {
						for (Path path : extractedDirs.toList()) {
							Files.move(path, appHome.resolve(path.getFileName()));
						}
					}
					Platform.exit();
				} catch (IOException e) {
					Utils.alertUnexpectedException(e);
				}
			} catch (IOException e) {
				Utils.alertUnexpectedException(e);
			}
		});
	}

	private static void deleteDir(Path dir) throws IOException {
		Files.walkFileTree(dir, new SimpleFileVisitor<>() {
			@Override
			public FileVisitResult visitFile(Path path, BasicFileAttributes basicFileAttributes) throws IOException {
				Files.delete(path);
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult postVisitDirectory(Path directory, IOException ex) throws IOException {
				Files.delete(directory);
				return FileVisitResult.CONTINUE;
			}
		});
	}

	private static void copyZipContent(Path dir, Path into) throws IOException {
		Files.walkFileTree(dir, new SimpleFileVisitor<>() {
			@Override
			public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
				Files.createDirectories(dir);
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				Path pathInZip = dir.relativize(file);
				Path to = into.resolve(pathInZip.toString()).normalize();
				if (to.startsWith(into)) {
					Files.createDirectories(to.getParent());
					Files.copy(file, to, StandardCopyOption.COPY_ATTRIBUTES);
				}
				return FileVisitResult.CONTINUE;
			}
		});
	}

	private static DownloadTask download(URI uri, Path path) throws IOException {
		// Not using getProperty("java.io.tmpdir") because %TEMP% will be used in the script anyways
		SatVoidDialog dialog = new SatVoidDialog();
		Utils.initDialog(dialog, Main.get().stage());
		DownloadTask downloadTask = new DownloadTask(
				HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build(),
				Utils.httpRequestBuilder().uri(uri).build(),
				Files.newOutputStream(path)) {
			@Override
			protected void succeeded() {
				dialog.close();
			}
		};
		dialog.setHeaderText(Main.lang("downloading..."));
		ProgressBar progressBar = new ProgressBar();
		dialog.getDialogPane().setContent(progressBar);
		progressBar.progressProperty().bind(downloadTask.progressProperty());
		dialog.show();
		new Thread(downloadTask).start();
		return downloadTask;
	}
}
