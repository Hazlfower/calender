import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDate;
import java.time.YearMonth;

// 가운데 "달력" 페이지: 머리(◀ 2026년 10월 ▶ · 일/주/월 · 오늘) + 월/주/일 보기 + 선택한 날 일정 카드
public class CalendarView extends JPanel {
    private final MainFrame frame;
    private final ScheduleManager HAZscheduleManager;
    private final CardLayout cards = new CardLayout();
    private final JPanel views = Ui.clear(cards);
    final MonthView month;
    final TimeGridView week, day;
    private final Ui.Label periodTitle = new Ui.Label("", Ui.TEXT, Ui.semi(21f));
    private final Ui.Segmented viewSeg;
    private final DefaultListModel<Schedule> dayModel = new DefaultListModel<>();
    private final JList<Schedule> dayList = new JList<>(dayModel);
    private final JComponent dayCard;

    private static final String[] VIEWS = {"DAY", "WEEK", "MONTH"};

    public CalendarView(MainFrame frame, ScheduleManager sm) {
        super(new BorderLayout(0, 12));
        this.frame = frame;
        this.HAZscheduleManager = sm;
        setOpaque(false);

        month = new MonthView(frame, sm);
        week = new TimeGridView(frame, sm, 7);
        day = new TimeGridView(frame, sm, 1);
        views.add(month, "MONTH");
        views.add(week, "WEEK");
        views.add(day, "DAY");

        viewSeg = new Ui.Segmented(new String[]{"일", "주", "월"}, viewIndex(frame.getView()), i -> frame.setView(VIEWS[i]));
        viewSeg.setToolTipText("일 / 주 / 월 보기 (Ctrl+1/2/3)");

        Ui.RoundPanel calendarCard = Ui.RoundPanel.card(new BorderLayout(0, 6));
        calendarCard.setBorder(Ui.pad(10, 14, 12, 14));
        calendarCard.add(buildHeader(), BorderLayout.NORTH);
        calendarCard.add(views, BorderLayout.CENTER);

        dayCard = buildDayCard();
        add(calendarCard, BorderLayout.CENTER);
        add(dayCard, BorderLayout.SOUTH);
    }

    private static int viewIndex(String v) {
        for (int i = 0; i < VIEWS.length; i++) if (VIEWS[i].equals(v)) return i;
        return 2;
    }

