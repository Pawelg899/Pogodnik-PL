package pl.pogodnik.app;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.graphics.*;
import android.os.Bundle;
import android.widget.RemoteViews;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

public class PogodnikWeatherWidget extends AppWidgetProvider {
    public static final String ACTION_REFRESH="pl.pogodnik.app.WIDGET_REFRESH";
    public static final String ACTION_LAYER="pl.pogodnik.app.WIDGET_LAYER";
    public static final String ACTION_ZOOM="pl.pogodnik.app.WIDGET_ZOOM";
    private static final String PREFS="PogodnikWidgetPrefs";
    private static final ExecutorService EXECUTOR=Executors.newSingleThreadExecutor();
    private static final int Z=6;

    public static void updateAll(Context c){ refreshAll(c); }

    @Override public void onUpdate(Context c, AppWidgetManager m, int[] ids){
        for(int id:ids) showLoading(c,m,id);
        update(c,m,ids);
    }

    @Override public void onAppWidgetOptionsChanged(Context c, AppWidgetManager m, int id, Bundle o){
        super.onAppWidgetOptionsChanged(c,m,id,o);
        update(c,m,new int[]{id});
    }

    @Override public void onReceive(Context c, Intent i){
        super.onReceive(c,i);
        if(ACTION_REFRESH.equals(i.getAction())) refreshAll(c);
        else if(ACTION_ZOOM.equals(i.getAction())){
            int dz=i.getIntExtra("dz",0);
            int z=Math.max(4,Math.min(9,getPrefs(c).getInt("zoom",6)+dz));
            getPrefs(c).edit().putInt("zoom",z).apply();
            refreshAll(c);
        } else if(ACTION_LAYER.equals(i.getAction())){
            String layer=i.getStringExtra("layer");
            if(layer!=null) getPrefs(c).edit().putString("layer",layer).apply();
            refreshAll(c);
        }
    }

    private static void refreshAll(Context c){
        AppWidgetManager m=AppWidgetManager.getInstance(c);
        int[] ids=m.getAppWidgetIds(new android.content.ComponentName(c,PogodnikWeatherWidget.class));
        if(ids.length>0) update(c,m,ids);
    }

    private static void showLoading(Context c, AppWidgetManager m, int id){
        RemoteViews v=new RemoteViews(c.getPackageName(),R.layout.widget_weather);
        fill(v,null,null,null,null,"Moja lokalizacja","RADAR");
        setClicks(c,v);
        m.updateAppWidget(id,v);
    }

    private static void update(Context c, AppWidgetManager m, int[] ids){
        if(ids==null||ids.length==0)return;
        final Context app=c.getApplicationContext();
        final float lat=getPrefs(app).getFloat("lat",52.07f),lon=getPrefs(app).getFloat("lon",19.48f);
        final String place=getPrefs(app).getString("name","Moja lokalizacja");
        final String layer=getPrefs(app).getString("layer","RADAR");
        final int zoom=getPrefs(app).getInt("zoom",6);
        EXECUTOR.execute(()->{
            JSONObject cur=null,daily=null,hourly=null; Bitmap map=null;
            try{
                String u="https://api.open-meteo.com/v1/forecast?latitude="+lat+"&longitude="+lon+
                    "&timezone=auto&forecast_days=5"+
                    "&current=temperature_2m,apparent_temperature,weather_code,precipitation,precipitation_probability,wind_speed_10m,wind_direction_10m,wind_gusts_10m,pressure_msl,relative_humidity_2m,cloud_cover"+
                    "&hourly=temperature_2m,weather_code,precipitation_probability"+
                    "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_sum,precipitation_probability_max";
                JSONObject d=getJson(u);
                cur=d.getJSONObject("current"); daily=d.getJSONObject("daily"); hourly=d.getJSONObject("hourly");
                map=buildMap(lat,lon,layer,zoom);
            }catch(Exception ignored){}
            Bitmap finalMap=map;
            JSONObject fcur=cur, fdaily=daily, fhourly=hourly;
            new android.os.Handler(android.os.Looper.getMainLooper()).post(()->{
                for(int id:ids){
                    RemoteViews v=new RemoteViews(app.getPackageName(),R.layout.widget_weather);
                    fill(v,fcur,fdaily,fhourly,finalMap,place,layer);
                    setClicks(app,v);
                    m.updateAppWidget(id,v);
                }
            });
        });
    }

