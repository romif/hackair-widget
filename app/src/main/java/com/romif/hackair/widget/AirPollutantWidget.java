package com.romif.hackair.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.support.v4.util.Consumer;
import android.support.v4.util.Pair;
import android.util.Log;
import android.widget.RemoteViews;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.apache.commons.math3.stat.descriptive.SummaryStatistics;
import org.apache.commons.math3.stat.regression.SimpleRegression;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

import static com.romif.hackair.widget.AirPollutantWidgetConfigureActivity.PREFS_NAME;
import static com.romif.hackair.widget.AirPollutantWidgetConfigureActivity.PREF_PREFIX_KEY;

public abstract class AirPollutantWidget extends AppWidgetProvider {

    private static LocationDto getLocationDto(Context context, int appWidgetId) {
        final SharedPreferences prefs = context.getSharedPreferences(AirPollutantWidgetConfigureActivity.PREFS_NAME, 0);
        float longitude = prefs.getFloat(PREF_PREFIX_KEY + appWidgetId + "_longitude", 0f);
        float latitude = prefs.getFloat(PREF_PREFIX_KEY + appWidgetId + "_latitude", 0f);
        String address = prefs.getString(PREF_PREFIX_KEY + appWidgetId + "_address", "");
        return new LocationDto(longitude, latitude, address);
    }

    private static void getPollutant(final Context context, final int appWidgetId, final Consumer<Pair<Double, Double>> pollutantConsumer) {
        LocationDto locationDto = getLocationDto(context, appWidgetId);
        TimeZone tz = TimeZone.getTimeZone("MSK");
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'");
        df.setTimeZone(tz);

        Calendar from = Calendar.getInstance();
        from.add(Calendar.MINUTE, -60);

        Uri.Builder builder = new Uri.Builder();
        double delta = 0.00001;
        builder.scheme("https")
                .authority("api.hackair.eu")
                .appendPath("measurements")
                .appendQueryParameter("location", (locationDto.getLongitude() - delta) + "," + (locationDto.getLatitude() - delta) + "|" + (locationDto.getLongitude() + delta) + "," + (locationDto.getLatitude() + delta))
                .appendQueryParameter("timestampStart", df.format(from.getTime()))
                //.appendQueryParameter("source", "sensors_arduino")
                .appendQueryParameter("pollutant", "pm2.5")
                .appendQueryParameter("show", "all");
        String url = builder.build().toString();
        //Log.d("AirPollutantFullWidget", url);
        RequestQueue queue = Volley.newRequestQueue(context);
        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(Request.Method.GET, url, null, new Response.Listener<JSONObject>() {

            @Override
            public void onResponse(JSONObject response) {
                //Log.d("AirPollutantFullWidget", response.toString());
                try {
                    JSONArray data = response.getJSONArray("data");
                    SimpleRegression simpleRegression = new SimpleRegression();
                    SummaryStatistics stats = new SummaryStatistics();
                    for (int i = 0; i < data.length(); i++) {
                        simpleRegression.addData(data.getJSONObject(i).getDouble("datetime"), data.getJSONObject(i).getJSONObject("pollutant_q").getDouble("value"));
                        stats.addValue(data.getJSONObject(i).getJSONObject("pollutant_q").getDouble("value"));
                    }
                    Double slope = simpleRegression.getSlope();
                    double mean = stats.getMean();
                    pollutantConsumer.accept(Pair.create(mean, slope));

                    SharedPreferences.Editor editor = context.getSharedPreferences(PREFS_NAME, 0).edit();
                    editor.putInt(PREF_PREFIX_KEY + appWidgetId + "_attemptsNumber", 0);
                    editor.apply();

                } catch (JSONException e) {
                    handleError(e, context, appWidgetId, pollutantConsumer);
                }
            }
        }, new Response.ErrorListener() {

            @Override
            public void onErrorResponse(VolleyError error) {
                handleError(error, context, appWidgetId, pollutantConsumer);
            }
        });

        queue.add(jsonObjectRequest);
    }

