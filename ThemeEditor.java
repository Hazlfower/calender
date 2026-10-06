import javax.swing.*;
import java.awt.*;
import java.io.File;

// 테마 색 직접 지정 창 (주 색상, 보조 색상 ... 10가지) + UI 종류
public class ThemeEditor extends JDialog {
    private final MainFrame frame;
    private final AppSettings HAZsettings;

    public ThemeEditor(MainFrame frame, AppSettings settings) {
        super(frame, "테마 설정", false);
        this.frame = frame;
        this.HAZsettings = settings;
        reload();
        pack();
        setResizable(false);
        setLocationRelativeTo(frame);
    }

    // 테마가 바뀔 때마다 이 창도 새 색으로 다시 만듦
    public void reload() {
        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setBackground(Theme.surface());
        root.setBorder(Ui.pad(16, 18, 16, 18));

        // UI 종류
        JComboBox<Theme.Style> styleBox = new JComboBox<>(Theme.Style.values());
        styleBox.setSelectedItem(Theme.style());
        styleBox.addActionListener(e -> {
            if (styleBox.getSelectedItem() == Theme.style()) return;
            HAZsettings.set("uiStyle", ((Theme.Style) styleBox.getSelectedItem()).name());
            HAZsettings.save();
            frame.rebuild();
        });
        JPanel top = Ui.clear(new FlowLayout(FlowLayout.LEFT, 8, 0));
        top.add(Ui.label("UI 종류", 13, true, Theme.text()));
        top.add(styleBox);
        top.add(Ui.label((Theme.isNight() ? "밤" : "낮") + " 모드 색을 편집 중", 12, false, Theme.sub()));

        // 색 10개 (2열)
        JPanel grid = Ui.clear(new GridLayout(5, 2, 16, 10));
        for (int i = 0; i < Theme.KEYS.length; i++) grid.add(colorRow(Theme.KEYS[i], Theme.LABELS[i]));

        Ui.FlatButton reset = Ui.button("기본값으로");
        Ui.FlatButton export = Ui.button("내보내기");
        Ui.FlatButton importBtn = Ui.button("가져오기");
        Ui.FlatButton close = Ui.primaryButton("닫기");
        reset.addActionListener(e -> {
            Theme.resetColors(HAZsettings);
            HAZsettings.save();
            frame.rebuild();
        });
        export.addActionListener(e -> {
            File f = Ui.saveFile(this, "테마 내보내기", "my_theme.hztheme", "hztheme");
            if (f == null) return;
            try { Theme.exportTo(HAZsettings, f.toPath()); JOptionPane.showMessageDialog(this, "내보냈어요! (이미지는 제외)"); }
            catch (Exception ex) { JOptionPane.showMessageDialog(this, "내보내기 실패: " + ex.getMessage()); }
        });
        importBtn.addActionListener(e -> {
            File f = Ui.openFile(this, "테마 가져오기", "hztheme");
            if (f == null) return;
            try { Theme.importFrom(HAZsettings, f.toPath()); frame.rebuild(); }
            catch (Exception ex) { JOptionPane.showMessageDialog(this, "가져오기 실패: " + ex.getMessage()); }
        });
        close.addActionListener(e -> setVisible(false));
        JPanel buttons = Ui.clear(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttons.add(reset);
        buttons.add(export);
        buttons.add(importBtn);
        buttons.add(close);

        root.add(top, BorderLayout.NORTH);
        root.add(grid, BorderLayout.CENTER);
        root.add(buttons, BorderLayout.SOUTH);
        setContentPane(root);
        revalidate();
        repaint();
    }

    // 라벨 / [색 네모] [#hex 입력칸]
    private JComponent colorRow(String key, String label) {
        Color current = Theme.c(key);
        JPanel p = Ui.clear(new BorderLayout(0, 3));
        p.add(Ui.label(label, 11, true, Theme.accent()), BorderLayout.NORTH);

        JButton swatch = new JButton() {
            @Override protected void paintComponent(Graphics g) {
                Ui.smooth(g);
                g.setColor(current);
                g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
                g.setColor(Theme.border());
                g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
            }
        };
        swatch.setPreferredSize(new Dimension(34, 28));
        swatch.setContentAreaFilled(false);
        swatch.setBorderPainted(false);
        swatch.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        swatch.addActionListener(e -> {
            Color c = JColorChooser.showDialog(this, label, current);
            if (c != null) apply(key, c);
        });

        JTextField hex = new JTextField(Theme.hex(current), 8);
        hex.addActionListener(e -> {
            try { apply(key, Color.decode(hex.getText().trim())); }
            catch (Exception ex) { Toolkit.getDefaultToolkit().beep(); hex.setText(Theme.hex(current)); }
        });
        hex.setToolTipText("#rrggbb 입력 후 Enter");

        JPanel row = Ui.clear(new BorderLayout(6, 0));
        row.add(swatch, BorderLayout.WEST);
        row.add(hex, BorderLayout.CENTER);
        p.add(row, BorderLayout.CENTER);
        return p;
    }

    private void apply(String key, Color c) {
        Theme.setColor(HAZsettings, key, c);
        HAZsettings.save();
        frame.rebuild();
    }
}
