import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// .ics(iCalendar) 파일 가져오기/내보내기
// 구글 캘린더 → 설정 → 가져오기/내보내기에서 받은 파일을 인터넷 없이 읽을 수 있고,
// 내보낸 파일은 구글 캘린더 등에 그대로 가져갈 수 있어요.
public final class Ics {
    private Ics() { }

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");
    private static final Pattern TZID = Pattern.compile("TZID=\"?([^;:\"]+)", Pattern.CASE_INSENSITIVE);

    // ================================================================== 가져오기

    public static List<Schedule> read(Path p) throws IOException {
        List<String> lines = new ArrayList<>();
        for (String raw : Files.readAllLines(p, StandardCharsets.UTF_8)) {
            if ((raw.startsWith(" ") || raw.startsWith("\t")) && !lines.isEmpty())
                lines.set(lines.size() - 1, lines.get(lines.size() - 1) + raw.substring(1));
            else lines.add(raw);
        }
        List<Schedule> out = new ArrayList<>();
        Schedule cur = null;
        LocalDateTime st = null, en = null;
        LocalDate sd = null, ed = null;
        String rrule = null;
        List<LocalDate> exdates = new ArrayList<>();
        boolean inAlarm = false;
        for (String line : lines) {
            String u = line.trim().toUpperCase(Locale.ROOT);
            if (u.equals("BEGIN:VEVENT")) {
                cur = new Schedule();
                cur.title = "";
                st = en = null;
                sd = ed = null;
                rrule = null;
                exdates.clear();
                continue;
            }
            if (u.equals("BEGIN:VALARM")) { inAlarm = true; continue; }
            if (u.equals("END:VALARM")) { inAlarm = false; continue; }
            if (cur == null || inAlarm) continue;
            if (u.equals("END:VEVENT")) {
                if (finish(cur, st, en, sd, ed, rrule)) {
                    cur.skip.addAll(exdates);
                    out.add(cur);
                }
                cur = null;
                continue;
            }
            int c = line.indexOf(':');
            if (c < 0) continue;
            String keyRaw = line.substring(0, c), val = line.substring(c + 1);
            String key = keyRaw.toUpperCase(Locale.ROOT);
            String name = key.contains(";") ? key.substring(0, key.indexOf(';')) : key;
            try {
                switch (name) {
                    case "SUMMARY": cur.title = sanitize(unescape(val)); break;
                    case "DESCRIPTION": {
                        String d = unescape(val);
                        cur.memo = d.length() > 500 ? d.substring(0, 500) + "…" : d;
                        break;
                    }
                    case "URL": cur.link = val.trim(); break;
                    case "UID": cur.uid = val.trim(); break;
                    case "DTSTART": {
                        Object o = dt(keyRaw, val);
                        if (o instanceof LocalDate) sd = (LocalDate) o; else st = (LocalDateTime) o;
                        break;
                    }
                    case "DTEND": {
                        Object o = dt(keyRaw, val);
                        if (o instanceof LocalDateTime) en = (LocalDateTime) o; else if (o instanceof LocalDate) ed = (LocalDate) o;
                        break;
                    }
                    case "RRULE": rrule = val.toUpperCase(Locale.ROOT); break;
                    case "EXDATE":
                        for (String v : val.split(",")) {
                            Object o = dt(keyRaw, v);
                            exdates.add(o instanceof LocalDate ? (LocalDate) o : ((LocalDateTime) o).toLocalDate());
                        }
                        break;
                    default:
                }
            } catch (RuntimeException ignored) {
                // 읽을 수 없는 줄은 건너뜀
            }
        }
        return out;
    }

    private static boolean finish(Schedule s, LocalDateTime st, LocalDateTime en, LocalDate sd, LocalDate ed, String rrule) {
        if (s.title.isBlank()) return false;
        if (sd != null) {
            s.date = sd;
            s.start = s.end = -1;
            // 하루 종일 일정의 DTEND 는 '다음 날'로 적혀 있음
            if (ed != null && ed.minusDays(1).isAfter(sd)) s.endDate = ed.minusDays(1);
        } else if (st != null) {
            s.date = st.toLocalDate();
            s.start = st.getHour() * 60 + st.getMinute();
            if (en == null) s.end = Math.min(s.start + 60, 1440);
            else {
                LocalDate endDay = en.toLocalDate();
                int endMin = en.getHour() * 60 + en.getMinute();
                if (endMin == 0 && endDay.isAfter(s.date)) { endDay = endDay.minusDays(1); endMin = 1440; }
                if (endDay.isAfter(s.date)) s.endDate = endDay;
                s.end = endMin;
            }
            if (!s.multiDay() && s.end <= s.start) s.end = Math.min(s.start + 30, 1440);
        } else return false;
        if (rrule != null) applyRule(s, rrule);
        return true;
    }

