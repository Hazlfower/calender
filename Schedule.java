
import java.time.LocalDateTime;

// 일정 데이터 (시나리오 1)
public class Schedule {
    public enum HAZRepeat { NONE, WEEKLY, MONTHLY, YEARLY }

    private String title;
    private LocalDateTime start;
    private LocalDateTime end;
    private String type;      // 집 / 개인 / 회사,학교 / 사용자 추가 타입
    private HAZRepeat repeat = HAZRepeat.NONE;
    private String memo;      // 메모
    private String link;      // 링크 (예: 티켓팅 사이트)

    public Schedule(String title, LocalDateTime start, LocalDateTime end, String type) {
        this.title = title;
        this.start = start;
        this.end = end;
        this.type = type;
    }
    
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public void setStart(LocalDateTime start) { this.start = start; }
    public void setEnd(LocalDateTime end) { this.end = end; }
    public void setType(String type) { this.type = type; }
    public LocalDateTime getStart() { return start; }
    public LocalDateTime getEnd() { return end; }
    public String getType() { return type; }
    public HAZRepeat getRepeat() { return repeat; }
    public void setRepeat(HAZRepeat repeat) { this.repeat = repeat; }
    public String getMemo() { return memo; }
    public void setMemo(String memo) { this.memo = memo; }
    public String getLink() { return link; }
    public void setLink(String link) { this.link = link; }

    @Override
    public String toString() {
        return "[" + type + "] " + title + " (" + start.toLocalTime() + "~" + end.toLocalTime() + ")";
    }
}
