import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

// 월 보기: 여러 날 일정은 칸을 가로지르는 막대로, 시간 일정은 "18:00 제목" 한 줄로
public class MonthView extends JComponent {
    static final int HEAD = 34, BAR_H = 19, NUM_H = 28;
    private static final String[] SF_DAYS = {"MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"};

    private final MainFrame app;
    private final ScheduleManager HAZscheduleManager;
    private Point hover;
    private final List<Hit> hits = new ArrayList<>();

    record Item(Schedule s, int c0, int c1, LocalDate occ, boolean bar) { }
    record Hit(Rectangle r, Schedule s, LocalDate occ) { }

    public MonthView(MainFrame app, ScheduleManager sm) {
        this.app = app;
        this.HAZscheduleManager = sm;
        setToolTipText("");
        setOpaque(false);
        MouseAdapter ma = new MouseAdapter() {
            @Override public void mouseMoved(MouseEvent e) { hover = e.getPoint(); repaint(); }
            @Override public void mouseExited(MouseEvent e) { hover = null; repaint(); }
            @Override public void mousePressed(MouseEvent e) {
                LocalDate d = dateAt(e.getPoint());
                if (d != null && e.isPopupTrigger()) popup(e, d);
            }
            @Override public void mouseReleased(MouseEvent e) {
                LocalDate d = dateAt(e.getPoint());
                if (d == null) return;
                if (e.isPopupTrigger()) { popup(e, d); return; }
                if (!SwingUtilities.isLeftMouseButton(e)) return;
                Hit hit = hitAt(e.getPoint());
                app.selectDate(d);
                if (e.getClickCount() == 2) {
                    if (hit != null) app.editSchedule(hit.s(), hit.occ());
                    else app.addSchedule(d, -1, -1);
                }
            }
        };
        addMouseListener(ma);
        addMouseMotionListener(ma);
        addMouseWheelListener(e -> {
            if (e.getWheelRotation() != 0) app.shift(e.getWheelRotation() > 0 ? 1 : -1);
        });
    }

    private void popup(MouseEvent e, LocalDate d) {
        app.selectDate(d);
        JPopupMenu m = new JPopupMenu();
        Hit hit = hitAt(e.getPoint());
        if (hit != null) {
            String t = hit.s().title.length() > 14 ? hit.s().title.substring(0, 14) + "…" : hit.s().title;
            m.add(Ui.item("「" + t + "」 수정", () -> app.editSchedule(hit.s(), hit.occ())));
            m.add(Ui.item("「" + t + "」 삭제", () -> app.confirmDelete(hit.s(), hit.occ())));
            m.addSeparator();
        }
        m.add(Ui.item("이 날에 일정 추가", () -> app.addSchedule(d, -1, -1)));
        m.add(Ui.item("이 날을 D-Day로", () -> app.addDDayFor(d)));
        m.add(Ui.item("주 보기로 열기", () -> app.setView("WEEK")));
        m.add(Ui.item("일 보기로 열기", () -> app.setView("DAY")));
        m.show(this, e.getX(), e.getY());
    }

    private YearMonth month() { return YearMonth.from(app.getAnchor()); }

    private LocalDate gridStart() { return app.weekStart(month().atDay(1)); }

    private int rows() {
        long days = ChronoUnit.DAYS.between(gridStart(), month().atEndOfMonth()) + 1;
        return (int) Math.ceil(days / 7.0);
    }

    private double cw() { return getWidth() / 7.0; }

    private double ch() { return (getHeight() - HEAD) / (double) rows(); }

    LocalDate dateAt(Point p) {
        if (p.y < HEAD) return null;
        int c = (int) (p.x / cw()), r = (int) ((p.y - HEAD) / ch());
        if (c < 0 || c > 6 || r < 0 || r >= rows()) return null;
        return gridStart().plusDays(r * 7L + c);
    }

    private Hit hitAt(Point p) {
        for (Hit h : hits) if (h.r().contains(p)) return h;
        return null;
    }