    private static void applyRule(Schedule s, String rule) {
        Map<String, String> m = new HashMap<>();
        for (String part : rule.split(";")) {
            int e = part.indexOf('=');
            if (e > 0) m.put(part.substring(0, e), part.substring(e + 1));
        }
        String byday = m.getOrDefault("BYDAY", "");
        try { s.interval = Math.max(1, Integer.parseInt(m.getOrDefault("INTERVAL", "1"))); }
        catch (NumberFormatException e) { s.interval = 1; }
        switch (m.getOrDefault("FREQ", "")) {
            case "DAILY": s.repeat = Schedule.HAZRepeat.DAILY; break;
            case "WEEKLY": s.repeat = Schedule.HAZRepeat.WEEKLY; break;
            case "MONTHLY": s.repeat = Schedule.HAZRepeat.MONTHLY; break;
            case "YEARLY": s.repeat = Schedule.HAZRepeat.YEARLY; break;
            default: s.repeat = Schedule.HAZRepeat.NONE;
        }
        if (s.repeat == Schedule.HAZRepeat.WEEKLY && !byday.isEmpty()) {
            for (String p : byday.split(",")) {
                DayOfWeek d = dow(p.replaceAll("[^A-Z]", ""));
                if (d != null) s.weekdays.add(d);
            }
        } else if (s.repeat == Schedule.HAZRepeat.MONTHLY && !byday.isEmpty()) {
            // 예) 2WE = 두 번째 수요일. 시작일이 그 규칙에 맞을 때만 'n번째 요일' 반복으로
            String num = byday.replaceAll("[^0-9-]", "");
            DayOfWeek d = dow(byday.replaceAll("[^A-Z]", ""));
            if (byday.contains(",") || d == null || num.isEmpty() || num.startsWith("-")
                    || s.date.getDayOfWeek() != d || Schedule.nth(s.date) != Integer.parseInt(num)) {
                s.repeat = Schedule.HAZRepeat.NONE;
                s.interval = 1;
                return;
            }
            s.monthlyNth = true;
        } else if (!byday.isEmpty()) {
            s.repeat = Schedule.HAZRepeat.NONE;   // 표현할 수 없는 규칙은 첫 일정만
        }
        if (s.repeat == Schedule.HAZRepeat.NONE) { s.interval = 1; return; }
        if (m.containsKey("UNTIL")) {
            s.until = LocalDate.parse(m.get("UNTIL").substring(0, 8), DateTimeFormatter.BASIC_ISO_DATE);
        } else if (m.containsKey("COUNT")) {
            int count = Math.max(1, Integer.parseInt(m.get("COUNT")));
            LocalDate d = s.date;
            for (int found = 0, guard = 0; guard < 20000; guard++, d = d.plusDays(1)) {
                if (s.startsOn(d) && ++found == count) { s.until = d; break; }
            }
        }
    }

    private static DayOfWeek dow(String c) {
        switch (c) {
            case "MO": return DayOfWeek.MONDAY;
            case "TU": return DayOfWeek.TUESDAY;
            case "WE": return DayOfWeek.WEDNESDAY;
            case "TH": return DayOfWeek.THURSDAY;
            case "FR": return DayOfWeek.FRIDAY;
            case "SA": return DayOfWeek.SATURDAY;
            case "SU": return DayOfWeek.SUNDAY;
            default: return null;
        }
    }

    private static Object dt(String keyRaw, String val) {
        val = val.trim();
        if (val.length() == 8) return LocalDate.parse(val, DateTimeFormatter.BASIC_ISO_DATE);
        boolean utc = val.endsWith("Z") || val.endsWith("z");
        if (utc) val = val.substring(0, val.length() - 1);
        LocalDateTime t = LocalDateTime.parse(val.length() > 15 ? val.substring(0, 15) : val, DT);
        ZoneId from = null;
        if (utc) from = ZoneOffset.UTC;
        else {
            Matcher mt = TZID.matcher(keyRaw);
            if (mt.find()) {
                try { from = ZoneId.of(mt.group(1)); } catch (Exception ignored) { }
            }
        }
        if (from != null) t = t.atZone(from).withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
        return t;
    }

    private static String unescape(String v) {
        return v.replace("\\n", "\n").replace("\\N", "\n").replace("\\,", ",").replace("\\;", ";").replace("\\\\", "\\").trim();
    }

    // 일정 이름에 쓸 수 없는 문자는 비슷하게 생긴 전각 문자로
    public static String sanitize(String s) {
        String from = ScheduleManager.HAZ_FORBIDDEN_CHARS, to = "＼／：＊？＂＜＞｜";
        StringBuilder b = new StringBuilder();
        for (char c : s.toCharArray()) {
            int i = from.indexOf(c);
            if (i >= 0) b.append(to.charAt(i));
            else if (!Character.isISOControl(c)) b.append(c);
        }
        String r = b.toString().trim();
        return r.length() > 60 ? r.substring(0, 60) : r;
    }

    // ================================================================== 내보내기

    private static final String[] BYDAY = {"MO", "TU", "WE", "TH", "FR", "SA", "SU"};

