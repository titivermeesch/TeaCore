package me.playbosswar.tea.core.api.platform;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The Minecraft version the server runs, e.g. 1.8.8 or 1.21.4.
 *
 * @param major always 1 so far
 * @param minor 8 for 1.8.8, 21 for 1.21.4
 * @param patch 8 for 1.8.8, 4 for 1.21.4
 */
public record ServerVersion(int major, int minor, int patch) implements Comparable<ServerVersion> {

    private static final Pattern VERSION = Pattern.compile("(\\d+)\\.(\\d+)(?:\\.(\\d+))?");

    /** Parses a Bukkit version string such as {@code 1.8.8-R0.1-SNAPSHOT}. */
    public static ServerVersion parse(String bukkitVersion) {
        Matcher m = VERSION.matcher(bukkitVersion);
        if (!m.find()) {
            throw new IllegalArgumentException("Not a version: " + bukkitVersion);
        }
        return new ServerVersion(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)),
                m.group(3) == null ? 0 : Integer.parseInt(m.group(3)));
    }

    public boolean atLeast(int minor, int patch) {
        return this.minor > minor || (this.minor == minor && this.patch >= patch);
    }

    public boolean atLeast(int minor) {
        return atLeast(minor, 0);
    }

    /** Before the 1.13 "flattening": numeric block data, no namespaced keys. */
    public boolean legacy() {
        return !atLeast(13);
    }

    /**
     * 1.13 to 1.16.4 can't load Java 17 classes (their plugin class rewriter is too old),
     * so Tea supports 1.8.8 to 1.12.2 and 1.16.5 onwards.
     */
    public boolean supported() {
        return legacy() || atLeast(16, 5);
    }

    @Override
    public int compareTo(ServerVersion o) {
        int c = Integer.compare(major, o.major);
        if (c == 0) c = Integer.compare(minor, o.minor);
        return c != 0 ? c : Integer.compare(patch, o.patch);
    }

    @Override
    public String toString() {
        return major + "." + minor + (patch > 0 ? "." + patch : "");
    }
}
