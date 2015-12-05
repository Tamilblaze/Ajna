package com.tamilblaze.example.ajna01;

import android.app.ActivityManager;
import android.app.LoaderManager;
import android.app.NotificationManager;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.Loader;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;

import no.nordicsemi.android.dfu.DfuProgressListener;
import no.nordicsemi.android.dfu.DfuProgressListenerAdapter;
import no.nordicsemi.android.dfu.DfuServiceInitiator;
import no.nordicsemi.android.dfu.DfuServiceListenerHelper;

/**
 * Created by tjaya on 10/26/2015.
 */
public class DfuActivity extends AppCompatActivity implements LoaderManager.LoaderCallbacks<Cursor>, UploadCancelFragment.CancelFragmentListener {
    private final String TAG = "AjnaTAG";
    public static final int TYPE_AUTO = 0x00;
    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";

    private static final String EXTRA_URI = "uri";

    private TextView tvFileName, tvFileType, tvFileSize, tvFileStatus, tvDeviceInfo, tvDFUStatus;
    private Button btSelectFile, btSelectDevice, btUpload;
    private ProgressBar mProgressBar;

    private String text_tvFileName, text_tvFileType, text_tvFileSize, text_tvFileStatus, text_deviceInfo;

    private String mDeviceName;
    private String mDeviceAddress;

