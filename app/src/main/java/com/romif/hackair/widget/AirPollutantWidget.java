package com.romif.hackair.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.util.Consumer;
import android.support.v4.util.Pair;
import android.util.Log;
import android.widget.RemoteViews;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.Volley;
import com.crashlytics.android.Crashlytics;
import com.google.firebase.analytics.FirebaseAnalytics;

import org.apache.commons.math3.stat.descriptive.SummaryStatistics;
import org.apache.commons.math3.stat.regression.SimpleRegression;
import org.json.JSONArray;
import org.json.JSONException;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

import static com.romif.hackair.widget.AirPollutantWidgetConfigureActivity.PREF_PREFIX_KEY;

public abstract class AirPollutantWidget extends AppWidgetProvider {

    private static LocationDto getLocationDto(Context context, int appWidgetId) {
        final SharedPreferences prefs = context.getSharedPreferences(AirPollutantWidgetConfigureActivity.PREFS_NAME, 0);
        float longitude = prefs.getFloat(PREF_PREFIX_KEY + appWidgetId + "_longitude", 0f);
        float latitude = prefs.getFloat(PREF_PREFIX_KEY + appWidgetId + "_latitude", 0f);
        String address = prefs.getString(PREF_PREFIX_KEY + appWidgetId + "_address", "");
        LocationDto locationDto = new LocationDto(longitude, latitude, address);
        locationDto.setSenseBoxId(prefs.getString(PREF_PREFIX_KEY + appWidgetId + "_senseBoxId", ""));
        locationDto.setSensorId(prefs.getString(PREF_PREFIX_KEY + appWidgetId + "_sensorId", ""));
        return locationDto;
    }

    private static void getPollutant(final Context context, final int appWidgetId, final Consumer<Pair<Double, Double>> pollutantConsumer) {
        LocationDto locationDto = getLocationDto(context, appWidgetId);
        TimeZone tz = TimeZone.getTimeZone("MSK");
        final SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        df.setTimeZone(tz);

        Calendar from = Calendar.getInstance();
        from.add(Calendar.MINUTE, -60);

        final FirebaseAnalytics mFirebaseAnalytics = FirebaseAnalytics.getInstance(context);

        Uri.Builder builder = new Uri.Builder();
        builder.scheme("https")
                .authority("api.opensensemap.org")
                .appendPath("boxes")
                .appendPath(locationDto.getSenseBoxId())
                .appendPath("data")
                .appendPath(locationDto.getSensorId())
                .appendQueryParameter("from-date", df.format(from.getTime()));
        String url = builder.build().toString();
        //Log.d("AirPollutantFullWidget", url);
        RequestQueue queue = Volley.newRequestQueue(context);
        JsonArrayRequest jsonObjectRequest = new JsonArrayRequest(Request.Method.GET, url, null, new Response.Listener<JSONArray>() {

            @Override
            public void onResponse(JSONArray response) {
                //Log.d("AirPollutantFullWidget", response.toString());
                try {
                    SimpleRegression simpleRegression = new SimpleRegression();
                    SummaryStatistics stats = new SummaryStatistics();
                    for (int i = 0; i < response.length(); i++) {
                        Date createdAt = df.parse(response.getJSONObject(i).getString("createdAt"));
                        simpleRegression.addData(createdAt.getTime(), response.getJSONObject(i).getDouble("value"));
                        stats.addValue(response.getJSONObject(i).getDouble("value"));
                    }
                    Double slope = simpleRegression.getSlope();
                    double mean = stats.getMean();
                    pollutantConsumer.accept(Pair.create(mean, slope));
                } catch (JSONException | ParseException e) {
                    Log.e("AirPollutantWidget", e.getLocalizedMessage(), e);
                    log("getPollutant", e, mFirebaseAnalytics);
                    pollutantConsumer.accept(null);
                }
            }
        }, new Response.ErrorListener() {

            @Override
            public void onErrorResponse(VolleyError error) {
                Log.e("AirPollutantWidget", error.getLocalizedMessage(), error);
                log("getPollutant", error, mFirebaseAnalytics);
                pollutantConsumer.accept(null);
            }
        });

        jsonObjectRequest.setRetryPolicy(new DefaultRetryPolicy(DefaultRetryPolicy.DEFAULT_TIMEOUT_MS, 10, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));

        queue.add(jsonObjectRequest);
    }

    private static void log(String event, Throwable throwable, FirebaseAnalytics mFirebaseAnalytics) {
        Bundle params = new Bundle();
        params.putString("error", throwable.getLocalizedMessage() != null ? throwable.getLocalizedMessage().substring(0, Math.min(throwable.getLocalizedMessage().length() - 1, 100)) : "");
        mFirebaseAnalytics.logEvent(event, params);
        Crashlytics.logException(throwable);
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
            final RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.air_pollutant_full_widget);

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
                    views.setTextViewText(R.id.appwidget_lastupdate, context.getString(R.string.time_format, new Date()));
                    views.setTextColor(R.id.appwidget_text, color);
                    views.setTextColor(R.id.appwidget_lastupdate, color);

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
                    views.setTextViewText(R.id.appwidget_lastupdate, context.getString(R.string.last_update, new Date()));
                    views.setTextColor(R.id.appwidget_text, color);
                    views.setTextColor(R.id.appwidget_lastupdate, color);

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

