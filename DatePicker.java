import javax.swing.*;
import java.awt.*;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.function.Consumer;

// 날짜 입력칸 + 누르면 뜨는 작은 달력 (클릭으로 날짜 선택)
public class DatePicker extends JPanel {
    private final JTextField field = new JTextField(9);
    private final Ui.FlatButton button = Ui.button("▼");
    private Runnable onChange = () -> { };

    public DatePicker(LocalDate initial) {
        super(new BorderLayout(4, 0));
        setOpaque(false);
        setDate(initial);
        button.setFont(Ui.font(10, false));
        button.setBorder(Ui.pad(4, 8, 4, 8));
        button.setToolTipText("달력에서 고르기");
        button.addActionListener(e -> popup(this, getDate() == null ? LocalDate.now() : getDate(), d -> { setDate(d); onChange.run(); }));
        field.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { onChange.run(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { onChange.run(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { onChange.run(); }
        });
        add(field, BorderLayout.CENTER);
        add(button, BorderLayout.EAST);
    }

    public LocalDate getDate() {
        try { return LocalDate.parse(field.getText().trim()); } catch (Exception e) { return null; }
    }
    public void setDate(LocalDate d) { field.setText(d == null ? "" : d.toString()); }
    public void setOnChange(Runnable r) { this.onChange = r; }
    @Override public void setEnabled(boolean b) { super.setEnabled(b); field.setEnabled(b); button.setEnabled(b); }

    // 작은 달력 팝업
    public static void popup(Component anchor, LocalDate initial, Consumer<LocalDate> onPick) {
        JPopupMenu menu = new JPopupMenu();
        menu.setBorder(BorderFactory.createLineBorder(Theme.border()));
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setBackground(Theme.surface());
        panel.setBorder(Ui.pad(8, 8, 8, 8));
        YearMonth[] month = {YearMonth.from(initial)};

        JLabel title = Ui.label("", 13, true, Theme.text());
        title.setHorizontalAlignment(SwingConstants.CENTER);
        JPanel grid = new JPanel(new GridLayout(7, 7, 2, 2));
        grid.setOpaque(false);

        Runnable[] draw = new Runnable[1];
        draw[0] = () -> {
            title.setText(month[0].getYear() + ". " + month[0].getMonthValue());
            grid.removeAll();
            for (String d : new String[]{"일", "월", "화", "수", "목", "금", "토"}) {
                JLabel l = Ui.label(d, 11, false, Theme.sub());
                l.setHorizontalAlignment(SwingConstants.CENTER);
                grid.add(l);
            }
            LocalDate first = month[0].atDay(1);
            for (int i = 0; i < first.getDayOfWeek().getValue() % 7; i++) grid.add(new JLabel());
            for (int d = 1; d <= month[0].lengthOfMonth(); d++) {
                LocalDate date = month[0].atDay(d);
                boolean sel = date.equals(initial);
                Ui.FlatButton b = new Ui.FlatButton(String.valueOf(d),
                        sel ? Theme.primary() : Theme.surface(),
                        sel ? Theme.onPrimary() : date.getDayOfWeek() == DayOfWeek.SUNDAY ? Theme.danger() : Theme.text());
                b.setBorder(Ui.pad(2, 2, 2, 2));
                b.setPreferredSize(new Dimension(30, 24));
                b.setFont(Ui.font(12, sel));
                b.addActionListener(e -> { menu.setVisible(false); onPick.accept(date); });
                grid.add(b);
            }
            grid.revalidate();
            grid.repaint();
            menu.pack();
        };

        Ui.FlatButton prev = new Ui.FlatButton("<", Theme.surface(), Theme.text());
        Ui.FlatButton next = new Ui.FlatButton(">", Theme.surface(), Theme.text());
        prev.addActionListener(e -> { month[0] = month[0].minusMonths(1); draw[0].run(); });
        next.addActionListener(e -> { month[0] = month[0].plusMonths(1); draw[0].run(); });
        JPanel head = Ui.clear(new BorderLayout());
        head.add(prev, BorderLayout.WEST);
        head.add(title, BorderLayout.CENTER);
        head.add(next, BorderLayout.EAST);

        panel.add(head, BorderLayout.NORTH);
        panel.add(grid, BorderLayout.CENTER);
        menu.add(panel);
        draw[0].run();
        menu.show(anchor, 0, anchor.getHeight());
    }
}
