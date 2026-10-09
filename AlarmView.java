import javax.swing.*;
import java.awt.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

// "알람" 탭: 일정마다 알람을 여러 개 달고(정각, N분 전, 하루 전 ...) 링크를 붙임
public class AlarmView extends JPanel {
    private static final int[] CHOICES = {0, 1, 5, 10, 15, 30, 60, 120, 180, 1440};

    private final ScheduleManager HAZscheduleManager;
    private final AlarmManager HAZalarmManager;
    private final DefaultListModel<Schedule> model = new DefaultListModel<>();
    private final JList<Schedule> list = new JList<>(model);
    private final JCheckBox onlyAlarms = new JCheckBox("알람 있는 일정만", true);
    private final JCheckBox onlyFuture = new JCheckBox("지난 일정 숨기기", true);
    private final JPanel detail = Ui.clear(null);

    public AlarmView(ScheduleManager sm, AlarmManager am) {
        super(new BorderLayout());
        this.HAZscheduleManager = sm;
        this.HAZalarmManager = am;
        setOpaque(false);

        list.setOpaque(false);
        list.setCellRenderer((l, s, i, sel, f) -> {
            JPanel p = Ui.clear(new BorderLayout(10, 0));
            p.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.border()), Ui.pad(8, 14, 8, 14)));
            if (sel) {
                p.setOpaque(true);
                p.setBackground(Ui.blend(Theme.highlight(), Theme.surface(), Theme.isNight() ? 0.3 : 0.14));
            }
            Ui.Label t = Ui.label(s.title, 14, true, s.hasAlarm() ? Ui.TEXT : Ui.SUB);
            t.setIcon(Ui.dot(sm.colorOf(s), 10));
            t.setIconTextGap(8);
            String when = (s.repeat == Schedule.HAZRepeat.NONE ? TimeText.ymd(s.date) : s.repeatText())
                    + (s.allDay() ? " · 하루 종일" : " · " + TimeText.fmt(s.start));
            Ui.Label w = Ui.label(when, 12, false, Ui.SUB);
            w.setBorder(Ui.pad(0, 18, 0, 0));
            Ui.Label a = Ui.label(s.allDay() ? "시간 없음" : s.hasAlarm() ? s.alarmText() : "꺼짐", 12.5f, true,
                    s.hasAlarm() ? Ui.HIGHLIGHT : Ui.SUB);
            if (s.hasAlarm()) {
                a.setIcon(new Icons(Icons.Kind.BELL, 14, Theme.highlight()));
                a.setIconTextGap(5);
            }
            JPanel left = Ui.clear(new GridLayout(2, 1));
            left.add(t);
            left.add(w);
            p.add(left, BorderLayout.CENTER);
            p.add(a, BorderLayout.EAST);
            return p;
        });
        list.addListSelectionListener(e -> { if (!e.getValueIsAdjusting()) showDetail(); });
        onlyAlarms.addActionListener(e -> refresh());
        onlyFuture.addActionListener(e -> refresh());

        Ui.FlatButton test = Ui.headerButton("미리보기");
        test.setIcon(new Icons(Icons.Kind.BELL, 13));
        test.addActionListener(e -> HAZalarmManager.showTest());
        JPanel filters = Ui.clear(new FlowLayout(FlowLayout.LEFT, 10, 6));
        filters.add(onlyAlarms);
        filters.add(onlyFuture);

        JPanel left = Ui.clear(new BorderLayout());
        left.add(filters, BorderLayout.NORTH);
        left.add(Ui.scroll(list), BorderLayout.CENTER);

        detail.setLayout(new BoxLayout(detail, BoxLayout.Y_AXIS));
        detail.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, Theme.border()), Ui.pad(14, 16, 14, 16)));
        detail.setPreferredSize(new Dimension(290, 0));

        JPanel body = Ui.clear(new BorderLayout());
        body.add(left, BorderLayout.CENTER);
        body.add(detail, BorderLayout.EAST);
        add(Ui.headerCard("알람", test, body), BorderLayout.CENTER);
        refresh();
    }

    private void showDetail() {
        detail.removeAll();
        Schedule s = list.getSelectedValue();
        if (s == null) {
            Ui.Label hint = Ui.label("<html>왼쪽에서 일정을 고르면<br>알람을 여러 개 달 수 있어요.<br><br>"
                    + "알람은 게임 중에도 키보드를 뺏지 않고<br>화면 오른쪽 아래에 떠요.</html>", 12.5f, false, Ui.SUB);
            hint.setAlignmentX(0f);
            detail.add(hint);
            detail.revalidate();
            detail.repaint();
            return;
        }
        Ui.Label title = Ui.label(s.title, 16, true, Ui.TEXT);
        title.setAlignmentX(0f);
        detail.add(title);
        Ui.Label when = Ui.label(s.allDay() ? "하루 종일 일정" : TimeText.fmt(s.start) + " 시작", 12.5f, false, Ui.SUB);
        when.setAlignmentX(0f);
        detail.add(when);
        detail.add(Box.createVerticalStrut(14));

        if (s.allDay()) {
            Ui.Label no = Ui.label("<html>시간이 정해진 일정만 알람을 쓸 수 있어요.<br>일정을 수정해서 시간을 넣어 주세요.</html>", 12.5f, false, Ui.SUB);
            no.setAlignmentX(0f);
            detail.add(no);
        } else {
            for (int a : new ArrayList<>(s.alarms)) {
                JButton x = Ui.iconButton(Icons.Kind.CLOSE, "이 알람 빼기", false, () -> {
                    List<Integer> l = new ArrayList<>(s.alarms);
                    l.remove(Integer.valueOf(a));
                    s.setAlarms(l);
                    HAZscheduleManager.update();
                });
                x.setPreferredSize(new Dimension(28, 28));
                Ui.Label lab = Ui.label("시작 " + TimeText.before(a), 13.5f, true, Ui.TEXT);
                lab.setIcon(new Icons(Icons.Kind.BELL, 14, Theme.highlight()));
                lab.setIconTextGap(6);
                detail.add(Ui.row(lab, 8, x));
                detail.add(Box.createVerticalStrut(4));
            }
            if (s.alarms.isEmpty()) {
                Ui.Label none = Ui.label("아직 알람이 없어요", 12.5f, false, Ui.SUB);
                none.setAlignmentX(0f);
                detail.add(none);
            }
            detail.add(Box.createVerticalStrut(10));
            JComboBox<String> box = new JComboBox<>();
            for (int m : CHOICES) box.addItem(m == 0 ? "정각에" : TimeText.before(m));
            box.setSelectedIndex(3);
            Ui.FlatButton add = Ui.primaryButton("추가");
            add.addActionListener(e -> {
                List<Integer> l = new ArrayList<>(s.alarms);
                l.add(CHOICES[box.getSelectedIndex()]);
                s.setAlarms(l);
                HAZscheduleManager.update();
            });
            detail.add(Ui.row(box, 6, add));
            detail.add(Box.createVerticalStrut(18));

            Ui.Label linkLab = Ui.label("알람에서 열 링크", 12, true, Ui.SUB);
            linkLab.setAlignmentX(0f);
            detail.add(linkLab);
            detail.add(Box.createVerticalStrut(6));
            JTextField link = new JTextField(s.link);
            link.putClientProperty("placeholder", "예) 티켓팅 사이트");
            link.setAlignmentX(0f);
            link.setMaximumSize(new Dimension(Integer.MAX_VALUE, link.getPreferredSize().height));
            detail.add(link);
            detail.add(Box.createVerticalStrut(6));
            Ui.FlatButton saveLink = Ui.button("링크 저장");
            saveLink.addActionListener(e -> {
                s.link = Ui.normalizeLink(link.getText());
                HAZscheduleManager.update();
            });
            detail.add(Ui.row(saveLink));
        }
        detail.revalidate();
        detail.repaint();
    }

    public void refresh() {
        Schedule selected = list.getSelectedValue();
        LocalDate today = LocalDate.now();
        List<Schedule> all = new ArrayList<>();
        for (Schedule s : HAZscheduleManager.getAll()) {
            if (onlyAlarms.isSelected() && !s.hasAlarm()) continue;
            if (onlyFuture.isSelected() && s.nextOn(today) == null) continue;
            all.add(s);
        }
        all.sort(Comparator.comparing((Schedule s) -> {
            LocalDate n = s.nextOn(today);
            return n == null ? s.date : n;
        }).thenComparingInt(s -> s.start));
        model.clear();
        for (Schedule s : all) model.addElement(s);
        if (selected != null) {
            for (int i = 0; i < model.size(); i++) if (model.get(i).id.equals(selected.id)) list.setSelectedIndex(i);
        }
        showDetail();
    }
}
