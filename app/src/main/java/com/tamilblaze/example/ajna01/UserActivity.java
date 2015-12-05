package com.tamilblaze.example.ajna01;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class UserActivity extends AppCompatActivity {

    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";

    private static String TAG = "AjnaTAG";

    private BluetoothLeService mBluetoothLeService;


    private boolean mConnected = false;


    private int tapCount = 0;
    private boolean noMotionFlag = false;


    private String mDeviceName;
    private String mDeviceAddress;

    private TextView tvStatus, tvLogData;

    private NotificationCompat.Builder mBuilder;
    private final int ALERT_NOTIFICATION_ID = 001;

    private Timer timer;
    private TimerTask timerTask;

    //we are going to use a handler to be able to run in our TimerTask
    final Handler handler = new Handler();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user);


        final Intent intent = getIntent();
        mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);
        mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);


        tvStatus = (TextView) findViewById(R.id.tvFileStatus);
        tvLogData = (TextView) findViewById(R.id.tvDeviceLogData);
        tvLogData.setMovementMethod(new ScrollingMovementMethod());
        tvLogData.setText("All Log data will go here" + "\n", TextView.BufferType.SPANNABLE);

        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
    }

    // Code to manage Service lifecycle.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
            // Automatically connects to the device upon successful start-up initialization.
            mBluetoothLeService.connect(mDeviceAddress);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };


    // Handles various events fired by the Service.
    // ACTION_GATT_CONNECTED: connected to a GATT server.
    // ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
    // ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
    // ACTION_DATA_AVAILABLE: received data from the device.  This can be a result of read
    //                        or notification operations.
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                mConnected = true;
                invalidateOptionsMenu();
                Log.d(TAG, "Device Connected");

            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                mConnected = false;
                invalidateOptionsMenu();
                tvStatus.setText("GATT Disconnected");
                Log.d(TAG, "Device Disconnected");
                //clearUI();
            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                // Show all the supported services and characteristics on the user interface.

                getGattServices(mBluetoothLeService.getSupportedGattServices());
                Log.d(TAG, "GATT Services Discovered");
            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
                //displayData(intent.getStringExtra(BluetoothLeService.EXTRA_DATA));
                displayLogData(intent.getStringExtra(BluetoothLeService.EXTRA_DATA));
                //Log.d(TAG, "Action Data Available" + intent.getStringExtra(BluetoothLeService.EXTRA_DATA));
            }
        }
    };

    private void getGattServices(List<BluetoothGattService> supportedGattServices) {

        if (supportedGattServices == null) return;

        for (BluetoothGattService gattService : supportedGattServices) {
            List<BluetoothGattCharacteristic> gattCharacteristics =
                    gattService.getCharacteristics();
            ArrayList<BluetoothGattCharacteristic> charas =
                    new ArrayList<BluetoothGattCharacteristic>();

            for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
                if (gattCharacteristic.getUuid().toString().equals(SampleGattAttributes.DEBUG_PACKET_CHAR)) {
                    Log.d(TAG, "Debug Char found");
                    mBluetoothLeService.setCharacteristicNotification(
                            gattCharacteristic, true);
                }
            }

        }

    }

    private void displayLogData(String stringExtra) {
        tvLogData.setText(stringExtra + "\n\n" + tvLogData.getText());
        //tvLogData.getEditableText().insert(0, stringExtra + "\n");
        String[] parts = stringExtra.split("\n");
        tvStatus.setText(parts[0]);
        sensorFunctions(parts[0].toLowerCase().trim());

    }

    private void sensorFunctions(String event) {
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        //Log.d(TAG, "Event is:" + event);

        if (event.contains("tap")) { //Tap detection
            tapCount++;
            noMotionFlag = true;

            if (timer != null) {
                timer.cancel();
                timer = null;
            }

            Log.d(TAG, "Tap Count: " + tapCount);
                if (tapCount >= Integer.parseInt(sharedPref.getString("tapcount", "5"))) {
                tapCount = 0;
                if (sharedPref.getBoolean("tap_sms", false)) {

                    Toast.makeText(getApplicationContext(), "SMS Sent to " + sharedPref.getString("smsContact", "Emergency Contact"), Toast.LENGTH_LONG).show();

                }

                if (sharedPref.getBoolean("tap_call", false)) {
                    String contactNumber = sharedPref.getString("callContact", "Emergency Contact");
                    Toast.makeText(getApplicationContext(), "Calling " + contactNumber, Toast.LENGTH_LONG).show();

                    if (!contactNumber.contains("Emergency")) {
                        Intent intent = new Intent(Intent.ACTION_CALL);
                        intent.setData(Uri.parse("tel:" + contactNumber));
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        intent.addFlags(Intent.FLAG_FROM_BACKGROUND);
                        startActivity(intent);
                    }

                }

            }

        }

        if (event.equals("motion!") || event.equals("portrait") || event.equals("land")) {
            noMotionFlag = true;
            if (timer != null) {
                timer.cancel();
                timer = null;
            }
        }

        if (event.contains("no") && noMotionFlag) {// No motion
            noMotionFlag = false;
            buildNotification("Activity Warning", "Idle for long time");
            // Creates an explicit intent for an Activity in your app
            Intent resultIntent = new Intent(this, UserActivity.class);

            TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
            // Adds the back stack for the Intent (but not the Intent itself)
            stackBuilder.addParentStack(MainActivity.class);
            // Adds the Intent that starts the Activity to the top of the stack
            stackBuilder.addNextIntent(resultIntent);
            PendingIntent resultPendingIntent =
                    stackBuilder.getPendingIntent(
                            0,
                            PendingIntent.FLAG_UPDATE_CURRENT
                    );
            mBuilder.setContentIntent(resultPendingIntent);
            NotificationManager mNotificationManager =
                    (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

            // mId allows you to update the notification later on.
            mNotificationManager.notify(ALERT_NOTIFICATION_ID, mBuilder.build());
            startTimer();

        }

    }

    public void startTimer() {
        //set a new Timer
        timer = new Timer();

        //initialize the TimerTask's job
        initializeTimerTask();

        //schedule the timer, one shot
        timer.schedule(timerTask, 10000); //
        Log.d(TAG, "Timer started");
    }

    public void initializeTimerTask() {
        timerTask = new TimerTask() {
            public void run() {
                handler.post(new Runnable() {
                    public void run() {
                        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(UserActivity.this);

                        if (sharedPref.getBoolean("idle_sms", false)) {

                            Toast.makeText(getApplicationContext(), "SMS Sent to " + sharedPref.getString("smsContact", "Emergency Contact"), Toast.LENGTH_LONG).show();

                        }

                        if (sharedPref.getBoolean("idle_call", false)) {
                            String contactNumber = sharedPref.getString("callContact", "Emergency Contact");
                            Toast.makeText(getApplicationContext(), "Calling " + contactNumber, Toast.LENGTH_LONG).show();

                            if (!contactNumber.contains("Emergency")) {
                                Intent intent = new Intent(Intent.ACTION_CALL);
                                intent.setData(Uri.parse("tel:" + contactNumber));
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                intent.addFlags(Intent.FLAG_FROM_BACKGROUND);
                                startActivity(intent);
                            }

                        }


                    }

                });
            }


        };
    }

    public void clearDeviceLog(View v) {
        tvLogData.setText("");
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        if (mBluetoothLeService != null) {
            final boolean result = mBluetoothLeService.connect(mDeviceAddress);
            Log.d(TAG, "Connect request result=" + result);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mGattUpdateReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(mServiceConnection);
        mBluetoothLeService = null;
    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.gatt_services, menu);
        if (mConnected) {
            menu.findItem(R.id.menu_connect).setVisible(false);
            menu.findItem(R.id.menu_disconnect).setVisible(true);
        } else {
            menu.findItem(R.id.menu_connect).setVisible(true);
            menu.findItem(R.id.menu_disconnect).setVisible(false);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_connect:
                mBluetoothLeService.connect(mDeviceAddress);
                return true;
            case R.id.menu_disconnect:
                mBluetoothLeService.disconnect();
                return true;
            case android.R.id.home:
                onBackPressed();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public void clearLog(View v) {
        tvLogData.setText("");
    }

    public void saveLog(View v) {

        mBluetoothLeService.saveLogData();

    }

    public void buildNotification(String title, String msg) {
        mBuilder =
                new NotificationCompat.Builder(this)
                        .setSmallIcon(R.drawable.notification_warning)
                        .setContentTitle(title)
                        .setContentText(msg)
                        .setDefaults(Notification.DEFAULT_ALL);


    }

}
