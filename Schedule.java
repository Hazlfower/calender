import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.TreeSet;
import java.util.UUID;

// 일정 하나
// - 시간은 하루 중 '분'(0~1440)으로 저장. start 가 -1 이면 하루 종일
// - endDate 가 있으면 여러 날 일정 (start 는 첫날, end 는 마지막 날 기준)
// - 반복: 매일/매주/매월/매년(N마다, 요일, n번째 요일, 종료일) 또는 날짜 직접 지정
public class Schedule {
    public enum HAZRepeat {
        NONE("반복 안 함", ""), DAILY("매일", "일"), WEEKLY("매주", "주"), MONTHLY("매월", "개월"),
        YEARLY("매년", "년"), DATES("날짜 지정", "");

        public final String label, unit;
        HAZRepeat(String label, String unit) { this.label = label; this.unit = unit; }
        @Override public String toString() { return label; }

        static HAZRepeat of(String s) {
            try { return valueOf(s); } catch (Exception e) { return NONE; }
        }
    }

    public String id = UUID.randomUUID().toString();
    public String title = "";
    public LocalDate date = LocalDate.now();
    public LocalDate endDate;                    // null = 하루짜리
    public int start = -1, end = -1;             // 분 (-1 = 하루 종일)
    public String category = "개인";
    public String color;                         // null = 카테고리 색
    public HAZRepeat repeat = HAZRepeat.NONE;
    public int interval = 1;                     // N일/주/개월/년마다
    public Set<DayOfWeek> weekdays = EnumSet.noneOf(DayOfWeek.class);
    public boolean monthlyNth;                   // 매월: true = n번째 x요일, false = n일
    public LocalDate until;                      // 반복 종료일 (null = 계속)
    public TreeSet<LocalDate> extraDates = new TreeSet<>();   // 날짜 지정 반복
    public TreeSet<LocalDate> skip = new TreeSet<>();         // '이 날만 삭제'한 날
    public String memo = "";
    public String link = "";
    public List<Integer> alarms = new ArrayList<>();          // 몇 분 전에 알릴지 (0 = 정각)
    public String uid;                                        // .ics 원래 UID (중복 가져오기 방지)

    public boolean allDay() { return start < 0; }

    public int span() { return endDate == null ? 0 : (int) Math.max(0, ChronoUnit.DAYS.between(date, endDate)); }

    public boolean multiDay() { return span() > 0; }

    public boolean hasAlarm() { return !alarms.isEmpty() && !allDay(); }

    public static LocalDate weekStart(LocalDate d) { return d.minusDays(d.getDayOfWeek().getValue() % 7); }

    public static int nth(LocalDate d) { return (d.getDayOfMonth() - 1) / 7 + 1; }

    // 이 날에 일정이 '시작'하는지 (반복 규칙 적용)
    public boolean startsOn(LocalDate d) {
        if (skip.contains(d)) return false;
        if (repeat == HAZRepeat.DATES) return d.equals(date) || extraDates.contains(d);
        if (d.isBefore(date)) return false;
        if (repeat != HAZRepeat.NONE && until != null && d.isAfter(until)) return false;
        int n = Math.max(1, interval);
        switch (repeat) {
            case DAILY:
                return ChronoUnit.DAYS.between(date, d) % n == 0;
            case WEEKLY: {
                Set<DayOfWeek> days = weekdays.isEmpty() ? EnumSet.of(date.getDayOfWeek()) : weekdays;
                if (!days.contains(d.getDayOfWeek())) return false;
                return ChronoUnit.WEEKS.between(weekStart(date), weekStart(d)) % n == 0;
            }
            case MONTHLY: {
                if (ChronoUnit.MONTHS.between(YearMonth.from(date), YearMonth.from(d)) % n != 0) return false;
                if (monthlyNth) return d.getDayOfWeek() == date.getDayOfWeek() && nth(d) == nth(date);
                return d.getDayOfMonth() == date.getDayOfMonth();
            }
            case YEARLY:
                return (d.getYear() - date.getYear()) % n == 0
                        && d.getMonth() == date.getMonth() && d.getDayOfMonth() == date.getDayOfMonth();
            default:
                return d.equals(date);
        }
    }

