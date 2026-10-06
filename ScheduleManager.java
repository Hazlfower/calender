package haz;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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

    // 저장 위치: 사용자 폴더\HazCalendar\haz_schedules.dat
    private Path getSavePath() {
        return Paths.get(System.getProperty("user.home"), "HazCalendar", HAZ_SAVE_FILE);
    }

    // 한 줄에 일정 하나: 제목|시작|종료|타입|반복|메모|링크
    public void save() {
        Path path = getSavePath();
        try {
            Files.createDirectories(path.getParent());
            try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                for (Schedule s : HAZscheduleList) {
                    String line = String.join("|",
                            escape(s.getTitle()),
                            s.getStart().toString(),
                            s.getEnd().toString(),
                            escape(s.getType()),
                            s.getRepeat().name(),
                            escape(s.getMemo()),
                            escape(s.getLink()));
                    writer.write(line);
                    writer.newLine();
                }
            }
        } catch (IOException e) {
            System.err.println("일정 저장 실패: " + e.getMessage());
        }
    }

    public void load() {
        Path path = getSavePath();
        if (!Files.exists(path)) return; // 처음 실행이면 파일 없음

        HAZscheduleList.clear();
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                String[] parts = line.split("\\|", -1);
                if (parts.length < 7) continue; // 깨진 줄은 건너뜀
                try {
                    Schedule s = new Schedule(
                            unescape(parts[0]),
                            LocalDateTime.parse(parts[1]),
                            LocalDateTime.parse(parts[2]),
                            unescape(parts[3]));
                    s.setRepeat(Schedule.HAZRepeat.valueOf(parts[4]));
                    s.setMemo(unescape(parts[5]));
                    s.setLink(unescape(parts[6]));
                    HAZscheduleList.add(s);
                } catch (Exception e) {
                    System.err.println("일정 한 줄 읽기 실패: " + line);
                }
            }
        } catch (IOException e) {
            System.err.println("일정 불러오기 실패: " + e.getMessage());
        }
    }

    // 메모 안의 | 나 줄바꿈이 저장 형식을 깨지 않도록 변환
    private String escape(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\").replace("|", "\\p").replace("\n", "\\n");
    }

    private String unescape(String text) {
        if (text == null || text.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\\' && i + 1 < text.length()) {
                char next = text.charAt(++i);
                if (next == 'p') sb.append('|');
                else if (next == 'n') sb.append('\n');
                else sb.append(next);
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
