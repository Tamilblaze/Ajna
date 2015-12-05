/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.tamilblaze.example.ajna01;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Service for managing connection and data communication with a GATT server hosted on a
 * given Bluetooth LE device.
 */
public class BluetoothLeService extends Service {
    private final static String TAG = BluetoothLeService.class.getSimpleName();

    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private String mBluetoothDeviceAddress;
    private BluetoothGatt mBluetoothGatt;
    private int mConnectionState = STATE_DISCONNECTED;

    private StringBuilder log28;
    private StringBuilder log26;
    private StringBuilder log27_a, log27_g, log27_q, log27_e, log27_h;
    private StringBuilder log27;


    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;

    public final static String ACTION_GATT_CONNECTED =
            "com.example.bluetooth.le.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED =
            "com.example.bluetooth.le.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED =
            "com.example.bluetooth.le.ACTION_GATT_SERVICES_DISCOVERED";
    public final static String ACTION_DATA_AVAILABLE =
            "com.example.bluetooth.le.ACTION_DATA_AVAILABLE";
    public final static String EXTRA_DATA =
            "com.example.bluetooth.le.EXTRA_DATA";

    public final static UUID UUID_HEART_RATE_MEASUREMENT =
            UUID.fromString(SampleGattAttributes.HEART_RATE_MEASUREMENT);

    public final static UUID UUID_QUATERNERION_PACKET_CHAR =
            UUID.fromString(SampleGattAttributes.DEBUG_PACKET_CHAR);

    public final static UUID UUID_QUATERNERION_DATA_CHAR =
            UUID.fromString(SampleGattAttributes.QUAT_PACKET_CHAR);

    public final static UUID UUID_OTHER_DATA_CHAR =
            UUID.fromString(SampleGattAttributes.DATA_PACKET_CHAR);

