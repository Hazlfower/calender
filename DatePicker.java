import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// 날짜 입력칸 + 달력 버튼 (누르면 작은 달력이 떠서 클릭으로 고름)
// 직접 쓸 때는 2026-10-06, 2026.10.6, 10/6, 10월 6일 처럼 써도 돼요
public class DatePicker extends JPanel {
    private final JTextField field = new JTextField(9);
    private final JButton button;
    private Runnable onChange = () -> { };

    public DatePicker(LocalDate initial) {
        super(new BorderLayout(4, 0));
        setOpaque(false);
        setDate(initial);
        button = Ui.iconButton(Icons.Kind.CALENDAR, "달력에서 고르기", false,
                () -> popup(this, getDate() == null ? LocalDate.now() : getDate(), d -> { setDate(d); onChange.run(); }));
        button.setPreferredSize(new Dimension(32, 30));
        field.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { onChange.run(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { onChange.run(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { onChange.run(); }
        });
        add(field, BorderLayout.CENTER);
        add(button, BorderLayout.EAST);
    }

    private static final Pattern NUMS = Pattern.compile("\\d+");

    public static LocalDate parse(String text) {
        if (text == null) return null;
        Matcher m = NUMS.matcher(text);
        int[] v = new int[3];
        int n = 0;
        while (m.find() && n < 3) v[n++] = Integer.parseInt(m.group());
        try {
            if (n == 3) return LocalDate.of(v[0] < 100 ? 2000 + v[0] : v[0], v[1], v[2]);
            if (n == 2) return LocalDate.of(LocalDate.now().getYear(), v[0], v[1]);
        } catch (Exception ignored) { }
        return null;
    }

    public LocalDate getDate() { return parse(field.getText()); }

    public void setDate(LocalDate d) { field.setText(d == null ? "" : d.toString()); }

    public void setOnChange(Runnable r) { this.onChange = r; }

    @Override public void setEnabled(boolean b) {
        super.setEnabled(b);
        field.setEnabled(b);
        button.setEnabled(b);
    }

    // 작은 달력 팝업
    public static void popup(Component anchor, LocalDate initial, Consumer<LocalDate> onPick) {
        JPopupMenu menu = new JPopupMenu();
        menu.setBorder(BorderFactory.createLineBorder(Theme.border()));
        JPanel panel = new JPanel(new BorderLayout(0, 6)) {
            @Override protected void paintComponent(Graphics g) {
                g.setColor(Theme.surface());
                g.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        panel.setBorder(Ui.pad(8, 8, 8, 8));
        YearMonth[] month = {YearMonth.from(initial)};

        Ui.Label title = Ui.label("", 13.5f, true, Ui.TEXT);
        title.setHorizontalAlignment(SwingConstants.CENTER);
        JPanel grid = Ui.clear(new GridLayout(0, 7, 2, 2));

        Runnable[] draw = new Runnable[1];
        draw[0] = () -> {
            title.setText(month[0].getYear() + "년 " + month[0].getMonthValue() + "월");
            grid.removeAll();
            String[] names = {"일", "월", "화", "수", "목", "금", "토"};
            for (int i = 0; i < 7; i++) {
                Ui.Label l = Ui.label(names[i], 11, false, i == 0 ? Ui.DANGER : Ui.SUB);
                l.setHorizontalAlignment(SwingConstants.CENTER);
                grid.add(l);
            }
            LocalDate first = month[0].atDay(1);
            for (int i = 0; i < first.getDayOfWeek().getValue() % 7; i++) grid.add(new JLabel());
            for (int d = 1; d <= month[0].lengthOfMonth(); d++) {
                LocalDate date = month[0].atDay(d);
                grid.add(new DayButton(date, date.equals(initial), () -> {
                    menu.setVisible(false);
                    onPick.accept(date);
                }));
            }
            grid.revalidate();
            grid.repaint();
            menu.pack();
        };

        JButton prev = Ui.iconButton(Icons.Kind.LEFT, "이전 달", false, () -> { month[0] = month[0].minusMonths(1); draw[0].run(); });
        JButton next = Ui.iconButton(Icons.Kind.RIGHT, "다음 달", false, () -> { month[0] = month[0].plusMonths(1); draw[0].run(); });
        prev.setPreferredSize(new Dimension(30, 28));
        next.setPreferredSize(new Dimension(30, 28));
        JPanel head = Ui.clear(new BorderLayout());
        head.add(prev, BorderLayout.WEST);
        head.add(title, BorderLayout.CENTER);
        head.add(next, BorderLayout.EAST);

        Ui.FlatButton today = Ui.button("오늘");
        today.setFont(Ui.font(12, true));
        today.setBorder(Ui.pad(4, 10, 4, 10));
        today.addActionListener(e -> { menu.setVisible(false); onPick.accept(LocalDate.now()); });
        JPanel foot = Ui.clear(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        foot.add(today);

        panel.add(head, BorderLayout.NORTH);
        panel.add(grid, BorderLayout.CENTER);
        panel.add(foot, BorderLayout.SOUTH);
        menu.add(panel);
        draw[0].run();
        menu.show(anchor, 0, anchor.getHeight());
    }

    private static class DayButton extends JComponent {
        private final LocalDate date;
        private final boolean selected;
        private boolean hover;

        DayButton(LocalDate date, boolean selected, Runnable pick) {
            this.date = date;
            this.selected = selected;
            setPreferredSize(new Dimension(32, 28));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            String hol = Holidays.get(date);
            if (hol != null) setToolTipText(hol);
            addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) { pick.run(); }
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { hover = false; repaint(); }
            });
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = Ui.aa(g);
            int s = Math.min(getWidth(), getHeight()) - 2;
            Ellipse2D c = new Ellipse2D.Float((getWidth() - s) / 2f, (getHeight() - s) / 2f, s, s);
            boolean today = date.equals(LocalDate.now());
            if (selected) { g2.setColor(Theme.highlight()); g2.fill(c); }
            else if (hover) { g2.setColor(Ui.alpha(Theme.highlight(), 40)); g2.fill(c); }
            if (today && !selected) {
                g2.setColor(Theme.highlight());
                g2.setStroke(new BasicStroke(1.4f));
                g2.draw(c);
            }
            Color fg = selected ? Theme.onColor(Theme.highlight())
                    : (date.getDayOfWeek() == DayOfWeek.SUNDAY || Holidays.get(date) != null) ? Theme.danger()
                    : date.getDayOfWeek() == DayOfWeek.SATURDAY ? Theme.saturday() : Theme.text();
            g2.setColor(fg);
            g2.setFont(Ui.font(12, selected || today));
            Ui.centerText(g2, String.valueOf(date.getDayOfMonth()), getWidth() / 2.0,
                    (getHeight() + g2.getFontMetrics().getAscent() - g2.getFontMetrics().getDescent()) / 2.0);
            g2.dispose();
        }
    }
}
