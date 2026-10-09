import java.awt.Color;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// 일정·카테고리·D-Day 를 들고 있고, 바뀌면 화면에 알리고 저장을 부탁함
public class ScheduleManager {
    public static final String HAZ_FORBIDDEN_CHARS = "\\/:*?\"<>|";

    public static class Category {
        public String name;
        public Color color;
        public boolean hidden;

        public Category(String name, Color color) {
            this.name = name;
            this.color = color;
        }

        @Override public String toString() { return name; }
    }

    // 실제 날짜가 정해진 일정 한 번 (반복 일정의 n번째 회차 같은 것)
    public static class Occurrence {
        public final Schedule schedule;
        public final LocalDate date;
        public final LocalDateTime start, end;

        Occurrence(Schedule s, LocalDate date) {
            this.schedule = s;
            this.date = date;
            this.start = s.startAt(date);
            this.end = s.endAt(date);
        }
    }

    private final List<Schedule> HAZscheduleList = new ArrayList<>();
    private final Map<String, Category> categories = new LinkedHashMap<>();
    private final List<DDay> ddays = new ArrayList<>();
    private final List<Runnable> listeners = new ArrayList<>();
    private Runnable saver = () -> { };

    public ScheduleManager() { addDefaultCategories(); }

    public void addDefaultCategories() {
        if (!categories.isEmpty()) return;
        categories.put("집", new Category("집", new Color(0xE8A0A0)));
        categories.put("개인", new Category("개인", new Color(0x8FC17A)));
        categories.put("회사,학교", new Category("회사,학교", new Color(0x7FA8DC)));
    }

    // ----- 변경 알림 -----
    public void setSaver(Runnable saver) { this.saver = saver; }
    public void addListener(Runnable r) { listeners.add(r); }

    public void changed() {
        saver.run();
        for (Runnable r : new ArrayList<>(listeners)) r.run();
    }

    // ----- 일정 -----
    public List<Schedule> getAll() { return HAZscheduleList; }

    public void add(Schedule s) {
        if (!categories.containsKey(s.category)) s.category = firstCategory();
        HAZscheduleList.add(s);
        changed();
    }

    public void remove(Schedule s) { HAZscheduleList.remove(s); changed(); }

    // 반복 일정의 이 날 한 번만 지우기
    public void skipOccurrence(Schedule s, LocalDate occ) {
        if (s.repeat == Schedule.HAZRepeat.DATES && s.extraDates.remove(occ)) { changed(); return; }
        s.skip.add(occ);
        changed();
    }

    public void update() { changed(); }

    // 수정한 복사본으로 바꿔 넣기 (목록 순서 유지)
    public void replace(Schedule original, Schedule edited) {
        int i = HAZscheduleList.indexOf(original);
        if (i >= 0) HAZscheduleList.set(i, edited); else HAZscheduleList.add(edited);
        if (!categories.containsKey(edited.category)) edited.category = firstCategory();
        changed();
    }

    // 반복 일정의 그 날만 따로 떼어서 바꾸기: 원래 일정은 그 날을 건너뛰고, 떼어낸 일정을 새로 추가
    public void detach(Schedule original, LocalDate occ, Schedule single) {
        if (!(original.repeat == Schedule.HAZRepeat.DATES && original.extraDates.remove(occ))) original.skip.add(occ);
        HAZscheduleList.add(single);
        changed();
    }

    public Schedule find(String id) {
        for (Schedule s : HAZscheduleList) if (s.id.equals(id)) return s;
        return null;
    }

    public boolean visible(Schedule s) {
        Category c = categories.get(s.category);
        return c == null || !c.hidden;
    }

    private static final Comparator<Schedule> ORDER = Comparator
            .comparing((Schedule s) -> !s.multiDay())
            .thenComparing(s -> !s.allDay())
            .thenComparingInt(s -> s.start)
            .thenComparing(s -> s.title);

    // 이 날에 걸쳐 있는 일정 (숨긴 카테고리 제외)
    public List<Schedule> getByDate(LocalDate d) { return getByDate(d, false); }

    public List<Schedule> getByDate(LocalDate d, boolean includeHidden) {
        List<Schedule> out = new ArrayList<>();
        for (Schedule s : HAZscheduleList) {
            if (!includeHidden && !visible(s)) continue;
            if (s.occursOn(d)) out.add(s);
        }
        out.sort(ORDER);
        return out;
    }

    // 이 날 '시작'하는 회차
    public List<Occurrence> startsOn(LocalDate d, boolean includeHidden) {
        List<Occurrence> out = new ArrayList<>();
        for (Schedule s : HAZscheduleList) {
            if (!includeHidden && !visible(s)) continue;
            if (s.startsOn(d)) out.add(new Occurrence(s, d));
        }
        out.sort(Comparator.comparing((Occurrence o) -> o.start).thenComparing(o -> o.schedule.title));
        return out;
    }

