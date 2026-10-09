import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// 할 일 목록 (haz_data.json 안에 저장)
public class Todos {
    public static class Item {
        public String text;
        public boolean done;
        Item(String text, boolean done) { this.text = text; this.done = done; }
        @Override public String toString() { return text; }
    }

    private final List<Item> items = new ArrayList<>();
    private Runnable saver = () -> { };

    public void setSaver(Runnable saver) { this.saver = saver; }
    public List<Item> all() { return items; }

    public void add(String text) {
        if (text == null || text.isBlank()) return;
        items.add(new Item(text.trim(), false));
        saver.run();
    }

    public void remove(Item item) { items.remove(item); saver.run(); }
    public void toggle(Item item) { item.done = !item.done; saver.run(); }

    public void edit(Item item, String text) {
        if (text == null || text.isBlank()) return;
        item.text = text.trim();
        saver.run();
    }

    // 다 한 일 지우기
    public int clearDone() {
        int before = items.size();
        items.removeIf(i -> i.done);
        saver.run();
        return before - items.size();
    }

    public void writeTo(Map<String, Object> root) {
        List<Object> list = new ArrayList<>();
        for (Item i : items) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("text", i.text);
            m.put("done", i.done);
            list.add(m);
        }
        root.put("todos", list);
    }

    public void readFrom(Map<String, Object> root) {
        items.clear();
        for (Map<String, Object> m : Json.maps(root, "todos")) {
            String t = Json.str(m, "text", "");
            if (!t.isBlank()) items.add(new Item(t, Json.bool(m, "done", false)));
        }
    }

    // 예전 haz_todos.dat 한 줄 (완료|글)
    public void addLegacy(String text, boolean done) {
        if (text != null && !text.isBlank()) items.add(new Item(text, done));
    }
}
