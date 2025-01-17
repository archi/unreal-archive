package net.shrimpworks.unreal.archive;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class ArchiveUtil {

	private static final Duration KILL_WAIT = Duration.ofSeconds(1);

	private static final Set<String> ARCHIVES = new HashSet<>(Arrays.asList(
			"zip", "z", "gz", "7z", "rar", "lzh", "exe"
	));

	private static final boolean IS_WINDOWS = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");

	private static final String NIX_SEVENZIP_CMD = "7z";
	private static final String NIX_UNRAR_CMD = "unrar";

	private static final String NIX_SEVENZIP_BIN = "/usr/bin/" + NIX_SEVENZIP_CMD;
	private static final String NIX_UNRAR_BIN = "/usr/bin/" + NIX_UNRAR_CMD;

	private static final Path PROGRAM_FILES = Paths.get(System.getenv().getOrDefault("ProgramFiles", "C:\\Program Files"));
	private static final Path WIN_SEVENZIP_BIN = PROGRAM_FILES.resolve("7-Zip").resolve("7z.exe");
	private static final Path WIN_UNRAR_BIN = PROGRAM_FILES.resolve("WinRAR").resolve("UnRAR.exe");

	// these will be populated at runtime and remembered after resolving OS-specific command paths
	private static String unrar = null;
	private static String sevenZip = null;

	public static boolean isArchive(Path path) {
		if (!Files.isRegularFile(path)) return false;
		return ARCHIVES.contains(Util.extension(path.toString().toLowerCase()));
	}

	public static Path extract(Path source, Path destination, Duration timeout)
			throws IOException, InterruptedException, BadArchiveException {
		return extract(source, destination, timeout, false, new HashSet<>());
	}

	public static Path extract(Path source, Path destination, Duration timeout, boolean recursive)
			throws IOException, InterruptedException, BadArchiveException {
		return extract(source, destination, timeout, recursive, new HashSet<>());
	}

	private static Path extract(Path source, Path destination, Duration timeout, boolean recursive, Set<Path> visited)
			throws IOException, InterruptedException, BadArchiveException {

		if (!Files.isDirectory(destination)) Files.createDirectories(destination);

		Path result;

		String ext = Util.extension(source).toLowerCase();
		switch (ext) {
			case "zip":
			case "7z":
			case "lzh":
			case "lza":
			case "exe":
				result = exec(sevenZipCmd(source, destination), source, destination, timeout);
				break;
			case "rar":
				result = exec(rarCmd(source, destination), source, destination, timeout);
				break;
			default:
				throw new UnsupportedArchiveException(String.format("Format %s not supported for archive %s", ext, source));
		}

		visited.add(source);

		if (recursive) {
			Set<Path> next = new HashSet<>();
			Files.walkFileTree(result, new SimpleFileVisitor<>() {
				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
					if (!visited.contains(file) && isArchive(file)) next.add(file);
					return FileVisitResult.CONTINUE;
				}
			});

			for (Path path : next) {
				try {
					if (!visited.contains(path)) extract(path, result.resolve(Util.plainName(path)), timeout, recursive, visited);
				} catch (Exception e) {
					// be lenient with recursive extraction...
				}
			}
		}

		return result;
	}

	public static void cleanPath(Path path) throws IOException {
		Files.walkFileTree(path, new SimpleFileVisitor<>() {
			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				Files.delete(file);
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
				Files.delete(dir);
				return FileVisitResult.CONTINUE;
			}
		});

		Files.deleteIfExists(path);
	}

	private static String sevenZipBin() {
		if (sevenZip != null) return sevenZip;

		if (IS_WINDOWS) {
			if (Files.exists(WIN_SEVENZIP_BIN)) {
				sevenZip = WIN_SEVENZIP_BIN.toString();
			} else {
				throw new RuntimeException("Could not find 7-Zip. Please install it.", new FileNotFoundException(WIN_SEVENZIP_BIN.toString()));
			}
		} else {
			// find 7z executable on *nix
			try {
				final Process which = new ProcessBuilder()
						.command("which", NIX_SEVENZIP_CMD)
						.start();
				final byte[] bytes = which.getInputStream().readAllBytes();
				sevenZip = new String(bytes, StandardCharsets.UTF_8).trim();
			} catch (Exception e) {
				sevenZip = NIX_SEVENZIP_BIN;
			}
		}

		return sevenZip;
	}

	private static String unrarBin() {
		if (unrar != null) return unrar;

		if (IS_WINDOWS) {
			if (Files.exists(WIN_UNRAR_BIN)) {
				unrar = WIN_UNRAR_BIN.toString();
			} else {
				throw new RuntimeException("Could not find WinRAR. Please install it.", new FileNotFoundException(WIN_UNRAR_BIN.toString()));
			}
		} else {
			// find unrar executable on *nix
			try {
				final Process which = new ProcessBuilder()
						.command("which", NIX_UNRAR_CMD)
						.start();
				final byte[] bytes = which.getInputStream().readAllBytes();
				unrar = new String(bytes, StandardCharsets.UTF_8).trim();
			} catch (Exception e) {
				unrar = NIX_UNRAR_BIN;
			}
		}

		return unrar;
	}

	private static String[] sevenZipCmd(Path source, Path destination) {
		return new String[] {
				sevenZipBin(),
				"x",                          // extract
				"-bd",                        // no progress
				"-y",                         // yes to all
				source.toString(),            // file to extract
				"-o" + destination.toString() // destination directory
		};
	}

	private static String[] rarCmd(Path source, Path destination) {
		return new String[] {
				unrarBin(),
				"e",                   // extract
				"-y",                  // yes to all
				source.toString(),     // file to extract
				destination.toString() // destination directory
		};
	}

	private static Path exec(String[] cmd, Path source, Path destination, Duration timeout)
			throws IOException, InterruptedException, BadArchiveException {

		Process process = new ProcessBuilder()
				.command(cmd)
				.directory(destination.toFile())
				.start();
		boolean b = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
		if (!b) {
			process.destroyForcibly().waitFor(KILL_WAIT.toMillis(), TimeUnit.MILLISECONDS);
			throw new IllegalStateException(String.format("Timed out unpacking file %s", source));
		}

		if (process.exitValue() != 0) {
			// cleanup
			cleanPath(destination);
			throw new BadArchiveException(String.format("File %s was not unpacked successfully", source));
		}

		return destination;
	}

	public static Path createZip(Path source, Path destination, Duration timeout)
			throws IOException, InterruptedException, IllegalStateException {

		if (!Files.isDirectory(source)) throw new IllegalArgumentException("Source is expected to be a directory");

		Process process = new ProcessBuilder()
				.command(
						sevenZipBin(),
						"a",                    // add to archive
						"-tzip",                // set zip archive type
						destination.toString(), // destination zip file
						source.toString()       // source directory
				)
				.directory(source.toFile())
				.start();
		boolean b = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
		if (!b) {
			process.destroyForcibly().waitFor(KILL_WAIT.toMillis(), TimeUnit.MILLISECONDS);
			throw new IllegalStateException(String.format("Timed out creating zip file %s", source));
		}

		if (process.exitValue() != 0) {
			throw new IllegalStateException(String.format("File %s was not zipped successfully", source));
		}

		return destination;
	}

	public static class BadArchiveException extends IOException {

		public BadArchiveException(String message) {
			super(message);
		}
	}

	public static class UnsupportedArchiveException extends UnsupportedOperationException {

		public UnsupportedArchiveException(String message) {
			super(message);
		}
	}
}
