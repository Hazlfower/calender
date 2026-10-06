import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.imageio.ImageIO;

// 메인 창
// ┌──────────── 위쪽 바: 검색 / 밤낮 / 테마 / 설정 ────────────┐
// │ 프로필·탭·카테고리·메모 │   달력   │ 예정된 일정 / 할 일 │ 인덱스 │
// └────────────────────────────────────────────────────────────┘
public class MainFrame extends JFrame {
    private final ScheduleManager HAZscheduleManager;
    private final AlarmManager HAZalarmManager;
    private final AppSettings HAZsettings;
    private final MemoManager memoManager;
    private final Todos todos;
    private final WidgetWindow widget;

    // 화면을 다시 만들어도 유지되는 상태
    private YearMonth month = YearMonth.now();
    private LocalDate selectedDate = LocalDate.now();
    private String page = "달력";
    private BufferedImage profileImage;

    // 화면 부품 (테마가 바뀌면 새로 만듦)
    private CalendarView calendarView;
    private RightPanel rightPanel;
    private AlarmView alarmView;
    private JPanel categoryList, memoList, indexTabs;
    private JLabel yearLabel, ddayLabel;
    private final Map<String, Ui.FlatButton> tabButtons = new LinkedHashMap<>();
    private CardLayout pageCards;
    private JPanel pages;
    private SearchWindow searchWindow;
    private ThemeEditor themeEditor;
    private ScheduleEditor editor;

    public MainFrame(ScheduleManager sm, AlarmManager am, AppSettings settings, MemoManager memos, Todos todos) {
        this.HAZscheduleManager = sm;
        this.HAZalarmManager = am;
        this.HAZsettings = settings;
        this.memoManager = memos;
        this.todos = todos;
        this.widget = new WidgetWindow(sm, settings);

        setTitle("캘린더 (미정)");
        setSize(1280, 780);
        setMinimumSize(new Dimension(1080, 660));
        setLocationRelativeTo(null);
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        sm.addListener(this::refreshData);
        memos.addListener(this::refreshMemos);

        loadProfileImage(false);
        rebuild();
        widget.apply();
    }

    // ===== 전체 화면 다시 만들기 (테마/밤낮/UI 종류가 바뀌면) =====
    public void rebuild() {
        Theme.load(HAZsettings);
        tabButtons.clear();

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Theme.bg());
        root.add(buildTopBar(), BorderLayout.NORTH);

        JPanel body = Ui.clear(new BorderLayout(14, 0));
        body.setBorder(Ui.pad(14, 14, 14, Theme.hasIndexTabs() ? 0 : 14));
        body.add(buildLeft(), BorderLayout.WEST);

        calendarView = new CalendarView(this, HAZscheduleManager);
        alarmView = new AlarmView(HAZscheduleManager, HAZalarmManager);
        pageCards = new CardLayout();
        pages = Ui.clear(pageCards);
        pages.add(calendarView, "달력");
        pages.add(alarmView, "알람");
        pages.add(new WidgetView(HAZsettings, widget::apply), "위젯");
        body.add(pages, BorderLayout.CENTER);

        rightPanel = new RightPanel(this, HAZscheduleManager, todos);
        JPanel east = Ui.clear(new BorderLayout(0, 0));
        east.add(rightPanel, BorderLayout.CENTER);
        if (Theme.hasIndexTabs()) east.add(buildIndex(), BorderLayout.EAST);
        body.add(east, BorderLayout.EAST);

        root.add(body, BorderLayout.CENTER);
        setContentPane(root);
        if (editor != null) { editor.setVisible(false); editor = null; }

        showPage(page);
        refreshCategories();
        refreshMemos();
        refreshDday();

