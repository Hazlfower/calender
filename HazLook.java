import javax.swing.*;
import javax.swing.border.AbstractBorder;
import javax.swing.plaf.BorderUIResource;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.FontUIResource;
import javax.swing.plaf.InsetsUIResource;
import javax.swing.plaf.UIResource;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.plaf.basic.BasicCheckBoxUI;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.BasicComboPopup;
import javax.swing.plaf.basic.BasicFormattedTextFieldUI;
import javax.swing.plaf.basic.BasicGraphicsUtils;
import javax.swing.plaf.basic.BasicPasswordFieldUI;
import javax.swing.plaf.basic.BasicRadioButtonUI;
import javax.swing.plaf.basic.BasicSpinnerUI;
import javax.swing.plaf.basic.BasicTextFieldUI;
import javax.swing.plaf.basic.BasicToggleButtonUI;
import javax.swing.plaf.basic.ComboPopup;
import javax.swing.plaf.metal.DefaultMetalTheme;
import javax.swing.plaf.metal.MetalLookAndFeel;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;

// 기본 컴포넌트(버튼, 입력칸, 콤보박스, 숫자 칸, 체크박스, 확인 창 아이콘 등)를 테마 색에 맞게 그리기
// 외부 라이브러리 없이 Swing 기본 기능만 사용
public class HazLook {
    private static boolean metalSet = false;

