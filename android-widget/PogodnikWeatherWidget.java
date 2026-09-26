package pl.pogodnik.app;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PogodnikWeatherWidget extends AppWidgetProvider {
    public static final String ACTION_REFRESH = "pl.pogodnik.app.WIDGET_REFRESH";
    private static final String PREFS = "PogodnikWidgetPrefs";
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) renderLoading(context, manager, id);
        fetchAndUpdate(context, manager, ids);
    }

    @Override public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (ACTION_REFRESH.equals(intent.getAction())) {
            AppWidgetManager manager = AppWidgetManager.getInstance(context);
            int[] ids = manager.getAppWidgetIds(new android.content.ComponentName(context, PogodnikWeatherWidget.class));
            fetchAndUpdate(context, manager, ids);
        }
    }

    public static void updateAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new android.content.ComponentName(context, PogodnikWeatherWidget.class));
        if (ids.length > 0) fetchAndUpdate(context, manager, ids);
    }

    private static void renderLoading(Context c, AppWidgetManager m, int id) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_weather);
        v.setTextViewText(R.id.widget_place, getPrefs(c).getString("name", "Moja lokalizacja"));
        v.setTextViewText(R.id.widget_temp, "—°");
        v.setTextViewText(R.id.widget_condition, "Pobieranie pogody…");
        v.setTextViewText(R.id.widget_updated, "Aktualizacja…");
        v.setTextViewText(R.id.widget_rain, "Opad —");
        v.setTextViewText(R.id.widget_wind, "Wiatr —");
        m.updateAppWidget(id, v);
    }

    private static void fetchAndUpdate(Context c, AppWidgetManager m, int[] ids) {
        if (ids == null || ids.length == 0) return;
        final Context app = c.getApplicationContext();
        final double lat = getPrefs(app).getFloat("lat", 52.07f);
        final double lon = getPrefs(app).getFloat("lon", 19.48f);
        EXECUTOR.execute(() -> {
            try {
                String url = "https://api.open-meteo.com/v1/forecast?latitude=" + lat +
                        "&longitude=" + lon +
                        "&timezone=auto&forecast_days=5" +
                        "&current=temperature_2m,weather_code,precipitation,wind_speed_10m" +
                        "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_sum,precipitation_probability_max";
                HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setConnectTimeout(9000);
                conn.setReadTimeout(9000);
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept", "application/json");
                int code = conn.getResponseCode();
                if (code < 200 || code >= 300) throw new Exception("HTTP " + code);
                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();
                JSONObject d = new JSONObject(sb.toString());
                JSONObject cur = d.getJSONObject("current");
                JSONObject daily = d.getJSONObject("daily");
                JSONArray times = daily.getJSONArray("time");
                JSONArray codes = daily.getJSONArray("weather_code");
                JSONArray max = daily.getJSONArray("temperature_2m_max");
                JSONArray min = daily.getJSONArray("temperature_2m_min");
                JSONArray rain = daily.getJSONArray("precipitation_sum");
                JSONArray prob = daily.getJSONArray("precipitation_probability_max");
                String place = getPrefs(app).getString("name", "Moja lokalizacja");
                String temp = Math.round(cur.getDouble("temperature_2m")) + "°";
                String condition = condition(cur.getInt("weather_code"));
                String rainNow = "Opad " + one(cur.getDouble("precipitation")) + " mm";
                String wind = "Wiatr " + Math.round(cur.getDouble("wind_speed_10m")) + " km/h";
                String updated = "Dane: " + new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
                final RemoteViews[] views = new RemoteViews[ids.length];
                for (int n = 0; n < ids.length; n++) {
                    RemoteViews v = new RemoteViews(app.getPackageName(), R.layout.widget_weather);
                    v.setTextViewText(R.id.widget_place, place);
                    v.setTextViewText(R.id.widget_temp, temp);
                    v.setTextViewText(R.id.widget_condition, condition);
                    v.setTextViewText(R.id.widget_updated, updated);
                    v.setTextViewText(R.id.widget_rain, rainNow);
                    v.setTextViewText(R.id.widget_wind, wind);
                    int[] dayIds = {R.id.day1,R.id.day2,R.id.day3,R.id.day4,R.id.day5};
                    int[] iconIds = {R.id.icon1,R.id.icon2,R.id.icon3,R.id.icon4,R.id.icon5};
                    int[] maxIds = {R.id.max1,R.id.max2,R.id.max3,R.id.max4,R.id.max5};
                    int[] minIds = {R.id.min1,R.id.min2,R.id.min3,R.id.min4,R.id.min5};
                    int[] rainIds = {R.id.rain1,R.id.rain2,R.id.rain3,R.id.rain4,R.id.rain5};
                    for (int i=0;i<5 && i<times.length();i++) {
                        String day = dayLabel(times.getString(i), i);
                        v.setTextViewText(dayIds[i], day);
                        v.setTextViewText(iconIds[i], icon(codes.getInt(i)));
                        v.setTextViewText(maxIds[i], Math.round(max.getDouble(i))+"°");
                        v.setTextViewText(minIds[i], Math.round(min.getDouble(i))+"°");
                        v.setTextViewText(rainIds[i], Math.round(prob.getDouble(i))+"% • "+one(rain.getDouble(i))+" mm");
                    }
                    setClicks(app, v);
                    views[n] = v;
                }
                new Handler(Looper.getMainLooper()).post(() -> {
                    for (int n=0;n<ids.length;n++) m.updateAppWidget(ids[n], views[n]);
                });
            } catch (Exception e) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    for (int id : ids) {
                        RemoteViews v = new RemoteViews(app.getPackageName(), R.layout.widget_weather);
                        v.setTextViewText(R.id.widget_place, getPrefs(app).getString("name", "Moja lokalizacja"));
                        v.setTextViewText(R.id.widget_temp, "—°");
                        v.setTextViewText(R.id.widget_condition, "Brak połączenia");
                        v.setTextViewText(R.id.widget_updated, "Dotknij ↻, aby ponowić");
                        setClicks(app, v);
                        m.updateAppWidget(id, v);
                    }
                });
            }
        });
    }

    private static void setClicks(Context c, RemoteViews v) {
        Intent refresh = new Intent(c, PogodnikWeatherWidget.class).setAction(ACTION_REFRESH);
        PendingIntent rp = PendingIntent.getBroadcast(c, 101, refresh, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        v.setOnClickPendingIntent(R.id.widget_refresh, rp);
        Intent open = c.getPackageManager().getLaunchIntentForPackage(c.getPackageName());
        if (open != null) {
            open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            PendingIntent op = PendingIntent.getActivity(c, 102, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            v.setOnClickPendingIntent(R.id.widget_root, op);
        }
    }

    private static android.content.SharedPreferences getPrefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String one(double n) {
        return String.format(Locale.US, "%.1f", n);
    }

    private static String dayLabel(String iso, int i) {
        if (i == 0) return "DZIŚ";
        try {
            Date d = new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(iso);
            return new SimpleDateFormat("EEE", new Locale("pl","PL")).format(d).toUpperCase(new Locale("pl","PL")).replace(".", "");
        } catch (Exception e) { return "DZIEŃ"; }
    }

    private static String condition(int c) {
        switch(c) {
            case 0: return "Bezchmurnie";
            case 1: return "Głównie bezchmurnie";
            case 2: return "Częściowe zachmurzenie";
            case 3: return "Pochmurno";
            case 45: case 48: return "Mgła";
            case 51: case 53: case 55: return "Mżawka";
            case 61: case 63: case 65: case 80: case 81: case 82: return "Deszcz";
            case 71: case 73: case 75: case 77: return "Śnieg";
            case 95: case 96: case 99: return "Burza";
            default: return "Pogoda";
        }
    }

    private static String icon(int c) {
        if (c == 0) return "☀";
        if (c == 1) return "🌤";
        if (c == 2) return "⛅";
        if (c == 3) return "☁";
        if (c >= 45 && c <= 48) return "🌫";
        if (c >= 51 && c <= 67) return "🌧";
        if (c >= 71 && c <= 77) return "❄";
        if (c >= 80 && c <= 82) return "🌦";
        if (c >= 95) return "⛈";
        return "•";
    }
}
