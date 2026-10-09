import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

// 화면 위에 떠 있는 작은 위젯 (가장 가까운 일정 또는 D-Day)
// - 끌어서 옮기기 (위치 기억) · 더블클릭: 캘린더 열기 · 우클릭: 메뉴
// - '바탕화면에 두기'면 다른 창 뒤로 깔림
public class WidgetWindow extends JWindow {
    private static final int W = 290, H = 108;
    private final ScheduleManager HAZscheduleManager;
    private final AppSettings settings;
    private final Timer timer;
    private final Runnable openMain;
    private String line1 = "", line2 = "", line3 = "";
    private Color dotColor;
    private Point dragOffset;
    private final boolean seeThrough;

    public WidgetWindow(ScheduleManager sm, AppSettings settings, Runnable openMain) {
        super((Window) null);
        this.HAZscheduleManager = sm;
        this.settings = settings;
        this.openMain = openMain;
        setSize(W, H);
        setFocusableWindowState(false);
        seeThrough = Ui.transparent(this);

        JPanel canvas = new JPanel() {
            @Override protected void paintComponent(Graphics g) { draw(g); }
        };
        canvas.setOpaque(false);
        canvas.setToolTipText("끌어서 옮기기 · 더블클릭: 캘린더 열기 · 우클릭: 메뉴");
        setContentPane(canvas);

        MouseAdapter mouse = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) { menu(canvas, e); return; }
                if (SwingUtilities.isLeftMouseButton(e)) dragOffset = e.getPoint();
            }
            @Override public void mouseDragged(MouseEvent e) {
                if (dragOffset == null) return;
                Point p = e.getLocationOnScreen();
                setLocation(p.x - dragOffset.x, p.y - dragOffset.y);
            }
            @Override public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) { menu(canvas, e); return; }
                if (dragOffset != null) {
                    settings.setInt("widgetX", getX());
                    settings.setInt("widgetY", getY());
                    settings.save();
                }
                dragOffset = null;
                if (!settings.getBool("widgetOnTop")) toBack();
            }
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) openMain.run();
            }
        };
        canvas.addMouseListener(mouse);
        canvas.addMouseMotionListener(mouse);
        timer = new Timer(20_000, e -> refresh());
    }

    private void menu(Component c, MouseEvent e) {
        JPopupMenu m = new JPopupMenu();
        m.add(Ui.item("캘린더 열기", openMain));
        boolean dday = "dday".equals(settings.get("widgetMode"));
        m.add(Ui.item(dday ? "가장 가까운 일정 보기" : "D-Day 보기", () -> {
            settings.set("widgetMode", dday ? "next" : "dday");
            settings.save();
            refresh();
        }));
        boolean top = settings.getBool("widgetOnTop");
        m.add(Ui.item(top ? "바탕화면에 두기" : "항상 맨 위에", () -> {
            settings.setBool("widgetOnTop", !top);
            settings.save();
            apply();
        }));
        m.addSeparator();
        m.add(Ui.item("위젯 닫기", () -> {
            settings.setBool("widgetEnabled", false);
            settings.save();
            apply();
        }));
        m.show(c, e.getX(), e.getY());
    }

    // 설정대로 켜고/끄고/위치 잡기
    public void apply() {
        if (!settings.getBool("widgetEnabled")) {
            timer.stop();
            setVisible(false);
            return;
        }
        boolean top = settings.getBool("widgetOnTop");
        setAlwaysOnTop(top);
        int x = settings.getInt("widgetX", -1), y = settings.getInt("widgetY", -1);
        if (x < 0 || y < 0) {
            Rectangle screen = Ui.screen();
            x = screen.x + screen.width - W - 20;
            y = screen.y + 20;
        }
        setLocation(x, y);
        Ui.ensureOnScreen(this);
        refresh();
        timer.start();
        setVisible(true);
        if (!top) toBack();
    }

    public void refresh() {
        dotColor = null;
        if ("dday".equals(settings.get("widgetMode"))) {
            List<DDay> list = HAZscheduleManager.activeDDays();
            line1 = "D-DAY";
            if (list.isEmpty()) {
                line2 = "D-Day가 없어요";
                line3 = "D-Day 탭에서 만들어 보세요";
            } else {
                DDay d = list.get(0);
                line2 = d.label() + "  " + d.name;
                line3 = TimeText.ymd(d.date) + (list.size() > 1 ? "  외 " + (list.size() - 1) + "개" : "");
            }
        } else {
            LocalDateTime now = LocalDateTime.now();
            ScheduleManager.Occurrence next = HAZscheduleManager.findNext(now);
            line1 = "다음 일정";
            if (next == null) {
                line2 = "예정된 일정이 없어요";
                line3 = "푹 쉬세요 :)";
            } else {
                dotColor = HAZscheduleManager.colorOf(next.schedule);
                line2 = next.schedule.title;
                boolean going = !next.start.isAfter(now);
                line3 = TimeText.relDay(next.date) + " " + TimeText.ampm(next.schedule.start) + "  ·  "
                        + (going ? "진행 중" : Ui.remaining(Duration.between(now, next.start).toMinutes()) + " 남음");
            }
        }
        repaint();
        if (isVisible() && !settings.getBool("widgetOnTop")) toBack();
    }

    private void draw(Graphics g) {
        Graphics2D g2 = Ui.aa(g);
        if (!seeThrough) {
            g2.setColor(Theme.bg());
            g2.fillRect(0, 0, W, H);
        }
        int r = Math.max(6, Theme.radius() + 4);
        RoundRectangle2D box = new RoundRectangle2D.Float(1, 1, W - 3, H - 3, r, r);
        g2.setColor(Ui.alpha(Theme.surface(), 245));
        g2.fill(box);
        Shape old = g2.getClip();
        g2.clip(box);
        g2.setColor(Theme.primary());
        g2.fillRect(0, 0, 7, H);
        g2.setClip(old);
        g2.setColor(Theme.border());
        g2.draw(box);
        g2.setFont(Ui.font(11, true));
        g2.setColor(Theme.highlight());
        g2.drawString(line1, 22, 25);
        g2.setFont(Ui.semi(17));
        g2.setColor(Theme.text());
        int tx = 22;
        if (dotColor != null) {
            g2.setColor(dotColor);
            g2.fillOval(22, 41, 10, 10);
            tx = 38;
            g2.setColor(Theme.text());
        }
        g2.drawString(Ui.ellipsize(line2, g2.getFontMetrics(), W - tx - 18), tx, 52);
        g2.setFont(Ui.font(12, false));
        g2.setColor(Theme.sub());
        g2.drawString(Ui.ellipsize(line3, g2.getFontMetrics(), W - 40), 22, 80);
        g2.dispose();
    }
}
