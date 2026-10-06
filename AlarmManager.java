
import javax.swing.JOptionPane;
import javax.swing.Timer;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

// 일정 알람 (시나리오 2)
public class AlarmManager {
    public static class HAZAlarm {
        Schedule schedule;
        int minutesBefore;   // N분 전 (0이면 정시)
        boolean fired = false;
        HAZAlarm(Schedule schedule, int minutesBefore) {
            this.schedule = schedule;
            this.minutesBefore = minutesBefore;
        }
    }

    private final ScheduleManager HAZscheduleManager;
    private final List<HAZAlarm> HAZalarmList = new ArrayList<>();
    private Timer timer;
    private String HAZmessageFormat = "앞으로 %d분 남았어요! - %s"; // 캐릭터 대사로 변경 가능

    public AlarmManager(ScheduleManager HAZscheduleManager) {
        this.HAZscheduleManager = HAZscheduleManager;
    }

    public void addAlarm(Schedule schedule, int minutesBefore) {
        if (schedule == null || schedule.getStart() == null) {
            JOptionPane.showMessageDialog(null, "시간이 지정되지 않은 일정입니다.", "경고", JOptionPane.WARNING_MESSAGE);
            return;
        }
        HAZalarmList.add(new HAZAlarm(schedule, minutesBefore));
    }

    public void setMessageFormat(String format) { this.HAZmessageFormat = format; }

    public void start() {
        timer = new Timer(30_000, e -> check()); // 30초마다 확인
        timer.start();
    }

    private void check() {
        LocalDateTime now = LocalDateTime.now();
        for (HAZAlarm a : HAZalarmList) {
            LocalDateTime fireTime = a.schedule.getStart().minusMinutes(a.minutesBefore);
            if (!a.fired && !now.isBefore(fireTime)) {
                a.fired = true;
                String msg = String.format(HAZmessageFormat, a.minutesBefore, a.schedule.getTitle());
                if (a.schedule.getLink() != null) msg += "\n링크: " + a.schedule.getLink();
                JOptionPane.showMessageDialog(null, msg, "알람", JOptionPane.INFORMATION_MESSAGE);
            }
        }
    }
}
