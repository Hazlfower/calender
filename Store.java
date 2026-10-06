import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

// 파일 읽기/쓰기 공통 도구. 한 줄 = 한 항목, 칸은 | 로 구분
public class Store {
    public static List<String[]> readLines(String fileName) {
        List<String[]> rows = new ArrayList<>();
        Path path = AppSettings.dataDir().resolve(fileName);
        if (!Files.exists(path)) return rows;
        try {
            for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                if (!line.isBlank()) rows.add(line.split("\\|", -1));
            }
        } catch (IOException e) {
            System.err.println(fileName + " 읽기 실패: " + e.getMessage());
        }
        return rows;
    }

    public static void writeLines(String fileName, List<String> lines) {
        Path path = AppSettings.dataDir().resolve(fileName);
        try {
            Files.createDirectories(path.getParent());
            Files.write(path, lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println(fileName + " 저장 실패: " + e.getMessage());
        }
    }

    // 글 안의 | 나 줄바꿈이 저장 형식을 깨지 않도록 변환
    public static String escape(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\").replace("|", "\\p").replace("\n", "\\n");
    }

    public static String unescape(String text) {
        if (text == null || text.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\\' && i + 1 < text.length()) {
                char next = text.charAt(++i);
                if (next == 'p') sb.append('|');
                else if (next == 'n') sb.append('\n');
                else sb.append(next);
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    public static String join(String... parts) { return String.join("|", parts); }
}
