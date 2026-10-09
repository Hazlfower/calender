import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Locale;

// 시간 입력 해석 + 날짜/시간을 글자로 보여 주기
public final class TimeText {
    private TimeText() { }

    private static final String[] DOW = {"월", "화", "수", "목", "금", "토", "일"};

    // "오후 6:00", "18:00", "6시 30분", "6시 반", "1830", "6:30pm", "저녁 7시" → 하루 중 분(0~1440). 모르면 -1
    public static int parse(String text) {
        if (text == null) return -1;
        String t = text.trim().toLowerCase(Locale.ROOT).replace(" ", "");
        if (t.isEmpty()) return -1;
        if (t.equals("정오")) return 12 * 60;
        if (t.equals("자정")) return 0;
        boolean pm = t.contains("오후") || t.contains("pm") || t.contains("저녁") || t.contains("밤");
        boolean am = t.contains("오전") || t.contains("am") || t.contains("새벽") || t.contains("아침");
        boolean half = t.contains("반");
        t = t.replace("분", "").replaceAll("[^0-9:시]", "").replace("시", ":");
        try {
            int h, m = 0;
            if (t.contains(":")) {
                String[] p = t.split(":", -1);
                if (p[0].isEmpty()) return -1;
                h = Integer.parseInt(p[0]);
                if (p.length > 1 && !p[1].isEmpty()) m = Integer.parseInt(p[1]);
            } else if (t.length() >= 3) {
                h = Integer.parseInt(t.substring(0, t.length() - 2));
                m = Integer.parseInt(t.substring(t.length() - 2));
            } else {
                h = Integer.parseInt(t);
            }
            if (half) m = 30;
            if (pm && h < 12) h += 12;
            if (am && h == 12) h = 0;
            if (h < 0 || h > 24 || m < 0 || m > 59 || (h == 24 && m > 0)) return -1;
            return h * 60 + m;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    public static String fmt(int min) { return String.format("%02d:%02d", min / 60, min % 60); }

    public static String ampm(int min) {
        int h = min / 60, m = min % 60;
        String ap = (h < 12 || h == 24) ? "오전" : "오후";
        int hh = h % 12 == 0 ? 12 : h % 12;
        return m == 0 ? ap + " " + hh + "시" : String.format("%s %d:%02d", ap, hh, m);
    }

    public static String dow(LocalDate d) { return dow(d.getDayOfWeek()); }

    public static String dow(DayOfWeek d) { return DOW[d.getValue() - 1]; }

    public static String md(LocalDate d) { return d.getMonthValue() + "월 " + d.getDayOfMonth() + "일 (" + dow(d) + ")"; }

    public static String shortMd(LocalDate d) { return d.getMonthValue() + "/" + d.getDayOfMonth(); }

    public static String ymd(LocalDate d) { return d.getYear() + "년 " + md(d); }

    // 오늘 / 내일 / 모레 / 10월 12일 (월)
    public static String relDay(LocalDate d) {
        LocalDate today = LocalDate.now();
        if (d.equals(today)) return "오늘";
        if (d.equals(today.plusDays(1))) return "내일";
        if (d.equals(today.plusDays(2))) return "모레";
        if (d.equals(today.minusDays(1))) return "어제";
        return md(d);
    }

    // 15분 / 2시간 5분 / 3일 4시간
    public static String duration(long minutes) {
        if (minutes < 60) return Math.max(0, minutes) + "분";
        long h = minutes / 60, m = minutes % 60;
        if (h < 24) return m == 0 ? h + "시간" : h + "시간 " + m + "분";
        long d = h / 24;
        return (h % 24 == 0) ? d + "일" : d + "일 " + (h % 24) + "시간";
    }

    // 알람 시점: 정각 / 10분 전 / 1시간 전 / 하루 전
    public static String before(int minutes) {
        if (minutes <= 0) return "정각";
        if (minutes == 1440) return "하루 전";
        if (minutes < 60) return minutes + "분 전";
        int h = minutes / 60, m = minutes % 60;
        if (h >= 24 && m == 0 && h % 24 == 0) return (h / 24) + "일 전";
        return m == 0 ? h + "시간 전" : h + "시간 " + m + "분 전";
    }
}
