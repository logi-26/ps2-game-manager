package ps2gm.game.manager;

public final class ChecksumSidecar {

    private ChecksumSidecar() {}

    // The lower-case hex digest from sidecarText, or null if it doesn't parse.
    public static String parseHexDigest(String sidecarText) {
        if (sidecarText == null) {
            return null;
        }
        String firstLine = sidecarText.strip().split("\\r?\\n", 2)[0].strip();
        int firstSpace = firstLine.indexOf(' ');
        String hex = firstSpace == -1 ? firstLine : firstLine.substring(0, firstSpace);
        hex = hex.toLowerCase();
        return isHex64(hex) ? hex : null;
    }

    private static boolean isHex64(String s) {
        if (s.length() != 64) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean hexDigit = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f');
            if (!hexDigit) {
                return false;
            }
        }
        return true;
    }
}
