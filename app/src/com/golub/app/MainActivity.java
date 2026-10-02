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
    private WebView web;
    private int nightMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        nightMode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        Reminders.createChannel(this);

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
            Vibrator v = getSystemService(Vibrator.class);
            if (v == null || !v.hasVibrator()) return;
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
                v.cancel();
            } else if (t.length == 1) {
                // Очень короткие импульсы (8 мс) многие моторы не успевают раскрутить
                if (Build.VERSION.SDK_INT >= 29 && t[0] <= 40) {
                    v.vibrate(VibrationEffect.createPredefined(
                            t[0] < 20 ? VibrationEffect.EFFECT_CLICK : VibrationEffect.EFFECT_HEAVY_CLICK));
                } else {
                    v.vibrate(VibrationEffect.createOneShot(Math.max(t[0], 20), VibrationEffect.DEFAULT_AMPLITUDE));
                }
            } else {
                long[] w = new long[t.length + 1];
                System.arraycopy(t, 0, w, 1, t.length);
                v.vibrate(VibrationEffect.createWaveform(w, -1));
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
