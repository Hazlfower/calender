import javax.swing.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

// 앱 시작점: 하나만 실행되게 확인 → 데이터 불러오기 → 화면 → 알람 감시 시작
public class Main {
    private static FileLock HAZ_LOCK;   // 앱이 켜져 있는 동안 계속 들고 있어야 함

    public static void main(String[] args) {
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");
        System.setProperty("sun.java2d.uiScale.enabled", "true");

        // 이미 켜져 있으면 그 창을 앞으로 부르고 끝냄 (알람이 두 번 뜨거나 저장 파일이 꼬이지 않게)
        if (!acquireLock()) {
            if (!askRunningToShow()) {
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(null,
                        "캘린더가 이미 실행 중이에요.\n작업 표시줄 오른쪽 트레이 아이콘을 확인해 주세요.", "캘린더", JOptionPane.INFORMATION_MESSAGE));
            }
            try { Thread.sleep(200); } catch (InterruptedException ignored) { }
            if (!SwingUtilities.isEventDispatchThread()) waitAndExit();
            return;
        }

        SwingUtilities.invokeLater(() -> {
            AppSettings HAZsettings = new AppSettings();
            HAZsettings.load();
            Theme.load(HAZsettings);

            ScheduleManager HAZscheduleManager = new ScheduleManager();
            Todos todos = new Todos();
            MemoManager memos = new MemoManager();
            DataStore HAZdataStore = new DataStore(HAZscheduleManager, todos, memos);
            HAZdataStore.load(HAZsettings);

            AlarmManager HAZalarmManager = new AlarmManager(HAZscheduleManager, HAZsettings);
            MainFrame frame = new MainFrame(HAZscheduleManager, HAZalarmManager, HAZsettings, memos, todos, HAZdataStore);
            boolean hidden = false;
            for (String a : args) if (a.equals("--tray")) hidden = true;   // 시작 프로그램용: 창 없이 트레이로
            if (!hidden) frame.setVisible(true);
            memos.openSavedWindows();
            HAZalarmManager.start();
            listenForShow(frame);
            Runtime.getRuntime().addShutdownHook(new Thread(HAZdataStore::saveNow));
            SwingUtilities.invokeLater(() -> {
                if (HAZdataStore.loadMessage != null) Ui.info(frame, "데이터", HAZdataStore.loadMessage);
                frame.checkImages();
            });
        });
    }

    private static void waitAndExit() {
        // 안내 창을 닫으면 끝나도록 (안내 창이 없으면 바로 끝남)
        new Thread(() -> {
            try { Thread.sleep(100); } catch (InterruptedException ignored) { }
            while (java.awt.Window.getWindows().length > 0) {
                boolean any = false;
                for (java.awt.Window w : java.awt.Window.getWindows()) if (w.isShowing()) any = true;
                if (!any) break;
                try { Thread.sleep(200); } catch (InterruptedException ignored) { }
            }
            System.exit(0);
        }).start();
    }

    private static boolean acquireLock() {
        try {
            Files.createDirectories(AppSettings.dataDir());
            FileChannel ch = FileChannel.open(AppSettings.dataDir().resolve("app.lock"),
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            HAZ_LOCK = ch.tryLock();
            return HAZ_LOCK != null;
        } catch (Exception e) {
            return true;   // 잠금 파일을 못 만들면 그냥 실행
        }
    }

    private static Path portFile() { return AppSettings.dataDir().resolve("app.port"); }

    // 먼저 켜진 쪽: 두 번째 실행이 "show" 를 보내면 창을 앞으로
    private static void listenForShow(MainFrame frame) {
        Thread t = new Thread(() -> {
            try (ServerSocket server = new ServerSocket(0, 5, InetAddress.getLoopbackAddress())) {
                Files.writeString(portFile(), String.valueOf(server.getLocalPort()), StandardCharsets.UTF_8);
                while (true) {
                    try (Socket s = server.accept();
                         BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8))) {
                        if ("show".equals(in.readLine())) SwingUtilities.invokeLater(frame::showMain);
                    } catch (Exception ignored) { }
                }
            } catch (Exception ignored) { }
        }, "show-listener");
        t.setDaemon(true);
        t.start();
    }

    private static boolean askRunningToShow() {
        try {
            int port = Integer.parseInt(Files.readString(portFile(), StandardCharsets.UTF_8).trim());
            try (Socket s = new Socket(InetAddress.getLoopbackAddress(), port)) {
                OutputStream out = s.getOutputStream();
                out.write("show\n".getBytes(StandardCharsets.UTF_8));
                out.flush();
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
