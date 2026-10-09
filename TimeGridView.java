import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

// 주 보기(7일) / 일 보기(1일): 위쪽은 하루 종일·여러 날 일정, 아래는 시간표
public class TimeGridView extends JPanel {
    static final int GUTTER = 56, HOUR = 50, DAY_HEAD = 52, BAR_H = 20;

    private final MainFrame app;
    private final ScheduleManager HAZscheduleManager;
    private final int days;
    private final Header header = new Header();
    private final Body body = new Body();
    private final JScrollPane scroll;

    record Hit(Rectangle r, Schedule s, LocalDate occ) { }

    public TimeGridView(MainFrame app, ScheduleManager sm, int days) {
        super(new BorderLayout());
        this.app = app;
        this.HAZscheduleManager = sm;
        this.days = days;
        setOpaque(false);
        scroll = new JScrollPane(body);
        scroll.setColumnHeaderView(header);
        scroll.setBorder(null);
        scroll.setViewportBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getColumnHeader().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(HOUR / 2);
        add(scroll);
    }

    LocalDate first() { return days == 7 ? app.weekStart(app.getAnchor()) : app.getAnchor(); }

    public void refresh() {
        header.revalidate();
        header.repaint();
        body.repaint();
        if (scroll.getColumnHeader() != null) scroll.getColumnHeader().revalidate();
    }

    // 아침(또는 지금 시각 근처)이 보이게
    public void scrollToMorning() {
        SwingUtilities.invokeLater(() -> {
            // 가장 이른 일정과 (오늘이면) 지금 시각 중 빠른 쪽이 보이게
            int earliest = 8 * 60;
            boolean any = false;
            for (int c = 0; c < days; c++)
                for (Schedule s : HAZscheduleManager.getByDate(first().plusDays(c)))
                    if (!s.allDay() && !s.multiDay()) { earliest = any ? Math.min(earliest, s.start) : s.start; any = true; }
            boolean showsToday = !LocalDate.now().isBefore(first()) && LocalDate.now().isBefore(first().plusDays(days));
            int target = any ? earliest : 8 * 60;
            if (showsToday) target = Math.min(target, LocalTime.now().getHour() * 60 - 60);
            target = Math.max(0, Math.min(target, 16 * 60));
            scroll.getVerticalScrollBar().setValue(target * HOUR / 60 - 10);
        });
    }

    private double colW(int width) { return (width - GUTTER) / (double) days; }

    private int colAt(int x, int width) {
        if (x < GUTTER) return -1;
        int c = (int) ((x - GUTTER) / colW(width));
        return c >= 0 && c < days ? c : -1;
    }

    // 위쪽 막대로 보여 줄 일정 (하루 종일 또는 여러 날)
    private List<MonthView.Item> barItems() {
        LocalDate ws = first(), we = ws.plusDays(days - 1);
        List<MonthView.Item> out = new ArrayList<>();
        for (Schedule s : HAZscheduleManager.getAll()) {
            if (!HAZscheduleManager.visible(s) || !(s.allDay() || s.multiDay())) continue;
            int span = s.span();
            for (LocalDate c = ws.minusDays(span); !c.isAfter(we); c = c.plusDays(1)) {
                if (!s.startsOn(c)) continue;
                LocalDate a = c.isBefore(ws) ? ws : c, b = c.plusDays(span);
                if (b.isAfter(we)) b = we;
                out.add(new MonthView.Item(s, (int) ChronoUnit.DAYS.between(ws, a), (int) ChronoUnit.DAYS.between(ws, b), c, true));
            }
        }
        out.sort(Comparator.comparingInt((MonthView.Item i) -> -(i.c1() - i.c0())).thenComparingInt(MonthView.Item::c0));
        return out;
    }

    private int[] lanesOf(List<MonthView.Item> items, int[] count) {
        List<boolean[]> l = new ArrayList<>();
        int[] laneOf = new int[items.size()];
        for (int i = 0; i < items.size(); i++) {
            MonthView.Item it = items.get(i);
            int lane = 0;
            for (; ; lane++) {
                if (lane == l.size()) l.add(new boolean[days]);
                boolean free = true;
                for (int c = it.c0(); c <= it.c1(); c++) if (l.get(lane)[c]) { free = false; break; }
                if (free) break;
            }
            for (int c = it.c0(); c <= it.c1(); c++) l.get(lane)[c] = true;
            laneOf[i] = lane;
        }
        count[0] = l.size();
        return laneOf;
    }

    // ------------------------------------------------------------------ 머리 (요일 + 하루 종일)

    private final class Header extends JComponent implements Scrollable {
        private final List<Hit> hits = new ArrayList<>();

