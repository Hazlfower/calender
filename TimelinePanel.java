import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;

// "시간 테트리스": 세로 = 시(0~23), 가로 = 분(0,10,20...50)
// 이미 있는 일정(과 앞뒤 여유 시간) 칸은 피해서, 빈 칸만 드래그로 고를 수 있음
public class TimelinePanel extends JComponent {
    static final int SLOTS = 144, COLS = 6, LABEL_W = 36, CELL_W = 42, CELL_H = 25, HEAD = 22;

    private final boolean[] busy = new boolean[SLOTS], pad = new boolean[SLOTS];
    private final String[] busyTitle = new String[SLOTS];
    private final Color[] busyColor = new Color[SLOTS];
    private int selA = -1, selB = -1, anchor = -1;
    private boolean typedOverlap;
    private String disabledText;
    private final BiConsumer<Integer, Integer> onSelect;

    public TimelinePanel(BiConsumer<Integer, Integer> onSelect) {
        this.onSelect = onSelect;
        setOpaque(false);
        setPreferredSize(new Dimension(LABEL_W + COLS * CELL_W + 8, HEAD + 24 * CELL_H + 6));
        setToolTipText("");
        setAutoscrolls(true);
        setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
        MouseAdapter ma = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                if (disabledText != null || !SwingUtilities.isLeftMouseButton(e)) return;
                int i = slotAt(e.getPoint(), false);
                if (i < 0) return;
                if (blocked(i)) { Toolkit.getDefaultToolkit().beep(); return; }
                anchor = i;
                selA = selB = i;
                typedOverlap = false;
                fire();
                repaint();
            }

            @Override public void mouseDragged(MouseEvent e) {
                if (anchor < 0) return;
                int j = slotAt(e.getPoint(), true);
                // 막힌 칸을 만나면 거기서 멈춤 (테트리스처럼 빈 곳에만 끼워 넣기)
                if (j >= anchor) {
                    int b = anchor;
                    while (b + 1 <= j && !blocked(b + 1)) b++;
                    selA = anchor;
                    selB = b;
                } else {
                    int a = anchor;
                    while (a - 1 >= j && !blocked(a - 1)) a--;
                    selA = a;
                    selB = anchor;
                }
                scrollRectToVisible(new Rectangle(e.getX(), e.getY() - CELL_H, 1, CELL_H * 2));
                fire();
                repaint();
            }

