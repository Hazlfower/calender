import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.geom.Ellipse2D;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

// 일정 추가/수정 화면: 메인 창 위를 어둡게 덮고, 왼쪽 = 일정 설정 / 오른쪽 = 시간 테트리스
public class ScheduleEditor extends JPanel {
    public enum Mode { NEW, EDIT_ALL, EDIT_ONE }

    static final String[] PALETTE = {"#FF9FB2", "#FFC48C", "#F5CF5F", "#B6B1F4", "#8CC6F0", "#7DD6B0",
            "#C3D66C", "#C898E4", "#FF6B6B", "#38C2B1", "#3D97E8"};
    private static final String[] TIMES = new String[48];
    private static final int[] ALARM_CHOICES = {0, 1, 5, 10, 15, 30, 60, 120, 180, 1440};

    static {
        for (int i = 0; i < 48; i++) TIMES[i] = TimeText.fmt(i * 30);
    }

    private final MainFrame frame;
    private final ScheduleManager HAZscheduleManager;
    private final AppSettings HAZsettings;
    private final Mode mode;
    private final Schedule original;     // 수정할 때 원래 일정 (새 일정이면 null)
    private final Schedule work;         // 편집 중인 복사본
    private final LocalDate occ;         // EDIT_ONE: 원래 반복 일정의 그 날

    private final JTextField titleField = new JTextField(16);
    private final JComboBox<String> categoryBox = new JComboBox<>();
    private final List<Ui.ColorDot> dots = new ArrayList<>();
    private final JPanel dotRow = Ui.clear(new FlowLayout(FlowLayout.LEFT, 0, 0));
    private Ui.ColorDot customDot;
    private String chosenColor;

    private final DatePicker startDate, endDate, untilDate;
    private final JCheckBox multiCheck = new JCheckBox("여러 날");
    private final JLabel tilde = Ui.label("~", 13, false, Ui.SUB);
    private final JCheckBox allDayCheck = new JCheckBox("하루 종일");
    private final JComboBox<String> startBox = timeBox("시작"), endBox = timeBox("끝");
    private JPanel timeRow;