    public static int write(Path p, List<Schedule> schedules, Map<String, ScheduleManager.Category> cats) throws IOException {
        StringBuilder b = new StringBuilder();
        b.append("BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//HazCalendar//KO\r\nCALSCALE:GREGORIAN\r\n");
        String stamp = LocalDateTime.now(ZoneOffset.UTC).format(DT) + "Z";
        int n = 0;
        for (Schedule s : schedules) {
            List<LocalDate> starts = new ArrayList<>();
            starts.add(s.date);
            if (s.repeat == Schedule.HAZRepeat.DATES) starts.addAll(s.extraDates);   // 날짜 지정은 각각 하나씩
            for (LocalDate d : starts) {
                if (s.skip.contains(d)) continue;
                b.append("BEGIN:VEVENT\r\n");
                line(b, "UID", (s.uid != null && starts.size() == 1 ? s.uid : s.id + (d.equals(s.date) ? "" : "-" + d)) + "@hazcalendar");
                line(b, "DTSTAMP", stamp);
                if (s.allDay()) {
                    line(b, "DTSTART;VALUE=DATE", d.format(DateTimeFormatter.BASIC_ISO_DATE));
                    line(b, "DTEND;VALUE=DATE", d.plusDays(s.span() + 1).format(DateTimeFormatter.BASIC_ISO_DATE));
                } else {
                    line(b, "DTSTART", s.startAt(d).format(DT));
                    line(b, "DTEND", s.endAt(d).format(DT));
                }
                line(b, "SUMMARY", escape(s.title));
                if (!s.memo.isBlank()) line(b, "DESCRIPTION", escape(s.memo));
                if (!s.link.isBlank()) line(b, "URL", s.link);
                line(b, "CATEGORIES", escape(s.category));
                String rule = rrule(s);
                if (rule != null && starts.size() == 1) {
                    line(b, "RRULE", rule);
                    if (!s.skip.isEmpty()) {
                        StringJoiner j = new StringJoiner(",");
                        for (LocalDate x : s.skip) j.add(s.allDay() ? x.format(DateTimeFormatter.BASIC_ISO_DATE) : s.startAt(x).format(DT));
                        line(b, s.allDay() ? "EXDATE;VALUE=DATE" : "EXDATE", j.toString());
                    }
                }
                for (int a : s.alarms) {
                    if (s.allDay()) break;
                    b.append("BEGIN:VALARM\r\nACTION:DISPLAY\r\n");
                    line(b, "DESCRIPTION", escape(s.title));
                    line(b, "TRIGGER", a == 0 ? "PT0M" : "-PT" + a + "M");
                    b.append("END:VALARM\r\n");
                }
                b.append("END:VEVENT\r\n");
                n++;
            }
        }
        b.append("END:VCALENDAR\r\n");
        Files.writeString(p, b.toString(), StandardCharsets.UTF_8);
        return n;
    }

    private static String rrule(Schedule s) {
        String freq;
        switch (s.repeat) {
            case DAILY: freq = "DAILY"; break;
            case WEEKLY: freq = "WEEKLY"; break;
            case MONTHLY: freq = "MONTHLY"; break;
            case YEARLY: freq = "YEARLY"; break;
            default: return null;
        }
        StringBuilder r = new StringBuilder("FREQ=" + freq);
        if (s.interval > 1) r.append(";INTERVAL=").append(s.interval);
        if (s.repeat == Schedule.HAZRepeat.WEEKLY) {
            StringJoiner j = new StringJoiner(",");
            for (DayOfWeek d : (s.weekdays.isEmpty() ? List.of(s.date.getDayOfWeek()) : Schedule.sortedSunFirst(s.weekdays)))
                j.add(BYDAY[d.getValue() - 1]);
            r.append(";BYDAY=").append(j);
        }
        if (s.repeat == Schedule.HAZRepeat.MONTHLY && s.monthlyNth)
            r.append(";BYDAY=").append(Schedule.nth(s.date)).append(BYDAY[s.date.getDayOfWeek().getValue() - 1]);
        if (s.until != null) r.append(";UNTIL=").append(s.until.format(DateTimeFormatter.BASIC_ISO_DATE)).append("T235959");
        return r.toString();
    }

    private static String escape(String v) {
        return v.replace("\\", "\\\\").replace("\n", "\\n").replace(",", "\\,").replace(";", "\\;");
    }

    // 75바이트가 넘는 줄은 접어서 씀 (iCalendar 규칙)
    private static void line(StringBuilder b, String key, String value) {
        String full = key + ":" + value;
        int count = 0;
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < full.length(); ) {
            int cp = full.codePointAt(i);
            String ch = new String(Character.toChars(cp));
            int bytes = ch.getBytes(StandardCharsets.UTF_8).length;
            if (count + bytes > 73) {
                b.append(cur).append("\r\n ");
                cur.setLength(0);
                count = 1;
            }
            cur.append(ch);
            count += bytes;
            i += Character.charCount(cp);
        }
        b.append(cur).append("\r\n");
    }
}
