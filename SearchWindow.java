import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDate;

// 일정 검색 (따로 뜨는 창). 제목·카테고리·메모·링크로 찾아요
public class SearchWindow extends JDialog {
    private final MainFrame frame;
    private final ScheduleManager HAZscheduleManager;
    private final JTextField field = new JTextField();
    private final DefaultListModel<Schedule> model = new DefaultListModel<>();
    private final JList<Schedule> list = new JList<>(model);
    private final Ui.Label count = Ui.label("", 12, false, Ui.SUB);

    public SearchWindow(MainFrame frame, ScheduleManager sm) {
        super(frame, "일정 검색", false);
        this.frame = frame;
        this.HAZscheduleManager = sm;
        setSize(460, 520);
        setLocationRelativeTo(frame);
        setIconImages(Ui.appIcons());

        JPanel root = new JPanel(new BorderLayout(0, 10)) {
            @Override protected void paintComponent(Graphics g) {
                g.setColor(Theme.surface());
                g.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        root.setBorder(Ui.pad(14, 14, 12, 14));

        field.setFont(Ui.font(15, false));
        field.putClientProperty("placeholder", "찾을 말 (제목, 카테고리, 메모)");
        field.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { search(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { search(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { search(); }
        });
        JPanel top = Ui.clear(new BorderLayout(0, 6));
        top.add(field, BorderLayout.CENTER);
        top.add(count, BorderLayout.SOUTH);

        list.setOpaque(false);
        list.setCellRenderer((l, v, i, sel, f) -> {
            JPanel p = Ui.clear(new BorderLayout(0, 2));
            if (sel) {
                p.setOpaque(true);
                p.setBackground(Ui.blend(Theme.highlight(), Theme.surface(), Theme.isNight() ? 0.3 : 0.14));
            }
            p.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.border()), Ui.pad(9, 10, 9, 10)));
            Ui.Label t = Ui.label(v.title, 14, true, Ui.TEXT);
            t.setIcon(Ui.dot(HAZscheduleManager.colorOf(v), 10));
            t.setIconTextGap(8);
            String r = v.repeat == Schedule.HAZRepeat.NONE ? "" : "  ·  " + v.repeatText();
            Ui.Label d = Ui.label(TimeText.ymd(v.date) + "  " + v.timeText() + "  ·  " + v.category + r, 11.5f, false, Ui.SUB);
            d.setBorder(Ui.pad(0, 18, 0, 0));
            p.add(t, BorderLayout.NORTH);
            p.add(d, BorderLayout.CENTER);
            if (!v.memo.isBlank()) {
                Ui.Label m = Ui.label(v.memo.replace('\n', ' '), 11.5f, false, Ui.SUB);
                m.setBorder(Ui.pad(0, 18, 0, 0));
                p.add(m, BorderLayout.SOUTH);
            }
            return p;
        });
        list.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                Schedule s = list.getSelectedValue();
                if (s == null) return;
                LocalDate next = s.nextOn(LocalDate.now());
                LocalDate target = next != null ? next : s.date;
                frame.goToDate(target);
                if (e.getClickCount() == 2) frame.editSchedule(s, s.occurrenceStart(target));
            }
        });
        JScrollPane sp = Ui.scroll(list);
        sp.setBorder(Ui.roundLine(10));

        root.add(top, BorderLayout.NORTH);
        root.add(sp, BorderLayout.CENTER);
        root.add(Ui.label("클릭: 그 날짜로 이동 · 더블클릭: 수정 · Esc: 닫기", 11.5f, false, Ui.SUB), BorderLayout.SOUTH);
        setContentPane(root);
        Ui.escCloses(this, () -> setVisible(false));
    }

    public void open(String query) {
        field.setText(query == null ? "" : query);
        search();
        setVisible(true);
        toFront();
        field.requestFocusInWindow();
        field.selectAll();
    }

    public void search() {
        model.clear();
        for (Schedule s : HAZscheduleManager.search(field.getText())) model.addElement(s);
        count.setText(field.getText().isBlank() ? "제목, 카테고리, 메모, 링크로 찾아요" : model.size() + "개 찾았어요");
    }

    public String query() { return field.getText(); }
}
