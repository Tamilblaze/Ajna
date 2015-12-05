package com.tamilblaze.example.ajna01;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.util.Log;
import android.widget.Toast;

public class TrackMovement extends Service implements SensorEventListener {

    private static String TAG = "AjnaTAG";

    private final int ALERT_NOTIFICATION_ID = 001;

    private SensorManager senSensorManager;
    private Sensor senAccelerometer;

    private NotificationCompat.Builder mBuilder;

    private int idleCounter = 0;

    int dot = 200;      // Length of a Morse Code "dot" in milliseconds
    int dash = 500;     // Length of a Morse Code "dash" in milliseconds
    int short_gap = 200;    // Length of Gap Between dots/dashes
    int medium_gap = 500;   // Length of Gap Between Letters
    int long_gap = 1000;    // Length of Gap Between Words
    long[] pattern = {
            0,  // Start immediately
            dot, short_gap, dot, short_gap, dot,    // s

    };


    private long lastUpdate = 0;
    private float last_x, last_y, last_z;

    private static final int FALL_SHAKE_THRESHOLD = 1200;
    private static final int IDLE_SHAKE_THRESHOLD = 150;
    private static final int WARN_IDLE_TIME = 100;
    private static final int DANGER_IDLE_TIME = 200;
    private final int DISPLAY_LENGTH = 4000; // number of milliseconds for which to show the dialogue box.


    public TrackMovement() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void onCreate() {
        super.onCreate();

        Toast.makeText(this, "Service Started", Toast.LENGTH_SHORT).show();
        senSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        senAccelerometer = senSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        senSensorManager.registerListener(this, senAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Toast.makeText(this, "Service Destroyed", Toast.LENGTH_SHORT).show();
        senSensorManager.unregisterListener(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        Sensor mySensor = sensorEvent.sensor;

        if (mySensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            float x = sensorEvent.values[0];
            float y = sensorEvent.values[1];
            float z = sensorEvent.values[2];

            long curTime = System.currentTimeMillis();

            if ((curTime - lastUpdate) > 100) {
                long diffTime = (curTime - lastUpdate);
                lastUpdate = curTime;
                idleCounter++;

                if (idleCounter == WARN_IDLE_TIME) {

                    Log.d(TAG, "Idle Alert WARNING");

                    buildNotification("Activity Warning", "Idle for long time");

                    // Creates an explicit intent for an Activity in your app
                    Intent resultIntent = new Intent(this, MainActivity.class);

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


                } else if (idleCounter == DANGER_IDLE_TIME) {
                    Log.d(TAG, "Idle Alert DANGER");
                    SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);

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

                float speed = Math.abs(x + y + z - last_x - last_y - last_z) / diffTime * 10000;


                if (speed > FALL_SHAKE_THRESHOLD) {
                    Log.d(TAG, "Shake Detected: " + speed);
                    Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                    v.vibrate(pattern, -1);

                } else if (speed > IDLE_SHAKE_THRESHOLD) {
                    Log.d(TAG, "Idle Reset: " + speed);
                    idleCounter = 0;
                }

                last_x = x;
                last_y = y;
                last_z = z;
            }
        }

    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

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
