package ps2gm.game.manager;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A minimal, dependency-free JSON reader for the FTP client. The project
 * ships no JSON library, and the API's response shapes are small and known,
 * so a hand-rolled parser avoids adding a new jar to the build for this.
 */
public final class MiniJson {

    private final String s;
    private int i;

    private MiniJson(String s) { this.s = s; }

    public static Object parse(String json) {
        MiniJson p = new MiniJson(json);
        p.skipWs();
        Object v = p.readValue();
        p.skipWs();
        return v;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String json) {
        Object v = parse(json);
        return (v instanceof Map) ? (Map<String, Object>) v : new LinkedHashMap<>();
    }

    private Object readValue() {
        char c = s.charAt(i);
        switch (c) {
            case '{': return readObject();
            case '[': return readArray();
            case '"': return readString();
            case 't': i += 4; return Boolean.TRUE;
            case 'f': i += 5; return Boolean.FALSE;
            case 'n': i += 4; return null;
            default:  return readNumber();
        }
    }

    private Map<String, Object> readObject() {
        Map<String, Object> map = new LinkedHashMap<>();
        i++; // {
        skipWs();
        if (peek() == '}') { i++; return map; }
        while (true) {
            skipWs();
            String key = readString();
            skipWs();
            i++; // :
            skipWs();
            map.put(key, readValue());
            skipWs();
            char c = s.charAt(i++);
            if (c == '}') break;
        }
        return map;
    }

    private List<Object> readArray() {
        List<Object> list = new ArrayList<>();
        i++; // [
        skipWs();
        if (peek() == ']') { i++; return list; }
        while (true) {
            skipWs();
            list.add(readValue());
            skipWs();
            char c = s.charAt(i++);
            if (c == ']') break;
        }
        return list;
    }

    private String readString() {
        StringBuilder sb = new StringBuilder();
        i++; // opening quote
        while (true) {
            char c = s.charAt(i++);
            if (c == '"') break;
            if (c == '\\') {
                char esc = s.charAt(i++);
                switch (esc) {
                    case 'n': sb.append('\n'); break;
                    case 't': sb.append('\t'); break;
                    case 'r': sb.append('\r'); break;
                    case 'b': sb.append('\b'); break;
                    case 'f': sb.append('\f'); break;
                    case '"': sb.append('"'); break;
                    case '\\': sb.append('\\'); break;
                    case '/': sb.append('/'); break;
                    case 'u':
                        sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                        i += 4;
                        break;
                    default: sb.append(esc);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private Double readNumber() {
        int start = i;
        while (i < s.length() && "-+.0123456789eE".indexOf(s.charAt(i)) >= 0) i++;
        return Double.parseDouble(s.substring(start, i));
    }

    private char peek() { return s.charAt(i); }

    private void skipWs() {
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
    }

    // ---- small helpers for pulling typed values out of a decoded Map ----
    public static String str(Map<String, Object> obj, String key) {
        Object v = obj.get(key);
        return v == null ? null : v.toString();
    }

    public static int intVal(Map<String, Object> obj, String key, int def) {
        Object v = obj.get(key);
        return v instanceof Number ? ((Number) v).intValue() : def;
    }

    public static long longVal(Map<String, Object> obj, String key, long def) {
        Object v = obj.get(key);
        return v instanceof Number ? ((Number) v).longValue() : def;
    }

    @SuppressWarnings("unchecked")
    public static List<Object> list(Map<String, Object> obj, String key) {
        Object v = obj.get(key);
        return v instanceof List ? (List<Object>) v : new ArrayList<>();
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> asObject(Object v) {
        return v instanceof Map ? (Map<String, Object>) v : new LinkedHashMap<>();
    }
}
