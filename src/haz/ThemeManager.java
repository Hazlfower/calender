package haz;

import java.awt.Color;

// 테마 커스텀 (시나리오 3)
public class ThemeManager {
    private Color backgroundColor = new Color(250, 245, 235); // 다이어리 종이 느낌
    private Color accentColor = new Color(200, 160, 120);
    private String backgroundImagePath = null;
    private String characterName = "기본 캐릭터";

    public Color getBackgroundColor() { return backgroundColor; }
    public Color getAccentColor() { return accentColor; }
    public void setBackgroundColor(Color c) { this.backgroundColor = c; }
    public void setAccentColor(Color c) { this.accentColor = c; }
    public void setBackgroundImage(String path) { this.backgroundImagePath = path; }
    public String getCharacterName() { return characterName; }

    public void savePreset(String presetName) {
        // TODO: 색/캐릭터 대사 등을 파일로 내보내기 (이미지 제외, 경로만)
    }

    public void loadPreset(String presetName) {
        // TODO: 프리셋 불러오기. 이미지 경로가 틀리면 이미지 변경 화면 띄우기
    }
}