    @Override public String getToolTipText(MouseEvent e) {
        Hit hit = hitAt(e.getPoint());
        if (hit != null) {
            Schedule s = hit.s();
            return "<html><b>" + Ui.html(s.title) + "</b><br>" + s.timeText() + " · " + Ui.html(s.category)
                    + (s.repeat != Schedule.HAZRepeat.NONE ? "<br>" + s.repeatText() : "")
                    + (s.hasAlarm() ? "<br>알람: " + s.alarmText() : "") + "</html>";
        }
        LocalDate d = dateAt(e.getPoint());
        if (d == null) return null;
        String hol = Holidays.get(d);
        return hol == null ? "더블클릭: 일정 추가 · 우클릭: 메뉴" : hol;
    }

    // 한 주에 보이는 조각들 (여러 날 → 하루 종일 → 시간 순)
    List<Item> weekItems(LocalDate ws) {
        LocalDate we = ws.plusDays(6);
        List<Item> out = new ArrayList<>();
        for (Schedule s : HAZscheduleManager.getAll()) {
            if (!HAZscheduleManager.visible(s)) continue;
            int span = s.span();
            for (LocalDate c = ws.minusDays(span); !c.isAfter(we); c = c.plusDays(1)) {
                if (!s.startsOn(c)) continue;
                LocalDate a = c.isBefore(ws) ? ws : c;
                LocalDate b = c.plusDays(span);
                if (b.isAfter(we)) b = we;
                out.add(new Item(s, (int) ChronoUnit.DAYS.between(ws, a), (int) ChronoUnit.DAYS.between(ws, b), c,
                        span > 0 || s.allDay()));
            }
        }
        out.sort(Comparator.comparingInt((Item i) -> -(i.c1() - i.c0()))
                .thenComparing(i -> !i.bar())
                .thenComparingInt(i -> i.c0())
                .thenComparingInt(i -> i.s().start)
                .thenComparing(i -> i.s().title));
        return out;
    }

