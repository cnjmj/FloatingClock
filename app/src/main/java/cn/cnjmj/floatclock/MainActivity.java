package cn.cnjmj.floatclock;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 主界面：申请悬浮窗权限并启/停前台服务。
 * 授权流程：点"开启"时若无权限则跳系统设置页，用户返回时自动开启。
 */
public class MainActivity extends Activity {

    private TextView status;
    /** 标记是否因申请悬浮窗权限跳去系统设置，返回时自动续开 */
    private boolean jumpedForPermission;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        status = findViewById(R.id.status);
        Button start = findViewById(R.id.btn_start);
        Button stop = findViewById(R.id.btn_stop);

        start.setOnClickListener(v -> {
            if (Settings.canDrawOverlays(this)) {
                startClock();
            } else {
                jumpedForPermission = true;
                try {
                    startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:" + getPackageName())));
                } catch (Exception e) {
                    Toast.makeText(this, R.string.open_perm_manually, Toast.LENGTH_LONG).show();
                }
            }
        });

        stop.setOnClickListener(v -> {
            stopService(new Intent(this, ClockService.class));
            Toast.makeText(this, R.string.stopped, Toast.LENGTH_SHORT).show();
            refreshStatus();
        });

        // Android 13+ 通知权限（拒绝也不影响服务运行，只是通知不显示）
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
        if (jumpedForPermission && Settings.canDrawOverlays(this)) {
            jumpedForPermission = false;
            startClock();
        }
    }

    private void refreshStatus() {
        boolean perm = Settings.canDrawOverlays(this);
        status.setText(getString(R.string.perm_status,
                getString(perm ? R.string.granted : R.string.denied)));
    }

    private void startClock() {
        startForegroundService(new Intent(this, ClockService.class));
        Toast.makeText(this, R.string.started, Toast.LENGTH_SHORT).show();
        finish();
    }
}
