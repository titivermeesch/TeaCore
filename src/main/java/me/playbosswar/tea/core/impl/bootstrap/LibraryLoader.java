package me.playbosswar.tea.core.impl.bootstrap;

import com.alessiodp.libby.BukkitLibraryManager;
import com.alessiodp.libby.Library;
import com.alessiodp.libby.Repositories;
import com.alessiodp.libby.logging.LogLevel;
import com.alessiodp.libby.relocation.Relocation;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import me.playbosswar.tea.core.impl.config.CoreConfig;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Downloads the large libraries listed in {@code teacore-libraries.txt} (written by the build) into
 * {@code plugins/TeaCore/libs/}, checks their SHA-256, relocates them and adds them to the plugin class loader.
 * Must run before any TeaCore class that uses them is loaded.
 */
public final class LibraryLoader {

    private static final String MANIFEST = "teacore-libraries.txt";
    private static final String DIRECTORY = "libs";
    // Libby downloads these to relocate the other jars. Keep in sync with Libby's RelocationHelper.
    private static final List<String> RELOCATOR_PATHS = List.of(
            "org/ow2/asm/asm-commons/9.10.1/asm-commons-9.10.1.jar",
            "org/ow2/asm/asm/9.10.1/asm-9.10.1.jar",
            "me/lucko/jar-relocator/1.7/jar-relocator-1.7.jar");

    private LibraryLoader() {
    }

    /** The library groups the given configuration needs. */
    public static Set<String> features(CoreConfig config) {
        Set<String> features = new LinkedHashSet<>();
        features.add("core");
        if (config.network().mode() != CoreConfig.NetworkMode.STANDALONE) {
            features.add("redis");
        }
        if (config.dashboard().enabled()) {
            features.add("dashboard");
        }
        if (config.database().type() == CoreConfig.DatabaseType.MYSQL) {
            features.add("mysql");
        }
        return features;
    }

    /** Returns false, after logging what to do, when a library could not be loaded. */
    public static boolean load(JavaPlugin plugin, Set<String> features) {
        Logger logger = plugin.getLogger();
        Path dir = plugin.getDataFolder().toPath().resolve(DIRECTORY);
        List<Library> libraries;
        try {
            libraries = readManifest(plugin, features);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Could not read " + MANIFEST + " from the TeaCore jar", e);
            return false;
        }

        BukkitLibraryManager manager = new BukkitLibraryManager(plugin, DIRECTORY);
        manager.setLogLevel(LogLevel.WARN);
        manager.addMavenCentral();

        int toDownload = 0;
        for (Library library : libraries) {
            Path file = dir.resolve(library.getPath());
            if (Files.exists(file) && !matchesChecksum(file, library.getChecksum())) {
                logger.warning("Checksum mismatch for " + file + ", downloading it again.");
                deleteQuietly(file);
                deleteQuietly(dir.resolve(library.getRelocatedPath()));
            }
            if (!Files.exists(file)) {
                toDownload++;
            }
        }
        if (toDownload > 0) {
            logger.info("Downloading " + toDownload + " library file(s) from Maven Central into " + dir + ".");
        }

        List<Library> failed = new ArrayList<>();
        for (Library library : libraries) {
            try {
                manager.loadLibrary(library);
            } catch (RuntimeException e) {
                logger.log(Level.FINE, "Loading " + library + " failed", e);
                failed.add(library);
            }
        }
        if (failed.isEmpty()) {
            return true;
        }
        logFailure(logger, dir, libraries);
        return false;
    }

    private static void logFailure(Logger logger, Path dir, List<Library> libraries) {
        List<String> missing = new ArrayList<>();
        boolean needsRelocation = false;
        for (Library library : libraries) {
            if (!Files.exists(dir.resolve(library.getPath()))) {
                missing.add(library.getPath());
            }
            if (!Files.exists(dir.resolve(library.getRelocatedPath()))) {
                needsRelocation = true;
            }
        }
        if (needsRelocation) {
            for (String path : RELOCATOR_PATHS) {
                if (!Files.exists(dir.resolve(path))) {
                    missing.add(path);
                }
            }
        }
        StringBuilder message = new StringBuilder()
                .append("TeaCore could not download the libraries it needs. It downloads them from Maven Central")
                .append(" on first start, so the server needs internet access once.\n")
                .append("Without internet, download these files yourself and put each one at the path shown,")
                .append(" then restart the server:\n");
        for (String path : missing) {
            message.append("  ").append(Repositories.MAVEN_CENTRAL).append(path).append('\n')
                    .append("    -> ").append(dir.resolve(path)).append('\n');
        }
        message.append("Disabling TeaCore.");
        logger.severe(message.toString());
    }

    private static List<Library> readManifest(JavaPlugin plugin, Set<String> features) throws IOException {
        List<Relocation> relocations = new ArrayList<>();
        List<String[]> entries = new ArrayList<>();
        try (InputStream in = plugin.getResource(MANIFEST)) {
            if (in == null) {
                throw new IOException(MANIFEST + " is missing");
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.trim().split(" ");
                if (parts.length == 3 && parts[0].equals("relocate")) {
                    relocations.add(new Relocation(parts[1], parts[2]));
                } else if (parts.length == 4 && parts[0].equals("library")) {
                    entries.add(parts);
                }
            }
        }
        List<Library> libraries = new ArrayList<>();
        for (String[] entry : entries) {
            if (Arrays.stream(entry[1].split(",")).noneMatch(features::contains)) {
                continue;
            }
            String[] coordinates = entry[2].split(":");
            Library.Builder builder = Library.builder()
                    .groupId(coordinates[0])
                    .artifactId(coordinates[1])
                    .version(coordinates[2])
                    .classifier(coordinates.length > 3 ? coordinates[3] : null)
                    .checksum(entry[3]);
            relocations.forEach(builder::relocate);
            libraries.add(builder.build());
        }
        return libraries;
    }

    private static boolean matchesChecksum(Path file, byte[] expected) {
        try {
            byte[] actual = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file));
            return Arrays.equals(actual, expected);
        } catch (IOException | NoSuchAlgorithmException e) {
            return false;
        }
    }

    private static void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
        }
    }
}
