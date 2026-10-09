import javax.swing.*;
import java.awt.*;
import java.awt.geom.*;

// 코드로 그리는 선 아이콘 (16 단위 격자라 어떤 크기로도 선명함)
public class Icons implements Icon {
    public enum Kind {
        MENU, LEFT, RIGHT, DOWN, SEARCH, MOON, SUN, BRUSH, GEAR, PLUS, CLOSE, EDIT, TRASH, BELL, REPEAT,
        PIN, POPOUT, CALENDAR, CLOCK, CHECK, IMPORT, WIDGET, TAG, FLAG, NOTE, UP, EXPORT, USER, LIST
    }

    private final Kind kind;
    private final int size;
    private final Color fixed;

    public Icons(Kind kind, int size) { this(kind, size, null); }

    public Icons(Kind kind, int size, Color fixed) {
        this.kind = kind;
        this.size = size;
        this.fixed = fixed;
    }

    @Override public int getIconWidth() { return size; }

    @Override public int getIconHeight() { return size; }

    @Override public void paintIcon(Component c, Graphics g, int x, int y) {
        Graphics2D g2 = Ui.aa(g);
        Color col = fixed != null ? fixed : c != null ? c.getForeground() : Theme.text();
        if (c != null && !c.isEnabled() && fixed == null) col = Theme.sub();
        g2.setColor(col);
        g2.translate(x, y);
        float s = size / 16f;
        g2.scale(s, s);
        g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        draw(g2);
        g2.dispose();
    }

    private void line(Graphics2D g, double x1, double y1, double x2, double y2) {
        g.draw(new Line2D.Double(x1, y1, x2, y2));
    }

