package com.golub.app;

import android.Manifest;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

import org.json.JSONObject;

/**
 * Напоминания «голубь соскучился». Игра присылает мету через setMeta (вкл/выкл, время последнего
 * захода, забрана ли ежедневная награда, имя голубя), а AlarmManager будит приложение спустя
 * REMIND_AFTER после последнего захода и дальше раз в сутки, не больше MAX_REMINDERS раз подряд.
 */
public class Reminders extends BroadcastReceiver {
    static final String PREFS = "golub";
    private static final String CHANNEL = "remind";
    private static final long HOUR = 3600_000L;
    private static final long REMIND_AFTER = 8 * HOUR;
    private static final long REPEAT = 24 * HOUR;
    private static final int MAX_REMINDERS = 3;
    private static final int ID_REMIND = 2;

    static void createChannel(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationChannel ch = new NotificationChannel(CHANNEL, "Напоминания", NotificationManager.IMPORTANCE_DEFAULT);
        ch.setDescription("Голубь позовёт, если ты давно не заходил");
        c.getSystemService(NotificationManager.class).createNotificationChannel(ch);
    }

    static boolean notificationsEnabled(Context c) {
        if (Build.VERSION.SDK_INT >= 33
                && c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        return c.getSystemService(NotificationManager.class).areNotificationsEnabled();
    }

    static void show(Context c, String title, String body, int id) {
        if (!notificationsEnabled(c)) return;
        createChannel(c);
        Intent open = new Intent(c, MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, 0, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, CHANNEL) : new Notification.Builder(c);
        b.setSmallIcon(R.drawable.ic_stat_pigeon)
                .setColor(0xFF2FC9A0)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new Notification.BigTextStyle().bigText(body))
                .setContentIntent(pi)
                .setAutoCancel(true);
        c.getSystemService(NotificationManager.class).notify(id, b.build());
    }

    static void cancelShown(Context c) {
        c.getSystemService(NotificationManager.class).cancel(ID_REMIND);
    }

    static void saveMeta(Context c, String json) {
        try {
            JSONObject m = new JSONObject(json);
            c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putBoolean("on", m.optBoolean("on"))
                    .putLong("last", m.optLong("last", System.currentTimeMillis()))
                    .putBoolean("claimed", m.optBoolean("claimed"))
                    .putBoolean("off", m.optBoolean("off"))
                    .putString("name", m.optString("name", ""))
                    .putInt("sent", 0)
                    .apply();
        } catch (Exception ignored) {
            return;
        }
        schedule(c);
    }

    static void schedule(Context c) {
        SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        AlarmManager am = c.getSystemService(AlarmManager.class);
        PendingIntent pi = PendingIntent.getBroadcast(c, 0, new Intent(c, Reminders.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        am.cancel(pi);
        int sent = p.getInt("sent", 0);
        if (!p.getBoolean("on", false) || sent >= MAX_REMINDERS) return;
        long at = p.getLong("last", System.currentTimeMillis()) + REMIND_AFTER + sent * REPEAT;
        at = Math.max(at, System.currentTimeMillis() + 60_000L);
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
    }

    @Override
    public void onReceive(Context c, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())) {
            schedule(c);
            return;
        }
        SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!p.getBoolean("on", false)) return;
        int sent = p.getInt("sent", 0);
        String name = p.getString("name", "");
        String who = name.isEmpty() ? "Голубь" : name;
        String body;
        if (sent == 0 && !p.getBoolean("claimed", true)) {
            body = "Ежедневная награда ещё не забрана 🎁";
        } else if (sent == 0 && p.getBoolean("off", false)) {
            body = who + " набрал зерна, пока тебя не было. Забери! 🌾";
        } else if (sent == 0) {
            body = who + " соскучился и ждёт тебя 🕊️";
        } else {
            body = "Новый день — новые задания и награда. " + who + " ждёт! 🎁";
        }
        show(c, who + " зовёт", body, ID_REMIND);
        p.edit().putInt("sent", sent + 1).apply();
        schedule(c);
    }
}
