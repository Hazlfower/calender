import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.RoundRectangle2D;
import java.io.File;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// 메인 창
// ┌──────────── 위쪽 바: ☰ · 검색 · 밤낮 · 테마 · 설정 ────────────┐
// │ 프로필·탭·카테고리·메모 │ 달력(월/주/일) │ D-Day·예정·할 일 │ 인덱스 │
// └──────────────────────────────────────────────────────────────┘
public class MainFrame extends JFrame {
    private final ScheduleManager HAZscheduleManager;
    private final AlarmManager HAZalarmManager;
    private final AppSettings HAZsettings;
    private final MemoManager memoManager;
    private final Todos todos;
    private final DataStore HAZdataStore;
    private final WidgetWindow widget;

    // 화면을 다시 만들어도 유지되는 상태
    private LocalDate anchor = LocalDate.now();      // 지금 보고 있는 기간의 기준 날짜
    private LocalDate selected = LocalDate.now();
    private String view;                             // MONTH / WEEK / DAY
    private String page = "달력";
    private LocalDate lastToday = LocalDate.now();

    // 화면 부품 (테마가 바뀌면 새로 만듦)
    private CalendarView calendarView;
    private RightPanel rightPanel;
    private AlarmView alarmView;
    private DDayView ddayView;
    private JPanel categoryList, memoList, indexTabs, leftPanel, body;
    private Ui.Label yearLabel, ddayLabel, profileNameLabel;
    private JTextField searchField;
    private final Map<String, Ui.FlatButton> tabButtons = new LinkedHashMap<>();
    private CardLayout pageCards;
    private JPanel pages;
    private SearchWindow searchWindow;
    private ThemeEditor themeEditor;
    private ScheduleEditor editor;
    private TrayIcon trayIcon;
    private boolean trayHintShown;