        memoManager.restyleAll();
        widget.refresh();
        if (themeEditor != null) themeEditor.reload();
        if (searchWindow != null) {
            boolean visible = searchWindow.isVisible();
            String q = searchWindow.query();
            searchWindow.dispose();
            searchWindow = new SearchWindow(this, HAZscheduleManager);
            if (visible) searchWindow.open(q);
        }
        revalidate();
        repaint();
    }

    // ===== 위쪽 바 =====
    private JComponent buildTopBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(Theme.primary());
        bar.setBorder(Ui.pad(6, 18, 6, 10));
        bar.setPreferredSize(new Dimension(0, 54));

        String name = Theme.style() == Theme.Style.SF ? "CALENDAR // HAZ" : "캘린더";
        bar.add(Ui.label(name, 17, true, Theme.onPrimary()), BorderLayout.WEST);

        // 검색칸 (Enter → 검색 창)
        JTextField search = new JTextField(16);
        search.setOpaque(false);
        search.setBorder(Ui.pad(0, 12, 0, 12));
        search.setForeground(Theme.onPrimary());
        search.setCaretColor(Theme.onPrimary());
        search.setToolTipText("일정 검색 (Enter)");
        search.addActionListener(e -> openSearch(search.getText()));
        Ui.RoundPanel searchBox = new Ui.RoundPanel(new BorderLayout(), Ui.blend(Color.BLACK, Theme.primary(), 0.22), null, 30);
        searchBox.setPreferredSize(new Dimension(220, 34));
        searchBox.add(search);

        JPanel right = Ui.clear(new FlowLayout(FlowLayout.RIGHT, 4, 2));
        right.add(searchBox);
        right.add(Box.createHorizontalStrut(4));
        right.add(Ui.icon("search", "일정 검색", () -> openSearch(search.getText())));
        right.add(Ui.icon("moon", Theme.isNight() ? "낮 모드로" : "밤 모드로", () -> {
            HAZsettings.set("nightMode", String.valueOf(!Theme.isNight()));
            HAZsettings.save();
            rebuild();
        }));
        right.add(Ui.icon("brush", "테마 설정", this::openThemeEditor));
        right.add(Ui.icon("gear", "설정", () -> new SettingsDialog(this, HAZsettings, HAZalarmManager).setVisible(true)));
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    // ===== 왼쪽: 프로필 / 탭 / 카테고리 / 메모 =====
    private JComponent buildLeft() {
        Ui.RoundPanel left = new Ui.RoundPanel(null, Ui.blend(Theme.secondary(), Theme.bg(), 0.35), null, Theme.radius());
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        left.setPreferredSize(new Dimension(210, 0));
        left.setBorder(Ui.pad(14, 14, 14, 14));

        // 프로필 사진 (클릭 → 윈도우 파일 창)
        JComponent profile = new JComponent() {
            @Override protected void paintComponent(Graphics g) {
                Ui.smooth(g);
                Graphics2D g2 = (Graphics2D) g;
                int r = Theme.radius() + 6;
                RoundRectangle2D shape = new RoundRectangle2D.Double(1, 1, getWidth() - 3, getHeight() - 3, r, r);
                if (profileImage != null) {
                    Shape old = g2.getClip();
                    g2.clip(shape);
                    double scale = Math.max((double) getWidth() / profileImage.getWidth(), (double) getHeight() / profileImage.getHeight());
                    int iw = (int) (profileImage.getWidth() * scale), ih = (int) (profileImage.getHeight() * scale);
                    g2.drawImage(profileImage, (getWidth() - iw) / 2, (getHeight() - ih) / 2, iw, ih, null);
                    g2.setClip(old);
                } else {
                    g2.setColor(Theme.surface());
                    g2.fill(shape);
                    g2.setColor(Theme.sub());
                    g2.setFont(Ui.font(12, false));
                    String t = "프로필 사진";
                    g2.drawString(t, (getWidth() - g2.getFontMetrics().stringWidth(t)) / 2, getHeight() / 2 + 4);
                }
                g2.setColor(Theme.border());
                g2.setStroke(new BasicStroke(2f));
                g2.draw(shape);
            }
        };
        Dimension ps = new Dimension(182, 140);
        profile.setPreferredSize(ps);
        profile.setMaximumSize(ps);
        profile.setAlignmentX(LEFT_ALIGNMENT);
        profile.setToolTipText("클릭해서 프로필 사진 바꾸기");
        Ui.onClick(profile, this::chooseProfileImage);
        left.add(profile);
        left.add(Box.createVerticalStrut(8));

        ddayLabel = Ui.label("", 12, true, Theme.accent());
        ddayLabel.setAlignmentX(LEFT_ALIGNMENT);
        left.add(ddayLabel);
        left.add(Box.createVerticalStrut(10));

        // 탭
        Color[] shades = {Theme.secondary(), Ui.blend(Theme.secondary(), Theme.surface(), 0.7), Ui.blend(Theme.secondary(), Theme.surface(), 0.5)};
        String[] names = {"달력", "알람", "위젯"};
        for (int i = 0; i < names.length; i++) {
            String n = names[i];
            Ui.FlatButton b = new Ui.FlatButton(n, shades[i], Theme.onColor(shades[i]));
            b.setFont(Ui.font(14, true));
            b.setHorizontalAlignment(SwingConstants.LEFT);
            b.setBorder(Ui.pad(0, 16, 0, 10));
            b.setAlignmentX(LEFT_ALIGNMENT);
            b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
            b.setPreferredSize(new Dimension(180, 38));
            b.addActionListener(e -> showPage(n));
            tabButtons.put(n, b);
            left.add(b);
            left.add(Box.createVerticalStrut(6));
        }

        JSeparator sep = new JSeparator();
        sep.setForeground(Theme.border());
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 2));
        left.add(Box.createVerticalStrut(6));
        left.add(sep);
        left.add(Box.createVerticalStrut(8));

        // 카테고리 목록
        JPanel catHeader = Ui.clear(new BorderLayout());
        catHeader.setAlignmentX(LEFT_ALIGNMENT);
        catHeader.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
        catHeader.add(Ui.label("카테고리", 12, true, Theme.sub()), BorderLayout.WEST);
        Ui.FlatButton addCat = new Ui.FlatButton("+", Theme.surface(), Theme.text());
        addCat.setBorder(Ui.pad(0, 8, 0, 8));
        addCat.setToolTipText("카테고리 추가");
        addCat.addActionListener(e -> askNewCategory());
        catHeader.add(addCat, BorderLayout.EAST);
        left.add(catHeader);

        categoryList = Ui.clear(null);
        categoryList.setLayout(new BoxLayout(categoryList, BoxLayout.Y_AXIS));
        categoryList.setAlignmentX(LEFT_ALIGNMENT);
        left.add(categoryList);
        left.add(Box.createVerticalGlue());

        // 메모 카드
        Ui.FlatButton addMemo = new Ui.FlatButton("+", Ui.blend(Theme.onPrimary(), Theme.primary(), 0.2), Theme.onPrimary());
        addMemo.setBorder(Ui.pad(0, 9, 0, 9));
        addMemo.setToolTipText("새 메모 (따로 창으로 떠요)");
        addMemo.addActionListener(e -> memoManager.open(memoManager.create()));
        memoList = Ui.clear(null);
        memoList.setLayout(new BoxLayout(memoList, BoxLayout.Y_AXIS));
        memoList.setBorder(Ui.pad(6, 10, 6, 10));
        JPanel memoWrap = Ui.clear(new BorderLayout());
        memoWrap.add(memoList, BorderLayout.NORTH);
        Ui.RoundPanel memoCard = Ui.headerCard("메모", addMemo, Ui.scroll(memoWrap));
        memoCard.setAlignmentX(LEFT_ALIGNMENT);
        memoCard.setPreferredSize(new Dimension(182, 150));
        memoCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, 150));
        left.add(memoCard);
        return left;
    }

    private void refreshCategories() {
        if (categoryList == null) return;
        categoryList.removeAll();
        for (Map.Entry<String, Color> e : HAZscheduleManager.getCategories().entrySet()) {
            String name = e.getKey();
            boolean hidden = HAZscheduleManager.isHidden(name);
            JLabel l = Ui.label(name, 13, false, hidden ? Theme.sub() : Theme.text());
            l.setIcon(hidden ? Ui.dot(Ui.blend(e.getValue(), Theme.bg(), 0.3), 11) : Ui.dot(e.getValue(), 11));
            l.setIconTextGap(9);
            l.setBorder(Ui.pad(4, 4, 4, 4));
            l.setToolTipText("클릭: 달력에서 보이기/숨기기 · 우클릭: 색 변경/삭제");
            if (hidden) l.setText("<html><strike>" + name + "</strike></html>");
            l.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            l.addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent ev) {
                    if (SwingUtilities.isRightMouseButton(ev)) {
                        JPopupMenu menu = new JPopupMenu();
                        JMenuItem color = new JMenuItem("색 변경");
                        color.addActionListener(a -> {
                            Color c = JColorChooser.showDialog(MainFrame.this, name + " 색", e.getValue());
                            if (c != null) HAZscheduleManager.setCategoryColor(name, c);
                        });
                        JMenuItem del = new JMenuItem("삭제");
                        del.addActionListener(a -> {
                            if (JOptionPane.showConfirmDialog(MainFrame.this, "'" + name + "' 카테고리를 삭제할까요?\n(일정은 지워지지 않아요)",
                                    "카테고리 삭제", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION)
                                HAZscheduleManager.removeCategory(name);
                        });
                        menu.add(color);
                        menu.add(del);
                        menu.show(l, ev.getX(), ev.getY());
                    } else {
                        HAZscheduleManager.toggleHidden(name);
                    }
                }
            });
            categoryList.add(l);
        }
        categoryList.revalidate();
        categoryList.repaint();
    }

    private void refreshMemos() {
        if (memoList == null) return;
        memoList.removeAll();
        if (memoManager.all().isEmpty()) memoList.add(Ui.label("+ 를 눌러 메모 추가", 11, false, Theme.sub()));
        for (MemoManager.Memo m : memoManager.all()) {
            JLabel l = Ui.label("· " + m.title + (m.pinned ? "  (고정)" : ""), 12, false, Theme.text());
            l.setBorder(Ui.pad(2, 0, 2, 0));
            Ui.onClick(l, () -> memoManager.open(m));
            memoList.add(l);
        }
        memoList.revalidate();
        memoList.repaint();
    }

    private void refreshDday() {
        LocalDate d = HAZsettings.getDate("ddayDate");
        if (d == null) { ddayLabel.setText(" "); return; }
        long days = ChronoUnit.DAYS.between(LocalDate.now(), d);
        ddayLabel.setText(HAZsettings.get("ddayTitle") + "  " + (days == 0 ? "D-DAY" : days > 0 ? "D-" + days : "D+" + (-days)));
    }

    // ===== 오른쪽 끝 인덱스 (연도 + 월) =====
    private JComponent buildIndex() {
        JPanel index = Ui.clear(new BorderLayout(0, 4));
        index.setPreferredSize(new Dimension(52, 0));
        index.setBorder(Ui.pad(0, 0, 0, 0));

        yearLabel = Ui.label("", 11, true, Theme.sub());
        yearLabel.setHorizontalAlignment(SwingConstants.CENTER);
        JButton up = arrow("▲", "이전 해", () -> setMonth(month.minusYears(1)));
        JButton down = arrow("▼", "다음 해", () -> setMonth(month.plusYears(1)));
        JPanel top = Ui.clear(new BorderLayout());
        top.add(up, BorderLayout.NORTH);
        top.add(yearLabel, BorderLayout.SOUTH);

        indexTabs = Ui.clear(new GridLayout(12, 1, 0, 2));
        for (int i = 0; i < 12; i++) {
            int m = i + 1;
            JButton tab = new JButton(String.valueOf(m)) {
                @Override protected void paintComponent(Graphics g) {
                    Ui.smooth(g);
                    Graphics2D g2 = (Graphics2D) g;
                    boolean active = page.equals("달력") && month.getMonthValue() == m;
                    boolean hover = getModel().isRollover();
                    int w = active ? getWidth() : hover ? getWidth() - 5 : getWidth() - 10;
                    Color fill = active ? Theme.highlight()
                            : Ui.blend(Theme.secondary(), Theme.bg(), m % 2 == 0 ? 0.55 : 0.8);
                    g2.setColor(fill);
                    if (Theme.style() == Theme.Style.SF) {
                        g2.fillRect(0, 1, w, getHeight() - 2);
                        g2.setColor(active ? Theme.accent() : Theme.border());
                        g2.drawRect(0, 1, w - 1, getHeight() - 3);
                    } else {
                        g2.fillRoundRect(-14, 1, w + 13, getHeight() - 2, 16, 16);
                        g2.setColor(Ui.blend(Theme.text(), fill, 0.15));
                        g2.drawRoundRect(-14, 1, w + 12, getHeight() - 3, 16, 16);
                    }
                    g2.setFont(Ui.font(11, active));
                    g2.setColor(Theme.onColor(fill));
                    String t = m + "월";
                    FontMetrics fm = g2.getFontMetrics();
                    g2.drawString(t, (w - fm.stringWidth(t)) / 2, (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
                }
            };
            tab.setContentAreaFilled(false);
            tab.setBorderPainted(false);
            tab.setFocusPainted(false);
            tab.setRolloverEnabled(true);
            tab.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            tab.addActionListener(e -> { showPage("달력"); setMonth(YearMonth.of(month.getYear(), m)); });
            indexTabs.add(tab);
        }
        index.add(top, BorderLayout.NORTH);
        index.add(indexTabs, BorderLayout.CENTER);
        index.add(down, BorderLayout.SOUTH);
        return index;
    }

    private JButton arrow(String text, String tip, Runnable r) {
        Ui.FlatButton b = new Ui.FlatButton(text, Theme.bg(), Theme.sub());
        b.setFont(Ui.font(10, false));
        b.setToolTipText(tip);
        b.addActionListener(e -> r.run());
        return b;
    }

    // ===== 상태 바꾸기 (다른 화면에서 부름) =====
    public YearMonth getMonth() { return month; }
    public LocalDate getSelectedDate() { return selectedDate; }

    public void setMonth(YearMonth m) {
        month = m;
        refreshCalendar();
    }

    public void selectDate(LocalDate d) {
        selectedDate = d;
        month = YearMonth.from(d);
        refreshCalendar();
    }

    public void goToDate(LocalDate d) {
        showPage("달력");
        selectDate(d);
    }

    private void refreshCalendar() {
        if (calendarView != null) calendarView.refresh();
        if (yearLabel != null) yearLabel.setText(String.valueOf(month.getYear()));
        if (indexTabs != null) indexTabs.repaint();
    }

    private void refreshData() {
        refreshCalendar();
        if (rightPanel != null) rightPanel.refresh();
        if (alarmView != null) alarmView.refresh();
        if (searchWindow != null && searchWindow.isVisible()) searchWindow.search();
        refreshCategories();
        widget.refresh();
    }

    private void showPage(String p) {
        page = p;
        pageCards.show(pages, p);
        for (Map.Entry<String, Ui.FlatButton> e : tabButtons.entrySet()) e.getValue().setActive(e.getKey().equals(p));
        if (p.equals("알람")) alarmView.refresh();
        refreshCalendar();
    }

    // ===== 일정 편집 (창 위에 어둡게 덮는 화면) =====
    public void openEditor(Schedule target) {
        editor = new ScheduleEditor(this, HAZscheduleManager, selectedDate, target);
        setGlassPane(editor);
        editor.setVisible(true);
    }

    public void editorClosed() {
        editor = null;
        requestFocus();
    }

    public boolean confirmDelete(Schedule s) {
        String extra = s.getRepeat() != Schedule.HAZRepeat.NONE ? "\n(반복 일정이라 모든 날짜에서 지워져요)" : "";
        if (JOptionPane.showConfirmDialog(this, "'" + s.getTitle() + "' 일정을 삭제할까요?" + extra,
                "삭제", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return false;
        HAZscheduleManager.remove(s);
        return true;
    }

    // 카테고리 추가: 이름 → 색 고르기
    public String askNewCategory() {
        String name = JOptionPane.showInputDialog(this, "새 카테고리 이름");
        if (name == null || name.isBlank()) return null;
        name = name.trim();
        if (name.contains("|")) { JOptionPane.showMessageDialog(this, "| 는 쓸 수 없어요."); return null; }
        Color c = JColorChooser.showDialog(this, name + " 색 고르기", new Color(0xC9B8E8));
        HAZscheduleManager.addCategory(name, c == null ? new Color(0xC9B8E8) : c);
        return name;
    }

    private void openSearch(String query) {
        if (searchWindow == null) searchWindow = new SearchWindow(this, HAZscheduleManager);
        searchWindow.open(query);
    }

    private void openThemeEditor() {
        if (themeEditor == null) themeEditor = new ThemeEditor(this, HAZsettings);
        themeEditor.setVisible(true);
    }

    // ===== 프로필 사진 (윈도우 파일 탐색기 창으로 고르기) =====
    private void chooseProfileImage() {
        File f = Ui.openFile(this, "프로필 사진 고르기", "png", "jpg", "jpeg", "gif", "bmp");
        if (f == null) return;
        HAZsettings.set("profileImage", f.getAbsolutePath());
        HAZsettings.save();
        loadProfileImage(true);
        repaint();
    }

    private void loadProfileImage(boolean askIfMissing) {
        String path = HAZsettings.get("profileImage");
        profileImage = null;
        if (path.isEmpty()) return;
        try { profileImage = ImageIO.read(new File(path)); } catch (Exception ignored) { }
        if (profileImage == null) {
            // 경로가 틀렸으면 다시 고르게 함
            SwingUtilities.invokeLater(() -> {
                int answer = JOptionPane.showConfirmDialog(this, "프로필 사진을 찾을 수 없어요.\n" + path + "\n다른 사진을 고를까요?",
                        "사진 없음", JOptionPane.YES_NO_OPTION);
                if (answer == JOptionPane.YES_OPTION) chooseProfileImage();
                else { HAZsettings.set("profileImage", null); HAZsettings.save(); }
            });
        }
    }
}
