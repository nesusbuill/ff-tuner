package com.khalid.fftuner;

import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;

import rikka.shizuku.Shizuku;

public class MainActivity extends AppCompatActivity implements Shizuku.OnRequestPermissionResultListener {

    private static final int SHIZUKU_REQ_CODE = 200;
    private TextView logView;
    private Handler handler = new Handler(Looper.getMainLooper());
    private Button btnConnect, btnStart;
    private RadioGroup radioGroup;
    private CheckBox cbDeepClean, cbCpuBoost;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 40, 40, 40);

        TextView title = new TextView(this);
        title.setText("FF ULTIMATE TUNER");
        title.setTextSize(22);
        title.setPadding(0, 0, 0, 20);
        layout.addView(title);

        // Chọn Game
        radioGroup = new RadioGroup(this);
        RadioButton rbFF = new RadioButton(this); rbFF.setText("Free Fire Thường"); rbFF.setId(1);
        RadioButton rbFFMax = new RadioButton(this); rbFFMax.setText("Free Fire Max"); rbFFMax.setId(2);
        RadioButton rbBoth = new RadioButton(this); rbBoth.setText("Cả Hai"); rbBoth.setId(3);
        radioGroup.addView(rbFF); radioGroup.addView(rbFFMax); radioGroup.addView(rbBoth);
        radioGroup.check(3);
        layout.addView(radioGroup);

        // Tùy chọn thêm
        cbDeepClean = new CheckBox(this);
        cbDeepClean.setText("Dọn rác hệ thống (Trim Cache)");
        cbDeepClean.setChecked(true);
        layout.addView(cbDeepClean);

        cbCpuBoost = new CheckBox(this);
        cbCpuBoost.setText("Ép xung CPU (Yêu cầu Root/Shizuku)");
        layout.addView(cbCpuBoost);

        // Nút
        btnConnect = new Button(this);
        btnConnect.setText("BẬT / KẾT NỐI SHIZUKU");
        btnConnect.setOnClickListener(v -> checkShizukuPermission());
        layout.addView(btnConnect);

        btnStart = new Button(this);
        btnStart.setText("BẮT ĐẦU FIX LAG");
        btnStart.setOnClickListener(v -> startTuning());
        layout.addView(btnStart);

        logView = new TextView(this);
        logView.setText("Chờ lệnh...\n");
        logView.setTextSize(12);
        logView.setPadding(0, 30, 0, 0);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(logView);
        layout.addView(scroll);

        setContentView(layout);
        Shizuku.addRequestPermissionResultListener(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Shizuku.removeRequestPermissionResultListener(this);
    }

    private void checkShizukuPermission() {
        if (Shizuku.isPreV11()) {
            log("Shizuku quá cũ. Cần bản v11+");
            return;
        }
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            log("Đã kết nối Shizuku!");
        } else {
            Shizuku.requestPermission(SHIZUKU_REQ_CODE);
        }
    }

    @Override
    public void onRequestPermissionResult(int requestCode, int grantResult) {
        if (requestCode == SHIZUKU_REQ_CODE) {
            if (grantResult == PackageManager.PERMISSION_GRANTED) {
                log("Xin quyền Shizuku thành công!");
            } else {
                log("Từ chối cấp quyền!");
            }
        }
    }

    private void startTuning() {
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            log("LỖI: Chưa cấp quyền Shizuku.");
            return;
        }

        int selectedId = radioGroup.getCheckedRadioButtonId();
        boolean deepClean = cbDeepClean.isChecked();
        boolean cpuBoost = cbCpuBoost.isChecked();

        new Thread(() -> {
            log("Bắt đầu quá trình tối ưu...");
            if (selectedId == 1 || selectedId == 3) tuneGame("com.dts.freefireth", "Free Fire Thường", deepClean);
            if (selectedId == 2 || selectedId == 3) tuneGame("com.dts.freefiremax", "Free Fire Max", deepClean);
            
            if (cpuBoost) {
                log("Đang ép xung CPU...");
                execCmd("echo performance > /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor");
                execCmd("echo performance > /sys/devices/system/cpu/cpu4/cpufreq/scaling_governor");
                execCmd("echo performance > /sys/devices/system/cpu/cpu7/cpufreq/scaling_governor");
                log("Ép xung CPU xong (nếu được cấp quyền).");
            }
            
            if (deepClean) {
                log("Đang dọn rác hệ thống...");
                execCmd("pm trim-caches 999999999");
                log("Dọn rác xong.");
            }
            
            log("HOÀN TẤT! Mở game lên chiến.");
        }).start();
    }

    private void tuneGame(String pkg, String name, boolean deepClean) {
        log("Đang xử lý: " + name);
        
        execCmd("am force-stop " + pkg);
        sleep(1000);

        String baseDir = "/data/data/" + pkg;
        String dataIniPath = baseDir + "/files/data.ini";
        String prefPath = baseDir + "/shared_prefs/" + pkg + "_preferences.xml";

        // Backup an toàn
        execCmd("cp " + dataIniPath + " /sdcard/" + pkg + "_data.bak");
        execCmd("cp " + prefPath + " /sdcard/" + pkg + "_pref.bak");

        // Patch file (Đọc ghi chuẩn bằng Java)
        patchFile(dataIniPath, "ResolutionQuality", "1");
        patchFile(dataIniPath, "GraphicsQuality", "1");
        patchFile(dataIniPath, "bShadow", "0");
        patchFile(dataIniPath, "bHDR", "0");
        
        patchFile(prefPath, "ff_shadow", "false");
        patchFile(prefPath, "ff_quality", "0");
        patchFile(prefPath, "ff_max_fps", "1");
        
        // Phân quyền lại file để game đọc được
        execCmd("chmod 660 " + dataIniPath);
        execCmd("chmod 660 " + prefPath);
        execCmd("chown system:system " + dataIniPath);
        execCmd("chown system:system " + prefPath);

        // Xóa cache
        execCmd("rm -rf " + baseDir + "/cache/*");
        execCmd("rm -rf /sdcard/Android/data/" + pkg + "/cache/*");
        
        log("Tối ưu xong: " + name);
    }

    // Hàm đọc ghi file an toàn, không làm hỏng cấu trúc
    private void patchFile(String path, String key, String newValue) {
        try {
            File file = new File(path);
            if (!file.exists()) return;

            List<String> lines = new ArrayList<>();
            FileInputStream fis = new FileInputStream(file);
            BufferedReader reader = new BufferedReader(new InputStreamReader(fis));
            String line;
            boolean found = false;

            while ((line = reader.readLine()) != null) {
                if (line.contains(key)) {
                    if (path.endsWith(".ini")) {
                        lines.add(key + "=" + newValue);
                    } else {
                        // Dùng Regex thay thế chính xác cụm value="..." trong XML
                        line = line.replaceAll("value=\"[^\"]*\"", "value=\"" + newValue + "\"");
                        lines.add(line);
                    }
                    found = true;
                } else {
                    lines.add(line);
                }
            }
            reader.close();

            if (!found && path.endsWith(".ini")) {
                lines.add(key + "=" + newValue);
            }

            FileOutputStream fos = new FileOutputStream(file);
            OutputStreamWriter writer = new OutputStreamWriter(fos);
            for (String l : lines) {
                writer.write(l + "\n");
            }
            writer.close();
            
        } catch (Exception e) {
            log("Lỗi ghi file " + key + ": " + e.getMessage());
        }
    }

    private void execCmd(String cmd) {
        try {
            Process process = Shizuku.newProcess(new String[]{"sh", "-c", cmd}, null, null);
            process.waitFor();
        } catch (Exception e) {
            log("Lỗi thực thi lệnh: " + e.getMessage());
        }
    }

    private void log(String msg) {
        handler.post(() -> logView.append("\n" + msg));
    }

    private void sleep(int ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }
}
