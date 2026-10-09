import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// 메모장. 메모마다 따로 창이 뜨고, 위치/크기/고정 여부를 기억함 (haz_data.json 안에 저장)
public class MemoManager {
    public static class Memo {
        public String title = "새 메모", text = "";
        public int x = -1, y = -1, w = 280, h = 260;
        public boolean pinned = false, open = false;
        public MemoWindow window;
        @Override public String toString() { return title; }
    }

    private final List<Memo> memos = new ArrayList<>();
    private final List<Runnable> listeners = new ArrayList<>();
    private Runnable saver = () -> { };

    public void setSaver(Runnable saver) { this.saver = saver; }
    public List<Memo> all() { return memos; }
    public void addListener(Runnable r) { listeners.add(r); }

    public Memo create(String text) {
        Memo m = new Memo();
        if (text != null) m.text = text;
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
        for (Memo m : new ArrayList<>(memos)) if (m.open) open(m);
    }

    public void changed() {
        save();
        notifyListeners();
    }

    public void notifyListeners() {
        for (Runnable r : listeners) r.run();
    }

    public void save() { saver.run(); }

    public void writeTo(Map<String, Object> root) {
        List<Object> list = new ArrayList<>();
        for (Memo m : memos) {
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("title", m.title);
            o.put("text", m.text);
            o.put("x", m.x);
            o.put("y", m.y);
            o.put("w", m.w);
            o.put("h", m.h);
            o.put("pinned", m.pinned);
            o.put("open", m.open);
            list.add(o);
        }
        root.put("memos", list);
    }

    public void readFrom(Map<String, Object> root) {
        memos.clear();
        for (Map<String, Object> o : Json.maps(root, "memos")) {
            Memo m = new Memo();
            m.title = Json.str(o, "title", "메모");
            m.text = Json.str(o, "text", "");
            m.x = Json.num(o, "x", -1);
            m.y = Json.num(o, "y", -1);
            m.w = Math.max(180, Json.num(o, "w", 280));
            m.h = Math.max(140, Json.num(o, "h", 260));
            m.pinned = Json.bool(o, "pinned", false);
            m.open = Json.bool(o, "open", false);
            memos.add(m);
        }
    }

    public void addLegacy(Memo m) { memos.add(m); }
}
