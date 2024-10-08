package com.satergo.build;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.tools.ant.util.PermissionUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.*;
import java.time.Instant;
import java.util.Iterator;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

class FileUtils {

	static void deleteDirectory(Path directory) throws IOException {
		try (Stream<Path> pathStream = Files.list(directory)) {
			for (Iterator<Path> iterator = pathStream.iterator(); iterator.hasNext(); ) {
				Path next = iterator.next();
				if (Files.isDirectory(next)) deleteDirectory(next);
				else Files.delete(next);
			}
			Files.delete(directory);
		}
	}

	/**
	 * @param sourceDirectory The source directory to compress. The contents of the directory will be compressed.
	 * @param output The path to write the compressed archive to.
	 * @param preserveTimestamps Whether file timestamps should be kept.
	 * @param reproducibleFileOrder Whether two invocations of this method should produce the same file order in the zip.
	 */
	static void zipContent(Path sourceDirectory, Path output, boolean preserveTimestamps, boolean reproducibleFileOrder) throws IOException {
		try (ZipArchiveOutputStream zs = new ZipArchiveOutputStream(output)) {
			FileVisitor<Path> fileVisitor = new FileVisitor<>() {
				@Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) throws IOException {
					ZipArchiveEntry zipEntry = new ZipArchiveEntry(path, sourceDirectory.relativize(path).toString());
					if (!preserveTimestamps) {
						zipEntry.setCreationTime(FileTime.from(Instant.EPOCH));
						zipEntry.setLastModifiedTime(FileTime.from(Instant.EPOCH));
						zipEntry.setLastAccessTime(FileTime.from(Instant.EPOCH));
					}
					zipEntry.setUnixMode(PermissionUtils.modeFromPermissions(Files.getPosixFilePermissions(path), PermissionUtils.FileType.of(path)));
					zs.putArchiveEntry(zipEntry);
					Files.copy(path, zs);
					zs.closeArchiveEntry();
					return FileVisitResult.CONTINUE;
				}

				@Override public FileVisitResult visitFileFailed(Path file, IOException e) throws IOException {
					throw e;
				}

				@Override public FileVisitResult postVisitDirectory(Path dir, IOException e) throws IOException {
					return FileVisitResult.CONTINUE;
				}
			};
			if (reproducibleFileOrder) {
				ReproducibleDirWalker.visit(sourceDirectory, fileVisitor);
			} else {
				Files.walkFileTree(sourceDirectory, fileVisitor);
			}
		}
	}

	private static void extractArchiveTo(ArchiveInputStream<?> ais, Path outputDirectory, Function<String, String> nameRewrite) throws IOException {
		ArchiveEntry archiveEntry = ais.getNextEntry();
		while (archiveEntry != null) {
			String name = nameRewrite.apply(archiveEntry.getName());
			if (name == null || name.isBlank()) {
				archiveEntry = ais.getNextEntry();
				continue;
			}
			Path newFile = outputDirectory.resolve(name);
			if (!newFile.normalize().startsWith(outputDirectory.normalize()))
				throw new IllegalArgumentException("malicious archive tried to escape output directory");
			if (archiveEntry.isDirectory()) {
				if (!Files.isDirectory(newFile)) {
					try {
						Files.createDirectories(newFile);
					} catch (IOException e) {
						throw new IOException("Failed to create directory " + newFile, e);
					}
				}
			} else {
				// fix for Windows-created archives
				Path parent = newFile.getParent();
				if (!Files.isDirectory(parent)) {
					try {
						Files.createDirectories(parent);
					} catch (IOException e) {
						throw new IOException("Failed to create directory " + parent, e);
					}
				}
				Files.copy(ais, newFile);
				// unix mode is incorrectly always 0 for ZipArchiveInputStream entries,
				// but since the OpenJDK archives only use zip for Windows and it doesn't
				// care about file permissions, it is not important
				if (archiveEntry instanceof TarArchiveEntry t) {
					Files.setPosixFilePermissions(newFile, PermissionUtils.permissionsFromMode(t.getMode()));
				}
			}
			archiveEntry = ais.getNextEntry();
		}
		ais.close();
	}

	enum ArchiveType {
		ZIP, TAR_GZ
	}

	static void extractZipTo(InputStream inputStream, Path outputDirectory, Function<String, String> nameRewrite) throws IOException {
		extractArchiveTo(new ZipArchiveInputStream(inputStream), outputDirectory, nameRewrite);
	}

	static void extractTarGzTo(InputStream inputStream, Path outputDirectory, Function<String, String> nameRewrite) throws IOException {
		extractArchiveTo(new TarArchiveInputStream(new GZIPInputStream(inputStream)), outputDirectory, nameRewrite);
	}

	static void downloadJdk(String archiveName, URI uri, String root, Path out) throws IOException, InterruptedException {
		HttpResponse<InputStream> request = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build()
				.send(HttpRequest.newBuilder().uri(uri).build(), HttpResponse.BodyHandlers.ofInputStream());
		ArchiveType archiveType;
		if (archiveName.endsWith(".zip")) archiveType = ArchiveType.ZIP;
		else if (archiveName.endsWith(".tar.gz")) archiveType = ArchiveType.TAR_GZ;
		else throw new IllegalArgumentException("unsupported archive type");
		Files.createDirectory(out);
		Function<String, String> pathRewriter = name -> {
			// appears in linux & mac archives
			if (name.startsWith("./")) name = name.substring(2);
			// skip top directory
			name = name.substring(name.indexOf('/') + 1);
			// skip path to root
			if (name.startsWith(root))
				return name.substring(root.length());
			else return null;
		};
		switch (archiveType) {
			case ZIP -> FileUtils.extractZipTo(request.body(), out, pathRewriter);
			case TAR_GZ -> FileUtils.extractTarGzTo(request.body(), out, pathRewriter);
		}
	}
}
