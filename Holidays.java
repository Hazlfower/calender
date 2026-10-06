import java.time.LocalDate;
import java.time.MonthDay;
import java.util.HashMap;
import java.util.Map;

// 공휴일 (양력 고정 공휴일만. 설날/추석/부처님오신날 같은 음력 공휴일은 TODO)
public class Holidays {
    private static final Map<MonthDay, String> FIXED = new HashMap<>();
    static {
        FIXED.put(MonthDay.of(1, 1), "신정");
        FIXED.put(MonthDay.of(3, 1), "삼일절");
        FIXED.put(MonthDay.of(5, 5), "어린이날");
        FIXED.put(MonthDay.of(6, 6), "현충일");
        FIXED.put(MonthDay.of(8, 15), "광복절");
        FIXED.put(MonthDay.of(10, 3), "개천절");
        FIXED.put(MonthDay.of(10, 9), "한글날");
        FIXED.put(MonthDay.of(12, 25), "성탄절");
    }

    public static String get(LocalDate date) {
        return FIXED.get(MonthDay.from(date));
    }
}