    private static void handleError(Exception error, Context context, int appWidgetId, Consumer<Pair<Double, Double>> pollutantConsumer) {
        Log.e("AirPollutantFullWidget", error.getLocalizedMessage(), error);
        final SharedPreferences prefs = context.getSharedPreferences(AirPollutantWidgetConfigureActivity.PREFS_NAME, 0);
        int attemptsNumber = prefs.getInt(PREF_PREFIX_KEY + appWidgetId + "_attemptsNumber", 0);
        SharedPreferences.Editor editor = context.getSharedPreferences(PREFS_NAME, 0).edit();
        if (attemptsNumber < 3) {
            editor.putInt(PREF_PREFIX_KEY + appWidgetId + "_attemptsNumber", ++attemptsNumber);
            editor.apply();
            getPollutant(context, appWidgetId, pollutantConsumer);
        } else {
            editor.putInt(PREF_PREFIX_KEY + appWidgetId + "_attemptsNumber", 0);
            editor.apply();
            pollutantConsumer.accept(null);
        }
    }

    private static String getQuality(Double avgValue, Context context) {
        if (avgValue < 10) {
            return context.getString(R.string.very_good);
        } else if (avgValue < 25) {
            return context.getString(R.string.good);
        } else if (avgValue < 35) {
            return context.getString(R.string.medium);
        } else {
            return context.getString(R.string.bad);
        }
    }

    protected static int getColor(Double avgValue) {
        if (avgValue < 10) {
            return Color.GREEN;
        } else if (avgValue < 25) {
            return Color.GREEN;
        } else if (avgValue < 35) {
            return Color.YELLOW;
        } else {
            return Color.RED;
        }
    }

    public static class AirPollutantFullWidget extends AirPollutantWidget {

        static void updateAppWidget(final Context context, final AppWidgetManager appWidgetManager, final int appWidgetId) {
            final RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.air_pollutant_widget);

            Intent launchActivity = new Intent(context, AirPollutantWidgetConfigureActivity.AirPollutantFullWidgetConfigureActivity.class);
            launchActivity.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
            PendingIntent pendingIntent = PendingIntent.getActivity(context, appWidgetId, launchActivity, 0);
            views.setOnClickPendingIntent(R.id.full_appwidget, pendingIntent);
            appWidgetManager.updateAppWidget(appWidgetId, views);

            getPollutant(context, appWidgetId, new Consumer<Pair<Double, Double>>() {
                @Override
                public void accept(Pair<Double, Double> avgValue) {
                    if (avgValue == null) {
                        views.setTextViewText(R.id.appwidget_text, context.getString(R.string.error_fetching_pollutant_data));
                        appWidgetManager.updateAppWidget(appWidgetId, views);
                        return;
                    }
                    final SharedPreferences prefs = context.getSharedPreferences(AirPollutantWidgetConfigureActivity.PREFS_NAME, 0);
                    views.setTextViewText(R.id.appwidget_text, context.getString(R.string.pm2_5_avg_format, avgValue.first));
                    int color = getColor(avgValue.first);
                    String quality = getQuality(avgValue.first, context);
                    views.setTextViewText(R.id.appwidget_quality, quality + " " + context.getString(R.string.mpc, avgValue.first / 25));
                    views.setTextViewText(R.id.appwidget_lastupdate, context.getString(R.string.last_update, new Date()));
                    views.setTextColor(R.id.appwidget_quality, color);
                    views.setTextColor(R.id.appwidget_text, color);
                    views.setTextColor(R.id.appwidget_location, color);
                    views.setTextColor(R.id.appwidget_lastupdate, color);

                    if (avgValue.second > 0) {
                        views.setImageViewResource(R.id.imageTrend, R.drawable.ic_arrow_up);
                        views.setInt(R.id.imageTrend, "setColorFilter", Color.RED);
                    } else {
                        views.setImageViewResource(R.id.imageTrend, R.drawable.ic_arrow_down);
                        views.setInt(R.id.imageTrend, "setColorFilter", Color.GREEN);
                    }

                    LocationDto locationDto = getLocationDto(context, appWidgetId);
                    views.setTextViewText(R.id.appwidget_location, locationDto.getAddress());

                    appWidgetManager.updateAppWidget(appWidgetId, views);
                }
            });

        }

