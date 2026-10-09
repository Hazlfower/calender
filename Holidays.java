import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

// 대한민국 공휴일
// - 양력 공휴일은 계산, 음력 공휴일(설날·부처님오신날·추석)은 아래 표에서 가져옴 (한국천문연구원 음력 기준)
// - 대체공휴일 규칙 적용 (설·추석: 일요일이나 다른 공휴일과 겹치면 / 어린이날: 주말이나 다른 공휴일과 겹치면 / 나머지: 주말이면)
// - 2026년부터 노동절(5/1)·제헌절(7/17)도 공휴일
// 새 연도를 넣으려면 LUNAR 표에 한 줄을 추가하면 돼요.
public final class Holidays {
    private Holidays() { }

    private static final int FIRST_YEAR = 2020, LAST_YEAR = 2035;
    private static final Map<LocalDate, String> HAZ_HOLIDAYS = new HashMap<>();

    // {연도, 설날 월, 일, 부처님오신날 월, 일, 추석 월, 일} - 모두 양력
    private static final int[][] LUNAR = {
            {2024, 2, 10, 5, 15, 9, 17},
            {2025, 1, 29, 5, 5, 10, 6},
            {2026, 2, 17, 5, 24, 9, 25},
            {2027, 2, 7, 5, 13, 9, 15},
            {2028, 1, 27, 5, 2, 10, 3},
            {2029, 2, 13, 5, 20, 9, 22},
            {2030, 2, 3, 5, 9, 9, 12},
            {2031, 1, 23, 5, 28, 10, 1},
            {2032, 2, 11, 5, 16, 9, 19},
            {2033, 1, 31, 5, 6, 9, 8},
            {2034, 2, 19, 5, 25, 9, 27},
            {2035, 2, 8, 5, 15, 9, 16},
    };

    static {
        for (int y = FIRST_YEAR; y <= LAST_YEAR; y++) {
            put(LocalDate.of(y, 1, 1), "신정");
            put(LocalDate.of(y, 3, 1), "삼일절");
            put(LocalDate.of(y, 5, 5), "어린이날");
            put(LocalDate.of(y, 6, 6), "현충일");
            put(LocalDate.of(y, 8, 15), "광복절");
            put(LocalDate.of(y, 10, 3), "개천절");
            put(LocalDate.of(y, 10, 9), "한글날");
            put(LocalDate.of(y, 12, 25), "성탄절");
            if (y >= 2026) {
                put(LocalDate.of(y, 5, 1), "노동절");
                put(LocalDate.of(y, 7, 17), "제헌절");
            }
        }
        for (int[] r : LUNAR) {
            LocalDate seol = LocalDate.of(r[0], r[1], r[2]);
            put(seol.minusDays(1), "설날 연휴");
            put(seol, "설날");
            put(seol.plusDays(1), "설날 연휴");
            put(LocalDate.of(r[0], r[3], r[4]), "부처님오신날");
            LocalDate chu = LocalDate.of(r[0], r[5], r[6]);
            put(chu.minusDays(1), "추석 연휴");
            put(chu, "추석");
            put(chu.plusDays(1), "추석 연휴");
        }
        put(LocalDate.of(2024, 4, 10), "국회의원선거");
        put(LocalDate.of(2025, 6, 3), "대통령선거");
        put(LocalDate.of(2026, 6, 3), "지방선거");

        // 대체공휴일 (설·추석 먼저 계산해야 다른 날과 겹치지 않음)
        for (int[] r : LUNAR) {
            lunarSub(LocalDate.of(r[0], r[1], r[2]));
            lunarSub(LocalDate.of(r[0], r[5], r[6]));
        }
        for (int y = 2024; y <= LAST_YEAR; y++) {
            weekendSub(LocalDate.of(y, 3, 1), false);
            weekendSub(LocalDate.of(y, 5, 5), true);
            weekendSub(LocalDate.of(y, 8, 15), false);
            weekendSub(LocalDate.of(y, 10, 3), false);
            weekendSub(LocalDate.of(y, 10, 9), false);
            weekendSub(LocalDate.of(y, 12, 25), false);
            if (y >= 2026) {
                weekendSub(LocalDate.of(y, 5, 1), false);
                weekendSub(LocalDate.of(y, 7, 17), false);
            }
        }
        for (int[] r : LUNAR) weekendSub(LocalDate.of(r[0], r[3], r[4]), false);
    }

    // 공휴일이면 이름, 아니면 null
    public static String get(LocalDate d) { return HAZ_HOLIDAYS.get(d); }

    public static boolean isHoliday(LocalDate d) { return HAZ_HOLIDAYS.containsKey(d); }

    private static void put(LocalDate d, String name) {
        HAZ_HOLIDAYS.merge(d, name, (a, b) -> a.contains(b) ? a : a + "·" + b);
    }

    private static boolean weekend(LocalDate d) {
        DayOfWeek w = d.getDayOfWeek();
        return w == DayOfWeek.SATURDAY || w == DayOfWeek.SUNDAY;
    }

    // d 다음의 첫 평일(공휴일도 아닌 날)을 대체공휴일로
    private static void substituteAfter(LocalDate d) {
        LocalDate x = d.plusDays(1);
        while (weekend(x) || HAZ_HOLIDAYS.containsKey(x)) x = x.plusDays(1);
        put(x, "대체공휴일");
    }

    // 설·추석 연휴 3일 중 일요일이 있거나 다른 공휴일과 겹치면 연휴 다음 평일
    private static void lunarSub(LocalDate center) {
        boolean need = false;
        for (int k = -1; k <= 1; k++) {
            LocalDate d = center.plusDays(k);
            String n = HAZ_HOLIDAYS.get(d);
            if (d.getDayOfWeek() == DayOfWeek.SUNDAY || (n != null && n.contains("·"))) need = true;
        }
        if (need) substituteAfter(center.plusDays(1));
    }

    private static void weekendSub(LocalDate d, boolean overlapToo) {
        String n = HAZ_HOLIDAYS.get(d);
        if (weekend(d) || (overlapToo && n != null && n.contains("·"))) substituteAfter(d);
    }
}
