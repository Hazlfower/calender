import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

// 오른쪽: D-Day 띠 / 예정된 일정 / (할 일 · 지난 일정)
public class RightPanel extends JPanel {
    private final MainFrame frame;
    private final ScheduleManager HAZscheduleManager;
    private final Todos todos;
    private final DefaultListModel<ScheduleManager.Occurrence> upcomingModel = new DefaultListModel<>();
    private final DefaultListModel<ScheduleManager.Occurrence> pastModel = new DefaultListModel<>();
    private final DefaultListModel<Todos.Item> todoModel = new DefaultListModel<>();
    private final CardLayout bottomCards = new CardLayout();
    private final JPanel bottomBody = Ui.clear(bottomCards);
    private final JPanel ddayStrip = Ui.clear(null);
    private Ui.Segmented bottomTabs;

    public RightPanel(MainFrame frame, ScheduleManager sm, Todos todos) {
        super(new BorderLayout(0, 12));
        this.frame = frame;
        this.HAZscheduleManager = sm;
        this.todos = todos;
        setOpaque(false);
        setPreferredSize(new Dimension(258, 0));

        ddayStrip.setLayout(new BoxLayout(ddayStrip, BoxLayout.Y_AXIS));
        add(ddayStrip, BorderLayout.NORTH);

        JPanel lists = Ui.clear(new GridLayout(2, 1, 0, 12));
        lists.add(Ui.headerCard("예정된 일정", null, occurrenceList(upcomingModel, false)));
        lists.add(buildBottomCard());
        add(lists, BorderLayout.CENTER);
        refresh();
    }

    // ------------------------------------------------------------------ D-Day 띠

    private void refreshDDays() {
        ddayStrip.removeAll();
        List<DDay> list = HAZscheduleManager.activeDDays();
        int shown = 0;
        for (DDay d : list) {
            if (shown++ == 3) break;
            ddayStrip.add(ddayChip(d));
            ddayStrip.add(Box.createVerticalStrut(6));
        }
        ddayStrip.setVisible(!list.isEmpty());
        ddayStrip.revalidate();
        ddayStrip.repaint();
    }