    @Override protected void paintComponent(Graphics g) {
        Graphics2D g2 = Ui.aa(g);
        hits.clear();
        int w = getWidth(), rows = rows();
        double cw = cw(), ch = ch();
        LocalDate d0 = gridStart(), today = LocalDate.now();
        YearMonth ym = month();
        boolean sf = Theme.style() == Theme.Style.SF;
        int r = Math.min(Theme.radius(), 10);

        // 요일 머리
        g2.setFont(sf ? Ui.font(11.5f, true) : Ui.font(12.5f, true));
        for (int c = 0; c < 7; c++) {
            DayOfWeek dw = app.firstDay().plus(c);
            g2.setColor(dw == DayOfWeek.SUNDAY ? Theme.danger() : dw == DayOfWeek.SATURDAY ? Theme.saturday() : Theme.sub());
            String name = sf ? SF_DAYS[dw.getValue() - 1] : TimeText.dow(dw);
            Ui.centerText(g2, name, c * cw + cw / 2, HEAD - 11);
        }
        g2.setColor(Theme.border());
        g2.fillRect(0, HEAD - 1, w, 1);

        // 칸 바탕
        for (int i = 0; i < rows * 7; i++) {
            int row = i / 7, c = i % 7;
            LocalDate d = d0.plusDays(i);
            Rectangle2D cell = cellRect(row, c, cw, ch);
            boolean in = YearMonth.from(d).equals(ym);
            if (!in) {
                g2.setColor(Ui.alpha(Ui.blend(Theme.bg(), Theme.surface(), 0.6), Theme.isNight() ? 150 : 200));
                g2.fill(cell);
            } else if (Holidays.get(d) != null) {
                g2.setColor(Ui.alpha(Theme.dangerLight(), Theme.isNight() ? 60 : 110));
                g2.fill(cell);
            }
            if (d.equals(today)) {
                g2.setPaint(new GradientPaint((float) cell.getX(), (float) cell.getY(), Ui.alpha(Theme.highlight(), 55),
                        (float) cell.getMaxX(), (float) cell.getMaxY(), Ui.alpha(Theme.highlight(), 10)));
                g2.fill(cell);
            }
            if (hover != null && cell.contains(hover)) {
                g2.setColor(Ui.alpha(Theme.highlight(), Theme.isNight() ? 36 : 22));
                g2.fill(cell);
            }
        }
        // 격자선
        g2.setColor(Theme.border());
        for (int c = 1; c < 7; c++) g2.fillRect((int) Math.round(c * cw), HEAD, 1, getHeight() - HEAD);
        for (int row = 1; row < rows; row++) g2.fillRect(0, HEAD + (int) Math.round(row * ch), w, 1);

        // 날짜 숫자
        for (int i = 0; i < rows * 7; i++) {
            int row = i / 7, c = i % 7;
            LocalDate d = d0.plusDays(i);
            Rectangle2D cell = cellRect(row, c, cw, ch);
            boolean in = YearMonth.from(d).equals(ym);
            String hol = Holidays.get(d);
            Color nc = (d.getDayOfWeek() == DayOfWeek.SUNDAY || hol != null) ? Theme.danger()
                    : d.getDayOfWeek() == DayOfWeek.SATURDAY ? Theme.saturday() : Theme.text();
            if (!in) nc = Ui.alpha(nc, 110);
            g2.setFont(Ui.semi(13f));
            String num = String.valueOf(d.getDayOfMonth());
            int sw = g2.getFontMetrics().stringWidth(num);
            float nx = (float) cell.getX() + 9, ny = (float) cell.getY() + 19;
            if (d.equals(today)) {
                float dia = Math.max(23, sw + 11);
                g2.setColor(Theme.highlight());
                if (sf) g2.fill(new Rectangle2D.Float(nx - (dia - sw) / 2f, ny - 16f, dia, 22));
                else g2.fill(new Ellipse2D.Float(nx - (dia - sw) / 2f, ny - 16f, dia, 22));
                nc = Theme.onColor(Theme.highlight());
            }
            g2.setColor(nc);
            g2.drawString(num, nx, ny);
            if (hol != null) {
                g2.setFont(Ui.font(10.5f, false));
                FontMetrics hf = g2.getFontMetrics();
                String hs = Ui.ellipsize(hol, hf, (int) cell.getWidth() - sw - 30);
                g2.setColor(Ui.alpha(Theme.danger(), in ? 255 : 120));
                g2.drawString(hs, (float) (cell.getMaxX() - 7 - hf.stringWidth(hs)), ny - 1);
            }
            if (d.equals(app.getSelectedDate())) {
                g2.setColor(Theme.highlight());
                g2.setStroke(new BasicStroke(2f));
                g2.draw(new RoundRectangle2D.Double(cell.getX() + 2, cell.getY() + 2, cell.getWidth() - 4, cell.getHeight() - 4, r, r));
                g2.setStroke(new BasicStroke(1f));
            }
        }

        // 일정
        for (int row = 0; row < rows; row++) drawWeek(g2, d0.plusDays(row * 7L), HEAD + row * ch, cw, ch, ym);
        g2.dispose();
    }

    private Rectangle2D cellRect(int r, int c, double cw, double ch) {
        double x = Math.round(c * cw), y = HEAD + Math.round(r * ch);
        return new Rectangle2D.Double(x, y, Math.round((c + 1) * cw) - x, HEAD + Math.round((r + 1) * ch) - y);
    }

