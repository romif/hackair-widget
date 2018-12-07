package com.romif.hackair;

import android.Manifest;
import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.ResultReceiver;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnSuccessListener;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.TimeZone;

public class AirPollutantWidgetConfigureActivity extends Activity {

    public static final String PREFS_NAME = "com.romif.hackair.AirPollutantWidget";
    public static final String PREF_PREFIX_KEY = "appwidget_";
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'");
    int mAppWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    private FusedLocationProviderClient mFusedLocationClient;
    private Spinner spinnerLocations;
    View.OnClickListener mOnClickListener = new View.OnClickListener() {
        public void onClick(View v) {
            final Context context = AirPollutantWidgetConfigureActivity.this;

            spinnerLocations = findViewById(R.id.spinner_locations);
            LocationDto locationDto = (LocationDto) spinnerLocations.getSelectedItem();
            saveLocationPref(context, mAppWidgetId, locationDto);

            // It is the responsibility of the configuration activity to update the app widget
            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
            AirPollutantWidget.updateAppWidget(context, appWidgetManager, mAppWidgetId);

            // Make sure we pass back the original appWidgetId
            Intent resultValue = new Intent();
            resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, mAppWidgetId);
            setResult(RESULT_OK, resultValue);
            finish();
        }
    };
    private AddressResultReceiver mResultReceiver;

    private Location coarseLocation;

    public AirPollutantWidgetConfigureActivity() {
        super();
    }

    // Write the prefix to the SharedPreferences object for this widget
    static void saveLocationPref(Context context, int appWidgetId, LocationDto locationDto) {
        SharedPreferences.Editor prefs = context.getSharedPreferences(PREFS_NAME, 0).edit();
        prefs.putFloat(PREF_PREFIX_KEY + appWidgetId + "_longitude", Double.valueOf(locationDto.getLongitude()).floatValue());
        prefs.putFloat(PREF_PREFIX_KEY + appWidgetId + "_latitude", Double.valueOf(locationDto.getLatitude()).floatValue());
        prefs.apply();
    }

    // Read the prefix from the SharedPreferences object for this widget.
    // If there is no preference saved, get the default from a resource
    static LocationDto loadLocationPref(Context context, int appWidgetId) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, 0);
        LocationDto locationDto = new LocationDto(prefs.getFloat(PREF_PREFIX_KEY + appWidgetId + "_longitude", 0f), prefs.getFloat(PREF_PREFIX_KEY + appWidgetId + "_latitude", 0f), "");
        if (locationDto.getLatitude() == 0 && locationDto.getLongitude() == 0) {
            return null;
        }
        return locationDto;
    }

    static void deleteTitlePref(Context context, int appWidgetId) {
        SharedPreferences.Editor prefs = context.getSharedPreferences(PREFS_NAME, 0).edit();
        prefs.remove(PREF_PREFIX_KEY + appWidgetId);
        prefs.apply();
    }

    private static void selectSpinnerItemByValue(Spinner spnr, LocationDto value) {
        ArrayAdapter<LocationDto> adapter = (ArrayAdapter) spnr.getAdapter();
        for (int position = 0; position < adapter.getCount(); position++) {
            if (adapter.getItem(position).equals(value)) {
                spnr.setSelection(position);
                return;
            }
        }
    }

    protected void startIntentService(Location mLastLocation) {
        Intent intent = new Intent(this, FetchAddressIntentService.class);
        intent.putExtra(Constants.RECEIVER, mResultReceiver);
        intent.putExtra(Constants.LOCATION_DATA_EXTRA, mLastLocation);
        startService(intent);
    }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        mResultReceiver = new AddressResultReceiver(new Handler());

        // Set the result to CANCELED.  This will cause the widget host to cancel
        // out of the widget placement if the user presses the back button.
        setResult(RESULT_CANCELED);

        setContentView(R.layout.air_pollutant_widget_configure);
        findViewById(R.id.add_button).setOnClickListener(mOnClickListener);

        // Find the widget id from the intent.
        Intent intent = getIntent();
        Bundle extras = intent.getExtras();
        if (extras != null) {
            mAppWidgetId = extras.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        }
        // If this activity was started with an intent without an app widget ID, finish with an error.
        if (mAppWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish();
            return;
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 999);
            return;
        }
        mFusedLocationClient.getLastLocation().addOnSuccessListener(this, new OnSuccessListener<Location>() {
            @Override
            public void onSuccess(final Location coarseLocation) {
                AirPollutantWidgetConfigureActivity.this.coarseLocation = coarseLocation;
                fillLocations();
            }
        });
    }

    private void fillLocations() {
        TimeZone tz = TimeZone.getTimeZone("MSK");
        DATE_FORMAT.setTimeZone(tz);

        Calendar from = Calendar.getInstance();
        from.add(Calendar.MINUTE, -60);
        Uri.Builder builder = new Uri.Builder();
        builder.scheme("https")
                .authority("api.hackair.eu")
                .appendPath("measurements")
                .appendQueryParameter("timestampStart", DATE_FORMAT.format(from.getTime()))
                .appendQueryParameter("pollutant", "pm2.5")
                .appendQueryParameter("show", "latest");
        if (coarseLocation != null && coarseLocation.getLatitude() > 0 && coarseLocation.getLongitude() > 0) {
            builder.appendQueryParameter("location", (coarseLocation.getLongitude() - 20) + "," + (coarseLocation.getLatitude() - 20) + "|" + (coarseLocation.getLongitude() + 20) + "," + (coarseLocation.getLatitude() + 20));
        }
        String url = builder.build().toString();
        RequestQueue queue = Volley.newRequestQueue(AirPollutantWidgetConfigureActivity.this);
        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(Request.Method.GET, url, null, new Response.Listener<JSONObject>() {

            @Override
            public void onResponse(JSONObject response) {
                try {
                    JSONArray data = response.getJSONArray("data");
                    Set<Pair<Double, Double>> locationSet = new HashSet<>();
                    for (int i = 0; i < data.length(); i++) {
                        if (!"Point".equals(data.getJSONObject(i).getJSONObject("loc").getString("type"))) {
                            continue;
                        }
                        JSONArray coordinates = data.getJSONObject(i).getJSONObject("loc").getJSONArray("coordinates");
                        double longitude = coordinates.getDouble(0);
                        double latitude = coordinates.getDouble(1);
                        locationSet.add(Pair.create(longitude, latitude));
                    }

                    for (Pair<Double, Double> pair : locationSet) {
                        Location location = new Location("");
                        location.setLongitude(pair.first);
                        location.setLatitude(pair.second);
                        startIntentService(location);
                    }

                    ArrayAdapter<LocationDto> adapter = new ArrayAdapter<>(AirPollutantWidgetConfigureActivity.this, android.R.layout.simple_spinner_item, new ArrayList<LocationDto>());
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                    spinnerLocations = findViewById(R.id.spinner_locations);
                    spinnerLocations.setAdapter(adapter);

                } catch (JSONException e) {
                    Toast.makeText(AirPollutantWidgetConfigureActivity.this, R.string.error_parse_locations, Toast.LENGTH_SHORT).show();
                    Log.e("AirPollutantWidget", e.getLocalizedMessage(), e);
                }
            }
        }, new Response.ErrorListener() {

            @Override
            public void onErrorResponse(VolleyError error) {
                Toast.makeText(AirPollutantWidgetConfigureActivity.this, R.string.error_get_locations, Toast.LENGTH_SHORT).show();
                Log.e("AirPollutantWidgetConf", error.getLocalizedMessage(), error);
            }
        });

        queue.add(jsonObjectRequest);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 999) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                AirPollutantWidgetConfigureActivity.this.coarseLocation = new Location("");
                fillLocations();
                return;
            }
            mFusedLocationClient.getLastLocation().addOnSuccessListener(this, new OnSuccessListener<Location>() {
                @Override
                public void onSuccess(final Location coarseLocation) {
                    AirPollutantWidgetConfigureActivity.this.coarseLocation = coarseLocation;
                    fillLocations();
                }
            });
        }
    }

    class AddressResultReceiver extends ResultReceiver {

        public AddressResultReceiver(Handler handler) {
            super(handler);
        }

        @Override
        protected void onReceiveResult(int resultCode, Bundle resultData) {

            if (resultData == null) {
                return;
            }


            String mAddressOutput = resultData.getString(Constants.RESULT_DATA_KEY);
            if (mAddressOutput == null) {
                mAddressOutput = "";
            }

            Location location = resultData.getParcelable(Constants.LOCATION_DATA_EXTRA);

            ArrayAdapter<LocationDto> adapter = (ArrayAdapter<LocationDto>) spinnerLocations.getAdapter();

            adapter.sort(new Comparator<LocationDto>() {
                @Override
                public int compare(LocationDto o1, LocationDto o2) {
                    double delta1 = Math.abs(coarseLocation.getLatitude() - o1.getLatitude()) + Math.abs(coarseLocation.getLongitude() - o1.getLongitude());
                    double delta2 = Math.abs(coarseLocation.getLatitude() - o2.getLatitude()) + Math.abs(coarseLocation.getLongitude() - o2.getLongitude());
                    return Double.compare(delta1, delta2);
                }
            });

            if (resultCode == Constants.SUCCESS_RESULT) {
                adapter.add(new LocationDto(location.getLongitude(), location.getLatitude(), mAddressOutput));
            } else {
                adapter.add(new LocationDto(location.getLongitude(), location.getLatitude(), location.getLongitude() + ", " + location.getLatitude()));
            }

            findViewById(R.id.progressBar).setVisibility(View.GONE);
            findViewById(R.id.add_button).setEnabled(true);

            LocationDto selectedLocationDto = loadLocationPref(AirPollutantWidgetConfigureActivity.this, mAppWidgetId);
            if (selectedLocationDto != null) {
                selectSpinnerItemByValue(spinnerLocations, selectedLocationDto);
            }

        }
    }
}