    private static void fill(RemoteViews v, JSONObject c, JSONObject d, JSONObject h, Bitmap map, String place, String layer){
        if(c==null){
            set(v,R.id.w_location,place==null?"Moja lokalizacja":place);
            set(v,R.id.w_updated,"Aktualizacja • pobieranie…");
            set(v,R.id.w_temp,"--°C"); set(v,R.id.w_condition,"Pobieranie danych…"); set(v,R.id.w_feels,"Odczuwalna --°C");
            set(v,R.id.w_wind,"—");set(v,R.id.w_gust,"");set(v,R.id.w_rain,"—");set(v,R.id.w_rainp,"");set(v,R.id.w_pressure,"—");set(v,R.id.w_humidity,"—");set(v,R.id.w_cloud,"—");
            set(v,R.id.w_icon,"☀");
        }else{
            int code=c.optInt("weather_code",3), temp=(int)Math.round(c.optDouble("temperature_2m",0));
            set(v,R.id.w_location,place==null?"Moja lokalizacja":place);
            set(v,R.id.w_updated,"Aktualizacja • "+new SimpleDateFormat("HH:mm",Locale.getDefault()).format(new Date()));
            set(v,R.id.w_icon,icon(code)); set(v,R.id.w_temp,temp+"°C"); set(v,R.id.w_condition,condition(code));
            set(v,R.id.w_feels,"Odczuwalna "+Math.round(c.optDouble("apparent_temperature",temp))+"°C");
            set(v,R.id.w_wind,"z "+wind(c.optDouble("wind_direction_10m",0))+"  "+Math.round(c.optDouble("wind_speed_10m",0))+" km/h");
            set(v,R.id.w_gust,"porywy "+Math.round(c.optDouble("wind_gusts_10m",0))+" km/h");
            set(v,R.id.w_rain,one(c.optDouble("precipitation",0))+" mm"); set(v,R.id.w_rainp,Math.round(c.optDouble("precipitation_probability",0))+"%");
            set(v,R.id.w_pressure,Math.round(c.optDouble("pressure_msl",0))+" hPa");
            set(v,R.id.w_humidity,Math.round(c.optDouble("relative_humidity_2m",0))+"%"); set(v,R.id.w_cloud,Math.round(c.optDouble("cloud_cover",0))+"%");
        }

        if(map!=null) v.setImageViewBitmap(R.id.w_map,map);
        set(v,R.id.w_map_status,layerName(layer));
        int selected=R.drawable.widget_button_selected, normal=R.drawable.widget_button_dark;
        v.setInt(R.id.widget_radar_button,"setBackgroundResource", "RADAR".equals(layer)?selected:normal);
        v.setInt(R.id.widget_lightning_button,"setBackgroundResource", "LIGHTNING".equals(layer)?selected:normal);
        v.setInt(R.id.widget_tsp_button,"setBackgroundResource", "TSP".equals(layer)?selected:normal);
        v.setInt(R.id.widget_sat_button,"setBackgroundResource", "SAT".equals(layer)?selected:normal);

        fillHourly(v,h); fillDaily(v,d);
    }

    private static void fillHourly(RemoteViews v, JSONObject h){
        if(h==null)return;
        JSONArray times=h.optJSONArray("time"), temps=h.optJSONArray("temperature_2m"), codes=h.optJSONArray("weather_code"), probs=h.optJSONArray("precipitation_probability");
        if(times==null||temps==null)return;
        int start=findNextHour(times);
        for(int j=0;j<5;j++){
            int i=start+j; if(i>=times.length()||i>=temps.length())break;
            set(v, hourId(j,"t"), hourLabel(times.optString(i))); set(v,hourId(j,"i"),icon(codes==null?0:codes.optInt(i,0))); set(v,hourId(j,"p"),probs==null?"":Math.round(probs.optDouble(i,0))+"%");
            int t=(int)Math.round(temps.optDouble(i,0));
            set(v, hourId(j,"v"), t+"°");
        }
    }

    private static int findNextHour(JSONArray a){
        String now=new SimpleDateFormat("yyyy-MM-dd'T'HH",Locale.US).format(new Date());
        for(int i=0;i<a.length();i++) if(a.optString(i).compareTo(now)>=0)return i;
        return 0;
    }

    private static int hourId(int n,String part){
        String s="h"+n+part;
        switch(s){
            case "h0t":return R.id.h0t;case "h0i":return R.id.h0i;case "h0v":return R.id.h0v;case "h0p":return R.id.h0p;
            case "h1t":return R.id.h1t;case "h1i":return R.id.h1i;case "h1v":return R.id.h1v;case "h1p":return R.id.h1p;
            case "h2t":return R.id.h2t;case "h2i":return R.id.h2i;case "h2v":return R.id.h2v;case "h2p":return R.id.h2p;
            case "h3t":return R.id.h3t;case "h3i":return R.id.h3i;case "h3v":return R.id.h3v;case "h3p":return R.id.h3p;
            case "h4t":return R.id.h4t;case "h4i":return R.id.h4i;case "h4p":return R.id.h4p;default:return R.id.h4v;
        }
    }

