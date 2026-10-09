import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

// 테마 창: UI 종류, 프리셋, 10가지 색 직접 지정, 테마 파일 내보내기/가져오기
public class ThemeEditor extends JDialog {
    private final MainFrame frame;
    private final AppSettings HAZsettings;

    public ThemeEditor(MainFrame frame, AppSettings settings) {
        super(frame, "테마 꾸미기", false);
        this.frame = frame;
        this.HAZsettings = settings;
        setIconImages(Ui.appIcons());
        reload();
        pack();
        setResizable(false);
        setLocationRelativeTo(frame);
        Ui.escCloses(this, () -> setVisible(false));
    }

    // 테마가 바뀔 때마다 이 창도 새 색으로 다시 만듦
    public void reload() {
        JPanel root = new JPanel(new BorderLayout(0, 14)) {
            @Override protected void paintComponent(Graphics g) {
                g.setColor(Theme.surface());
                g.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        root.setBorder(Ui.pad(18, 20, 16, 20));

        // UI 종류 + 밤낮
        JComboBox<Theme.Style> styleBox = new JComboBox<>(Theme.Style.values());
        styleBox.setSelectedItem(Theme.style());
        styleBox.addActionListener(e -> {
            if (styleBox.getSelectedItem() == Theme.style()) return;
            HAZsettings.set("uiStyle", ((Theme.Style) styleBox.getSelectedItem()).name());
            HAZsettings.save();
            frame.rebuild();
        });
        Ui.Segmented mode = new Ui.Segmented(new String[]{"낮", "밤"}, Theme.isNight() ? 1 : 0, i -> {
            HAZsettings.setBool("nightMode", i == 1);
            HAZsettings.save();
            frame.rebuild();
        });

        // 프리셋
        List<Theme.Preset> presets = new ArrayList<>(Theme.builtInPresets());
        List<Theme.Preset> mine = Theme.userPresets(HAZsettings);
        presets.addAll(mine);
        JComboBox<Theme.Preset> presetBox = new JComboBox<>(presets.toArray(new Theme.Preset[0]));
        presetBox.setRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean s, boolean f) {
                JLabel lb = (JLabel) super.getListCellRendererComponent(l, v, i, s, f);
                if (v instanceof Theme.Preset) {
                    Theme.Preset p = (Theme.Preset) v;
                    lb.setText((p.builtIn ? "" : "★ ") + p);
                    lb.setIcon(swatches(p));
                    lb.setIconTextGap(8);
                }
                lb.setBorder(Ui.pad(4, 6, 4, 6));
                return lb;
            }
        });
        Ui.FlatButton applyPreset = Ui.primaryButton("적용");
        applyPreset.addActionListener(e -> {
            Theme.Preset p = (Theme.Preset) presetBox.getSelectedItem();
            if (p == null) return;
            Theme.applyPreset(HAZsettings, p);
            HAZsettings.save();
            frame.rebuild();
        });
        Ui.FlatButton savePreset = Ui.button("지금 색 저장");
        savePreset.setToolTipText("지금 색을 내 프리셋으로 저장");
        savePreset.addActionListener(e -> {
            String name = JOptionPane.showInputDialog(this, "프리셋 이름", "내 프리셋 " + (mine.size() + 1));
            if (name == null || name.isBlank()) return;
            Theme.saveUserPreset(HAZsettings, name.trim());
            HAZsettings.save();
            reload();
        });
        Ui.FlatButton delPreset = Ui.button("삭제");
        delPreset.setToolTipText("내가 저장한 프리셋 지우기");
        delPreset.addActionListener(e -> {
            Theme.Preset p = (Theme.Preset) presetBox.getSelectedItem();
            if (p == null || p.builtIn) { Ui.info(this, "프리셋", "기본 프리셋은 지울 수 없어요. ★ 표시된 내 프리셋만 지울 수 있어요."); return; }
            Theme.deleteUserPreset(HAZsettings, p.name);
            HAZsettings.save();
            reload();
        });

