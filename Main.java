
import javax.swing.SwingUtilities;

// 앱 시작점
public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            ScheduleManager HAZscheduleManager = new ScheduleManager();
            HAZscheduleManager.load(); // 저장된 일정 불러오기
            AlarmManager HAZalarmManager = new AlarmManager(HAZscheduleManager);
            ThemeManager HAZthemeManager = new ThemeManager();

            MainFrame mainFrame = new MainFrame(HAZscheduleManager, HAZalarmManager, HAZthemeManager);
            mainFrame.setVisible(true);

            HAZalarmManager.start(); // 알람 감시 시작
        });
    }
}