    // d 를 포함하는 회차의 시작일 (없으면 null)
    public LocalDate occurrenceStart(LocalDate d) {
        int span = span();
        for (int k = 0; k <= span; k++) {
            LocalDate s = d.minusDays(k);
            if (startsOn(s)) return s;
        }
        return null;
    }

    public boolean occursOn(LocalDate d) { return occurrenceStart(d) != null; }

    // 이 날 차지하는 시간 [시작분, 끝분] (하루 종일이면 0~1440). 해당 없으면 null
    public int[] rangeOn(LocalDate d) {
        LocalDate occ = occurrenceStart(d);
        if (occ == null) return null;
        if (allDay()) return new int[]{0, 1440};
        int k = (int) ChronoUnit.DAYS.between(occ, d);
        return new int[]{k == 0 ? start : 0, k == span() ? end : 1440};
    }

    // from(포함) 이후 첫 시작일 (없으면 null)
    public LocalDate nextOn(LocalDate from) {
        LocalDate x = from.isBefore(date) && repeat != HAZRepeat.DATES ? date : from;
        if (repeat == HAZRepeat.NONE) return startsOn(date) && !date.isBefore(from) ? date : null;
        if (repeat == HAZRepeat.DATES) {
            TreeSet<LocalDate> all = new TreeSet<>(extraDates);
            all.add(date);
            for (LocalDate d : all.tailSet(from, true)) if (!skip.contains(d)) return d;
            return null;
        }
        for (int k = 0; k < 4000; k++, x = x.plusDays(1)) {
            if (until != null && x.isAfter(until)) return null;
            if (startsOn(x)) return x;
        }
        return null;
    }

    public LocalDateTime startAt(LocalDate occ) { return occ.atStartOfDay().plusMinutes(allDay() ? 0 : start); }

    public LocalDateTime endAt(LocalDate occ) {
        LocalDate last = occ.plusDays(span());
        return allDay() ? last.plusDays(1).atStartOfDay() : last.atStartOfDay().plusMinutes(end);
    }

    public String timeText() {
        if (allDay()) return multiDay() ? TimeText.shortMd(date) + " – " + TimeText.shortMd(endDate) + " 하루 종일" : "하루 종일";
        if (multiDay()) return TimeText.shortMd(date) + " " + TimeText.fmt(start) + " – " + TimeText.shortMd(endDate) + " " + TimeText.fmt(end);
        return TimeText.fmt(start) + " – " + TimeText.fmt(end);
    }

    public static String nthText(int n) {
        switch (n) {
            case 1: return "첫 번째";
            case 2: return "두 번째";
            case 3: return "세 번째";
            case 4: return "네 번째";
            default: return "다섯 번째";
        }
    }

    // "매주 월·수·금", "2주마다 화", "매월 첫 번째 수요일 · 2026.12.31까지" ...
    public String repeatText() {
        if (repeat == HAZRepeat.NONE) return "";
        if (repeat == HAZRepeat.DATES) return "지정한 날짜 " + (extraDates.size() + 1) + "번";
        int n = Math.max(1, interval);
        String head = n == 1 ? repeat.label : n + repeat.unit + "마다";
        String body = "";
        if (repeat == HAZRepeat.WEEKLY) {
            Set<DayOfWeek> days = weekdays.isEmpty() ? EnumSet.of(date.getDayOfWeek()) : weekdays;
            StringJoiner j = new StringJoiner("·");
            for (DayOfWeek d : sortedSunFirst(days)) j.add(TimeText.dow(d));
            body = " " + j;
        } else if (repeat == HAZRepeat.MONTHLY) {
            body = monthlyNth ? " " + nthText(nth(date)) + " " + TimeText.dow(date) + "요일" : " " + date.getDayOfMonth() + "일";
        } else if (repeat == HAZRepeat.YEARLY) {
            body = " " + date.getMonthValue() + "월 " + date.getDayOfMonth() + "일";
        }
        String tail = until == null ? "" : " · " + until.getYear() + "." + until.getMonthValue() + "." + until.getDayOfMonth() + "까지";
        return head + body + tail;
    }

    public static List<DayOfWeek> sortedSunFirst(Collection<DayOfWeek> days) {
        List<DayOfWeek> l = new ArrayList<>(days);
        l.sort(Comparator.comparingInt(d -> d.getValue() % 7));
        return l;
    }

