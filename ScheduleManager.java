import java.awt.Color;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

// 일정 + 카테고리 관리, 검색, 파일 저장
public class ScheduleManager {
    // 실제 날짜가 계산된 일정 한 번 (반복 일정의 n번째 같은 것)
    public static class Occurrence {
        public final Schedule schedule;
        public final LocalDateTime start;
        Occurrence(Schedule schedule, LocalDateTime start) { this.schedule = schedule; this.start = start; }
    }

    private static final String HAZ_SAVE_FILE = "haz_schedules.dat";
    private static final String HAZ_FORBIDDEN_CHARS = "\\/:*?\"<>|";

    private final List<Schedule> HAZscheduleList = new ArrayList<>();
    private final Map<String, Color> categories = new LinkedHashMap<>();
    private final Set<String> hidden = new HashSet<>();   // 달력에서 숨긴 카테고리
    private final List<Runnable> listeners = new ArrayList<>();

    public ScheduleManager() {
        categories.put("집", new Color(0xE8A0A0));
        categories.put("개인", new Color(0xA8C98A));
        categories.put("회사,학교", new Color(0x8FB3D9));
    }

    // ----- 변경 알림 -----
    public void addListener(Runnable r) { listeners.add(r); }
    private void changed() {
        save();
        for (Runnable r : new ArrayList<>(listeners)) r.run();
    }

    // ----- 일정 -----
    public void add(Schedule s) { HAZscheduleList.add(s); changed(); }
    public void remove(Schedule s) { HAZscheduleList.remove(s); changed(); }
    public void update() { changed(); }
    public List<Schedule> getAll() { return HAZscheduleList; }

    // 그날 일정 (숨긴 카테고리 제외), 시간순
    public List<Schedule> getByDate(LocalDate date) { return getByDate(date, false); }

    public List<Schedule> getByDate(LocalDate date, boolean includeHidden) {
        List<Schedule> result = new ArrayList<>();
        for (Schedule s : HAZscheduleList) {
            if (!includeHidden && hidden.contains(s.getCategory())) continue;
            if (s.occursOn(date)) result.add(s);
        }
        // 여러 날 일정이 먼저 (달력 막대가 위쪽에 이어지게)
        result.sort(Comparator.comparing((Schedule s) -> !s.isMultiDay())
                .thenComparing(s -> s.timeRangeOn(date)[0]));
        return result;
    }

    // 그날 "시작"하는 일정만 (알람, 예정 목록용)
    public List<Occurrence> startsOn(LocalDate date, boolean includeHidden) {
        List<Occurrence> result = new ArrayList<>();
        for (Schedule s : HAZscheduleList) {
            if (!includeHidden && hidden.contains(s.getCategory())) continue;
            if (s.startsOn(date)) result.add(new Occurrence(s, s.startOn(date)));
        }
        result.sort(Comparator.comparing(o -> o.start));
        return result;
    }

    // 지금 이후 예정된 일정 (max개, 최대 days일 뒤까지)
    public List<Occurrence> upcoming(LocalDateTime now, int days, int max) {
        List<Occurrence> result = new ArrayList<>();
        for (int i = 0; i <= days && result.size() < max; i++) {
            for (Occurrence o : startsOn(now.toLocalDate().plusDays(i), false)) {
                if (o.start.isAfter(now) && result.size() < max) result.add(o);
            }
        }
        return result;
    }

    // 지난 일정 (최근 것부터)
    public List<Occurrence> past(LocalDateTime now, int days, int max) {
        List<Occurrence> result = new ArrayList<>();
        for (int i = 0; i <= days && result.size() < max; i++) {
            List<Occurrence> day = startsOn(now.toLocalDate().minusDays(i), false);
            for (int j = day.size() - 1; j >= 0; j--) {
                if (day.get(j).start.isBefore(now) && result.size() < max) result.add(day.get(j));
            }
        }
        return result;
    }

    public Occurrence findNext(LocalDateTime now) {
        List<Occurrence> list = upcoming(now, 366, 1);
        return list.isEmpty() ? null : list.get(0);
    }

    // 이 일정이 다음에 시작하는 날짜 (없으면 시작 날짜)
    public LocalDate nextDateOf(Schedule s, LocalDate from) {
        for (int i = 0; i < 366 * 2; i++) if (s.startsOn(from.plusDays(i))) return from.plusDays(i);
        return s.getDate();
    }

    public List<Schedule> search(String query) {
        List<Schedule> result = new ArrayList<>();
        String q = query.trim().toLowerCase();
        if (q.isEmpty()) return result;
        for (Schedule s : HAZscheduleList) {
            String all = (s.getTitle() + " " + s.getCategory() + " " + (s.getMemo() == null ? "" : s.getMemo())).toLowerCase();
            if (all.contains(q)) result.add(s);
        }
        result.sort(Comparator.comparing(Schedule::getDate));
        return result;
    }

    public List<Schedule> findOverlaps(LocalDate date, LocalTime start, LocalTime end, Schedule except) {
        List<Schedule> result = new ArrayList<>();
        for (Schedule s : getByDate(date, true)) {
            if (s != except && s.overlaps(date, start, end)) result.add(s);
        }
        return result;
    }

    public boolean isValidTitle(String title) {
        if (title == null || title.isBlank()) return false;
        for (char c : HAZ_FORBIDDEN_CHARS.toCharArray()) if (title.indexOf(c) >= 0) return false;
        return true;
    }

