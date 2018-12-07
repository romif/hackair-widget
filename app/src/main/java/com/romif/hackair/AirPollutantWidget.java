package com.romif.hackair;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.TimeZone;

import static com.romif.hackair.AirPollutantWidgetConfigureActivity.PREF_PREFIX_KEY;

/**
 * Implementation of App Widget functionality.
 */
public class AirPollutantWidget extends AppWidgetProvider {

    static void updateAppWidget(final Context context, final AppWidgetManager appWidgetManager, final int appWidgetId) {
        final RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.air_pollutant_widget);

        Intent launchActivity = new Intent(context, AirPollutantWidgetConfigureActivity.class);
        launchActivity.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, appWidgetId, launchActivity, 0);
        views.setOnClickPendingIntent(R.id.appwidget, pendingIntent);

        TimeZone tz = TimeZone.getTimeZone("MSK");
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'");
        df.setTimeZone(tz);

        Calendar from = Calendar.getInstance();
        from.add(Calendar.MINUTE, -60);

        SharedPreferences prefs = context.getSharedPreferences(AirPollutantWidgetConfigureActivity.PREFS_NAME, 0);
        float longitude = prefs.getFloat(PREF_PREFIX_KEY + appWidgetId + "_longitude", 0f);
        float latitude = prefs.getFloat(PREF_PREFIX_KEY + appWidgetId + "_latitude", 0f);

        Uri.Builder builder = new Uri.Builder();
        double delta = 0.00001;
        builder.scheme("https")
                .authority("api.hackair.eu")
                .appendPath("measurements")
                .appendQueryParameter("location", (longitude - delta) + "," + (latitude - delta) + "|" + (longitude + delta) + "," + (latitude + delta))
                .appendQueryParameter("timestampStart", df.format(from.getTime()))
                //.appendQueryParameter("source", "sensors_arduino")
                .appendQueryParameter("pollutant", "pm2.5")
                .appendQueryParameter("show", "all");
        String url = builder.build().toString();
        //Log.d("AirPollutantWidget", url);
        RequestQueue queue = Volley.newRequestQueue(context);
        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(Request.Method.GET, url, null, new Response.Listener<JSONObject>() {

            @Override
            public void onResponse(JSONObject response) {
                //Log.d("AirPollutantWidget", response.toString());
                try {
                    JSONArray data = response.getJSONArray("data");
                    double value = 0d;
                    for (int i = 0; i < data.length(); i++) {
                        value = value + data.getJSONObject(i).getJSONObject("pollutant_q").getDouble("value");
                    }
                    double avgValue = value / data.length();
                    views.setTextViewText(R.id.appwidget_text, context.getString(R.string.pm2_5_avg_format, avgValue));
                    if (avgValue < 10) {
                        views.setTextViewText(R.id.appwidget_quality, context.getString(R.string.very_good));
                        views.setTextColor(R.id.appwidget_quality, Color.GREEN);
                        views.setTextColor(R.id.appwidget_text, Color.GREEN);
                    } else if (avgValue < 25) {
                        views.setTextViewText(R.id.appwidget_quality, context.getString(R.string.good));
                        views.setTextColor(R.id.appwidget_quality, Color.GREEN);
                        views.setTextColor(R.id.appwidget_text, Color.GREEN);
                    } else if (avgValue < 35) {
                        views.setTextViewText(R.id.appwidget_quality, context.getString(R.string.medium));
                        views.setTextColor(R.id.appwidget_quality, Color.YELLOW);
                        views.setTextColor(R.id.appwidget_text, Color.YELLOW);
                    } else {
                        views.setTextViewText(R.id.appwidget_quality, context.getString(R.string.bad));
                        views.setTextColor(R.id.appwidget_quality, Color.RED);
                        views.setTextColor(R.id.appwidget_text, Color.RED);
                    }
                } catch (JSONException e) {
                    Log.e("AirPollutantWidget", e.getLocalizedMessage(), e);
                }
                appWidgetManager.updateAppWidget(appWidgetId, views);
            }
        }, new Response.ErrorListener() {

            @Override
            public void onErrorResponse(VolleyError error) {
                Log.e("AirPollutantWidget", error.getLocalizedMessage(), error);
            }
        });

        queue.add(jsonObjectRequest);

        appWidgetManager.updateAppWidget(appWidgetId, views);
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        // There may be multiple widgets active, so update all of them
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
    }

}

