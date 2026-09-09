package ps2gm.game.manager;

import java.util.ArrayList;
import java.util.List;

/**
 * A dotted version string ("1.0", "0.6.1", "2.0.0-beta"), parsed for
 * numeric comparison instead of the plain string equality the update check
 * used before. A missing/malformed segment reads as 0 rather than throwing -
 * a garbled version string from the server should never crash the update
 * check, just compare oddly.
 *
 * An optional "-suffix" (pre-release tag, e.g. "-beta"/"-test") sorts below
 * the same numeric version without one, matching normal semver precedence
 * (1.0.0-beta &lt; 1.0.0); with two suffixed versions, the suffix breaks the
 * tie lexicographically.
 */
public final class AppVersion implements Comparable<AppVersion> {

    private final List<Integer> segments;
    private final String suffix; // null if none

    private AppVersion(List<Integer> segments, String suffix) {
        this.segments = segments;
        this.suffix = suffix;
    }

    public static AppVersion parse(String raw) {
        if (raw == null) {
            raw = "";
        }
        String numericPart = raw;
        String suffix = null;
        int dash = raw.indexOf('-');
        if (dash != -1) {
            numericPart = raw.substring(0, dash);
            suffix = raw.substring(dash + 1);
        }

        List<Integer> segments = new ArrayList<>();
        for (String part : numericPart.split("\\.")) {
            int value;
            try {
                value = Integer.parseInt(part.trim());
            } catch (NumberFormatException ex) {
                value = 0;
            }
            segments.add(value);
        }
        if (segments.isEmpty()) {
            segments.add(0);
        }
        return new AppVersion(segments, suffix);
    }

    /** True if {@code candidate} is a newer version than this one. */
    public boolean isOlderThan(AppVersion candidate) {
        return compareTo(candidate) < 0;
    }

    @Override
    public int compareTo(AppVersion other) {
        int longest = Math.max(segments.size(), other.segments.size());
        for (int i = 0; i < longest; i++) {
            int a = i < segments.size() ? segments.get(i) : 0;
            int b = i < other.segments.size() ? other.segments.get(i) : 0;
            if (a != b) {
                return Integer.compare(a, b);
            }
        }
        if (suffix == null && other.suffix == null) {
            return 0;
        }
        if (suffix == null) {
            return 1; // no suffix beats any suffix at equal numeric version
        }
        if (other.suffix == null) {
            return -1;
        }
        return suffix.compareTo(other.suffix);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof AppVersion other && compareTo(other) == 0;
    }

    @Override
    public int hashCode() {
        return segments.hashCode();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < segments.size(); i++) {
            if (i > 0) sb.append('.');
            sb.append(segments.get(i));
        }
        if (suffix != null) sb.append('-').append(suffix);
        return sb.toString();
    }
}
