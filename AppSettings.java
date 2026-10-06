import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.Properties;

// 모든 설정을 저장하는 곳 (haz_settings.properties)
public class AppSettings {
    private static final String HAZ_SETTINGS_FILE = "haz_settings.properties";
    private final Properties props = new Properties();

    public static Path dataDir() {
        return Paths.get(System.getProperty("user.home"), "HazCalendar");
    }

    public AppSettings() {
        props.setProperty("uiStyle", "DIARY");
        props.setProperty("nightMode", "false");
        props.setProperty("characterName", "스마일");
        props.setProperty("alarmMessage", "앞으로 {min}분 남았어요!");
        props.setProperty("widgetOnTop", "true");
        props.setProperty("widgetMode", "next");
    }

    public String get(String key) { return props.getProperty(key, ""); }
    public void set(String key, String value) {
        if (value == null || value.isEmpty()) props.remove(key);
        else props.setProperty(key, value);
    }
    public boolean getBool(String key) { return Boolean.parseBoolean(get(key)); }
    public int getInt(String key, int def) {
        try { return Integer.parseInt(get(key)); } catch (Exception e) { return def; }
    }
    public LocalDate getDate(String key) {
        try { return LocalDate.parse(get(key)); } catch (Exception e) { return null; }
    }
    public Properties raw() { return props; }

    // 알람 대사: {min} → 남은 분, {title} → 일정 이름
    public String formatAlarmMessage(int minutes, String title) {
        return get("alarmMessage").replace("{min}", String.valueOf(minutes)).replace("{title}", title);
    }

    public void save() {
        Path path = dataDir().resolve(HAZ_SETTINGS_FILE);
        try {
            Files.createDirectories(path.getParent());
            try (Writer w = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                props.store(w, "HazCalendar settings");
            }
        } catch (IOException e) {
            System.err.println("설정 저장 실패: " + e.getMessage());
        }
    }

    public void load() {
        Path path = dataDir().resolve(HAZ_SETTINGS_FILE);
        if (!Files.exists(path)) return;
        try (Reader r = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            props.load(r);
        } catch (IOException e) {
            System.err.println("설정 불러오기 실패: " + e.getMessage());
        }
    }
}