            @Override public void mouseReleased(MouseEvent e) { anchor = -1; }
        };
        addMouseListener(ma);
        addMouseMotionListener(ma);
    }

    // null 이면 쓸 수 있음, 글이 있으면 그 안내를 띄우고 막음
    public void setDisabledText(String text) {
        disabledText = text;
        setCursor(Cursor.getPredefinedCursor(text == null ? Cursor.CROSSHAIR_CURSOR : Cursor.DEFAULT_CURSOR));
        repaint();
    }

    private int slotAt(Point p, boolean clamp) {
        int c = Math.floorDiv(p.x - LABEL_W, CELL_W), r = Math.floorDiv(p.y - HEAD, CELL_H);
        if (!clamp && (c < 0 || c >= COLS || r < 0 || r >= 24)) return -1;
        c = Math.max(0, Math.min(COLS - 1, c));
        r = Math.max(0, Math.min(23, r));
        return r * COLS + c;
    }

    private boolean blocked(int i) { return busy[i] || pad[i]; }

    // 그날 다른 일정들로 막힌 칸 계산. buffer = 앞뒤로 비워 둘 여유(분)
    public void setBusy(List<Schedule> others, LocalDate day, int buffer, ScheduleManager sm) {
        Arrays.fill(busy, false);
        Arrays.fill(pad, false);
        Arrays.fill(busyTitle, null);
        for (Schedule s : others) {
            if (s.allDay()) continue;
            int[] r = s.rangeOn(day);
            if (r == null) continue;
            int a = r[0] / 10, b = Math.min(SLOTS, (r[1] + 9) / 10);
            for (int i = a; i < b; i++) {
                busy[i] = true;
                busyTitle[i] = s.title;
                busyColor[i] = sm.colorOf(s);
            }
            if (buffer > 0) {
                for (int i = b; i < Math.min(SLOTS, (r[1] + buffer + 9) / 10); i++) pad[i] = true;
                for (int i = Math.max(0, (r[0] - buffer) / 10); i < a; i++) pad[i] = true;
            }
        }
        for (int i = 0; i < SLOTS; i++) if (busy[i]) pad[i] = false;
        checkTypedOverlap();
        repaint();
    }

    // 입력칸에 쓴 시간을 그림에 반영 (onSelect 는 부르지 않음)
    public void showSelection(int start, int end) {
        if (start < 0 || end <= start || start >= 1440) selA = selB = -1;
        else {
            selA = start / 10;
            selB = Math.min(SLOTS, (end + 9) / 10) - 1;
        }
        checkTypedOverlap();
        repaint();
    }

    private void checkTypedOverlap() {
        typedOverlap = false;
        if (selA < 0) return;
        for (int i = selA; i <= selB; i++) if (busy[i]) typedOverlap = true;
    }

    public boolean overlapsBusy() { return typedOverlap; }

    public int focusY() { return HEAD + ((selA < 0 ? 8 * COLS : selA) / COLS) * CELL_H; }

    private void fire() {
        if (onSelect != null) onSelect.accept(selA < 0 ? -1 : selA * 10, selA < 0 ? -1 : (selB + 1) * 10);
    }

    @Override public String getToolTipText(MouseEvent e) {
        int i = slotAt(e.getPoint(), false);
        if (i < 0 || disabledText != null) return null;
        String time = TimeText.fmt(i * 10);
        if (busy[i]) return time + "  「" + busyTitle[i] + "」";
        if (pad[i]) return time + "  일정 사이 여유 시간";
        return time + "  (드래그해서 고르기)";
    }

    // 이미 있는 일정 칸: 그 일정 색을 진하게
    private Color busyFill(int i) {
        Color c = busyColor[i] == null ? Theme.sub() : busyColor[i];
        return Theme.isNight() ? Ui.blend(c, Theme.surface(), 0.7) : Ui.blend(c, Theme.text(), 0.82);
    }

    @Override protected void paintComponent(Graphics g) {
        Graphics2D g2 = Ui.aa(g);
        Color free = Theme.isNight() ? Ui.blend(Color.WHITE, Theme.surface(), 0.06) : Ui.blend(Theme.bg(), Theme.surface(), 0.55);
        boolean off = disabledText != null;
        int arc = Theme.style() == Theme.Style.SF ? 2 : 7;

        g2.setFont(Ui.font(10.5f, false));
        g2.setColor(Theme.sub());
        for (int c = 0; c < COLS; c++) Ui.centerText(g2, c * 10 + "", LABEL_W + c * CELL_W + CELL_W / 2.0, HEAD - 7);

        for (int r = 0; r < 24; r++) {
            int y = HEAD + r * CELL_H;
            g2.setFont(Ui.font(11f, true));
            g2.setColor(Theme.sub());
            String h = String.valueOf(r);
            g2.drawString(h, LABEL_W - 10 - g2.getFontMetrics().stringWidth(h), y + 17);
            for (int c = 0; c < COLS; c++) {
                int i = r * COLS + c, x = LABEL_W + c * CELL_W;
                RoundRectangle2D cell = new RoundRectangle2D.Float(x + 2, y + 2, CELL_W - 4, CELL_H - 4, arc, arc);
                boolean sel = !off && selA >= 0 && i >= selA && i <= selB;
                if (sel) g2.setColor(busy[i] ? Theme.danger() : Theme.highlight());
                else if (busy[i]) g2.setColor(Ui.alpha(busyFill(i), off ? 90 : 255));
                else if (pad[i]) g2.setColor(Ui.alpha(Theme.danger(), off ? 30 : 55));
                else g2.setColor(free);
                g2.fill(cell);
            }
            g2.setFont(Ui.font(10.5f, true));
            for (int c = 0; c < COLS; c++) {
                int i = r * COLS + c;
                if (!busy[i] || (c > 0 && busy[i - 1] && busyTitle[i].equals(busyTitle[i - 1]))) continue;
                int run = 1;
                while (c + run < COLS && busy[i + run] && busyTitle[i + run].equals(busyTitle[i])) run++;
                g2.setColor(Theme.onColor(busyFill(i)));
                g2.drawString(Ui.ellipsize(busyTitle[i], g2.getFontMetrics(), run * CELL_W - 12), LABEL_W + c * CELL_W + 7, y + 17);
            }
        }
        if (off) {
            Rectangle vis = getVisibleRect();
            g2.setColor(Ui.alpha(Theme.surface(), 205));
            g2.fill(vis);
            g2.setFont(Ui.semi(13.5f));
            g2.setColor(Theme.sub());
            Ui.centerText(g2, disabledText, vis.getCenterX(), vis.getCenterY());
        }
        g2.dispose();
    }
}
