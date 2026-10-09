import java.awt.Color;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

// UI 종류(다이어리/SF/심플) + 밤낮 모드 + 10가지 테마 색 + 프리셋
public class Theme {
    public enum Style {
        DIARY("다이어리"), SF("SF 미래"), SIMPLE("심플");
        private final String label;
        Style(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }

    public static final String[] KEYS = {"primary", "secondary", "accent", "background", "surface",
                                         "text", "subText", "border", "danger", "dangerLight"};
    public static final String[] LABELS = {"주 색상", "보조 색상", "강조 색상", "배경색", "표면색",
                                           "텍스트", "보조 텍스트", "테두리", "위험", "위험 (연한)"};
    public static final String[] HELP = {"위쪽 바, 오늘, 선택", "탭, 보조 버튼", "토요일, 강조 글자", "창 바깥 배경",
                                         "카드·창 바탕", "기본 글자", "설명 글자", "선과 테두리", "삭제, 공휴일, 일요일", "경고 배경"};

    // 스타일별 기본 색 [낮, 밤]
    private static final Map<String, String[]> DEFAULTS = new HashMap<>();
    static {
        DEFAULTS.put("DIARY.day",    new String[]{"#5C6390", "#D9C7B0", "#8C6E54", "#EFE8DE", "#F8F5EE", "#3A3540", "#8A8090", "#DDD3C6", "#D9534F", "#F2C4C1"});
        DEFAULTS.put("DIARY.night",  new String[]{"#2B2E45", "#5A5048", "#C9A27E", "#1E1D23", "#2A2930", "#ECE6DD", "#A39C94", "#45424C", "#F15A5A", "#6B3434"});
        DEFAULTS.put("SF.day",       new String[]{"#0F1B2D", "#C9D8E8", "#00A3B8", "#E3ECF4", "#F6F9FC", "#0F1B2D", "#5A6B80", "#A9BDD0", "#FF3B5C", "#FFC2CD"});
        DEFAULTS.put("SF.night",     new String[]{"#05080F", "#12304D", "#00E5FF", "#0A0F1A", "#101828", "#D8F6FF", "#6E8CA8", "#1F3A55", "#FF3B5C", "#5A1A28"});
        DEFAULTS.put("SIMPLE.day",   new String[]{"#27272A", "#E4E4E7", "#3B82F6", "#F4F4F5", "#FFFFFF", "#18181B", "#71717A", "#E4E4E7", "#EF4444", "#FECACA"});
        DEFAULTS.put("SIMPLE.night", new String[]{"#18181B", "#3F3F46", "#60A5FA", "#09090B", "#18181B", "#FAFAFA", "#A1A1AA", "#2E2E33", "#F87171", "#7F1D1D"});
    }

    // 색 묶음 (프리셋)
    public static class Preset {
        public final String name;
        public final boolean dark;
        public final String[] colors;
        public final boolean builtIn;

        Preset(String name, boolean dark, boolean builtIn, String... colors) {
            this.name = name;
            this.dark = dark;
            this.builtIn = builtIn;
            this.colors = colors;
        }

        @Override public String toString() { return (dark ? "밤 · " : "낮 · ") + name; }
    }

