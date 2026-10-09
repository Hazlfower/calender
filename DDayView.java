import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

// "D-Day" 탭: 여러 개 만들고, '표시'를 켠 것만 오른쪽 패널·위젯에 보여요
public class DDayView extends JPanel {
    private final ScheduleManager HAZscheduleManager;
    private final JPanel listPanel = new Ui.WidthTracking(null);

    public DDayView(ScheduleManager sm) {
        super(new BorderLayout());
        this.HAZscheduleManager = sm;
        setOpaque(false);
        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
        listPanel.setBorder(Ui.pad(10, 14, 14, 14));

        Ui.FlatButton add = Ui.headerButton("+ D-Day 추가");
        add.addActionListener(e -> {
            DDay d = new DDay();
            d.date = LocalDate.now().plusDays(30);
            if (edit(this, d, true)) HAZscheduleManager.addDDay(d);
        });
        JPanel wrap = Ui.clear(new BorderLayout());
        wrap.add(listPanel, BorderLayout.NORTH);
        add(Ui.headerCard("D-Day", add, Ui.scroll(wrap)), BorderLayout.CENTER);
        refresh();
    }

    public void refresh() {
        listPanel.removeAll();
        List<DDay> all = new ArrayList<>(HAZscheduleManager.getDDays());
        all.sort(Comparator.comparing((DDay d) -> d.daysLeft() < 0).thenComparingLong(d -> Math.abs(d.daysLeft())));
        if (all.isEmpty()) {
            Ui.Label empty = Ui.label("<html>아직 D-Day가 없어요.<br>위의 + D-Day 추가를 누르거나, 달력에서 날짜를 우클릭해 보세요.</html>", 13, false, Ui.SUB);
            empty.setAlignmentX(0f);
            empty.setBorder(Ui.pad(10, 4, 0, 0));
            listPanel.add(empty);
        }
        for (DDay d : all) {
            listPanel.add(row(d));
            listPanel.add(Box.createVerticalStrut(8));
        }
        listPanel.revalidate();
        listPanel.repaint();
    }

    private JComponent row(DDay d) {
        Ui.RoundPanel p = new Ui.RoundPanel(new BorderLayout(14, 0), null, null, -1) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = Ui.aa(g);
                int r = Theme.radius();
                g2.setColor(Theme.isNight() ? Ui.blend(Color.WHITE, Theme.surface(), 0.05) : Ui.blend(Theme.bg(), Theme.surface(), 0.5));
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, r, r);
                g2.dispose();
            }
        };
        p.setBorder(Ui.pad(10, 16, 10, 10));
        p.setAlignmentX(0f);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 64));

        Ui.Label big = Ui.label(d.label(), 22, true, d.daysLeft() < 0 ? Ui.SUB : Ui.HIGHLIGHT);
        big.setFont(Ui.semi(22));
        big.setPreferredSize(new Dimension(96, 40));
        Ui.Label name = Ui.label(d.name.isBlank() ? "(이름 없음)" : d.name, 14.5f, true, d.active ? Ui.TEXT : Ui.SUB);
        Ui.Label date = Ui.label(TimeText.ymd(d.date), 12, false, Ui.SUB);
        JPanel mid = Ui.clear(new GridLayout(2, 1));
        mid.add(name);
        mid.add(date);

        JCheckBox show = new JCheckBox("표시", d.active);
        show.setToolTipText("켜면 오른쪽 패널과 위젯에 보여요");
        show.addActionListener(e -> { d.active = show.isSelected(); HAZscheduleManager.changed(); });
        JButton editB = Ui.iconButton(Icons.Kind.EDIT, "고치기", false, () -> { if (edit(this, d, false)) HAZscheduleManager.changed(); });
        JButton del = Ui.iconButton(Icons.Kind.TRASH, "삭제", false, () -> {
            if (Ui.confirm(this, "D-Day 삭제", "「" + d.name + "」 D-Day를 삭제할까요?")) HAZscheduleManager.removeDDay(d);
        });
        JPanel right = Ui.clear(new FlowLayout(FlowLayout.RIGHT, 4, 4));
        right.add(show);
        right.add(editB);
        right.add(del);

        p.add(big, BorderLayout.WEST);
        p.add(mid, BorderLayout.CENTER);
        p.add(right, BorderLayout.EAST);
        p.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && edit(DDayView.this, d, false)) HAZscheduleManager.changed();
            }
        });
        return p;
    }

    // D-Day 이름·날짜 입력 창. 확인을 누르면 true
    public static boolean edit(Component parent, DDay d, boolean isNew) {
        JTextField name = new JTextField(d.name, 18);
        name.putClientProperty("placeholder", "예) 중간고사, 콘서트");
        DatePicker date = new DatePicker(d.date);
        JCheckBox active = new JCheckBox("오른쪽 패널과 위젯에 표시", d.active);
        JPanel p = Ui.clear(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(4, 0, 4, 10);
        c.gridy = 0; p.add(Ui.label("이름", 13, true, Ui.SUB), c);
        c.gridx = 1; c.weightx = 1; p.add(name, c);
        c.gridy = 1; c.gridx = 0; c.weightx = 0; p.add(Ui.label("날짜", 13, true, Ui.SUB), c);
        c.gridx = 1; c.weightx = 1; p.add(date, c);
        c.gridy = 2; c.gridx = 1; p.add(active, c);
        SwingUtilities.invokeLater(name::requestFocusInWindow);
        while (true) {
            int r = JOptionPane.showConfirmDialog(parent, p, isNew ? "새 D-Day" : "D-Day 고치기", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (r != JOptionPane.OK_OPTION) return false;
            LocalDate dd = date.getDate();
            if (name.getText().isBlank()) { Ui.warn(parent, "D-Day 이름을 적어 주세요."); continue; }
            if (dd == null) { Ui.warn(parent, "날짜를 알아볼 수 없어요. 예) 2026-12-25"); continue; }
            d.name = name.getText().trim();
            d.date = dd;
            d.active = active.isSelected();
            return true;
        }
    }
}
