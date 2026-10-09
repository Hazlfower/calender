import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

// 화면 오른쪽 아래에 뜨는 알림 카드 + 캐릭터 말풍선
// 게임 중에 키보드 입력을 빼앗지 않도록 포커스를 가져가지 않아요
public class AlarmPopup extends JWindow {
    private static final List<AlarmPopup> OPEN = new ArrayList<>();
    private static final int W = 470, BOX_W = 320, BOX_Y = 96, CHAR_D = 116;

    private final AppSettings settings;
    private final Schedule s;
    private final LocalDateTime at;
    private final int before;
    private final int h;
    private final String speech;
    private final Runnable openMain;
    private final Timer ticker;
    private final boolean seeThrough;

    public static void show(AppSettings settings, Schedule s, LocalDateTime at, int before, Runnable openMain) {
        AlarmPopup p = new AlarmPopup(settings, s, at, before, openMain);
        OPEN.add(p);
        p.place();
        p.setVisible(true);
        Toolkit.getDefaultToolkit().beep();
    }

    private AlarmPopup(AppSettings settings, Schedule s, LocalDateTime at, int before, Runnable openMain) {
        super((Window) null);
        this.settings = settings;
        this.s = s;
        this.at = at;
        this.before = before;
        this.openMain = openMain;
        this.h = s.memo.isBlank() ? 258 : 294;
        long mins = Math.max(0, (Duration.between(LocalDateTime.now(), at).getSeconds() + 59) / 60);
        long shown = (mins > 0 && mins <= before + 1) ? mins : before;
        this.speech = settings.formatAlarmMessage(before == 0 || mins == 0, s.title, shown, s.start);

        setAlwaysOnTop(true);
        setFocusableWindowState(false);
        seeThrough = Ui.transparent(this);
        setSize(W, h);
        setIconImages(Ui.appIcons());

        JPanel c = new JPanel(null) {
            @Override protected void paintComponent(Graphics g) { draw(g); }
        };
        c.setOpaque(false);
        c.setToolTipText("더블클릭: 캘린더 열기");
        c.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { if (e.getClickCount() == 2) openMain.run(); }
        });
        int x = 22, y = h - 52;
        if (!s.link.isBlank()) {
            Ui.FlatButton link = Ui.softButton("링크 열기");
            link.setIcon(new Icons(Icons.Kind.POPOUT, 14));
            link.addActionListener(e -> Ui.browse(this, s.link));
            x = put(c, link, x, y);
        }
        Ui.FlatButton snooze = Ui.button("5분 뒤 다시");
        snooze.addActionListener(e -> snooze());
        x = put(c, snooze, x, y);
        Ui.FlatButton ok = Ui.primaryButton("확인");
        ok.addActionListener(e -> close());
        put(c, ok, x, y);
        setContentPane(c);

        ticker = new Timer(15_000, e -> repaint());
        ticker.start();
    }

    private static int put(JPanel c, JComponent b, int x, int y) {
        Dimension d = b.getPreferredSize();
        b.setBounds(x, y, d.width, 34);
        c.add(b);
        return x + d.width + 6;
    }

    private void place() {
        Rectangle scr = Ui.screen();
        int i = OPEN.indexOf(this);
        setLocation(scr.x + scr.width - W - 14 - i * 26, Math.max(scr.y, scr.y + scr.height - h - 10 - i * 44));
    }

    private void close() {
        ticker.stop();
        OPEN.remove(this);
        dispose();
    }

    private void snooze() {
        close();
        Timer t = new Timer(5 * 60_000, e -> show(settings, s, at, before, openMain));
        t.setRepeats(false);
        t.start();
    }

    private void draw(Graphics g) {
        Graphics2D g2 = Ui.aa(g);
        if (!seeThrough) {   // 투명 창을 못 쓰는 컴퓨터: 바탕을 배경색으로
            g2.setColor(Theme.bg());
            g2.fillRect(0, 0, getWidth(), getHeight());
        }
        int boxH = h - BOX_Y - 6, r = Math.max(6, Theme.radius() + 4);
        for (int i = 6; i >= 1; i--) {
            g2.setColor(new Color(0, 0, 0, 9));
            g2.fill(new RoundRectangle2D.Float(4 - i / 2f, BOX_Y + i, BOX_W + i, boxH, r, r));
        }
        RoundRectangle2D box = new RoundRectangle2D.Float(4, BOX_Y, BOX_W, boxH, r, r);
        g2.setColor(Theme.surface());
        g2.fill(box);
        g2.setColor(Theme.border());
        g2.draw(box);
        Shape old = g2.getClip();
        g2.clip(box);
        g2.setColor(Theme.primary());
        g2.fillRect(4, BOX_Y, BOX_W, 6);
        g2.setClip(old);

        int x = 22, y = BOX_Y + 32;
        g2.setColor(Ui.blend(Theme.highlight(), Theme.surface(), 0.18));
        g2.fill(new Ellipse2D.Float(x, y - 16, 22, 22));
        new Icons(Icons.Kind.BELL, 13, Theme.highlight()).paintIcon(null, g2, x + 4, y - 12);
        g2.setFont(Ui.font(12f, false));
        g2.setColor(Theme.sub());
        g2.drawString(TimeText.relDay(at.toLocalDate()) + " " + TimeText.ampm(s.start) + " 시작"
                + (s.category.isBlank() ? "" : "  ·  " + s.category), x + 30, y);
        y += 30;
        g2.setFont(Ui.semi(18f));
        g2.setColor(Theme.text());
        g2.drawString(Ui.ellipsize(s.title, g2.getFontMetrics(), BOX_W - 70), x, y);
        y += 24;
        long secs = Duration.between(LocalDateTime.now(), at).getSeconds();
        String remain = secs > 0 ? "남은 시간  " + TimeText.duration((secs + 59) / 60)
                : secs > -60 ? "지금 시작해요!" : "시작한 지 " + TimeText.duration(-secs / 60) + " 지났어요";
        g2.setFont(Ui.font(14f, true));
        g2.setColor(Theme.highlight());
        g2.drawString(remain, x, y);
        if (!s.memo.isBlank()) {
            g2.setFont(Ui.font(12.5f, false));
            g2.setColor(Theme.sub());
            List<String> lines = Ui.wrap(s.memo, g2.getFontMetrics(), BOX_W - 80);
            for (int i = 0; i < Math.min(2, lines.size()); i++) {
                y += 19;
                String l = lines.get(i);
                if (i == 1 && lines.size() > 2) l = Ui.ellipsize(l + "…", g2.getFontMetrics(), BOX_W - 80);
                g2.drawString(l, x, y);
            }
        }

        int cx = BOX_W - 30, cy = BOX_Y + 8;
        g2.setColor(new Color(0, 0, 0, 30));
        g2.fill(new Ellipse2D.Float(cx + 3, cy + 5, CHAR_D, CHAR_D));
        Ui.drawCharacter(g2, cx, cy, CHAR_D, settings.get("characterImage"));
        String name = settings.get("characterName");
        if (!name.isBlank()) {
            g2.setFont(Ui.font(11.5f, true));
            int nw = g2.getFontMetrics().stringWidth(name) + 16;
            int nx = cx + CHAR_D / 2 - nw / 2, ny = cy + CHAR_D - 10;
            g2.setColor(Theme.primary());
            g2.fill(new RoundRectangle2D.Float(nx, ny, nw, 20, 20, 20));
            g2.setColor(Theme.onPrimary());
            g2.drawString(name, nx + 8, ny + 14);
        }

        g2.setFont(Ui.semi(13.5f));
        FontMetrics fm = g2.getFontMetrics();
        int bx = 130, bw = W - bx - 6;
        List<String> lines = Ui.wrap(speech, fm, bw - 28);
        if (lines.size() > 3) lines = lines.subList(0, 3);
        int bh = 22 + lines.size() * 19;
        int by = Math.max(4, BOX_Y - 20 - bh);
        Ui.bubble(g2, bx, by, bw, bh, cx + CHAR_D / 2 - 6, cy + 6, Theme.surface(), Theme.border(), Math.max(8, r));
        g2.setColor(Theme.text());
        for (int i = 0; i < lines.size(); i++) g2.drawString(lines.get(i), bx + 14, by + 25 + i * 19);
        g2.dispose();
    }
}
