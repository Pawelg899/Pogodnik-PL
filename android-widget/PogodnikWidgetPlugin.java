package pl.pogodnik.app;

import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.JSObject;

import android.content.Context;

@CapacitorPlugin(name = "PogodnikWidget")
public class PogodnikWidgetPlugin extends Plugin {
    @com.getcapacitor.PluginMethod
    public void setLocation(PluginCall call) {
        double lat = call.getDouble("lat", 52.07);
        double lon = call.getDouble("lon", 19.48);
        String name = call.getString("name", "Moja lokalizacja");
        getContext().getSharedPreferences("PogodnikWidgetPrefs", Context.MODE_PRIVATE)
                .edit().putFloat("lat", (float)lat).putFloat("lon", (float)lon).putString("name", name).apply();
        PogodnikWeatherWidget.updateAll(getContext());
        call.resolve();
    }

    @com.getcapacitor.PluginMethod
    public void refresh(PluginCall call) {
        PogodnikWeatherWidget.updateAll(getContext());
        call.resolve();
    }
}
