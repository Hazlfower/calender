import javax.swing.*;

// 앱 시작점: 데이터 불러오기 → 화면 → 알람 감시 시작
public class Main {
    public static void main(String[] args) {
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");

        SwingUtilities.invokeLater(() -> {
            AppSettings HAZsettings = new AppSettings();
            HAZsettings.load();
            Theme.load(HAZsettings);

            ScheduleManager HAZscheduleManager = new ScheduleManager();
            HAZscheduleManager.load();
            MemoManager memos = new MemoManager();
            memos.load();
            Todos todos = new Todos();
            todos.load();

            AlarmManager HAZalarmManager = new AlarmManager(HAZscheduleManager, HAZsettings);
            MainFrame frame = new MainFrame(HAZscheduleManager, HAZalarmManager, HAZsettings, memos, todos);
            frame.setVisible(true);
            memos.openSavedWindows();
            HAZalarmManager.start();
        });
    }
}
