import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;

// 윈도우를 켤 때 자동으로 시작 (시작 프로그램 폴더에 바로가기를 만듦, 창 없이 트레이로 시작)
public final class Startup {
    private Startup() { }

    public static boolean supported() {
        return System.getProperty("os.name", "").toLowerCase().startsWith("windows") && System.getenv("APPDATA") != null;
    }

    private static Path link() {
        return Paths.get(System.getenv("APPDATA"), "Microsoft", "Windows", "Start Menu", "Programs", "Startup", "HazCalendar.lnk");
    }

    public static boolean isOn() { return supported() && Files.exists(link()); }

    // 켜기/끄기. 실패하면 이유를 담은 글, 성공하면 null
    public static String set(boolean on) {
        if (!supported()) return "윈도우에서만 쓸 수 있어요.";
        try {
            if (!on) {
                Files.deleteIfExists(link());
                return null;
            }
            String target, args, dir;
            String self = ProcessHandle.current().info().command().orElse("");
            File jar = new File(Startup.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            if (self.toLowerCase().endsWith("hazcalendar.exe")) {          // package.bat 로 만든 exe
                target = self;
                args = "--tray";
                dir = new File(self).getParent();
            } else if (jar.isFile()) {                                     // run.bat 으로 실행한 jar
                target = Paths.get(System.getProperty("java.home"), "bin", "javaw.exe").toString();
                args = "-Dfile.encoding=UTF-8 -jar \"" + jar.getAbsolutePath() + "\" --tray";
                dir = jar.getParent();
            } else {
                return "HazCalendar.jar 로 실행했을 때만 설정할 수 있어요. (run.bat 으로 실행해 주세요)";
            }
            String ps = "$s=(New-Object -ComObject WScript.Shell).CreateShortcut(" + q(link().toString()) + ");"
                    + "$s.TargetPath=" + q(target) + ";$s.Arguments=" + q(args) + ";$s.WorkingDirectory=" + q(dir) + ";"
                    + "$s.Description='HazCalendar';$s.Save()";
            String encoded = Base64.getEncoder().encodeToString(ps.getBytes(StandardCharsets.UTF_16LE));
            Process p = new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
                    "-EncodedCommand", encoded).redirectErrorStream(true).start();
            p.getInputStream().readAllBytes();
            p.waitFor();
            return Files.exists(link()) ? null : "바로가기를 만들지 못했어요.";
        } catch (Exception e) {
            return "설정하지 못했어요: " + e.getMessage();
        }
    }

    // PowerShell 작은따옴표 문자열
    private static String q(String s) { return "'" + s.replace("'", "''") + "'"; }
}