    private void drawWeek(Graphics2D g2, LocalDate ws, double top, double cw, double ch, YearMonth ym) {
        List<Item> items = weekItems(ws);
        int maxLanes = Math.max(0, (int) ((ch - NUM_H - 3) / (BAR_H + 2)));
        List<boolean[]> lanes = new ArrayList<>();
        int[] laneOf = new int[items.size()];
        for (int i = 0; i < items.size(); i++) {
            Item it = items.get(i);
            int lane = 0;
            for (; ; lane++) {
                if (lane == lanes.size()) lanes.add(new boolean[7]);
                boolean free = true;
                for (int c = it.c0(); c <= it.c1(); c++) if (lanes.get(lane)[c]) { free = false; break; }
                if (free) break;
            }
            for (int c = it.c0(); c <= it.c1(); c++) lanes.get(lane)[c] = true;
            laneOf[i] = lane;
        }
        // 칸마다 보여 줄 줄 수: 다 들어가면 전부, 넘치면 한 줄을 "+N개 더" 자리로 비움
        // (칸이 아주 작으면 일정 하나만 보이고 "+N"은 날짜 옆에)
        int[] count = new int[7];
        for (Item it : items) for (int c = it.c0(); c <= it.c1(); c++) count[c]++;
        int[] limit = new int[7];
        boolean tiny = maxLanes <= 1;
        for (int c = 0; c < 7; c++) limit[c] = count[c] <= maxLanes || tiny ? maxLanes : maxLanes - 1;
        int[] hidden = new int[7];
        int arc = Theme.style() == Theme.Style.SF ? 2 : 7;
        for (int i = 0; i < items.size(); i++) {
            Item it = items.get(i);
            int lane = laneOf[i];
            boolean show = true;
            for (int c = it.c0(); c <= it.c1(); c++) if (lane >= limit[c]) show = false;
            if (!show) {
                for (int c = it.c0(); c <= it.c1(); c++) hidden[c]++;
                continue;
            }
            double y = top + NUM_H + lane * (BAR_H + 2);
            double x0 = Math.round(it.c0() * cw) + 4, x1 = Math.round((it.c1() + 1) * cw) - 4;
            Color col = HAZscheduleManager.colorOf(it.s());
            boolean inMonth = YearMonth.from(ws.plusDays(it.c0())).equals(ym) || YearMonth.from(ws.plusDays(it.c1())).equals(ym);
            int a = inMonth ? 255 : 120;
            hits.add(new Hit(new Rectangle((int) x0, (int) y, (int) (x1 - x0), BAR_H), it.s(), it.occ()));
            if (it.bar()) {
                RoundRectangle2D bar = new RoundRectangle2D.Double(x0, y, x1 - x0, BAR_H, arc, arc);
                g2.setColor(Ui.alpha(Ui.soft(col), a));
                g2.fill(bar);
                g2.setColor(Ui.alpha(col, a));
                g2.fill(new RoundRectangle2D.Double(x0, y, 4, BAR_H, 4, 4));
                g2.setFont(Ui.semi(11.5f));
                g2.setColor(Ui.alpha(Theme.onColor(Ui.soft(col)), a));
                String label = (it.occ().isBefore(ws) ? "… " : "") + it.s().title;
                g2.drawString(Ui.ellipsize(label, g2.getFontMetrics(), (int) (x1 - x0) - 14), (float) x0 + 9, (float) y + 14);
            } else {
                g2.setColor(Ui.alpha(col, a));
                g2.fill(new RoundRectangle2D.Double(x0 + 1, y + 3, 4, BAR_H - 6, 4, 4));
                g2.setFont(Ui.font(11.5f, false));
                FontMetrics fm = g2.getFontMetrics();
                String time = TimeText.fmt(it.s().start) + " ";
                int tw = fm.stringWidth(time);
                boolean showTime = (x1 - x0) > tw + 40;
                if (showTime) {
                    g2.setColor(Ui.alpha(Theme.sub(), a));
                    g2.drawString(time, (float) x0 + 10, (float) y + 14);
                } else tw = 0;
                g2.setColor(Ui.alpha(Theme.text(), a));
                g2.drawString(Ui.ellipsize(it.s().title, fm, (int) (x1 - x0) - 14 - tw), (float) x0 + 10 + tw, (float) y + 14);
            }
        }
        g2.setFont(Ui.font(11f, true));
        for (int c = 0; c < 7; c++) {
            if (hidden[c] == 0) continue;
            g2.setColor(Theme.sub());
            if (tiny) {   // 칸이 너무 작으면 날짜 숫자 바로 옆에
                String num = String.valueOf(ws.plusDays(c).getDayOfMonth());
                int nw = getFontMetrics(Ui.semi(13f)).stringWidth(num);
                g2.drawString("+" + hidden[c], (float) (Math.round(c * cw) + 9 + nw + 12), (float) top + 18);
            } else {
                double y = top + NUM_H + limit[c] * (BAR_H + 2);
                g2.drawString("+" + hidden[c] + "개 더", (float) (Math.round(c * cw) + 10), (float) y + 13);
            }
        }
    }
}
