
import javax.swing.*;
import java.awt.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;

// 메인 화면: 상단 메뉴 / 달력 / 디데이 / 일정 목록 / 메모
public class MainFrame extends JFrame {
    private final ScheduleManager HAZscheduleManager;
    private final AlarmManager HAZalarmManager;
    private final ThemeManager HAZthemeManager;

    private YearMonth currentMonth = YearMonth.now();
    private LocalDate HAZselectedDate = LocalDate.now();
    private final JPanel HAZcalendarPanel = new JPanel(new GridLayout(0, 7));
    private final JLabel monthLabel = new JLabel("", SwingConstants.CENTER);
    private final DefaultListModel<Schedule> listModel = new DefaultListModel<>();

    public MainFrame(ScheduleManager sm, AlarmManager am, ThemeManager tm) {
        this.HAZscheduleManager = sm;
        this.HAZalarmManager = am;
        this.HAZthemeManager = tm;

        setTitle("캘린더 (미정)");
        setSize(1000, 650);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        getContentPane().setBackground(HAZthemeManager.getBackgroundColor());

        add(createMenuBar(), BorderLayout.NORTH);
        add(createCalendarArea(), BorderLayout.CENTER);
        add(createSidePanel(), BorderLayout.EAST);

        refreshCalendar();
    }

    private JPanel createMenuBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        bar.setBackground(new Color(207, 226, 243));
        String[] menus = {"일정", "알람", "위젯", "설정"};
        for (String name : menus) {
            JButton btn = new JButton(name);
            btn.addActionListener(e -> onMenu(name));
            bar.add(btn);
        }
        return bar;
    }

    private JPanel createCalendarArea() {
        JPanel area = new JPanel(new BorderLayout());
        area.setOpaque(false);
        JButton prev = new JButton("<");
        JButton next = new JButton(">");
        prev.addActionListener(e -> { currentMonth = currentMonth.minusMonths(1); refreshCalendar(); });
        next.addActionListener(e -> { currentMonth = currentMonth.plusMonths(1); refreshCalendar(); });

        JPanel header = new JPanel(new BorderLayout());
        header.add(prev, BorderLayout.WEST);
        header.add(monthLabel, BorderLayout.CENTER);
        header.add(next, BorderLayout.EAST);

        area.add(header, BorderLayout.NORTH);
        area.add(HAZcalendarPanel, BorderLayout.CENTER);
        area.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        return area;
    }

    private JPanel createSidePanel() {
        JPanel side = new JPanel();
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        side.setPreferredSize(new Dimension(260, 0));

        JLabel dday = new JLabel("디데이: 미설정");   // TODO: 디데이 설정
        dday.setBorder(BorderFactory.createTitledBorder("디데이"));

        JList<Schedule> list = new JList<>(listModel);
        JScrollPane listScroll = new JScrollPane(list);
        listScroll.setBorder(BorderFactory.createTitledBorder("일정 목록"));

        JButton addBtn = new JButton("일정 추가");
        addBtn.addActionListener(e -> openScheduleDialog());

        JTextArea memo = new JTextArea(5, 20);   // 메모지 창 (드롭다운은 TODO)
        JScrollPane memoScroll = new JScrollPane(memo);
        memoScroll.setBorder(BorderFactory.createTitledBorder("메모"));

        side.add(dday);
        side.add(listScroll);
        side.add(addBtn);
        side.add(memoScroll);
        return side;
    }

    private void refreshCalendar() {
        HAZcalendarPanel.removeAll();
        monthLabel.setText(currentMonth.getYear() + "년 " + currentMonth.getMonthValue() + "월");
        String[] days = {"일", "월", "화", "수", "목", "금", "토"};
        for (String d : days) HAZcalendarPanel.add(new JLabel(d, SwingConstants.CENTER));

        int offset = currentMonth.atDay(1).getDayOfWeek().getValue() % 7;
        for (int i = 0; i < offset; i++) HAZcalendarPanel.add(new JLabel(""));

        for (int day = 1; day <= currentMonth.lengthOfMonth(); day++) {
            LocalDate date = currentMonth.atDay(day);
            JButton cell = new JButton(String.valueOf(day));
            // TODO: 공휴일 표시
            cell.addActionListener(e -> { HAZselectedDate = date; refreshList(); });
            HAZcalendarPanel.add(cell);
        }
        HAZcalendarPanel.revalidate();
        HAZcalendarPanel.repaint();
        refreshList();
    }

    private void refreshList() {
        listModel.clear();
        for (Schedule s : HAZscheduleManager.getByDate(HAZselectedDate)) listModel.addElement(s);
    }

    // 일정 설정 탭 (아주 단순한 버전)
    private void openScheduleDialog() {
        JTextField titleField = new JTextField();
        JTextField startField = new JTextField("18:00");
        JTextField endField = new JTextField("19:00");
        JComboBox<String> typeBox = new JComboBox<>(HAZscheduleManager.getTypes().toArray(new String[0]));
        Object[] form = {"일정 이름", titleField, "시작 (HH:mm)", startField,
                            "종료 (HH:mm)", endField, "타입", typeBox};
        // TODO: 반복 설정, 테트리스(드래그) 모드

        int result = JOptionPane.showConfirmDialog(this, form, "일정 추가", JOptionPane.OK_CANCEL_OPTION);
        if (result != JOptionPane.OK_OPTION) return;

        String title = titleField.getText();
        if (!HAZscheduleManager.isValidTitle(title)) {
            JOptionPane.showMessageDialog(this, "사용할 수 없는 이름입니다. 이름을 변경해 주세요.");
            return;
        }
        try {
            LocalDateTime start = HAZselectedDate.atTime(java.time.LocalTime.parse(startField.getText()));
            LocalDateTime end = HAZselectedDate.atTime(java.time.LocalTime.parse(endField.getText()));
            Schedule newSchedule = new Schedule(title, start, end, (String) typeBox.getSelectedItem());
            HAZscheduleManager.add(newSchedule);
            HAZalarmManager.addAlarm(newSchedule, 10); // 기본 10분 전 알람 (임시)
            refreshList();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "시간 형식이 잘못되었습니다.");
        }
    }

    private void onMenu(String name) {
        switch (name) {
            case "위젯" -> new WidgetWindow(true).setVisible(true);
            default -> JOptionPane.showMessageDialog(this, name + " 화면 (준비 중)");
        }
    }
}