    // 앞으로 있을 일정 (오늘 하루 종일 일정 포함)
    public List<Occurrence> upcoming(LocalDateTime now, int days, int max) {
        List<Occurrence> out = new ArrayList<>();
        LocalDate today = now.toLocalDate();
        for (int i = 0; i <= days && out.size() < max; i++) {
            for (Occurrence o : startsOn(today.plusDays(i), false)) {
                boolean future = o.start.isAfter(now) || (o.schedule.allDay() && o.end.isAfter(now));
                if (future && out.size() < max) out.add(o);
            }
        }
        return out;
    }

    // 지난 일정 (최근 것부터)
    public List<Occurrence> past(LocalDateTime now, int days, int max) {
        List<Occurrence> out = new ArrayList<>();
        LocalDate today = now.toLocalDate();
        for (int i = 0; i <= days && out.size() < max; i++) {
            List<Occurrence> day = startsOn(today.minusDays(i), false);
            for (int j = day.size() - 1; j >= 0 && out.size() < max; j--) {
                if (!day.get(j).end.isAfter(now)) out.add(day.get(j));
            }
        }
        return out;
    }

    // 지금 진행 중이거나 다음에 시작하는, 시간이 정해진 일정 (위젯용)
    public Occurrence findNext(LocalDateTime now) {
        Occurrence best = null;
        for (Schedule s : HAZscheduleList) {
            if (s.allDay() || !visible(s)) continue;
            LocalDate d = s.nextOn(now.toLocalDate().minusDays(s.span()));
            for (int guard = 0; d != null && guard < 4; guard++) {
                Occurrence o = new Occurrence(s, d);
                if (o.end.isAfter(now)) {
                    if (best == null || o.start.isBefore(best.start)) best = o;
                    break;
                }
                d = s.nextOn(d.plusDays(1));
            }
        }
        return best;
    }

    public List<Schedule> search(String query) {
        List<Schedule> out = new ArrayList<>();
        String q = query == null ? "" : query.trim().toLowerCase();
        if (q.isEmpty()) return out;
        for (Schedule s : HAZscheduleList) {
            String all = (s.title + " " + s.category + " " + s.memo + " " + s.link).toLowerCase();
            if (all.contains(q)) out.add(s);
        }
        out.sort(Comparator.comparing((Schedule s) -> s.date).reversed());
        return out;
    }

    // 같은 날 시간이 겹치는 일정 (except = 수정 중인 자기 자신)
    public List<Schedule> findOverlaps(LocalDate d, int start, int end, String exceptId) {
        List<Schedule> out = new ArrayList<>();
        for (Schedule s : getByDate(d, true)) {
            if (s.id.equals(exceptId) || s.allDay()) continue;
            int[] r = s.rangeOn(d);
            if (r != null && start < r[1] && end > r[0]) out.add(s);
        }
        return out;
    }

    public static boolean isValidTitle(String title) {
        if (title == null || title.isBlank()) return false;
        for (char c : title.toCharArray()) {
            if (HAZ_FORBIDDEN_CHARS.indexOf(c) >= 0 || Character.isISOControl(c)) return false;
        }
        return true;
    }

    // ----- 카테고리 -----
    public Map<String, Category> getCategories() { return categories; }

    public String firstCategory() { return categories.isEmpty() ? "개인" : categories.keySet().iterator().next(); }

    public Color colorOf(String category) {
        Category c = categories.get(category);
        return c != null ? c.color : new Color(0xB0B0B0);
    }

    public Color colorOf(Schedule s) {
        if (s.color != null) {
            try { return Color.decode(s.color); } catch (Exception ignored) { }
        }
        return colorOf(s.category);
    }

    public boolean isHidden(String category) {
        Category c = categories.get(category);
        return c != null && c.hidden;
    }

    public void addCategory(String name, Color color) {
        if (name == null || name.isBlank()) return;
        name = name.trim();
        Category c = categories.get(name);
        if (c != null) c.color = color;
        else categories.put(name, new Category(name, color));
        changed();
    }

    public void setCategoryColor(String name, Color color) {
        Category c = categories.get(name);
        if (c != null) { c.color = color; changed(); }
    }

