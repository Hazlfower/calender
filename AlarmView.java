import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

// "알람" 탭: 모든 일정의 알람 켜기/끄기
public class AlarmView extends JPanel {
    private final ScheduleManager HAZscheduleManager;
    private final DefaultListModel<Schedule> model = new DefaultListModel<>();
    private final JList<Schedule> list = new JList<>(model);
    private final JSpinner minutes = new JSpinner(new SpinnerNumberModel(10, 0, 1440, 5));

    public AlarmView(ScheduleManager sm, AlarmManager am) {
        super(new BorderLayout());
        this.HAZscheduleManager = sm;
        setOpaque(false);

        list.setOpaque(false);
        list.setCellRenderer((l, s, i, sel, f) -> {
            JPanel p = Ui.clear(new BorderLayout());
            p.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.border()), Ui.pad(8, 12, 8, 12)));
            if (sel) { p.setOpaque(true); p.setBackground(Ui.blend(Theme.secondary(), Theme.surface(), 0.5)); }
            JLabel t = Ui.label(s.getTitle(), 14, true, s.hasAlarm() ? Theme.text() : Theme.sub());
            t.setIcon(Ui.dot(sm.colorOf(s.getCategory()), 10));
            t.setIconTextGap(8);
            String when = s.getRepeat() == Schedule.HAZRepeat.NONE ? s.getDate() + " " + s.getStart()
                    : s.repeatSummary() + " " + s.getStart();
            JLabel w = Ui.label(when, 12, false, Theme.sub());
            JLabel a = Ui.label(s.hasAlarm() ? "알람 " + (s.getAlarmMinutes() == 0 ? "정각" : s.getAlarmMinutes() + "분 전") : "꺼짐",
                    13, true, s.hasAlarm() ? Theme.accent() : Theme.sub());
            JPanel left = Ui.clear(new GridLayout(2, 1));
            left.add(t);
            left.add(w);
            p.add(left, BorderLayout.CENTER);
            p.add(a, BorderLayout.EAST);
            return p;
        });
        list.addListSelectionListener(e -> {
            Schedule s = list.getSelectedValue();
            if (s != null && s.hasAlarm()) minutes.setValue(s.getAlarmMinutes());
        });

        Ui.FlatButton on = Ui.primaryButton("알람 켜기 / 변경");
        Ui.FlatButton off = Ui.button("알람 끄기");
        Ui.FlatButton test = Ui.button("미리보기");
        on.addActionListener(e -> setAlarm((Integer) minutes.getValue()));
        off.addActionListener(e -> setAlarm(-1));
        test.addActionListener(e -> am.showTest());
        JPanel bottom = Ui.clear(new FlowLayout(FlowLayout.LEFT, 6, 8));
        bottom.add(minutes);
        bottom.add(Ui.label("분 전", 13, false, Theme.text()));
        bottom.add(on);
        bottom.add(off);
        bottom.add(test);

        JPanel body = Ui.clear(new BorderLayout());
        body.add(Ui.scroll(list), BorderLayout.CENTER);
        body.add(bottom, BorderLayout.SOUTH);
        body.setBorder(Ui.pad(4, 8, 4, 8));
        add(Ui.headerCard("알람", null, body), BorderLayout.CENTER);
        refresh();
    }

    private void setAlarm(int value) {
        Schedule s = list.getSelectedValue();
        if (s == null) { JOptionPane.showMessageDialog(this, "일정을 먼저 선택하세요."); return; }
        s.setAlarmMinutes(value);
        HAZscheduleManager.update();
    }

    public void refresh() {
        Schedule selected = list.getSelectedValue();
        List<Schedule> all = new ArrayList<>(HAZscheduleManager.getAll());
        all.sort(Comparator.comparing(Schedule::getDate).thenComparing(Schedule::getStart));
        model.clear();
        for (Schedule s : all) model.addElement(s);
        if (selected != null) list.setSelectedValue(selected, true);
    }
}