    // Implements callback methods for GATT events that the app cares about.  For example,
    // connection change and services discovered.
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            String intentAction;
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                intentAction = ACTION_GATT_CONNECTED;
                mConnectionState = STATE_CONNECTED;
                broadcastUpdate(intentAction);
                Log.i(TAG, "Connected to GATT server.");
                // Attempts to discover services after successful connection.
                Log.i(TAG, "Attempting to start service discovery:" +
                        mBluetoothGatt.discoverServices());

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                intentAction = ACTION_GATT_DISCONNECTED;
                mConnectionState = STATE_DISCONNECTED;
                Log.i(TAG, "Disconnected from GATT server.");
                broadcastUpdate(intentAction);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED);
            } else {
                Log.w(TAG, "onServicesDiscovered received: " + status);
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            //Log.d(TAG, "On char changed triggered");


            broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {

            Log.d(TAG, "On char write with Status: " + status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
            } else {
                Log.d(TAG, "ON write Failed");
            }

        }
    };

    private void broadcastUpdate(final String action) {
        final Intent intent = new Intent(action);
        sendBroadcast(intent);
    }

    private void broadcastUpdate(final String action,
                                 final BluetoothGattCharacteristic characteristic) {
        final Intent intent = new Intent(action);

        // This is special handling for the Heart Rate Measurement profile.  Data parsing is
        // carried out as per profile specifications:
        // http://developer.bluetooth.org/gatt/characteristics/Pages/CharacteristicViewer.aspx?u=org.bluetooth.characteristic.heart_rate_measurement.xml
        if (UUID_HEART_RATE_MEASUREMENT.equals(characteristic.getUuid())) {
            int flag = characteristic.getProperties();
            int format = -1;
            if ((flag & 0x01) != 0) {
                format = BluetoothGattCharacteristic.FORMAT_UINT16;
                Log.d(TAG, "Heart rate format UINT16.");
            } else {
                format = BluetoothGattCharacteristic.FORMAT_UINT8;
                Log.d(TAG, "Heart rate format UINT8.");
            }
            final int heartRate = characteristic.getIntValue(format, 1);
            Log.d(TAG, String.format("Received heart rate: %d", heartRate));
            intent.putExtra(EXTRA_DATA, String.valueOf(heartRate));
        } else {
            // For all other profiles, writes the data formatted in HEX.
            final byte[] data = characteristic.getValue();
            if (data != null && data.length > 0) {
                final StringBuilder stringBuilder = new StringBuilder(data.length);
                for (byte byteChar : data)
                    stringBuilder.append(String.format("%02X ", byteChar));
                intent.putExtra(EXTRA_DATA, new String(data) + "\n" + stringBuilder.toString());
                if (characteristic.getUuid().toString().equals(SampleGattAttributes.DEBUG_PACKET_CHAR)) {
                    log28.append("\n" + new String(data) + "\n" + stringBuilder.toString() + "\n");
                } else if (characteristic.getUuid().toString().equals(SampleGattAttributes.QUAT_PACKET_CHAR)) {
                    log26.append("\n" + new String(data) + "\n" + stringBuilder.toString() + "\n");
                } else if (characteristic.getUuid().toString().equals(SampleGattAttributes.DATA_PACKET_CHAR)) {
                    log27.append("\n" + new String(data) + "\n" + stringBuilder.toString() + "\n");
                    String dataType = stringBuilder.substring(3, 5);
                    Log.d(TAG, "String Subset: " + dataType);
                    switch (dataType) {
                        case "00": //a
                            log27_a.append("\n" + new String(data) + "\n" + stringBuilder.toString() + "\n");
                            break;
                        case "01": //g
                            log27_g.append("\n" + new String(data) + "\n" + stringBuilder.toString() + "\n");
                            break;
                        case "03": //q
                            log27_q.append("\n" + new String(data) + "\n" + stringBuilder.toString() + "\n");
                            break;
                        case "04": //e
                            log27_e.append("\n" + new String(data) + "\n" + stringBuilder.toString() + "\n");
                            break;
                        case "06": //h
                            log27_h.append("\n" + new String(data) + "\n" + stringBuilder.toString() + "\n");
                            break;
                    }
                }
            }
        }
        sendBroadcast(intent);
    }

    public void saveLogData() {

        Long tsLong = System.currentTimeMillis() / 1000;
        String ts = tsLong.toString();

        File mydir = new File(Environment.getExternalStorageDirectory() + "/Ajna/", "Logs");
        if (!mydir.exists())
            mydir.mkdirs();


        File log28File = new File(mydir, ts + "LOG-28.csv");
        File log26File = new File(mydir, ts + "LOG-26.csv");
        //File log27File = new File(mydir, ts + "LOG-27.csv");

        try {
            FileWriter fw = new FileWriter(log28File.getAbsoluteFile());
            BufferedWriter bw = new BufferedWriter(fw);

/*
            StringBuilder readableLog28 = new StringBuilder();
            StringBuilder output = removeBlankSpace(log28);


            for (int i = 0; i < log28.length(); i += 2) {
                String str = log28.substring(i, i + 2);
                readableLog28.append((char) Integer.parseInt(str, 16));
            }
*/

            // bw.write(readableLog28.toString());
            bw.write(log28.toString());
            bw.close();

            Toast.makeText(this, "Log 28 Captured", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            FileWriter fw = new FileWriter(log26File.getAbsoluteFile());
            BufferedWriter bw = new BufferedWriter(fw);
            bw.write(log26.toString());
            bw.close();

            Toast.makeText(this, "Log 26 Captured", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            e.printStackTrace();
        }

        write27LogData(mydir, ts, "LOG-27");


        log26.setLength(0);
        log27_a.setLength(0);
        log27_g.setLength(0);
        log27_q.setLength(0);
        log27_e.setLength(0);
        log27_h.setLength(0);

        log27.setLength(0);
        log28.setLength(0);


    }

    private void write27LogData(File mydir, String ts, String Log_27) {

        if (!log27_a.toString().equals("")) {
            File log27_aFile = new File(mydir, ts + Log_27 + "_a.csv");
            write27Log(log27_aFile, log27_a);
        }
        if (!log27_g.toString().equals("")) {
            File log27_gFile = new File(mydir, ts + Log_27 + "_g.csv");
            write27Log(log27_gFile, log27_g);
        }
        if (!log27_q.toString().equals("")) {
            File log27_qFile = new File(mydir, ts + Log_27 + "_q.csv");
            write27Log(log27_qFile, log27_q);
        }
        if (!log27_e.toString().equals("")) {
            File log27_eFile = new File(mydir, ts + Log_27 + "_e.csv");
            write27Log(log27_eFile, log27_e);
        }
        if (!log27_h.toString().equals("")) {
            File log27_hFile = new File(mydir, ts + Log_27 + "_h.csv");
            write27Log(log27_hFile, log27_h);
        }

        File log27File = new File(mydir, ts + "LOG-27.csv");
        write27Log(log27File, log27);


    }

    public void write27Log(File file27Type, StringBuilder logFile) {
        try {
            FileWriter fw = new FileWriter(file27Type.getAbsoluteFile());
            BufferedWriter bw = new BufferedWriter(fw);
            bw.write(logFile.toString());
            bw.close();

            Toast.makeText(this, file27Type.getName()+" Captured", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static StringBuilder removeBlankSpace(StringBuilder sb) {
        int currentEnd = -1;
        for (int i = sb.length() - 1; i >= 0; i--) {
            if (Character.isWhitespace(sb.charAt(i))) {
                if (currentEnd == -1) {
                    currentEnd = i + 1;
                }
            } else {
                // Moved from whitespace to non-whitespace
                if (currentEnd != -1) {
                    sb.delete(i + 1, currentEnd);
                    currentEnd = -1;
                }
            }
        }
        // All leading whitespace
        if (currentEnd != -1) {
            sb.delete(0, currentEnd);
        }
        return sb;
    }

    public class LocalBinder extends Binder {
        BluetoothLeService getService() {
            return BluetoothLeService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        // After using a given device, you should make sure that BluetoothGatt.close() is called
        // such that resources are cleaned up properly.  In this particular example, close() is
        // invoked when the UI is disconnected from the Service.
        close();
        return super.onUnbind(intent);
    }

    private final IBinder mBinder = new LocalBinder();

    /**
     * Initializes a reference to the local Bluetooth adapter.
     *
     * @return Return true if the initialization is successful.
     */
    public boolean initialize() {
        // For API level 18 and above, get a reference to BluetoothAdapter through
        // BluetoothManager.

        log27_a = new StringBuilder();
        log27_g = new StringBuilder();
        log27_q = new StringBuilder();
        log27_e = new StringBuilder();
        log27_h = new StringBuilder();

        log26 = new StringBuilder();
        log28 = new StringBuilder();
        log27 = new StringBuilder();

        if (mBluetoothManager == null) {
            mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBluetoothManager == null) {
                Log.e(TAG, "Unable to initialize BluetoothManager.");
                return false;
            }
        }

        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
            return false;
        }

        return true;
    }

    /**
     * Connects to the GATT server hosted on the Bluetooth LE device.
     *
     * @param address The device address of the destination device.
     * @return Return true if the connection is initiated successfully. The connection result
     * is reported asynchronously through the
     * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     * callback.
     */
    public boolean connect(final String address) {
        if (mBluetoothAdapter == null || address == null) {
            Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.");
            return false;
        }

        // Previously connected device.  Try to reconnect.
        if (mBluetoothDeviceAddress != null && address.equals(mBluetoothDeviceAddress)
                && mBluetoothGatt != null) {
            Log.d(TAG, "Trying to use an existing mBluetoothGatt for connection.");
            if (mBluetoothGatt.connect()) {
                mConnectionState = STATE_CONNECTING;
                return true;
            } else {
                return false;
            }
        }

        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        if (device == null) {
            Log.w(TAG, "Device not found.  Unable to connect.");
            return false;
        }
        // We want to directly connect to the device, so we are setting the autoConnect
        // parameter to false.
        mBluetoothGatt = device.connectGatt(this, false, mGattCallback);
        Log.d(TAG, "Trying to create a new connection.");
        mBluetoothDeviceAddress = address;
        mConnectionState = STATE_CONNECTING;
        return true;
    }

    /**
     * Disconnects an existing connection or cancel a pending connection. The disconnection result
     * is reported asynchronously through the
     * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     * callback.
     */
    public void disconnect() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.disconnect();
    }

    /**
     * After using a given BLE device, the app must call this method to ensure resources are
     * released properly.
     */
    public void close() {
        if (mBluetoothGatt == null) {
            return;
        }
        mBluetoothGatt.close();
        mBluetoothGatt = null;
    }

    /**
     * Request a read on a given {@code BluetoothGattCharacteristic}. The read result is reported
     * asynchronously through the {@code BluetoothGattCallback#onCharacteristicRead(android.bluetooth.BluetoothGatt, android.bluetooth.BluetoothGattCharacteristic, int)}
     * callback.
     *
     * @param characteristic The characteristic to read from.
     */
    public void readCharacteristic(BluetoothGattCharacteristic characteristic) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.readCharacteristic(characteristic);
    }

    public void writeCharacteristic(BluetoothGattCharacteristic characteristic) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.d(TAG, "BluetoothAdapter not initialized");
            return;
        }
        Log.d(TAG, "About to write Char");
        mBluetoothGatt.writeCharacteristic(characteristic);
    }

    /**
     * Enables or disables notification on a give characteristic.
     *
     * @param characteristic Characteristic to act on.
     * @param enabled        If true, enable notification.  False otherwise.
     */
    public void setCharacteristicNotification(BluetoothGattCharacteristic characteristic,
                                              boolean enabled) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.setCharacteristicNotification(characteristic, enabled);


        // This is specific to Ajna Device

        if (UUID_QUATERNERION_PACKET_CHAR.equals(characteristic.getUuid()) || UUID_QUATERNERION_DATA_CHAR.equals(characteristic.getUuid()) ||
                UUID_OTHER_DATA_CHAR.equals(characteristic.getUuid())) {
            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(
                    UUID.fromString(SampleGattAttributes.CLIENT_CHARACTERISTIC_CONFIG));
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            mBluetoothGatt.writeDescriptor(descriptor);
        }

    }

    /**
     * Retrieves a list of supported GATT services on the connected device. This should be
     * invoked only after {@code BluetoothGatt#discoverServices()} completes successfully.
     *
     * @return A {@code List} of supported services.
     */
    public List<BluetoothGattService> getSupportedGattServices() {
        if (mBluetoothGatt == null) return null;

        return mBluetoothGatt.getServices();
    }
}
