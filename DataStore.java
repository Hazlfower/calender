import java.awt.Color;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.Timer;

// 일정·카테고리·D-Day·할 일·메모를 haz_data.json 한 파일에 저장
// - 처음 실행할 때 예전 버전 파일(haz_schedules.dat 등)이 있으면 자동으로 옮겨 옴
// - 저장은 바뀐 뒤 0.6초 모아서 한 번 (임시 파일에 쓰고 바꿔치기 → 도중에 꺼져도 안 깨짐)
public class DataStore {
    public static final String HAZ_DATA_FILE = "haz_data.json";

    private final ScheduleManager HAZscheduleManager;
    private final Todos todos;
    private final MemoManager memos;
    private final Timer saveTimer;
    public String loadMessage;        // 시작할 때 보여 줄 안내 (데이터 이전, 읽기 오류)

    public DataStore(ScheduleManager sm, Todos todos, MemoManager memos) {
        this.HAZscheduleManager = sm;
        this.todos = todos;
        this.memos = memos;
        saveTimer = new Timer(600, e -> saveNow());
        saveTimer.setRepeats(false);
        sm.setSaver(this::saveLater);
        todos.setSaver(this::saveLater);
        memos.setSaver(this::saveLater);
    }

    public static Path file() { return AppSettings.dataDir().resolve(HAZ_DATA_FILE); }

    public void saveLater() { saveTimer.restart(); }

    public synchronized void saveNow() {
        saveTimer.stop();
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("app", "HazCalendar");
        root.put("version", 3);
        HAZscheduleManager.writeTo(root);
        todos.writeTo(root);
        memos.writeTo(root);
        try {
            Files.createDirectories(file().getParent());
            Path tmp = file().resolveSibling(HAZ_DATA_FILE + ".tmp");
            Files.writeString(tmp, Json.write(root), StandardCharsets.UTF_8);
            replace(tmp, file());
        } catch (IOException e) {
            System.err.println("저장 실패: " + e.getMessage());
        }
    }

