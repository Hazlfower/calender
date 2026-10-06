import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Arc2D;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.io.File;

// 공통 디자인 도구: 폰트, 둥근 패널, 납작한 버튼, 아이콘, 윈도우 파일 창
public class Ui {
    public static Font font(float size, boolean bold) {
        return new Font("맑은 고딕", bold ? Font.BOLD : Font.PLAIN, Math.round(size));
    }

    public static void smooth(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
    }

    // a를 t만큼, b를 (1-t)만큼 섞은 색
    public static Color blend(Color a, Color b, double t) {
        return new Color(
                (int) Math.round(a.getRed() * t + b.getRed() * (1 - t)),
                (int) Math.round(a.getGreen() * t + b.getGreen() * (1 - t)),
                (int) Math.round(a.getBlue() * t + b.getBlue() * (1 - t)));
    }

    public static Color alpha(Color c, int a) { return new Color(c.getRed(), c.getGreen(), c.getBlue(), a); }

    public static Border pad(int t, int l, int b, int r) { return BorderFactory.createEmptyBorder(t, l, b, r); }

    public static JLabel label(String text, float size, boolean bold, Color color) {
        JLabel l = new JLabel(text);
        l.setFont(font(size, bold));
        l.setForeground(color);
        return l;
    }

    public static JPanel clear(LayoutManager layout) {
        JPanel p = new JPanel(layout);
        p.setOpaque(false);
        return p;
    }

    // ===== 둥근 모서리 패널 =====
    public static class RoundPanel extends JPanel {
        private Color fill, line;
        private final int radius;

        public RoundPanel(LayoutManager layout, Color fill, Color line, int radius) {
            super(layout);
            this.fill = fill;
            this.line = line;
            this.radius = radius;
            setOpaque(false);
        }
        public void setFill(Color fill) { this.fill = fill; repaint(); }

