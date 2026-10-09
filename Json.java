import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// 외부 라이브러리 없이 쓰는 작은 JSON 읽기/쓰기 (Map, List, String, Number, Boolean, null)
public final class Json {
    private Json() { }

    public static Object parse(String text) {
        Parser p = new Parser(text);
        Object v = p.value();
        p.ws();
        if (p.i < text.length()) throw new IllegalArgumentException("JSON 뒤에 알 수 없는 내용이 있어요");
        return v;
    }

    public static String write(Object o) {
        StringBuilder sb = new StringBuilder();
        write(o, sb, 0);
        return sb.append('\n').toString();
    }

    private static void write(Object o, StringBuilder sb, int depth) {
        if (o == null) sb.append("null");
        else if (o instanceof String) quote((String) o, sb);
        else if (o instanceof Boolean || o instanceof Integer || o instanceof Long) sb.append(o);
        else if (o instanceof Number) {
            double d = ((Number) o).doubleValue();
            if (d == Math.rint(d) && Math.abs(d) < 1e15) sb.append((long) d); else sb.append(d);
        } else if (o instanceof Map) {
            Map<?, ?> m = (Map<?, ?>) o;
            if (m.isEmpty()) { sb.append("{}"); return; }
            sb.append("{\n");
            int k = 0;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                indent(sb, depth + 1);
                quote(String.valueOf(e.getKey()), sb);
                sb.append(": ");
                write(e.getValue(), sb, depth + 1);
                if (++k < m.size()) sb.append(',');
                sb.append('\n');
            }
            indent(sb, depth);
            sb.append('}');
        } else if (o instanceof Collection) {
            Collection<?> c = (Collection<?>) o;
            if (c.isEmpty()) { sb.append("[]"); return; }
            sb.append("[\n");
            int k = 0;
            for (Object v : c) {
                indent(sb, depth + 1);
                write(v, sb, depth + 1);
                if (++k < c.size()) sb.append(',');
                sb.append('\n');
            }
            indent(sb, depth);
            sb.append(']');
        } else quote(o.toString(), sb);
    }

    private static void indent(StringBuilder sb, int n) { sb.append("  ".repeat(n)); }

    private static void quote(String s, StringBuilder sb) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        sb.append('"');
    }

    private static final class Parser {
        final String s;
        int i;

        Parser(String s) { this.s = s; }

        void ws() { while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++; }

        char peek() { return i < s.length() ? s.charAt(i) : 0; }

        Object value() {
            ws();
            if (i >= s.length()) throw err("값이 없어요");
            char c = s.charAt(i);
            switch (c) {
                case '{': return obj();
                case '[': return arr();
                case '"': return str();
                case 't': expect("true"); return Boolean.TRUE;
                case 'f': expect("false"); return Boolean.FALSE;
                case 'n': expect("null"); return null;
                default: return num();
            }
        }

        Map<String, Object> obj() {
            i++;
            Map<String, Object> m = new LinkedHashMap<>();
            ws();
            if (peek() == '}') { i++; return m; }
            while (true) {
                ws();
                String k = str();
                ws();
                if (peek() != ':') throw err("':' 가 필요해요");
                i++;
                m.put(k, value());
                ws();
                char c = peek();
                i++;
                if (c == '}') return m;
                if (c != ',') throw err("',' 가 필요해요");
            }
        }

        List<Object> arr() {
            i++;
            List<Object> l = new ArrayList<>();
            ws();
            if (peek() == ']') { i++; return l; }
            while (true) {
                l.add(value());
                ws();
                char c = peek();
                i++;
                if (c == ']') return l;
                if (c != ',') throw err("',' 가 필요해요");
            }
        }

        String str() {
            if (peek() != '"') throw err("문자열이 필요해요");
            i++;
            StringBuilder b = new StringBuilder();
            while (true) {
                if (i >= s.length()) throw err("문자열이 끝나지 않았어요");
                char c = s.charAt(i++);
                if (c == '"') break;
                if (c == '\\') {
                    if (i >= s.length()) throw err("문자열이 끝나지 않았어요");
                    char e = s.charAt(i++);
                    switch (e) {
                        case 'n': b.append('\n'); break;
                        case 't': b.append('\t'); break;
                        case 'r': b.append('\r'); break;
                        case 'b': b.append('\b'); break;
                        case 'f': b.append('\f'); break;
                        case 'u':
                            if (i + 4 > s.length()) throw err("\\u 뒤에 글자가 부족해요");
                            b.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                            i += 4;
                            break;
                        default: b.append(e);
                    }
                } else b.append(c);
            }
            return b.toString();
        }

        Object num() {
            int st = i;
            while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
            if (st == i) throw err("알 수 없는 문자 '" + peek() + "'");
            return Double.parseDouble(s.substring(st, i));
        }

        void expect(String w) {
            if (!s.startsWith(w, i)) throw err("'" + w + "' 가 필요해요");
            i += w.length();
        }

        RuntimeException err(String m) { return new IllegalArgumentException("JSON 오류(" + i + "번째 글자): " + m); }
    }

    // ----- Map 에서 값 꺼내기 -----
    public static String str(Map<?, ?> m, String k, String def) {
        Object v = m == null ? null : m.get(k);
        return v == null ? def : v.toString();
    }

    public static int num(Map<?, ?> m, String k, int def) {
        Object v = m == null ? null : m.get(k);
        return v instanceof Number ? ((Number) v).intValue() : def;
    }

    public static boolean bool(Map<?, ?> m, String k, boolean def) {
        Object v = m == null ? null : m.get(k);
        return v instanceof Boolean ? (Boolean) v : def;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> map(Map<?, ?> m, String k) {
        Object v = m == null ? null : m.get(k);
        return v instanceof Map ? (Map<String, Object>) v : null;
    }

    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> maps(Map<?, ?> m, String k) {
        List<Map<String, Object>> out = new ArrayList<>();
        Object v = m == null ? null : m.get(k);
        if (v instanceof List) for (Object o : (List<?>) v) if (o instanceof Map) out.add((Map<String, Object>) o);
        return out;
    }

    public static List<String> strs(Map<?, ?> m, String k) {
        List<String> out = new ArrayList<>();
        Object v = m == null ? null : m.get(k);
        if (v instanceof List) for (Object o : (List<?>) v) if (o != null) out.add(o.toString());
        return out;
    }

    public static List<Integer> ints(Map<?, ?> m, String k) {
        List<Integer> out = new ArrayList<>();
        Object v = m == null ? null : m.get(k);
        if (v instanceof List) for (Object o : (List<?>) v) if (o instanceof Number) out.add(((Number) o).intValue());
        return out;
    }
}