    // ----- 카테고리 -----
    public Map<String, Color> getCategories() { return categories; }
    public Color colorOf(String category) { return categories.getOrDefault(category, new Color(0xBBBBBB)); }
    public boolean isHidden(String category) { return hidden.contains(category); }

    public void addCategory(String name, Color color) {
        if (name == null || name.isBlank()) return;
        categories.put(name.trim(), color);
        changed();
    }
    public void setCategoryColor(String name, Color color) { categories.put(name, color); changed(); }
    public void toggleHidden(String name) {
        if (!hidden.remove(name)) hidden.add(name);
        changed();
    }
    public void removeCategory(String name) {
        categories.remove(name);
        hidden.remove(name);
        changed();
    }

    // ----- 저장 / 불러오기 -----
    // #cat|이름|색|숨김
    // v2|제목|시작일|종료일|시작시각|끝시각|카테고리|반복|간격|요일들|n번째요일|종료일|지정날짜들|메모|링크|알람
    public void save() {
        List<String> lines = new ArrayList<>();
        for (Map.Entry<String, Color> e : categories.entrySet()) {
            lines.add(Store.join("#cat", Store.escape(e.getKey()), Theme.hex(e.getValue()), hidden.contains(e.getKey()) ? "1" : "0"));
        }
        for (Schedule s : HAZscheduleList) {
            StringBuilder days = new StringBuilder();
            for (DayOfWeek d : s.getWeekdays()) days.append(days.length() > 0 ? "," : "").append(d.getValue());
            StringBuilder dates = new StringBuilder();
            for (LocalDate d : s.getExtraDates()) dates.append(dates.length() > 0 ? "," : "").append(d);
            lines.add(Store.join("v2",
                    Store.escape(s.getTitle()), s.getDate().toString(), s.getEndDate().toString(),
                    s.getStart().toString(), s.getEnd().toString(), Store.escape(s.getCategory()),
                    s.getRepeat().name(), String.valueOf(s.getInterval()), days.toString(),
                    s.isMonthlyByWeekday() ? "1" : "0", s.getUntil() == null ? "" : s.getUntil().toString(),
                    dates.toString(), Store.escape(s.getMemo()), Store.escape(s.getLink()),
                    String.valueOf(s.getAlarmMinutes())));
        }
        Store.writeLines(HAZ_SAVE_FILE, lines);
    }

    public void load() {
        List<String[]> rows = Store.readLines(HAZ_SAVE_FILE);
        if (rows.isEmpty()) return;
        HAZscheduleList.clear();
        boolean sawCategory = false;
        for (String[] p : rows) {
            try {
                if (p[0].equals("#cat")) {
                    if (!sawCategory) { categories.clear(); sawCategory = true; }
                    String name = Store.unescape(p[1]);
                    categories.put(name, Theme.decode(p[2], "#BBBBBB"));
                    if (p.length > 3 && p[3].equals("1")) hidden.add(name);
                } else if (p[0].equals("#types")) {          // 예전 버전 호환
                    for (int i = 1; i < p.length; i++) {
                        String name = Store.unescape(p[i]);
                        if (name != null) categories.putIfAbsent(name, new Color(0xC9B8E8));
                    }
                } else if (p[0].equals("v2") && p.length >= 16) {
                    Schedule s = new Schedule(Store.unescape(p[1]), LocalDate.parse(p[2]),
                            LocalTime.parse(p[4]), LocalTime.parse(p[5]), Store.unescape(p[6]));
                    s.setEndDate(LocalDate.parse(p[3]));
                    s.setRepeat(Schedule.HAZRepeat.valueOf(p[7]));
                    s.setInterval(Integer.parseInt(p[8]));
                    Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
                    for (String d : p[9].split(",")) if (!d.isEmpty()) days.add(DayOfWeek.of(Integer.parseInt(d)));
                    s.setWeekdays(days);
                    s.setMonthlyByWeekday(p[10].equals("1"));
                    if (!p[11].isEmpty()) s.setUntil(LocalDate.parse(p[11]));
                    Set<LocalDate> dates = new TreeSet<>();
                    for (String d : p[12].split(",")) if (!d.isEmpty()) dates.add(LocalDate.parse(d));
                    s.setExtraDates(dates);
                    s.setMemo(Store.unescape(p[13]));
                    s.setLink(Store.unescape(p[14]));
                    s.setAlarmMinutes(Integer.parseInt(p[15]));
                    HAZscheduleList.add(s);
                } else if (p.length == 9) {                   // 예전 버전 호환
                    Schedule s = new Schedule(Store.unescape(p[0]), LocalDate.parse(p[1]),
                            LocalTime.parse(p[2]), LocalTime.parse(p[3]), Store.unescape(p[4]));
                    s.setRepeat(Schedule.HAZRepeat.valueOf(p[5]));
                    s.setMemo(Store.unescape(p[6]));
                    s.setLink(Store.unescape(p[7]));
                    s.setAlarmMinutes(Integer.parseInt(p[8]));
                    HAZscheduleList.add(s);
                }
            } catch (Exception e) {
                System.err.println("일정 한 줄 읽기 실패: " + String.join("|", p));
            }
        }
        for (Schedule s : HAZscheduleList) categories.putIfAbsent(s.getCategory(), new Color(0xC9B8E8));
    }
}
