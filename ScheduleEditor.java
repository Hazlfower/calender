import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

// 일정 추가/수정 화면: 메인 창 위를 어둡게 덮고, 왼쪽 = 일정 설정 / 오른쪽 = 시간 테트리스
public class ScheduleEditor extends JPanel {
    private final MainFrame frame;
    private final ScheduleManager HAZscheduleManager;
    private final Schedule target;

    private final JTextField titleField = new JTextField(16);
    private final JComboBox<String> categoryBox = new JComboBox<>();
    private final DatePicker startDate, endDate;
    private final JTextField startTime = new JTextField(5), endTime = new JTextField(5);
    private final JComboBox<Schedule.HAZRepeat> repeatBox = new JComboBox<>(Schedule.HAZRepeat.values());
    private final JSpinner intervalSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 99, 1));
    private final JLabel intervalUnit = new JLabel();
    private final JToggleButton[] dayButtons = new JToggleButton[7];   // 일~토
    private final JRadioButton byDay = new JRadioButton(), byWeekday = new JRadioButton();
    private final JCheckBox untilCheck = new JCheckBox("종료일");
    private final DatePicker untilDate;
    private final DefaultListModel<LocalDate> datesModel = new DefaultListModel<>();
    private final JCheckBox alarmCheck = new JCheckBox("알람");
    private final JSpinner alarmSpinner = new JSpinner(new SpinnerNumberModel(10, 0, 1440, 5));
    private final JTextArea memoArea = new JTextArea(2, 16);
    private final JTextField linkField = new JTextField(16);

    private JComponent intervalRow, weekRow, monthRow, datesRow, untilRow;
    private final TimelinePanel timeline;
    private boolean syncing = false;

    public ScheduleEditor(MainFrame frame, ScheduleManager sm, LocalDate date, Schedule target) {
        super(new GridBagLayout());
        this.frame = frame;
        this.HAZscheduleManager = sm;
        this.target = target;
        setOpaque(false);
        addMouseListener(new MouseAdapter() { });   // 뒤쪽 화면 클릭 막기
        addMouseMotionListener(new MouseAdapter() { });

        LocalDate base = target != null ? target.getDate() : date;
        startDate = new DatePicker(base);
        endDate = new DatePicker(target != null ? target.getEndDate() : base);
        untilDate = new DatePicker(target != null && target.getUntil() != null ? target.getUntil() : base.plusMonths(3));
        timeline = new TimelinePanel(sm, target, this::onTimelineSelect);

        fillValues(base);

        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.BOTH;
        c.insets = new Insets(30, 16, 30, 16);
        c.weighty = 1;
        c.weightx = 0;
        add(buildFormCard(), c);
        add(buildTetrisCard(), c);

        // ESC = 닫기
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "close");
        getActionMap().put("close", new AbstractAction() {
            public void actionPerformed(java.awt.event.ActionEvent e) { close(); }
        });

        updateRepeatRows();
        syncTimeline();
        SwingUtilities.invokeLater(titleField::requestFocusInWindow);
    }

    private void fillValues(LocalDate base) {
        for (String name : HAZscheduleManager.getCategories().keySet()) categoryBox.addItem(name);
        categoryBox.setRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean s, boolean f) {
                JLabel lb = (JLabel) super.getListCellRendererComponent(l, v, i, s, f);
                if (v != null) lb.setIcon(Ui.dot(HAZscheduleManager.colorOf((String) v), 10));
                return lb;
            }
        });
        String[] names = {"일", "월", "화", "수", "목", "금", "토"};
        for (int i = 0; i < 7; i++) {
            JToggleButton b = new JToggleButton(names[i]) {
                @Override protected void paintComponent(Graphics g) {
                    Ui.smooth(g);
                    Color fill = isSelected() ? Theme.highlight() : Theme.surface();
                    g.setColor(fill);
                    g.fillOval(1, 1, getWidth() - 3, getHeight() - 3);
                    g.setColor(isSelected() ? fill : Theme.border());
                    g.drawOval(1, 1, getWidth() - 3, getHeight() - 3);
                    g.setFont(getFont());
                    g.setColor(Theme.onColor(fill));
                    FontMetrics fm = g.getFontMetrics();
                    g.drawString(getText(), (getWidth() - fm.stringWidth(getText())) / 2, (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
                }
            };
            b.setFont(Ui.font(12, true));
            b.setPreferredSize(new Dimension(28, 28));
            b.setContentAreaFilled(false);
            b.setBorderPainted(false);
            b.setFocusPainted(false);
            b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            dayButtons[i] = b;
        }
        ButtonGroup g = new ButtonGroup();
        g.add(byDay);
        g.add(byWeekday);
        memoArea.setLineWrap(true);

        if (target != null) {
            titleField.setText(target.getTitle());
            categoryBox.setSelectedItem(target.getCategory());
            startTime.setText(target.getStart().toString());
            endTime.setText(target.getEnd().toString());
            repeatBox.setSelectedItem(target.getRepeat());
            intervalSpinner.setValue(target.getInterval());
            for (DayOfWeek d : target.getWeekdays()) dayButtons[d.getValue() % 7].setSelected(true);
            (target.isMonthlyByWeekday() ? byWeekday : byDay).setSelected(true);
            untilCheck.setSelected(target.getUntil() != null);
            for (LocalDate d : target.getExtraDates()) datesModel.addElement(d);
            alarmCheck.setSelected(target.hasAlarm());
            if (target.hasAlarm()) alarmSpinner.setValue(target.getAlarmMinutes());
            memoArea.setText(target.getMemo() == null ? "" : target.getMemo());
            linkField.setText(target.getLink() == null ? "" : target.getLink());
        } else {
            startTime.setText("18:00");
            endTime.setText("19:00");
            byDay.setSelected(true);
            alarmCheck.setSelected(true);
            dayButtons[base.getDayOfWeek().getValue() % 7].setSelected(true);
        }
        alarmSpinner.setEnabled(alarmCheck.isSelected());
        alarmCheck.addActionListener(e -> alarmSpinner.setEnabled(alarmCheck.isSelected()));
        untilDate.setEnabled(untilCheck.isSelected());
        untilCheck.addActionListener(e -> untilDate.setEnabled(untilCheck.isSelected()));
        repeatBox.addActionListener(e -> updateRepeatRows());

        startDate.setOnChange(() -> {
            LocalDate s = startDate.getDate();
            LocalDate e = endDate.getDate();
            if (s != null && (e == null || e.isBefore(s))) endDate.setDate(s);
            updateMonthLabels();
            syncTimeline();
        });
        Runnable sync = this::syncTimeline;
        startTime.getDocument().addDocumentListener(doc(sync));
        endTime.getDocument().addDocumentListener(doc(sync));
    }

    // ===== 왼쪽: 일정 설정 카드 =====
    private JComponent buildFormCard() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        form.setBorder(Ui.pad(14, 18, 10, 18));
        GridBagConstraints c = new GridBagConstraints();
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(4, 0, 4, 8);
        int[] y = {0};

        Ui.FlatButton addCategory = Ui.button("+");
        addCategory.setToolTipText("카테고리 추가");
        addCategory.addActionListener(e -> {
            String name = frame.askNewCategory();
            if (name != null) { categoryBox.addItem(name); categoryBox.setSelectedItem(name); }
        });

        row(form, c, y, "제목", titleField);
        row(form, c, y, "카테고리", flow(categoryBox, addCategory));
        row(form, c, y, "날짜", flow(startDate, new JLabel("~"), endDate));
        row(form, c, y, "시간", flow(startTime, new JLabel("~"), endTime, Ui.label("  오른쪽에서 드래그해도 돼요", 11, false, Theme.sub())));

        // 반복 영역 (배경 있는 박스)
        Ui.RoundPanel repeatBoxPanel = new Ui.RoundPanel(new GridBagLayout(), Ui.blend(Theme.secondary(), Theme.surface(), 0.3), null, Theme.radius());
        repeatBoxPanel.setBorder(Ui.pad(8, 10, 8, 10));
        GridBagConstraints rc = (GridBagConstraints) c.clone();
        int[] ry = {0};
        row(repeatBoxPanel, rc, ry, "반복", repeatBox);
        intervalRow = row(repeatBoxPanel, rc, ry, "간격", flow(intervalSpinner, intervalUnit, new JLabel("마다")));
        JPanel days = flow();
        for (JToggleButton b : dayButtons) days.add(b);
        weekRow = row(repeatBoxPanel, rc, ry, "요일", days);
        monthRow = row(repeatBoxPanel, rc, ry, "방식", flow(byDay, byWeekday));
        datesRow = row(repeatBoxPanel, rc, ry, "날짜들", buildDatesEditor());
        untilRow = row(repeatBoxPanel, rc, ry, "", flow(untilCheck, untilDate));

        c.gridy = y[0]++; c.gridx = 0; c.gridwidth = 2;
        c.insets = new Insets(8, 0, 8, 0);
        form.add(repeatBoxPanel, c);
        c.gridwidth = 1;
        c.insets = new Insets(4, 0, 4, 8);

        row(form, c, y, "알람", flow(alarmCheck, alarmSpinner, new JLabel("분 전")));
        JScrollPane memoScroll = new JScrollPane(memoArea);
        row(form, c, y, "메모", memoScroll);
        row(form, c, y, "링크", linkField);

        c.gridy = y[0]; c.gridx = 0; c.gridwidth = 2; c.weighty = 1;
        form.add(Box.createVerticalGlue(), c);

        Ui.FlatButton save = Ui.primaryButton("완료");
        Ui.FlatButton cancel = Ui.button("취소");
        save.addActionListener(e -> save());
        cancel.addActionListener(e -> close());
        JPanel buttons = Ui.clear(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttons.setBorder(Ui.pad(6, 0, 12, 14));
        if (target != null) {
            Ui.FlatButton delete = Ui.dangerButton("삭제");
            delete.addActionListener(e -> {
                if (frame.confirmDelete(target)) close();
            });
            buttons.add(delete);
        }
        buttons.add(cancel);
        buttons.add(save);

        JPanel body = Ui.clear(new BorderLayout());
        JScrollPane sp = Ui.scroll(form);
        body.add(sp, BorderLayout.CENTER);
        body.add(buttons, BorderLayout.SOUTH);
        Ui.RoundPanel card = Ui.headerCard(target == null ? "일정 추가" : "일정 수정", null, body);
        card.setPreferredSize(new Dimension(470, 560));
        return card;
    }

    private JComponent buildDatesEditor() {
        JList<LocalDate> list = new JList<>(datesModel);
        list.setVisibleRowCount(3);
        Ui.FlatButton add = Ui.button("+ 날짜");
        Ui.FlatButton remove = Ui.button("빼기");
        add.addActionListener(e -> DatePicker.popup(add, startDate.getDate() == null ? LocalDate.now() : startDate.getDate(), d -> {
            if (!datesModel.contains(d)) datesModel.addElement(d);
        }));
        remove.addActionListener(e -> {
            LocalDate d = list.getSelectedValue();
            if (d != null) datesModel.removeElement(d);
        });
        JPanel p = Ui.clear(new BorderLayout(6, 0));
        JScrollPane sp = new JScrollPane(list);
        sp.setPreferredSize(new Dimension(130, 62));
        p.add(sp, BorderLayout.CENTER);
        JPanel b = Ui.clear(new GridLayout(2, 1, 0, 4));
        b.add(add);
        b.add(remove);
        p.add(b, BorderLayout.EAST);
        return p;
    }

    // ===== 오른쪽: 시간 테트리스 카드 =====
    private JComponent buildTetrisCard() {
        JScrollPane sp = Ui.scroll(timeline);
        sp.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        sp.setBorder(Ui.pad(10, 10, 10, 6));
        SwingUtilities.invokeLater(() -> sp.getViewport().setViewPosition(new Point(0, Math.max(0, timeline.selectionY() - 80))));
        Ui.RoundPanel card = Ui.headerCard("시간 테트리스", Ui.label("색칠된 칸 = 이미 있는 일정  ", 11, false, Theme.onPrimary()), sp);
        card.setPreferredSize(new Dimension(timeline.getPreferredSize().width + 40, 560));
        return card;
    }

    private static JComponent row(JPanel p, GridBagConstraints c, int[] y, String label, JComponent comp) {
        JLabel l = Ui.label(label, 13, true, Theme.text());
        c.gridy = y[0]++;
        c.gridx = 0; c.weightx = 0;
        p.add(l, c);
        c.gridx = 1; c.weightx = 1;
        p.add(comp, c);
        comp.putClientProperty("rowLabel", l);
        return comp;
    }

    private static JPanel flow(Component... items) {
        JPanel p = Ui.clear(new FlowLayout(FlowLayout.LEFT, 4, 0));
        for (Component i : items) {
            if (i instanceof AbstractButton && !(i instanceof Ui.FlatButton)) ((AbstractButton) i).setOpaque(false);
            p.add(i);
        }
        return p;
    }

    private static void showRow(JComponent comp, boolean visible) {
        comp.setVisible(visible);
        ((JComponent) comp.getClientProperty("rowLabel")).setVisible(visible);
    }

    private void updateRepeatRows() {
        Schedule.HAZRepeat r = (Schedule.HAZRepeat) repeatBox.getSelectedItem();
        boolean periodic = r != Schedule.HAZRepeat.NONE && r != Schedule.HAZRepeat.DATES;
        showRow(intervalRow, periodic);
        showRow(weekRow, r == Schedule.HAZRepeat.WEEKLY);
        showRow(monthRow, r == Schedule.HAZRepeat.MONTHLY);
        showRow(datesRow, r == Schedule.HAZRepeat.DATES);
        showRow(untilRow, periodic);
        intervalUnit.setText(r.unit);
        updateMonthLabels();
        revalidate();
        repaint();
    }

    private void updateMonthLabels() {
        LocalDate d = startDate.getDate() == null ? LocalDate.now() : startDate.getDate();
        byDay.setText("매월 " + d.getDayOfMonth() + "일");
        byWeekday.setText("매월 " + Schedule.nthWeek(d) + "번째 " + Schedule.dowName(d.getDayOfWeek()) + "요일");
    }

    private void onTimelineSelect(LocalTime s, LocalTime e) {
        syncing = true;
        startTime.setText(s.toString());
        endTime.setText(e.toString());
        syncing = false;
    }

    private void syncTimeline() {
        if (syncing) return;
        timeline.setDate(startDate.getDate());
        timeline.setSelection(parseTime(startTime), parseTime(endTime));
    }

    private static LocalTime parseTime(JTextField f) {
        String t = f.getText().trim();
        if (t.matches("\\d:\\d\\d")) t = "0" + t;
        try { return LocalTime.parse(t); } catch (Exception e) { return null; }
    }

    private void save() {
        String title = titleField.getText().trim();
        LocalDate from = startDate.getDate(), to = endDate.getDate();
        LocalTime s = parseTime(startTime), e = parseTime(endTime);
        Schedule.HAZRepeat repeat = (Schedule.HAZRepeat) repeatBox.getSelectedItem();

        if (!HAZscheduleManager.isValidTitle(title)) { warn("제목에 쓸 수 없는 글자가 있어요. (\\ / : * ? \" < > |)"); return; }
        if (from == null || to == null) { warn("날짜 형식이 잘못되었어요. 예) 2026-10-06"); return; }
        if (to.isBefore(from)) { warn("끝 날짜가 시작 날짜보다 빨라요."); return; }
        if (s == null || e == null) { warn("시간 형식이 잘못되었어요. 예) 18:00"); return; }
        if (from.equals(to) && !e.isAfter(s)) { warn("끝 시간은 시작 시간보다 늦어야 해요."); return; }
        if (untilCheck.isSelected() && untilDate.getDate() == null) { warn("반복 종료일 형식이 잘못되었어요."); return; }

        if (from.equals(to)) {
            List<Schedule> overlaps = HAZscheduleManager.findOverlaps(from, s, e, target);
            if (!overlaps.isEmpty() && JOptionPane.showConfirmDialog(frame,
                    "'" + overlaps.get(0).getTitle() + "' 일정과 시간이 겹쳐요. 그래도 저장할까요?",
                    "시간 겹침", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        }

        Schedule sc = target != null ? target : new Schedule(title, from, s, e, "");
        sc.setTitle(title);
        sc.setDate(from);
        sc.setEndDate(to);
        sc.setStart(s);
        sc.setEnd(e);
        sc.setCategory((String) categoryBox.getSelectedItem());
        sc.setRepeat(repeat);
        sc.setInterval((Integer) intervalSpinner.getValue());
        Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        for (int i = 0; i < 7; i++) if (dayButtons[i].isSelected()) days.add(i == 0 ? DayOfWeek.SUNDAY : DayOfWeek.of(i));
        sc.setWeekdays(days);
        sc.setMonthlyByWeekday(byWeekday.isSelected());
        sc.setUntil(untilCheck.isSelected() ? untilDate.getDate() : null);
        Set<LocalDate> dates = new TreeSet<>();
        for (int i = 0; i < datesModel.size(); i++) dates.add(datesModel.get(i));
        sc.setExtraDates(dates);
        sc.setAlarmMinutes(alarmCheck.isSelected() ? (Integer) alarmSpinner.getValue() : -1);
        sc.setMemo(memoArea.getText().isBlank() ? null : memoArea.getText());
        sc.setLink(linkField.getText().isBlank() ? null : linkField.getText().trim());

        close();
        if (target == null) HAZscheduleManager.add(sc);
        else HAZscheduleManager.update();
    }

    private void warn(String msg) {
        JOptionPane.showMessageDialog(frame, msg, "확인해 주세요", JOptionPane.WARNING_MESSAGE);
    }

    private void close() {
        setVisible(false);
        frame.editorClosed();
    }

    // 뒤 화면을 어둡게
    @Override protected void paintComponent(Graphics g) {
        g.setColor(new Color(0, 0, 0, 120));
        g.fillRect(0, 0, getWidth(), getHeight());
        super.paintComponent(g);
    }

    private static javax.swing.event.DocumentListener doc(Runnable r) {
        return new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { r.run(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { r.run(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { r.run(); }
        };
    }
}
