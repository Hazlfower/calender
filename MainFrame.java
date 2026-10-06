import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;

// 메인 화면: [왼쪽] 프로필+설정 탭 / [가운데] 캘린더 / [오른쪽] 인덱스 월 선택
public class MainFrame extends JFrame {
    private final ScheduleManager HAZscheduleManager;
    private final AlarmManager HAZalarmManager;
    private final ThemeManager HAZthemeManager;

    private YearMonth currentMonth = YearMonth.now();
    private LocalDate HAZselectedDate = LocalDate.now();
    private final JPanel HAZcalendarPanel = new JPanel(new GridLayout(0, 7, 4, 4));
    private final JLabel monthLabel = new JLabel("", SwingConstants.CENTER);
    private final JLabel dateLabel = new JLabel();
    private final DefaultListModel<Schedule> listModel = new DefaultListModel<>();
    private final JList<Schedule> scheduleJList = new JList<>(listModel);
    private final JButton[] monthTabs = new JButton[12];

    private final Color paper = new Color(250, 245, 235);
    private final Color line = new Color(120, 100, 80);

    public MainFrame(ScheduleManager sm, AlarmManager am, ThemeManager tm) {
        this.HAZscheduleManager = sm;
        this.HAZalarmManager = am;
        this.HAZthemeManager = tm;

        setTitle("캘린더 (미정)");
        setSize(1100, 650);
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        JPanel root = new JPanel(new BorderLayout(0, 0));
        root.setBackground(new Color(225, 215, 200));
        root.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 10));
        root.add(createLeftPanel(), BorderLayout.WEST);
        root.add(createCenterPanel(), BorderLayout.CENTER);
        root.add(createIndexPanel(), BorderLayout.EAST);
        setContentPane(root);

        refreshCalendar();
    }

    // ===== 왼쪽: 프로필 사진 + 설정 탭 =====
    private JPanel createLeftPanel() {
        JPanel left = new JPanel();
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        left.setPreferredSize(new Dimension(220, 0));
        left.setBackground(paper);
        left.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(2, 2, 2, 2, line),
                BorderFactory.createEmptyBorder(20, 15, 20, 15)));

        // 프로필 사진 자리 (TODO: 이미지 선택)
        JLabel profile = new JLabel("프로필 사진", SwingConstants.CENTER) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(230, 220, 205));
                g2.fillOval(0, 0, getWidth() - 1, getHeight() - 1);
                g2.setColor(line);
                g2.drawOval(0, 0, getWidth() - 1, getHeight() - 1);
                super.paintComponent(g);
            }
        };
        Dimension size = new Dimension(120, 120);
        profile.setPreferredSize(size);
        profile.setMaximumSize(size);
        profile.setAlignmentX(Component.CENTER_ALIGNMENT);
        left.add(profile);
        left.add(Box.createVerticalStrut(30));

        String[] menus = {"달력", "위젯", "알람", "설정"};
        for (String name : menus) {
            JButton btn = new JButton(name);
            btn.setAlignmentX(Component.CENTER_ALIGNMENT);
            btn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
            btn.setFocusPainted(false);
            btn.setBackground(Color.WHITE);
            btn.addActionListener(e -> onMenu(name));
            left.add(btn);
            left.add(Box.createVerticalStrut(8));
        }
        left.add(Box.createVerticalGlue());
        return left;
    }

    // ===== 가운데: 캘린더 + 선택한 날짜의 일정 =====
    private JPanel createCenterPanel() {
        JPanel center = new JPanel(new BorderLayout(0, 10));
        center.setBackground(paper);
        center.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(2, 0, 2, 2, line),
                BorderFactory.createEmptyBorder(15, 20, 15, 20)));

        monthLabel.setFont(monthLabel.getFont().deriveFont(Font.BOLD, 22f));
        center.add(monthLabel, BorderLayout.NORTH);

        HAZcalendarPanel.setOpaque(false);
        center.add(HAZcalendarPanel, BorderLayout.CENTER);

        // 일정 목록 + 추가/수정/삭제 버튼
        JPanel bottom = new JPanel(new BorderLayout(0, 5));
        bottom.setOpaque(false);
        bottom.setPreferredSize(new Dimension(0, 160));
        bottom.add(dateLabel, BorderLayout.NORTH);

        scheduleJList.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) editSelected(); // 더블클릭 = 수정
            }
        });
        bottom.add(new JScrollPane(scheduleJList), BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.setOpaque(false);
        JButton addBtn = new JButton("일정 추가");
        JButton editBtn = new JButton("수정");
        JButton deleteBtn = new JButton("삭제");
        addBtn.addActionListener(e -> openScheduleDialog(null));
        editBtn.addActionListener(e -> editSelected());
        deleteBtn.addActionListener(e -> deleteSelected());
        buttons.add(addBtn);
        buttons.add(editBtn);
        buttons.add(deleteBtn);
        bottom.add(buttons, BorderLayout.SOUTH);

        center.add(bottom, BorderLayout.SOUTH);
        return center;
    }

    // ===== 오른쪽: 다이어리 인덱스 같은 월 선택 탭 =====
    private JPanel createIndexPanel() {
        JPanel index = new JPanel(new GridLayout(12, 1, 0, 3));
        index.setOpaque(false);
        index.setPreferredSize(new Dimension(60, 0));
        index.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0));

        Color[] tabColors = {
            new Color(244, 204, 204), new Color(252, 229, 205), new Color(255, 242, 204),
            new Color(217, 234, 211), new Color(208, 224, 227), new Color(207, 226, 243)
        };
        for (int i = 0; i < 12; i++) {
            int month = i + 1;
            Color tabColor = tabColors[i % tabColors.length];
            JButton tab = new JButton(month + "월") {
                @Override protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g;
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    boolean selected = currentMonth.getMonthValue() == month;
                    int w = selected ? getWidth() : getWidth() - 10; // 선택된 탭은 튀어나옴
                    g2.setColor(tabColor);
                    g2.fillRoundRect(-12, 0, w + 12, getHeight(), 14, 14);
                    g2.setColor(line);
                    g2.drawRoundRect(-12, 0, w + 11, getHeight() - 1, 14, 14);
                    g2.setColor(Color.DARK_GRAY);
                    FontMetrics fm = g2.getFontMetrics();
                    String text = getText();
                    g2.drawString(text, (w - fm.stringWidth(text)) / 2,
                            (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
                }
            };
            tab.setContentAreaFilled(false);
            tab.setBorderPainted(false);
            tab.setFocusPainted(false);
            tab.addActionListener(e -> {
                currentMonth = YearMonth.of(currentMonth.getYear(), month);
                refreshCalendar();
            });
            monthTabs[i] = tab;
            index.add(tab);
        }
        return index;
    }

    private void refreshCalendar() {
        HAZcalendarPanel.removeAll();
        monthLabel.setText(currentMonth.getYear() + "년 " + currentMonth.getMonthValue() + "월");

        String[] days = {"일", "월", "화", "수", "목", "금", "토"};
        for (int i = 0; i < 7; i++) {
            JLabel d = new JLabel(days[i], SwingConstants.CENTER);
            if (i == 0) d.setForeground(new Color(200, 60, 60));
            if (i == 6) d.setForeground(new Color(60, 90, 200));
            HAZcalendarPanel.add(d);
        }

        int offset = currentMonth.atDay(1).getDayOfWeek().getValue() % 7;
        for (int i = 0; i < offset; i++) HAZcalendarPanel.add(new JLabel(""));

        for (int day = 1; day <= currentMonth.lengthOfMonth(); day++) {
            LocalDate date = currentMonth.atDay(day);
            int count = HAZscheduleManager.getByDate(date).size();
            JButton cell = new JButton(count > 0 ? "<html><center>" + day + "<br>●" + count + "</center></html>" : String.valueOf(day));
            cell.setFocusPainted(false);
            cell.setBackground(date.equals(HAZselectedDate) ? new Color(255, 235, 180) : Color.WHITE);
            // TODO: 공휴일 표시
            cell.addActionListener(e -> { HAZselectedDate = date; refreshCalendar(); });
            HAZcalendarPanel.add(cell);
        }
        HAZcalendarPanel.revalidate();
        HAZcalendarPanel.repaint();
        for (JButton tab : monthTabs) if (tab != null) tab.repaint();
        refreshList();
    }

    private void refreshList() {
        dateLabel.setText(HAZselectedDate + " 일정");
        listModel.clear();
        for (Schedule s : HAZscheduleManager.getByDate(HAZselectedDate)) listModel.addElement(s);
    }

    private void editSelected() {
        Schedule selected = scheduleJList.getSelectedValue();
        if (selected == null) {
            JOptionPane.showMessageDialog(this, "수정할 일정을 선택하세요.");
            return;
        }
        openScheduleDialog(selected);
    }

    private void deleteSelected() {
        Schedule selected = scheduleJList.getSelectedValue();
        if (selected == null) {
            JOptionPane.showMessageDialog(this, "삭제할 일정을 선택하세요.");
            return;
        }
        int answer = JOptionPane.showConfirmDialog(this,
                "'" + selected.getTitle() + "' 일정을 삭제할까요?", "삭제", JOptionPane.YES_NO_OPTION);
        if (answer == JOptionPane.YES_OPTION) {
            HAZscheduleManager.remove(selected);
            refreshCalendar();
        }
    }

    // 일정 추가/수정 창 (target이 null이면 추가, 아니면 수정)
    private void openScheduleDialog(Schedule target) {
        boolean isEdit = target != null;
        JTextField titleField = new JTextField(isEdit ? target.getTitle() : "");
        JTextField startField = new JTextField(isEdit ? target.getStart().toLocalTime().toString() : "18:00");
        JTextField endField = new JTextField(isEdit ? target.getEnd().toLocalTime().toString() : "19:00");
        JComboBox<String> typeBox = new JComboBox<>(HAZscheduleManager.getTypes().toArray(new String[0]));
        if (isEdit) typeBox.setSelectedItem(target.getType());
        JTextField memoField = new JTextField(isEdit && target.getMemo() != null ? target.getMemo() : "");
        JTextField linkField = new JTextField(isEdit && target.getLink() != null ? target.getLink() : "");

        Object[] form = {"일정 이름", titleField, "시작 (HH:mm)", startField, "종료 (HH:mm)", endField,
                         "타입", typeBox, "메모", memoField, "링크", linkField};
        // TODO: 반복 설정, 테트리스(드래그) 모드

        int result = JOptionPane.showConfirmDialog(this, form, isEdit ? "일정 수정" : "일정 추가",
                JOptionPane.OK_CANCEL_OPTION);
        if (result != JOptionPane.OK_OPTION) return;

        String title = titleField.getText();
        if (!HAZscheduleManager.isValidTitle(title)) {
            JOptionPane.showMessageDialog(this, "사용할 수 없는 이름입니다. 이름을 변경해 주세요.");
            return;
        }
        try {
            LocalDate date = isEdit ? target.getStart().toLocalDate() : HAZselectedDate;
            LocalDateTime start = date.atTime(LocalTime.parse(startField.getText().trim()));
            LocalDateTime end = date.atTime(LocalTime.parse(endField.getText().trim()));
            if (!end.isAfter(start)) {
                JOptionPane.showMessageDialog(this, "종료 시간은 시작 시간보다 늦어야 합니다.");
                return;
            }
            String type = (String) typeBox.getSelectedItem();
            String memo = memoField.getText().isBlank() ? null : memoField.getText();
            String link = linkField.getText().isBlank() ? null : linkField.getText();

            if (isEdit) {
                target.setTitle(title);
                target.setStart(start);
                target.setEnd(end);
                target.setType(type);
                target.setMemo(memo);
                target.setLink(link);
                HAZscheduleManager.update();
            } else {
                Schedule newSchedule = new Schedule(title, start, end, type);
                newSchedule.setMemo(memo);
                newSchedule.setLink(link);
                HAZscheduleManager.add(newSchedule);
                HAZalarmManager.addAlarm(newSchedule, 10); // 기본 10분 전 알람 (임시)
            }
            refreshCalendar();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "시간 형식이 잘못되었습니다. (예: 18:00)");
        }
    }

    private void onMenu(String name) {
        switch (name) {
            case "달력" -> { currentMonth = YearMonth.now(); HAZselectedDate = LocalDate.now(); refreshCalendar(); }
            case "위젯" -> new WidgetWindow(true).setVisible(true);
            default -> JOptionPane.showMessageDialog(this, name + " 화면 (준비 중)");
        }
    }
}
