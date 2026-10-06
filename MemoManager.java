import java.util.ArrayList;
import java.util.List;

// 메모장 관리. 메모마다 따로 창이 뜨고, 위치/크기/고정 여부를 기억함 (haz_memos.dat)
public class MemoManager {
    public static class Memo {
        public String title = "새 메모", text = "";
        public int x = -1, y = -1, w = 260, h = 240;
        public boolean pinned = false, open = false;
        public MemoWindow window;
        @Override public String toString() { return title; }
    }

    private static final String HAZ_MEMO_FILE = "haz_memos.dat";
    private final List<Memo> memos = new ArrayList<>();
    private final List<Runnable> listeners = new ArrayList<>();

    public List<Memo> all() { return memos; }
    public void addListener(Runnable r) { listeners.add(r); }

    public Memo create() {
        Memo m = new Memo();
        memos.add(m);
        changed();
        return m;
    }

    public void delete(Memo m) {
        if (m.window != null) m.window.dispose();
        memos.remove(m);
        changed();
    }

    public void open(Memo m) {
        if (m.window == null) m.window = new MemoWindow(this, m);
        m.open = true;
        m.window.setVisible(true);
        m.window.toFront();
        save();
    }

    // 테마가 바뀌면 열려 있는 메모 창 다시 칠하기
    public void restyleAll() {
        for (Memo m : memos) if (m.window != null) m.window.restyle();
    }

    public void openSavedWindows() {
        for (Memo m : memos) if (m.open) open(m);
    }

    public void changed() {
        save();
        for (Runnable r : listeners) r.run();
    }

    public void save() {
        List<String> lines = new ArrayList<>();
        for (Memo m : memos) {
            lines.add(Store.join(Store.escape(m.title), Store.escape(m.text), String.valueOf(m.x), String.valueOf(m.y),
                    String.valueOf(m.w), String.valueOf(m.h), m.pinned ? "1" : "0", m.open ? "1" : "0"));
        }
        Store.writeLines(HAZ_MEMO_FILE, lines);
    }

    public void load() {
        memos.clear();
        for (String[] p : Store.readLines(HAZ_MEMO_FILE)) {
            if (p.length < 8) continue;
            try {
                Memo m = new Memo();
                m.title = Store.unescape(p[0]) == null ? "메모" : Store.unescape(p[0]);
                m.text = Store.unescape(p[1]) == null ? "" : Store.unescape(p[1]);
                m.x = Integer.parseInt(p[2]); m.y = Integer.parseInt(p[3]);
                m.w = Integer.parseInt(p[4]); m.h = Integer.parseInt(p[5]);
                m.pinned = p[6].equals("1"); m.open = p[7].equals("1");
                memos.add(m);
            } catch (Exception ignored) { }
        }
    }
}
