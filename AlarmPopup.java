import javax.swing.*;
import java.awt.*;
import java.awt.geom.Path2D;
import java.net.URI;

// 알람 팝업: 말풍선(캐릭터 대사) + 정보 상자 + 캐릭터
public class AlarmPopup extends JWindow {
    private static final int W = 400, H = 220;

    public AlarmPopup(AppSettings settings, Schedule schedule, int minutesLeft) {
        setSize(W, H);
        setAlwaysOnTop(true);
        try { setBackground(new Color(0, 0, 0, 0)); } catch (Exception ignored) { }

        String speech = settings.formatAlarmMessage(minutesLeft, schedule.getTitle());
        String speaker = settings.get("characterName");
        Color box = Ui.blend(Theme.primary(), Theme.surface(), 0.25);
        int r = Math.max(Theme.radius(), 4);

        JPanel canvas = new JPanel(null) {
            @Override protected void paintComponent(Graphics g) {
                Ui.smooth(g);
                Graphics2D g2 = (Graphics2D) g;
                g2.setColor(box);
                g2.fillRoundRect(0, 90, 290, 125, r, r);
                g2.setColor(Theme.border());
                g2.drawRoundRect(0, 90, 290, 125, r, r);

                Path2D tail = new Path2D.Double();
                tail.moveTo(200, 62); tail.lineTo(250, 85); tail.lineTo(225, 62); tail.closePath();
                g2.setColor(Theme.surface());
                g2.fillRoundRect(0, 0, 270, 64, r, r);
                g2.fill(tail);
                g2.setColor(Theme.border());
                g2.drawRoundRect(0, 0, 270, 64, r, r);

                int cx = 255, cy = 75, d = 140;   // 캐릭터 (스마일)
                g2.setColor(new Color(255, 225, 60));
                g2.fillOval(cx, cy, d, d);
                g2.setColor(new Color(90, 80, 40));
                g2.setStroke(new BasicStroke(2f));
                g2.drawOval(cx, cy, d, d);
                g2.fillOval(cx + 42, cy + 45, 12, 16);
                g2.fillOval(cx + 86, cy + 45, 12, 16);
                g2.drawArc(cx + 35, cy + 55, 70, 50, 200, 140);
            }
        };
        canvas.setOpaque(false);

        JLabel speechLabel = new JLabel("<html><b>" + speaker + "</b><br>" + speech + "</html>");
        speechLabel.setFont(Ui.font(14, false));
        speechLabel.setForeground(Theme.text());
        speechLabel.setBounds(14, 6, 245, 52);
        canvas.add(speechLabel);

        String info = "<html><b style='font-size:13px'>" + schedule.getTitle() + "</b><br>"
                + schedule.getStart() + " 시작 · " + (minutesLeft <= 0 ? "지금!" : minutesLeft + "분 남음")
                + (schedule.getMemo() != null ? "<br>" + schedule.getMemo() : "") + "</html>";
        JLabel infoLabel = new JLabel(info);
        infoLabel.setFont(Ui.font(13, false));
        infoLabel.setForeground(Theme.onColor(box));
        infoLabel.setVerticalAlignment(SwingConstants.TOP);
        infoLabel.setBounds(16, 100, 240, 70);
        canvas.add(infoLabel);

        int x = 16;
        if (schedule.getLink() != null) {
            Ui.FlatButton link = Ui.button("링크 열기");
            link.setBounds(x, 178, 96, 28);
            link.addActionListener(e -> openLink(schedule.getLink()));
            canvas.add(link);
            x += 104;
        }
        Ui.FlatButton ok = Ui.primaryButton("확인");
        ok.setBounds(x, 178, 70, 28);
        ok.addActionListener(e -> dispose());
        canvas.add(ok);
        setContentPane(canvas);
    }

    public void showPopup() {
        Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        setLocation(screen.x + screen.width - W - 20, screen.y + screen.height - H - 20);
        Toolkit.getDefaultToolkit().beep();
        setVisible(true);
    }

    private void openLink(String link) {
        try {
            String url = link.matches("^[a-zA-Z]+://.*") ? link : "https://" + link;
            Desktop.getDesktop().browse(new URI(url));
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "링크를 열 수 없습니다: " + link);
        }
    }
}
