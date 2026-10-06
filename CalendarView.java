import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

// 가운데 달력 + 아래쪽 "선택한 날 일정" 카드
public class CalendarView extends JPanel {
    private final MainFrame frame;
    private final ScheduleManager HAZscheduleManager;
    private final JPanel HAZcalendarPanel = Ui.clear(new GridLayout(6, 7, 0, 0));
    private final JLabel monthLabel = Ui.label("", 22, true, Theme.text());
    private final DefaultListModel<Schedule> dayModel = new DefaultListModel<>();
    private final JList<Schedule> dayList = new JList<>(dayModel);
    private JLabel dayTitle;

    public CalendarView(MainFrame frame, ScheduleManager sm) {
        super(new BorderLayout(0, 12));
        this.frame = frame;
        this.HAZscheduleManager = sm;
        setOpaque(false);

        Ui.RoundPanel calendarCard = new Ui.RoundPanel(new BorderLayout(0, 6), Theme.surface(), Theme.border(), Theme.radius());
        calendarCard.setBorder(Ui.pad(12, 16, 14, 16));
        calendarCard.add(buildHeader(), BorderLayout.NORTH);
        JPanel grid = Ui.clear(new BorderLayout(0, 4));
        grid.add(buildWeekHeader(), BorderLayout.NORTH);
        grid.add(HAZcalendarPanel, BorderLayout.CENTER);
        calendarCard.add(grid, BorderLayout.CENTER);

        add(calendarCard, BorderLayout.CENTER);
        add(buildDayCard(), BorderLayout.SOUTH);
        refresh();
    }

    private JPanel buildHeader() {
        Ui.FlatButton prev = new Ui.FlatButton("<", Theme.surface(), Theme.text());
        Ui.FlatButton next = new Ui.FlatButton(">", Theme.surface(), Theme.text());
        prev.setFont(Ui.font(18, true));
        next.setFont(Ui.font(18, true));
        prev.addActionListener(e -> frame.setMonth(frame.getMonth().minusMonths(1)));
        next.addActionListener(e -> frame.setMonth(frame.getMonth().plusMonths(1)));

        Ui.FlatButton today = new Ui.FlatButton("오늘", Theme.secondary(), Theme.onColor(Theme.secondary()));
        today.setFont(Ui.font(13, true));
        today.addActionListener(e -> frame.goToDate(LocalDate.now()));

        JPanel left = Ui.clear(new FlowLayout(FlowLayout.LEFT, 4, 0));
        left.add(prev);
        left.add(monthLabel);
        left.add(next);
        JPanel header = Ui.clear(new BorderLayout());
        header.add(left, BorderLayout.WEST);
        header.add(today, BorderLayout.EAST);
        return header;
    }

    private JPanel buildWeekHeader() {
        JPanel week = Ui.clear(new GridLayout(1, 7));
        String[] names = Theme.style() == Theme.Style.SF
                ? new String[]{"SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT"}
                : new String[]{"일", "월", "화", "수", "목", "금", "토"};
        for (int i = 0; i < 7; i++) {
            JLabel l = Ui.label(names[i], 12, true, i == 0 ? Theme.danger() : i == 6 ? Theme.accent() : Theme.sub());
            l.setHorizontalAlignment(SwingConstants.CENTER);
            week.add(l);
        }
        return week;
    }