    public static void replace(Path tmp, Path target) throws IOException {
        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @SuppressWarnings("unchecked")
    public void load(AppSettings settings) {
        Path f = file();
        if (Files.exists(f)) {
            try {
                Object o = Json.parse(Files.readString(f, StandardCharsets.UTF_8));
                if (!(o instanceof Map)) throw new IllegalArgumentException("형식이 올바르지 않아요");
                Map<String, Object> root = (Map<String, Object>) o;
                HAZscheduleManager.readFrom(root);
                todos.readFrom(root);
                memos.readFrom(root);
                return;
            } catch (Exception e) {
                Path broken = f.resolveSibling("haz_data-broken-" + System.currentTimeMillis() + ".json");
                try { Files.copy(f, broken); } catch (IOException ignored) { }
                loadMessage = "저장된 데이터를 읽지 못해서 새로 시작해요.\n원래 파일은 " + broken.getFileName()
                        + " 으로 보관해 두었어요.\n\n(" + e.getMessage() + ")";
                return;
            }
        }
        if (migrateLegacy(settings)) {
            saveNow();
            loadMessage = "예전 버전에서 쓰던 일정·할 일·메모를 새 저장 방식으로 옮겼어요.\n(예전 파일은 지우지 않고 그대로 두었어요)";
        }
    }

    // ================================================================== 예전 버전 (.dat) 옮겨 오기

    private boolean migrateLegacy(AppSettings settings) {
        Path dir = AppSettings.dataDir();
        boolean any = false;
        List<String[]> rows = readLegacy(dir.resolve("haz_schedules.dat"));
        if (!rows.isEmpty()) {
            any = true;
            Map<String, Object> root = new LinkedHashMap<>();
            List<Object> cats = new ArrayList<>(), list = new ArrayList<>();
            for (String[] p : rows) {
                try {
                    if (p[0].equals("#cat") && p.length >= 3) {
                        Map<String, Object> c = new LinkedHashMap<>();
                        c.put("name", unescape(p[1]));
                        c.put("color", p[2]);
                        c.put("hidden", p.length > 3 && p[3].equals("1"));
                        cats.add(c);
                    } else if (p[0].equals("#types")) {
                        for (int i = 1; i < p.length; i++) {
                            String n = unescape(p[i]);
                            if (n == null) continue;
                            Map<String, Object> c = new LinkedHashMap<>();
                            c.put("name", n);
                            c.put("color", "#C9B8E8");
                            cats.add(c);
                        }
                    } else if (p[0].equals("v2") && p.length >= 16) {
                        Schedule s = new Schedule();
                        s.title = nz(unescape(p[1]));
                        s.date = LocalDate.parse(p[2]);
                        LocalDate end = LocalDate.parse(p[3]);
                        s.endDate = end.isAfter(s.date) ? end : null;
                        s.start = minutes(p[4]);
                        s.end = minutes(p[5]);
                        if (s.end == 23 * 60 + 59) s.end = 1440;
                        s.category = nz(unescape(p[6]));
                        s.repeat = Schedule.HAZRepeat.of(p[7]);
                        s.interval = Math.max(1, Integer.parseInt(p[8]));
                        for (String d : p[9].split(",")) if (!d.isEmpty()) s.weekdays.add(DayOfWeek.of(Integer.parseInt(d)));
                        s.monthlyNth = p[10].equals("1");
                        if (!p[11].isEmpty()) s.until = LocalDate.parse(p[11]);
                        for (String d : p[12].split(",")) if (!d.isEmpty()) s.extraDates.add(LocalDate.parse(d));
                        s.memo = nz(unescape(p[13]));
                        s.link = nz(unescape(p[14]));
                        int a = Integer.parseInt(p[15]);
                        if (a >= 0) s.alarms.add(a);
                        list.add(s.toMap());
                    } else if (p.length == 9) {      // 아주 예전 형식
                        Schedule s = new Schedule();
                        s.title = nz(unescape(p[0]));
                        s.date = LocalDate.parse(p[1]);
                        s.start = minutes(p[2]);
                        s.end = minutes(p[3]);
                        s.category = nz(unescape(p[4]));
                        s.repeat = Schedule.HAZRepeat.of(p[5]);
                        s.memo = nz(unescape(p[6]));
                        s.link = nz(unescape(p[7]));
                        int a = Integer.parseInt(p[8]);
                        if (a >= 0) s.alarms.add(a);
                        list.add(s.toMap());
                    }
                } catch (Exception e) {
                    System.err.println("예전 일정 한 줄을 건너뛰었어요: " + String.join("|", p));
                }
            }
            root.put("categories", cats);
            root.put("schedules", list);
            HAZscheduleManager.readFrom(root);
        }
        for (String[] p : readLegacy(dir.resolve("haz_todos.dat"))) {
            if (p.length >= 2) { todos.addLegacy(unescape(p[1]), p[0].equals("1")); any = true; }
        }
        for (String[] p : readLegacy(dir.resolve("haz_memos.dat"))) {
            if (p.length < 8) continue;
            try {
                MemoManager.Memo m = new MemoManager.Memo();
                m.title = p[0].isEmpty() ? "메모" : unescape(p[0]);
                m.text = nz(unescape(p[1]));
                m.x = Integer.parseInt(p[2]); m.y = Integer.parseInt(p[3]);
                m.w = Integer.parseInt(p[4]); m.h = Integer.parseInt(p[5]);
                m.pinned = p[6].equals("1"); m.open = p[7].equals("1");
                memos.addLegacy(m);
                any = true;
            } catch (Exception ignored) { }
        }
        // 예전 설정의 디데이 하나 → D-Day 목록으로
        LocalDate dd = settings.getDate("ddayDate");
        if (dd != null) {
            DDay d = new DDay();
            d.name = settings.get("ddayTitle").isBlank() ? "D-Day" : settings.get("ddayTitle");
            d.date = dd;
            HAZscheduleManager.getDDays().add(d);
            settings.set("ddayDate", null);
            settings.set("ddayTitle", null);
            settings.save();
            any = true;
        }
        return any;
    }

    private static List<String[]> readLegacy(Path p) {
        List<String[]> rows = new ArrayList<>();
        if (!Files.exists(p)) return rows;
        try {
            for (String line : Files.readAllLines(p, StandardCharsets.UTF_8))
                if (!line.isBlank()) rows.add(line.split("\\|", -1));
        } catch (IOException ignored) { }
        return rows;
    }

    private static int minutes(String t) {
        LocalTime lt = LocalTime.parse(t);
        return lt.getHour() * 60 + lt.getMinute();
    }

    private static String nz(String s) { return s == null ? "" : s; }

    private static String unescape(String text) {
        if (text == null || text.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\\' && i + 1 < text.length()) {
                char next = text.charAt(++i);
                if (next == 'p') sb.append('|');
                else if (next == 'n') sb.append('\n');
                else sb.append(next);
            } else sb.append(c);
        }
        return sb.toString();
    }

