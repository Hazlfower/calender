import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDate;

// 일정 검색 (따로 뜨는 창)
public class SearchWindow extends JDialog {
    private final MainFrame frame;
    private final ScheduleManager HAZscheduleManager;
    private final JTextField field = new JTextField();
    private final DefaultListModel<Schedule> model = new DefaultListModel<>();
    private final JList<Schedule> list = new JList<>(model);
    private final JLabel count = Ui.label("", 12, false, Theme.sub());

    public SearchWindow(MainFrame frame, ScheduleManager sm) {
        super(frame, "일정 검색", false);
        this.frame = frame;
        this.HAZscheduleManager = sm;
        setSize(440, 480);
        setLocationRelativeTo(frame);

        JPanel root = new JPanel(new BorderLayout(0, 8));
        root.setBackground(Theme.surface());
        root.setBorder(Ui.pad(12, 12, 12, 12));

        field.setFont(Ui.font(15, false));
        field.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { search(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { search(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { search(); }
        });
        JPanel top = Ui.clear(new BorderLayout(0, 4));
        top.add(field, BorderLayout.CENTER);
        top.add(count, BorderLayout.SOUTH);

        list.setCellRenderer((l, v, i, sel, f) -> {
            JPanel p = new JPanel(new BorderLayout());
            p.setBackground(sel ? Ui.blend(Theme.secondary(), Theme.surface(), 0.5) : Theme.surface());
            p.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.border()), Ui.pad(8, 10, 8, 10)));
            JLabel t = Ui.label(v.getTitle(), 14, true, Theme.text());
            t.setIcon(Ui.dot(HAZscheduleManager.colorOf(v.getCategory()), 10));
            t.setIconTextGap(8);
            String r = v.getRepeat() == Schedule.HAZRepeat.NONE ? "" : "  · " + v.repeatSummary();
            JLabel d = Ui.label(v.getDate() + "  " + v.timeText() + "  [" + v.getCategory() + "]" + r, 11, false, Theme.sub());
            p.add(t, BorderLayout.NORTH);
            p.add(d, BorderLayout.SOUTH);
            return p;
        });
        list.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                Schedule s = list.getSelectedValue();
                if (s == null) return;
                frame.goToDate(HAZscheduleManager.nextDateOf(s, LocalDate.now().isAfter(s.getDate()) ? LocalDate.now() : s.getDate()));
                if (e.getClickCount() == 2) frame.openEditor(s);
            }
        });
        JScrollPane sp = new JScrollPane(list);
        sp.setBorder(BorderFactory.createLineBorder(Theme.border()));

        root.add(top, BorderLayout.NORTH);
        root.add(sp, BorderLayout.CENTER);
        root.add(Ui.label("클릭 = 그 날짜로 이동 · 더블클릭 = 수정", 11, false, Theme.sub()), BorderLayout.SOUTH);
        setContentPane(root);
    }

    public void open(String query) {
        field.setText(query == null ? "" : query);
        search();
        setVisible(true);
        field.requestFocusInWindow();
    }

    public void search() {
        model.clear();
        for (Schedule s : HAZscheduleManager.search(field.getText())) model.addElement(s);
        count.setText(field.getText().isBlank() ? "제목, 카테고리, 메모로 찾아요" : model.size() + "개 찾았어요");
    }

    public String query() { return field.getText(); }
}