    // "여기에 뭐 넣지" 자리 → 선택한 날의 일정 목록
    private JComponent buildDayCard() {
        dayList.setOpaque(false);
        dayList.setVisibleRowCount(4);
        dayList.setCellRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean sel, boolean f) {
                JLabel c = (JLabel) super.getListCellRendererComponent(l, v, i, sel, f);
                Schedule s = (Schedule) v;
                c.setIcon(Ui.dot(HAZscheduleManager.colorOf(s.getCategory()), 10));
                c.setIconTextGap(8);
                c.setOpaque(sel);
                c.setBorder(Ui.pad(4, 8, 4, 8));
                return c;
            }
        });
        dayList.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && dayList.getSelectedValue() != null) frame.openEditor(dayList.getSelectedValue());
            }
        });

        Ui.FlatButton add = new Ui.FlatButton("+ 추가", Ui.blend(Theme.onPrimary(), Theme.primary(), 0.2), Theme.onPrimary());
        Ui.FlatButton del = new Ui.FlatButton("삭제", Ui.blend(Theme.onPrimary(), Theme.primary(), 0.2), Theme.onPrimary());
        add.setFont(Ui.font(12, true));
        del.setFont(Ui.font(12, true));
        add.setBorder(Ui.pad(3, 10, 3, 10));
        del.setBorder(Ui.pad(3, 10, 3, 10));
        add.addActionListener(e -> frame.openEditor(null));
        del.addActionListener(e -> {
            Schedule s = dayList.getSelectedValue();
            if (s == null) JOptionPane.showMessageDialog(frame, "삭제할 일정을 선택하세요.");
            else frame.confirmDelete(s);
        });
        JPanel buttons = Ui.clear(new FlowLayout(FlowLayout.RIGHT, 4, 5));
        buttons.add(add);
        buttons.add(del);

        JScrollPane sp = Ui.scroll(dayList);
        sp.setBorder(Ui.pad(4, 6, 6, 6));
        Ui.RoundPanel card = Ui.headerCard("", buttons, sp);
        dayTitle = (JLabel) ((JPanel) card.getComponent(0)).getComponent(0);
        card.setPreferredSize(new Dimension(0, 150));
        return card;
    }

    public void refresh() {
        YearMonth month = frame.getMonth();
        LocalDate selected = frame.getSelectedDate();
        monthLabel.setText(Theme.style() == Theme.Style.SF
                ? String.format("%d.%02d", month.getYear(), month.getMonthValue())
                : month.getYear() + "년 " + month.getMonthValue() + "월");

        HAZcalendarPanel.removeAll();
        LocalDate first = month.atDay(1);
        LocalDate cursor = first.minusDays(first.getDayOfWeek().getValue() % 7);
        for (int i = 0; i < 42; i++) {
            HAZcalendarPanel.add(new DayCell(cursor, i % 7, i / 7));
            cursor = cursor.plusDays(1);
        }
        HAZcalendarPanel.revalidate();
        HAZcalendarPanel.repaint();

        String holiday = Holidays.get(selected);
        dayTitle.setText(selected.getMonthValue() + "월 " + selected.getDayOfMonth() + "일 ("
                + Schedule.dowName(selected.getDayOfWeek()) + ")" + (holiday != null ? " · " + holiday : ""));
        dayModel.clear();
        for (Schedule s : HAZscheduleManager.getByDate(selected)) dayModel.addElement(s);
    }

    // 달력 한 칸
    private class DayCell extends JComponent {
        private final LocalDate date;
        private final int col, row;

        DayCell(LocalDate date, int col, int row) {
            this.date = date;
            this.col = col;
            this.row = row;
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) {
                    frame.selectDate(date);
                    if (e.getClickCount() == 2) frame.openEditor(null);
                }
            });
            List<Schedule> items = HAZscheduleManager.getByDate(date);
            if (!items.isEmpty()) {
                StringBuilder tip = new StringBuilder("<html>");
                for (Schedule s : items) tip.append(s.getTitle()).append(" · ").append(s.timeText()).append("<br>");
                setToolTipText(tip.append("</html>").toString());
            }
        }

        @Override protected void paintComponent(Graphics g) {
            Ui.smooth(g);
            Graphics2D g2 = (Graphics2D) g;
            int w = getWidth(), h = getHeight();
            boolean inMonth = YearMonth.from(date).equals(frame.getMonth());
            boolean selected = date.equals(frame.getSelectedDate());
            boolean today = date.equals(LocalDate.now());
            String holiday = Holidays.get(date);

            if (!inMonth) {
                g2.setColor(Ui.blend(Theme.bg(), Theme.surface(), 0.5));
                g2.fillRect(0, 0, w, h);
            }
            // 격자선
            g2.setColor(Theme.border());
            if (col < 6) g2.drawLine(w - 1, 0, w - 1, h);
            if (row < 5) g2.drawLine(0, h - 1, w, h - 1);

            // 날짜 숫자
            Color num = Theme.text();
            if (date.getDayOfWeek() == DayOfWeek.SUNDAY || holiday != null) num = Theme.danger();
            else if (date.getDayOfWeek() == DayOfWeek.SATURDAY) num = Theme.accent();
            if (!inMonth) num = Ui.blend(num, Theme.surface(), 0.4);
            g2.setFont(Ui.font(13, true));
            String n = String.valueOf(date.getDayOfMonth());
            if (today) {
                g2.setColor(Theme.highlight());
                g2.fillOval(3, 2, 22, 20);
                num = Theme.onColor(Theme.highlight());
            }
            g2.setColor(num);
            g2.drawString(n, 14 - g2.getFontMetrics().stringWidth(n) / 2, 17);
            if (holiday != null) {
                g2.setFont(Ui.font(10, false));
                g2.setColor(Theme.danger());
                g2.drawString(holiday, 28, 16);
            }

            // 일정 막대 (여러 날 일정은 옆 칸과 이어짐)
            List<Schedule> items = HAZscheduleManager.getByDate(date);
            g2.setFont(Ui.font(10, false));
            int y = 25, barH = 15, shown = 0, max = Math.max(1, (h - 30) / (barH + 2));
            for (Schedule s : items) {
                if (shown == max) break;
                Color c = HAZscheduleManager.colorOf(s.getCategory());
                int k = s.dayIndexOn(date);
                boolean joinLeft = s.isMultiDay() && k > 0;
                boolean joinRight = s.isMultiDay() && k < s.spanDays();
                int x0 = joinLeft ? 0 : 4, x1 = joinRight ? w : w - 5;
                g2.setColor(Ui.blend(c, Theme.surface(), inMonth ? 0.75 : 0.35));
                int r = Math.min(Theme.radius(), 10);
                g2.fillRoundRect(x0, y, x1 - x0, barH, r, r);
                if (joinLeft) g2.fillRect(0, y, 6, barH);
                if (joinRight) g2.fillRect(w - 6, y, 6, barH);
                if (!joinLeft || col == 0) {
                    g2.setColor(Theme.onColor(c));
                    Shape old = g2.getClip();
                    g2.clipRect(x0, y, (joinRight ? w : x1) - x0 - 3, barH);
                    g2.drawString(s.getTitle(), x0 + 5, y + 11);
                    g2.setClip(old);
                }
                y += barH + 2;
                shown++;
            }
            if (items.size() > shown) {
                g2.setColor(Theme.sub());
                g2.drawString("+" + (items.size() - shown), w - 22, h - 4);
            }

            // 선택한 날: 주 색상 테두리 (스케치의 남색 네모)
            if (selected) {
                g2.setColor(Theme.highlight());
                g2.setStroke(new BasicStroke(2.2f));
                int r = Math.min(Theme.radius(), 10);
                g2.drawRoundRect(2, 2, w - 5, h - 5, r, r);
            }
        }
    }
}
