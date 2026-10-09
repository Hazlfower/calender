import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.AbstractBorder;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Arc2D;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;

// 공통 디자인 도구: 글꼴, 색 섞기, 카드, 버튼, 라벨, 그리기 도우미, 파일 창
public class Ui {
    // ================================================================== 글꼴 (Pretendard, 없으면 맑은 고딕)

    private static Font regular, semi, bold;

    static {
        regular = loadFont("Pretendard-Regular.otf");
        semi = loadFont("Pretendard-SemiBold.otf");
        bold = loadFont("Pretendard-Bold.otf");
        if (regular == null) {
            String fam = pick("맑은 고딕", "Malgun Gothic", "Apple SD Gothic Neo", "Noto Sans CJK KR", "NanumGothic", Font.SANS_SERIF);
            regular = new Font(fam, Font.PLAIN, 13);
            bold = new Font(fam, Font.BOLD, 13);
            semi = bold;
        }
        if (bold == null) bold = regular.deriveFont(Font.BOLD);
        if (semi == null) semi = bold;
    }

    // jar 안(fonts/...) → 실행 폴더의 fonts 폴더 순서로 찾음
    private static Font loadFont(String name) {
        try (InputStream in = Ui.class.getResourceAsStream("/fonts/" + name)) {
            if (in != null) return register(Font.createFont(Font.TRUETYPE_FONT, in));
        } catch (Exception ignored) { }
        try {
            Path p = Paths.get("fonts", name);
            if (Files.isRegularFile(p)) return register(Font.createFont(Font.TRUETYPE_FONT, p.toFile()));
        } catch (Exception ignored) { }
        return null;
    }

    private static Font register(Font f) {
        GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(f);
        return f;
    }

