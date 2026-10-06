import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.*;

// 얇고 둥근 스크롤바 (테마 색)
public class ThinScrollBarUI extends BasicScrollBarUI {
    public static ComponentUI createUI(JComponent c) { return new ThinScrollBarUI(); }

    @Override protected void configureScrollBarColors() { }

    @Override protected JButton createDecreaseButton(int o) { return zero(); }
    @Override protected JButton createIncreaseButton(int o) { return zero(); }

    private static JButton zero() {
        JButton b = new JButton();
        b.setPreferredSize(new Dimension(0, 0));
        b.setMinimumSize(new Dimension(0, 0));
        b.setMaximumSize(new Dimension(0, 0));
        return b;
    }

    @Override protected void paintTrack(Graphics g, JComponent c, Rectangle r) { }

    @Override protected void paintThumb(Graphics g, JComponent c, Rectangle r) {
        if (r.isEmpty() || !scrollbar.isEnabled()) return;
        Ui.smooth(g);
        g.setColor(isThumbRollover() ? Ui.blend(Theme.sub(), Theme.border(), 0.7) : Ui.blend(Theme.sub(), Theme.border(), 0.35));
        boolean vertical = scrollbar.getOrientation() == JScrollBar.VERTICAL;
        int pad = 3;
        if (vertical) g.fillRoundRect(r.x + pad, r.y + 2, r.width - pad * 2, r.height - 4, 8, 8);
        else g.fillRoundRect(r.x + 2, r.y + pad, r.width - 4, r.height - pad * 2, 8, 8);
    }

    @Override public Dimension getPreferredSize(JComponent c) {
        return scrollbar.getOrientation() == JScrollBar.VERTICAL ? new Dimension(11, 0) : new Dimension(0, 11);
    }
}
