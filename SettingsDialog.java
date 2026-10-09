import javax.swing.*;
import java.awt.*;
import java.io.File;

// 톱니바퀴: 프로필, 캐릭터(알람), 달력 설정, 가져오기/내보내기
public class SettingsDialog extends JDialog {
    private final AppSettings settings;
    private String charImage, profileImage;

    public SettingsDialog(MainFrame frame, AppSettings settings, AlarmManager alarmManager) {
        super(frame, "설정", true);
        this.settings = settings;
        setIconImages(Ui.appIcons());
        charImage = settings.get("characterImage");
        profileImage = settings.get("profileImage");

        JTextField profileName = new JTextField(settings.get("profileName"), 18);
        Ui.Label profileImgLabel = Ui.label(fileName(profileImage, "없음"), 12, false, Ui.SUB);
        Ui.FlatButton pickProfile = Ui.button("사진 고르기");
        Ui.FlatButton clearProfile = Ui.button("없애기");
        pickProfile.addActionListener(e -> {
            File f = Ui.openFile(this, "프로필 사진 고르기", "png", "jpg", "jpeg", "gif", "bmp");
            if (f != null) { profileImage = f.getAbsolutePath(); profileImgLabel.setText(f.getName()); }
        });
        clearProfile.addActionListener(e -> { profileImage = ""; profileImgLabel.setText("없음"); });

        JTextField charName = new JTextField(settings.get("characterName"), 18);
        Ui.Label charImgLabel = Ui.label(fileName(charImage, "기본 스마일"), 12, false, Ui.SUB);
        Ui.FlatButton pickChar = Ui.button("이미지 고르기");
        Ui.FlatButton clearChar = Ui.button("기본으로");
        pickChar.addActionListener(e -> {
            File f = Ui.openFile(this, "캐릭터 이미지 고르기", "png", "jpg", "jpeg", "gif", "bmp");
            if (f != null) { charImage = f.getAbsolutePath(); charImgLabel.setText(f.getName()); }
        });
        clearChar.addActionListener(e -> { charImage = ""; charImgLabel.setText("기본 스마일"); });
        JTextField msgBefore = new JTextField(settings.get("alarmMessage"), 26);
        JTextField msgOnTime = new JTextField(settings.get("alarmMessageOnTime"), 26);

        JComboBox<String> weekStart = new JComboBox<>(new String[]{"일요일", "월요일"});
        weekStart.setSelectedIndex("MONDAY".equals(settings.get("weekStart")) ? 1 : 0);
        JCheckBox toTray = new JCheckBox("창을 닫아도 트레이(작업 표시줄 오른쪽)에서 알람을 계속 챙기기", settings.getBool("closeToTray"));
        boolean startupWas = Startup.isOn();
        JCheckBox startup = new JCheckBox("윈도우를 켤 때 자동으로 시작 (창 없이 트레이로)", startupWas);
        startup.setEnabled(Startup.supported());

        Form f = new Form();
        f.section("프로필");
        f.row("이름", profileName);
        f.row("사진", Ui.row(pickProfile, 6, clearProfile, 10, profileImgLabel));
        f.section("알람 캐릭터");
        f.row("이름", charName);
        f.row("이미지", Ui.row(pickChar, 6, clearChar, 10, charImgLabel));
        f.row("N분 전 대사", msgBefore);
        f.row("정각 대사", msgOnTime);
        f.row("", Ui.label("{일정} = 일정 이름, {분} = 남은 분, {시각} = 시작 시각, {캐릭터} = 캐릭터 이름", 11.5f, false, Ui.SUB));
        Ui.FlatButton preview = Ui.button("알람 미리보기");
        preview.setIcon(new Icons(Icons.Kind.BELL, 14));
        preview.addActionListener(e -> {
            String[] keep = {settings.get("characterName"), settings.get("characterImage"), settings.get("alarmMessage"), settings.get("alarmMessageOnTime")};
            settings.set("characterName", charName.getText().trim());
            settings.set("characterImage", charImage);
            settings.set("alarmMessage", msgBefore.getText().trim());
            settings.set("alarmMessageOnTime", msgOnTime.getText().trim());
            alarmManager.showTest();
            settings.set("characterName", keep[0]);
            settings.set("characterImage", keep[1]);
            settings.set("alarmMessage", keep[2]);
            settings.set("alarmMessageOnTime", keep[3]);
        });
        f.row("", Ui.row(preview));
        f.section("달력");
        f.row("한 주 시작", Ui.row(weekStart));
        f.row("", toTray);
        f.row("", startup);
        f.section("가져오기 · 내보내기");
        Ui.FlatButton ics = Ui.button("구글 캘린더(.ics) 가져오기");
        ics.setIcon(new Icons(Icons.Kind.IMPORT, 14));
        ics.addActionListener(e -> frame.importIcs(this));
        Ui.FlatButton icsOut = Ui.button(".ics로 내보내기");
        icsOut.setIcon(new Icons(Icons.Kind.EXPORT, 14));
        icsOut.addActionListener(e -> frame.exportIcs(this));
        Ui.FlatButton diary = Ui.button("DiaryCalendar 데이터 가져오기");
        diary.setIcon(new Icons(Icons.Kind.IMPORT, 14));
        diary.addActionListener(e -> frame.importDiaryCalendar(this));
        Ui.FlatButton backup = Ui.button("백업 만들기");
        backup.addActionListener(e -> frame.backup(this));
        Ui.FlatButton folder = Ui.button("저장 폴더 열기");
        folder.addActionListener(e -> Ui.openFolder(this, AppSettings.dataDir()));
        f.row("", Ui.row(ics, 6, icsOut));
        f.row("", Ui.row(diary));
        f.row("", Ui.row(backup, 6, folder));
        Ui.Label where = Ui.label("저장 위치: " + AppSettings.dataDir(), 11.5f, false, Ui.SUB);
        where.setToolTipText(AppSettings.dataDir().toString());
        where.setPreferredSize(new Dimension(420, where.getPreferredSize().height));
        f.row("", where);

        Ui.FlatButton cancel = Ui.button("취소");
        Ui.FlatButton save = Ui.primaryButton("저장");
        cancel.addActionListener(e -> dispose());
        save.addActionListener(e -> {
            settings.set("profileName", profileName.getText().trim().isEmpty() ? "나의 다이어리" : profileName.getText().trim());
            settings.set("profileImage", profileImage);
            settings.set("characterName", charName.getText().trim());
            settings.set("characterImage", charImage);
            settings.set("alarmMessage", msgBefore.getText().trim());
            settings.set("alarmMessageOnTime", msgOnTime.getText().trim());
            settings.set("weekStart", weekStart.getSelectedIndex() == 1 ? "MONDAY" : "SUNDAY");
            settings.setBool("closeToTray", toTray.isSelected());
            settings.save();
            if (startup.isSelected() != startupWas) {
                String err = Startup.set(startup.isSelected());
                if (err != null) Ui.warn(this, err);
            }
            dispose();
            frame.rebuild();
        });
        JPanel buttons = Ui.clear(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttons.setBorder(Ui.pad(10, 18, 14, 18));
        buttons.add(cancel);
        buttons.add(save);

        JPanel root = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                g.setColor(Theme.surface());
                g.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        JPanel holder = new Ui.WidthTracking(new BorderLayout());
        holder.add(f, BorderLayout.NORTH);
        JScrollPane sp = Ui.scroll(holder);
        root.add(sp, BorderLayout.CENTER);
        root.add(buttons, BorderLayout.SOUTH);
        setContentPane(root);
        Ui.escCloses(this, this::dispose);
        pack();
        Rectangle scr = Ui.screen();
        setSize(Math.min(Math.min(getWidth() + 20, 680), scr.width - 40), Math.min(getHeight(), scr.height - 40));
        setLocationRelativeTo(frame);
    }

    private static String fileName(String path, String none) {
        if (path == null || path.isBlank()) return none;
        return new File(path).getName();
    }

    private static class Form extends JPanel {
        private int y;
        private final GridBagConstraints c = new GridBagConstraints();

        Form() {
            super(new GridBagLayout());
            setOpaque(false);
            setBorder(Ui.pad(10, 22, 6, 22));
            c.anchor = GridBagConstraints.WEST;
            c.fill = GridBagConstraints.HORIZONTAL;
        }

        void section(String title) {
            c.gridy = y++; c.gridx = 0; c.gridwidth = 2; c.weightx = 1;
            c.insets = new Insets(y == 1 ? 4 : 18, 0, 4, 0);
            Ui.Label l = Ui.label(title, 14.5f, true, Ui.HIGHLIGHT);
            l.setFont(Ui.semi(14.5f));
            add(l, c);
            c.gridwidth = 1;
        }

        void row(String label, JComponent comp) {
            c.gridy = y++; c.gridx = 0; c.weightx = 0;
            c.insets = new Insets(5, 0, 5, 14);
            add(Ui.label(label, 13, false, Ui.SUB), c);
            c.gridx = 1; c.weightx = 1;
            c.insets = new Insets(5, 0, 5, 0);
            add(comp, c);
        }
    }
}
