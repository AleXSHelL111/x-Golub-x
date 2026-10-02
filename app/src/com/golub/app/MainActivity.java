package com.golub.app;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.media.AudioAttributes;
import android.os.VibrationAttributes;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.View;
import android.view.Window;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MainActivity extends Activity {
    private static final String START_URL = "file:///android_asset/index.html";
    private static final int REQ_NOTIF = 1;
    private static final AudioAttributes GAME_AUDIO = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build();
    private WebView web;
    private Vibrator vibrator;
    private int nightMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        nightMode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        Reminders.createChannel(this);
        vibrator = getSystemService(Vibrator.class);

        web = new WebView(this);
        web.setBackgroundColor(getColor(R.color.bg));
        setContentView(web);
        applyBars(getColor(R.color.bg));

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setTextZoom(100);
        // Облачная синхронизация шлёт fetch на script.google.com со страницы file://
        s.setAllowFileAccess(true);
        s.setAllowUniversalAccessFromFileURLs(true);
        s.setSupportMultipleWindows(false);

        web.addJavascriptInterface(new Bridge(), "GolubAndroid");
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                return openExternal(req.getUrl());
            }
        });

        if (savedInstanceState != null) web.restoreState(savedInstanceState);
        else web.loadUrl(START_URL);
    }

    // Внешние ссылки (донат и т.п.) открываем в браузере, а не внутри игры
    private boolean openExternal(Uri uri) {
        if ("file".equals(uri.getScheme())) return false;
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (Exception ignored) {
        }
        return true;
    }

    // Статус-бар и панель навигации в цвет фона игры, иконки — контрастные
    private void applyBars(int color) {
        Window w = getWindow();
        w.setStatusBarColor(color);
        w.setNavigationBarColor(color);
        if (Build.VERSION.SDK_INT >= 28) w.setNavigationBarDividerColor(color);
        boolean light = (Color.red(color) * 299 + Color.green(color) * 587 + Color.blue(color) * 114) / 1000 > 140;
        View d = w.getDecorView();
        int f = d.getSystemUiVisibility();
        int lightFlags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                | (Build.VERSION.SDK_INT >= 26 ? View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR : 0);
        d.setSystemUiVisibility(light ? (f | lightFlags) : (f & ~lightFlags));
    }

    private String notifPermission() {
        if (Reminders.notificationsEnabled(this)) return "granted";
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return "default";
        }
        return "denied";
    }

    private void permResult() {
        String st = notifPermission();
        if (!"granted".equals(st)) st = "denied";
        final String js = "window.__golubPerm&&window.__golubPerm('" + st + "')";
        web.evaluateJavascript(js, null);
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        if (code == REQ_NOTIF) permResult();
    }

    private class Bridge {
        @JavascriptInterface
        public String notifPermission() {
            return MainActivity.this.notifPermission();
        }

        @JavascriptInterface
        public void requestNotifPermission() {
            runOnUiThread(() -> {
                if (Build.VERSION.SDK_INT >= 33
                        && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
                } else {
                    permResult();
                }
            });
        }

        @JavascriptInterface
        public void notify(String title, String body) {
            Reminders.show(MainActivity.this, title.isEmpty() ? getString(R.string.app_name) : title, body, 1);
        }

        @JavascriptInterface
        public void setMeta(String json) {
            Reminders.saveMeta(MainActivity.this, json);
        }

        // navigator.vibrate в WebView не работает, поэтому вибрируем сами
        @JavascriptInterface
        public void vibrate(String pattern) {
            if (vibrator == null || !vibrator.hasVibrator()) return;
            String[] parts = pattern.split(",");
            long[] t = new long[parts.length];
            long total = 0;
            for (int i = 0; i < parts.length; i++) {
                try {
                    t[i] = Math.max(0, Math.min(5000, Long.parseLong(parts[i].trim())));
                } catch (NumberFormatException e) {
                    t[i] = 0;
                }
                total += t[i];
            }
            if (total == 0) {
                vibrator.cancel();
                return;
            }
            VibrationEffect fx;
            if (t.length == 1) {
                // Игра просит 8 мс на клик и 30 мс на крит: такие импульсы мотор
                // не успевает раскрутить, поэтому растягиваем и задаём силу явно.
                boolean strong = t[0] >= 20;
                long ms = strong ? Math.max(t[0], 55) : Math.max(t[0], 28);
                int amp = vibrator.hasAmplitudeControl() ? (strong ? 255 : 190) : VibrationEffect.DEFAULT_AMPLITUDE;
                fx = VibrationEffect.createOneShot(ms, amp);
            } else {
                long[] w = new long[t.length + 1];
                System.arraycopy(t, 0, w, 1, t.length);
                fx = VibrationEffect.createWaveform(w, -1);
            }
            // Помечаем как игровую (медиа), а не как отклик на касание: такие вибрации
            // прошивки глушат настройками тактильного отклика и при частых нажатиях.
            if (Build.VERSION.SDK_INT >= 33) {
                vibrator.vibrate(fx, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_MEDIA));
            } else {
                vibrator.vibrate(fx, GAME_AUDIO);
            }
        }

        @JavascriptInterface
        public void setBars(int r, int g, int b) {
            final int c = Color.rgb(r, g, b);
            runOnUiThread(() -> {
                web.setBackgroundColor(c);
                applyBars(c);
            });
        }
    }

    @Override
    public void onConfigurationChanged(Configuration cfg) {
        super.onConfigurationChanged(cfg);
        int night = cfg.uiMode & Configuration.UI_MODE_NIGHT_MASK;
        if (night != nightMode) {
            // Тема WebView берётся из темы активити, поэтому пересоздаём её,
            // предварительно сохранив прогресс игры.
            nightMode = night;
            web.evaluateJavascript("try{save()}catch(e){}", v -> recreate());
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }

    @Override
    protected void onPause() {
        super.onPause();
        web.evaluateJavascript("try{save();writeMeta()}catch(e){}", null);
        web.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        web.onResume();
        Reminders.cancelShown(this);
    }

    @Override
    public void onBackPressed() {
        if (web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        web.destroy();
        super.onDestroy();
    }
}
