import javax.swing.*;
import java.awt.*;

// "위젯" 탭: 켜기/끄기, 위치, 보여줄 내용
public class WidgetView extends JPanel {
    public WidgetView(AppSettings settings, Runnable applyWidget) {
        super(new BorderLayout());
        setOpaque(false);

        JCheckBox use = new JCheckBox("위젯 사용", settings.getBool("widgetEnabled"));
        JRadioButton onTop = new JRadioButton("항상 화면 맨 위에");
        JRadioButton normal = new JRadioButton("일반 창처럼 (다른 창에 가려짐)");
        ButtonGroup g1 = new ButtonGroup(); g1.add(onTop); g1.add(normal);
        (settings.getBool("widgetOnTop") ? onTop : normal).setSelected(true);
        JRadioButton next = new JRadioButton("가장 가까운 일정");
        JRadioButton dday = new JRadioButton("디데이 (톱니바퀴 설정에서 지정)");
        ButtonGroup g2 = new ButtonGroup(); g2.add(next); g2.add(dday);
        ("dday".equals(settings.get("widgetMode")) ? dday : next).setSelected(true);

        Ui.FlatButton apply = Ui.primaryButton("적용");
        apply.addActionListener(e -> {
            settings.set("widgetEnabled", String.valueOf(use.isSelected()));
            settings.set("widgetOnTop", String.valueOf(onTop.isSelected()));
            settings.set("widgetMode", dday.isSelected() ? "dday" : "next");
            settings.save();
            applyWidget.run();
        });

        JPanel box = Ui.clear(null);
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        box.setBorder(Ui.pad(16, 20, 16, 20));
        Component[] items = {
            use, Box.createVerticalStrut(16),
            Ui.label("위치", 14, true, Theme.accent()), onTop, normal, Box.createVerticalStrut(16),
            Ui.label("보여줄 내용", 14, true, Theme.accent()), next, dday, Box.createVerticalStrut(20),
            apply, Box.createVerticalStrut(20),
            Ui.label("<html>· 위젯은 마우스로 끌어서 옮길 수 있고, 위치는 기억돼요.<br>"
                    + "· 위젯을 우클릭하면 닫을 수 있어요.</html>", 12, false, Theme.sub())
        };
        for (Component c : items) {
            if (c instanceof JComponent) ((JComponent) c).setAlignmentX(LEFT_ALIGNMENT);
            if (c instanceof JToggleButton) ((JToggleButton) c).setOpaque(false);
            box.add(c);
        }
        JPanel wrap = Ui.clear(new BorderLayout());
        wrap.add(box, BorderLayout.NORTH);
        add(Ui.headerCard("위젯", null, wrap), BorderLayout.CENTER);
    }
}
