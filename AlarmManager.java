import javax.swing.Timer;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.Set;

// 일정마다 설정된 "N분 전" 알람을 감시해서 팝업을 띄움
public class AlarmManager {
    private final ScheduleManager HAZscheduleManager;
    private final AppSettings settings;
    private final Set<String> firedKeys = new HashSet<>();
    private Timer timer;

    public AlarmManager(ScheduleManager sm, AppSettings settings) {
        this.HAZscheduleManager = sm;
        this.settings = settings;
    }

    public void start() {
        timer = new Timer(15_000, e -> check());
        timer.setInitialDelay(2_000);
        timer.start();
    }

    private void check() {
        LocalDateTime now = LocalDateTime.now();
        for (int i = 0; i <= 1; i++) {   // 자정 넘어가는 알람 때문에 내일 것도 확인
            LocalDate day = now.toLocalDate().plusDays(i);
            for (ScheduleManager.Occurrence o : HAZscheduleManager.startsOn(day, true)) {
                Schedule s = o.schedule;
                if (!s.hasAlarm()) continue;
                LocalDateTime fireAt = o.start.minusMinutes(s.getAlarmMinutes());
                boolean inWindow = !now.isBefore(fireAt) && now.isBefore(o.start.plusMinutes(1));
                String key = System.identityHashCode(s) + "@" + o.start;
                if (inWindow && firedKeys.add(key)) {
                    long left = Math.max(0, Duration.between(now, o.start).toMinutes() + 1);
                    new AlarmPopup(settings, s, (int) Math.min(left, s.getAlarmMinutes())).showPopup();
                }
            }
        }
    }

    public void showTest() {
        Schedule sample = new Schedule("테스트 일정", LocalDate.now(), LocalTime.of(18, 0), LocalTime.of(19, 0), "개인");
        sample.setMemo("알람은 이렇게 떠요!");
        new AlarmPopup(settings, sample, 10).showPopup();
    }
}