    public MainFrame(ScheduleManager sm, AlarmManager am, AppSettings settings, MemoManager memos, Todos todos, DataStore store) {
        super("캘린더");
        this.HAZscheduleManager = sm;
        this.HAZalarmManager = am;
        this.HAZsettings = settings;
        this.memoManager = memos;
        this.todos = todos;
        this.HAZdataStore = store;
        this.view = settings.get("view").isEmpty() ? "MONTH" : settings.get("view");
        this.widget = new WidgetWindow(sm, settings, this::showMain);
        am.setOpenMain(this::showMain);

        Rectangle screen = Ui.screen();
        setSize(Math.min(settings.getInt("winW", 1320), screen.width - 20), Math.min(settings.getInt("winH", 820), screen.height - 20));
        setMinimumSize(new Dimension(Math.min(980, screen.width), Math.min(620, screen.height)));
        setLocationRelativeTo(null);
        if (settings.getBool("winMax")) setExtendedState(MAXIMIZED_BOTH);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { onClose(); }
        });
        addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) {
                boolean max = (getExtendedState() & MAXIMIZED_BOTH) != 0;
                HAZsettings.setBool("winMax", max);
                if (!max) {
                    HAZsettings.setInt("winW", getWidth());
                    HAZsettings.setInt("winH", getHeight());
                }
            }
        });
        // 일정 편집 화면은 창 크기를 따라감
        getLayeredPane().addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) {
                if (editor != null) {
                    editor.setBounds(0, 0, getLayeredPane().getWidth(), getLayeredPane().getHeight());
                    editor.revalidate();
                }
            }
        });
        // 1분마다: 남은 시간, 지금 시각 선, 날짜 바뀜(자정), D-Day
        new Timer(60_000, e -> {
            if (!LocalDate.now().equals(lastToday)) {
                if (selected.equals(lastToday)) selected = LocalDate.now();
                if (anchor.equals(lastToday)) anchor = LocalDate.now();
                lastToday = LocalDate.now();
                setIconImages(Ui.appIcons());
                if (trayIcon != null) trayIcon.setImage(Ui.appIcon(32));
            }
            refreshData();
        }).start();

        sm.addListener(this::refreshData);
        memos.addListener(this::refreshMemos);
        bindKeys();
        rebuild();
        widget.apply();
        setupTray();
    }

    // ================================================================== 화면 만들기 (테마/밤낮/UI 종류가 바뀌면 다시)

    public void rebuild() {
        Theme.load(HAZsettings);
        tabButtons.clear();
        setIconImages(Ui.appIcons());
        setTitle(Theme.style() == Theme.Style.SF ? "CALENDAR // HAZ" : "캘린더");

        JPanel root = new Ui.Back(new BorderLayout());
        root.add(buildTopBar(), BorderLayout.NORTH);

        body = Ui.clear(new BorderLayout(14, 0));
        body.setBorder(Ui.pad(14, 14, 14, Theme.hasIndexTabs() ? 0 : 14));
        leftPanel = buildLeft();
        if (HAZsettings.getBool("sidebarOpen")) body.add(leftPanel, BorderLayout.WEST);

        calendarView = new CalendarView(this, HAZscheduleManager);
        alarmView = new AlarmView(HAZscheduleManager, HAZalarmManager);
        ddayView = new DDayView(HAZscheduleManager);
        pageCards = new CardLayout();
        pages = Ui.clear(pageCards);
        pages.add(calendarView, "달력");
        pages.add(alarmView, "알람");
        pages.add(ddayView, "D-Day");
        pages.add(new WidgetView(HAZsettings, widget::apply), "위젯");
        body.add(pages, BorderLayout.CENTER);

        rightPanel = new RightPanel(this, HAZscheduleManager, todos);
        JPanel east = Ui.clear(new BorderLayout());
        east.add(rightPanel, BorderLayout.CENTER);
        if (Theme.hasIndexTabs()) east.add(buildIndex(), BorderLayout.EAST);
        body.add(east, BorderLayout.EAST);

        root.add(body, BorderLayout.CENTER);
        setContentPane(root);
        removeEditor();

        calendarView.setView(view);
        showPage(page);
        refreshCategories();
        refreshMemos();
        refreshLeftInfo();

        // 다른 창들도 새 색으로
        for (Window w : Window.getWindows()) {
            if (w == this || w instanceof MemoWindow || !w.isDisplayable()) continue;
            SwingUtilities.updateComponentTreeUI(w);
            w.repaint();
        }
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

    // ----- 위쪽 바 -----
    private JComponent buildTopBar() {
        JPanel bar = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                g.setColor(Theme.primary());
                g.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        bar.setBorder(Ui.pad(6, 10, 6, 12));
        bar.setPreferredSize(new Dimension(0, 54));

        JButton menu = Ui.iconButton(Icons.Kind.MENU, "사이드바 접기/펴기", true, this::toggleSidebar);
        Ui.Label title = new Ui.Label(Theme.style() == Theme.Style.SF ? "CALENDAR // HAZ" : "캘린더", Ui.ON_PRIMARY, Ui.semi(17));
        title.setBorder(Ui.pad(0, 6, 0, 0));
        JPanel left = Ui.clear(new FlowLayout(FlowLayout.LEFT, 2, 3));
        left.add(menu);
        left.add(title);
        bar.add(left, BorderLayout.WEST);

        // 검색칸 (Enter → 검색 창)
        searchField = new JTextField(15);
        searchField.putClientProperty("haz.plain", true);
        searchField.putClientProperty("placeholder", "일정 검색 (Ctrl+F)");
        searchField.setOpaque(false);
        searchField.setBorder(Ui.pad(0, 4, 0, 10));
        searchField.setForeground(Theme.onPrimary());
        searchField.setCaretColor(Theme.onPrimary());
        searchField.addActionListener(e -> openSearch(searchField.getText()));
        JPanel searchBox = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = Ui.aa(g);
                Color p = Theme.primary();
                g2.setColor(Theme.lum(p) > 150 ? Ui.blend(Color.BLACK, p, 0.08) : Ui.blend(Color.WHITE, p, 0.14));
                int r = Theme.style() == Theme.Style.SF ? 4 : getHeight();
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), r, r));
                g2.dispose();
            }
        };
        searchBox.setOpaque(false);
        searchBox.setPreferredSize(new Dimension(240, 34));
        JLabel glass = new JLabel(new Icons(Icons.Kind.SEARCH, 15, Ui.alpha(Theme.onPrimary(), 190)));
        glass.setBorder(Ui.pad(0, 12, 0, 4));
        searchBox.add(glass, BorderLayout.WEST);
        searchBox.add(searchField, BorderLayout.CENTER);
        // 검색칸 안 안내 글자는 바탕이 진해서 직접 그림
        searchField.setUI(new javax.swing.plaf.basic.BasicTextFieldUI() {
            @Override protected void paintSafely(Graphics g) {
                super.paintSafely(g);
                if (searchField.getText().isEmpty() && !searchField.hasFocus()) {
                    Graphics2D g2 = Ui.aa(g);
                    g2.setFont(searchField.getFont());
                    g2.setColor(Ui.alpha(Theme.onPrimary(), 150));
                    FontMetrics fm = g2.getFontMetrics();
                    g2.drawString("일정 검색 (Ctrl+F)", 4, (searchField.getHeight() + fm.getAscent() - fm.getDescent()) / 2);
                    g2.dispose();
                }
            }
        });
        searchField.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override public void focusGained(java.awt.event.FocusEvent e) { searchField.repaint(); }
            @Override public void focusLost(java.awt.event.FocusEvent e) { searchField.repaint(); }
        });
        searchField.setOpaque(false);
        searchField.setBorder(Ui.pad(0, 4, 0, 10));
        searchField.setForeground(Theme.onPrimary());
        searchField.setCaretColor(Theme.onPrimary());
        searchField.setFont(Ui.font(13, false));

        JPanel right = Ui.clear(new FlowLayout(FlowLayout.RIGHT, 4, 3));
        right.add(searchBox);
        right.add(Box.createHorizontalStrut(6));
        right.add(Ui.iconButton(Icons.Kind.SEARCH, "일정 검색 창", true, () -> openSearch(searchField.getText())));
        right.add(Ui.iconButton(Theme.isNight() ? Icons.Kind.SUN : Icons.Kind.MOON, Theme.isNight() ? "낮 모드로" : "밤 모드로", true, () -> {
            HAZsettings.setBool("nightMode", !Theme.isNight());
            HAZsettings.save();
            rebuild();
        }));
        right.add(Ui.iconButton(Icons.Kind.BRUSH, "테마 꾸미기", true, this::openThemeEditor));
        right.add(Ui.iconButton(Icons.Kind.GEAR, "설정", true, () -> new SettingsDialog(this, HAZsettings, HAZalarmManager).setVisible(true)));
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    // ----- 왼쪽: 프로필 / 탭 / 카테고리 / 메모 -----
    private JPanel buildLeft() {
        JPanel left = new Ui.RoundPanel(null, null, null, -1) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = Ui.aa(g);
                int r = Theme.radius();
                g2.setColor(Ui.blend(Theme.secondary(), Theme.bg(), Theme.isNight() ? 0.3 : 0.35));
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), r, r));
                g2.dispose();
            }
        };
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        left.setPreferredSize(new Dimension(214, 0));
        left.setBorder(Ui.pad(14, 14, 14, 14));

        // 프로필 사진 (클릭 → 사진 고르기)
        JComponent profile = new JComponent() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = Ui.aa(g);
                int r = Theme.radius() + 6;
                RoundRectangle2D shape = new RoundRectangle2D.Double(1, 1, getWidth() - 3, getHeight() - 3, r, r);
                Image img = Ui.image(HAZsettings.get("profileImage"));
                if (img != null) Ui.drawCover(g2, img, shape, 40);
                else {
                    g2.setColor(Theme.surface());
                    g2.fill(shape);
                    new Icons(Icons.Kind.USER, 34, Ui.alpha(Theme.sub(), 160)).paintIcon(this, g2, getWidth() / 2 - 17, getHeight() / 2 - 26);
                    g2.setColor(Theme.sub());
                    g2.setFont(Ui.font(11.5f, false));
                    Ui.centerText(g2, "눌러서 사진 넣기", getWidth() / 2.0, getHeight() / 2.0 + 26);
                }
                g2.setColor(Theme.border());
                g2.setStroke(new BasicStroke(1.5f));
                g2.draw(shape);
                g2.dispose();
            }
        };
        Dimension ps = new Dimension(186, 116);
        profile.setPreferredSize(ps);
        profile.setMaximumSize(new Dimension(Integer.MAX_VALUE, 116));
        profile.setAlignmentX(LEFT_ALIGNMENT);
        profile.setToolTipText("클릭해서 프로필 사진 바꾸기");
        Ui.onClick(profile, this::chooseProfileImage);
        left.add(profile);
        left.add(Box.createVerticalStrut(8));

        profileNameLabel = new Ui.Label("", Ui.TEXT, Ui.semi(15));
        profileNameLabel.setAlignmentX(LEFT_ALIGNMENT);
        left.add(profileNameLabel);
        ddayLabel = Ui.label("", 12, true, Ui.HIGHLIGHT);
        ddayLabel.setAlignmentX(LEFT_ALIGNMENT);
        ddayLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        Ui.onClick(ddayLabel, () -> showPage("D-Day"));
        left.add(ddayLabel);
        left.add(Box.createVerticalStrut(10));

        // 탭
        String[] names = {"달력", "알람", "D-Day", "위젯"};
        Icons.Kind[] icons = {Icons.Kind.CALENDAR, Icons.Kind.BELL, Icons.Kind.FLAG, Icons.Kind.WIDGET};
        for (int i = 0; i < names.length; i++) {
            String n = names[i];
            double shade = i == 0 ? 1.0 : 0.82 - i * 0.1;
            Ui.FlatButton b = new Ui.FlatButton(n, Ui.B_PLAIN) {
                @Override Color fillColor() { return Ui.blend(Theme.secondary(), Theme.surface(), shade); }
                @Override Color textColor() { return Theme.onColor(fillColor()); }
            };
            b.setIcon(new Icons(icons[i], 15));
            b.setFont(Ui.font(14, true));
            b.setHorizontalAlignment(SwingConstants.LEFT);
            b.setBorder(Ui.pad(0, 14, 0, 10));
            b.setAlignmentX(LEFT_ALIGNMENT);
            b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
            b.setPreferredSize(new Dimension(180, 36));
            b.addActionListener(e -> showPage(n));
            tabButtons.put(n, b);
            left.add(b);
            left.add(Box.createVerticalStrut(5));
        }
        left.add(Box.createVerticalStrut(8));

        // 카테고리 목록
        JPanel catHeader = Ui.clear(new BorderLayout());
        catHeader.setAlignmentX(LEFT_ALIGNMENT);
        catHeader.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        catHeader.add(Ui.label("카테고리", 12, true, Ui.SUB), BorderLayout.WEST);
        JButton addCat = Ui.iconButton(Icons.Kind.PLUS, "카테고리 추가", false, this::askNewCategory);
        addCat.setPreferredSize(new Dimension(26, 26));
        catHeader.add(addCat, BorderLayout.EAST);
        left.add(catHeader);

        categoryList = Ui.clear(null);
        categoryList.setLayout(new BoxLayout(categoryList, BoxLayout.Y_AXIS));
        JPanel catWrap = Ui.clear(new BorderLayout());
        catWrap.add(categoryList, BorderLayout.NORTH);
        JScrollPane catScroll = Ui.scroll(catWrap);
        catScroll.setAlignmentX(LEFT_ALIGNMENT);
        catScroll.setPreferredSize(new Dimension(186, 60));
        catScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        left.add(catScroll);
        left.add(Box.createVerticalStrut(8));

        // 메모 카드
        Ui.FlatButton addMemo = Ui.headerButton("+");
        addMemo.setToolTipText("새 메모 (따로 창으로 떠요)");
        addMemo.addActionListener(e -> memoManager.open(memoManager.create(null)));
        memoList = Ui.clear(null);
        memoList.setLayout(new BoxLayout(memoList, BoxLayout.Y_AXIS));
        memoList.setBorder(Ui.pad(6, 12, 6, 10));
        JPanel memoWrap = Ui.clear(new BorderLayout());
        memoWrap.add(memoList, BorderLayout.NORTH);
        Ui.RoundPanel memoCard = Ui.headerCard("메모", addMemo, Ui.scroll(memoWrap));
        memoCard.setAlignmentX(LEFT_ALIGNMENT);
        memoCard.setPreferredSize(new Dimension(186, 140));
        memoCard.setMinimumSize(new Dimension(100, 90));
        memoCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, 150));
        left.add(memoCard);
        return left;
    }

    private void refreshCategories() {
        if (categoryList == null) return;
        categoryList.removeAll();
        for (ScheduleManager.Category cat : HAZscheduleManager.getCategories().values()) {
            String name = cat.name;
            boolean hidden = cat.hidden;
            Ui.Label l = Ui.label(hidden ? "<html><strike>" + Ui.html(name) + "</strike></html>" : name, 13, false, hidden ? Ui.SUB : Ui.TEXT);
            l.setIcon(Ui.dot(hidden ? Ui.blend(cat.color, Theme.bg(), 0.3) : cat.color, 11));
            l.setIconTextGap(9);
            l.setBorder(Ui.pad(4, 4, 4, 4));
            l.setToolTipText("클릭: 달력에서 보이기/숨기기 · 우클릭: 메뉴");
            l.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            l.addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent ev) {
                    if (SwingUtilities.isRightMouseButton(ev)) categoryMenu(l, ev, cat);
                    else HAZscheduleManager.toggleHidden(name);
                }
            });
            categoryList.add(l);
        }
        categoryList.revalidate();
        categoryList.repaint();
    }

    private void categoryMenu(Component c, MouseEvent ev, ScheduleManager.Category cat) {
        String name = cat.name;
        JPopupMenu menu = new JPopupMenu();
        menu.add(Ui.item("이 카테고리만 보기", () -> HAZscheduleManager.showOnly(name)));
        menu.add(Ui.item("모두 보기", HAZscheduleManager::showAll));
        menu.addSeparator();
        menu.add(Ui.item("색 바꾸기", () -> {
            Color col = JColorChooser.showDialog(this, name + " 색", cat.color);
            if (col != null) HAZscheduleManager.setCategoryColor(name, col);
        }));
        menu.add(Ui.item("이름 바꾸기", () -> {
            String to = (String) JOptionPane.showInputDialog(this, "새 이름", "카테고리 이름 바꾸기", JOptionPane.PLAIN_MESSAGE, null, null, name);
            if (to == null || to.trim().equals(name)) return;
            if (to.contains("|") || !HAZscheduleManager.renameCategory(name, to)) Ui.warn(this, "쓸 수 없는 이름이거나 이미 있는 이름이에요.");
        }));
        menu.add(Ui.item("삭제", () -> {
            String fallback = HAZscheduleManager.fallbackFor(name);
            if (fallback == null) { Ui.info(this, "카테고리", "카테고리는 하나 이상 있어야 해요."); return; }
            if (Ui.confirm(this, "카테고리 삭제", "'" + name + "' 카테고리를 삭제할까요?\n이 카테고리의 일정은 '" + fallback + "'(으)로 옮겨져요."))
                HAZscheduleManager.removeCategory(name);
        }));
        menu.show(c, ev.getX(), ev.getY());
    }

    private void refreshMemos() {
        if (memoList == null) return;
        memoList.removeAll();
        if (memoManager.all().isEmpty()) memoList.add(Ui.label("+ 를 눌러 메모 추가", 11.5f, false, Ui.SUB));
        for (MemoManager.Memo m : memoManager.all()) {
            String t = m.title == null || m.title.isBlank() ? "(제목 없음)" : m.title;
            Ui.Label l = Ui.label(t, 12.5f, false, Ui.TEXT);
            l.setIcon(new Icons(m.pinned ? Icons.Kind.PIN : Icons.Kind.NOTE, 13, Theme.sub()));
            l.setIconTextGap(6);
            l.setBorder(Ui.pad(3, 0, 3, 0));
            l.setToolTipText("클릭: 메모 창 열기");
            Ui.onClick(l, () -> memoManager.open(m));
            memoList.add(l);
        }
        memoList.revalidate();
        memoList.repaint();
    }

    private void refreshLeftInfo() {
        if (profileNameLabel == null) return;
        profileNameLabel.setText(HAZsettings.get("profileName"));
        List<DDay> dd = HAZscheduleManager.activeDDays();
        ddayLabel.setText(dd.isEmpty() ? " " : dd.get(0).label() + "  " + dd.get(0).name);
        ddayLabel.setToolTipText(dd.isEmpty() ? null : "D-Day 관리");
    }

    // ----- 오른쪽 끝 인덱스 (연도 + 월) -----
    private JComponent buildIndex() {
        JPanel index = Ui.clear(new BorderLayout(0, 4));
        index.setPreferredSize(new Dimension(54, 0));

        yearLabel = Ui.label("", 11, true, Ui.SUB);
        yearLabel.setHorizontalAlignment(SwingConstants.CENTER);
        JButton up = Ui.iconButton(Icons.Kind.UP, "이전 해", false, () -> setMonth(YearMonth.from(anchor).minusYears(1)));
        JButton down = Ui.iconButton(Icons.Kind.DOWN, "다음 해", false, () -> setMonth(YearMonth.from(anchor).plusYears(1)));
        up.setPreferredSize(new Dimension(40, 26));
        down.setPreferredSize(new Dimension(40, 26));
        JPanel top = Ui.clear(new BorderLayout());
        top.add(up, BorderLayout.NORTH);
        top.add(yearLabel, BorderLayout.SOUTH);

        indexTabs = Ui.clear(new GridLayout(12, 1, 0, 2));
        for (int i = 0; i < 12; i++) {
            int m = i + 1;
            JButton tab = new JButton(String.valueOf(m)) {
                @Override protected void paintComponent(Graphics g) {
                    Graphics2D g2 = Ui.aa(g);
                    boolean active = page.equals("달력") && anchor.getMonthValue() == m;
                    boolean hover = getModel().isRollover();
                    int w = active ? getWidth() : hover ? getWidth() - 5 : getWidth() - 11;
                    Color fill = active ? Theme.highlight() : Ui.blend(Theme.secondary(), Theme.bg(), m % 2 == 0 ? 0.55 : 0.8);
                    g2.setColor(fill);
                    if (Theme.style() == Theme.Style.SF) {
                        g2.fillRect(0, 1, w, getHeight() - 2);
                        g2.setColor(active ? Theme.accent() : Theme.border());
                        g2.drawRect(0, 1, w - 1, getHeight() - 3);
                    } else {
                        g2.fill(new RoundRectangle2D.Float(-14, 1, w + 13, getHeight() - 2, 16, 16));
                        g2.setColor(Ui.blend(Theme.text(), fill, 0.14));
                        g2.draw(new RoundRectangle2D.Float(-14, 1.5f, w + 12.5f, getHeight() - 3, 16, 16));
                    }
                    g2.setFont(Ui.font(11.5f, active));
                    g2.setColor(Theme.onColor(fill));
                    Ui.centerText(g2, m + "월", w / 2.0, (getHeight() + g2.getFontMetrics().getAscent() - g2.getFontMetrics().getDescent()) / 2.0);
                    g2.dispose();
                }
            };
            tab.setContentAreaFilled(false);
            tab.setBorderPainted(false);
            tab.setFocusPainted(false);
            tab.setOpaque(false);
            tab.setRolloverEnabled(true);
            tab.setToolTipText(m + "월로 이동");
            tab.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            tab.addActionListener(e -> {
                showPage("달력");
                setView("MONTH");
                setMonth(YearMonth.of(anchor.getYear(), m));
            });
            indexTabs.add(tab);
        }
        index.add(top, BorderLayout.NORTH);
        index.add(indexTabs, BorderLayout.CENTER);
        index.add(down, BorderLayout.SOUTH);
        return index;
    }

    // ================================================================== 상태 (다른 화면에서 부름)

    public LocalDate getAnchor() { return anchor; }
    public LocalDate getSelectedDate() { return selected; }
    public String getView() { return view; }

    public DayOfWeek firstDay() { return "MONDAY".equals(HAZsettings.get("weekStart")) ? DayOfWeek.MONDAY : DayOfWeek.SUNDAY; }

    public LocalDate weekStart(LocalDate d) {
        int off = (d.getDayOfWeek().getValue() - firstDay().getValue() + 7) % 7;
        return d.minusDays(off);
    }

    public void setView(String v) {
        view = v;
        HAZsettings.set("view", v);
        if (calendarView != null) calendarView.setView(v);
        refreshIndex();
    }

    // 이전/다음 (월 보기는 한 달, 주 보기는 한 주, 일 보기는 하루)
    public void shift(int n) {
        switch (view) {
            case "DAY": anchor = anchor.plusDays(n); selected = anchor; break;
            case "WEEK": anchor = anchor.plusWeeks(n); selected = selected.plusWeeks(n); break;
            default: setMonth(YearMonth.from(anchor).plusMonths(n)); return;
        }
        refreshCalendar();
    }

    public void setMonth(YearMonth m) {
        // 다른 달로 가면 그 달 1일(이번 달이면 오늘)을 고름
        if (!YearMonth.from(selected).equals(m)) selected = m.equals(YearMonth.now()) ? LocalDate.now() : m.atDay(1);
        anchor = selected;
        refreshCalendar();
    }

    public void selectDate(LocalDate d) {
        selected = d;
        anchor = d;
        refreshCalendar();
    }

    public void goToDate(LocalDate d) {
        showPage("달력");
        selectDate(d);
    }

    private void refreshCalendar() {
        if (calendarView != null) calendarView.refresh();
        refreshIndex();
    }

    private void refreshIndex() {
        if (yearLabel != null) yearLabel.setText(String.valueOf(anchor.getYear()));
        if (indexTabs != null) indexTabs.repaint();
    }

    private void refreshData() {
        refreshCalendar();
        if (rightPanel != null) rightPanel.refresh();
        if (alarmView != null) alarmView.refresh();
        if (ddayView != null) ddayView.refresh();
        if (searchWindow != null && searchWindow.isVisible()) searchWindow.search();
        refreshCategories();
        refreshLeftInfo();
        widget.refresh();
    }

    public void showPage(String p) {
        page = p;
        pageCards.show(pages, p);
        for (Map.Entry<String, Ui.FlatButton> e : tabButtons.entrySet()) e.getValue().setActive(e.getKey().equals(p));
        if (p.equals("알람")) alarmView.refresh();
        refreshIndex();
    }

    private void toggleSidebar() {
        boolean open = !HAZsettings.getBool("sidebarOpen");
        HAZsettings.setBool("sidebarOpen", open);
        HAZsettings.save();
        if (open) body.add(leftPanel, BorderLayout.WEST); else body.remove(leftPanel);
        body.revalidate();
        body.repaint();
    }

    private void bindKeys() {
        JRootPane rp = getRootPane();
        InputMap im = rp.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = rp.getActionMap();
        int ctrl = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        key(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_N, ctrl), "add", () -> { if (editor == null) addSchedule(selected, -1, -1); });
        key(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_T, ctrl), "today", () -> goToDate(LocalDate.now()));
        key(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_F, ctrl), "find", () -> { if (searchField != null) searchField.requestFocusInWindow(); });
        key(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_UP, 0), "prev", () -> { if (editor == null) shift(-1); });
        key(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_DOWN, 0), "next", () -> { if (editor == null) shift(1); });
        key(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_1, ctrl), "day", () -> { showPage("달력"); setView("DAY"); });
        key(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_2, ctrl), "week", () -> { showPage("달력"); setView("WEEK"); });
        key(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_3, ctrl), "month", () -> { showPage("달력"); setView("MONTH"); });
    }

    private static void key(InputMap im, ActionMap am, KeyStroke ks, String name, Runnable r) {
        im.put(ks, name);
        am.put(name, new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { r.run(); }
        });
    }

    // ================================================================== 일정 추가 / 수정 / 삭제

    public void addSchedule(LocalDate d, int start, int end) {
        Schedule s = new Schedule();
        s.date = d;
        s.start = start;
        s.end = end;
        s.category = HAZscheduleManager.getCategories().containsKey("개인") ? "개인" : HAZscheduleManager.firstCategory();
        openEditor(ScheduleEditor.Mode.NEW, null, s, null);
    }

    // 반복 일정이면 "이 날만 / 전체" 를 먼저 물어봄
    public void editSchedule(Schedule s, LocalDate occ) {
        if (s.repeat != Schedule.HAZRepeat.NONE && occ != null) {
            Object[] opts = {"이 날만 수정", "반복 전체 수정", "취소"};
            int r = JOptionPane.showOptionDialog(this, "반복 일정이에요. 어떻게 바꿀까요?\n「" + s.title + "」 · " + TimeText.md(occ),
                    "반복 일정 수정", JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, opts, opts[0]);
            if (r == 0) { openEditor(ScheduleEditor.Mode.EDIT_ONE, s, ScheduleEditor.detachedCopy(s, occ), occ); return; }
            if (r != 1) return;
        }
        openEditor(ScheduleEditor.Mode.EDIT_ALL, s, s.copy(), occ);
    }

    // 지웠으면 true
    public boolean confirmDelete(Schedule s, LocalDate occ) {
        if (s.repeat != Schedule.HAZRepeat.NONE && occ != null) {
            Object[] opts = {"이 날만 삭제", "반복 전체 삭제", "취소"};
            int r = JOptionPane.showOptionDialog(this, "반복 일정이에요.\n「" + s.title + "」 · " + TimeText.md(occ), "일정 삭제",
                    JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, opts, opts[0]);
            if (r == 0) { HAZscheduleManager.skipOccurrence(s, occ); return true; }
            if (r != 1) return false;
        } else {
            String extra = s.repeat != Schedule.HAZRepeat.NONE ? "\n(반복 일정이라 모든 날짜에서 지워져요)" : "";
            if (!Ui.confirm(this, "일정 삭제", "「" + s.title + "」 일정을 삭제할까요?" + extra)) return false;
        }
        HAZscheduleManager.remove(s);
        return true;
    }

    // 일정 편집 화면을 창 위에 띄움 (콤보박스·작은 달력 팝업이 가려지지 않게 팝업 층 바로 아래에)
    private void openEditor(ScheduleEditor.Mode mode, Schedule original, Schedule work, LocalDate occ) {
        if (!isVisible()) showMain();
        removeEditor();
        editor = new ScheduleEditor(this, HAZscheduleManager, HAZsettings, mode, original, work, occ);
        JLayeredPane lp = getLayeredPane();
        editor.setBounds(0, 0, lp.getWidth(), lp.getHeight());
        lp.add(editor, JLayeredPane.MODAL_LAYER);
        lp.revalidate();
        lp.repaint();
    }

    // 테스트용: 반복 일정을 '전체 수정'으로 바로 열기
    void openEditorForTest(Schedule s) { openEditor(ScheduleEditor.Mode.EDIT_ALL, s, s.copy(), null); }

    public void editorClosed() {
        removeEditor();
        getContentPane().requestFocusInWindow();
    }

    private void removeEditor() {
        if (editor == null) return;
        getLayeredPane().remove(editor);
        getLayeredPane().repaint();
        editor = null;
    }

    // 카테고리 추가: 이름 → 색
    public String askNewCategory() {
        String name = JOptionPane.showInputDialog(this, "새 카테고리 이름", "카테고리 추가", JOptionPane.PLAIN_MESSAGE);
        if (name == null || name.isBlank()) return null;
        name = name.trim();
        if (name.contains("|")) { Ui.warn(this, "| 는 쓸 수 없어요."); return null; }
        Color c = JColorChooser.showDialog(this, name + " 색 고르기", Theme.decode(ScheduleEditor.PALETTE[(int) (Math.random() * 11)], "#C9B8E8"));
        HAZscheduleManager.addCategory(name, c == null ? new Color(0xC9B8E8) : c);
        return name;
    }

    public void addDDayFor(LocalDate d) {
        DDay dd = new DDay();
        dd.date = d;
        if (DDayView.edit(this, dd, true)) HAZscheduleManager.addDDay(dd);
    }

    // ================================================================== 창 열기

    private void openSearch(String query) {
        if (searchWindow == null) searchWindow = new SearchWindow(this, HAZscheduleManager);
        searchWindow.open(query);
    }

    private void openThemeEditor() {
        if (themeEditor == null) themeEditor = new ThemeEditor(this, HAZsettings);
        themeEditor.reload();
        themeEditor.pack();
        themeEditor.setVisible(true);
    }

    private void chooseProfileImage() {
        File f = Ui.openFile(this, "프로필 사진 고르기", "png", "jpg", "jpeg", "gif", "bmp");
        if (f == null) return;
        if (Ui.image(f.getAbsolutePath()) == null) { Ui.warn(this, "이 그림 파일은 열 수 없어요. png, jpg 파일을 골라 주세요."); return; }
        HAZsettings.set("profileImage", f.getAbsolutePath());
        HAZsettings.save();
        repaint();
    }

    // 시작할 때 프로필/캐릭터 이미지 경로가 틀렸으면 다시 고르게 함
    public void checkImages() {
        for (String[] k : new String[][]{{"profileImage", "프로필 사진"}, {"characterImage", "알람 캐릭터 이미지"}}) {
            String path = HAZsettings.get(k[0]);
            if (path.isBlank() || Ui.image(path) != null) continue;
            if (Ui.confirm(this, k[1], k[1] + "을(를) 찾을 수 없어요.\n" + path + "\n\n다른 이미지를 고를까요?")) {
                File f = Ui.openFile(this, k[1] + " 고르기", "png", "jpg", "jpeg", "gif", "bmp");
                HAZsettings.set(k[0], f == null ? null : f.getAbsolutePath());
            } else HAZsettings.set(k[0], null);
            HAZsettings.save();
            repaint();
        }
    }

    // ================================================================== 가져오기 / 내보내기

    public void importIcs(Component parent) {
        if (!Ui.confirm(parent, "구글 캘린더 가져오기", "구글 캘린더 일정은 .ics 파일로 가져올 수 있어요. (인터넷 없이)\n\n"
                + "구글 캘린더 웹 → 설정 → 가져오기/내보내기 → 내보내기\n"
                + "받은 zip 파일을 풀면 나오는 .ics 파일을 골라 주세요.\n\n파일을 고를까요?")) return;
        File f = Ui.openFile(parent, ".ics 파일 고르기", "ics");
        if (f == null) return;
        try {
            List<Schedule> in = Ics.read(f.toPath());
            String cat = "가져온 일정";
            if (!HAZscheduleManager.getCategories().containsKey(cat))
                HAZscheduleManager.getCategories().put(cat, new ScheduleManager.Category(cat, new Color(0x9A8FD6)));
            for (Schedule s : in) s.category = cat;
            int added = HAZscheduleManager.importAll(in);
            Ui.info(parent, "가져오기", added + "개의 일정을 가져왔어요." + (in.size() > added ? "\n이미 있는 일정 " + (in.size() - added) + "개는 건너뛰었어요." : ""));
        } catch (Exception ex) {
            Ui.warn(parent, "파일을 읽을 수 없어요. .ics 파일이 맞는지 확인해 주세요.\n(" + ex.getMessage() + ")");
        }
    }

    public void exportIcs(Component parent) {
        File f = Ui.saveFile(parent, ".ics로 내보내기", "HazCalendar.ics", "ics");
        if (f == null) return;
        try {
            int n = Ics.write(f.toPath(), HAZscheduleManager.getAll(), HAZscheduleManager.getCategories());
            Ui.info(parent, "내보내기", n + "개의 일정을 내보냈어요.\n구글 캘린더 → 설정 → 가져오기에서 이 파일을 넣으면 돼요.");
        } catch (Exception ex) {
            Ui.warn(parent, "내보내기 실패: " + ex.getMessage());
        }
    }

    public void importDiaryCalendar(Component parent) {
        Path def = DataStore.diaryCalendarFile();
        File f = def.toFile();
        if (!f.isFile()) {
            Ui.info(parent, "DiaryCalendar", "DiaryCalendar 데이터를 기본 위치에서 찾지 못했어요.\n(" + def + ")\n\ndata.json 파일을 직접 골라 주세요.");
            f = Ui.openFile(parent, "DiaryCalendar data.json 고르기", "json");
            if (f == null) return;
        } else if (!Ui.confirm(parent, "DiaryCalendar", "DiaryCalendar 데이터를 찾았어요.\n" + def + "\n\n일정·카테고리·알람·D-Day·메모를 가져올까요?\n(지금 있는 일정은 그대로 두고 더해요)")) {
            return;
        }
        try {
            String msg = HAZdataStore.importDiaryCalendar(f.toPath());
            Ui.info(parent, "DiaryCalendar 가져오기", msg);
        } catch (Exception ex) {
            Ui.warn(parent, "가져오지 못했어요.\n(" + ex.getMessage() + ")");
        }
    }

    public void backup(Component parent) {
        try {
            Path p = HAZdataStore.backup();
            Ui.info(parent, "백업", "백업을 만들었어요.\n" + p);
        } catch (Exception ex) {
            Ui.warn(parent, "백업 실패: " + ex.getMessage());
        }
    }

    // ================================================================== 트레이 / 종료

    private void setupTray() {
        if (!SystemTray.isSupported()) return;
        PopupMenu menu = new PopupMenu();
        MenuItem open = new MenuItem("열기");
        open.addActionListener(e -> SwingUtilities.invokeLater(this::showMain));
        MenuItem add = new MenuItem("새 일정");
        add.addActionListener(e -> SwingUtilities.invokeLater(() -> { showMain(); addSchedule(LocalDate.now(), -1, -1); }));
        MenuItem memo = new MenuItem("새 메모");
        memo.addActionListener(e -> SwingUtilities.invokeLater(() -> memoManager.open(memoManager.create(null))));
        MenuItem w = new MenuItem("위젯 켜기/끄기");
        w.addActionListener(e -> SwingUtilities.invokeLater(() -> {
            HAZsettings.setBool("widgetEnabled", !HAZsettings.getBool("widgetEnabled"));
            HAZsettings.save();
            widget.apply();
        }));
        MenuItem quit = new MenuItem("종료");
        quit.addActionListener(e -> SwingUtilities.invokeLater(this::exit));
        menu.add(open);
        menu.add(add);
        menu.add(memo);
        menu.add(w);
        menu.addSeparator();
        menu.add(quit);
        trayIcon = new TrayIcon(Ui.appIcon(32), "캘린더", menu);
        trayIcon.setImageAutoSize(true);
        trayIcon.addActionListener(e -> SwingUtilities.invokeLater(this::showMain));
        try {
            SystemTray.getSystemTray().add(trayIcon);
        } catch (Exception e) {
            trayIcon = null;
        }
    }

    public void showMain() {
        setVisible(true);
        setExtendedState(getExtendedState() & ~ICONIFIED);
        toFront();
        requestFocus();
    }

    private void onClose() {
        flush();
        if (!HAZsettings.getBool("closeToTray")) { exit(); return; }
        if (trayIcon != null) {
            setVisible(false);
            if (!trayHintShown) {
                trayIcon.displayMessage("캘린더", "트레이에서 계속 알람을 챙길게요.\n완전히 끄려면 아이콘 우클릭 → 종료", TrayIcon.MessageType.INFO);
                trayHintShown = true;
            }
        } else if (Ui.confirm(this, "종료", "이 PC에서는 트레이를 쓸 수 없어서, 창을 닫으면 알람이 울리지 않아요.\n그래도 종료할까요?")) {
            exit();
        } else setExtendedState(ICONIFIED);
    }

    private void flush() {
        HAZdataStore.saveNow();
        HAZsettings.save();
    }

    public void exit() {
        flush();
        if (trayIcon != null) SystemTray.getSystemTray().remove(trayIcon);
        System.exit(0);
    }
}