        Header() {
            setToolTipText("");
            addMouseListener(new MouseAdapter() {
                @Override public void mouseReleased(MouseEvent e) {
                    int c = colAt(e.getX(), getWidth());
                    if (c < 0 || !SwingUtilities.isLeftMouseButton(e)) return;
                    for (Hit h : hits)
                        if (h.r().contains(e.getPoint())) {
                            if (e.getClickCount() == 2) app.editSchedule(h.s(), h.occ());
                            return;
                        }
                    LocalDate d = first().plusDays(c);
                    app.selectDate(d);
                    if (e.getClickCount() == 2) {
                        if (days == 7) app.setView("DAY");
                        else app.addSchedule(d, -1, -1);
                    }
                }
            });
        }

        @Override public String getToolTipText(MouseEvent e) {
            for (Hit h : hits)
                if (h.r().contains(e.getPoint())) return h.s().title + " · " + h.s().timeText();
            return days == 7 ? "더블클릭: 일 보기" : "더블클릭: 하루 종일 일정 추가";
        }

        @Override public Dimension getPreferredSize() {
            int[] n = new int[1];
            lanesOf(barItems(), n);
            int lanes = Math.max(1, Math.min(3, n[0]));
            return new Dimension(200, DAY_HEAD + 10 + lanes * (BAR_H + 3));
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = Ui.aa(g);
            hits.clear();
            int w = getWidth(), h = getHeight();
            double cw = colW(w);
            LocalDate ws = first(), today = LocalDate.now();
            boolean sf = Theme.style() == Theme.Style.SF;
            for (int c = 0; c < days; c++) {
                LocalDate d = ws.plusDays(c);
                double x = GUTTER + c * cw;
                String hol = Holidays.get(d);
                Color dc = (d.getDayOfWeek() == DayOfWeek.SUNDAY || hol != null) ? Theme.danger()
                        : d.getDayOfWeek() == DayOfWeek.SATURDAY ? Theme.saturday() : Theme.sub();
                if (d.equals(app.getSelectedDate()) && days == 7) {
                    g2.setColor(Ui.alpha(Theme.highlight(), Theme.isNight() ? 50 : 30));
                    g2.fill(new RoundRectangle2D.Double(x + 3, 3, cw - 6, DAY_HEAD - 6, 10, 10));
                }
                g2.setFont(Ui.font(12f, false));
                g2.setColor(dc);
                String dow = TimeText.dow(d) + (hol != null ? " · " + hol : "");
                Ui.centerText(g2, Ui.ellipsize(dow, g2.getFontMetrics(), (int) cw - 8), x + cw / 2, 18);
                g2.setFont(Ui.semi(17f));
                String num = String.valueOf(d.getDayOfMonth());
                if (d.equals(today)) {
                    g2.setColor(Theme.highlight());
                    if (sf) g2.fill(new Rectangle2D.Double(x + cw / 2 - 15, 23, 30, 25));
                    else g2.fill(new Ellipse2D.Double(x + cw / 2 - 15, 23, 30, 25));
                    g2.setColor(Theme.onColor(Theme.highlight()));
                } else g2.setColor(dc == Theme.sub() ? Theme.text() : dc);
                Ui.centerText(g2, num, x + cw / 2, 42);
            }
            g2.setFont(Ui.font(10.5f, false));
            g2.setColor(Theme.sub());
            g2.drawString("종일", 16, DAY_HEAD + 20);
            g2.setColor(Theme.border());
            g2.fillRect(0, DAY_HEAD, w, 1);
            g2.fillRect(0, h - 1, w, 1);

            List<MonthView.Item> items = barItems();
            int[] n = new int[1];
            int[] laneOf = lanesOf(items, n);
            int[] more = new int[days];
            int arc = sf ? 2 : 7;
            for (int i = 0; i < items.size(); i++) {
                MonthView.Item it = items.get(i);
                int lane = laneOf[i];
                if (lane >= 3) {
                    for (int c = it.c0(); c <= it.c1(); c++) more[c]++;
                    continue;
                }
                double y = DAY_HEAD + 6 + lane * (BAR_H + 3);
                double x0 = GUTTER + it.c0() * cw + 3, x1 = GUTTER + (it.c1() + 1) * cw - 3;
                Color col = HAZscheduleManager.colorOf(it.s());
                g2.setColor(Ui.soft(col));
                g2.fill(new RoundRectangle2D.Double(x0, y, x1 - x0, BAR_H, arc, arc));
                g2.setColor(col);
                g2.fill(new RoundRectangle2D.Double(x0, y, 4, BAR_H, 4, 4));
                g2.setFont(Ui.semi(11.5f));
                g2.setColor(Theme.onColor(Ui.soft(col)));
                g2.drawString(Ui.ellipsize(it.s().title, g2.getFontMetrics(), (int) (x1 - x0) - 14), (float) x0 + 9, (float) y + 14.5f);
                hits.add(new Hit(new Rectangle((int) x0, (int) y, (int) (x1 - x0), BAR_H), it.s(), it.occ()));
            }
            g2.setFont(Ui.font(10.5f, true));
            g2.setColor(Theme.sub());
            for (int c = 0; c < days; c++)
                if (more[c] > 0) g2.drawString("+" + more[c], (float) (GUTTER + (c + 1) * cw - 24), DAY_HEAD + 16);
            g2.dispose();
        }