    public static List<Preset> builtInPresets() {
        List<Preset> l = new ArrayList<>();
        l.add(new Preset("다이어리", false, true, DEFAULTS.get("DIARY.day")));
        l.add(new Preset("기본 블루", false, true, "#4A8DF8", "#EAF2FE", "#3B7DDD", "#F3F5F9", "#FFFFFF", "#1F2430", "#8A91A1", "#E4E8EF", "#E5484D", "#FDEDED"));
        l.add(new Preset("민트", false, true, "#1FA394", "#E3F5F2", "#178A7D", "#F2F6F5", "#FFFFFF", "#1D2A28", "#80908D", "#E0E8E6", "#E5484D", "#FDEDED"));
        l.add(new Preset("로즈", false, true, "#E5668B", "#FCEBF0", "#C94F75", "#F9F4F5", "#FFFFFF", "#2E2226", "#9A898F", "#EEE2E5", "#D63A3A", "#FCEAEA"));
        l.add(new Preset("모노", false, true, "#2F3238", "#EEEFF1", "#5B6170", "#F4F4F5", "#FFFFFF", "#1C1D20", "#8B8E95", "#E3E4E7", "#E5484D", "#FDEDED"));
        l.add(new Preset("다이어리 밤", true, true, DEFAULTS.get("DIARY.night")));
        l.add(new Preset("나이트 블루", true, true, "#6EA8FE", "#283141", "#82AAFF", "#131519", "#1C1F26", "#E7E9EE", "#8D94A5", "#2D323C", "#FF6B6B", "#3A2428"));
        l.add(new Preset("체리 나이트", true, true, "#FFCFD9", "#565656", "#363334", "#1A1A1B", "#27272B", "#F791A7", "#E04163", "#F2C3D4", "#F53939", "#F19090"));
        l.add(new Preset("미드나잇 그린", true, true, "#4FD1A5", "#22352F", "#6FE0BA", "#121614", "#1A201E", "#E3EEEA", "#86978F", "#2A3430", "#FF6B6B", "#3A2428"));
        return l;
    }

    // 내가 저장한 프리셋 (설정 파일에 preset.N.* 로 저장)
    public static List<Preset> userPresets(AppSettings s) {
        List<Preset> l = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            String name = s.get("preset." + i + ".name");
            if (name.isEmpty()) continue;
            String[] cols = s.get("preset." + i + ".colors").split(",");
            if (cols.length != KEYS.length) continue;
            l.add(new Preset(name, s.getBool("preset." + i + ".dark"), false, cols));
        }
        return l;
    }

    public static void saveUserPreset(AppSettings s, String name) {
        int slot = -1;
        for (int i = 0; i < 30 && slot < 0; i++) if (s.get("preset." + i + ".name").equals(name)) slot = i;   // 같은 이름은 덮어쓰기
        for (int i = 0; i < 30 && slot < 0; i++) if (s.get("preset." + i + ".name").isEmpty()) slot = i;      // 아니면 빈 칸
        if (slot < 0) slot = 29;
        StringBuilder b = new StringBuilder();
        for (String k : KEYS) b.append(b.length() > 0 ? "," : "").append(hex(c(k)));
        s.set("preset." + slot + ".name", name);
        s.setBool("preset." + slot + ".dark", night);
        s.set("preset." + slot + ".colors", b.toString());
    }

    public static void deleteUserPreset(AppSettings s, String name) {
        for (int i = 0; i < 30; i++) {
            if (s.get("preset." + i + ".name").equals(name)) {
                s.set("preset." + i + ".name", null);
                s.set("preset." + i + ".dark", null);
                s.set("preset." + i + ".colors", null);
            }
        }
    }

    // 프리셋을 지금 UI 종류에 적용 (밤 프리셋이면 밤 모드로 바뀜)
    public static void applyPreset(AppSettings s, Preset p) {
        s.setBool("nightMode", p.dark);
        night = p.dark;
        for (int i = 0; i < KEYS.length; i++) s.set(colorKey(KEYS[i]), p.colors[i].trim());
        load(s);
    }

    private static Style style = Style.DIARY;
    private static boolean night = false;
    private static final Map<String, Color> colors = new HashMap<>();

    // 설정에서 지금 테마를 읽어서 기본 컴포넌트에도 적용
    public static void load(AppSettings s) {
        try { style = Style.valueOf(s.get("uiStyle")); } catch (Exception e) { style = Style.DIARY; }
        night = s.getBool("nightMode");
        String[] def = DEFAULTS.get(presetKey());
        for (int i = 0; i < KEYS.length; i++) {
            String custom = s.get(colorKey(KEYS[i]));
            colors.put(KEYS[i], decode(custom.isEmpty() ? def[i] : custom, def[i]));
        }
        HazLook.install();
    }

    private static String presetKey() { return style.name() + (night ? ".night" : ".day"); }
    private static String colorKey(String key) { return "color." + presetKey() + "." + key; }

    public static void setColor(AppSettings s, String key, Color c) {
        s.set(colorKey(key), hex(c));
        colors.put(key, c);
    }

