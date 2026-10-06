import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.function.BiConsumer;

// "시간 테트리스": 세로 = 시(0~23), 가로 = 분(0,10,20...50)
// 이미 일정이 있는 칸에는 걸치게 선택할 수 없음
public class TimelinePanel extends JPanel {
    private static final int SLOTS = 24 * 6;
    private static final int LABEL_W = 34, HEAD_H = 22, CELL_W = 42, CELL_H = 24;

    private final ScheduleManager HAZscheduleManager;
    private final Schedule except;
    private final boolean[] occupied = new boolean[SLOTS];
    private final String[] occupiedTitle = new String[SLOTS];
    private final Color[] occupiedColor = new Color[SLOTS];
    private final BiConsumer<LocalTime, LocalTime> onSelect;

    private int selFrom = -1, selTo = -1, dragStart = -1;
    private boolean invalid = false;

    public TimelinePanel(ScheduleManager sm, Schedule except, BiConsumer<LocalTime, LocalTime> onSelect) {
        this.HAZscheduleManager = sm;
        this.except = except;
        this.onSelect = onSelect;
        setOpaque(false);
        setPreferredSize(new Dimension(LABEL_W + CELL_W * 6 + 4, HEAD_H + CELL_H * 24 + 4));

        MouseAdapter mouse = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                int s = slotAt(e.getPoint());
                if (s < 0) return;
                dragStart = s;
                updateDrag(s);
            }
            @Override public void mouseDragged(MouseEvent e) {
                int s = slotAt(e.getPoint());
                if (dragStart >= 0 && s >= 0) updateDrag(s);
            }
            @Override public void mouseReleased(MouseEvent e) {
                if (dragStart < 0) return;
                dragStart = -1;
                if (invalid) {
                    Toolkit.getDefaultToolkit().beep();
                    selFrom = selTo = -1;
                    invalid = false;
                } else if (selFrom >= 0) {
                    onSelect.accept(slotToTime(selFrom), slotToTime(selTo + 1));
                }
                repaint();
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        ToolTipManager.sharedInstance().registerComponent(this);
    }

    public void setDate(LocalDate date) {
        Arrays.fill(occupied, false);
        Arrays.fill(occupiedTitle, null);
        if (date != null) {
            for (Schedule s : HAZscheduleManager.getByDate(date, true)) {
                if (s == except) continue;
                for (int i = 0; i < SLOTS; i++) {
                    if (s.overlaps(date, slotToTime(i), slotToTime(i + 1))) {
                        occupied[i] = true;
                        occupiedTitle[i] = s.getTitle();
                        occupiedColor[i] = HAZscheduleManager.colorOf(s.getCategory());
                    }
                }
            }
        }
        repaint();
    }

    public void setSelection(LocalTime start, LocalTime end) {
        if (start == null || end == null || !end.isAfter(start)) selFrom = selTo = -1;
        else {
            selFrom = minutes(start) / 10;
            selTo = Math.max(selFrom, (int) Math.ceil(minutes(end) / 10.0) - 1);
        }
        invalid = false;   // 직접 입력한 시간이 다른 일정과 겹치면 빨갛게
        if (selFrom >= 0) for (int i = selFrom; i <= selTo; i++) if (occupied[i]) invalid = true;
        repaint();
    }

    public int selectionY() { return HEAD_H + (selFrom < 0 ? 8 : selFrom / 6) * CELL_H; }

    private void updateDrag(int current) {
        selFrom = Math.min(dragStart, current);
        selTo = Math.max(dragStart, current);
        invalid = false;
        for (int i = selFrom; i <= selTo; i++) if (occupied[i]) invalid = true;
        repaint();
    }

    private int slotAt(Point p) {
        if (p.x < LABEL_W || p.y < HEAD_H) return -1;
        int col = (p.x - LABEL_W) / CELL_W, row = (p.y - HEAD_H) / CELL_H;
        if (col > 5 || row > 23) return -1;
        return row * 6 + col;
    }

    private static int minutes(LocalTime t) { return t.getHour() * 60 + t.getMinute(); }
    private static LocalTime slotToTime(int slot) {
        int m = slot * 10;
        return m >= 24 * 60 ? LocalTime.of(23, 59) : LocalTime.of(m / 60, m % 60);
    }

    @Override public String getToolTipText(MouseEvent e) {
        int s = slotAt(e.getPoint());
        if (s < 0) return null;
        return occupied[s] ? slotToTime(s) + " · " + occupiedTitle[s] + " (이미 있는 일정)" : slotToTime(s).toString();
    }

    @Override protected void paintComponent(Graphics g) {
        Ui.smooth(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setFont(Ui.font(11, false));
        g2.setColor(Theme.sub());
        for (int c = 0; c < 6; c++) g2.drawString(String.valueOf(c * 10), LABEL_W + c * CELL_W + 4, 15);
        for (int h = 0; h < 24; h++) {
            String t = String.valueOf(h);
            g2.drawString(t, LABEL_W - 8 - g2.getFontMetrics().stringWidth(t), HEAD_H + h * CELL_H + 16);
        }

        // 빈 칸 격자
        g2.setColor(Theme.border());
        for (int h = 0; h <= 24; h++) g2.drawLine(LABEL_W, HEAD_H + h * CELL_H, LABEL_W + 6 * CELL_W, HEAD_H + h * CELL_H);
        for (int c = 0; c <= 6; c++) g2.drawLine(LABEL_W + c * CELL_W, HEAD_H, LABEL_W + c * CELL_W, HEAD_H + 24 * CELL_H);

        // 이미 있는 일정 / 선택 영역을 줄 단위 둥근 블록으로
        int r = Math.min(Theme.radius(), 12);
        for (int row = 0; row < 24; row++) {
            int c = 0;
            while (c < 6) {
                int i = row * 6 + c;
                boolean sel = selFrom >= 0 && i >= selFrom && i <= selTo;
                if (!occupied[i] && !sel) { c++; continue; }
                int startC = c;
                while (c < 6 && occupied[row * 6 + c] == occupied[i]
                        && (selFrom >= 0 && row * 6 + c >= selFrom && row * 6 + c <= selTo) == sel) c++;
                Color fill = sel ? (invalid ? Theme.danger() : Theme.accent()) : Ui.blend(occupiedColor[i], Theme.surface(), 0.55);
                g2.setColor(sel ? Ui.alpha(fill, 200) : fill);
                g2.fillRoundRect(LABEL_W + startC * CELL_W + 2, HEAD_H + row * CELL_H + 3, (c - startC) * CELL_W - 4, CELL_H - 6, r, r);
                if (!sel) {
                    g2.setColor(Theme.text());
                    Shape old = g2.getClip();
                    g2.clipRect(LABEL_W + startC * CELL_W + 2, HEAD_H + row * CELL_H, (c - startC) * CELL_W - 6, CELL_H);
                    g2.drawString(occupiedTitle[i], LABEL_W + startC * CELL_W + 7, HEAD_H + row * CELL_H + 16);
                    g2.setClip(old);
                }
            }
        }
    }
}
