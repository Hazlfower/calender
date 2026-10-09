import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.Properties;

// 화면·테마·캐릭터 같은 설정 (haz_settings.properties)
public class AppSettings {
    private static final String HAZ_SETTINGS_FILE = "haz_settings.properties";
    private final Properties props = new Properties();

    // 저장 폴더: 사용자 폴더\HazCalendar (테스트용으로 -Dhaz.home=폴더 로 바꿀 수 있음)
    public static Path dataDir() {
        String custom = System.getProperty("haz.home");
        if (custom != null && !custom.isBlank()) return Paths.get(custom);
        return Paths.get(System.getProperty("user.home"), "HazCalendar");
    }

    public AppSettings() {
        props.setProperty("uiStyle", "DIARY");
        props.setProperty("nightMode", "false");
        props.setProperty("profileName", "나의 다이어리");
        props.setProperty("characterName", "스마일");
        props.setProperty("alarmMessage", "{일정}까지 앞으로 {분}분 남았어요!");
        props.setProperty("alarmMessageOnTime", "{일정} 시간이에요! 늦지 않게 가요~");
        props.setProperty("widgetOnTop", "true");
        props.setProperty("widgetMode", "next");
        props.setProperty("view", "MONTH");
        props.setProperty("weekStart", "SUNDAY");
        props.setProperty("closeToTray", "true");
        props.setProperty("sidebarOpen", "true");
        props.setProperty("tetrisBuffer", "0");
    }

    public String get(String key) { return props.getProperty(key, ""); }

    public void set(String key, String value) {
        if (value == null || value.isEmpty()) props.remove(key);
        else props.setProperty(key, value);
    }

    public boolean getBool(String key) { return Boolean.parseBoolean(get(key)); }

    public void setBool(String key, boolean v) { props.setProperty(key, String.valueOf(v)); }

    public int getInt(String key, int def) {
        try { return Integer.parseInt(get(key).trim()); } catch (Exception e) { return def; }
    }

    public void setInt(String key, int v) { props.setProperty(key, String.valueOf(v)); }

    public LocalDate getDate(String key) {
        try { return LocalDate.parse(get(key)); } catch (Exception e) { return null; }
    }

    public Properties raw() { return props; }

    // 알람 대사. {일정}/{title} = 일정 이름, {분}/{min} = 남은 분, {캐릭터} = 캐릭터 이름, {시각} = 시작 시각
    public String formatAlarmMessage(boolean onTime, String title, long minutes, int startMinute) {
        String msg = onTime ? get("alarmMessageOnTime") : get("alarmMessage");
        if (msg.isBlank()) msg = onTime ? "{일정} 시간이에요!" : "{일정}까지 {분}분 남았어요!";
        return msg.replace("{일정}", title).replace("{title}", title)
                .replace("{분}", String.valueOf(minutes)).replace("{min}", String.valueOf(minutes))
                .replace("{캐릭터}", get("characterName"))
                .replace("{시각}", startMinute < 0 ? "" : TimeText.ampm(startMinute));
    }

    public void save() {
        Path path = dataDir().resolve(HAZ_SETTINGS_FILE);
        try {
            Files.createDirectories(path.getParent());
            Path tmp = path.resolveSibling(HAZ_SETTINGS_FILE + ".tmp");
            try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                props.store(w, "HazCalendar settings");
            }
            DataStore.replace(tmp, path);
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
        // 예전 기본 대사는 새 기본 대사로 (직접 바꾼 대사는 그대로 둠)
        if (get("alarmMessage").equals("앞으로 {min}분 남았어요!")) set("alarmMessage", "{일정}까지 앞으로 {분}분 남았어요!");
        if (get("alarmMessageOnTime").isBlank()) set("alarmMessageOnTime", "{일정} 시간이에요! 늦지 않게 가요~");
    }
}
