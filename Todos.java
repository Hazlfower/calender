import java.util.ArrayList;
import java.util.List;

// 할 일 목록 (haz_todos.dat)
public class Todos {
    public static class Item {
        public String text;
        public boolean done;
        Item(String text, boolean done) { this.text = text; this.done = done; }
        @Override public String toString() { return text; }
    }

    private static final String HAZ_TODO_FILE = "haz_todos.dat";
    private final List<Item> items = new ArrayList<>();

    public List<Item> all() { return items; }
    public void add(String text) { if (text != null && !text.isBlank()) { items.add(new Item(text.trim(), false)); save(); } }
    public void remove(Item item) { items.remove(item); save(); }
    public void toggle(Item item) { item.done = !item.done; save(); }

    public void save() {
        List<String> lines = new ArrayList<>();
        for (Item i : items) lines.add(Store.join(i.done ? "1" : "0", Store.escape(i.text)));
        Store.writeLines(HAZ_TODO_FILE, lines);
    }

    public void load() {
        items.clear();
        for (String[] p : Store.readLines(HAZ_TODO_FILE)) {
            if (p.length >= 2) items.add(new Item(Store.unescape(p[1]), p[0].equals("1")));
        }
    }
}
