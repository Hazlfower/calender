import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

// 메모 하나 = 창 하나. 맨 위 고정, 크기 조절, 닫기 가능
public class MemoWindow extends JFrame {
    private final MemoManager manager;
    private final MemoManager.Memo memo;
    private final Timer saveTimer;
    private JTextField titleField;
    private JTextArea area;

    public MemoWindow(MemoManager manager, MemoManager.Memo memo) {
        this.manager = manager;
        this.memo = memo;
        setTitle(memo.title);
        setSize(memo.w, memo.h);
        setMinimumSize(new Dimension(180, 140));
        if (memo.x >= 0) setLocation(memo.x, memo.y); else setLocationByPlatform(true);
        setAlwaysOnTop(memo.pinned);
        setDefaultCloseOperation(HIDE_ON_CLOSE);

        saveTimer = new Timer(700, e -> manager.save());   // 글 쓰는 동안 너무 자주 저장하지 않게
        saveTimer.setRepeats(false);

        restyle();

        addComponentListener(new ComponentAdapter() {
            @Override public void componentMoved(ComponentEvent e) { remember(); }
            @Override public void componentResized(ComponentEvent e) { remember(); }
        });
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) {
                memo.open = false;
                manager.save();
            }
        });
    }

    private void remember() {
        if (!isShowing()) return;
        memo.x = getX(); memo.y = getY(); memo.w = getWidth(); memo.h = getHeight();
        saveTimer.restart();
    }

    // 테마에 맞게 내용 다시 만들기
    public void restyle() {
        String text = area == null ? memo.text : area.getText();

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Theme.surface());

        JPanel bar = new JPanel(new BorderLayout(6, 0));
        bar.setBackground(Theme.primary());
        bar.setBorder(Ui.pad(6, 8, 6, 6));

        titleField = new JTextField(memo.title);
        titleField.setOpaque(false);
        titleField.setBorder(BorderFactory.createEmptyBorder());
        titleField.setForeground(Theme.onPrimary());
        titleField.setCaretColor(Theme.onPrimary());
        titleField.setFont(Ui.font(13, true));
        titleField.getDocument().addDocumentListener(onChange(() -> {
            memo.title = titleField.getText();
            setTitle(memo.title);
            manager.changed();
        }));

        Ui.FlatButton pin = new Ui.FlatButton(memo.pinned ? "고정됨" : "고정",
                memo.pinned ? Theme.accent() : Ui.blend(Theme.onPrimary(), Theme.primary(), 0.2),
                memo.pinned ? Theme.onColor(Theme.accent()) : Theme.onPrimary());
        pin.setFont(Ui.font(11, true));
        pin.setToolTipText("다른 창 위에 항상 띄우기");
        pin.addActionListener(e -> {
            memo.pinned = !memo.pinned;
            setAlwaysOnTop(memo.pinned);
            manager.save();
            restyle();
        });

        Ui.FlatButton delete = new Ui.FlatButton("삭제", Ui.blend(Theme.onPrimary(), Theme.primary(), 0.2), Theme.onPrimary());
        delete.setFont(Ui.font(11, true));
        delete.addActionListener(e -> {
            if (JOptionPane.showConfirmDialog(this, "이 메모를 삭제할까요?", "메모 삭제", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION)
                manager.delete(memo);
        });

        JPanel buttons = Ui.clear(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        buttons.add(pin);
        buttons.add(delete);
        bar.add(titleField, BorderLayout.CENTER);
        bar.add(buttons, BorderLayout.EAST);

        area = new JTextArea(text);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setFont(Ui.font(14, false));
        area.setBackground(Theme.surface());
        area.setForeground(Theme.text());
        area.setCaretColor(Theme.text());
        area.setBorder(Ui.pad(10, 12, 10, 12));
        area.getDocument().addDocumentListener(onChange(() -> {
            memo.text = area.getText();
            saveTimer.restart();
        }));

        JScrollPane sp = new JScrollPane(area);
        sp.setBorder(BorderFactory.createEmptyBorder());

        root.add(bar, BorderLayout.NORTH);
        root.add(sp, BorderLayout.CENTER);
        setContentPane(root);
        revalidate();
        repaint();
    }

    private static DocumentListener onChange(Runnable r) {
        return new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { r.run(); }
            public void removeUpdate(DocumentEvent e) { r.run(); }
            public void changedUpdate(DocumentEvent e) { r.run(); }
        };
    }
}