    private JComponent ddayChip(DDay d) {
        JComponent chip = new JComponent() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = Ui.aa(g);
                int r = Theme.radius();
                boolean soon = d.daysLeft() >= 0 && d.daysLeft() <= 7;
                Color fill = soon ? Ui.blend(Theme.highlight(), Theme.surface(), Theme.isNight() ? 0.35 : 0.14) : Theme.surface();
                g2.setColor(fill);
                g2.fill(new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1, getHeight() - 1, r, r));
                g2.setColor(Theme.border());
                g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1, getHeight() - 1, r, r));
                g2.setFont(Ui.semi(18f));
                g2.setColor(d.daysLeft() < 0 ? Theme.sub() : Theme.highlight());
                String lab = d.label();
                g2.drawString(lab, 14, 27);
                int lw = g2.getFontMetrics().stringWidth(lab);
                g2.setFont(Ui.font(12.5f, true));
                g2.setColor(Theme.text());
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(Ui.ellipsize(d.name, fm, getWidth() - lw - 34), 14 + lw + 10, 20);
                g2.setFont(Ui.font(11f, false));
                g2.setColor(Theme.sub());
                g2.drawString(TimeText.ymd(d.date), 14 + lw + 10, 34);
                g2.dispose();
            }
        };
        chip.setPreferredSize(new Dimension(200, 44));
        chip.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        chip.setAlignmentX(0f);
        chip.setToolTipText("클릭: 그 날짜로 이동 · 우클릭: D-Day 관리");
        chip.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        chip.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) frame.showPage("D-Day");
                else frame.goToDate(d.date);
            }
        });
        return chip;
    }

    // ------------------------------------------------------------------ 일정 목록

    private JComponent occurrenceList(DefaultListModel<ScheduleManager.Occurrence> model, boolean past) {
        JList<ScheduleManager.Occurrence> list = new JList<>(model);
        list.setOpaque(false);
        list.setCellRenderer((l, v, i, sel, f) -> {
            JPanel p = Ui.clear(new BorderLayout(8, 1));
            p.setBorder(Ui.pad(6, 12, 6, 10));
            if (sel) {
                p.setOpaque(true);
                p.setBackground(Ui.blend(Theme.highlight(), Theme.surface(), Theme.isNight() ? 0.3 : 0.14));
            }
            Ui.Label title = Ui.label(v.schedule.title, 13, true, past ? Ui.SUB : Ui.TEXT);
            title.setIcon(Ui.dot(HAZscheduleManager.colorOf(v.schedule), 9));
            title.setIconTextGap(7);
            String when = TimeText.relDay(v.date) + (v.schedule.allDay() ? " · 하루 종일" : " " + TimeText.fmt(v.schedule.start));
            if (!past && !v.schedule.allDay()) when += " · " + Ui.remaining(Duration.between(LocalDateTime.now(), v.start).toMinutes()) + " 뒤";
            Ui.Label w = Ui.label(when, 11.5f, false, Ui.SUB);
            w.setBorder(Ui.pad(0, 16, 0, 0));
            p.add(title, BorderLayout.NORTH);
            p.add(w, BorderLayout.CENTER);
            return p;
        });
        list.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                int idx = list.locationToIndex(e.getPoint());
                if (idx < 0 || !list.getCellBounds(idx, idx).contains(e.getPoint())) return;
                ScheduleManager.Occurrence o = model.get(idx);
                frame.goToDate(o.date);
                if (e.getClickCount() == 2) frame.editSchedule(o.schedule, o.date);
            }
        });
        JScrollPane sp = Ui.scroll(list);
        sp.setBorder(Ui.pad(4, 0, 6, 0));
        JPanel wrap = Ui.clear(new BorderLayout());
        wrap.add(sp, BorderLayout.CENTER);
        Ui.Label empty = Ui.label(past ? "최근 60일 동안 지난 일정이 없어요" : "앞으로 60일 동안 일정이 없어요", 12, false, Ui.SUB);
        empty.setBorder(Ui.pad(12, 14, 0, 0));
        wrap.putClientProperty("empty", empty);
        Runnable sync = () -> {
            if (model.isEmpty() && empty.getParent() == null) wrap.add(empty, BorderLayout.NORTH);
            if (!model.isEmpty() && empty.getParent() != null) wrap.remove(empty);
            wrap.revalidate();
            wrap.repaint();
        };
        model.addListDataListener(new javax.swing.event.ListDataListener() {
            public void intervalAdded(javax.swing.event.ListDataEvent e) { sync.run(); }
            public void intervalRemoved(javax.swing.event.ListDataEvent e) { sync.run(); }
            public void contentsChanged(javax.swing.event.ListDataEvent e) { sync.run(); }
        });
        sync.run();
        return wrap;
    }

    // ------------------------------------------------------------------ 할 일 / 지난 일정

    private JComponent buildBottomCard() {
        bottomBody.add(buildTodo(), "todo");
        bottomBody.add(occurrenceList(pastModel, true), "past");
        bottomCards.show(bottomBody, "todo");
        bottomTabs = new Ui.Segmented(new String[]{"할 일", "지난 일정"}, 0,
                i -> bottomCards.show(bottomBody, i == 0 ? "todo" : "past")) {
            @Override public Dimension getPreferredSize() { return new Dimension(140, 26); }
        };
        bottomTabs.setFont(Ui.font(11.5f, true));
        JPanel tabWrap = Ui.clear(new FlowLayout(FlowLayout.RIGHT, 0, 5));
        tabWrap.add(bottomTabs);
        return Ui.headerCard("", tabWrap, bottomBody);
    }

    private JComponent buildTodo() {
        JList<Todos.Item> list = new JList<>(todoModel);
        list.setOpaque(false);
        list.setCellRenderer((l, v, i, sel, f) -> {
            JCheckBox cb = new JCheckBox("<html>" + (v.done ? "<strike>" + Ui.html(v.text) + "</strike>" : Ui.html(v.text)) + "</html>", v.done);
            cb.setOpaque(false);
            cb.setFont(Ui.font(13, false));
            cb.setForeground(v.done ? Theme.sub() : Theme.text());
            cb.setBorder(Ui.pad(4, 10, 4, 8));
            return cb;
        });
        list.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                int idx = list.locationToIndex(e.getPoint());
                if (idx < 0 || !list.getCellBounds(idx, idx).contains(e.getPoint())) return;
                Todos.Item item = todoModel.get(idx);
                if (SwingUtilities.isRightMouseButton(e)) {
                    JPopupMenu menu = new JPopupMenu();
                    menu.add(Ui.item("고치기", () -> {
                        String t = (String) JOptionPane.showInputDialog(frame, "할 일", "할 일 고치기", JOptionPane.PLAIN_MESSAGE, null, null, item.text);
                        if (t != null) { todos.edit(item, t); refreshTodos(); }
                    }));
                    menu.add(Ui.item("삭제", () -> { todos.remove(item); refreshTodos(); }));
                    menu.addSeparator();
                    menu.add(Ui.item("다 한 일 모두 지우기", () -> { todos.clearDone(); refreshTodos(); }));
                    menu.show(list, e.getX(), e.getY());
                } else {
                    todos.toggle(item);
                    refreshTodos();
                }
            }
        });

        JTextField input = new JTextField();
        input.putClientProperty("placeholder", "할 일을 쓰고 Enter");
        input.setToolTipText("클릭: 완료 표시 · 우클릭: 고치기/삭제");
        input.addActionListener(e -> {
            todos.add(input.getText());
            input.setText("");
            refreshTodos();
        });

        JPanel p = Ui.clear(new BorderLayout(0, 6));
        p.setBorder(Ui.pad(4, 8, 10, 8));
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
        for (ScheduleManager.Occurrence o : HAZscheduleManager.upcoming(now, 60, 15)) upcomingModel.addElement(o);
        pastModel.clear();
        for (ScheduleManager.Occurrence o : HAZscheduleManager.past(now, 60, 15)) pastModel.addElement(o);
        refreshDDays();
    }
}