    public static void install() {
        try {
            if (!metalSet) {
                MetalLookAndFeel.setCurrentTheme(new DefaultMetalTheme());
                UIManager.setLookAndFeel(new MetalLookAndFeel());
                metalSet = true;
            }
        } catch (Exception ignored) { }

        ColorUIResource surface = c(Theme.surface()), field = c(Theme.field()), text = c(Theme.text()), sub = c(Theme.sub());
        ColorUIResource sel = c(Ui.blend(Theme.highlight(), Theme.surface(), Theme.isNight() ? 0.45 : 0.22));
        ColorUIResource textSel = c(Ui.blend(Theme.highlight(), Theme.surface(), 0.35));

        for (String k : new String[]{"Panel.background", "OptionPane.background", "CheckBox.background", "RadioButton.background",
                "ScrollPane.background", "Viewport.background", "ColorChooser.background", "Spinner.background",
                "Button.background", "ToggleButton.background", "Label.background", "List.background", "Table.background",
                "TabbedPane.background", "TabbedPane.contentAreaColor", "TabbedPane.selected", "Slider.background",
                "Separator.background", "Tree.background", "SplitPane.background"})
            UIManager.put(k, surface);
        for (String k : new String[]{"TextField.background", "FormattedTextField.background", "PasswordField.background",
                "TextArea.background", "ComboBox.background", "EditorPane.background", "TextPane.background"})
            UIManager.put(k, field);
        for (String k : new String[]{"Label.foreground", "CheckBox.foreground", "RadioButton.foreground", "Button.foreground",
                "ToggleButton.foreground", "TextField.foreground", "FormattedTextField.foreground", "PasswordField.foreground",
                "TextArea.foreground", "List.foreground", "ComboBox.foreground", "OptionPane.messageForeground",
                "TitledBorder.titleColor", "TabbedPane.foreground", "ColorChooser.foreground", "Slider.foreground",
                "TextField.caretForeground", "FormattedTextField.caretForeground", "PasswordField.caretForeground",
                "TextArea.caretForeground", "EditorPane.foreground", "TextPane.foreground", "Table.foreground"})
            UIManager.put(k, text);
        for (String k : new String[]{"TextField.inactiveForeground", "FormattedTextField.inactiveForeground",
                "TextArea.inactiveForeground", "Label.disabledForeground", "Button.disabledText", "CheckBox.disabledText",
                "RadioButton.disabledText", "ComboBox.disabledForeground"})
            UIManager.put(k, sub);
        UIManager.put("ComboBox.disabledBackground", field);
        UIManager.put("TextField.inactiveBackground", field);
        UIManager.put("FormattedTextField.inactiveBackground", field);
        for (String k : new String[]{"List.selectionBackground", "ComboBox.selectionBackground", "Table.selectionBackground"})
            UIManager.put(k, sel);
        for (String k : new String[]{"List.selectionForeground", "ComboBox.selectionForeground", "Table.selectionForeground"})
            UIManager.put(k, c(Theme.onColor(sel)));
        for (String k : new String[]{"TextField.selectionBackground", "FormattedTextField.selectionBackground",
                "TextArea.selectionBackground", "PasswordField.selectionBackground", "EditorPane.selectionBackground"})
            UIManager.put(k, textSel);
        for (String k : new String[]{"TextField.selectionForeground", "FormattedTextField.selectionForeground",
                "TextArea.selectionForeground", "PasswordField.selectionForeground", "EditorPane.selectionForeground"})
            UIManager.put(k, c(Theme.onColor(textSel)));

        // 메뉴 / 툴팁
        Color popupBg = Theme.isNight() ? Ui.blend(Color.WHITE, Theme.surface(), 0.06) : Theme.surface();
        UIManager.put("PopupMenu.background", c(popupBg));
        UIManager.put("PopupMenu.border", new BorderUIResource(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.border()), Ui.pad(4, 0, 4, 0))));
        UIManager.put("MenuItem.background", c(popupBg));
        UIManager.put("MenuItem.foreground", text);
        UIManager.put("MenuItem.selectionBackground", sel);
        UIManager.put("MenuItem.selectionForeground", c(Theme.onColor(sel)));
        UIManager.put("MenuItem.acceleratorForeground", sub);
        UIManager.put("MenuItem.acceleratorSelectionForeground", sub);
        UIManager.put("MenuItem.border", new BorderUIResource(Ui.pad(6, 6, 6, 16)));
        UIManager.put("Separator.foreground", c(Theme.border()));
        UIManager.put("PopupMenuSeparator.foreground", c(Theme.border()));
        UIManager.put("PopupMenuSeparator.background", c(popupBg));
        Color tipBg = Theme.isNight() ? Ui.blend(Color.WHITE, Theme.surface(), 0.14) : Ui.blend(Color.BLACK, Theme.text(), 0.1);
        UIManager.put("ToolTip.background", c(tipBg));
        UIManager.put("ToolTip.foreground", c(Theme.onColor(tipBg)));
        UIManager.put("ToolTip.border", new BorderUIResource(Ui.pad(5, 9, 5, 9)));

        // 탭(색 고르기 창 안)
        UIManager.put("TabbedPane.light", c(Theme.border()));
        UIManager.put("TabbedPane.highlight", c(Theme.border()));
        UIManager.put("TabbedPane.shadow", c(Theme.border()));
        UIManager.put("TabbedPane.darkShadow", c(Theme.border()));
        UIManager.put("TabbedPane.focus", surface);
        UIManager.put("TabbedPane.unselectedBackground", c(Ui.blend(Theme.bg(), Theme.surface(), 0.6)));
        UIManager.put("TabbedPane.tabAreaBackground", surface);
        UIManager.put("TabbedPane.borderHightlightColor", c(Theme.border()));

        // 스크롤바
        UIManager.put("ScrollBarUI", "ThinScrollBarUI");
        UIManager.put("ScrollBar.width", 11);
        UIManager.put("ScrollBar.background", surface);
        UIManager.put("ScrollPane.border", new BorderUIResource(BorderFactory.createEmptyBorder()));
        UIManager.put("TitledBorder.border", new BorderUIResource(BorderFactory.createLineBorder(Theme.border())));

        // 직접 그리는 컴포넌트
        UIManager.put("ButtonUI", ButtonUI.class.getName());
        UIManager.put("ToggleButtonUI", ToggleButtonUI.class.getName());
        UIManager.put("ComboBoxUI", ComboUI.class.getName());
        UIManager.put("SpinnerUI", SpinnerUI.class.getName());
        UIManager.put("TextFieldUI", TextFieldUI.class.getName());
        UIManager.put("FormattedTextFieldUI", FormattedTextFieldUI.class.getName());
        UIManager.put("PasswordFieldUI", PasswordFieldUI.class.getName());
        UIManager.put("CheckBoxUI", CheckBoxUI.class.getName());
        UIManager.put("RadioButtonUI", RadioButtonUI.class.getName());
        UIManager.put("Button.border", new BorderUIResource(Ui.pad(7, 16, 7, 16)));
        UIManager.put("ToggleButton.border", new BorderUIResource(Ui.pad(6, 12, 6, 12)));
        UIManager.put("Button.margin", new InsetsUIResource(7, 16, 7, 16));
        UIManager.put("CheckBox.icon", new CheckIcon(false));
        UIManager.put("RadioButton.icon", new CheckIcon(true));
        UIManager.put("CheckBox.border", new BorderUIResource(Ui.pad(3, 2, 3, 6)));
        UIManager.put("RadioButton.border", new BorderUIResource(Ui.pad(3, 2, 3, 6)));
        UIManager.put("TextArea.border", new BorderUIResource(Ui.pad(6, 8, 6, 8)));
        UIManager.put("OptionPane.informationIcon", new MessageIcon('i'));
        UIManager.put("OptionPane.questionIcon", new MessageIcon('?'));
        UIManager.put("OptionPane.warningIcon", new MessageIcon('!'));
        UIManager.put("OptionPane.errorIcon", new MessageIcon('x'));

        // 글꼴
        FontUIResource f = new FontUIResource(Ui.font(13, false));
        for (String k : new String[]{"Label.font", "Button.font", "ToggleButton.font", "CheckBox.font", "RadioButton.font",
                "TextField.font", "FormattedTextField.font", "PasswordField.font", "TextArea.font", "List.font",
                "ComboBox.font", "Spinner.font", "OptionPane.messageFont", "ToolTip.font", "MenuItem.font", "Menu.font",
                "PopupMenu.font", "TitledBorder.font", "TabbedPane.font", "ColorChooser.font", "Slider.font",
                "Table.font", "TableHeader.font", "EditorPane.font", "TextPane.font"})
            UIManager.put(k, f);
        UIManager.put("OptionPane.buttonFont", new FontUIResource(Ui.font(13, true)));
        UIManager.put("ToolTip.font", new FontUIResource(Ui.font(12, false)));
    }

    private static ColorUIResource c(Color col) { return new ColorUIResource(col); }

    // 콤보박스/숫자 칸 안에 들어간 글 칸은 따로 바탕을 그리지 않음
    static boolean embedded(Component c) {
        Container p = c.getParent();
        return p instanceof JComboBox || p instanceof JSpinner.DefaultEditor || (c instanceof JComponent
                && Boolean.TRUE.equals(((JComponent) c).getClientProperty("haz.plain")));
    }

    static int fieldArc() { return Theme.style() == Theme.Style.SF ? 2 : 10; }

    static boolean focused(Component c) {
        if (c == null) return false;
        if (c.hasFocus()) return true;
        if (c instanceof JComboBox) {
            Component ed = ((JComboBox<?>) c).getEditor().getEditorComponent();
            return ((JComboBox<?>) c).isEditable() && ed != null && ed.hasFocus();
        }
        if (c instanceof JSpinner) {
            JComponent ed = ((JSpinner) c).getEditor();
            return ed instanceof JSpinner.DefaultEditor && ((JSpinner.DefaultEditor) ed).getTextField().hasFocus();
        }
        return false;
    }

    static void paintField(Graphics g, Component c, int w, int h) {
        Graphics2D g2 = Ui.aa(g);
        int r = fieldArc();
        RoundRectangle2D rr = new RoundRectangle2D.Float(0.5f, 0.5f, w - 1, h - 1, r, r);
        g2.setColor(c.isEnabled() ? Theme.field() : Ui.blend(Theme.field(), Theme.surface(), 0.5));
        g2.fill(rr);
        g2.dispose();
    }

    // ================================================================== 입력칸 테두리

    public static class FieldBorder extends AbstractBorder implements UIResource {
        private final Component focusOwner;
        private final Insets insets;

        public FieldBorder(Component focusOwner) { this(focusOwner, new Insets(6, 10, 6, 10)); }

        public FieldBorder(Component focusOwner, Insets insets) {
            this.focusOwner = focusOwner;
            this.insets = insets;
        }

        @Override public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
            Graphics2D g2 = Ui.aa(g);
            int r = fieldArc();
            boolean focus = focused(focusOwner == null ? c : focusOwner);
            g2.setColor(focus ? Theme.highlight() : Theme.border());
            g2.setStroke(new BasicStroke(focus ? 1.6f : 1f));
            g2.draw(new RoundRectangle2D.Float(x + 0.8f, y + 0.8f, w - 1.6f, h - 1.6f, r, r));
            g2.dispose();
        }

        @Override public Insets getBorderInsets(Component c) { return new Insets(insets.top, insets.left, insets.bottom, insets.right); }

        @Override public Insets getBorderInsets(Component c, Insets i) {
            i.set(insets.top, insets.left, insets.bottom, insets.right);
            return i;
        }
    }

    // ================================================================== 버튼

    public static class ButtonUI extends BasicButtonUI {
        public static ComponentUI createUI(JComponent c) { return new ButtonUI(); }

        @Override protected void installDefaults(AbstractButton b) {
            super.installDefaults(b);
            b.setOpaque(false);
            b.setRolloverEnabled(true);
        }

        static boolean primary(AbstractButton b) {
            return (b instanceof JButton && ((JButton) b).isDefaultButton()) || Boolean.TRUE.equals(b.getClientProperty("haz.primary"));
        }

        @Override public void paint(Graphics g, JComponent c) {
            AbstractButton b = (AbstractButton) c;
            if (b.isContentAreaFilled()) paintBack(g, b, primary(b), false);
            super.paint(g, c);
        }

        static void paintBack(Graphics g, AbstractButton b, boolean primary, boolean selected) {
            Graphics2D g2 = Ui.aa(g);
            ButtonModel m = b.getModel();
            Color base = primary || selected ? Theme.highlight()
                    : Theme.isNight() ? Ui.blend(Color.WHITE, Theme.surface(), 0.08) : Ui.blend(Theme.bg(), Theme.surface(), 0.85);
            Color over = Theme.lum(base) > 140 ? Color.BLACK : Color.WHITE;
            Color fill = m.isPressed() ? Ui.blend(over, base, 0.16) : m.isRollover() ? Ui.blend(over, base, 0.08) : base;
            if (!b.isEnabled()) fill = Ui.blend(Theme.surface(), fill, 0.5);
            int r = Theme.style() == Theme.Style.SF ? 2 : 10;
            RoundRectangle2D rr = new RoundRectangle2D.Float(0.5f, 0.5f, b.getWidth() - 1, b.getHeight() - 1, r, r);
            g2.setColor(fill);
            g2.fill(rr);
            if (!primary && !selected) {
                g2.setColor(Theme.border());
                g2.draw(rr);
            }
            g2.dispose();
        }

        @Override protected void paintText(Graphics g, AbstractButton b, Rectangle textRect, String text) {
            Ui.smooth(g);
            Color fg = !b.isEnabled() ? Theme.sub() : primary(b) ? Theme.onColor(Theme.highlight()) : Theme.text();
            g.setColor(fg);
            g.setFont(b.getFont());
            FontMetrics fm = b.getFontMetrics(b.getFont());
            BasicGraphicsUtils.drawStringUnderlineCharAt(b, (Graphics2D) g, text, -1, textRect.x, textRect.y + fm.getAscent());
        }

        @Override protected void paintFocus(Graphics g, AbstractButton b, Rectangle v, Rectangle t, Rectangle i) { }
        @Override protected void paintButtonPressed(Graphics g, AbstractButton b) { }
    }

    public static class ToggleButtonUI extends BasicToggleButtonUI {
        public static ComponentUI createUI(JComponent c) { return new ToggleButtonUI(); }

        @Override protected void installDefaults(AbstractButton b) {
            super.installDefaults(b);
            b.setOpaque(false);
            b.setRolloverEnabled(true);
        }

        @Override public void paint(Graphics g, JComponent c) {
            AbstractButton b = (AbstractButton) c;
            if (b.isContentAreaFilled()) ButtonUI.paintBack(g, b, false, b.isSelected());
            super.paint(g, c);
        }

        @Override protected void paintText(Graphics g, AbstractButton b, Rectangle textRect, String text) {
            Ui.smooth(g);
            g.setColor(!b.isEnabled() ? Theme.sub() : b.isSelected() ? Theme.onColor(Theme.highlight()) : Theme.text());
            g.setFont(b.getFont());
            BasicGraphicsUtils.drawStringUnderlineCharAt(b, (Graphics2D) g, text, -1, textRect.x, textRect.y + b.getFontMetrics(b.getFont()).getAscent());
        }

        @Override protected void paintFocus(Graphics g, AbstractButton b, Rectangle v, Rectangle t, Rectangle i) { }
        @Override protected void paintButtonPressed(Graphics g, AbstractButton b) { }
    }

    // ================================================================== 체크박스 / 라디오

    public static class CheckBoxUI extends BasicCheckBoxUI {
        public static ComponentUI createUI(JComponent c) { return new CheckBoxUI(); }

        @Override protected void installDefaults(AbstractButton b) {
            super.installDefaults(b);
            b.setOpaque(false);
            b.setRolloverEnabled(true);
            b.setIconTextGap(7);
        }

        @Override public void paint(Graphics g, JComponent c) { Ui.smooth(g); super.paint(g, c); }
        @Override protected void paintFocus(Graphics g, Rectangle t, Dimension d) { }
    }

    public static class RadioButtonUI extends BasicRadioButtonUI {
        public static ComponentUI createUI(JComponent c) { return new RadioButtonUI(); }

        @Override protected void installDefaults(AbstractButton b) {
            super.installDefaults(b);
            b.setOpaque(false);
            b.setRolloverEnabled(true);
            b.setIconTextGap(7);
        }

        @Override public void paint(Graphics g, JComponent c) { Ui.smooth(g); super.paint(g, c); }
        @Override protected void paintFocus(Graphics g, Rectangle t, Dimension d) { }
    }

    public static class CheckIcon implements Icon, UIResource {
        private final boolean radio;

        CheckIcon(boolean radio) { this.radio = radio; }

        public int getIconWidth() { return 17; }
        public int getIconHeight() { return 17; }

        public void paintIcon(Component c, Graphics g, int x, int y) {
            ButtonModel m = ((AbstractButton) c).getModel();
            Graphics2D g2 = Ui.aa(g);
            g2.translate(x, y);
            boolean on = m.isSelected();
            Color hl = Theme.highlight();
            Shape box = radio ? new Ellipse2D.Float(0.5f, 0.5f, 16, 16) : new RoundRectangle2D.Float(0.5f, 0.5f, 16, 16, 6, 6);
            if (Theme.style() == Theme.Style.SF && !radio) box = new RoundRectangle2D.Float(0.5f, 0.5f, 16, 16, 2, 2);
            g2.setColor(on ? hl : Theme.field());
            g2.fill(box);
            g2.setColor(on ? hl : m.isRollover() ? Ui.blend(hl, Theme.border(), 0.5) : Ui.blend(Theme.sub(), Theme.border(), 0.45));
            g2.setStroke(new BasicStroke(1.3f));
            g2.draw(box);
            if (on) {
                g2.setColor(Theme.onColor(hl));
                if (radio) g2.fill(new Ellipse2D.Float(5, 5, 7, 7));
                else {
                    g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    Path2D p = new Path2D.Float();
                    p.moveTo(4.2, 8.8);
                    p.lineTo(7.2, 11.7);
                    p.lineTo(12.6, 5.4);
                    g2.draw(p);
                }
            }
            if (!c.isEnabled()) {
                g2.setColor(Ui.alpha(Theme.surface(), 140));
                g2.fill(box);
            }
            g2.dispose();
        }
    }

    // ================================================================== 확인 창 아이콘

    public static class MessageIcon implements Icon, UIResource {
        private final char kind;

        MessageIcon(char kind) { this.kind = kind; }

        public int getIconWidth() { return 38; }
        public int getIconHeight() { return 38; }

        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = Ui.aa(g);
            Color col = kind == '!' ? new Color(0xF0A43A) : kind == 'x' ? Theme.danger() : Theme.highlight();
            g2.setColor(Ui.alpha(col, 40));
            g2.fill(new Ellipse2D.Float(x, y, 38, 38));
            g2.setColor(col);
            g2.fill(new Ellipse2D.Float(x + 6, y + 6, 26, 26));
            g2.setColor(Theme.onColor(col));
            g2.setStroke(new BasicStroke(2.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            int cx = x + 19, cy = y + 19;
            switch (kind) {
                case 'i':
                    g2.fill(new Ellipse2D.Float(cx - 1.8f, cy - 8.5f, 3.6f, 3.6f));
                    g2.drawLine(cx, cy - 1, cx, cy + 7);
                    break;
                case '!':
                    g2.drawLine(cx, cy - 8, cx, cy + 2);
                    g2.fill(new Ellipse2D.Float(cx - 1.8f, cy + 5, 3.6f, 3.6f));
                    break;
                case '?':
                    g2.setFont(Ui.font(19, true));
                    Ui.centerText(g2, "?", cx, cy + 7);
                    break;
                default:
                    g2.drawLine(cx - 6, cy - 6, cx + 6, cy + 6);
                    g2.drawLine(cx + 6, cy - 6, cx - 6, cy + 6);
            }
            g2.dispose();
        }
    }

    // ================================================================== 글 입력칸

    static void installField(JTextComponent c) {
        if (embedded(c)) return;
        c.setOpaque(false);
        if (c.getBorder() == null || c.getBorder() instanceof UIResource) c.setBorder(new FieldBorder(c));
        c.addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent e) { c.repaint(); }
            @Override public void focusLost(FocusEvent e) { c.repaint(); }
        });
    }

    static void paintPlaceholder(Graphics g, JTextComponent c) {
        Object ph = c.getClientProperty("placeholder");
        if (ph == null || !c.getText().isEmpty()) return;
        Graphics2D g2 = Ui.aa(g);
        Insets in = c.getInsets();
        g2.setFont(c.getFont());
        g2.setColor(Ui.alpha(Theme.sub(), 190));
        FontMetrics fm = g2.getFontMetrics();
        int y = c instanceof JTextField ? (c.getHeight() - fm.getHeight()) / 2 + fm.getAscent() : in.top + fm.getAscent();
        g2.drawString(ph.toString(), in.left + 1, y);
        g2.dispose();
    }

    public static class TextFieldUI extends BasicTextFieldUI {
        public static ComponentUI createUI(JComponent c) { return new TextFieldUI(); }

        @Override protected void installDefaults() { super.installDefaults(); installField(getComponent()); }

        @Override protected void paintSafely(Graphics g) {
            JTextComponent c = getComponent();
            if (!embedded(c) && !c.isOpaque()) paintField(g, c, c.getWidth(), c.getHeight());
            Ui.smooth(g);
            super.paintSafely(g);
            paintPlaceholder(g, c);
        }
    }

    public static class FormattedTextFieldUI extends BasicFormattedTextFieldUI {
        public static ComponentUI createUI(JComponent c) { return new FormattedTextFieldUI(); }

        @Override protected void installDefaults() { super.installDefaults(); installField(getComponent()); }

        @Override protected void paintSafely(Graphics g) {
            JTextComponent c = getComponent();
            if (!embedded(c) && !c.isOpaque()) paintField(g, c, c.getWidth(), c.getHeight());
            Ui.smooth(g);
            super.paintSafely(g);
        }
    }

    public static class PasswordFieldUI extends BasicPasswordFieldUI {
        public static ComponentUI createUI(JComponent c) { return new PasswordFieldUI(); }

        @Override protected void installDefaults() { super.installDefaults(); installField(getComponent()); }

        @Override protected void paintSafely(Graphics g) {
            JTextComponent c = getComponent();
            if (!embedded(c) && !c.isOpaque()) paintField(g, c, c.getWidth(), c.getHeight());
            super.paintSafely(g);
        }
    }

    // ================================================================== 콤보박스

    public static class ComboUI extends BasicComboBoxUI {
        public static ComponentUI createUI(JComponent c) { return new ComboUI(); }

        @Override public void installUI(JComponent c) {
            super.installUI(c);
            comboBox.setOpaque(false);
            comboBox.setBorder(new FieldBorder(comboBox, new Insets(3, 8, 3, 4)));
            comboBox.setMaximumRowCount(10);
        }

        @Override protected JButton createArrowButton() {
            JButton b = new JButton() {
                @Override protected void paintComponent(Graphics g) {
                    new Icons(Icons.Kind.DOWN, 13, comboBox.isEnabled() ? Theme.sub() : Ui.alpha(Theme.sub(), 110))
                            .paintIcon(this, g, (getWidth() - 13) / 2, (getHeight() - 13) / 2);
                }
            };
            b.setName("ComboBox.arrowButton");
            b.setContentAreaFilled(false);
            b.setBorderPainted(false);
            b.setFocusable(false);
            b.setOpaque(false);
            b.setBorder(BorderFactory.createEmptyBorder());
            b.setPreferredSize(new Dimension(22, 20));
            return b;
        }

        @Override public void paint(Graphics g, JComponent c) {
            paintField(g, c, c.getWidth(), c.getHeight());
            if (!comboBox.isEditable()) paintCurrentValue(g, rectangleForCurrentValue(), false);
        }

        @Override public void paintCurrentValueBackground(Graphics g, Rectangle bounds, boolean hasFocus) { }

        @Override public void paintCurrentValue(Graphics g, Rectangle bounds, boolean hasFocus) {
            @SuppressWarnings("unchecked")
            ListCellRenderer<Object> r = (ListCellRenderer<Object>) comboBox.getRenderer();
            Component comp = r.getListCellRendererComponent(listBox, comboBox.getSelectedItem(), -1, false, false);
            comp.setFont(comboBox.getFont());
            comp.setForeground(comboBox.isEnabled() ? Theme.text() : Theme.sub());
            boolean wasOpaque = comp instanceof JComponent && comp.isOpaque();
            if (comp instanceof JComponent) ((JComponent) comp).setOpaque(false);
            Ui.smooth(g);
            currentValuePane.paintComponent(g, comp, comboBox, bounds.x, bounds.y, bounds.width, bounds.height, false);
            if (wasOpaque) ((JComponent) comp).setOpaque(true);
        }

        @Override protected ComboPopup createPopup() {
            return new BasicComboPopup(comboBox) {
                @Override protected void configurePopup() {
                    super.configurePopup();
                    setBorder(BorderFactory.createLineBorder(Theme.border()));
                }

                @Override protected void configureList() {
                    super.configureList();
                    list.setBackground(Theme.isNight() ? Ui.blend(Color.WHITE, Theme.surface(), 0.06) : Theme.surface());
                    list.setSelectionBackground(Ui.blend(Theme.highlight(), Theme.surface(), Theme.isNight() ? 0.45 : 0.22));
                    list.setSelectionForeground(Theme.text());
                }

                @Override protected JScrollPane createScroller() {
                    JScrollPane sp = super.createScroller();
                    sp.setBorder(BorderFactory.createEmptyBorder());
                    return sp;
                }
            };
        }

        @Override protected ComboBoxEditor createEditor() {
            ComboBoxEditor ed = super.createEditor();
            Component c = ed.getEditorComponent();
            if (c instanceof JTextField) {
                JTextField tf = (JTextField) c;
                tf.setOpaque(false);
                tf.setBorder(Ui.pad(2, 2, 2, 2));
                tf.addFocusListener(new FocusAdapter() {
                    @Override public void focusGained(FocusEvent e) { comboBox.repaint(); }
                    @Override public void focusLost(FocusEvent e) { comboBox.repaint(); }
                });
            }
            return ed;
        }
    }

    // ================================================================== 숫자 칸 (스피너)

    public static class SpinnerUI extends BasicSpinnerUI {
        public static ComponentUI createUI(JComponent c) { return new SpinnerUI(); }

        @Override protected void installDefaults() {
            super.installDefaults();
            spinner.setOpaque(false);
            spinner.setBorder(new FieldBorder(spinner, new Insets(3, 6, 3, 3)));
        }

        @Override protected Component createNextButton() {
            JButton b = arrow(true);
            installNextButtonListeners(b);
            return b;
        }

        @Override protected Component createPreviousButton() {
            JButton b = arrow(false);
            installPreviousButtonListeners(b);
            return b;
        }

        private JButton arrow(boolean up) {
            JButton b = new JButton() {
                @Override protected void paintComponent(Graphics g) {
                    Graphics2D g2 = Ui.aa(g);
                    if (getModel().isRollover()) {
                        g2.setColor(Ui.alpha(Theme.text(), 20));
                        g2.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
                    }
                    new Icons(up ? Icons.Kind.UP : Icons.Kind.DOWN, 11, Theme.sub())
                            .paintIcon(this, g2, (getWidth() - 11) / 2, (getHeight() - 11) / 2);
                    g2.dispose();
                }
            };
            b.setName(up ? "Spinner.nextButton" : "Spinner.previousButton");
            b.setContentAreaFilled(false);
            b.setBorderPainted(false);
            b.setFocusable(false);
            b.setOpaque(false);
            b.setRolloverEnabled(true);
            b.setBorder(BorderFactory.createEmptyBorder());
            b.setPreferredSize(new Dimension(18, 11));
            return b;
        }

        @Override protected JComponent createEditor() {
            JComponent e = super.createEditor();
            e.setOpaque(false);
            if (e instanceof JSpinner.DefaultEditor) {
                JFormattedTextField tf = ((JSpinner.DefaultEditor) e).getTextField();
                tf.setOpaque(false);
                tf.setBorder(Ui.pad(1, 2, 1, 2));
                tf.addFocusListener(new FocusAdapter() {
                    @Override public void focusGained(FocusEvent ev) { spinner.repaint(); }
                    @Override public void focusLost(FocusEvent ev) { spinner.repaint(); }
                });
            }
            return e;
        }

        @Override public void paint(Graphics g, JComponent c) {
            paintField(g, c, c.getWidth(), c.getHeight());
            super.paint(g, c);
        }
    }
}
