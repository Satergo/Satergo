package com.satergo.build;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.function.Function;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

class FileUtils {
	static void deleteDirectory(Path directory) throws IOException {
		for (Iterator<Path> iterator = Files.list(directory).iterator(); iterator.hasNext();) {
			Path next = iterator.next();
			if (Files.isDirectory(next)) deleteDirectory(next);
			else Files.delete(next);
		}
		Files.delete(directory);
	}

	static void zipContent(Path sourceDirectory, Path output) throws IOException {
		try (ZipOutputStream zs = new ZipOutputStream(Files.newOutputStream(output))) {
			for (Iterator<Path> iterator = Files.walk(sourceDirectory)
					.filter(path -> !Files.isDirectory(path)).iterator(); iterator.hasNext();) {
				Path path = iterator.next();
				ZipEntry zipEntry = new ZipEntry(sourceDirectory.relativize(path).toString());
				try {
					zs.putNextEntry(zipEntry);
					Files.copy(path, zs);
					zs.closeEntry();
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			}
		}
	}

	private static void extractArchiveTo(ArchiveInputStream ais, Path outputDirectory, Function<String, String> nameRewrite) throws IOException {
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
			}
			archiveEntry = ais.getNextEntry();
		}
		ais.close();
	}

	public enum ArchiveType {
		ZIP, TAR_GZ
	}

	static void extractZipTo(InputStream inputStream, Path outputDirectory, Function<String, String> nameRewrite) throws IOException {
		extractArchiveTo(new ZipArchiveInputStream(inputStream), outputDirectory, nameRewrite);
	}

	static void extractTarGzTo(InputStream inputStream, Path outputDirectory, Function<String, String> nameRewrite) throws IOException {
		extractArchiveTo(new TarArchiveInputStream(new GZIPInputStream(inputStream)), outputDirectory, nameRewrite);
	}
}