    // ================================================================== DiaryCalendar 데이터 가져오기

    // Windows: %APPDATA%\DiaryCalendar\data.json
    public static Path diaryCalendarFile() {
        String appdata = System.getenv("APPDATA");
        if (appdata != null) return Paths.get(appdata, "DiaryCalendar", "data.json");
        return Paths.get(System.getProperty("user.home"), ".diarycalendar", "data.json");
    }

    // 가져온 일정 수, D-Day 수 등을 담은 안내 문장
    @SuppressWarnings("unchecked")
    public String importDiaryCalendar(Path f) throws IOException {
        Object o = Json.parse(Files.readString(f, StandardCharsets.UTF_8));
        if (!(o instanceof Map)) throw new IOException("DiaryCalendar 데이터 파일이 아니에요");
        Map<String, Object> root = (Map<String, Object>) o;
        if (!root.containsKey("schedules") && !root.containsKey("types")) throw new IOException("DiaryCalendar 데이터 파일이 아니에요");

        for (Map<String, Object> t : Json.maps(root, "types")) {
            String name = Json.str(t, "name", "").trim();
            if (name.isEmpty() || HAZscheduleManager.getCategories().containsKey(name)) continue;
            Color c = Theme.decode(Json.str(t, "color", "#B0B0B0"), "#B0B0B0");
            HAZscheduleManager.getCategories().put(name, new ScheduleManager.Category(name, c));
        }
        // 알람은 일정마다 붙어 있던 것을 그 일정의 알람 목록으로
        Map<String, List<Map<String, Object>>> alarmsBy = new LinkedHashMap<>();
        for (Map<String, Object> a : Json.maps(root, "alarms")) {
            if (!Json.bool(a, "enabled", true)) continue;
            alarmsBy.computeIfAbsent(Json.str(a, "scheduleId", ""), k -> new ArrayList<>()).add(a);
        }
        List<Schedule> in = new ArrayList<>();
        for (Map<String, Object> m : Json.maps(root, "schedules")) {
            try {
                Schedule s = Schedule.fromMap(m);
                List<Integer> mins = new ArrayList<>();
                for (Map<String, Object> a : alarmsBy.getOrDefault(s.id, List.of())) {
                    mins.add(Json.num(a, "before", 10));
                    String link = Json.str(a, "link", "");
                    if (s.link.isEmpty() && !link.isBlank()) s.link = link;
                }
                s.setAlarms(mins);
                in.add(s);
            } catch (Exception ignored) { }
        }
        int ddays = 0;
        for (Map<String, Object> m : Json.maps(root, "ddays")) {
            try {
                DDay d = DDay.fromMap(m);
                boolean dup = false;
                for (DDay x : HAZscheduleManager.getDDays()) if (x.id.equals(d.id)) dup = true;
                if (!dup) { HAZscheduleManager.getDDays().add(d); ddays++; }
            } catch (Exception ignored) { }
        }
        int added = HAZscheduleManager.importAll(in);
        String memoText = "";
        Object memo = root.get("memo");
        if (memo instanceof String) memoText = (String) memo;
        else if (memo instanceof Map) memoText = Json.str((Map<?, ?>) memo, "text", "");
        boolean memoAdded = false;
        if (!memoText.isBlank()) {
            MemoManager.Memo m = memos.create(memoText);
            m.title = "DiaryCalendar 메모";
            memos.changed();
            memoAdded = true;
        }
        return "일정 " + added + "개" + (in.size() > added ? " (이미 있는 " + (in.size() - added) + "개는 건너뜀)" : "")
                + ", D-Day " + ddays + "개" + (memoAdded ? ", 메모 1개" : "") + "를 가져왔어요.";
    }

    // 지금 데이터를 백업 파일로 복사
    public Path backup() throws IOException {
        saveNow();
        Path dir = AppSettings.dataDir().resolve("backup");
        Files.createDirectories(dir);
        Path out = dir.resolve("haz_data-" + LocalDate.now() + "-" + System.currentTimeMillis() % 100000 + ".json");
        Files.copy(file(), out);
        return out;
    }
}
