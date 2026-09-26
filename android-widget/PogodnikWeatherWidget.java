package pl.pogodnik.app;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
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
    private static final int MAP_ZOOM = 6;
    private static final int TILE = 256;

    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) renderLoading(context, manager, id);
        fetchAndUpdate(context, manager, ids);
    }

    @Override public void onAppWidgetOptionsChanged(Context context, AppWidgetManager manager, int id, Bundle options) {
        super.onAppWidgetOptionsChanged(context, manager, id, options);
        fetchAndUpdate(context, manager, new int[]{id});
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
        setClicks(c, v);
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
                JSONObject d = getJson(url);
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
                String updated = "Aktualizacja  •  " + new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());

                Bitmap radar = buildRadarMap(app, lat, lon);

                final RemoteViews[] views = new RemoteViews[ids.length];
                for (int n = 0; n < ids.length; n++) {
                    RemoteViews v = new RemoteViews(app.getPackageName(), R.layout.widget_weather);
                    v.setTextViewText(R.id.widget_place, place);
                    v.setTextViewText(R.id.widget_temp, temp);
                    v.setTextViewText(R.id.widget_condition, condition);
                    v.setTextViewText(R.id.widget_updated, updated);
                    v.setTextViewText(R.id.widget_rain, rainNow);
                    v.setTextViewText(R.id.widget_wind, wind);
                    if (radar != null) v.setImageViewBitmap(R.id.widget_radar, radar);

                    int[] dayIds = {R.id.day1Label,R.id.day2Label,R.id.day3Label,R.id.day4Label,R.id.day5Label};
                    int[] iconIds = {R.id.icon1,R.id.icon2,R.id.icon3,R.id.icon4,R.id.icon5};
                    int[] maxIds = {R.id.max1,R.id.max2,R.id.max3,R.id.max4,R.id.max5};
                    int[] minIds = {R.id.min1,R.id.min2,R.id.min3,R.id.min4,R.id.min5};
                    int[] rainIds = {R.id.rain1,R.id.rain2,R.id.rain3,R.id.rain4,R.id.rain5};

                    for (int i = 0; i < 5 && i < times.length(); i++) {
                        v.setTextViewText(dayIds[i], dayLabel(times.getString(i), i));
                        v.setTextViewText(iconIds[i], icon(codes.getInt(i)));
                        v.setTextViewText(maxIds[i], Math.round(max.getDouble(i)) + "°");
                        v.setTextViewText(minIds[i], Math.round(min.getDouble(i)) + "°");
                        v.setTextViewText(rainIds[i], Math.round(prob.getDouble(i)) + "%");
                    }
                    setClicks(app, v);
                    views[n] = v;
                }

                new Handler(Looper.getMainLooper()).post(() -> {
                    for (int n = 0; n < ids.length; n++) m.updateAppWidget(ids[n], views[n]);
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

    private static JSONObject getJson(String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(9000);
        conn.setReadTimeout(9000);
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Accept", "application/json");
        try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            return new JSONObject(sb.toString());
        } finally {
            conn.disconnect();
        }
    }

    private static Bitmap buildRadarMap(Context c, double lat, double lon) {
        HttpURLConnection metaConn = null;
        try {
            JSONObject meta = getJson("https://api.rainviewer.com/public/weather-maps.json");
            JSONArray past = meta.getJSONObject("radar").getJSONArray("past");
            if (past.length() == 0) return null;
            String host = meta.getString("host");
            String path = past.getJSONObject(past.length() - 1).getString("path");

            int x = lon2tile(lon, MAP_ZOOM);
            int y = lat2tile(lat, MAP_ZOOM);
            Bitmap base = Bitmap.createBitmap(TILE * 2, TILE * 2, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(base);

            for (int dx = -1; dx <= 0; dx++) {
                for (int dy = -1; dy <= 0; dy++) {
                    int tx = x + dx;
                    int ty = y + dy;
                    Bitmap map = downloadBitmap("https://tile.openstreetmap.org/" + MAP_ZOOM + "/" + tx + "/" + ty + ".png", true);
                    if (map != null) canvas.drawBitmap(map, (dx + 1) * TILE, (dy + 1) * TILE, null);

                    Bitmap radar = downloadBitmap(host + path + "/256/" + MAP_ZOOM + "/" + tx + "/" + ty + "/2/1_0.png", false);
                    if (radar != null) canvas.drawBitmap(radar, (dx + 1) * TILE, (dy + 1) * TILE, null);
                }
            }

            float px = (float)((lonToPixel(lon, MAP_ZOOM) - Math.floor(lonToPixel(lon, MAP_ZOOM) / TILE) * TILE) + TILE);
            float py = (float)((latToPixel(lat, MAP_ZOOM) - Math.floor(latToPixel(lat, MAP_ZOOM) / TILE) * TILE) + TILE);
            Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
            dot.setStyle(Paint.Style.STROKE);
            dot.setStrokeWidth(5f);
            dot.setColor(Color.WHITE);
            canvas.drawCircle(px, py, 13f, dot);
            dot.setStyle(Paint.Style.FILL);
            dot.setColor(Color.rgb(32, 139, 255));
            canvas.drawCircle(px, py, 8f, dot);

            Bitmap out = Bitmap.createBitmap(512, 300, Bitmap.Config.ARGB_8888);
            Canvas crop = new Canvas(out);
            crop.drawBitmap(base, 0, 0, null);
            return out;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Bitmap downloadBitmap(String url, boolean map) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection)new URL(url).openConnection();
            conn.setConnectTimeout(7000);
            conn.setReadTimeout(7000);
            conn.setRequestProperty("User-Agent", "Pogodnik-PL/0.2 (+https://github.com/Pawelg899/Pogodnik-PL)");
            conn.setRequestProperty("Accept", "image/png,image/*;q=0.8");
            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) return null;
            try (BufferedInputStream in = new BufferedInputStream(conn.getInputStream());
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) >= 0) out.write(buf, 0, n);
                return BitmapFactory.decodeByteArray(out.toByteArray(), 0, out.size());
            }
        } catch (Exception ignored) {
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static int lon2tile(double lon, int z) { return (int)Math.floor((lon + 180.0) / 360.0 * (1 << z)); }
    private static int lat2tile(double lat, int z) { return (int)Math.floor((1 - Math.log(Math.tan(Math.toRadians(lat)) + 1 / Math.cos(Math.toRadians(lat))) / Math.PI) / 2 * (1 << z)); }
    private static double lonToPixel(double lon, int z) { return (lon + 180.0) / 360.0 * (1 << z) * TILE; }
    private static double latToPixel(double lat, int z) { return (1 - Math.log(Math.tan(Math.toRadians(lat)) + 1 / Math.cos(Math.toRadians(lat))) / Math.PI) / 2 * (1 << z) * TILE; }

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

    private static String one(double n) { return String.format(Locale.US, "%.1f", n); }

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
