import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

// 오른쪽: 예정된 일정 / (지난 일정 · 할 일)
public class RightPanel extends JPanel {
    private final MainFrame frame;
    private final ScheduleManager HAZscheduleManager;
    private final Todos todos;
    private final DefaultListModel<ScheduleManager.Occurrence> upcomingModel = new DefaultListModel<>();
    private final DefaultListModel<ScheduleManager.Occurrence> pastModel = new DefaultListModel<>();
    private final DefaultListModel<Todos.Item> todoModel = new DefaultListModel<>();
    private final CardLayout bottomCards = new CardLayout();
    private final JPanel bottomBody = Ui.clear(bottomCards);

    public RightPanel(MainFrame frame, ScheduleManager sm, Todos todos) {
        super(new GridLayout(2, 1, 0, 12));
        this.frame = frame;
        this.HAZscheduleManager = sm;
        this.todos = todos;
        setOpaque(false);
        setPreferredSize(new Dimension(240, 0));

        add(Ui.headerCard("예정된 일정", null, occurrenceList(upcomingModel, false)));
        add(buildBottomCard());
        refresh();
    }

    private JComponent occurrenceList(DefaultListModel<ScheduleManager.Occurrence> model, boolean past) {
        JList<ScheduleManager.Occurrence> list = new JList<>(model);
        list.setOpaque(false);
        list.setCellRenderer((l, v, i, sel, f) -> {
            JPanel p = Ui.clear(new BorderLayout(8, 0));
            p.setBorder(Ui.pad(6, 10, 6, 8));
            if (sel) { p.setOpaque(true); p.setBackground(Ui.blend(Theme.secondary(), Theme.surface(), 0.5)); }
            JLabel title = Ui.label(v.schedule.getTitle(), 13, true, past ? Theme.sub() : Theme.text());
            title.setIcon(Ui.dot(HAZscheduleManager.colorOf(v.schedule.getCategory()), 9));
            title.setIconTextGap(6);
            String when = v.start.getMonthValue() + "/" + v.start.getDayOfMonth() + " ("
                    + Schedule.dowName(v.start.getDayOfWeek()) + ") " + v.start.toLocalTime();
            if (!past) when += " · " + Ui.remaining(Duration.between(LocalDateTime.now(), v.start).toMinutes()) + " 뒤";
            p.add(title, BorderLayout.NORTH);
            p.add(Ui.label("   " + when, 11, false, Theme.sub()), BorderLayout.CENTER);
            return p;
        });
        list.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                ScheduleManager.Occurrence o = list.getSelectedValue();
                if (o == null) return;
                frame.goToDate(o.start.toLocalDate());
                if (e.getClickCount() == 2) frame.openEditor(o.schedule);
            }
        });
        JScrollPane sp = Ui.scroll(list);
        sp.setBorder(Ui.pad(4, 2, 6, 2));
        return sp;
    }

    private JComponent buildBottomCard() {
        Ui.FlatButton pastTab = tabButton("지난 일정");
        Ui.FlatButton todoTab = tabButton("할 일");
        pastTab.addActionListener(e -> { bottomCards.show(bottomBody, "past"); pastTab.setActive(true); todoTab.setActive(false); });
        todoTab.addActionListener(e -> { bottomCards.show(bottomBody, "todo"); todoTab.setActive(true); pastTab.setActive(false); });
        todoTab.setActive(true);

        bottomBody.add(buildTodo(), "todo");
        bottomBody.add(occurrenceList(pastModel, true), "past");
        bottomCards.show(bottomBody, "todo");

        JPanel tabs = Ui.clear(new FlowLayout(FlowLayout.RIGHT, 3, 6));
        tabs.add(todoTab);
        tabs.add(pastTab);
        return Ui.headerCard("", tabs, bottomBody);
    }

    private static Ui.FlatButton tabButton(String text) {
        Ui.FlatButton b = new Ui.FlatButton(text, Ui.blend(Theme.onPrimary(), Theme.primary(), 0.15), Theme.onPrimary()) {
            @Override protected void paintComponent(Graphics g) { super.paintComponent(g); }
        };
        b.setFont(Ui.font(12, true));
        b.setBorder(Ui.pad(2, 10, 2, 10));
        return b;
    }

    private JComponent buildTodo() {
        JList<Todos.Item> list = new JList<>(todoModel);
        list.setOpaque(false);
        list.setCellRenderer((l, v, i, sel, f) -> {
            JCheckBox cb = new JCheckBox(v.done ? "<html><strike>" + v.text + "</strike></html>" : v.text, v.done);
            cb.setOpaque(false);
            cb.setForeground(v.done ? Theme.sub() : Theme.text());
            cb.setBorder(Ui.pad(3, 8, 3, 8));
            return cb;
        });
        list.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                int idx = list.locationToIndex(e.getPoint());
                if (idx < 0 || !list.getCellBounds(idx, idx).contains(e.getPoint())) return;
                Todos.Item item = todoModel.get(idx);
                if (SwingUtilities.isRightMouseButton(e)) {
                    JPopupMenu menu = new JPopupMenu();
                    JMenuItem del = new JMenuItem("삭제");
                    del.addActionListener(a -> { todos.remove(item); refreshTodos(); });
                    menu.add(del);
                    menu.show(list, e.getX(), e.getY());
                } else {
                    todos.toggle(item);
                    refreshTodos();
                }
            }
        });

        JTextField input = new JTextField();
        input.setToolTipText("할 일을 쓰고 Enter (우클릭으로 삭제)");
        input.addActionListener(e -> { todos.add(input.getText()); input.setText(""); refreshTodos(); });

        JPanel p = Ui.clear(new BorderLayout(0, 4));
        p.setBorder(Ui.pad(4, 6, 8, 6));
        p.add(Ui.scroll(list), BorderLayout.CENTER);
        p.add(input, BorderLayout.SOUTH);
        refreshTodos();
        return p;
    }

    private void refreshTodos() {
        todoModel.clear();
        for (Todos.Item i : todos.all()) todoModel.addElement(i);
    }

    public void refresh() {
        LocalDateTime now = LocalDateTime.now();
        upcomingModel.clear();
        for (ScheduleManager.Occurrence o : HAZscheduleManager.upcoming(now, 60, 12)) upcomingModel.addElement(o);
        pastModel.clear();
        List<ScheduleManager.Occurrence> past = HAZscheduleManager.past(now, 60, 12);
        for (ScheduleManager.Occurrence o : past) pastModel.addElement(o);
    }
}
