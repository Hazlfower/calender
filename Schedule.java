import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.Set;
import java.util.TreeSet;

// 일정 하나의 데이터 (여러 날 일정 + 다양한 반복 지원)
public class Schedule {
    public enum HAZRepeat {
        NONE("반복 없음", ""), DAILY("매일", "일"), WEEKLY("매주", "주"), MONTHLY("매월", "개월"),
        YEARLY("매년", "년"), DATES("날짜 지정", "");

        public final String label, unit;
        HAZRepeat(String label, String unit) { this.label = label; this.unit = unit; }
        @Override public String toString() { return label; }
    }

    private static final String[] DOW = {"월", "화", "수", "목", "금", "토", "일"};

    private String title;
    private LocalDate date;           // 시작 날짜
    private LocalDate endDate;        // 끝 날짜 (하루짜리면 date와 같음)
    private LocalTime start, end;     // 첫날 시작 시각 / 마지막 날 끝 시각
    private String category;

    private HAZRepeat repeat = HAZRepeat.NONE;
    private int interval = 1;                                       // N일/주/개월/년마다
    private Set<DayOfWeek> weekdays = EnumSet.noneOf(DayOfWeek.class); // 매주: 반복할 요일
    private boolean monthlyByWeekday = false;                       // 매월: n일(false) / n번째 x요일(true)
    private LocalDate until;                                        // 반복 종료일 (null = 계속)
    private Set<LocalDate> extraDates = new TreeSet<>();            // 날짜 지정 반복

    private String memo, link;
    private int alarmMinutes = -1;    // -1: 알람 없음, 0: 정각, N: N분 전

    public Schedule(String title, LocalDate date, LocalTime start, LocalTime end, String category) {
        this.title = title;
        this.date = date;
        this.endDate = date;
        this.start = start;
        this.end = end;
        this.category = category;
    }

    public int spanDays() { return (int) Math.max(0, ChronoUnit.DAYS.between(date, endDate)); }
    public boolean isMultiDay() { return spanDays() > 0; }

    // 이 날짜에 일정이 "시작"하는지 (반복 규칙 적용)
    public boolean startsOn(LocalDate day) {
        if (day.isBefore(date)) return false;
        if (until != null && day.isAfter(until) && repeat != HAZRepeat.DATES) return false;
        int n = Math.max(1, interval);
        switch (repeat) {
            case DAILY:
                return ChronoUnit.DAYS.between(date, day) % n == 0;
            case WEEKLY: {
                Set<DayOfWeek> days = weekdays.isEmpty() ? EnumSet.of(date.getDayOfWeek()) : weekdays;
                long weeks = ChronoUnit.WEEKS.between(weekStart(date), weekStart(day));
                return weeks % n == 0 && days.contains(day.getDayOfWeek());
            }
            case MONTHLY: {
                long months = ChronoUnit.MONTHS.between(YearMonth.from(date), YearMonth.from(day));
                if (months % n != 0) return false;
                if (monthlyByWeekday) return day.getDayOfWeek() == date.getDayOfWeek() && nthWeek(day) == nthWeek(date);
                return day.getDayOfMonth() == date.getDayOfMonth();
            }
            case YEARLY: {
                long years = day.getYear() - date.getYear();
                return years % n == 0 && day.getMonth() == date.getMonth() && day.getDayOfMonth() == date.getDayOfMonth();
            }
            case DATES:
                return day.equals(date) || extraDates.contains(day);
            default:
                return day.equals(date);
        }
    }

    // 이 날짜에 걸쳐 있는지 (여러 날 일정이면 중간 날짜도 true)
    public boolean occursOn(LocalDate day) { return dayIndexOn(day) >= 0; }

    // 이 날짜가 일정의 몇 번째 날인지 (-1 = 해당 없음)
    public int dayIndexOn(LocalDate day) {
        for (int k = 0; k <= spanDays(); k++) if (startsOn(day.minusDays(k))) return k;
        return -1;
    }

    // 이 날짜에서 차지하는 시간대 [시작, 끝]
    public LocalTime[] timeRangeOn(LocalDate day) {
        int k = dayIndexOn(day);
        if (k < 0) return null;
        return new LocalTime[]{k == 0 ? start : LocalTime.MIN, k == spanDays() ? end : LocalTime.of(23, 59)};
    }