        JPanel top = Ui.clear(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(4, 0, 4, 10);
        c.gridy = 0; c.gridx = 0;
        top.add(Ui.label("UI 종류", 12.5f, true, Ui.SUB), c);
        c.gridx = 1;
        top.add(Ui.row(styleBox, 10, mode), c);
        c.gridy = 1; c.gridx = 0;
        top.add(Ui.label("프리셋", 12.5f, true, Ui.SUB), c);
        c.gridx = 1;
        top.add(Ui.row(presetBox, 6, applyPreset, 6, savePreset, 4, delPreset), c);

        // 색 10개 (2열)
        JPanel grid = Ui.clear(new GridLayout(5, 2, 18, 10));
        for (int i = 0; i < Theme.KEYS.length; i++) grid.add(colorRow(Theme.KEYS[i], Theme.LABELS[i], Theme.HELP[i]));
        JPanel middle = Ui.clear(new BorderLayout(0, 8));
        middle.add(Ui.label((Theme.isNight() ? "밤" : "낮") + " 모드 · " + Theme.style() + " 색 (네모를 누르거나 #색 코드를 쓰고 Enter)",
                12, false, Ui.SUB), BorderLayout.NORTH);
        middle.add(grid, BorderLayout.CENTER);

        Ui.FlatButton reset = Ui.button("기본값으로");
        Ui.FlatButton export = Ui.button("내보내기");
        Ui.FlatButton importBtn = Ui.button("가져오기");
        Ui.FlatButton close = Ui.primaryButton("닫기");
        export.setIcon(new Icons(Icons.Kind.EXPORT, 14));
        importBtn.setIcon(new Icons(Icons.Kind.IMPORT, 14));
        reset.addActionListener(e -> {
            Theme.resetColors(HAZsettings);
            HAZsettings.save();
            frame.rebuild();
        });
        export.addActionListener(e -> {
            File f = Ui.saveFile(this, "테마 내보내기", "my_theme.hztheme", "hztheme");
            if (f == null) return;
            try {
                Theme.exportTo(HAZsettings, f.toPath());
                Ui.info(this, "테마", "내보냈어요! (이미지는 경로만 들어가요)");
            } catch (Exception ex) { Ui.warn(this, "내보내기 실패: " + ex.getMessage()); }
        });
        importBtn.addActionListener(e -> {
            File f = Ui.openFile(this, "테마 가져오기", "hztheme");
            if (f == null) return;
            try {
                String missing = Theme.importFrom(HAZsettings, f.toPath());
                frame.rebuild();
                if (missing != null && Ui.confirm(this, "캐릭터 이미지", "테마에 들어 있던 캐릭터 이미지를 이 PC에서 찾을 수 없어요.\n"
                        + missing + "\n\n다른 이미지를 고를까요?")) {
                    File img = Ui.openFile(this, "캐릭터 이미지 고르기", "png", "jpg", "jpeg", "gif", "bmp");
                    if (img != null) { HAZsettings.set("characterImage", img.getAbsolutePath()); HAZsettings.save(); }
                }
            } catch (Exception ex) { Ui.warn(this, "가져오기 실패: " + ex.getMessage()); }
        });
        close.addActionListener(e -> setVisible(false));
        JPanel buttons = Ui.clear(new BorderLayout());
        JPanel l = Ui.clear(new FlowLayout(FlowLayout.LEFT, 6, 0));
        l.add(reset);
        l.add(export);
        l.add(importBtn);
        JPanel r = Ui.clear(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        r.add(close);
        buttons.add(l, BorderLayout.WEST);
        buttons.add(r, BorderLayout.EAST);

        root.add(top, BorderLayout.NORTH);
        root.add(middle, BorderLayout.CENTER);
        root.add(buttons, BorderLayout.SOUTH);
        setContentPane(root);
        revalidate();
        repaint();
        if (isVisible()) pack();
    }

    private static Icon swatches(Theme.Preset p) {
        return new Icon() {
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = Ui.aa(g);
                int[] idx = {0, 3, 4, 5};
                for (int i = 0; i < idx.length; i++) {
                    g2.setColor(Theme.decode(p.colors[idx[i]], "#888888"));
                    g2.fillOval(x + i * 9, y, 12, 12);
                    g2.setColor(new Color(0, 0, 0, 50));
                    g2.drawOval(x + i * 9, y, 12, 12);
                }
                g2.dispose();
            }
            public int getIconWidth() { return 40; }
            public int getIconHeight() { return 12; }
        };
    }

    // 라벨 / [색 네모] [#hex 입력칸]
    private JComponent colorRow(String key, String label, String help) {
        Color current = Theme.c(key);
        JPanel p = Ui.clear(new BorderLayout(0, 4));
        Ui.Label lab = Ui.label(label, 12, true, Ui.TEXT);
        lab.setToolTipText(help);
        p.add(lab, BorderLayout.NORTH);

        JButton swatch = new JButton() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = Ui.aa(g);
                g2.setColor(current);
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
                g2.setColor(Theme.border());
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
                g2.dispose();
            }
        };
        swatch.setPreferredSize(new Dimension(36, 30));
        swatch.setContentAreaFilled(false);
        swatch.setBorderPainted(false);
        swatch.setToolTipText(help);
        swatch.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        swatch.addActionListener(e -> {
            Color col = JColorChooser.showDialog(this, label, current);
            if (col != null) apply(key, col);
        });

        JTextField hex = new JTextField(Theme.hex(current), 8);
        hex.setToolTipText("#rrggbb 입력 후 Enter");
        hex.addActionListener(e -> {
            try { apply(key, Color.decode(hex.getText().trim())); }
            catch (Exception ex) { Toolkit.getDefaultToolkit().beep(); hex.setText(Theme.hex(current)); }
        });

        JPanel row = Ui.clear(new BorderLayout(6, 0));
        row.add(swatch, BorderLayout.WEST);
        row.add(hex, BorderLayout.CENTER);
        p.add(row, BorderLayout.CENTER);
        return p;
    }

    private void apply(String key, Color col) {
        Theme.setColor(HAZsettings, key, col);
        HAZsettings.save();
        frame.rebuild();
    }
}