    private void draw(Graphics2D g) {
        switch (kind) {
            case MENU -> { line(g, 2.5, 4, 13.5, 4); line(g, 2.5, 8, 13.5, 8); line(g, 2.5, 12, 13.5, 12); }
            case LEFT -> { line(g, 10, 3, 5, 8); line(g, 5, 8, 10, 13); }
            case RIGHT -> { line(g, 6, 3, 11, 8); line(g, 11, 8, 6, 13); }
            case DOWN -> { line(g, 4, 6, 8, 10); line(g, 8, 10, 12, 6); }
            case SEARCH -> { g.draw(new Ellipse2D.Double(2.5, 2.5, 8.5, 8.5)); line(g, 10, 10, 13.5, 13.5); }
            case MOON -> {
                Area a = new Area(new Ellipse2D.Double(2, 2, 12, 12));
                a.subtract(new Area(new Ellipse2D.Double(6, -0.5, 11, 11)));
                g.fill(a);
            }
            case SUN -> {
                g.fill(new Ellipse2D.Double(5, 5, 6, 6));
                for (int i = 0; i < 8; i++) {
                    double r = Math.toRadians(i * 45);
                    line(g, 8 + Math.cos(r) * 5, 8 + Math.sin(r) * 5, 8 + Math.cos(r) * 6.8, 8 + Math.sin(r) * 6.8);
                }
            }
            case BRUSH -> {
                line(g, 13.5, 2.5, 7.5, 8.5);
                Path2D p = new Path2D.Double();
                p.moveTo(7.8, 8.2);
                p.curveTo(5.5, 7.5, 3.5, 9, 3.5, 11);
                p.curveTo(3.5, 12.5, 3, 13.5, 2, 14);
                p.curveTo(5.5, 14.5, 8.5, 12.5, 7.8, 8.2);
                g.fill(p);
            }
            case GEAR -> {
                Area a = new Area(new Ellipse2D.Double(3, 3, 10, 10));
                for (int i = 0; i < 8; i++) {
                    AffineTransform at = AffineTransform.getRotateInstance(Math.toRadians(i * 45), 8, 8);
                    a.add(new Area(at.createTransformedShape(new RoundRectangle2D.Double(6.6, 0.8, 2.8, 3.4, 1, 1))));
                }
                a.subtract(new Area(new Ellipse2D.Double(5.6, 5.6, 4.8, 4.8)));
                g.fill(a);
            }
            case PLUS -> { line(g, 8, 3, 8, 13); line(g, 3, 8, 13, 8); }
            case CLOSE -> { line(g, 4, 4, 12, 12); line(g, 12, 4, 4, 12); }
            case EDIT -> {
                Path2D p = new Path2D.Double();
                p.moveTo(3, 13);
                p.lineTo(3.6, 10.2);
                p.lineTo(10.8, 3);
                p.lineTo(13, 5.2);
                p.lineTo(5.8, 12.4);
                p.closePath();
                g.draw(p);
                line(g, 9.3, 4.5, 11.5, 6.7);
            }
            case TRASH -> {
                line(g, 2.5, 4.5, 13.5, 4.5);
                line(g, 6, 4.5, 6.5, 2.5);
                line(g, 6.5, 2.5, 9.5, 2.5);
                line(g, 9.5, 2.5, 10, 4.5);
                g.draw(new RoundRectangle2D.Double(4, 4.5, 8, 9, 2, 2));
                line(g, 6.7, 7, 6.7, 11);
                line(g, 9.3, 7, 9.3, 11);
            }
            case BELL -> {
                Path2D p = new Path2D.Double();
                p.moveTo(3, 11.5);
                p.lineTo(4, 10);
                p.lineTo(4, 7);
                p.curveTo(4, 4.5, 6, 3, 8, 3);
                p.curveTo(10, 3, 12, 4.5, 12, 7);
                p.lineTo(12, 10);
                p.lineTo(13, 11.5);
                p.closePath();
                g.draw(p);
                line(g, 6.8, 13.5, 9.2, 13.5);
            }
            case REPEAT -> {
                g.draw(new Arc2D.Double(3, 3, 10, 10, 20, 250, Arc2D.OPEN));
                Path2D p = new Path2D.Double();
                p.moveTo(13.2, 3.5);
                p.lineTo(12.8, 6.8);
                p.lineTo(9.6, 6.2);
                g.draw(p);
            }
            case PIN -> {
                Path2D p = new Path2D.Double();
                p.moveTo(6, 2.5);
                p.lineTo(10, 2.5);
                p.lineTo(9.5, 7);
                p.lineTo(12, 9.5);
                p.lineTo(4, 9.5);
                p.lineTo(6.5, 7);
                p.closePath();
                g.draw(p);
                line(g, 8, 9.5, 8, 14);
            }
            case POPOUT -> {
                Path2D p = new Path2D.Double();
                p.moveTo(7, 3);
                p.lineTo(3, 3);
                p.lineTo(3, 13);
                p.lineTo(13, 13);
                p.lineTo(13, 9);
                g.draw(p);
                line(g, 8, 8, 13.5, 2.5);
                line(g, 9.5, 2.5, 13.5, 2.5);
                line(g, 13.5, 2.5, 13.5, 6.5);
            }
            case CALENDAR -> {
                g.draw(new RoundRectangle2D.Double(2.5, 3.5, 11, 10, 2.5, 2.5));
                line(g, 2.5, 6.8, 13.5, 6.8);
                line(g, 5.5, 2, 5.5, 4.5);
                line(g, 10.5, 2, 10.5, 4.5);
            }
            case CLOCK -> { g.draw(new Ellipse2D.Double(2.5, 2.5, 11, 11)); line(g, 8, 5, 8, 8); line(g, 8, 8, 10.5, 9.5); }
            case CHECK -> { line(g, 3.5, 8.5, 6.5, 11.5); line(g, 6.5, 11.5, 12.5, 4.5); }
            case IMPORT -> {
                line(g, 8, 2.5, 8, 10);
                line(g, 5, 7, 8, 10);
                line(g, 8, 10, 11, 7);
                Path2D p = new Path2D.Double();
                p.moveTo(2.5, 10);
                p.lineTo(2.5, 13.5);
                p.lineTo(13.5, 13.5);
                p.lineTo(13.5, 10);
                g.draw(p);
            }
            case WIDGET -> {
                g.draw(new RoundRectangle2D.Double(2.5, 2.5, 5, 5, 1.5, 1.5));
                g.draw(new RoundRectangle2D.Double(8.5, 2.5, 5, 5, 1.5, 1.5));
                g.draw(new RoundRectangle2D.Double(2.5, 8.5, 5, 5, 1.5, 1.5));
                g.draw(new RoundRectangle2D.Double(8.5, 8.5, 5, 5, 1.5, 1.5));
            }
            case TAG -> {
                Path2D p = new Path2D.Double();
                p.moveTo(2.5, 2.5);
                p.lineTo(8, 2.5);
                p.lineTo(13.5, 8);
                p.lineTo(8, 13.5);
                p.lineTo(2.5, 8);
                p.closePath();
                g.draw(p);
                g.fill(new Ellipse2D.Double(4.8, 4.8, 2, 2));
            }
            case FLAG -> {
                line(g, 3.5, 2.5, 3.5, 14);
                Path2D p = new Path2D.Double();
                p.moveTo(3.5, 3);
                p.lineTo(12.5, 3);
                p.lineTo(10.5, 6);
                p.lineTo(12.5, 9);
                p.lineTo(3.5, 9);
                g.draw(p);
            }
            case UP -> { line(g, 4, 10, 8, 6); line(g, 8, 6, 12, 10); }
            case EXPORT -> {
                line(g, 8, 10, 8, 2.5);
                line(g, 5, 5.5, 8, 2.5);
                line(g, 8, 2.5, 11, 5.5);
                Path2D p = new Path2D.Double();
                p.moveTo(2.5, 10);
                p.lineTo(2.5, 13.5);
                p.lineTo(13.5, 13.5);
                p.lineTo(13.5, 10);
                g.draw(p);
            }
            case USER -> {
                g.draw(new Ellipse2D.Double(5, 2.5, 6, 6));
                g.draw(new Arc2D.Double(2.5, 9.5, 11, 9, 0, 180, Arc2D.OPEN));
            }
            case LIST -> {
                line(g, 6, 4, 13.5, 4); line(g, 6, 8, 13.5, 8); line(g, 6, 12, 13.5, 12);
                g.fill(new Ellipse2D.Double(2, 3, 2, 2)); g.fill(new Ellipse2D.Double(2, 7, 2, 2)); g.fill(new Ellipse2D.Double(2, 11, 2, 2));
            }
            case NOTE -> {
                g.draw(new RoundRectangle2D.Double(3, 2.5, 10, 11, 2, 2));
                line(g, 5.5, 6, 10.5, 6);
                line(g, 5.5, 8.5, 10.5, 8.5);
                line(g, 5.5, 11, 8.5, 11);
            }
        }
    }
}
