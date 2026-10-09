import javax.swing.Timer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.Set;

// 10초마다 알람 시각이 되었는지 확인해서 알림을 띄움
// (컴퓨터가 잠깐 멈췄어도 3분 안이면 울림, 같은 알람은 한 번만)
public class AlarmManager {
    private final ScheduleManager HAZscheduleManager;
    private final AppSettings settings;
    private final Set<String> fired = new HashSet<>();
    private Timer timer;
    private Runnable openMain = () -> { };

    public AlarmManager(ScheduleManager sm, AppSettings settings) {
        this.HAZscheduleManager = sm;
        this.settings = settings;
    }

    public void setOpenMain(Runnable r) { this.openMain = r; }

    public void start() {
        timer = new Timer(10_000, e -> check());
        timer.setInitialDelay(2_000);
        timer.start();
    }

    void check() {
        LocalDateTime now = LocalDateTime.now();
        LocalDate today = now.toLocalDate();
        for (int k = 0; k <= 1; k++) {             // 알람은 최대 하루 전까지라 오늘·내일만 보면 됨
            LocalDate d = today.plusDays(k);
            for (ScheduleManager.Occurrence o : HAZscheduleManager.startsOn(d, true)) {
                Schedule s = o.schedule;
                if (!s.hasAlarm()) continue;
                for (int before : s.alarms) {
                    LocalDateTime trigger = o.start.minusMinutes(before);
                    if (!now.isBefore(trigger) && now.isBefore(trigger.plusMinutes(3))
                            && fired.add(s.id + "@" + d + "@" + before))
                        AlarmPopup.show(settings, s, o.start, before, openMain);
                }
            }
        }
    }

    // 설정 화면에서 미리보기
    public void showTest() {
        Schedule sample = new Schedule();
        sample.title = "테스트 일정";
        sample.start = LocalTime.now().getHour() * 60 + LocalTime.now().getMinute() + 10;
        if (sample.start >= 1440) sample.start = 1430;
        sample.end = Math.min(1440, sample.start + 60);
        sample.memo = "알람은 이렇게 떠요!";
        sample.link = "https://example.com";
        AlarmPopup.show(settings, sample, LocalDate.now().atStartOfDay().plusMinutes(sample.start), 10, openMain);
    }
}
