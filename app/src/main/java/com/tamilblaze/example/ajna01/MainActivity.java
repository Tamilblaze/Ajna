package com.tamilblaze.example.ajna01;

import android.app.Fragment;
import android.app.FragmentManager;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.provider.Settings;
import android.support.v4.app.ActionBarDrawerToggle;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationServices;

import java.io.IOException;
import java.util.List;
import java.util.Locale;


public class MainActivity extends ActionBarActivity implements
        GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    private final String TAG = "AjnaTAG";


    private final static int PLAY_SERVICES_RESOLUTION_REQUEST = 1000;
    // Google client to interact with Google API
    private GoogleApiClient mGoogleApiClient;
    private Location mLastLocation = null;
    private Geocoder geocoder;
    private List<Address> addresses;
    private Bundle locationBundle;
    String area, subArea, locality, feature, countryName;


    private String[] mNavigationDrawerItemTitles;
    private DrawerLayout mDrawerLayout;
    private ListView mDrawerList;

    private CharSequence mDrawerTitle;
    private CharSequence mTitle;

    ActionBarDrawerToggle mDrawerToggle;

    ActionBar actionBar;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (!isInternetAvailable()) {
            Log.d(TAG, "not online");
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage("Connect to Internet & try again").setPositiveButton("ok", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    //finish();
                    startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
                }
            });
            AlertDialog alertDialog = builder.create();
            alertDialog.show();

        }

        mTitle = mDrawerTitle = getTitle();

        mNavigationDrawerItemTitles = getResources().getStringArray(R.array.navigation_drawer_items_array);
        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        mDrawerList = (ListView) findViewById(R.id.left_drawer);

        ObjectDrawerItem[] drawerItem = new ObjectDrawerItem[4];
        drawerItem[0] = new ObjectDrawerItem(R.drawable.admin, getResources().getString(R.string.admin_dash));
        drawerItem[1] = new ObjectDrawerItem(R.drawable.user, getResources().getString(R.string.user_dash));
        drawerItem[2] = new ObjectDrawerItem(R.drawable.bt, getResources().getString(R.string.device_dash));
        drawerItem[3] = new ObjectDrawerItem(R.drawable.sync_cloud_icon, getResources().getString(R.string.dfu_update));

        DrawerItemCustomAdapter adapter = new DrawerItemCustomAdapter(this, R.layout.listview_item_row, drawerItem);
        mDrawerList.setAdapter(adapter);

        mDrawerList.setOnItemClickListener(new DrawerItemClickListener());

        actionBar = getSupportActionBar();

        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        mDrawerToggle = new ActionBarDrawerToggle(
                this,
                mDrawerLayout,
                R.drawable.ic_drawer,
                R.string.drawer_open,
                R.string.drawer_close
        ) {

            /** Called when a drawer has settled in a completely closed state. */
            public void onDrawerClosed(View view) {
                super.onDrawerClosed(view);
                actionBar.setTitle(mTitle);
            }

            /** Called when a drawer has settled in a completely open state. */
            public void onDrawerOpened(View drawerView) {
                super.onDrawerOpened(drawerView);
                actionBar.setTitle(mDrawerTitle);
            }
        };

        if (checkPlayServices()) {
            // Building the GoogleApi client
            Log.d(TAG, "PLAY services all set");
            // Building the GoogleApi client
            buildGoogleApiClient();
        } else {
            Log.d(TAG, "PLAY services Something wrong");
        }
        mDrawerLayout.setDrawerListener(mDrawerToggle);
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setHomeButtonEnabled(true);


    }

    private boolean checkPlayServices() {
        int resultCode = GooglePlayServicesUtil
                .isGooglePlayServicesAvailable(this);
        if (resultCode != ConnectionResult.SUCCESS) {
            Log.d(TAG, "GPLay Connection result Success");
            if (GooglePlayServicesUtil.isUserRecoverableError(resultCode)) {
                GooglePlayServicesUtil.getErrorDialog(resultCode, this,
                        PLAY_SERVICES_RESOLUTION_REQUEST).show();
                Log.d(TAG, "GPLay Connection result ERROR");
            } else {
                Toast.makeText(this,
                        "This device is not supported for location Tracking", Toast.LENGTH_LONG)
                        .show();
                Log.d(TAG, "Device not supported");
            }
            return false;
        }
        return true;
    }

    @Override
    public void setTitle(CharSequence title) {
        mTitle = title;
        actionBar.setTitle(mTitle);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            startActivity(new Intent(this, PreferenceActivity.class));
            return true;
        }

        if (mDrawerToggle.onOptionsItemSelected(item)) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * Creating google api client object
     */
    protected synchronized void buildGoogleApiClient() {
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API).build();
    }

    @Override
    public void onConnected(Bundle bundle) {
        // Once connected with google api, get the location
        displayLocation();

    }

    private void displayLocation() {
        mLastLocation = LocationServices.FusedLocationApi
                .getLastLocation(mGoogleApiClient);

        if (mLastLocation != null) {

            Log.d(TAG, "Latitude: " + mLastLocation.getLatitude());
            Log.d(TAG, "Longitude: " + mLastLocation.getLongitude());

            geocoder = new Geocoder(this, Locale.getDefault());
            try {
                addresses = geocoder.getFromLocation(mLastLocation.getLatitude(), mLastLocation.getLongitude(), 1);
                Log.d(TAG, "Returned Adress: " + addresses.toString());

                area = addresses.get(0).getAdminArea(); //kerala
                subArea = addresses.get(0).getSubAdminArea(); //Palakkad
                locality = addresses.get(0).getLocality(); //Ottapalam
                feature = addresses.get(0).getFeatureName(); //thottakkara
                countryName = addresses.get(0).getCountryName();//India

                buildDataAdminFragment();

                Fragment fragment = new AdminFragment();
                fragment.setArguments(locationBundle);
                FragmentManager fragmentManager = getFragmentManager();
                fragmentManager.beginTransaction().replace(R.id.content_frame, fragment).commit();

            } catch (IOException e) {
                e.printStackTrace();
                Log.d(TAG, "Address error: " + e.toString());

            }


        } else {

            Log.d(TAG, "Couldn't get the location. Make sure location is enabled on the device ");
            final AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage("Turn On Location & try again").setPositiveButton("ok", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    // FIRE ZE MISSILES!
                    //finish();
                    startActivity(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                }
            });
            AlertDialog alertDialog = builder.create();
            alertDialog.show();
        }
    }

    @Override
    public void onConnectionSuspended(int i) {
        mGoogleApiClient.connect();

    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        Log.d(TAG, "Connection failed: ConnectionResult.getErrorCode() = "
                + connectionResult.getErrorCode());

    }

    private class DrawerItemClickListener implements ListView.OnItemClickListener {

        @Override
        public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
            selectItem(i);

        }

        private void selectItem(int position) {

            Fragment fragment = null;

            switch (position) {
                case 0:
                    mLastLocation = LocationServices.FusedLocationApi
                            .getLastLocation(mGoogleApiClient);

                    if (mLastLocation != null) {
                        buildDataAdminFragment();
                        fragment = new AdminFragment();
                        fragment.setArguments(locationBundle);
                    } else {
                        Toast.makeText(getApplicationContext(), "Location not available", Toast.LENGTH_SHORT).show();
                    }


                    break;
                case 1:
                    startActivity(new Intent(MainActivity.this, DeviceScanActivity.class).putExtra("mode", "user"));

                    break;
                case 2:
                    //fragment = new HelpFragment();
                    startActivity(new Intent(MainActivity.this, DeviceScanActivity.class).putExtra("mode", "device"));
                    break;

                case 3:
                    startActivity(new Intent(MainActivity.this, DfuActivity.class));
                    break;

                default:
                    break;
            }

            if (fragment != null) {
                FragmentManager fragmentManager = getFragmentManager();
                fragmentManager.beginTransaction().replace(R.id.content_frame, fragment).commit();

                mDrawerList.setItemChecked(position, true);
                mDrawerList.setSelection(position);
                setTitle(mNavigationDrawerItemTitles[position]);
                mDrawerLayout.closeDrawer(mDrawerList);

            } else {
                Log.e("MainActivity", "Error in creating fragment");
            }
        }
    }

    private void buildDataAdminFragment() {
        locationBundle = new Bundle();
        locationBundle.putDouble("latitude", mLastLocation.getLatitude());
        locationBundle.putDouble("longitude", mLastLocation.getLongitude());
        locationBundle.putString("area", area);
        locationBundle.putString("subArea", subArea);
        locationBundle.putString("locality", locality);
        locationBundle.putString("feature", feature);
        locationBundle.putString("countryName", countryName);

    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        mDrawerToggle.syncState();
    }

    public boolean isInternetAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        return (cm.getActiveNetworkInfo() != null);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (mGoogleApiClient != null) {
            mGoogleApiClient.connect();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "On Resume Mainactivity");
        checkPlayServices();
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "On Pause Mainactivity");
    }
}
