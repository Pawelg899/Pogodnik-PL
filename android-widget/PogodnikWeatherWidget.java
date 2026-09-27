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
    private static final String PREFS="PogodnikWidgetPrefs";
    private static final ExecutorService EXECUTOR=Executors.newSingleThreadExecutor();
    private static final int Z=6, TILE=256;

    @Override public void onUpdate(Context c, AppWidgetManager m, int[] ids){
        for(int id:ids) showLoading(c,m,id);
        update(c,m,ids);
    }
    @Override public void onAppWidgetOptionsChanged(Context c,AppWidgetManager m,int id,Bundle o){
        super.onAppWidgetOptionsChanged(c,m,id,o); update(c,m,new int[]{id});
    }
    @Override public void onReceive(Context c,Intent i){
        super.onReceive(c,i);
        if(ACTION_REFRESH.equals(i.getAction())){
            AppWidgetManager m=AppWidgetManager.getInstance(c);
            int[] ids=m.getAppWidgetIds(new android.content.ComponentName(c,PogodnikWeatherWidget.class));
            update(c,m,ids);
        }
    }
    public static void updateAll(Context c){
        AppWidgetManager m=AppWidgetManager.getInstance(c);
        int[] ids=m.getAppWidgetIds(new android.content.ComponentName(c,PogodnikWeatherWidget.class));
        if(ids.length>0) update(c,m,ids);
    }

    private static void showLoading(Context c,AppWidgetManager m,int id){
        RemoteViews v=new RemoteViews(c.getPackageName(),R.layout.widget_weather);
        v.setImageViewBitmap(R.id.widget_canvas, dashboard(null,null,null,null,null,null,null,null,null,null));
        setClick(c,v); m.updateAppWidget(id,v);
    }

    private static void update(Context c,AppWidgetManager m,int[] ids){
        if(ids==null||ids.length==0)return;
        final Context app=c.getApplicationContext();
        final float plat=getPrefs(app).getFloat("lat",52.07f), plon=getPrefs(app).getFloat("lon",19.48f);
        EXECUTOR.execute(()->{
            JSONObject cur=null,daily=null; Bitmap radar=null;
            try{
                String u="https://api.open-meteo.com/v1/forecast?latitude="+plat+"&longitude="+plon+
                    "&timezone=auto&forecast_days=5"+
                    "&current=temperature_2m,apparent_temperature,weather_code,precipitation,precipitation_probability,wind_speed_10m,wind_direction_10m,wind_gusts_10m,pressure_msl,relative_humidity_2m,cloud_cover"+
                    "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_sum,precipitation_probability_max";
                JSONObject d=getJson(u); cur=d.getJSONObject("current"); daily=d.getJSONObject("daily");
                radar=buildRadar(plat,plon);
            }catch(Exception ignored){}
            Bitmap image=dashboard(cur,daily,radar,getPrefs(app).getString("name","Moja lokalizacja"),
                    plat,plon,null,null,null,null);
            android.os.Handler h=new android.os.Handler(android.os.Looper.getMainLooper());
            final Bitmap out=image;
            h.post(()->{for(int id:ids){RemoteViews v=new RemoteViews(app.getPackageName(),R.layout.widget_weather);v.setImageViewBitmap(R.id.widget_canvas,out);setClick(app,v);m.updateAppWidget(id,v);}});
        });
    }

    private static Bitmap dashboard(JSONObject c,JSONObject d,Bitmap radar,String place,Float lat,Float lon,Object a,Object b,Object e,Object f){
        final int W=720,H=1400;
        Bitmap bmap=Bitmap.createBitmap(W,H,Bitmap.Config.ARGB_8888); Canvas x=new Canvas(bmap);
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG); p.setTypeface(Typeface.create("sans",Typeface.NORMAL));
        LinearGradient bg=new LinearGradient(0,0,W,H,new int[]{Color.rgb(9,22,40),Color.rgb(3,13,25),Color.rgb(8,25,43)},null,Shader.TileMode.CLAMP);p.setShader(bg);x.drawRect(0,0,W,H,p);p.setShader(null);
        // subtle sunset/cloud background matching the reference
        p.setColor(Color.rgb(70,48,66));x.drawCircle(600,70,150,p);p.setColor(Color.rgb(30,45,65));x.drawCircle(120,90,180,p);
        round(x,p,22,22,W-22,1100,Color.argb(210,4,18,32),Color.rgb(45,77,105),2);
        int white=Color.WHITE,muted=Color.rgb(180,198,218),blue=Color.rgb(55,166,255);
        text(x,p,place==null?"Moja lokalizacja":place,42,70,30,white,true);
        text(x,p,"Aktualizacja  •  "+new SimpleDateFormat("HH:mm",Locale.getDefault()).format(new Date()),42,96,15,muted,false);
        text(x,p,"↻",640,80,42,white,false);

        int temp= c==null?15:(int)Math.round(c.optDouble("temperature_2m",15));
        int code=c==null?3:c.optInt("weather_code",3);
        text(x,p,icon(code),70,205,72,white,false);
        text(x,p,temp+"°C",70,290,78,white,true);
        text(x,p,condition(code),75,330,27,white,true);
        int feels=c==null?temp:(int)Math.round(c.optDouble("apparent_temperature",temp));
        text(x,p,"Odczuwalna "+feels+"°C",75,360,20,Color.rgb(130,184,235),false);

        float[] div={190,295,400,505}; for(float q:div){p.setColor(Color.rgb(45,70,92));x.drawRect(q,385,q+1,535,p);}
        metric(x,p,"➤","Wiatr",c==null?"—":("z "+wind(c.optDouble("wind_direction_10m",0))),c==null?"":Math.round(c.optDouble("wind_speed_10m",0))+" km/h",75,white,muted);
        metric(x,p,"◆","Opad (1h)",c==null?"—":one(c.optDouble("precipitation",0))+" mm",c==null?"":Math.round(c.optDouble("precipitation_probability",0))+"%",205,white,blue);
        metric(x,p,"◷","Ciśnienie",c==null?"—":Math.round(c.optDouble("pressure_msl",0))+" hPa","",310,white,muted);
        metric(x,p,"◆","Wilgotność",c==null?"—":Math.round(c.optDouble("relative_humidity_2m",0))+"%","",415,white,blue);
        metric(x,p,"☁","Zachmurzenie",c==null?"—":Math.round(c.optDouble("cloud_cover",0))+"%","",520,white,muted);

        int mapTop=555,mapH=365;
        round(x,p,38,mapTop,W-38,mapTop+mapH,Color.rgb(7,25,40),Color.rgb(45,77,105),2);
        if(radar!=null)x.drawBitmap(radar,null,new RectF(40,mapTop+45,W-40,mapTop+mapH-8),p);
        else {p.setColor(Color.rgb(15,43,62));x.drawRect(40,mapTop+45,W-40,mapTop+mapH-8,p);}
        pill(x,p,"RADAR",58,mapTop+12,blue);pill(x,p,"WYŁADOWANIA",155,mapTop+12,Color.rgb(8,28,45));pill(x,p,"TSP",315,mapTop+12,Color.rgb(8,28,45));pill(x,p,"SAT",385,mapTop+12,Color.rgb(8,28,45));
        p.setColor(Color.WHITE);x.drawCircle(360,mapTop+205,15,p);p.setColor(Color.rgb(35,140,255));x.drawCircle(360,mapTop+205,10,p);

        text(x,p,"NAJBLIŻSZE DNI  ›",42,965,22,white,true);
        if(d!=null){
            JSONArray times=d.optJSONArray("time"), codes=d.optJSONArray("weather_code"), max=d.optJSONArray("temperature_2m_max"), min=d.optJSONArray("temperature_2m_min"), prob=d.optJSONArray("precipitation_probability_max");
            for(int i=0;i<5;i++){
                int xx=50+i*130; String lab=times==null?"—":dayLabel(times.optString(i),i);
                text(x,p,lab,xx,1015,13,muted,true);
                text(x,p,icon(codes==null?3:codes.optInt(i,3)),xx+20,1060,34,white,false);
                text(x,p,(max==null?"--":Math.round(max.optDouble(i,0)))+"°",xx,1100,22,white,true);
                text(x,p,(min==null?"--":Math.round(min.optDouble(i,0)))+"°",xx+42,1100,16,muted,false);
                text(x,p,(prob==null?"--":Math.round(prob.optDouble(i,0)))+"%",xx+12,1130,13,blue,false);
            }
        }
        return bmap;
    }

    private static void metric(Canvas x,Paint p,String ico,String title,String value,String sub,int xx,int wc,int vc){
        text(x,p,ico,xx,425,22,wc,false);text(x,p,title,xx,450,13,Color.rgb(180,198,218),false);text(x,p,value,xx,480,15,wc,true);if(!sub.isEmpty())text(x,p,sub,xx,510,13,vc,false);
    }
    private static void pill(Canvas x,Paint p,String s,int xx,int yy,int col){round(x,p,xx,yy,xx+(s.length()*8+30),yy+38,Color.argb(225,col==Color.rgb(55,166,255)?55:8,28,45),col,1);text(x,p,s,xx+15,yy+25,12,Color.WHITE,true);}
    private static void round(Canvas x,Paint p,float l,float t,float r,float b,int fill,int stroke,float sw){p.setStyle(Paint.Style.FILL);p.setColor(fill);x.drawRoundRect(l,t,r,b,22,22,p);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(sw);p.setColor(stroke);x.drawRoundRect(l,t,r,b,22,22,p);p.setStyle(Paint.Style.FILL);}
    private static void text(Canvas x,Paint p,String s,float xx,float yy,float size,int col,boolean bold){p.setShader(null);p.setColor(col);p.setTextSize(size);p.setTypeface(Typeface.create("sans",bold?Typeface.BOLD:Typeface.NORMAL));x.drawText(s,xx,yy,p);}
    private static String one(double n){return String.format(Locale.US,"%.1f",n);}
    private static String wind(double d){String[] a={"północy","północnego wschodu","wschodu","południowego wschodu","południa","południowego zachodu","zachodu","północnego zachodu"};return a[(int)Math.round((((d%360)+360)%360)/45)%8];}
    private static String dayLabel(String s,int i){if(i==0)return"DZIŚ";try{return new SimpleDateFormat("EEE",new Locale("pl","PL")).format(new SimpleDateFormat("yyyy-MM-dd",Locale.US).parse(s)).toUpperCase(new Locale("pl","PL")).replace(".","");}catch(Exception e){return"—";}}
    private static String condition(int c){if(c==0)return"Bezchmurnie";if(c==1)return"Głównie bezchmurnie";if(c==2)return"Częściowe zachmurzenie";if(c==3)return"Pochmurno";if(c>=45&&c<=48)return"Mgła";if(c>=51&&c<=55)return"Mżawka";if(c>=61&&c<=82)return"Deszcz";if(c>=71&&c<=77)return"Śnieg";if(c>=95)return"Burza";return"Pogoda";}
    private static String icon(int c){if(c==0)return"☀";if(c==1)return"🌤";if(c==2)return"⛅";if(c==3)return"☁";if(c>=45&&c<=48)return"🌫";if(c>=51&&c<=67)return"🌧";if(c>=71&&c<=77)return"❄";if(c>=80&&c<=82)return"🌦";if(c>=95)return"⛈";return"•";}

    private static Bitmap buildRadar(double lat,double lon){
        try{
            JSONObject meta=getJson("https://api.rainviewer.com/public/weather-maps.json");JSONArray past=meta.getJSONObject("radar").getJSONArray("past");if(past.length()==0)return null;
            String host=meta.getString("host"),path=past.getJSONObject(past.length()-1).getString("path");int tx=lon2tile(lon,Z),ty=lat2tile(lat,Z);
            Bitmap base=Bitmap.createBitmap(512,512,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(base);
            for(int dx=-1;dx<=0;dx++)for(int dy=-1;dy<=0;dy++){int xx=tx+dx,yy=ty+dy;Bitmap m=download("https://tile.openstreetmap.org/"+Z+"/"+xx+"/"+yy+".png");if(m!=null)c.drawBitmap(m,(dx+1)*256,(dy+1)*256,null);Bitmap r=download(host+path+"/256/"+Z+"/"+xx+"/"+yy+"/2/1_0.png");if(r!=null)c.drawBitmap(r,(dx+1)*256,(dy+1)*256,null);}
            Paint p=new Paint(1);p.setColor(Color.WHITE);c.drawCircle(256,256,14,p);p.setColor(Color.rgb(35,140,255));c.drawCircle(256,256,9,p);return base;
        }catch(Exception e){return null;}
    }
    private static Bitmap download(String u){HttpURLConnection c=null;try{c=(HttpURLConnection)new URL(u).openConnection();c.setConnectTimeout(5000);c.setReadTimeout(5000);c.setRequestProperty("User-Agent","Pogodnik-PL");if(c.getResponseCode()/100!=2)return null;ByteArrayOutputStream o=new ByteArrayOutputStream();try(InputStream in=c.getInputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))>0)o.write(b,0,n);}return BitmapFactory.decodeByteArray(o.toByteArray(),0,o.size());}catch(Exception e){return null;}finally{if(c!=null)c.disconnect();}}
    private static JSONObject getJson(String u)throws Exception{HttpURLConnection c=(HttpURLConnection)new URL(u).openConnection();c.setConnectTimeout(8000);c.setReadTimeout(8000);try(BufferedReader r=new BufferedReader(new InputStreamReader(c.getInputStream()))){StringBuilder s=new StringBuilder();String z;while((z=r.readLine())!=null)s.append(z);return new JSONObject(s.toString());}finally{c.disconnect();}}
    private static int lon2tile(double lon,int z){return(int)Math.floor((lon+180)/360*(1<<z));}
    private static int lat2tile(double lat,int z){return(int)Math.floor((1-Math.log(Math.tan(Math.toRadians(lat))+1/Math.cos(Math.toRadians(lat)))/Math.PI)/2*(1<<z));}
    private static void setClick(Context c,RemoteViews v){Intent i=c.getPackageManager().getLaunchIntentForPackage(c.getPackageName());if(i!=null){PendingIntent p=PendingIntent.getActivity(c,102,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);v.setOnClickPendingIntent(R.id.widget_root,p);}}
    private static android.content.SharedPreferences getPrefs(Context c){return c.getSharedPreferences(PREFS,Context.MODE_PRIVATE);}
}