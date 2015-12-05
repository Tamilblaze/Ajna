package com.tamilblaze.example.ajna01;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ExpandableListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class AjnaControl extends AppCompatActivity {

    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";
    private static String TAG = "AjnaTAG";

    private TextView tvDeviceAddress;
    private TextView tvDeviceStatus, tvLogData;
    private Button btsendCtrlChar;
    private EditText etCtrlChar;
    private String mDeviceName;
    private String mDeviceAddress;
    private ExpandableListView mGattServicesList;
    private BluetoothLeService mBluetoothLeService;
    private ArrayList<ArrayList<BluetoothGattCharacteristic>> mGattCharacteristics =
            new ArrayList<ArrayList<BluetoothGattCharacteristic>>();
    private List<BluetoothGattCharacteristic> ajnaGattCharacteristics;


    private boolean mConnected = false;
    private boolean mNotifyQuatCharacteristic = true;
    private boolean mNotifyDataCharacteristic = true;

    private final String LIST_NAME = "NAME";
    private final String LIST_UUID = "UUID";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ajna_control);

        final Intent intent = getIntent();
        mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);
        mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);

        ((TextView) findViewById(R.id.tvDeviceAddress)).setText(mDeviceAddress);
        tvDeviceStatus = (TextView) findViewById(R.id.tvDeviceStatus);
        tvLogData = (TextView) findViewById(R.id.tvLogData);
        tvLogData.setMovementMethod(new ScrollingMovementMethod());
        tvLogData.setText("All Log data will go here" + "\n", TextView.BufferType.SPANNABLE);

        etCtrlChar = (EditText) findViewById(R.id.etCtrlChar);
        etCtrlChar.setFocusable(false);

        btsendCtrlChar = (Button) findViewById(R.id.btSendCtrlChar);
        btsendCtrlChar.setEnabled(false);

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
                updateConnectionState(R.string.connected);
                invalidateOptionsMenu();
                etCtrlChar.setFocusableInTouchMode(true);
                btsendCtrlChar.setEnabled(true);
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                mConnected = false;
                updateConnectionState(R.string.disconnected);
                invalidateOptionsMenu();
                etCtrlChar.setFocusable(false);
                btsendCtrlChar.setEnabled(false);
                Log.d(TAG, "GATT Disconnected");
                //clearUI();
            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                // Show all the supported services and characteristics on the user interface.
                //displayGattServices(mBluetoothLeService.getSupportedGattServices());
                getGattServices(mBluetoothLeService.getSupportedGattServices());
                Log.d(TAG, "GATT Services Discovered");
            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
                //displayData(intent.getStringExtra(BluetoothLeService.EXTRA_DATA));
                displayLogData(intent.getStringExtra(BluetoothLeService.EXTRA_DATA));
                //Log.d(TAG, "Action Data Available" + intent.getStringExtra(BluetoothLeService.EXTRA_DATA));
            }
        }
    };

    private void displayLogData(String stringExtra) {
        tvLogData.setText(stringExtra + "\n\n" + tvLogData.getText());
        //tvLogData.getEditableText().insert(0, stringExtra + "\n");
    }

    private void getGattServices(List<BluetoothGattService> supportedGattServices) {
        if (supportedGattServices == null) return;

        ArrayList<HashMap<String, String>> gattServiceData = new ArrayList<HashMap<String, String>>();
        ArrayList<ArrayList<HashMap<String, String>>> gattCharacteristicData
                = new ArrayList<ArrayList<HashMap<String, String>>>();
        mGattCharacteristics = new ArrayList<ArrayList<BluetoothGattCharacteristic>>();
        ajnaGattCharacteristics = new ArrayList<BluetoothGattCharacteristic>();
        // Loops through available GATT Services.
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

                if (gattService.getUuid().toString().equals(SampleGattAttributes.AJNA_SERVICES)) {
                    ajnaGattCharacteristics.add(gattCharacteristic);
                }

                charas.add(gattCharacteristic);

            }
            mGattCharacteristics.add(charas);

        }


    }

    private void updateConnectionState(final int resourceId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                tvDeviceStatus.setText(resourceId);
            }
        });
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

    public void diableCharNotifications() {
        for (final BluetoothGattCharacteristic gattCharacteristic : ajnaGattCharacteristics) {
            if (gattCharacteristic.getUuid().toString().equals(SampleGattAttributes.QUAT_PACKET_CHAR) ||
                    gattCharacteristic.getUuid().toString().equals(SampleGattAttributes.DATA_PACKET_CHAR) ||
                    gattCharacteristic.getUuid().toString().equals(SampleGattAttributes.DEBUG_PACKET_CHAR)) {
                mBluetoothLeService.setCharacteristicNotification(
                        gattCharacteristic, false);
            }
        }
        mNotifyQuatCharacteristic = true;
        mNotifyDataCharacteristic = true;

    }

    public void deleteLogData(View v) {

        File dir = new File(Environment.getExternalStorageDirectory() + "/Ajna/Logs");
        if (dir.isDirectory()) {
            String[] children = dir.list();
            for (int i = 0; i < children.length; i++) {
                new File(dir, children[i]).delete();
            }
        }

        Toast.makeText(this, "Log Data Deleted", Toast.LENGTH_SHORT).show();

    }

    public void sendData(View v) {

        final String str = etCtrlChar.getText().toString().toLowerCase();

        //diableCharNotifications();


        for (final BluetoothGattCharacteristic gattCharacteristic : ajnaGattCharacteristics) {

            if (gattCharacteristic.getUuid().toString().equals(SampleGattAttributes.QUAT_PACKET_CHAR) &&
                    str.equals("s")) {
                Log.d(TAG, "QUAT Char found");
                mNotifyQuatCharacteristic = false;
                mBluetoothLeService.setCharacteristicNotification(
                        gattCharacteristic, true);

            }

            if (gattCharacteristic.getUuid().toString().equals(SampleGattAttributes.DATA_PACKET_CHAR)) {
                Log.d(TAG, "DATA Packet Char found");
                mNotifyDataCharacteristic = false;
                mBluetoothLeService.setCharacteristicNotification(
                        gattCharacteristic, true);

            }


            if (gattCharacteristic.getUuid().toString().equals(SampleGattAttributes.GET_PACKET_DATA)) {
                final Handler myHandler = new Handler();
                final Runnable r = new Runnable() {
                    public void run() {
                        Log.d(TAG, "Writing Data");
                        if (gattCharacteristic.getUuid().toString().equals(SampleGattAttributes.GET_PACKET_DATA)) {
                            Log.d(TAG, "GET Char found Sending char");

                            char ch = str.charAt(0);
                            Log.d(TAG, "char data: " + ch);

                            String hex = String.format("%02x", (int) ch);
                            Log.d(TAG, "Char value: " + ch + " Hex value: " + hex);

                            byte[] b = new BigInteger(hex, 16).toByteArray();

                            String s = new String(b);
                            Log.d(TAG, "Byte array as string is: " + s);

                            gattCharacteristic.setValue(b);
                            mBluetoothLeService.writeCharacteristic(gattCharacteristic);
                        }

                    }
                };

                myHandler.postDelayed(r, 1000);
            }


        }
    }
}