    public static void resetColors(AppSettings s) {
        for (String key : KEYS) s.set(colorKey(key), null);
        load(s);
    }

    // ----- 색 꺼내 쓰기 -----
    public static Color c(String key) { return colors.getOrDefault(key, Color.GRAY); }
    public static Color primary()     { return c("primary"); }
    public static Color secondary()   { return c("secondary"); }
    public static Color accent()      { return c("accent"); }
    public static Color bg()          { return c("background"); }
    public static Color surface()     { return c("surface"); }
    public static Color text()        { return c("text"); }
    public static Color sub()         { return c("subText"); }
    public static Color border()      { return c("border"); }
    public static Color danger()      { return c("danger"); }
    public static Color dangerLight() { return c("dangerLight"); }

    // 선택/오늘 표시 (밤에는 주 색상이 배경에 묻히기 쉬워서 강조 색상)
    public static Color highlight()   { return night && lum(primary()) < 90 ? accent() : primary(); }
    public static Color onPrimary()   { return onColor(primary()); }
    public static Color saturday()    { return night ? new Color(0x82AAFF) : new Color(0x3B7DDD); }

    // 입력칸 바탕
    public static Color field()       { return night ? Ui.blend(Color.WHITE, surface(), 0.05) : Ui.blend(bg(), surface(), 0.35); }

    public static Color onColor(Color c) { return lum(c) > 150 ? new Color(30, 30, 35) : Color.WHITE; }

    public static double lum(Color c) { return 0.299 * c.getRed() + 0.587 * c.getGreen() + 0.114 * c.getBlue(); }

    public static Style style() { return style; }
    public static boolean isNight() { return night; }
    public static int radius() { return style == Style.SF ? 2 : style == Style.SIMPLE ? 10 : 16; }
    public static boolean hasIndexTabs() { return style != Style.SIMPLE; }

    // ----- 테마 파일 내보내기 / 가져오기 (이미지는 경로만) -----
    public static void exportTo(AppSettings s, Path file) throws IOException {
        Properties p = new Properties();
        p.setProperty("uiStyle", style.name());
        for (String mode : new String[]{"day", "night"}) {
            String[] def = DEFAULTS.get(style.name() + "." + mode);
            for (int i = 0; i < KEYS.length; i++) {
                String custom = s.get("color." + style.name() + "." + mode + "." + KEYS[i]);
                p.setProperty(mode + "." + KEYS[i], custom.isEmpty() ? def[i] : custom);
            }
        }
        for (String k : new String[]{"characterName", "characterImage", "alarmMessage", "alarmMessageOnTime"})
            p.setProperty(k, s.get(k));
        try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            p.store(w, "HazCalendar theme");
        }
    }

    // 가져온 테마의 캐릭터 이미지 경로가 이 PC에 없으면 그 경로를 돌려줌 (다시 고르게 하려고)
    public static String importFrom(AppSettings s, Path file) throws IOException {
        Properties p = new Properties();
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) { p.load(r); }
        String st = p.getProperty("uiStyle", style.name());
        try { Style.valueOf(st); } catch (Exception e) { st = style.name(); }
        s.set("uiStyle", st);
        for (String mode : new String[]{"day", "night"}) {
            for (String key : KEYS) {
                String v = p.getProperty(mode + "." + key);
                if (v != null) s.set("color." + st + "." + mode + "." + key, v);
            }
        }
        for (String k : new String[]{"characterName", "alarmMessage", "alarmMessageOnTime"})
            if (p.getProperty(k) != null) s.set(k, p.getProperty(k));
        String img = p.getProperty("characterImage", "");
        String missing = null;
        if (!img.isBlank()) {
            if (Files.isRegularFile(java.nio.file.Paths.get(img))) s.set("characterImage", img);
            else missing = img;
        }
        s.save();
        return missing;
    }

    public static String hex(Color c) { return String.format("#%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue()); }

    public static Color decode(String hex, String fallback) {
        try { return Color.decode(hex.trim()); } catch (Exception e) { return Color.decode(fallback); }
    }
}
