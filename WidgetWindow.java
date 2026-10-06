import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

// 화면 위에 떠 있는 작은 위젯 (다음 일정 또는 디데이)
public class WidgetWindow extends JWindow {
    private static final int W = 260, H = 100;
    private final ScheduleManager HAZscheduleManager;
    private final AppSettings settings;
    private final Timer timer;
    private String line1 = "", line2 = "", line3 = "";
    private Point dragOffset;

    public WidgetWindow(ScheduleManager sm, AppSettings settings) {
        this.HAZscheduleManager = sm;
        this.settings = settings;
        setSize(W, H);
        try { setBackground(new Color(0, 0, 0, 0)); } catch (Exception ignored) { }

        JPanel canvas = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Ui.smooth(g);
                Graphics2D g2 = (Graphics2D) g;
                int r = Math.max(Theme.radius(), 2) + 4;
                g2.setColor(Theme.surface());
                g2.fillRoundRect(0, 0, W - 1, H - 1, r, r);
                g2.setColor(Theme.primary());
                g2.fillRoundRect(0, 0, 8, H - 1, 4, 4);
                g2.setColor(Theme.border());
                g2.drawRoundRect(0, 0, W - 1, H - 1, r, r);
                g2.setFont(Ui.font(11, true));
                g2.setColor(Theme.accent());
                g2.drawString(line1, 20, 24);
                g2.setFont(Ui.font(17, true));
                g2.setColor(Theme.text());
                g2.drawString(line2, 20, 52);
                g2.setFont(Ui.font(12, false));
                g2.setColor(Theme.sub());
                g2.drawString(line3, 20, 78);
            }
        };
        canvas.setOpaque(false);
        setContentPane(canvas);

        MouseAdapter mouse = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    JPopupMenu menu = new JPopupMenu();
                    JMenuItem close = new JMenuItem("위젯 닫기");
                    close.addActionListener(a -> { settings.set("widgetEnabled", "false"); settings.save(); apply(); });
                    menu.add(close);
                    menu.show(canvas, e.getX(), e.getY());
                } else dragOffset = e.getPoint();
            }
            @Override public void mouseDragged(MouseEvent e) {
                if (dragOffset == null) return;
                Point p = e.getLocationOnScreen();
                setLocation(p.x - dragOffset.x, p.y - dragOffset.y);
            }
            @Override public void mouseReleased(MouseEvent e) {
                if (dragOffset == null) return;
                dragOffset = null;
                settings.set("widgetX", String.valueOf(getX()));
                settings.set("widgetY", String.valueOf(getY()));
                settings.save();
            }
        };
        canvas.addMouseListener(mouse);
        canvas.addMouseMotionListener(mouse);
        timer = new Timer(20_000, e -> refresh());
    }

    public void apply() {
        if (!settings.getBool("widgetEnabled")) { timer.stop(); setVisible(false); return; }
        setAlwaysOnTop(settings.getBool("widgetOnTop"));
        int x = settings.getInt("widgetX", -1), y = settings.getInt("widgetY", -1);
        if (x < 0 || y < 0) {
            Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
            x = screen.x + screen.width - W - 20;
            y = screen.y + 20;
        }
        setLocation(x, y);
        refresh();
        timer.start();
        setVisible(true);
    }

    public void refresh() {
        if ("dday".equals(settings.get("widgetMode"))) {
            LocalDate date = settings.getDate("ddayDate");
            line1 = "D-DAY";
            if (date == null) { line2 = "디데이가 없어요"; line3 = "설정에서 지정하세요"; }
            else {
                long days = ChronoUnit.DAYS.between(LocalDate.now(), date);
                line2 = days == 0 ? "D-DAY!" : days > 0 ? "D-" + days : "D+" + (-days);
                line3 = settings.get("ddayTitle") + " (" + date + ")";
            }
        } else {
            LocalDateTime now = LocalDateTime.now();
            ScheduleManager.Occurrence next = HAZscheduleManager.findNext(now);
            line1 = "다음 일정";
            if (next == null) { line2 = "일정이 없어요"; line3 = "푹 쉬세요 :)"; }
            else {
                line2 = next.schedule.getTitle();
                line3 = next.start.toLocalDate() + " " + next.schedule.getStart()
                        + " · " + Ui.remaining(Duration.between(now, next.start).toMinutes()) + " 남음";
            }
        }
        repaint();
    }
}