        @Override
        public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
            // There may be multiple widgets active, so update all of them
            for (int appWidgetId : appWidgetIds) {
                updateAppWidget(context, appWidgetManager, appWidgetId);
            }
        }

    }

    public static class AirPollutantLightValueWidget extends AirPollutantWidget {

        static void updateAppWidget(final Context context, final AppWidgetManager appWidgetManager, final int appWidgetId) {
            final RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.air_pollutant_light_value_widget);

            Intent launchActivity = new Intent(context, AirPollutantWidgetConfigureActivity.AirPollutantLightValueWidgetConfigureActivity.class);
            launchActivity.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
            PendingIntent pendingIntent = PendingIntent.getActivity(context, appWidgetId, launchActivity, 0);
            views.setOnClickPendingIntent(R.id.light_value_appwidget, pendingIntent);
            appWidgetManager.updateAppWidget(appWidgetId, views);

            getPollutant(context, appWidgetId, new Consumer<Pair<Double, Double>>() {
                @Override
                public void accept(Pair<Double, Double> avgValue) {
                    if (avgValue == null) {
                        views.setTextViewText(R.id.appwidget_text, context.getString(R.string.error_fetching_pollutant_data));
                        appWidgetManager.updateAppWidget(appWidgetId, views);
                        return;
                    }
                    views.setTextViewText(R.id.appwidget_text, context.getString(R.string.avg_format, avgValue.first));
                    int color = getColor(avgValue.first);
                    views.setTextColor(R.id.appwidget_text, color);

                    if (avgValue.second > 0) {
                        views.setImageViewResource(R.id.imageTrend, R.drawable.ic_arrow_up);
                        views.setInt(R.id.imageTrend, "setColorFilter", Color.RED);
                    } else {
                        views.setImageViewResource(R.id.imageTrend, R.drawable.ic_arrow_down);
                        views.setInt(R.id.imageTrend, "setColorFilter", Color.GREEN);
                    }

                    appWidgetManager.updateAppWidget(appWidgetId, views);
                }
            });

        }

        @Override
        public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
            // There may be multiple widgets active, so update all of them
            for (int appWidgetId : appWidgetIds) {
                updateAppWidget(context, appWidgetManager, appWidgetId);
            }
        }

    }

    public static class AirPollutantLightQualityWidget extends AirPollutantWidget {

        static void updateAppWidget(final Context context, final AppWidgetManager appWidgetManager, final int appWidgetId) {
            final RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.air_pollutant_light_quality_widget);

            Intent launchActivity = new Intent(context, AirPollutantWidgetConfigureActivity.AirPollutantLightQualityWidgetConfigureActivity.class);
            launchActivity.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
            PendingIntent pendingIntent = PendingIntent.getActivity(context, appWidgetId, launchActivity, 0);
            views.setOnClickPendingIntent(R.id.light_quality_appwidget, pendingIntent);
            appWidgetManager.updateAppWidget(appWidgetId, views);

            getPollutant(context, appWidgetId, new Consumer<Pair<Double, Double>>() {
                @Override
                public void accept(Pair<Double, Double> avgValue) {
                    if (avgValue == null) {
                        views.setTextViewText(R.id.appwidget_text, context.getString(R.string.error_fetching_pollutant_data));
                        appWidgetManager.updateAppWidget(appWidgetId, views);
                        return;
                    }
                    int color = getColor(avgValue.first);
                    String quality = getQuality(avgValue.first, context);
                    views.setTextViewText(R.id.appwidget_text, quality);
                    views.setTextColor(R.id.appwidget_text, color);

                    if (avgValue.second > 0) {
                        views.setImageViewResource(R.id.imageTrend, R.drawable.ic_arrow_up);
                        views.setInt(R.id.imageTrend, "setColorFilter", Color.RED);
                    } else {
                        views.setImageViewResource(R.id.imageTrend, R.drawable.ic_arrow_down);
                        views.setInt(R.id.imageTrend, "setColorFilter", Color.GREEN);
                    }

                    appWidgetManager.updateAppWidget(appWidgetId, views);
                }
            });

        }

        @Override
        public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
            // There may be multiple widgets active, so update all of them
            for (int appWidgetId : appWidgetIds) {
                updateAppWidget(context, appWidgetManager, appWidgetId);
            }
        }

    }

}

