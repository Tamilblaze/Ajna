package com.tamilblaze.example.ajna01;


import android.app.Activity;
import android.app.Fragment;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;


/**
 * A simple {@link Fragment} subclass.
 */
public class UserFragment extends Fragment {

    private final String TAG = "AjnaTAG";



    public UserFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment

        View userView = inflater.inflate(R.layout.fragment_user, container, false);
        getActivity().startService(new Intent(getActivity(), TrackMovement.class));



        return userView;

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        getActivity().stopService(new Intent(getActivity(), TrackMovement.class));

    }
}