        public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 10; }
        public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return 50; }
        public boolean getScrollableTracksViewportWidth() { return true; }
        public boolean getScrollableTracksViewportHeight() { return false; }
    }

    // ------------------------------------------------------------------ 시간표

    private final class Body extends JComponent implements Scrollable {
        private final List<Hit> hits = new ArrayList<>();
        private Point hover;

        Body() {
            setToolTipText("");
            MouseAdapter ma = new MouseAdapter() {
                @Override public void mouseMoved(MouseEvent e) { hover = e.getPoint(); repaint(); }
                @Override public void mouseExited(MouseEvent e) { hover = null; repaint(); }
                @Override public void mousePressed(MouseEvent e) { pop(e); }
                @Override public void mouseReleased(MouseEvent e) {
                    if (pop(e)) return;
                    int c = colAt(e.getX(), getWidth());
                    if (c < 0 || !SwingUtilities.isLeftMouseButton(e)) return;
                    LocalDate d = first().plusDays(c);
                    Hit hit = hitAt(e.getPoint());
                    app.selectDate(d);
                    if (e.getClickCount() == 2) {
                        if (hit != null) app.editSchedule(hit.s(), hit.occ());
                        else {
                            int m = Math.min(23 * 60 + 30, (e.getY() / HOUR) * 60 + ((e.getY() % HOUR) >= HOUR / 2 ? 30 : 0));
                            app.addSchedule(d, m, Math.min(1440, m + 60));
                        }
                    }
                }

                private boolean pop(MouseEvent e) {
                    if (!e.isPopupTrigger()) return false;
                    Hit hit = hitAt(e.getPoint());
                    int c = colAt(e.getX(), getWidth());
                    if (c < 0) return true;
                    LocalDate d = first().plusDays(c);
                    JPopupMenu m = new JPopupMenu();
                    if (hit != null) {
                        m.add(Ui.item("수정", () -> app.editSchedule(hit.s(), hit.occ())));
                        m.add(Ui.item("삭제", () -> app.confirmDelete(hit.s(), hit.occ())));
                    } else {
                        int min = Math.min(23 * 60, (e.getY() / HOUR) * 60);
                        m.add(Ui.item(TimeText.fmt(min) + "에 일정 추가", () -> app.addSchedule(d, min, Math.min(1440, min + 60))));
                    }
                    m.show(Body.this, e.getX(), e.getY());
                    return true;
                }
            };
            addMouseListener(ma);
            addMouseMotionListener(ma);
        }

        private Hit hitAt(Point p) {
            for (Hit h : hits) if (h.r().contains(p)) return h;
            return null;
        }

        @Override public String getToolTipText(MouseEvent e) {
            Hit h = hitAt(e.getPoint());
            if (h != null) return "<html><b>" + Ui.html(h.s().title) + "</b><br>" + h.s().timeText() + " · " + Ui.html(h.s().category) + "</html>";
            return "더블클릭: 이 시간에 일정 추가";
        }

        @Override public Dimension getPreferredSize() { return new Dimension(200, HOUR * 24 + 1); }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = Ui.aa(g);
            hits.clear();
            int w = getWidth();
            double cw = colW(w);
            LocalDate ws = first(), today = LocalDate.now();

            for (int c = 0; c < days; c++) {
                if (ws.plusDays(c).equals(today)) {
                    g2.setColor(Ui.alpha(Theme.highlight(), Theme.isNight() ? 18 : 12));
                    g2.fill(new Rectangle2D.Double(GUTTER + c * cw, 0, cw, HOUR * 24));
                }
            }
            if (hover != null && hover.x >= GUTTER) {
                int c = colAt(hover.x, w), r = hover.y / HOUR;
                if (c >= 0) {
                    g2.setColor(Ui.alpha(Theme.highlight(), Theme.isNight() ? 34 : 22));
                    g2.fill(new Rectangle2D.Double(GUTTER + c * cw, r * HOUR, cw, HOUR));
                }
            }
            g2.setFont(Ui.font(11f, false));
            for (int hr = 0; hr < 24; hr++) {
                int y = hr * HOUR;
                g2.setColor(Theme.border());
                g2.fillRect(GUTTER, y, w - GUTTER, 1);
                if (hr > 0) {
                    g2.setColor(Theme.sub());
                    String lab = TimeText.fmt(hr * 60);
                    g2.drawString(lab, GUTTER - 9 - g2.getFontMetrics().stringWidth(lab), y + 4);
                }
                g2.setColor(Ui.alpha(Theme.border(), 110));
                for (int x = GUTTER; x < w; x += 6) g2.fillRect(x, y + HOUR / 2, 3, 1);   // 30분 점선
            }
            g2.setColor(Theme.border());
            for (int c = 0; c <= days; c++) g2.fillRect((int) Math.round(GUTTER + c * cw), 0, 1, HOUR * 24);

            for (int c = 0; c < days; c++) drawDay(g2, ws.plusDays(c), GUTTER + c * cw, cw);

            // 지금 시각
            LocalDateTime now = LocalDateTime.now();
            long idx = ChronoUnit.DAYS.between(ws, now.toLocalDate());
            if (idx >= 0 && idx < days) {
                double y = (now.getHour() * 60 + now.getMinute()) * HOUR / 60.0;
                double x = GUTTER + idx * cw;
                g2.setColor(Theme.danger());
                g2.fill(new Ellipse2D.Double(x - 4, y - 4, 8, 8));
                g2.fill(new Rectangle2D.Double(x, y - 1, cw, 2));
            }
            g2.dispose();
        }

        private void drawDay(Graphics2D g2, LocalDate d, double x, double cw) {
            List<Schedule> ev = new ArrayList<>();
            for (Schedule s : HAZscheduleManager.getByDate(d)) if (!s.allDay() && !s.multiDay()) ev.add(s);
            ev.sort(Comparator.comparingInt((Schedule s) -> s.start).thenComparingInt(s -> -s.end));
            int n = ev.size();
            int[] col = new int[n], cols = new int[n];
            List<Integer> colEnds = new ArrayList<>();
            int clusterStart = 0, clusterEnd = -1;
            // 겹치는 일정은 나란히
            for (int i = 0; i <= n; i++) {
                if (i == n || ev.get(i).start >= clusterEnd) {
                    for (int k = clusterStart; k < i; k++) cols[k] = colEnds.size();
                    colEnds.clear();
                    clusterStart = i;
                    if (i == n) break;
                }
                Schedule s = ev.get(i);
                int c = 0;
                while (c < colEnds.size() && colEnds.get(c) > s.start) c++;
                if (c == colEnds.size()) colEnds.add(s.end); else colEnds.set(c, s.end);
                col[i] = c;
                clusterEnd = Math.max(clusterEnd, s.end);
            }
            int arc = Theme.style() == Theme.Style.SF ? 2 : 8;
            for (int i = 0; i < n; i++) {
                Schedule s = ev.get(i);
                double w = (cw - 6) / cols[i];
                double bx = x + 3 + col[i] * w, by = s.start * HOUR / 60.0 + 1;
                double bh = Math.max(20, (s.end - s.start) * HOUR / 60.0 - 2);
                Color c = HAZscheduleManager.colorOf(s);
                RoundRectangle2D r = new RoundRectangle2D.Double(bx, by, w - 2, bh, arc, arc);
                Color fill = Ui.soft(c);
                g2.setColor(fill);
                g2.fill(r);
                g2.setColor(c);
                g2.fill(new RoundRectangle2D.Double(bx, by, 4, bh, 4, 4));
                Shape old = g2.getClip();
                g2.clip(r);
                g2.setFont(Ui.semi(12f));
                g2.setColor(Theme.onColor(fill));
                FontMetrics fm = g2.getFontMetrics();
                int tw = (int) w - 14;
                g2.drawString(Ui.ellipsize(s.title, fm, tw), (float) bx + 9, (float) by + 15);
                if (bh > 34) {
                    g2.setFont(Ui.font(11f, false));
                    g2.setColor(Ui.alpha(Theme.onColor(fill), 170));
                    String t = s.timeText() + (s.hasAlarm() ? "  · 알람" : "");
                    g2.drawString(Ui.ellipsize(t, g2.getFontMetrics(), tw), (float) bx + 9, (float) by + 30);
                }
                g2.setClip(old);
                hits.add(new Hit(new Rectangle((int) bx, (int) by, (int) w - 2, (int) bh), s, d));
            }
        }

        public Dimension getPreferredScrollableViewportSize() { return new Dimension(400, 500); }
        public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return HOUR / 2; }
        public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return HOUR * 4; }
        public boolean getScrollableTracksViewportWidth() { return true; }
        public boolean getScrollableTracksViewportHeight() { return false; }
    }
}