        @Override protected void paintComponent(Graphics g) {
            smooth(g);
            Graphics2D g2 = (Graphics2D) g;
            if (fill != null) {
                g2.setColor(fill);
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius, radius);
            }
            if (line != null) {
                g2.setColor(line);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius, radius);
            }
        }
    }

    // 위에 주 색상 띠가 있는 카드 (스케치의 남색 머리 부분)
    public static RoundPanel headerCard(String title, JComponent headerRight, JComponent body) {
        int r = Theme.radius();
        RoundPanel card = new RoundPanel(new BorderLayout(), Theme.surface(), Theme.border(), r) {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                Shape old = g2.getClip();
                g2.clip(new RoundRectangle2D.Double(0, 0, getWidth() - 1, getHeight() - 1, r, r));
                g2.setColor(Theme.primary());
                g2.fillRect(0, 0, getWidth(), 34);
                g2.setClip(old);
            }
        };
        JPanel header = clear(new BorderLayout());
        header.setPreferredSize(new Dimension(0, 34));
        header.setBorder(pad(0, 12, 0, 6));
        header.add(label(title, 13, true, Theme.onPrimary()), BorderLayout.WEST);
        if (headerRight != null) header.add(headerRight, BorderLayout.EAST);
        card.add(header, BorderLayout.NORTH);
        body.setOpaque(false);
        card.add(body, BorderLayout.CENTER);
        return card;
    }

    // ===== 납작한 버튼 =====
    public static class FlatButton extends JButton {
        private Color fill, fg;
        private boolean selected;

        public FlatButton(String text, Color fill, Color fg) {
            super(text);
            this.fill = fill;
            this.fg = fg;
            setFont(font(13, false));
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setRolloverEnabled(true);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setBorder(pad(6, 12, 6, 12));
        }
        public void setColors(Color fill, Color fg) { this.fill = fill; this.fg = fg; repaint(); }
        public void setActive(boolean active) { this.selected = active; repaint(); }

        @Override protected void paintComponent(Graphics g) {
            smooth(g);
            Graphics2D g2 = (Graphics2D) g;
            Color c = fill;
            if (selected) c = blend(Theme.text(), fill, 0.18);
            else if (getModel().isPressed()) c = blend(Theme.text(), fill, 0.15);
            else if (getModel().isRollover()) c = blend(Theme.text(), fill, 0.07);
            if (!isEnabled()) c = blend(Theme.bg(), fill, 0.5);
            int r = Math.min(Theme.radius(), getHeight());
            g2.setColor(c);
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, r, r);
            if (Theme.style() == Theme.Style.SF) {
                g2.setColor(selected ? Theme.accent() : Theme.border());
                g2.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
            }
            g2.setFont(getFont());
            g2.setColor(isEnabled() ? fg : Theme.sub());
            FontMetrics fm = g2.getFontMetrics();
            String t = getText();
            int x = getHorizontalAlignment() == SwingConstants.LEFT ? getInsets().left : (getWidth() - fm.stringWidth(t)) / 2;
            g2.drawString(t, x, (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
        }
    }

    public static FlatButton button(String text) {
        return new FlatButton(text, blend(Theme.secondary(), Theme.surface(), 0.55), Theme.text());
    }
    public static FlatButton primaryButton(String text) {
        return new FlatButton(text, Theme.primary(), Theme.onPrimary());
    }
    public static FlatButton dangerButton(String text) {
        return new FlatButton(text, Theme.dangerLight(), Theme.onColor(Theme.dangerLight()));
    }

    // ===== 위쪽 바 아이콘 (돋보기 / 달 / 붓 / 톱니) =====
    public static JButton icon(String kind, String tooltip, Runnable action) {
        JButton b = new JButton() {
            @Override protected void paintComponent(Graphics g) {
                smooth(g);
                Graphics2D g2 = (Graphics2D) g;
                if (getModel().isRollover()) {
                    g2.setColor(alpha(Theme.onPrimary(), 40));
                    g2.fillOval(2, 2, getWidth() - 4, getHeight() - 4);
                }
                g2.setColor(Theme.onPrimary());
                g2.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                int w = getWidth(), h = getHeight(), cx = w / 2, cy = h / 2;
                switch (kind) {
                    case "search":
                        g2.drawOval(cx - 9, cy - 9, 13, 13);
                        g2.drawLine(cx + 2, cy + 2, cx + 8, cy + 8);
                        break;
                    case "moon": {
                        Area moon = new Area(new Ellipse2D.Double(cx - 9, cy - 9, 18, 18));
                        moon.subtract(new Area(new Ellipse2D.Double(cx - 3, cy - 13, 18, 18)));
                        if (Theme.isNight()) {   // 밤이면 해 모양
                            g2.drawOval(cx - 5, cy - 5, 10, 10);
                            for (int i = 0; i < 8; i++) {
                                double a = Math.PI / 4 * i;
                                g2.drawLine(cx + (int) (8 * Math.cos(a)), cy + (int) (8 * Math.sin(a)),
                                        cx + (int) (11 * Math.cos(a)), cy + (int) (11 * Math.sin(a)));
                            }
                        } else g2.fill(moon);
                        break;
                    }
                    case "brush": {
                        Path2D handle = new Path2D.Double();
                        handle.moveTo(cx + 9, cy - 10); handle.lineTo(cx - 1, cy + 1);
                        g2.draw(handle);
                        g2.fill(new Ellipse2D.Double(cx - 9, cy - 1, 10, 10));
                        break;
                    }
                    case "gear": {
                        g2.drawOval(cx - 4, cy - 4, 8, 8);
                        g2.draw(new Arc2D.Double(cx - 8, cy - 8, 16, 16, 0, 360, Arc2D.OPEN));
                        for (int i = 0; i < 8; i++) {
                            double a = Math.PI / 4 * i;
                            g2.drawLine(cx + (int) (8 * Math.cos(a)), cy + (int) (8 * Math.sin(a)),
                                    cx + (int) (11 * Math.cos(a)), cy + (int) (11 * Math.sin(a)));
                        }
                        break;
                    }
                }
            }
        };
        b.setPreferredSize(new Dimension(38, 38));
        b.setContentAreaFilled(false);
        b.setBorderPainted(false);
        b.setFocusPainted(false);
        b.setRolloverEnabled(true);
        b.setToolTipText(tooltip);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.addActionListener(e -> action.run());
        return b;
    }

    // 목록에 색 점 + 글자 (카테고리 표시용)
    public static Icon dot(Color c, int size) {
        return new Icon() {
            public void paintIcon(Component comp, Graphics g, int x, int y) {
                smooth(g);
                g.setColor(c);
                g.fillOval(x, y, size, size);
            }
            public int getIconWidth() { return size; }
            public int getIconHeight() { return size; }
        };
    }

    public static void onClick(JComponent c, Runnable r) {
        c.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        c.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { if (SwingUtilities.isLeftMouseButton(e)) r.run(); }
        });
    }

    public static JScrollPane scroll(JComponent c) {
        JScrollPane sp = new JScrollPane(c);
        sp.setBorder(BorderFactory.createEmptyBorder());
        sp.setOpaque(false);
        sp.getViewport().setOpaque(false);
        sp.getVerticalScrollBar().setUnitIncrement(16);
        return sp;
    }

    // ===== 윈도우 기본 파일 창 (탐색기 스타일) =====
    public static File openFile(Component parent, String title, String... extensions) {
        FileDialog fd = new FileDialog(frameOf(parent), title, FileDialog.LOAD);
        applyFilter(fd, extensions);
        fd.setVisible(true);
        return fd.getFile() == null ? null : new File(fd.getDirectory(), fd.getFile());
    }

    public static File saveFile(Component parent, String title, String defaultName, String extension) {
        FileDialog fd = new FileDialog(frameOf(parent), title, FileDialog.SAVE);
        fd.setFile(defaultName);
        fd.setVisible(true);
        if (fd.getFile() == null) return null;
        String name = fd.getFile();
        if (!name.toLowerCase().endsWith("." + extension)) name += "." + extension;
        return new File(fd.getDirectory(), name);
    }

    private static void applyFilter(FileDialog fd, String... extensions) {
        if (extensions.length == 0) return;
        StringBuilder pattern = new StringBuilder();
        for (String ext : extensions) pattern.append(pattern.length() > 0 ? ";" : "").append("*.").append(ext);
        fd.setFile(pattern.toString());   // 윈도우에서는 이게 파일 형식 필터로 동작
        fd.setFilenameFilter((dir, name) -> {
            for (String ext : extensions) if (name.toLowerCase().endsWith("." + ext)) return true;
            return false;
        });
    }

    private static Frame frameOf(Component c) {
        Window w = c == null ? null : (c instanceof Window ? (Window) c : SwingUtilities.getWindowAncestor(c));
        while (w != null && !(w instanceof Frame)) w = w.getOwner();
        return (Frame) w;
    }

    // 남은 시간을 "2일 3시간" / "15분" 형태로
    public static String remaining(long totalMinutes) {
        if (totalMinutes <= 0) return "곧 시작";
        long d = totalMinutes / (60 * 24), h = (totalMinutes / 60) % 24, m = totalMinutes % 60;
        StringBuilder sb = new StringBuilder();
        if (d > 0) sb.append(d).append("일 ");
        if (h > 0) sb.append(h).append("시간 ");
        if (d == 0) sb.append(m).append("분");
        return sb.toString().trim();
    }
}
