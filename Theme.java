import javax.swing.*;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.metal.DefaultMetalTheme;
import javax.swing.plaf.metal.MetalLookAndFeel;
import java.awt.Color;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

// UI 종류(다이어리/SF/심플) + 밤낮 모드 + 10가지 테마 색
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

    // 스타일별 기본 색 [낮, 밤]
    private static final Map<String, String[]> PRESETS = new HashMap<>();
    static {
        PRESETS.put("DIARY.day",   new String[]{"#5C6390", "#D9C7B0", "#8C6E54", "#EFE8DE", "#F8F5EE", "#3A3540", "#8A8090", "#DDD3C6", "#D9534F", "#F2C4C1"});
        PRESETS.put("DIARY.night", new String[]{"#2B2E45", "#5A5048", "#C9A27E", "#1E1D23", "#2A2930", "#ECE6DD", "#A39C94", "#45424C", "#F15A5A", "#6B3434"});
        PRESETS.put("SF.day",      new String[]{"#0F1B2D", "#C9D8E8", "#00A3B8", "#E3ECF4", "#F6F9FC", "#0F1B2D", "#5A6B80", "#A9BDD0", "#FF3B5C", "#FFC2CD"});
        PRESETS.put("SF.night",    new String[]{"#05080F", "#12304D", "#00E5FF", "#0A0F1A", "#101828", "#D8F6FF", "#6E8CA8", "#1F3A55", "#FF3B5C", "#5A1A28"});
        PRESETS.put("SIMPLE.day",  new String[]{"#27272A", "#E4E4E7", "#3B82F6", "#F4F4F5", "#FFFFFF", "#18181B", "#71717A", "#E4E4E7", "#EF4444", "#FECACA"});
        PRESETS.put("SIMPLE.night",new String[]{"#18181B", "#3F3F46", "#60A5FA", "#09090B", "#18181B", "#FAFAFA", "#A1A1AA", "#2E2E33", "#F87171", "#7F1D1D"});
    }

    private static Style style = Style.DIARY;
    private static boolean night = false;
    private static final Map<String, Color> colors = new HashMap<>();

    // 설정에서 현재 테마 읽기
    public static void load(AppSettings s) {
        try { style = Style.valueOf(s.get("uiStyle")); } catch (Exception e) { style = Style.DIARY; }
        night = s.getBool("nightMode");
        String[] preset = PRESETS.get(presetKey());
        for (int i = 0; i < KEYS.length; i++) {
            String custom = s.get(colorKey(KEYS[i]));
            colors.put(KEYS[i], decode(custom.isEmpty() ? preset[i] : custom, preset[i]));
        }
        applyLookAndFeel();
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
    // 강조 표시용 (낮 = 주 색상, 밤 = 강조 색상이 더 잘 보임)
    public static Color highlight()   { return night ? accent() : primary(); }

    // 주 색상 위에 올라갈 글자색 (밝기 보고 흰/검 결정)
    public static Color onPrimary() { return onColor(primary()); }
    public static Color onColor(Color c) {
        double lum = 0.299 * c.getRed() + 0.587 * c.getGreen() + 0.114 * c.getBlue();
        return lum > 150 ? new Color(30, 30, 35) : Color.WHITE;
    }

    public static Style style() { return style; }
    public static boolean isNight() { return night; }
    public static int radius() { return style == Style.SF ? 0 : style == Style.SIMPLE ? 8 : 16; }
    public static boolean hasIndexTabs() { return style != Style.SIMPLE; }

    // ----- 기본 컴포넌트(입력칸, 목록 등)도 테마 색으로 -----
    private static void applyLookAndFeel() {
        try {
            MetalLookAndFeel.setCurrentTheme(new DefaultMetalTheme());
            UIManager.setLookAndFeel(new MetalLookAndFeel());
        } catch (Exception ignored) { }
        ColorUIResource surface = new ColorUIResource(surface()), bg = new ColorUIResource(bg());
        ColorUIResource text = new ColorUIResource(text()), sel = new ColorUIResource(Ui.blend(accent(), surface(), 0.65));
        ColorUIResource border = new ColorUIResource(border());
        String[] backgrounds = {"Panel.background", "OptionPane.background", "CheckBox.background", "RadioButton.background",
                "ScrollPane.background", "Viewport.background", "TabbedPane.background", "ColorChooser.background",
                "Spinner.background", "ToolTip.background", "PopupMenu.background", "MenuItem.background", "Button.background"};
        for (String k : backgrounds) UIManager.put(k, surface);
        String[] fields = {"TextField.background", "TextArea.background", "FormattedTextField.background",
                "List.background", "ComboBox.background", "Table.background"};
        for (String k : fields) UIManager.put(k, bg);
        String[] foregrounds = {"Label.foreground", "CheckBox.foreground", "RadioButton.foreground", "TextField.foreground",
                "TextArea.foreground", "FormattedTextField.foreground", "List.foreground", "ComboBox.foreground",
                "OptionPane.messageForeground", "ToolTip.foreground", "MenuItem.foreground", "Button.foreground",
                "TextField.caretForeground", "TextArea.caretForeground", "FormattedTextField.caretForeground", "TitledBorder.titleColor"};
        for (String k : foregrounds) UIManager.put(k, text);
        for (String k : new String[]{"List.selectionBackground", "ComboBox.selectionBackground", "TextField.selectionBackground", "TextArea.selectionBackground"})
            UIManager.put(k, sel);
        for (String k : new String[]{"List.selectionForeground", "ComboBox.selectionForeground", "TextField.selectionForeground", "TextArea.selectionForeground"})
            UIManager.put(k, new ColorUIResource(onColor(sel)));
        UIManager.put("ScrollBar.thumb", new ColorUIResource(Ui.blend(border(), sub(), 0.4)));
        UIManager.put("ScrollBar.track", bg);
        UIManager.put("ScrollBar.background", bg);
        UIManager.put("ScrollBar.width", 11);
        UIManager.put("ScrollBarUI", "ThinScrollBarUI");
        UIManager.put("ComboBox.buttonBackground", surface);
        UIManager.put("TextField.border", BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(border()), Ui.pad(3, 6, 3, 6)));
        UIManager.put("TextArea.border", Ui.pad(4, 6, 4, 6));
        UIManager.put("ToolTip.border", BorderFactory.createLineBorder(border()));
        UIManager.put("ScrollPane.border", BorderFactory.createLineBorder(border()));
        UIManager.put("ComboBox.border", BorderFactory.createLineBorder(border()));
        UIManager.put("Spinner.border", BorderFactory.createLineBorder(border()));
        UIManager.put("CheckBox.focus", surface);
        UIManager.put("RadioButton.focus", surface);
        java.awt.Font f = Ui.font(13, false);
        for (String k : new String[]{"Label.font", "Button.font", "CheckBox.font", "RadioButton.font", "TextField.font", "TextArea.font",
                "List.font", "ComboBox.font", "Spinner.font", "OptionPane.messageFont", "OptionPane.buttonFont", "ToolTip.font",
                "MenuItem.font", "FormattedTextField.font", "TitledBorder.font"})
            UIManager.put(k, new javax.swing.plaf.FontUIResource(f));
    }

    // ----- 테마 파일 내보내기 / 가져오기 (이미지는 제외) -----
    public static void exportTo(AppSettings s, Path file) throws IOException {
        Properties p = new Properties();
        p.setProperty("uiStyle", style.name());
        for (String mode : new String[]{"day", "night"}) {
            String[] preset = PRESETS.get(style.name() + "." + mode);
            for (int i = 0; i < KEYS.length; i++) {
                String custom = s.get("color." + style.name() + "." + mode + "." + KEYS[i]);
                p.setProperty(mode + "." + KEYS[i], custom.isEmpty() ? preset[i] : custom);
            }
        }
        p.setProperty("characterName", s.get("characterName"));
        p.setProperty("alarmMessage", s.get("alarmMessage"));
        try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            p.store(w, "HazCalendar theme");
        }
    }

    public static void importFrom(AppSettings s, Path file) throws IOException {
        Properties p = new Properties();
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) { p.load(r); }
        String st = p.getProperty("uiStyle", style.name());
        s.set("uiStyle", st);
        for (String mode : new String[]{"day", "night"}) {
            for (String key : KEYS) {
                String v = p.getProperty(mode + "." + key);
                if (v != null) s.set("color." + st + "." + mode + "." + key, v);
            }
        }
        if (p.getProperty("characterName") != null) s.set("characterName", p.getProperty("characterName"));
        if (p.getProperty("alarmMessage") != null) s.set("alarmMessage", p.getProperty("alarmMessage"));
        s.save();
    }

    public static String hex(Color c) { return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue()); }
    public static Color decode(String hex, String fallback) {
        try { return Color.decode(hex.trim()); } catch (Exception e) { return Color.decode(fallback); }
    }
}