    private int mFileType;
    private int mFileTypeTmp;
    private String mFilePath;
    private Uri mFileStreamUri;
    private static final int SELECT_FILE_REQ = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dfu);

        tvFileName = (TextView) findViewById(R.id.tvFileName);
        tvFileType = (TextView) findViewById(R.id.tvFileType);
        tvFileSize = (TextView) findViewById(R.id.tvFileSize);
        tvFileStatus = (TextView) findViewById(R.id.tvFileStatus);
        tvDeviceInfo = (TextView) findViewById(R.id.tvDeviceInfo);
        tvDFUStatus = (TextView) findViewById(R.id.tvDFUStatus);

        text_tvFileName = tvFileName.getText().toString();
        text_tvFileType = tvFileType.getText().toString();
        text_tvFileSize = tvFileSize.getText().toString();
        text_tvFileStatus = tvFileStatus.getText().toString();
        text_deviceInfo = tvDeviceInfo.getText().toString();

        btSelectFile = (Button) findViewById(R.id.btSelectFile);
        btSelectDevice = (Button) findViewById(R.id.btSelectDevice);
        btUpload = (Button) findViewById(R.id.btUpload);

        btSelectDevice.setEnabled(false);
        btUpload.setEnabled(false);
        mProgressBar = (ProgressBar) findViewById(R.id.pbUpload);

    }

    public void fileChooser(View v) {
        final Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType(mFileTypeTmp == DfuService.TYPE_AUTO ? DfuService.MIME_TYPE_ZIP : DfuService.MIME_TYPE_OCTET_STREAM);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        if (intent.resolveActivity(getPackageManager()) != null) {
            // file browser has been found on the device
            startActivityForResult(intent, SELECT_FILE_REQ);
        }

    }


    @Override
    protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
        if (resultCode != RESULT_OK)
            return;

        if (requestCode == SELECT_FILE_REQ) {
            mFileType = mFileTypeTmp;
            mFilePath = null;
            mFileStreamUri = null;
            Log.d(TAG, "File typTemp: " + mFileType);
            // and read new one
            final Uri uri = data.getData();

            if (uri.getScheme().equals("file")) {
                // the direct path to the file has been returned
                final String path = uri.getPath();
                final File file = new File(path);
                mFilePath = path;

                updateFileInfo(file.getName(), file.length(), mFileType);
                Log.d(TAG, "FileName: " + file.getName() + " Length: " + file.length() + "Type: " + mFileType);

            } else if (uri.getScheme().equals("content")) {
                // an Uri has been returned
                mFileStreamUri = uri;
                // if application returned Uri for streaming, let's us it. Does it works?
                // FIXME both Uris works with Google Drive app. Why both? What's the difference? How about other apps like DropBox?
                final Bundle extras = data.getExtras();
                if (extras != null && extras.containsKey(Intent.EXTRA_STREAM))
                    mFileStreamUri = extras.getParcelable(Intent.EXTRA_STREAM);

                // file name and size must be obtained from Content Provider
                final Bundle bundle = new Bundle();
                bundle.putParcelable(EXTRA_URI, uri);
                getLoaderManager().restartLoader(SELECT_FILE_REQ, bundle, DfuActivity.this);
            }
        }
    }

    @Override
    public Loader<Cursor> onCreateLoader(int i, Bundle args) {
        final Uri uri = args.getParcelable(EXTRA_URI);
        /*
         * Some apps, f.e. Google Drive allow to select file that is not on the device. There is no "_data" column handled by that provider. Let's try to obtain
		 * all columns and than check which columns are present.
		 */
        // final String[] projection = new String[] { MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.DATA };
        return new CursorLoader(this, uri, null /* all columns, instead of projection */, null, null, null);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        if (data != null && data.moveToNext()) {
            /*
             * Here we have to check the column indexes by name as we have requested for all. The order may be different.
			 */
            final String fileName = data.getString(data.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)/* 0 DISPLAY_NAME */);
            final int fileSize = data.getInt(data.getColumnIndex(MediaStore.MediaColumns.SIZE) /* 1 SIZE */);
            String filePath = null;
            final int dataIndex = data.getColumnIndex(MediaStore.MediaColumns.DATA);
            if (dataIndex != -1)
                filePath = data.getString(dataIndex /* 2 DATA */);
            if (!TextUtils.isEmpty(filePath))
                mFilePath = filePath;

            updateFileInfo(fileName, fileSize, mFileType);
            //Log.d(TAG, " Loader Finished :: FileName: "+ fileName+ " Length: "+ fileSize + "Type: "+ mFileType);
            Log.d(TAG, "Stream URI: " + mFileStreamUri + "  ::File Path: " + mFilePath);
        } else {
            Log.d(TAG, "Loader Finished No data");
        }

    }

    private void updateFileInfo(String fileName, final long fileSize, int mFileType) {


        tvFileName.setText(String.format("%s%s", text_tvFileName, fileName));
        if (mFileType == TYPE_AUTO) {
            tvFileType.setText(String.format("%sDistribution packet (ZIP)", text_tvFileType));
            tvFileSize.setText(String.format("%s%dBytes", text_tvFileSize, fileSize));
            tvFileStatus.setText(String.format("%sOk", text_tvFileStatus));
            btSelectDevice.setEnabled(true);
        } else {
            tvFileType.setText(String.format("%sUnknown", text_tvFileType));
            tvFileSize.setText(String.format("%s--", text_tvFileSize));
            tvFileStatus.setText(String.format("%sInvalid File Type", text_tvFileStatus));
        }
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        Log.d(TAG, "Clear all data");

        tvFileName.setText(text_tvFileName);
        tvFileType.setText(text_tvFileType);
        tvFileSize.setText(text_tvFileSize);
        tvFileStatus.setText(text_tvFileStatus);
        tvDeviceInfo.setText(text_tvFileStatus);

        mFilePath = null;
        mFileStreamUri = null;

        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        final SharedPreferences.Editor editor = preferences.edit();
        editor.clear().apply();
    }

    public void onSelectDevice(View v) {
        saveAllData();
        startActivity(new Intent(DfuActivity.this, DeviceScanActivity.class).putExtra("mode", "dfu"));
        //finish();
    }

    private void saveAllData() {
        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        final SharedPreferences.Editor editor = preferences.edit();

        editor.putString("fileName", tvFileName.getText().toString());
        editor.putString("fileType", tvFileType.getText().toString());
        editor.putString("fileSize", tvFileSize.getText().toString());
        editor.putString("fileStatus", tvFileStatus.getText().toString());
        editor.putString("filePath", mFilePath);
        editor.putString("fileUri", String.valueOf(mFileStreamUri));
        editor.apply();
        Log.d(TAG, " DFU on Save All data");
    }

    @Override
    protected void onPause() {
        super.onPause();

        DfuServiceListenerHelper.unregisterProgressListener(this, mDfuProgressListener);
    }

    @Override
    protected void onResume() {
        super.onResume();

        DfuServiceListenerHelper.registerProgressListener(this, mDfuProgressListener);

        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);

        tvFileName.setText(preferences.getString("fileName", text_tvFileName));
        tvFileType.setText(preferences.getString("fileType", text_tvFileType));
        tvFileSize.setText(preferences.getString("fileSize", text_tvFileSize));
        tvFileStatus.setText(preferences.getString("fileStatus", text_tvFileStatus));

        if (preferences.getString("fileStatus", null) != null)
            btSelectDevice.setEnabled(true);

        if (preferences.getString("filePath", null) != null)
            mFilePath = preferences.getString("filePath", null);

        if (preferences.getString("fileUri", null) != null)
            mFileStreamUri = Uri.parse(preferences.getString("fileUri", "null"));

        Log.d(TAG, "Resume -- FilePath: " + mFilePath + " file Stream: " + mFileStreamUri);

        final Intent intent = getIntent();
        mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);
        mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);

        Log.d(TAG, "Device Found: " + mDeviceName + " @Address: " + mDeviceAddress);

        tvDeviceInfo.setText(String.format("Device Name: %s", mDeviceName));

        if (mDeviceName != null && mDeviceAddress != null) {
            btUpload.setEnabled(true);
        }

    }

    public void startDFU(View v) {
        Log.d(TAG, "started DFU");
        if (isDfuServiceRunning()) {
            showUploadCancelDialog();
            return;
        }
        btUpload.setEnabled(false);
        btSelectDevice.setEnabled(false);
        btSelectFile.setEnabled(false);

        final boolean keepBond = false;

        final DfuServiceInitiator starter = new DfuServiceInitiator(mDeviceAddress)
                .setDeviceName(mDeviceName)
                .setKeepBond(keepBond);

        starter.setZip(mFileStreamUri, mFilePath);
        starter.start(this, DfuService.class);
    }


    @Override
    public void onCancelUpload() {
        mProgressBar.setIndeterminate(true);
        tvDFUStatus.setText(R.string.dfu_status_aborting);

        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        final SharedPreferences.Editor editor = preferences.edit();
        editor.clear().apply();

    }

    private final DfuProgressListener mDfuProgressListener = new DfuProgressListenerAdapter() {
        @Override
        public void onDeviceConnecting(final String deviceAddress) {
            mProgressBar.setIndeterminate(true);
            tvDFUStatus.setText(R.string.dfu_status_connecting);
        }

        @Override
        public void onDfuProcessStarting(final String deviceAddress) {
            mProgressBar.setIndeterminate(true);
            tvDFUStatus.setText(R.string.dfu_status_starting);
        }

        @Override
        public void onEnablingDfuMode(final String deviceAddress) {
            mProgressBar.setIndeterminate(true);
            tvDFUStatus.setText(R.string.dfu_status_switching_to_dfu);
        }

        @Override
        public void onFirmwareValidating(final String deviceAddress) {
            mProgressBar.setIndeterminate(true);
            tvDFUStatus.setText(R.string.dfu_status_validating);
        }

        @Override
        public void onDeviceDisconnecting(final String deviceAddress) {
            mProgressBar.setIndeterminate(true);
            tvDFUStatus.setText(R.string.dfu_status_disconnecting);
        }

        @Override
        public void onDfuCompleted(final String deviceAddress) {
            tvDFUStatus.setText(R.string.dfu_status_completed);

            final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(DfuActivity.this);
            final SharedPreferences.Editor editor = preferences.edit();
            editor.clear().apply();

            // let's wait a bit until we cancel the notification. When canceled immediately it will be recreated by service again.
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    onTransferCompleted();

                    // if this activity is still open and upload process was completed, cancel the notification
                    final NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                    manager.cancel(DfuService.NOTIFICATION_ID);
                }
            }, 200);
        }

        @Override
        public void onDfuAborted(final String deviceAddress) {
            tvDFUStatus.setText(R.string.dfu_status_aborted);

            final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(DfuActivity.this);
            final SharedPreferences.Editor editor = preferences.edit();
            editor.clear().apply();

            // let's wait a bit until we cancel the notification. When canceled immediately it will be recreated by service again.
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    onUploadCanceled();

                    // if this activity is still open and upload process was completed, cancel the notification
                    final NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                    manager.cancel(DfuService.NOTIFICATION_ID);
                }
            }, 200);
        }

        @Override
        public void onProgressChanged(final String deviceAddress, final int percent, final float speed, final float avgSpeed, final int currentPart, final int partsTotal) {
            mProgressBar.setIndeterminate(false);
            mProgressBar.setProgress(percent);
            tvDFUStatus.setText(getString(R.string.dfu_uploading_percentage, percent));


            //if (partsTotal > 1)
                //tvDFUStatus.setText(getString(R.string.dfu_status_uploading_part, currentPart, partsTotal));
            //else
                //tvDFUStatus.setText(R.string.dfu_status_uploading);
        }

        @Override
        public void onError(final String deviceAddress, final int error, final int errorType, final String message) {
            showErrorMessage(message);

            // We have to wait a bit before canceling notification. This is called before DfuService creates the last notification.
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    // if this activity is still open and upload process was completed, cancel the notification
                    final NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                    manager.cancel(DfuService.NOTIFICATION_ID);
                }
            }, 200);
        }
    };

    private void onTransferCompleted() {
        // clearUI(true);
        Toast.makeText(DfuActivity.this, "Application has been Transferred Successfully", Toast.LENGTH_SHORT).show();
    }

    public void onUploadCanceled() {
        //clearUI(false);
        Toast.makeText(DfuActivity.this, "Uploading Cancelled", Toast.LENGTH_SHORT).show();
    }

    private void showErrorMessage(final String message) {
        //clearUI(false);
        Toast.makeText(DfuActivity.this, "Uploading Failed: " + message, Toast.LENGTH_SHORT).show();
    }

    private void showUploadCancelDialog() {
        final LocalBroadcastManager manager = LocalBroadcastManager.getInstance(this);
        final Intent pauseAction = new Intent(DfuService.BROADCAST_ACTION);
        pauseAction.putExtra(DfuService.EXTRA_ACTION, DfuService.ACTION_PAUSE);
        manager.sendBroadcast(pauseAction);

        final UploadCancelFragment fragment = UploadCancelFragment.getInstance();
        fragment.show(getSupportFragmentManager(), TAG);
    }

    private boolean isDfuServiceRunning() {
        ActivityManager manager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (DfuService.class.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }
}
