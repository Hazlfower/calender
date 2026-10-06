package haz;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

// 일정 추가/수정/삭제/저장 담당
public class ScheduleManager {
    private final List<Schedule> HAZscheduleList = new ArrayList<>();
    private final List<String> typeList = new ArrayList<>(List.of("집", "개인", "회사,학교"));
    private static final String HAZ_FORBIDDEN_CHARS = "\\/:*?\"<>|";
    private static final String HAZ_SAVE_FILE = "haz_schedules.dat";

    public boolean isValidTitle(String title) {
        if (title == null || title.isBlank()) return false;
        for (char c : HAZ_FORBIDDEN_CHARS.toCharArray()) {
            if (title.indexOf(c) >= 0) return false;
        }
        return true;
    }

    public void add(Schedule schedule) { HAZscheduleList.add(schedule); save(); }
    public void remove(Schedule schedule) { HAZscheduleList.remove(schedule); save(); }
    public List<Schedule> getAll() { return HAZscheduleList; }
    public List<String> getTypes() { return typeList; }
    public void addType(String type) { typeList.add(type); }

    public List<Schedule> getByDate(LocalDate date) {
        List<Schedule> result = new ArrayList<>();
        for (Schedule s : HAZscheduleList) {
            if (s.getStart().toLocalDate().equals(date)) result.add(s);
        }
        return result;
    }

    // "테트리스" 모드: 해당 시간이 기존 일정과 겹치는지 확인
    public boolean isTimeOccupied(LocalDateTime start, LocalDateTime end) {
        for (Schedule s : HAZscheduleList) {
            if (start.isBefore(s.getEnd()) && end.isAfter(s.getStart())) return true;
        }
        return false;
    }

    public void save() {
        // TODO: HAZ_SAVE_FILE 에 일정 목록 저장 (JSON 등)
    }

    public void load() {
        // TODO: HAZ_SAVE_FILE 에서 일정 목록 불러오기
    }
}
