
import javax.swing.*;
import java.awt.*;

// 위젯 (시나리오 4, 추가 기능)
public class WidgetWindow extends JWindow {
    private final JLabel label = new JLabel("일정이 없습니다", SwingConstants.CENTER);

    public WidgetWindow(boolean alwaysOnTop) {
        setSize(220, 90);
        setAlwaysOnTop(alwaysOnTop); // 최상단 or 배경화면
        label.setOpaque(true);
        label.setBackground(new Color(207, 226, 243));
        add(label);
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        setLocation(screen.width - 240, 40);
    }

    public void showSchedule(Schedule schedule) {
        label.setText(schedule == null ? "일정이 없습니다" : schedule.getTitle());
    }
}