    private JPanel buildHeader() {
        JButton prev = Ui.iconButton(Icons.Kind.LEFT, "이전 (Page Up)", false, () -> frame.shift(-1));
        JButton next = Ui.iconButton(Icons.Kind.RIGHT, "다음 (Page Down)", false, () -> frame.shift(1));
        Ui.FlatButton today = Ui.softButton("오늘");
        today.setFont(Ui.font(13, true));
        today.setToolTipText("오늘로 이동 (Ctrl+T)");
        today.addActionListener(e -> frame.goToDate(LocalDate.now()));
        periodTitle.setBorder(Ui.pad(0, 4, 0, 4));

        JPanel left = Ui.clear(new FlowLayout(FlowLayout.LEFT, 2, 0));
        left.add(prev);
        left.add(periodTitle);
        left.add(next);
        JPanel right = Ui.clear(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.add(viewSeg);
        right.add(today);

        JPanel header = Ui.clear(new BorderLayout());
        header.setBorder(Ui.pad(0, 0, 4, 0));
        header.add(left, BorderLayout.WEST);
        header.add(right, BorderLayout.EAST);
        return header;
    }

    // 선택한 날의 일정 (스케치의 "여기에 뭐 넣지" 자리)
    private JComponent buildDayCard() {
        dayList.setOpaque(false);
        dayList.setVisibleRowCount(4);
        dayList.setCellRenderer((l, s, i, sel, f) -> {
            JPanel p = new JPanel(new BorderLayout(10, 0));
            p.setOpaque(sel);
            p.setBackground(Ui.blend(Theme.highlight(), Theme.surface(), Theme.isNight() ? 0.3 : 0.14));
            p.setBorder(Ui.pad(5, 12, 5, 12));
            String time = s.allDay() ? (s.multiDay() ? s.timeText() : "하루 종일") : s.timeText();
            Ui.Label t = Ui.label(time, 12.5f, false, Ui.SUB);
            t.setIcon(Ui.dot(HAZscheduleManager.colorOf(s), 10));
            t.setIconTextGap(8);
            t.setPreferredSize(new Dimension(s.multiDay() ? 230 : 130, 20));
            String extra = (s.repeat != Schedule.HAZRepeat.NONE ? "  · " + s.repeatText() : "")
                    + (s.hasAlarm() ? "  · 알람 " + s.alarmText() : "") + "  · " + s.category;
            Ui.Label title = Ui.label("<html><b>" + Ui.html(s.title) + "</b><span style='color:" + Theme.hex(Theme.sub()) + "'>"
                    + Ui.html(extra) + "</span></html>", 13, false, Ui.TEXT);
            p.add(t, BorderLayout.WEST);
            p.add(title, BorderLayout.CENTER);
            return p;
        });
        dayList.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                int idx = dayList.locationToIndex(e.getPoint());
                if (idx < 0 || !dayList.getCellBounds(idx, idx).contains(e.getPoint())) return;
                dayList.setSelectedIndex(idx);
                Schedule s = dayModel.get(idx);
                LocalDate occ = s.occurrenceStart(frame.getSelectedDate());
                if (SwingUtilities.isRightMouseButton(e)) {
                    JPopupMenu m = new JPopupMenu();
                    m.add(Ui.item("수정", () -> frame.editSchedule(s, occ)));
                    m.add(Ui.item("삭제", () -> frame.confirmDelete(s, occ)));
                    m.show(dayList, e.getX(), e.getY());
                } else if (e.getClickCount() == 2) frame.editSchedule(s, occ);
            }
        });

        Ui.FlatButton add = Ui.headerButton("+ 추가");
        add.setToolTipText("일정 추가 (Ctrl+N)");
        Ui.FlatButton del = Ui.headerButton("삭제");
        add.addActionListener(e -> frame.addSchedule(frame.getSelectedDate(), -1, -1));
        del.addActionListener(e -> {
            Schedule s = dayList.getSelectedValue();
            if (s == null) Ui.info(frame, "삭제", "삭제할 일정을 목록에서 고르세요.");
            else frame.confirmDelete(s, s.occurrenceStart(frame.getSelectedDate()));
        });
        JPanel buttons = Ui.clear(new FlowLayout(FlowLayout.RIGHT, 4, 6));
        buttons.add(add);
        buttons.add(del);

        JScrollPane sp = Ui.scroll(dayList);
        sp.setBorder(Ui.pad(4, 2, 6, 2));
        JPanel body = Ui.clear(new BorderLayout());
        body.add(sp, BorderLayout.CENTER);
        Ui.Label empty = Ui.label("일정이 없어요. 더블클릭하거나 + 추가를 눌러 보세요.", 12.5f, false, Ui.SUB);
        empty.setBorder(Ui.pad(10, 14, 10, 14));
        body.putClientProperty("empty", empty);
        Ui.RoundPanel card = Ui.headerCard("", buttons, body);
        card.setPreferredSize(new Dimension(0, 146));
        return card;
    }

    public void setView(String v) {
        cards.show(views, v);
        viewSeg.setSelected(viewIndex(v));
        dayCard.setVisible(v.equals("MONTH"));   // 주·일 보기는 시간표가 넓게
        if (v.equals("WEEK")) week.scrollToMorning();
        if (v.equals("DAY")) day.scrollToMorning();
        refresh();
    }

    public void refresh() {
        LocalDate a = frame.getAnchor();
        switch (frame.getView()) {
            case "DAY":
                periodTitle.setText(a.getMonthValue() + "월 " + a.getDayOfMonth() + "일 " + TimeText.dow(a) + "요일");
                break;
            case "WEEK": {
                LocalDate s = frame.weekStart(a), e = s.plusDays(6);
                periodTitle.setText(s.getMonthValue() + "월 " + s.getDayOfMonth() + "일 – "
                        + (s.getMonth() == e.getMonth() ? "" : e.getMonthValue() + "월 ") + e.getDayOfMonth() + "일");
                break;
            }
            default:
                YearMonth ym = YearMonth.from(a);
                periodTitle.setText(Theme.style() == Theme.Style.SF ? String.format("%d.%02d", ym.getYear(), ym.getMonthValue())
                        : ym.getYear() + "년 " + ym.getMonthValue() + "월");
        }
        month.repaint();
        week.refresh();
        day.refresh();
        refreshDayList();
    }

    private void refreshDayList() {
        LocalDate d = frame.getSelectedDate();
        String hol = Holidays.get(d);
        String head = (d.equals(LocalDate.now()) ? "오늘 · " : "") + TimeText.md(d) + (hol != null ? "  ·  " + hol : "");
        Ui.cardTitle(dayCard).setText(head);
        Schedule keep = dayList.getSelectedValue();
        dayModel.clear();
        for (Schedule s : HAZscheduleManager.getByDate(d)) dayModel.addElement(s);
        if (keep != null) dayList.setSelectedValue(keep, false);
        JPanel body = (JPanel) ((BorderLayout) ((JComponent) dayCard).getLayout()).getLayoutComponent(BorderLayout.CENTER);
        JComponent empty = (JComponent) body.getClientProperty("empty");
        if (dayModel.isEmpty() && empty.getParent() == null) body.add(empty, BorderLayout.NORTH);
        if (!dayModel.isEmpty() && empty.getParent() != null) body.remove(empty);
        body.revalidate();
        body.repaint();
    }
}
