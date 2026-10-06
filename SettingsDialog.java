import javax.swing.*;
import java.awt.*;
import java.time.LocalDate;

// 톱니바퀴: 캐릭터 대사, 디데이 등 일반 설정
public class SettingsDialog extends JDialog {
    public SettingsDialog(MainFrame frame, AppSettings settings, AlarmManager alarmManager) {
        super(frame, "설정", true);

        JTextField characterField = new JTextField(settings.get("characterName"), 18);
        JTextField messageField = new JTextField(settings.get("alarmMessage"), 18);
        JTextField ddayTitle = new JTextField(settings.get("ddayTitle"), 18);
        DatePicker ddayDate = new DatePicker(settings.getDate("ddayDate"));

        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(Theme.surface());
        form.setBorder(Ui.pad(16, 18, 10, 18));
        GridBagConstraints c = new GridBagConstraints();
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(5, 0, 5, 10);
        int y = 0;
        y = section(form, c, y, "캐릭터");
        y = row(form, c, y, "이름", characterField);
        y = row(form, c, y, "알람 대사", messageField);
        y = row(form, c, y, "", Ui.label("{min} = 남은 분, {title} = 일정 이름", 11, false, Theme.sub()));
        y = section(form, c, y, "디데이");
        y = row(form, c, y, "이름", ddayTitle);
        y = row(form, c, y, "날짜", ddayDate);
        y = section(form, c, y, "저장 위치");
        row(form, c, y, "", Ui.label(AppSettings.dataDir().toString(), 11, false, Theme.sub()));

        Ui.FlatButton preview = Ui.button("알람 미리보기");
        Ui.FlatButton cancel = Ui.button("취소");
        Ui.FlatButton save = Ui.primaryButton("저장");
        preview.addActionListener(e -> {
            settings.set("characterName", characterField.getText().trim());
            settings.set("alarmMessage", messageField.getText().trim());
            alarmManager.showTest();
        });
        cancel.addActionListener(e -> { settings.load(); dispose(); });
        save.addActionListener(e -> {
            LocalDate d = ddayDate.getDate();
            settings.set("characterName", characterField.getText().trim());
            settings.set("alarmMessage", messageField.getText().trim());
            settings.set("ddayTitle", ddayTitle.getText().trim());
            settings.set("ddayDate", d == null ? null : d.toString());
            settings.save();
            dispose();
            frame.rebuild();
        });

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 10));
        buttons.setBackground(Theme.surface());
        buttons.add(preview);
        buttons.add(cancel);
        buttons.add(save);

        JPanel root = new JPanel(new BorderLayout());
        root.add(form, BorderLayout.CENTER);
        root.add(buttons, BorderLayout.SOUTH);
        setContentPane(root);
        pack();
        setResizable(false);
        setLocationRelativeTo(frame);
    }

    private static int section(JPanel p, GridBagConstraints c, int y, String title) {
        c.gridy = y; c.gridx = 0; c.gridwidth = 2;
        c.insets = new Insets(y == 0 ? 0 : 14, 0, 2, 10);
        p.add(Ui.label(title, 14, true, Theme.accent()), c);
        c.gridwidth = 1;
        c.insets = new Insets(5, 0, 5, 10);
        return y + 1;
    }

    private static int row(JPanel p, GridBagConstraints c, int y, String label, JComponent comp) {
        c.gridy = y; c.gridx = 0; c.weightx = 0;
        p.add(Ui.label(label, 13, false, Theme.text()), c);
        c.gridx = 1; c.weightx = 1;
        p.add(comp, c);
        return y + 1;
    }
}
