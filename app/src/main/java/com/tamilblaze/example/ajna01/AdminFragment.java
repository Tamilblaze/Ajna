package com.tamilblaze.example.ajna01;


import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;


/**
 * A simple {@link Fragment} subclass.
 */
public class AdminFragment extends Fragment {

    private static String TAG = "AjnaTAG";
    private final int DISPLAY_LENGTH = 4000; // number of milliseconds for which to show the dialogue box.

    private WebView wvMap;
    String area, subArea, locality, feature, countryName;
    String comma = ", ";

    private Context mContext;

    private ProgressDialog progress;


    public AdminFragment() {
        // Required empty public constructor
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment

        String latitude = String.valueOf(getArguments().getDouble("latitude"));
        String longitude = String.valueOf(getArguments().getDouble("longitude"));

        area = getArguments().getString("area");
        subArea = getArguments().getString("subArea");
        locality = getArguments().getString("locality");
        feature = getArguments().getString("feature");
        countryName = getArguments().getString("countryName");

        Log.d(TAG, "Frag Lat: " + latitude);
        Log.d(TAG, "Frag Lon: " + longitude);

        View adminView = inflater.inflate(R.layout.fragment_admin, container, false);
        wvMap = (WebView) adminView.findViewById(R.id.wvMap);
        //tvadminLog = (TextView) adminView.findViewById(R.id.tvAdminLog);

        wvMap.getSettings().setJavaScriptEnabled(true);
        wvMap.addJavascriptInterface(new WebAppInterface(mContext), "Android");
        wvMap.loadUrl("file:///android_asset/locate.html?lat=" + latitude + "&lon=" + longitude);

        progress = new ProgressDialog(getActivity());
        progress.setMessage("Finding you.. Please wait!");
        progress.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        progress.setIndeterminate(true);
        progress.setCancelable(false);
        progress.show();
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {

                progress.dismiss();

            }
        }, DISPLAY_LENGTH);


        return adminView;
    }

    /**
     * *******************************************************************************
     * Class to interface webapp with android;
     * This allows the class object's methods to be accessed from JavaScript.
     * only public methods that are annotated with JavascriptInterface can be accessed from JavaScript
     */
    public class WebAppInterface {
        Context wContext;

        /**
         * Instantiate the interface and set the context
         */
        WebAppInterface(Context c) {
            wContext = c;
        }

        @JavascriptInterface
        public void showAndroidAddress() {
            Log.d(TAG, "Blip Pressed");
            android.support.v7.app.AlertDialog.Builder builder = new android.support.v7.app.AlertDialog.Builder(getActivity());
            builder.setTitle("Found you at");
            builder.setMessage(feature + comma + locality + comma + subArea + comma + area + comma + countryName).setPositiveButton("ok", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    // FIRE ZE MISSILES!
                    Log.d(TAG, "Alert Click");
                }
            });
            android.support.v7.app.AlertDialog alertDialog = builder.create();
            alertDialog.show();


        }
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        mContext = context;
    }

}