    public String alarmText() {
        if (!hasAlarm()) return "";
        StringJoiner j = new StringJoiner(", ");
        for (int a : alarms) j.add(TimeText.before(a));
        return j.toString();
    }

    public void setAlarms(Collection<Integer> list) {
        TreeSet<Integer> sorted = new TreeSet<>(Comparator.reverseOrder());
        for (Integer a : list) if (a != null && a >= 0) sorted.add(a);
        alarms = new ArrayList<>(sorted);
    }

    public Schedule copy() {
        Schedule c = new Schedule();
        c.id = id; c.title = title; c.date = date; c.endDate = endDate; c.start = start; c.end = end;
        c.category = category; c.color = color; c.repeat = repeat; c.interval = interval;
        c.weekdays = weekdays.isEmpty() ? EnumSet.noneOf(DayOfWeek.class) : EnumSet.copyOf(weekdays);
        c.monthlyNth = monthlyNth; c.until = until;
        c.extraDates = new TreeSet<>(extraDates); c.skip = new TreeSet<>(skip);
        c.memo = memo; c.link = link; c.alarms = new ArrayList<>(alarms); c.uid = uid;
        return c;
    }

    @Override public String toString() { return title; }

    // ----- 저장 -----
    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("title", title);
        m.put("date", date.toString());
        if (endDate != null && endDate.isAfter(date)) m.put("endDate", endDate.toString());
        m.put("start", start);
        m.put("end", end);
        m.put("category", category);
        if (color != null) m.put("color", color);
        m.put("repeat", repeat.name());
        if (interval != 1) m.put("interval", interval);
        if (!weekdays.isEmpty()) {
            List<String> l = new ArrayList<>();
            for (DayOfWeek d : weekdays) l.add(d.name());
            m.put("weekdays", l);
        }
        if (monthlyNth) m.put("monthlyNth", true);
        if (until != null) m.put("until", until.toString());
        if (!extraDates.isEmpty()) m.put("dates", dateList(extraDates));
        if (!skip.isEmpty()) m.put("skip", dateList(skip));
        m.put("memo", memo);
        if (!link.isEmpty()) m.put("link", link);
        m.put("alarms", new ArrayList<>(alarms));
        if (uid != null) m.put("uid", uid);
        return m;
    }

    private static List<String> dateList(Collection<LocalDate> ds) {
        List<String> l = new ArrayList<>();
        for (LocalDate d : ds) l.add(d.toString());
        return l;
    }

    public static Schedule fromMap(Map<String, Object> m) {
        Schedule s = new Schedule();
        s.id = Json.str(m, "id", s.id);
        s.title = Json.str(m, "title", "");
        s.date = LocalDate.parse(Json.str(m, "date", LocalDate.now().toString()));
        String e = Json.str(m, "endDate", null);
        s.endDate = e == null ? null : LocalDate.parse(e);
        if (s.endDate != null && !s.endDate.isAfter(s.date)) s.endDate = null;
        s.start = Json.num(m, "start", -1);
        s.end = Json.num(m, "end", -1);
        if (s.start >= 0 && s.end < 0) s.end = Math.min(1440, s.start + 60);
        s.category = Json.str(m, "category", Json.str(m, "type", "개인"));
        s.color = Json.str(m, "color", null);
        s.repeat = HAZRepeat.of(Json.str(m, "repeat", "NONE"));
        s.interval = Math.max(1, Json.num(m, "interval", 1));
        for (String d : Json.strs(m, "weekdays")) {
            try { s.weekdays.add(DayOfWeek.valueOf(d)); } catch (Exception ignored) { }
        }
        s.monthlyNth = Json.bool(m, "monthlyNth", false);
        String u = Json.str(m, "until", null);
        s.until = u == null ? null : LocalDate.parse(u);
        for (String d : Json.strs(m, "dates")) s.extraDates.add(LocalDate.parse(d));
        for (String d : Json.strs(m, "skip")) s.skip.add(LocalDate.parse(d));
        s.memo = Json.str(m, "memo", "");
        s.link = Json.str(m, "link", "");
        s.setAlarms(Json.ints(m, "alarms"));
        s.uid = Json.str(m, "uid", null);
        return s;
    }
}