    public boolean renameCategory(String from, String to) {
        if (to == null || to.isBlank() || categories.containsKey(to.trim())) return false;
        to = to.trim();
        Map<String, Category> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Category> e : categories.entrySet()) {
            if (e.getKey().equals(from)) { e.getValue().name = to; copy.put(to, e.getValue()); }
            else copy.put(e.getKey(), e.getValue());
        }
        categories.clear();
        categories.putAll(copy);
        for (Schedule s : HAZscheduleList) if (from.equals(s.category)) s.category = to;
        changed();
        return true;
    }

    public void toggleHidden(String name) {
        Category c = categories.get(name);
        if (c != null) { c.hidden = !c.hidden; changed(); }
    }

    // 이 카테고리만 보기 (이미 그렇게 보고 있으면 모두 보기)
    public void showOnly(String name) {
        boolean already = true;
        for (Category c : categories.values()) if (c.hidden == c.name.equals(name)) already = false;
        for (Category c : categories.values()) c.hidden = !already && !c.name.equals(name);
        changed();
    }

    public void showAll() {
        for (Category c : categories.values()) c.hidden = false;
        changed();
    }

    public String fallbackFor(String name) {
        for (String c : categories.keySet()) if (!c.equals(name)) return c;
        return null;
    }

    // 지우면 그 카테고리 일정은 다른 카테고리로 옮김 (마지막 하나는 못 지움)
    public boolean removeCategory(String name) {
        String fallback = fallbackFor(name);
        if (fallback == null) return false;
        for (Schedule s : HAZscheduleList) if (name.equals(s.category)) s.category = fallback;
        categories.remove(name);
        changed();
        return true;
    }

    // ----- D-Day -----
    public List<DDay> getDDays() { return ddays; }

    public List<DDay> activeDDays() {
        List<DDay> out = new ArrayList<>();
        for (DDay d : ddays) if (d.active) out.add(d);
        // 다가오는 것(가까운 순) 먼저, 지난 것은 뒤로
        out.sort(Comparator.comparing((DDay d) -> d.daysLeft() < 0).thenComparingLong(d -> Math.abs(d.daysLeft())));
        return out;
    }

    public void addDDay(DDay d) { ddays.add(d); changed(); }
    public void removeDDay(DDay d) { ddays.remove(d); changed(); }

    // ----- 저장 (DataStore 가 부름) -----
    public void writeTo(Map<String, Object> root) {
        List<Object> list = new ArrayList<>();
        for (Schedule s : HAZscheduleList) list.add(s.toMap());
        root.put("schedules", list);
        List<Object> cats = new ArrayList<>();
        for (Category c : categories.values()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", c.name);
            m.put("color", Theme.hex(c.color));
            m.put("hidden", c.hidden);
            cats.add(m);
        }
        root.put("categories", cats);
        List<Object> dd = new ArrayList<>();
        for (DDay d : ddays) dd.add(d.toMap());
        root.put("ddays", dd);
    }

    public void readFrom(Map<String, Object> root) {
        HAZscheduleList.clear();
        categories.clear();
        ddays.clear();
        for (Map<String, Object> m : Json.maps(root, "categories")) {
            String name = Json.str(m, "name", "").trim();
            if (name.isEmpty()) continue;
            Category c = new Category(name, Theme.decode(Json.str(m, "color", "#B0B0B0"), "#B0B0B0"));
            c.hidden = Json.bool(m, "hidden", false);
            categories.put(name, c);
        }
        for (Map<String, Object> m : Json.maps(root, "schedules")) {
            try { HAZscheduleList.add(Schedule.fromMap(m)); }
            catch (Exception e) { System.err.println("일정 하나를 읽지 못했어요: " + m); }
        }
        for (Map<String, Object> m : Json.maps(root, "ddays")) {
            try { ddays.add(DDay.fromMap(m)); } catch (Exception ignored) { }
        }
        addDefaultCategories();
        ensureCategories();
    }

    // 일정에 쓰인 카테고리가 목록에 없으면 추가
    public void ensureCategories() {
        for (Schedule s : HAZscheduleList) {
            if (s.category == null || s.category.isBlank()) s.category = firstCategory();
            categories.putIfAbsent(s.category, new Category(s.category, new Color(0xC9B8E8)));
        }
    }

    // 다른 곳에서 가져온 일정 추가 (같은 id/uid 는 건너뜀). 추가한 개수
    public int importAll(List<Schedule> in) {
        java.util.Set<String> ids = new java.util.HashSet<>(), uids = new java.util.HashSet<>();
        for (Schedule s : HAZscheduleList) {
            ids.add(s.id);
            if (s.uid != null) uids.add(s.uid);
        }
        int added = 0;
        for (Schedule s : in) {
            if (ids.contains(s.id) || (s.uid != null && uids.contains(s.uid))) continue;
            ids.add(s.id);
            if (s.uid != null) uids.add(s.uid);
            HAZscheduleList.add(s);
            added++;
        }
        ensureCategories();
        changed();
        return added;
    }
}