    private static void fillDaily(RemoteViews v, JSONObject d){
        if(d==null)return;
        JSONArray t=d.optJSONArray("time"), co=d.optJSONArray("weather_code"), mx=d.optJSONArray("temperature_2m_max"), mn=d.optJSONArray("temperature_2m_min");
        for(int i=0;i<5;i++){
            set(v,dayId(i,"n"),i==0?"DZIŚ":dayLabel(t==null?"":t.optString(i),i));
            set(v,dayId(i,"i"),icon(co==null?3:co.optInt(i,3)));
            String hi=mx==null?"--":String.valueOf(Math.round(mx.optDouble(i,0)));
            String lo=mn==null?"--":String.valueOf(Math.round(mn.optDouble(i,0)));
            set(v,dayId(i,"v"),hi+"° / "+lo+"°");
        }
    }

    private static int dayId(int n,String part){
        String s="d"+n+part;
        switch(s){
            case "d0n":return R.id.d0n;case "d0i":return R.id.d0i;case "d0v":return R.id.d0v;
            case "d1n":return R.id.d1n;case "d1i":return R.id.d1i;case "d1v":return R.id.d1v;
            case "d2n":return R.id.d2n;case "d2i":return R.id.d2i;case "d2v":return R.id.d2v;
            case "d3n":return R.id.d3n;case "d3i":return R.id.d3i;case "d3v":return R.id.d3v;
            case "d4n":return R.id.d4n;case "d4i":return R.id.d4i;default:return R.id.d4v;
        }
    }

    private static Bitmap buildMap(double lat,double lon,String layer,int Z){
        try{
            int tx=lon2tile(lon,Z),ty=lat2tile(lat,Z);
            Bitmap base=Bitmap.createBitmap(512,512,Bitmap.Config.ARGB_8888); Canvas c=new Canvas(base);
            JSONObject meta=null;String radarHost="",radarPath="";
            if("RADAR".equals(layer)){
                meta=getJson("https://api.rainviewer.com/public/weather-maps.json");
                JSONArray past=meta.getJSONObject("radar").getJSONArray("past");
                if(past.length()>0){radarHost=meta.getString("host");radarPath=past.getJSONObject(past.length()-1).getString("path");}
            }
            for(int dx=-1;dx<=0;dx++)for(int dy=-1;dy<=0;dy++){
                int xx=tx+dx,yy=ty+dy; Bitmap bm;
                String baseUrl;
                if("SAT".equals(layer)) baseUrl="https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/"+Z+"/"+yy+"/"+xx;
                else baseUrl="https://tile.openstreetmap.org/"+Z+"/"+xx+"/"+yy+".png";
                bm=download(baseUrl); if(bm!=null)c.drawBitmap(bm,(dx+1)*256,(dy+1)*256,null);
                if("RADAR".equals(layer)&&!radarPath.isEmpty()){
                    Bitmap rm=download(radarHost+radarPath+"/256/"+Z+"/"+xx+"/"+yy+"/2/1_0.png"); if(rm!=null)c.drawBitmap(rm,(dx+1)*256,(dy+1)*256,null);
                }
                if("LIGHTNING".equals(layer)){
                    Bitmap lm=download("https://tiles.lightningmaps.org/?x="+xx+"&y="+yy+"&z="+Z+"&s=256&t=5&T=1"); if(lm!=null)c.drawBitmap(lm,(dx+1)*256,(dy+1)*256,null);
                }
            }
            Paint p=new Paint(3);p.setColor(Color.WHITE);c.drawCircle(256,256,13,p);p.setColor(Color.rgb(35,140,255));c.drawCircle(256,256,9,p);
            return base;
        }catch(Exception e){return null;}
    }

