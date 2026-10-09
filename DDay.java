import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

// D-Day 하나. active 를 켠 것만 오른쪽 패널/위젯에 보여요
public class DDay {
    public String id = UUID.randomUUID().toString();
    public String name = "";
    public LocalDate date = LocalDate.now();
    public boolean active = true;

    public long daysLeft() { return ChronoUnit.DAYS.between(LocalDate.now(), date); }

    public String label() {
        long n = daysLeft();
        return n == 0 ? "D-DAY" : n > 0 ? "D-" + n : "D+" + (-n);
    }

    @Override public String toString() { return name; }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("name", name);
        m.put("date", date.toString());
        m.put("active", active);
        return m;
    }

    public static DDay fromMap(Map<String, Object> m) {
        DDay d = new DDay();
        d.id = Json.str(m, "id", d.id);
        d.name = Json.str(m, "name", "");
        d.date = LocalDate.parse(Json.str(m, "date", LocalDate.now().toString()));
        d.active = Json.bool(m, "active", true);
        return d;
    }
}