    private static String pick(String... names) {
        java.util.Set<String> av = new java.util.HashSet<>(java.util.Arrays.asList(
                GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
        for (String n : names) if (av.contains(n)) return n;
        return names[names.length - 1];
    }

    public static Font font(float size, boolean isBold) { return (isBold ? bold : regular).deriveFont(Font.PLAIN, size); }

    // 제목용 세미볼드
    public static Font semi(float size) { return semi.deriveFont(Font.PLAIN, size); }

    // ================================================================== 색

    // a 를 t 만큼, b 를 (1-t) 만큼 섞은 색
    public static Color blend(Color a, Color b, double t) {
        t = Math.max(0, Math.min(1, t));
        return new Color(
                (int) Math.round(a.getRed() * t + b.getRed() * (1 - t)),
                (int) Math.round(a.getGreen() * t + b.getGreen() * (1 - t)),
                (int) Math.round(a.getBlue() * t + b.getBlue() * (1 - t)));
    }

    public static Color alpha(Color c, int a) { return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.max(0, Math.min(255, a))); }

    // 일정 막대 바탕 (밤에는 어둡게 섞음)
    public static Color soft(Color c) { return Theme.isNight() ? blend(c, Theme.surface(), 0.45) : blend(c, Color.WHITE, 0.42); }

    // ================================================================== 그리기

    public static void smooth(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
    }

    public static Graphics2D aa(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        smooth(g2);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        return g2;
    }

    public static void centerText(Graphics2D g, String s, double cx, double baseline) {
        g.drawString(s, (float) (cx - g.getFontMetrics().stringWidth(s) / 2.0), (float) baseline);
    }

    // 넘치면 … 으로 줄이기
    public static String ellipsize(String s, FontMetrics fm, int w) {
        if (s == null) return "";
        if (fm.stringWidth(s) <= w) return s;
        String e = "…";
        int n = s.length();
        while (n > 0 && fm.stringWidth(s.substring(0, n) + e) > w) n--;
        return n <= 0 ? "" : s.substring(0, n) + e;
    }

    // 폭에 맞춰 줄 나누기
    public static List<String> wrap(String text, FontMetrics fm, int width) {
        List<String> lines = new ArrayList<>();
        for (String para : text.split("\n", -1)) {
            StringBuilder cur = new StringBuilder();
            for (int i = 0; i < para.length(); i++) {
                char c = para.charAt(i);
                if (cur.length() > 0 && fm.stringWidth(cur.toString() + c) > width) {
                    int sp = cur.lastIndexOf(" ");
                    if (c != ' ' && sp > 0) {
                        lines.add(cur.substring(0, sp));
                        cur = new StringBuilder(cur.substring(sp + 1));
                    } else {
                        lines.add(cur.toString());
                        cur.setLength(0);
                    }
                    if (c == ' ') continue;
                }
                cur.append(c);
            }
            lines.add(cur.toString());
        }
        return lines;
    }

    // 꼬리 달린 말풍선
    public static void bubble(Graphics2D g, int x, int y, int w, int h, int tailX, int tailY, Color fill, Color stroke, int r) {
        Area a = new Area(new RoundRectangle2D.Float(x, y, w, h, r, r));
        int bx = Math.max(x + 22, Math.min(x + w - 40, tailX - 24));
        Path2D tail = new Path2D.Float();
        tail.moveTo(bx, y + h - 2);
        tail.lineTo(bx + 20, y + h - 2);
        tail.lineTo(tailX, tailY);
        tail.closePath();
        a.add(new Area(tail));
        g.setColor(fill);
        g.fill(a);
        g.setColor(stroke);
        g.setStroke(new BasicStroke(1.2f));
        g.draw(a);
    }

    // 알람 캐릭터: 고른 이미지를 동그랗게, 없으면 기본 스마일
    public static void drawCharacter(Graphics2D g, double x, double y, double d, String imagePath) {
        Ellipse2D circle = new Ellipse2D.Double(x, y, d, d);
        Image img = image(imagePath);
        if (img != null) {
            drawCover(g, img, circle, 50);
            g.setColor(Color.WHITE);
            g.setStroke(new BasicStroke((float) Math.max(2, d / 30)));
            g.draw(circle);
            return;
        }
        g.setColor(new Color(0xFFE15C));
        g.fill(circle);
        g.setColor(new Color(0x6E5B1C));
        g.setStroke(new BasicStroke((float) Math.max(1.2, d / 70)));
        g.draw(circle);
        double e = d * 0.1;
        g.fill(new Ellipse2D.Double(x + d * 0.33 - e / 2, y + d * 0.36, e, e * 1.25));
        g.fill(new Ellipse2D.Double(x + d * 0.67 - e / 2, y + d * 0.36, e, e * 1.25));
        g.setColor(new Color(255, 130, 140, 90));
        g.fill(new Ellipse2D.Double(x + d * 0.16, y + d * 0.56, d * 0.15, d * 0.08));
        g.fill(new Ellipse2D.Double(x + d * 0.69, y + d * 0.56, d * 0.15, d * 0.08));
        g.setColor(new Color(0x6E5B1C));
        g.setStroke(new BasicStroke((float) Math.max(1.5, d / 45), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Arc2D.Double(x + d * 0.27, y + d * 0.3, d * 0.46, d * 0.44, 205, 130, Arc2D.OPEN));
    }

    // 이미지를 도형 안에 꽉 차게 (focusY: 0~100 세로 위치)
    public static void drawCover(Graphics2D g, Image img, Shape clip, int focusY) {
        Rectangle2D b = clip.getBounds2D();
        int iw = img.getWidth(null), ih = img.getHeight(null);
        if (iw <= 0 || ih <= 0) return;
        double s = Math.max(b.getWidth() / iw, b.getHeight() / ih);
        double dw = iw * s, dh = ih * s;
        double dx = b.getX() + (b.getWidth() - dw) / 2;
        double dy = b.getY() + (b.getHeight() - dh) * focusY / 100.0;
        Shape old = g.getClip();
        g.clip(clip);
        g.drawImage(img, (int) dx, (int) dy, (int) Math.ceil(dw), (int) Math.ceil(dh), null);
        g.setClip(old);
    }

    // ================================================================== 이미지 (큰 사진은 줄여서 기억)

    private static final Map<String, Image> CACHE = new HashMap<>();

    public static Image image(String path) {
        if (path == null || path.isBlank()) return null;
        File f = new File(path);
        if (!f.isFile()) return null;
        String key = path + "@" + f.lastModified();
        Image img = CACHE.get(key);
        if (img == null) {
            try {
                BufferedImage raw = ImageIO.read(f);
                if (raw != null) {
                    double scale = Math.min(1.0, 600.0 / Math.max(raw.getWidth(), raw.getHeight()));
                    int w = Math.max(1, (int) (raw.getWidth() * scale)), h = Math.max(1, (int) (raw.getHeight() * scale));
                    BufferedImage small = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
                    Graphics2D g = small.createGraphics();
                    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                    g.drawImage(raw, 0, 0, w, h, null);
                    g.dispose();
                    img = small;
                }
            } catch (Exception ignored) { }
            if (img != null) CACHE.put(key, img);
        }
        return img;
    }

    // 앱 아이콘: 둥근 사각형 달력 + 오늘 날짜
    public static BufferedImage appIcon(int s) { return appIcon(s, String.valueOf(java.time.LocalDate.now().getDayOfMonth())); }

    // label 이 null 이면 숫자 없이 (exe 아이콘용)
    public static BufferedImage appIcon(int s, String label) {
        BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = aa(img.getGraphics());
        float u = s / 16f;
        Color top = new Color(0x6E76AA), bottom = new Color(0x4F5687);
        g.setPaint(new GradientPaint(0, 0, top, 0, s, bottom));
        g.fill(new RoundRectangle2D.Float(u * 0.5f, u * 0.5f, u * 15, u * 15, u * 4.5f, u * 4.5f));
        g.setColor(new Color(0xFAF6EE));
        g.fill(new RoundRectangle2D.Float(u * 3f, u * 4.2f, u * 10, u * 9, u * 2, u * 2));
        g.setColor(new Color(0xE8A0A0));
        g.fill(new Rectangle2D.Float(u * 3f, u * 4.2f + u * 2f, u * 10, u * 0.9f));
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(Math.max(1f, u * 1.1f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new java.awt.geom.Line2D.Float(u * 6, u * 2.8f, u * 6, u * 5.2f));
        g.draw(new java.awt.geom.Line2D.Float(u * 10, u * 2.8f, u * 10, u * 5.2f));
        if (s >= 24 && label != null) {
            g.setColor(new Color(0x3A3540));
            g.setFont(semi(u * 5.2f));
            centerText(g, label, u * 8, u * 12.4f);
        } else if (s >= 24) {   // 숫자 대신 점 세 개
            g.setColor(new Color(0x8A8090));
            for (int i = 0; i < 3; i++) g.fill(new java.awt.geom.Ellipse2D.Float(u * (5.2f + i * 2.3f), u * 9.6f, u * 1.3f, u * 1.3f));
        }
        g.dispose();
        return img;
    }

    public static List<Image> appIcons() {
        List<Image> l = new ArrayList<>();
        for (int s : new int[]{16, 24, 32, 48, 64, 128}) l.add(appIcon(s));
        return l;
    }

    // ================================================================== 테마 색을 따라가는 라벨 / 패널

    public static final int TEXT = 0, SUB = 1, PRIMARY = 2, ACCENT = 3, DANGER = 4, ON_PRIMARY = 5, HIGHLIGHT = 6;

    public static Color roleColor(int role) {
        switch (role) {
            case SUB: return Theme.sub();
            case PRIMARY: return Theme.primary();
            case ACCENT: return Theme.accent();
            case DANGER: return Theme.danger();
            case ON_PRIMARY: return Theme.onPrimary();
            case HIGHLIGHT: return Theme.highlight();
            default: return Theme.text();
        }
    }

    public static class Label extends JLabel {
        public int role;

        public Label(String s, int role, Font f) {
            super(s);
            this.role = role;
            setFont(f);
            setForeground(roleColor(role));
        }

        @Override protected void paintComponent(Graphics g) {
            Color c = roleColor(role);
            if (!c.equals(getForeground())) setForeground(c);
            Ui.smooth(g);
            super.paintComponent(g);
        }
    }

    public static Label label(String text, float size, boolean isBold, int role) {
        return new Label(text, role, font(size, isBold));
    }

    // 예전 방식 (색 직접 지정)
    public static JLabel label(String text, float size, boolean isBold, Color color) {
        JLabel l = new JLabel(text);
        l.setFont(font(size, isBold));
        l.setForeground(color);
        return l;
    }

    public static JPanel clear(LayoutManager layout) {
        JPanel p = new JPanel(layout);
        p.setOpaque(false);
        return p;
    }

    public static Border pad(int t, int l, int b, int r) { return BorderFactory.createEmptyBorder(t, l, b, r); }

    // 배경색으로 칠하는 판
    public static class Back extends JPanel {
        public Back(LayoutManager lm) { super(lm); setOpaque(true); }
        @Override protected void paintComponent(Graphics g) {
            g.setColor(Theme.bg());
            g.fillRect(0, 0, getWidth(), getHeight());
        }
    }

    // 둥근 카드. fill/line 이 null 이면 테마 색(표면/테두리)을 그때그때 씀
    public static class RoundPanel extends JPanel {
        private Color fill, line;
        private final int radius;
        public boolean themed;

        public RoundPanel(LayoutManager layout, Color fill, Color line, int radius) {
            super(layout);
            this.fill = fill;
            this.line = line;
            this.radius = radius;
            setOpaque(false);
        }

        public static RoundPanel card(LayoutManager layout) {
            RoundPanel p = new RoundPanel(layout, null, null, -1);
            p.themed = true;
            return p;
        }

        public void setFill(Color fill) { this.fill = fill; repaint(); }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = aa(g);
            int r = radius < 0 ? Theme.radius() : radius;
            Color f = themed ? Theme.surface() : fill, l = themed ? Theme.border() : line;
            RoundRectangle2D rr = new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1, getHeight() - 1, r, r);
            if (f != null) { g2.setColor(f); g2.fill(rr); }
            if (l != null) { g2.setColor(l); g2.draw(rr); }
            g2.dispose();
        }
    }

    // 위에 주 색상 띠가 있는 카드 (스케치의 남색 머리 부분)
    public static final int HEADER_H = 36;

    public static RoundPanel headerCard(String title, JComponent headerRight, JComponent body) {
        RoundPanel card = new RoundPanel(new BorderLayout(), null, null, -1) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = aa(g);
                int r = Theme.radius();
                RoundRectangle2D rr = new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1, getHeight() - 1, r, r);
                g2.setColor(Theme.surface());
                g2.fill(rr);
                Shape old = g2.getClip();
                g2.clip(rr);
                g2.setColor(Theme.primary());
                g2.fillRect(0, 0, getWidth(), HEADER_H);
                g2.setClip(old);
                g2.setColor(Theme.border());
                g2.draw(rr);
                g2.dispose();
            }
        };
        JPanel header = clear(new BorderLayout());
        header.setPreferredSize(new Dimension(0, HEADER_H));
        header.setBorder(pad(0, 14, 0, 6));
        Label t = new Label(title, ON_PRIMARY, semi(13.5f));
        header.add(t, BorderLayout.WEST);
        if (headerRight != null) {
            headerRight.setOpaque(false);
            header.add(headerRight, BorderLayout.EAST);
        }
        card.putClientProperty("titleLabel", t);
        card.add(header, BorderLayout.NORTH);
        body.setOpaque(false);
        card.add(body, BorderLayout.CENTER);
        return card;
    }

    public static Label cardTitle(JComponent card) { return (Label) card.getClientProperty("titleLabel"); }

    // ================================================================== 버튼

    public static final int B_PLAIN = 0, B_PRIMARY = 1, B_SOFT = 2, B_DANGER = 3, B_ON_PRIMARY = 4, B_GHOST = 5;

    // 납작한 둥근 버튼 (색은 그릴 때마다 테마에서 가져옴)
    public static class FlatButton extends JButton {
        public int kind;
        private Color fixedFill, fixedFg;
        private boolean active;

        public FlatButton(String text, int kind) {
            super(text);
            this.kind = kind;
            setFont(font(13, kind == B_PRIMARY));
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setRolloverEnabled(true);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setBorder(pad(7, 14, 7, 14));
        }

        // 예전 방식 (색 직접 지정)
        public FlatButton(String text, Color fill, Color fg) {
            this(text, B_PLAIN);
            this.fixedFill = fill;
            this.fixedFg = fg;
        }

        public void setColors(Color fill, Color fg) { fixedFill = fill; fixedFg = fg; repaint(); }
        public void setActive(boolean a) { active = a; repaint(); }
        public boolean isActive() { return active; }

        Color fillColor() {
            if (fixedFill != null) return fixedFill;
            switch (kind) {
                case B_PRIMARY: return Theme.highlight();
                case B_SOFT: return blend(Theme.secondary(), Theme.surface(), Theme.isNight() ? 0.6 : 0.55);
                case B_DANGER: return Theme.dangerLight();
                case B_ON_PRIMARY: return blend(Theme.onPrimary(), Theme.primary(), 0.16);
                case B_GHOST: return null;
                default: return Theme.isNight() ? blend(Color.WHITE, Theme.surface(), 0.07) : blend(Theme.bg(), Theme.surface(), 0.8);
            }
        }

        Color textColor() {
            if (fixedFg != null) return fixedFg;
            switch (kind) {
                case B_PRIMARY: return Theme.onColor(Theme.highlight());
                case B_DANGER: return Theme.isNight() ? Theme.onColor(Theme.dangerLight()) : Theme.danger();
                case B_ON_PRIMARY: return Theme.onPrimary();
                default: return Theme.text();
            }
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = aa(g);
            Color c = fillColor();
            Color base = c == null ? Theme.surface() : c;
            Color over = Theme.lum(base) > 140 ? Color.BLACK : Color.WHITE;
            if (active) c = blend(Theme.highlight(), base, 0.35);
            else if (getModel().isPressed()) c = blend(over, base, 0.16);
            else if (getModel().isRollover()) c = blend(over, base, 0.08);
            if (!isEnabled() && c != null) c = blend(Theme.surface(), c, 0.5);
            int r = Math.min(Theme.radius() <= 2 ? 2 : 10, getHeight());
            if (c != null) {
                g2.setColor(c);
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), r, r));
            }
            if (Theme.style() == Theme.Style.SF && kind != B_GHOST) {
                g2.setColor(active ? Theme.accent() : alpha(Theme.border(), 200));
                g2.draw(new Rectangle2D.Float(0.5f, 0.5f, getWidth() - 1, getHeight() - 1));
            }
            Color fg = isEnabled() ? textColor() : Theme.sub();
            if (active) fg = Theme.onColor(c);
            Icon icon = getIcon();
            g2.setFont(getFont());
            FontMetrics fm = g2.getFontMetrics();
            String t = getText() == null ? "" : getText();
            int gap = icon != null && !t.isEmpty() ? 6 : 0;
            int iw = icon == null ? 0 : icon.getIconWidth();
            int total = iw + gap + fm.stringWidth(t);
            int x = getHorizontalAlignment() == SwingConstants.LEFT ? getInsets().left : (getWidth() - total) / 2;
            if (icon != null) {
                setForeground(fg);
                icon.paintIcon(this, g2, x, (getHeight() - icon.getIconHeight()) / 2);
            }
            g2.setColor(fg);
            g2.drawString(t, x + iw + gap, (getHeight() + fm.getAscent() - fm.getDescent()) / 2f);
            g2.dispose();
        }
    }

    public static FlatButton button(String text) { return new FlatButton(text, B_PLAIN); }
    public static FlatButton primaryButton(String text) { return new FlatButton(text, B_PRIMARY); }
    public static FlatButton softButton(String text) { return new FlatButton(text, B_SOFT); }
    public static FlatButton dangerButton(String text) { return new FlatButton(text, B_DANGER); }

    // 주 색상 띠 위에 놓는 작은 버튼
    public static FlatButton headerButton(String text) {
        FlatButton b = new FlatButton(text, B_ON_PRIMARY);
        b.setFont(font(12, true));
        b.setBorder(pad(3, 10, 3, 10));
        return b;
    }

    // 아이콘만 있는 버튼 (위쪽 바 등). onPrimary = 주 색상 띠 위에 놓이는지
    public static JButton iconButton(Icons.Kind kind, String tip, boolean onPrimary, Runnable r) {
        JButton b = new JButton() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = aa(g);
                Color fg = onPrimary ? Theme.onPrimary() : Theme.text();
                if (getModel().isRollover() || getModel().isPressed()) {
                    g2.setColor(alpha(fg, getModel().isPressed() ? 50 : 28));
                    int r = Theme.style() == Theme.Style.SF ? 2 : 10;
                    g2.fill(new RoundRectangle2D.Float(1, 1, getWidth() - 2, getHeight() - 2, r, r));
                }
                Icons ic = new Icons(kindOf(this), 18, fg);
                ic.paintIcon(this, g2, (getWidth() - 18) / 2, (getHeight() - 18) / 2);
                g2.dispose();
            }
        };
        b.putClientProperty("iconKind", kind);
        b.setPreferredSize(new Dimension(36, 36));
        b.setContentAreaFilled(false);
        b.setBorderPainted(false);
        b.setFocusPainted(false);
        b.setOpaque(false);
        b.setRolloverEnabled(true);
        b.setToolTipText(tip);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        if (r != null) b.addActionListener(e -> r.run());
        return b;
    }

    static Icons.Kind kindOf(JComponent c) { return (Icons.Kind) c.getClientProperty("iconKind"); }

    public static void setIconKind(JButton b, Icons.Kind k) {
        b.putClientProperty("iconKind", k);
        b.repaint();
    }

    // 알약 모양 탭 선택기 (일/주/월 등)
    public static class Segmented extends JComponent {
        private final String[] items;
        private int sel, hover = -1;

        public Segmented(String[] items, int sel, IntConsumer onChange) {
            this.items = items;
            this.sel = sel;
            setFont(font(12.5f, true));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            MouseAdapter ma = new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) {
                    int i = indexAt(e.getX());
                    if (i >= 0 && i != Segmented.this.sel) {
                        Segmented.this.sel = i;
                        repaint();
                        onChange.accept(i);
                    }
                }
                @Override public void mouseMoved(MouseEvent e) { hover = indexAt(e.getX()); repaint(); }
                @Override public void mouseExited(MouseEvent e) { hover = -1; repaint(); }
            };
            addMouseListener(ma);
            addMouseMotionListener(ma);
        }

        public void setSelected(int i) { sel = i; repaint(); }

        private int indexAt(int x) {
            int w = (getWidth() - 6) / items.length;
            int i = (x - 3) / Math.max(1, w);
            return i >= 0 && i < items.length ? i : -1;
        }

        @Override public Dimension getPreferredSize() {
            FontMetrics fm = getFontMetrics(getFont());
            int max = 0;
            for (String s : items) max = Math.max(max, fm.stringWidth(s));
            return new Dimension(items.length * (max + 24) + 6, 32);
        }

        @Override public Dimension getMaximumSize() { return getPreferredSize(); }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = aa(g);
            int h = getHeight(), w = getWidth();
            boolean sf = Theme.style() == Theme.Style.SF;
            int r = sf ? 2 : 12, ir = sf ? 1 : 9;
            g2.setColor(Theme.isNight() ? blend(Color.WHITE, Theme.surface(), 0.07) : blend(Theme.bg(), Theme.surface(), 0.9));
            g2.fill(new RoundRectangle2D.Float(0, 0, w, h, r, r));
            float cw = (w - 6f) / items.length;
            g2.setFont(getFont());
            FontMetrics fm = g2.getFontMetrics();
            for (int i = 0; i < items.length; i++) {
                float x = 3 + i * cw;
                if (i == sel) {
                    g2.setColor(Theme.highlight());
                    g2.fill(new RoundRectangle2D.Float(x, 3, cw, h - 6, ir, ir));
                } else if (i == hover) {
                    g2.setColor(alpha(Theme.highlight(), 30));
                    g2.fill(new RoundRectangle2D.Float(x, 3, cw, h - 6, ir, ir));
                }
                g2.setColor(i == sel ? Theme.onColor(Theme.highlight()) : Theme.sub());
                centerText(g2, items[i], x + cw / 2, (h + fm.getAscent() - fm.getDescent()) / 2.0);
            }
            g2.dispose();
        }
    }

    // 색 고르기 동그라미
    public static class ColorDot extends JComponent {
        public Color color;
        public boolean selected;
        private final boolean plus;

        public ColorDot(Color color, boolean plus, Runnable onClick) {
            this.color = color;
            this.plus = plus;
            setPreferredSize(new Dimension(28, 28));
            setMaximumSize(new Dimension(28, 28));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) { onClick.run(); }
            });
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = aa(g);
            Ellipse2D c = new Ellipse2D.Float(4, 4, getWidth() - 8, getHeight() - 8);
            if (plus) {
                g2.setColor(alpha(Theme.sub(), 170));
                g2.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f, new float[]{3, 3}, 0));
                g2.draw(c);
                new Icons(Icons.Kind.PLUS, 12, Theme.text()).paintIcon(this, g2, getWidth() / 2 - 6, getHeight() / 2 - 6);
            } else {
                g2.setColor(color);
                g2.fill(c);
                if (selected) {
                    g2.setColor(Theme.text());
                    g2.setStroke(new BasicStroke(2f));
                    g2.draw(new Ellipse2D.Float(1.5f, 1.5f, getWidth() - 3, getHeight() - 3));
                }
            }
            g2.dispose();
        }
    }

    // ================================================================== 작은 도구

    public static Icon dot(Color c, int size) {
        return new Icon() {
            public void paintIcon(Component comp, Graphics g, int x, int y) {
                Graphics2D g2 = aa(g);
                g2.setColor(c);
                g2.fill(new Ellipse2D.Float(x, y, size, size));
                g2.dispose();
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

    public static JMenuItem item(String text, Runnable r) {
        JMenuItem m = new JMenuItem(text);
        m.addActionListener(e -> r.run());
        return m;
    }

    public static JScrollPane scroll(JComponent c) {
        JScrollPane sp = new JScrollPane(c);
        sp.setBorder(BorderFactory.createEmptyBorder());
        sp.setViewportBorder(null);
        sp.setOpaque(false);
        sp.getViewport().setOpaque(false);
        sp.getVerticalScrollBar().setUnitIncrement(16);
        return sp;
    }

    // 입력칸처럼 둥근 테두리를 두른 스크롤 (메모 입력 등)
    public static JScrollPane fieldScroll(JTextArea area) {
        JScrollPane sp = new JScrollPane(area);
        sp.setBorder(new HazLook.FieldBorder(area));
        sp.setOpaque(false);
        sp.getViewport().setOpaque(false);
        area.setOpaque(false);
        area.setBorder(pad(6, 10, 6, 10));
        area.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override public void focusGained(java.awt.event.FocusEvent e) { sp.repaint(); }
            @Override public void focusLost(java.awt.event.FocusEvent e) { sp.repaint(); }
        });
        return sp;
    }

    // 스크롤 영역 가로폭에 맞춰 늘어나는 판 (세로로만 스크롤)
    public static class WidthTracking extends JPanel implements Scrollable {
        public WidthTracking(LayoutManager lm) { super(lm); setOpaque(false); }
        public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 16; }
        public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return 120; }
        public boolean getScrollableTracksViewportWidth() { return true; }
        public boolean getScrollableTracksViewportHeight() { return false; }
    }

    // 가로로 나열. Integer = 간격, String = 라벨, Component = 그대로
    public static JPanel row(Object... items) {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.X_AXIS));
        p.setOpaque(false);
        for (Object o : items) {
            if (o instanceof Integer) p.add(Box.createHorizontalStrut((Integer) o));
            else if (o instanceof String) p.add(label((String) o, 13, false, TEXT));
            else if (o instanceof JComponent) {
                JComponent c = (JComponent) o;
                if (c instanceof JTextField || c instanceof JComboBox || c instanceof JSpinner)
                    c.setMaximumSize(new Dimension(c.getPreferredSize().width, c.getPreferredSize().height));
                c.setAlignmentY(0.5f);
                p.add(c);
            }
        }
        p.add(Box.createHorizontalGlue());
        p.setAlignmentX(0f);
        return p;
    }

    public static void escCloses(JDialog d, Runnable r) {
        d.getRootPane().registerKeyboardAction(e -> r.run(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    public static void warn(Component parent, String msg) {
        JOptionPane.showMessageDialog(parent, msg, "확인해 주세요", JOptionPane.WARNING_MESSAGE);
    }

    public static void info(Component parent, String title, String msg) {
        JOptionPane.showMessageDialog(parent, msg, title, JOptionPane.INFORMATION_MESSAGE);
    }

    public static boolean confirm(Component parent, String title, String msg) {
        return JOptionPane.showConfirmDialog(parent, msg, title, JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION;
    }

    // 사용자가 쓴 글을 HTML 라벨에 넣을 때 (< > & 때문에 깨지지 않게)
    public static String html(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>");
    }

    public static String normalizeLink(String s) {
        s = s == null ? "" : s.trim();
        if (s.isEmpty()) return s;
        if (!s.matches("(?i)^[a-z][a-z0-9+.-]*:.*")) s = "https://" + s;
        return s;
    }

    public static void browse(Component parent, String link) {
        try {
            if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE))
                throw new UnsupportedOperationException();
            Desktop.getDesktop().browse(new URI(normalizeLink(link)));
        } catch (Exception e) {
            warn(parent, "링크를 열 수 없어요. 주소를 확인해 주세요.\n" + link);
        }
    }

    public static void openFolder(Component parent, Path dir) {
        try {
            Files.createDirectories(dir);
            Desktop.getDesktop().open(dir.toFile());
        } catch (Exception e) {
            info(parent, "저장 폴더", dir.toString());
        }
    }

    // 창이 화면 밖에 있거나 화면보다 크면 안으로
    public static void ensureOnScreen(Window w) {
        Rectangle b = w.getBounds();
        for (GraphicsDevice d : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
            Rectangle s = d.getDefaultConfiguration().getBounds();
            if (s.contains(b.x + 40, b.y + 20)) return;
        }
        w.setLocationRelativeTo(null);
    }

    public static Rectangle screen() { return GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds(); }

    public static void fitOnScreen(Window w) {
        Rectangle scr = screen();
        int ww = Math.min(w.getWidth(), scr.width), wh = Math.min(w.getHeight(), scr.height);
        if (ww != w.getWidth() || wh != w.getHeight()) w.setSize(ww, wh);
        w.setLocation(Math.max(scr.x, Math.min(w.getX(), scr.x + scr.width - ww)),
                Math.max(scr.y, Math.min(w.getY(), scr.y + scr.height - wh)));
    }

    // 창 바탕을 투명하게 (지원하지 않는 컴퓨터면 false → 그 창은 바탕을 직접 칠함)
    public static boolean transparent(Window w) {
        try {
            GraphicsDevice gd = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
            if (gd.isWindowTranslucencySupported(GraphicsDevice.WindowTranslucency.PERPIXEL_TRANSLUCENT)) {
                w.setBackground(new Color(0, 0, 0, 0));
                return true;
            }
        } catch (Exception ignored) { }
        return false;
    }

    // ================================================================== 윈도우 기본 파일 창 (탐색기 모양)

    public static File openFile(Component parent, String title, String... extensions) {
        FileDialog fd = new FileDialog(frameOf(parent), title, FileDialog.LOAD);
        if (extensions.length > 0) {
            StringBuilder pattern = new StringBuilder();
            for (String ext : extensions) pattern.append(pattern.length() > 0 ? ";" : "").append("*.").append(ext);
            fd.setFile(pattern.toString());   // 윈도우에서는 파일 형식 필터로 동작
            fd.setFilenameFilter((dir, name) -> {
                for (String ext : extensions) if (name.toLowerCase().endsWith("." + ext)) return true;
                return false;
            });
        }
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

    private static Frame frameOf(Component c) {
        Window w = c == null ? null : (c instanceof Window ? (Window) c : SwingUtilities.getWindowAncestor(c));
        while (w != null && !(w instanceof Frame)) w = w.getOwner();
        return (Frame) w;
    }

    // 남은 시간 "2일 3시간" / "15분"
    public static String remaining(long totalMinutes) {
        if (totalMinutes <= 0) return "곧 시작";
        return TimeText.duration(totalMinutes);
    }

    // 둥근 테두리 (테마 색)
    public static Border roundLine(int r) {
        return new AbstractBorder() {
            @Override public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
                Graphics2D g2 = aa(g);
                g2.setColor(Theme.border());
                g2.draw(new RoundRectangle2D.Float(x + 0.5f, y + 0.5f, w - 1, h - 1, r, r));
                g2.dispose();
            }
            @Override public Insets getBorderInsets(Component c) { return new Insets(1, 1, 1, 1); }
        };
    }
}
