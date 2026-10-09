import javax.swing.*;
import java.awt.*;

// "위젯" 탭: 켜기/끄기, 위치(맨 위 / 바탕화면), 보여줄 내용(다음 일정 / D-Day)
public class WidgetView extends JPanel {
    public WidgetView(AppSettings settings, Runnable applyWidget) {
        super(new BorderLayout());
        setOpaque(false);

        JCheckBox use = new JCheckBox("위젯 사용", settings.getBool("widgetEnabled"));
        use.setFont(Ui.font(14, true));
        JRadioButton onTop = new JRadioButton("항상 화면 맨 위에 (게임·다른 창 위에도 보임)");
        JRadioButton desktop = new JRadioButton("바탕화면에 두기 (다른 창 뒤에 깔림)");
        ButtonGroup g1 = new ButtonGroup();
        g1.add(onTop);
        g1.add(desktop);
        (settings.getBool("widgetOnTop") ? onTop : desktop).setSelected(true);
        JRadioButton next = new JRadioButton("가장 가까운 일정");
        JRadioButton dday = new JRadioButton("D-Day (D-Day 탭에서 '표시'를 켠 것 중 가장 가까운 것)");
        ButtonGroup g2 = new ButtonGroup();
        g2.add(next);
        g2.add(dday);
        ("dday".equals(settings.get("widgetMode")) ? dday : next).setSelected(true);

        Runnable apply = () -> {
            settings.setBool("widgetEnabled", use.isSelected());
            settings.setBool("widgetOnTop", onTop.isSelected());
            settings.set("widgetMode", dday.isSelected() ? "dday" : "next");
            settings.save();
            applyWidget.run();
        };
        for (AbstractButton b : new AbstractButton[]{use, onTop, desktop, next, dday}) b.addActionListener(e -> apply.run());

        Ui.FlatButton reset = Ui.button("위치 처음으로");
        reset.addActionListener(e -> {
            settings.set("widgetX", null);
            settings.set("widgetY", null);
            settings.save();
            applyWidget.run();
        });

        JPanel box = Ui.clear(null);
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        box.setBorder(Ui.pad(16, 22, 16, 22));
        Component[] items = {
            use, Box.createVerticalStrut(18),
            Ui.label("위치", 12.5f, true, Ui.SUB), Box.createVerticalStrut(4), onTop, desktop, Box.createVerticalStrut(18),
            Ui.label("보여줄 내용", 12.5f, true, Ui.SUB), Box.createVerticalStrut(4), next, dday, Box.createVerticalStrut(18),
            reset, Box.createVerticalStrut(22),
            Ui.label("<html>· 위젯은 마우스로 끌어서 옮길 수 있고, 위치를 기억해요.<br>"
                    + "· 위젯을 더블클릭하면 캘린더가 열리고, 우클릭하면 메뉴가 나와요.<br>"
                    + "· 위젯은 클릭해도 키보드 입력을 가져가지 않아요.</html>", 12.5f, false, Ui.SUB)
        };
        for (Component c : items) {
            if (c instanceof JComponent) ((JComponent) c).setAlignmentX(LEFT_ALIGNMENT);
            box.add(c);
        }
        JPanel wrap = Ui.clear(new BorderLayout());
        wrap.add(box, BorderLayout.NORTH);
        add(Ui.headerCard("위젯", null, Ui.scroll(wrap)), BorderLayout.CENTER);
    }
}