    public boolean overlaps(LocalDate day, LocalTime otherStart, LocalTime otherEnd) {
        LocalTime[] r = timeRangeOn(day);
        return r != null && otherStart.isBefore(r[1]) && otherEnd.isAfter(r[0]);
    }

    public LocalDateTime startOn(LocalDate day) { return day.atTime(start); }

    private static LocalDate weekStart(LocalDate d) { return d.minusDays(d.getDayOfWeek().getValue() % 7); }
    public static int nthWeek(LocalDate d) { return (d.getDayOfMonth() - 1) / 7 + 1; }
    public static String dowName(DayOfWeek d) { return DOW[d.getValue() - 1]; }

    // "매주 월·수 · 2주마다 · ~12/31" 같은 설명
    public String repeatSummary() {
        if (repeat == HAZRepeat.NONE) return "";
        StringBuilder sb = new StringBuilder();
        if (repeat == HAZRepeat.DATES) {
            sb.append("지정한 날짜 ").append(extraDates.size() + 1).append("일");
            return sb.toString();
        }
        sb.append(interval > 1 ? interval + repeat.unit + "마다" : repeat.label);
        if (repeat == HAZRepeat.WEEKLY) {
            Set<DayOfWeek> days = weekdays.isEmpty() ? EnumSet.of(date.getDayOfWeek()) : weekdays;
            StringBuilder d = new StringBuilder();
            for (DayOfWeek w : days) d.append(d.length() > 0 ? "·" : "").append(dowName(w));
            sb.append(" ").append(d);
        } else if (repeat == HAZRepeat.MONTHLY) {
            sb.append(monthlyByWeekday ? " " + nthWeek(date) + "번째 " + dowName(date.getDayOfWeek()) + "요일"
                                       : " " + date.getDayOfMonth() + "일");
        }
        if (until != null) sb.append(" (~").append(until).append(")");
        return sb.toString();
    }

    // ----- getter / setter -----
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate == null || endDate.isBefore(date) ? date : endDate; }
    public LocalTime getStart() { return start; }
    public void setStart(LocalTime start) { this.start = start; }
    public LocalTime getEnd() { return end; }
    public void setEnd(LocalTime end) { this.end = end; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public HAZRepeat getRepeat() { return repeat; }
    public void setRepeat(HAZRepeat repeat) { this.repeat = repeat; }
    public int getInterval() { return interval; }
    public void setInterval(int interval) { this.interval = Math.max(1, interval); }
    public Set<DayOfWeek> getWeekdays() { return weekdays; }
    public void setWeekdays(Set<DayOfWeek> weekdays) { this.weekdays = weekdays.isEmpty() ? EnumSet.noneOf(DayOfWeek.class) : EnumSet.copyOf(weekdays); }
    public boolean isMonthlyByWeekday() { return monthlyByWeekday; }
    public void setMonthlyByWeekday(boolean v) { this.monthlyByWeekday = v; }
    public LocalDate getUntil() { return until; }
    public void setUntil(LocalDate until) { this.until = until; }
    public Set<LocalDate> getExtraDates() { return extraDates; }
    public void setExtraDates(Set<LocalDate> dates) { this.extraDates = new TreeSet<>(dates); }
    public String getMemo() { return memo; }
    public void setMemo(String memo) { this.memo = memo; }
    public String getLink() { return link; }
    public void setLink(String link) { this.link = link; }
    public int getAlarmMinutes() { return alarmMinutes; }
    public void setAlarmMinutes(int alarmMinutes) { this.alarmMinutes = alarmMinutes; }
    public boolean hasAlarm() { return alarmMinutes >= 0; }

    public String timeText() {
        if (isMultiDay()) return date.getMonthValue() + "/" + date.getDayOfMonth() + " " + start + " ~ "
                + endDate.getMonthValue() + "/" + endDate.getDayOfMonth() + " " + end;
        return start + " ~ " + end;
    }

    @Override
    public String toString() {
        String r = repeatSummary();
        return timeText() + "   " + title + (r.isEmpty() ? "" : "   (" + r + ")") + (hasAlarm() ? "   [알람]" : "");
    }
}