    private final JComboBox<Schedule.HAZRepeat> repeatBox = new JComboBox<>(Schedule.HAZRepeat.values());
    private final JSpinner intervalSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 99, 1));
    private final Ui.Label intervalUnit = Ui.label("", 13, false, Ui.TEXT);
    private final JToggleButton[] dayButtons = new JToggleButton[7];
    private final DayOfWeek[] dayOrder = new DayOfWeek[7];
    private final JRadioButton byDay = new JRadioButton(), byNth = new JRadioButton();
    private final JRadioButton forever = new JRadioButton("계속 반복"), untilRadio = new JRadioButton("종료일");
    private final DefaultListModel<LocalDate> datesModel = new DefaultListModel<>();
    private final Ui.Label repeatSummary = Ui.label(" ", 12.5f, true, Ui.HIGHLIGHT);
    private JPanel intervalRow, weekRow, monthRow, datesRow, untilRow, repeatPanel;

    private final JPanel alarmRows = Ui.clear(null);
    private final List<JComboBox<String>> alarmBoxes = new ArrayList<>();
    private final Ui.Label alarmNote = Ui.label("시간이 정해진 일정만 알람을 쓸 수 있어요", 12, false, Ui.SUB);
    private final JTextField linkField = new JTextField(16);
    private final JTextArea memoArea = new JTextArea(3, 16);

    private final TimelinePanel timeline;
    private final JComboBox<String> bufferBox = new JComboBox<>(new String[]{"여유 없음", "여유 10분", "여유 20분", "여유 30분"});
    private boolean syncing;

    public ScheduleEditor(MainFrame frame, ScheduleManager sm, AppSettings settings, Mode mode, Schedule original, Schedule work, LocalDate occ) {
        super(new GridBagLayout());
        this.frame = frame;
        this.HAZscheduleManager = sm;
        this.HAZsettings = settings;
        this.mode = mode;
        this.original = original;
        this.work = work;
        this.occ = occ;
        setOpaque(false);
        addMouseListener(new MouseAdapter() { });          // 뒤 화면 클릭 막기
        addMouseMotionListener(new MouseAdapter() { });
        addMouseWheelListener(e -> { });

        startDate = new DatePicker(work.date);
        endDate = new DatePicker(work.endDate != null ? work.endDate : work.date.plusDays(1));
        untilDate = new DatePicker(work.until != null ? work.until : work.date.plusMonths(3));
        timeline = new TimelinePanel(this::onTimelineSelect);

        buildDayToggles();
        fillValues();

        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.BOTH;
        c.insets = new Insets(20, 10, 20, 10);
        c.weighty = 1;
        add(buildFormCard(), c);
        add(buildTetrisCard(), c);

        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "close");
        getActionMap().put("close", new AbstractAction() {
            public void actionPerformed(java.awt.event.ActionEvent e) { close(); }
        });
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, KeyEvent.CTRL_DOWN_MASK), "save");
        getActionMap().put("save", new AbstractAction() {
            public void actionPerformed(java.awt.event.ActionEvent e) { save(); }
        });

        wire();
        updateAll();
        reloadTimeline();
        SwingUtilities.invokeLater(() -> {
            titleField.requestFocusInWindow();
            titleField.selectAll();
        });
    }

    // ================================================================== 구성

    private static JComboBox<String> timeBox(String placeholder) {
        JComboBox<String> b = new JComboBox<>(TIMES);
        b.setEditable(true);
        b.setSelectedItem("");
        b.setMaximumRowCount(10);
        b.setPrototypeDisplayValue("오후 12:30");
        editor(b).putClientProperty("placeholder", placeholder);
        return b;
    }

    private static JTextField editor(JComboBox<String> b) { return (JTextField) b.getEditor().getEditorComponent(); }

    private static void setTime(JComboBox<String> b, int min) { b.setSelectedItem(min < 0 ? "" : TimeText.fmt(min)); }

    private void buildDayToggles() {
        DayOfWeek first = frame.firstDay();
        for (int i = 0; i < 7; i++) {
            dayOrder[i] = first.plus(i);
            DayOfWeek dw = dayOrder[i];
            JToggleButton b = new JToggleButton(TimeText.dow(dw)) {
                @Override protected void paintComponent(Graphics g) {
                    Graphics2D g2 = Ui.aa(g);
                    Color fill = isSelected() ? Theme.highlight() : Theme.field();
                    Ellipse2D circle = new Ellipse2D.Float(1, 1, getWidth() - 3, getHeight() - 3);
                    g2.setColor(fill);
                    g2.fill(circle);
                    g2.setColor(isSelected() ? fill : Theme.border());
                    g2.draw(circle);
                    g2.setFont(getFont());
                    g2.setColor(isSelected() ? Theme.onColor(fill) : dw == DayOfWeek.SUNDAY ? Theme.danger() : dw == DayOfWeek.SATURDAY ? Theme.saturday() : Theme.text());
                    FontMetrics fm = g2.getFontMetrics();
                    g2.drawString(getText(), (getWidth() - fm.stringWidth(getText())) / 2f, (getHeight() + fm.getAscent() - fm.getDescent()) / 2f);
                    g2.dispose();
                }
            };
            b.setFont(Ui.font(12, true));
            b.setPreferredSize(new Dimension(30, 30));
            b.setContentAreaFilled(false);
            b.setBorderPainted(false);
            b.setFocusPainted(false);
            b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            b.addActionListener(e -> updateSummary());
            dayButtons[i] = b;
        }
    }

    private void fillValues() {
        reloadCategories(work.category);
        categoryBox.setRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean s, boolean f) {
                JLabel lb = (JLabel) super.getListCellRendererComponent(l, v, i, s, f);
                if (v != null) lb.setIcon(Ui.dot(HAZscheduleManager.colorOf((String) v), 10));
                lb.setIconTextGap(8);
                lb.setBorder(Ui.pad(4, 6, 4, 6));
                return lb;
            }
        });
        ButtonGroup g1 = new ButtonGroup();
        g1.add(byDay);
        g1.add(byNth);
        ButtonGroup g2 = new ButtonGroup();
        g2.add(forever);
        g2.add(untilRadio);
        memoArea.setLineWrap(true);
        memoArea.setWrapStyleWord(true);
        titleField.putClientProperty("placeholder", "일정 제목");
        linkField.putClientProperty("placeholder", "예) 티켓팅 사이트 주소 (선택)");
        memoArea.putClientProperty("placeholder", "메모 (선택)");

        titleField.setText(work.title);
        chosenColor = work.color;
        multiCheck.setSelected(work.multiDay());
        allDayCheck.setSelected(work.allDay() && mode != Mode.NEW);
        if (work.start >= 0) {
            setTime(startBox, work.start);
            setTime(endBox, work.end);
        }
        repeatBox.setSelectedItem(work.repeat);
        intervalSpinner.setValue(Math.max(1, work.interval));
        Set<DayOfWeek> wd = work.weekdays.isEmpty() ? EnumSet.of(work.date.getDayOfWeek()) : work.weekdays;
        for (int i = 0; i < 7; i++) dayButtons[i].setSelected(wd.contains(dayOrder[i]));
        (work.monthlyNth ? byNth : byDay).setSelected(true);
        (work.until != null ? untilRadio : forever).setSelected(true);
        for (LocalDate d : work.extraDates) datesModel.addElement(d);
        List<Integer> al = new ArrayList<>(work.alarms);
        if (mode == Mode.NEW && al.isEmpty()) al.add(10);
        for (int a : al) addAlarmRow(a);
        linkField.setText(work.link);
        memoArea.setText(work.memo);
        bufferBox.setSelectedIndex(Math.max(0, Math.min(3, HAZsettings.getInt("tetrisBuffer", 0) / 10)));
    }

    private void reloadCategories(String select) {
        categoryBox.removeAllItems();
        for (String name : HAZscheduleManager.getCategories().keySet()) categoryBox.addItem(name);
        if (select != null && HAZscheduleManager.getCategories().containsKey(select)) categoryBox.setSelectedItem(select);
    }

    // ----- 왼쪽: 일정 설정 카드 -----
    private JComponent buildFormCard() {
        Form f = new Form();

        f.label("제목");
        titleField.setFont(Ui.font(15, false));
        f.field(titleField);

        f.label("카테고리 · 색");
        Ui.FlatButton addCat = Ui.button("+ 새 카테고리");
        addCat.setFont(Ui.font(12, false));
        addCat.addActionListener(e -> {
            String name = frame.askNewCategory();
            if (name != null) reloadCategories(name);
        });
        categoryBox.addActionListener(e -> refreshDots());
        f.field(Ui.row(categoryBox, 6, addCat));
        buildDots();
        f.field(dotRow);

        f.label("날짜");
        f.field(Ui.row(startDate, 6, tilde, 6, endDate, 10, multiCheck));

        f.label("시간");
        Ui.Label hint = Ui.label("‘오후 6시 반’, ‘18:30’ 처럼 써도 돼요", 11.5f, false, Ui.SUB);
        timeRow = Ui.row(startBox, 6, Ui.label("~", 13, false, Ui.SUB), 6, endBox);
        f.field(Ui.row(allDayCheck));
        f.field(timeRow);
        f.field(hint);
        timeRow.putClientProperty("hint", hint);

        f.label("반복");
        repeatPanel = new Ui.RoundPanel(null, null, null, -1) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = Ui.aa(g);
                g2.setColor(Ui.blend(Theme.secondary(), Theme.surface(), Theme.isNight() ? 0.35 : 0.3));
                int r = Theme.radius();
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), r, r);
                g2.dispose();
            }
        };
        repeatPanel.setLayout(new BoxLayout(repeatPanel, BoxLayout.Y_AXIS));
        repeatPanel.setBorder(Ui.pad(10, 12, 10, 12));
        repeatPanel.add(Ui.row(repeatBox));
        intervalSpinner.setPreferredSize(new Dimension(64, 30));
        intervalRow = Ui.row(intervalSpinner, 6, intervalUnit);
        weekRow = Ui.row((Object[]) toggleItems());
        monthRow = Ui.row(byDay, 10, byNth);
        datesRow = buildDatesEditor();
        untilRow = Ui.row(forever, 10, untilRadio, 6, untilDate);
        for (JComponent row : new JComponent[]{intervalRow, weekRow, monthRow, datesRow, untilRow}) {
            row.setBorder(Ui.pad(8, 0, 0, 0));      // 숨긴 줄은 자리도 안 차지하게 간격을 줄 안에 둠
            repeatPanel.add(row);
        }
        repeatSummary.setAlignmentX(0f);
        repeatSummary.setBorder(Ui.pad(8, 2, 0, 0));
        repeatPanel.add(repeatSummary);
        f.field(repeatPanel);

        f.label("알람");
        alarmRows.setLayout(new BoxLayout(alarmRows, BoxLayout.Y_AXIS));
        Ui.FlatButton addAlarm = Ui.button("+ 알람 추가");
        addAlarm.setFont(Ui.font(12, false));
        addAlarm.setIcon(new Icons(Icons.Kind.BELL, 14));
        addAlarm.addActionListener(e -> {
            addAlarmRow(alarmBoxes.isEmpty() ? 10 : 60);
            updateAlarmState();
        });
        f.field(alarmRows);
        f.field(Ui.row(addAlarm, 10, alarmNote));

        f.label("링크");
        f.field(linkField);
        f.label("메모");
        JScrollPane memoScroll = Ui.fieldScroll(memoArea);
        memoScroll.setPreferredSize(new Dimension(200, 70));
        memoScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 70));
        f.field(memoScroll);

        JPanel holder = new Ui.WidthTracking(new BorderLayout());
        holder.add(f, BorderLayout.NORTH);
        JScrollPane formScroll = Ui.scroll(holder);
        formScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

        Ui.FlatButton save = Ui.primaryButton("저장");
        Ui.FlatButton cancel = Ui.button("취소");
        save.setToolTipText("저장 (Ctrl+Enter)");
        cancel.setToolTipText("닫기 (Esc)");
        save.addActionListener(e -> save());
        cancel.addActionListener(e -> close());
        JPanel foot = Ui.clear(new BorderLayout());
        foot.setBorder(Ui.pad(10, 16, 14, 16));
        JPanel right = Ui.clear(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        right.add(cancel);
        right.add(save);
        foot.add(right, BorderLayout.EAST);
        if (mode != Mode.NEW) {
            Ui.FlatButton delete = Ui.dangerButton(mode == Mode.EDIT_ONE ? "이 날만 삭제" : "삭제");
            delete.setIcon(new Icons(Icons.Kind.TRASH, 14));
            delete.addActionListener(e -> delete());
            foot.add(delete, BorderLayout.WEST);
        }

        JPanel body = Ui.clear(new BorderLayout());
        body.add(formScroll, BorderLayout.CENTER);
        body.add(foot, BorderLayout.SOUTH);
        String title = mode == Mode.NEW ? "새 일정" : mode == Mode.EDIT_ONE ? "일정 수정 · " + TimeText.md(occ) + "만" : "일정 수정";
        Ui.RoundPanel card = Ui.headerCard(title, null, body);
        card.setPreferredSize(new Dimension(500, 680));
        card.setMinimumSize(new Dimension(420, 300));
        return card;
    }

    private Object[] toggleItems() {
        Object[] items = new Object[13];
        for (int i = 0; i < 7; i++) {
            items[i * 2] = dayButtons[i];
            if (i < 6) items[i * 2 + 1] = 4;
        }
        return items;
    }

    private JPanel buildDatesEditor() {
        JList<LocalDate> list = new JList<>(datesModel);
        list.setVisibleRowCount(3);
        list.setCellRenderer((l, v, i, sel, f) -> {
            Ui.Label lb = Ui.label(TimeText.ymd(v), 12.5f, false, Ui.TEXT);
            lb.setBorder(Ui.pad(3, 8, 3, 8));
            lb.setOpaque(sel);
            lb.setBackground(Ui.blend(Theme.highlight(), Theme.surface(), 0.25));
            return lb;
        });
        Ui.FlatButton add = Ui.button("+ 날짜");
        Ui.FlatButton remove = Ui.button("빼기");
        add.setFont(Ui.font(12, false));
        remove.setFont(Ui.font(12, false));
        add.addActionListener(e -> DatePicker.popup(add, startDate.getDate() == null ? LocalDate.now() : startDate.getDate(), d -> {
            if (!datesModel.contains(d) && !d.equals(startDate.getDate())) {
                List<LocalDate> all = new ArrayList<>();
                for (int i = 0; i < datesModel.size(); i++) all.add(datesModel.get(i));
                all.add(d);
                all.sort(null);
                datesModel.clear();
                for (LocalDate x : all) datesModel.addElement(x);
                updateSummary();
            }
        }));
        remove.addActionListener(e -> {
            LocalDate d = list.getSelectedValue();
            if (d != null) { datesModel.removeElement(d); updateSummary(); }
        });
        JScrollPane sp = new JScrollPane(list);
        sp.setBorder(Ui.roundLine(8));
        sp.setPreferredSize(new Dimension(190, 74));
        JPanel b = Ui.clear(new GridLayout(2, 1, 0, 4));
        b.add(add);
        b.add(remove);
        JPanel p = Ui.clear(new BorderLayout(8, 0));
        p.add(sp, BorderLayout.CENTER);
        p.add(b, BorderLayout.EAST);
        p.setAlignmentX(0f);
        p.setMaximumSize(new Dimension(320, 80));
        JPanel wrap = Ui.clear(new BorderLayout());
        wrap.add(p, BorderLayout.WEST);
        wrap.setAlignmentX(0f);
        wrap.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));
        return wrap;
    }

    private void buildDots() {
        dotRow.removeAll();
        dots.clear();
        Ui.ColorDot cat = new Ui.ColorDot(Color.GRAY, false, () -> pickColor(null));
        cat.setToolTipText("카테고리 색 그대로");
        dots.add(cat);
        dotRow.add(cat);
        for (String h : PALETTE) {
            Ui.ColorDot d = new Ui.ColorDot(Theme.decode(h, "#888888"), false, () -> pickColor(h));
            d.setToolTipText(h);
            dots.add(d);
            dotRow.add(d);
        }
        customDot = new Ui.ColorDot(Color.GRAY, false, () -> { });
        customDot.setVisible(false);
        dotRow.add(customDot);
        Ui.ColorDot plus = new Ui.ColorDot(null, true, () -> {
            Color c = JColorChooser.showDialog(frame, "일정 색 고르기", chosenColor == null ? Color.PINK : Theme.decode(chosenColor, "#FFC0CB"));
            if (c != null) pickColor(Theme.hex(c));
        });
        plus.setToolTipText("다른 색 고르기");
        dotRow.add(plus);
        dotRow.setAlignmentX(0f);
        dotRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        refreshDots();
    }

    private void pickColor(String hex) {
        chosenColor = hex;
        refreshDots();
    }

    private void refreshDots() {
        if (dots.isEmpty()) return;
        String cat = (String) categoryBox.getSelectedItem();
        dots.get(0).color = HAZscheduleManager.colorOf(cat);
        boolean matched = chosenColor == null;
        dots.get(0).selected = chosenColor == null;
        for (int i = 0; i < PALETTE.length; i++) {
            boolean sel = PALETTE[i].equalsIgnoreCase(chosenColor == null ? "" : chosenColor);
            dots.get(i + 1).selected = sel;
            matched |= sel;
        }
        customDot.setVisible(!matched);
        if (!matched) {
            customDot.color = Theme.decode(chosenColor, "#888888");
            customDot.selected = true;
        }
        dotRow.revalidate();
        dotRow.repaint();
    }

    private void addAlarmRow(int minutes) {
        JComboBox<String> box = new JComboBox<>();
        List<Integer> mins = new ArrayList<>();
        for (int m : ALARM_CHOICES) mins.add(m);
        if (!mins.contains(minutes)) { mins.add(minutes); mins.sort(null); }
        for (int m : mins) box.addItem(TimeText.before(m).equals("정각") ? "정각에" : TimeText.before(m));
        box.putClientProperty("mins", mins);
        box.setSelectedIndex(mins.indexOf(minutes));
        JButton remove = Ui.iconButton(Icons.Kind.CLOSE, "이 알람 빼기", false, null);
        remove.setPreferredSize(new Dimension(30, 30));
        JPanel row = Ui.row(Ui.label("시작", 13, false, Ui.TEXT), 8, box, 4, remove);
        row.setBorder(Ui.pad(0, 0, 6, 0));
        remove.addActionListener(e -> {
            alarmRows.remove(row);
            alarmBoxes.remove(box);
            alarmRows.revalidate();
            alarmRows.repaint();
        });
        alarmBoxes.add(box);
        alarmRows.add(row);
        alarmRows.revalidate();
    }

    // ----- 오른쪽: 시간 테트리스 카드 -----
    private JComponent buildTetrisCard() {
        JScrollPane sp = Ui.scroll(timeline);
        sp.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        sp.getVerticalScrollBar().setUnitIncrement(TimelinePanel.CELL_H);
        sp.setBorder(Ui.pad(6, 8, 10, 4));

        JPanel head = Ui.clear(null);
        head.setLayout(new BoxLayout(head, BoxLayout.Y_AXIS));
        head.setBorder(Ui.pad(10, 14, 0, 12));
        Ui.Label sub = Ui.label("빈 칸을 드래그해서 시간을 골라요", 12, false, Ui.SUB);
        sub.setAlignmentX(0f);
        head.add(sub);
        head.add(Box.createVerticalStrut(8));
        bufferBox.addActionListener(e -> {
            HAZsettings.setInt("tetrisBuffer", bufferBox.getSelectedIndex() * 10);
            HAZsettings.save();
            reloadTimeline();
        });
        head.add(Ui.row(legend("있는 일정", Theme.sub(), 200), 10, legend("여유", Theme.danger(), 70), 10,
                legend("선택", Theme.highlight(), 255), 10, bufferBox));

        JPanel body = Ui.clear(new BorderLayout());
        body.add(head, BorderLayout.NORTH);
        body.add(sp, BorderLayout.CENTER);
        Ui.RoundPanel card = Ui.headerCard("시간 테트리스", null, body);
        card.setPreferredSize(new Dimension(timeline.getPreferredSize().width + 40, 680));
        card.setMinimumSize(new Dimension(timeline.getPreferredSize().width + 40, 300));
        // 화면에 처음 보일 때 고른 시간(없으면 8시) 근처로 스크롤
        timeline.addHierarchyListener(e -> {
            if ((e.getChangeFlags() & java.awt.event.HierarchyEvent.SHOWING_CHANGED) != 0 && timeline.isShowing())
                SwingUtilities.invokeLater(() -> timeline.scrollRectToVisible(new Rectangle(0, Math.max(0, timeline.focusY() - 90), 10, sp.getViewport().getHeight())));
        });
        return card;
    }

    private static JLabel legend(String text, Color c, int alpha) {
        Ui.Label l = Ui.label(text, 11.5f, false, Ui.SUB);
        l.setIcon(Ui.dot(Ui.alpha(c, alpha), 9));
        l.setIconTextGap(4);
        return l;
    }

    // ================================================================== 상태 갱신

    private void wire() {
        multiCheck.addActionListener(e -> updateAll());
        allDayCheck.addActionListener(e -> updateAll());
        repeatBox.addActionListener(e -> updateAll());
        intervalSpinner.addChangeListener(e -> updateSummary());
        byDay.addActionListener(e -> updateSummary());
        byNth.addActionListener(e -> updateSummary());
        forever.addActionListener(e -> updateAll());
        untilRadio.addActionListener(e -> updateAll());
        startDate.setOnChange(() -> {
            LocalDate s = startDate.getDate();
            LocalDate e = endDate.getDate();
            if (s != null && multiCheck.isSelected() && (e == null || !e.isAfter(s))) endDate.setDate(s.plusDays(1));
            reloadTimeline();
            updateAll();
        });
        endDate.setOnChange(this::updateTimelineState);
        untilDate.setOnChange(this::updateSummary);
        DocumentListener timeTyped = doc(() -> SwingUtilities.invokeLater(this::timeTyped));
        editor(startBox).getDocument().addDocumentListener(timeTyped);
        editor(endBox).getDocument().addDocumentListener(timeTyped);
        startBox.addActionListener(e -> timeTyped());
        endBox.addActionListener(e -> timeTyped());
    }

    private void onTimelineSelect(int a, int b) {
        syncing = true;
        setTime(startBox, a);
        setTime(endBox, b);
        syncing = false;
        updateAlarmState();
    }

    private void timeTyped() {
        if (syncing) return;
        int a = TimeText.parse(editor(startBox).getText()), b = TimeText.parse(editor(endBox).getText());
        timeline.showSelection(a, b);
        updateAlarmState();
    }

    private void updateAll() {
        boolean multi = multiCheck.isSelected(), allDay = allDayCheck.isSelected();
        tilde.setVisible(multi);
        endDate.setVisible(multi);
        timeRow.setVisible(!allDay);
        ((JComponent) timeRow.getClientProperty("hint")).setVisible(!allDay);
        Schedule.HAZRepeat r = (Schedule.HAZRepeat) repeatBox.getSelectedItem();
        boolean periodic = r != Schedule.HAZRepeat.NONE && r != Schedule.HAZRepeat.DATES;
        intervalRow.setVisible(periodic);
        weekRow.setVisible(r == Schedule.HAZRepeat.WEEKLY);
        monthRow.setVisible(r == Schedule.HAZRepeat.MONTHLY);
        datesRow.setVisible(r == Schedule.HAZRepeat.DATES);
        untilRow.setVisible(periodic);
        repeatSummary.setVisible(r != Schedule.HAZRepeat.NONE);
        intervalUnit.setText(r.unit + "마다");
        untilDate.setEnabled(untilRadio.isSelected());
        LocalDate d = startDate.getDate() == null ? LocalDate.now() : startDate.getDate();
        byDay.setText("매월 " + d.getDayOfMonth() + "일");
        byNth.setText("매월 " + Schedule.nthText(Schedule.nth(d)) + " " + TimeText.dow(d) + "요일");
        updateSummary();
        updateTimelineState();
        updateAlarmState();
        revalidate();
        repaint();
    }

    private void updateTimelineState() {
        if (allDayCheck.isSelected()) timeline.setDisabledText("하루 종일 일정은 시간 칸을 쓰지 않아요");
        else if (multiCheck.isSelected()) timeline.setDisabledText("여러 날 일정은 시간 칸을 쓰지 않아요");
        else timeline.setDisabledText(null);
    }

    private void updateAlarmState() {
        boolean hasTime = !allDayCheck.isSelected();
        alarmNote.setVisible(!hasTime);
        alarmRows.setVisible(hasTime);
        alarmRows.revalidate();
    }

    private Schedule.HAZRepeat repeat() { return (Schedule.HAZRepeat) repeatBox.getSelectedItem(); }

    private void updateSummary() {
        Schedule p = new Schedule();
        p.date = startDate.getDate() == null ? LocalDate.now() : startDate.getDate();
        p.repeat = repeat();
        p.interval = (Integer) intervalSpinner.getValue();
        for (int i = 0; i < 7; i++) if (dayButtons[i].isSelected()) p.weekdays.add(dayOrder[i]);
        p.monthlyNth = byNth.isSelected();
        p.until = untilRadio.isSelected() ? untilDate.getDate() : null;
        for (int i = 0; i < datesModel.size(); i++) p.extraDates.add(datesModel.get(i));
        repeatSummary.setText(p.repeat == Schedule.HAZRepeat.NONE ? " " : "↻ " + p.repeatText());
    }

    private void reloadTimeline() {
        LocalDate d = startDate.getDate();
        List<Schedule> others = new ArrayList<>();
        if (d != null) {
            for (Schedule o : HAZscheduleManager.getByDate(d, true)) {
                if (o.id.equals(work.id) || (original != null && o.id.equals(original.id) && mode == Mode.EDIT_ALL)) continue;
                if (mode == Mode.EDIT_ONE && original != null && o.id.equals(original.id) && d.equals(occ)) continue;
                others.add(o);
            }
        }
        timeline.setBusy(others, d == null ? LocalDate.now() : d, bufferBox.getSelectedIndex() * 10, HAZscheduleManager);
        timeTyped();
    }

    // ================================================================== 저장 / 삭제

    private void save() {
        String title = titleField.getText().trim();
        if (title.isEmpty()) { warn("일정 제목을 적어 주세요."); titleField.requestFocusInWindow(); return; }
        for (char c : title.toCharArray()) {
            if (ScheduleManager.HAZ_FORBIDDEN_CHARS.indexOf(c) >= 0 || Character.isISOControl(c)) {
                warn("제목에 쓸 수 없는 글자 ' " + c + " ' 가 들어 있어요.\n쓸 수 없는 글자:  \\  /  :  *  ?  \"  <  >  |");
                titleField.requestFocusInWindow();
                return;
            }
        }
        if (title.length() > 60) { warn("제목은 60자까지 쓸 수 있어요."); return; }
        if (categoryBox.getSelectedItem() == null) { warn("카테고리를 먼저 만들어 주세요."); return; }
        LocalDate d0 = startDate.getDate();
        if (d0 == null) { warn("날짜를 알아볼 수 없어요. 예) 2026-10-06"); return; }
        boolean multi = multiCheck.isSelected();
        LocalDate d1 = multi ? endDate.getDate() : null;
        if (multi && (d1 == null || !d1.isAfter(d0))) { warn("끝 날짜는 시작 날짜보다 뒤여야 해요."); return; }

        boolean allDay = allDayCheck.isSelected();
        int st = -1, en = -1;
        String a = editor(startBox).getText().trim(), b = editor(endBox).getText().trim();
        if (!allDay && a.isEmpty() && b.isEmpty()) allDay = true;     // 시간을 비워 두면 하루 종일
        if (!allDay) {
            st = TimeText.parse(a);
            if (st < 0 || st >= 1440) { warn("시작 시간을 알아볼 수 없어요.\n예) 오후 6:00 · 18:00 · 6시 30분"); return; }
            en = b.isEmpty() ? Math.min(st + 60, 1440) : TimeText.parse(b);
            if (en < 0) { warn("끝 시간을 알아볼 수 없어요.\n예) 오후 7:30 · 19:30"); return; }
            if (!multi && en <= st) { warn("끝 시간은 시작 시간보다 늦어야 해요."); return; }
            if (!multi) {
                List<Schedule> ov = new ArrayList<>();
                for (Schedule o : HAZscheduleManager.findOverlaps(d0, st, en, work.id)) {
                    if (original != null && o.id.equals(original.id) && (mode == Mode.EDIT_ALL || d0.equals(occ))) continue;
                    ov.add(o);
                }
                if (!ov.isEmpty() && JOptionPane.showConfirmDialog(frame,
                        "같은 시간에 「" + ov.get(0).title + "」 일정이 있어요. (" + ov.get(0).timeText() + ")\n그래도 저장할까요?",
                        "시간이 겹쳐요", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.OK_OPTION) return;
            }
        }
        Schedule.HAZRepeat rep = repeat();
        boolean periodic = rep != Schedule.HAZRepeat.NONE && rep != Schedule.HAZRepeat.DATES;
        LocalDate until = periodic && untilRadio.isSelected() ? untilDate.getDate() : null;
        if (periodic && untilRadio.isSelected() && (until == null || until.isBefore(d0))) { warn("반복 종료일은 시작 날짜보다 뒤여야 해요."); return; }
        if (rep == Schedule.HAZRepeat.DATES && datesModel.isEmpty()) { warn("'날짜 지정' 반복은 + 날짜로 날짜를 하나 이상 넣어 주세요."); return; }
        String link = Ui.normalizeLink(linkField.getText());
        if (!link.isEmpty()) {
            try { new java.net.URI(link).toURL(); }
            catch (Exception ex) { warn("링크 주소가 올바르지 않아요.\n예) https://ticket.example.com"); return; }
        }

        work.title = title;
        work.date = d0;
        work.endDate = d1;
        work.start = st;
        work.end = en;
        work.category = (String) categoryBox.getSelectedItem();
        work.color = chosenColor;
        work.repeat = rep;
        work.interval = periodic ? (Integer) intervalSpinner.getValue() : 1;
        work.weekdays = EnumSet.noneOf(DayOfWeek.class);
        if (rep == Schedule.HAZRepeat.WEEKLY) {
            for (int i = 0; i < 7; i++) if (dayButtons[i].isSelected()) work.weekdays.add(dayOrder[i]);
            if (work.weekdays.isEmpty()) work.weekdays.add(d0.getDayOfWeek());
        }
        work.monthlyNth = rep == Schedule.HAZRepeat.MONTHLY && byNth.isSelected();
        work.until = until;
        TreeSet<LocalDate> dates = new TreeSet<>();
        if (rep == Schedule.HAZRepeat.DATES) for (int i = 0; i < datesModel.size(); i++) dates.add(datesModel.get(i));
        dates.remove(d0);
        work.extraDates = dates;
        if (rep == Schedule.HAZRepeat.NONE) work.skip.clear();
        List<Integer> al = new ArrayList<>();
        if (!allDay) {
            for (JComboBox<String> box : alarmBoxes) {
                @SuppressWarnings("unchecked") List<Integer> mins = (List<Integer>) box.getClientProperty("mins");
                al.add(mins.get(Math.max(0, box.getSelectedIndex())));
            }
        }
        work.setAlarms(al);
        work.link = link;
        work.memo = memoArea.getText();

        close();
        switch (mode) {
            case NEW: HAZscheduleManager.add(work); break;
            case EDIT_ALL: HAZscheduleManager.replace(original, work); break;
            case EDIT_ONE: HAZscheduleManager.detach(original, occ, work); break;
        }
        frame.selectDate(d0);
    }

    private void delete() {
        if (mode == Mode.EDIT_ONE) {
            close();
            HAZscheduleManager.skipOccurrence(original, occ);
            return;
        }
        if (frame.confirmDelete(original, null)) close();
    }

    private void warn(String msg) { Ui.warn(frame, msg); }

    private void close() {
        setVisible(false);
        frame.editorClosed();
    }

    // 뒤 화면을 어둡게
    @Override protected void paintComponent(Graphics g) {
        g.setColor(new Color(0, 0, 0, Theme.isNight() ? 150 : 110));
        g.fillRect(0, 0, getWidth(), getHeight());
    }

    private static DocumentListener doc(Runnable r) {
        return new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { r.run(); }
            public void removeUpdate(DocumentEvent e) { r.run(); }
            public void changedUpdate(DocumentEvent e) { r.run(); }
        };
    }

    // 위아래로 쌓는 폼: 작은 회색 제목 → 입력칸
    private static class Form extends JPanel {
        Form() {
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setOpaque(false);
            setBorder(Ui.pad(4, 18, 12, 18));
        }

        void label(String text) {
            Ui.Label l = Ui.label(text, 12, true, Ui.SUB);
            l.setAlignmentX(0f);
            l.setBorder(Ui.pad(12, 2, 6, 0));
            add(l);
        }

        void field(JComponent c) {
            c.setAlignmentX(0f);
            if (c instanceof JTextField || c instanceof JComboBox)
                c.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height));
            add(c);
        }
    }

    // 반복 일정의 n번째 회차를 따로 떼어낸 복사본 (이 날만 수정할 때)
    public static Schedule detachedCopy(Schedule s, LocalDate occ) {
        Schedule c = s.copy();
        c.id = java.util.UUID.randomUUID().toString();
        c.uid = null;
        int span = s.span();
        c.date = occ;
        c.endDate = span > 0 ? occ.plusDays(span) : null;
        c.repeat = Schedule.HAZRepeat.NONE;
        c.interval = 1;
        c.weekdays = EnumSet.noneOf(DayOfWeek.class);
        c.monthlyNth = false;
        c.until = null;
        c.extraDates = new TreeSet<>();
        c.skip = new TreeSet<>();
        return c;
    }

    static long daysBetween(LocalDate a, LocalDate b) { return ChronoUnit.DAYS.between(a, b); }
}
