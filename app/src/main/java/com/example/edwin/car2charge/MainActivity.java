package com.example.edwin.car2charge;

import android.app.ListActivity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.location.Location;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.SimpleCursorAdapter.ViewBinder;
import android.widget.TextView;
import android.widget.Toast;
import java.util.Locale;

public class MainActivity extends ListActivity implements GpsTrackerCallback {
    private final static String TAG = "MainActivity";

    private static final String[] FROM = {CarDatabase.C_ADDRESS, CarDatabase.C_BATTERY, CarDatabase.C_DISTANCE, CarDatabase.C_DISTANCE_CP};
    private static final int[] TO = {R.id.text_address, R.id.text_load, R.id.text_distance, R.id.text_distance_cp};
    private Intent carDownloadIntent;
    private GpsTracker gps;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.app_name);

        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) !=
                PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) !=
                        PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] {android.Manifest.permission.ACCESS_FINE_LOCATION}, 1);
        }

        if (!isNetworkConnected()){
            Toast.makeText(getApplicationContext(), "Sorry, no internet connection", Toast.LENGTH_LONG).show();
        }

        else {
            //Toast.makeText(getApplicationContext(), "Yes! network", Toast.LENGTH_LONG).show();
            getContentResolver().delete(CarDataProvider.CONTENT_URI, null, null);
            String[] projection = {CarDatabase.C_ID, CarDatabase.C_ADDRESS, CarDatabase.C_LICENSE, CarDatabase.C_BATTERY, CarDatabase.C_DISTANCE, CarDatabase.C_DISTANCE_CP};
            Cursor cars = getContentResolver().query(CarDataProvider.CONTENT_URI, projection, null, null, null);

            SimpleCursorAdapter adapter = new SimpleCursorAdapter (this, R.layout.row, cars, FROM, TO);
            adapter.setViewBinder(VIEW_BINDER);
            setListAdapter(adapter);
            carDownloadIntent = new Intent(getApplicationContext(), CarDownloaderService.class);
            gps = new GpsTracker(this, this);
            gps.getLocation();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == 1) {
            for (int i = 0; i < grantResults.length; i++) {
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED){
                    return;
                }
            }
        }
    }

    private boolean isNetworkConnected() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo ni = cm.getActiveNetworkInfo();
        if (ni == null) {
            // There are no active networks.
            return false;
        } else
            return true;
    }

    static final ViewBinder VIEW_BINDER = new ViewBinder() {

        @Override
        public boolean setViewValue(View view, Cursor cursor, int columnIndex) {

            if (view.getId() == R.id.text_load) {
                int load = cursor.getInt(cursor.getColumnIndex(CarDatabase.C_BATTERY));
                String strLoad = String.format("Load: %s%%", load);
                ((TextView)view).setText(strLoad);
                return true;
            }

            if (view.getId() == R.id.text_distance) {
                int distance = cursor.getInt(cursor.getColumnIndex(CarDatabase.C_DISTANCE));
                String strDistance = String.format("Distance: %sm", distance);
                ((TextView)view).setText(strDistance);
                return true;
            }

            if (view.getId() == R.id.text_distance_cp) {
                int distanceCP = cursor.getInt(cursor.getColumnIndex(CarDatabase.C_DISTANCE_CP));
                String strDistanceCP = String.format("Distance to CP: %sm", distanceCP);
                ((TextView)view).setText(strDistanceCP);
                return true;
            }
            return false;
        }
    };

    private int refreshMenuId;
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_main, menu);
        MenuItem refresh = menu.findItem(R.id.refresh_option_item);
        refresh.setIntent(carDownloadIntent);
        refreshMenuId = refresh.getItemId();
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == refreshMenuId) {
            //item.getIntent().setData(Uri.parse(getURLincludingLocation()));
            //startService(item.getIntent());
            gps.getLocation();
        }
        if (item.getItemId() == R.id.menu_settings) {
            startActivity(new Intent(this, PrefsActivity.class));
        }

        return true;
    }

    private String getURLincludingLocation() {
        String url = "";
        final String WS_URL = "http://%s:%d/c2c/ws.php?lat=%f&lng=%f&perc=%d";
        String server = ((C2cApplication)getApplication()).prefs.getString("server", "192.168.72.104");
        int perc = Integer.parseInt(((C2cApplication)getApplication()).prefs.getString("max_load", "30"));
        // Default lat/long DamSquare in Amsterdam
        double lat = 52.372789;
        double lng = 4.893669;

        lat = gps.getLatitude(); if (lat == 0) { lat = 52.372789; }
        lng = gps.getLongitude(); if (lng == 0) { lng = 4.893669; }

        url = String.format(Locale.getDefault(), WS_URL, server, 8081, lat, lng, perc);
        Log.d("GetURL", url);
        return url;
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {

        String[] projection = {CarDatabase.C_LAT, CarDatabase.C_LONG, CarDatabase.C_ADDRESS, CarDatabase.C_LICENSE, CarDatabase.C_BATTERY};
        Cursor cursor = getContentResolver().query(Uri.withAppendedPath(CarDataProvider.CONTENT_URI,
                String.valueOf(id)), projection, null, null,null);
        cursor.moveToFirst();
        double lat = cursor.getDouble(0);
        double lng = cursor.getDouble(1);

        Intent i = new Intent(getApplicationContext(), DetailActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        i.putExtra("lat", lat);
        i.putExtra("long", lng);
        i.putExtra("address", cursor.getString(2));
        i.putExtra("license", cursor.getString(3));
        i.putExtra("battery", cursor.getString(4));
        startActivity(i);
        cursor.close();
    }

    @Override
    public void LocationFound(Location location) {
        Toast.makeText(this, "Location found", Toast.LENGTH_SHORT).show();
        carDownloadIntent.setData(Uri.parse(getURLincludingLocation()));
        startService(carDownloadIntent);
    }

    @Override
    public void LocationNotAvailable() {
        Toast.makeText(this, "Turn on your location services", Toast.LENGTH_LONG).show();
    }
}