    private static void set(RemoteViews v,int id,String s){v.setTextViewText(id,s==null?"":s);}
    private static String layerName(String l){if("LIGHTNING".equals(l))return"WYŁADOWANIA • LightningMaps / Blitzortung";if("SAT".equals(l))return"SAT • zdjęcie satelitarne";if("TSP".equals(l))return"TSP • warstwa dostępna w aplikacji";return"RADAR • opad w czasie rzeczywistym";}
    private static String one(double n){return String.format(Locale.US,"%.1f",n);}
    private static String wind(double d){String[] a={"północy","północnego wschodu","wschodu","południowego wschodu","południa","południowego zachodu","zachodu","północnego zachodu"};return a[(int)Math.round((((d%360)+360)%360)/45)%8];}
    private static String hourLabel(String s){try{return new SimpleDateFormat("HH",Locale.US).format(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm",Locale.US).parse(s));}catch(Exception e){return"--";}}
    private static String dayLabel(String s,int i){if(i==0)return"DZIŚ";try{return new SimpleDateFormat("EEE",new Locale("pl","PL")).format(new SimpleDateFormat("yyyy-MM-dd",Locale.US).parse(s)).toUpperCase(new Locale("pl","PL")).replace(".","");}catch(Exception e){return"—";}}
    private static String condition(int c){if(c==0)return"Bezchmurnie";if(c==1)return"Głównie bezchmurnie";if(c==2)return"Częściowe zachmurzenie";if(c==3)return"Pochmurno";if(c>=45&&c<=48)return"Mgła";if(c>=51&&c<=55)return"Mżawka";if(c>=61&&c<=82)return"Deszcz";if(c>=71&&c<=77)return"Śnieg";if(c>=95)return"Burza";return"Pogoda";}
    private static String icon(int c){if(c==0)return"☀";if(c==1)return"☀";if(c==2)return"⛅";if(c==3)return"☁";if(c>=45&&c<=48)return"≋";if(c>=51&&c<=67)return"☂";if(c>=71&&c<=77)return"❄";if(c>=80&&c<=82)return"☂";if(c>=95)return"ϟ";return"•";}
    private static Bitmap download(String u){HttpURLConnection c=null;try{c=(HttpURLConnection)new URL(u).openConnection();c.setConnectTimeout(5000);c.setReadTimeout(5000);c.setRequestProperty("User-Agent","Pogodnik-PL");if(c.getResponseCode()/100!=2)return null;ByteArrayOutputStream o=new ByteArrayOutputStream();try(InputStream in=c.getInputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))>0)o.write(b,0,n);}return BitmapFactory.decodeByteArray(o.toByteArray(),0,o.size());}catch(Exception e){return null;}finally{if(c!=null)c.disconnect();}}
    private static JSONObject getJson(String u)throws Exception{HttpURLConnection c=(HttpURLConnection)new URL(u).openConnection();c.setConnectTimeout(8000);c.setReadTimeout(8000);try(BufferedReader r=new BufferedReader(new InputStreamReader(c.getInputStream()))){StringBuilder s=new StringBuilder();String z;while((z=r.readLine())!=null)s.append(z);return new JSONObject(s.toString());}finally{c.disconnect();}}
    private static int lon2tile(double lon,int z){return(int)Math.floor((lon+180)/360*(1<<z));}
    private static int lat2tile(double lat,int z){return(int)Math.floor((1-Math.log(Math.tan(Math.toRadians(lat))+1/Math.cos(Math.toRadians(lat)))/Math.PI)/2*(1<<z));}

    private static void setClicks(Context c,RemoteViews v){
        Intent refresh=new Intent(c,PogodnikWeatherWidget.class).setAction(ACTION_REFRESH);
        v.setOnClickPendingIntent(R.id.widget_refresh,PendingIntent.getBroadcast(c,101,refresh,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE));
        setZoom(c,v,R.id.widget_zoom_in,1,301); setZoom(c,v,R.id.widget_zoom_out,-1,302);
        setLayer(c,v,R.id.widget_radar_button,"RADAR",201);
        setLayer(c,v,R.id.widget_lightning_button,"LIGHTNING",202);
        setLayer(c,v,R.id.widget_tsp_button,"TSP",203);
        setLayer(c,v,R.id.widget_sat_button,"SAT",204);
    }
    private static void setZoom(Context c,RemoteViews v,int id,int dz,int req){
        Intent i=new Intent(c,PogodnikWeatherWidget.class).setAction(ACTION_ZOOM).putExtra("dz",dz);
        v.setOnClickPendingIntent(id,PendingIntent.getBroadcast(c,req,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE));
    }
    private static void setLayer(Context c,RemoteViews v,int id,String layer,int req){
        Intent i=new Intent(c,PogodnikWeatherWidget.class).setAction(ACTION_LAYER).putExtra("layer",layer);
        v.setOnClickPendingIntent(id,PendingIntent.getBroadcast(c,req,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE));
    }
    private static android.content.SharedPreferences getPrefs(Context c){return c.getSharedPreferences(PREFS,Context.MODE_PRIVATE);}
}
