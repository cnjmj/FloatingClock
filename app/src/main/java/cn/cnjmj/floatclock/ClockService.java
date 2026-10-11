package cn.cnjmj.floatclock;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 前台服务：挂载悬浮时钟（TYPE_APPLICATION_OVERLAY）。
 * 交互：按住拖动=移动；单击=显示/隐藏控制条（- + ✕）。
 * 持久化：位置(x,y) 与字号自动记忆。
 */
public class ClockService extends Service {

    static final String ACTION_QUIT = "cn.cnjmj.floatclock.QUIT";
    private static final String CHANNEL_ID = "floatclock";
    private static final int NOTIF_ID = 1;

    private WindowManager wm;
    private LinearLayout overlay;
    private TextView tvTime;
    private LinearLayout ctrlBar;
    private WindowManager.LayoutParams lp;
    private SharedPreferences prefs;
    private int fontSize;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final SimpleDateFormat fmt = new SimpleDateFormat("HH:mm:ss", Locale.US);

    /** 每秒对齐系统整秒刷新，避免累积误差 */
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (tvTime != null) tvTime.setText(fmt.format(new Date()));
            handler.postDelayed(this, 1050 - System.currentTimeMillis() % 1000);
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences("floatclock", MODE_PRIVATE);
        fontSize = prefs.getInt("fs", 28);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_QUIT.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        startAsForeground();
        if (overlay == null) addOverlay();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(tick);
        if (overlay != null) {
            try {
                wm.removeView(overlay);
            } catch (Exception ignored) {
            }
            overlay = null;
        }
        super.onDestroy();
    }

    // ---------------------------------------------------------------- 通知

    private void startAsForeground() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        NotificationChannel ch = new NotificationChannel(CHANNEL_ID,
                getString(R.string.app_name), NotificationManager.IMPORTANCE_LOW);
        ch.setDescription(getString(R.string.channel_desc));
        nm.createNotificationChannel(ch);

        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent quit = PendingIntent.getService(this, 1,
                new Intent(this, ClockService.class).setAction(ACTION_QUIT),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification n = new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_clock)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.notif_running))
                .setContentIntent(open)
                .setOngoing(true)
                .addAction(new Notification.Action.Builder(null,
                        getString(R.string.quit), quit).build())
                .build();
        startForeground(NOTIF_ID, n);
    }

    // ---------------------------------------------------------------- 悬浮窗

    private void addOverlay() {
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        overlay = buildOverlay();

        lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;

        Point sz = screen();
        int sx = prefs.getInt("x", Integer.MIN_VALUE);
        int sy = prefs.getInt("y", 48);
        if (sx == Integer.MIN_VALUE) sx = Math.max(16, sz.x / 4);
        lp.x = clamp(sx, 0, Math.max(0, sz.x - dp(64)));
        lp.y = clamp(sy, 0, Math.max(0, sz.y - dp(64)));

        wm.addView(overlay, lp);
        handler.post(tick);
    }

    private LinearLayout buildOverlay() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xCC101216);
        bg.setCornerRadius(dp(22));
        root.setBackground(bg);
        root.setElevation(dp(6));

        tvTime = new TextView(this);
        tvTime.setText(fmt.format(new Date()));
        tvTime.setTextColor(Color.WHITE);
        tvTime.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        tvTime.setTextSize(fontSize);
        tvTime.setPadding(dp(18), dp(8), dp(18), dp(10));
        tvTime.setShadowLayer(dp(2), 0, dp(1), 0x50000000);
        tvTime.setFontFeatureSettings("tnum"); // 等宽数字，秒跳时宽度不抖动
        root.addView(tvTime);

        ctrlBar = new LinearLayout(this);
        ctrlBar.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        cp.gravity = Gravity.CENTER_HORIZONTAL;
        cp.bottomMargin = dp(10);
        ctrlBar.setLayoutParams(cp);
        ctrlBar.addView(ctrlBtn("−", v -> changeSize(-4)));
        ctrlBar.addView(ctrlBtn("+", v -> changeSize(4)));
        ctrlBar.addView(ctrlBtn(getString(R.string.close), v -> {
            savePos();
            stopSelf();
        }));
        ctrlBar.setVisibility(View.GONE);
        root.addView(ctrlBar);

        root.setOnTouchListener(this::onOverlayTouch);
        return root;
    }

    private Button ctrlBtn(String label, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextColor(Color.WHITE);
        b.setTextSize(15);
        GradientDrawable d = new GradientDrawable();
        d.setColor(0x66FFFFFF);
        d.setCornerRadius(dp(20));
        b.setBackground(d);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(40), dp(40));
        p.leftMargin = dp(6);
        p.rightMargin = dp(6);
        b.setLayoutParams(p);
        b.setPadding(0, 0, 0, 0);
        b.setElevation(0);
        b.setOnClickListener(l);
        return b;
    }

    // 拖动：位移超过 10dp 判定为拖动；否则视为单击
    private float downX, downY;
    private int startLx, startLy;
    private boolean dragging;

    private boolean onOverlayTouch(View v, MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = ev.getRawX();
                downY = ev.getRawY();
                startLx = lp.x;
                startLy = lp.y;
                dragging = false;
                return true;
            case MotionEvent.ACTION_MOVE: {
                float dx = ev.getRawX() - downX;
                float dy = ev.getRawY() - downY;
                if (!dragging && Math.hypot(dx, dy) > dp(10)) dragging = true;
                if (dragging) {
                    Point sz = screen();
                    lp.x = clamp(startLx + (int) dx, 0, sz.x - overlay.getWidth());
                    lp.y = clamp(startLy + (int) dy, 0, sz.y - overlay.getHeight());
                    wm.updateViewLayout(overlay, lp);
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
                if (dragging) {
                    savePos();
                } else {
                    toggleCtrl();
                }
                return true;
            default:
                return false;
        }
    }

    private void toggleCtrl() {
        if (ctrlBar.getVisibility() == View.VISIBLE) {
            ctrlBar.setVisibility(View.GONE);
        } else {
            ctrlBar.setVisibility(View.VISIBLE);
        }
    }

    private void changeSize(int delta) {
        fontSize = clamp(fontSize + delta, 12, 72);
        prefs.edit().putInt("fs", fontSize).apply();
        tvTime.setTextSize(fontSize);
        // 字号变大后可能越出屏幕，拉回边界内
        Point sz = screen();
        lp.x = clamp(lp.x, 0, Math.max(0, sz.x - overlay.getWidth()));
        lp.y = clamp(lp.y, 0, Math.max(0, sz.y - overlay.getHeight()));
        wm.updateViewLayout(overlay, lp);
        savePos();
    }

    private void savePos() {
        prefs.edit().putInt("x", lp.x).putInt("y", lp.y).apply();
    }

    // ---------------------------------------------------------------- 工具

    private Point screen() {
        Point sz = new Point();
        wm.getDefaultDisplay().getSize(sz);
        return sz;
    }

    private int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(v, hi));
    }

    private int dp(int v) {
        return Math.round(getResources().getDisplayMetrics().density * v + 0.5f);
    }
}
