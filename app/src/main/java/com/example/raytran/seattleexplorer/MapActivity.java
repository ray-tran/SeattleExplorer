package com.example.raytran.seattleexplorer;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Color;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.raytran.seattleexplorer.models.PlaceInfo;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesNotAvailableException;
import com.google.android.gms.common.GooglePlayServicesRepairableException;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.places.AutocompletePrediction;
import com.google.android.gms.location.places.GeoDataClient;
import com.google.android.gms.location.places.Place;
import com.google.android.gms.location.places.PlaceBuffer;
import com.google.android.gms.location.places.Places;
import com.google.android.gms.location.places.ui.PlacePicker;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;


import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import Modules.DirectionFinder;
import Modules.DirectionFinderListener;
import Modules.Route;

/**
 * Created by Ray Tran on 3/6/18.
 */

public class MapActivity extends AppCompatActivity implements OnMapReadyCallback,
        GoogleApiClient.OnConnectionFailedListener {

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        Toast.makeText(this, "Map is Ready", Toast.LENGTH_SHORT).show();
        Log.d(TAG, "onMapReady: map is ready");

        try {
            // Customise the styling of the base map using a JSON object defined
            // in a raw resource file.
            boolean success = googleMap.setMapStyle(
                    MapStyleOptions.loadRawResourceStyle(
                            this, R.raw.mapstyle));

            if (!success) {
                Log.e(TAG, "Style parsing failed.");
            }
        } catch (Resources.NotFoundException e) {
            Log.e(TAG, "Can't find style. Error: ", e);

        }
            mMap = googleMap;

        //if permission is granted, zoom in current location
        if (mLocationPermissionsGranted) {
            getDeviceLocation();

            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this,
                    Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            mMap.setMyLocationEnabled(true); //blue dot of current location
            mMap.getUiSettings().setMyLocationButtonEnabled(false);

            init();
        }
    }

    private static final String TAG = "MapActivity";
    private static final String FINE_LOCATION = Manifest.permission.ACCESS_FINE_LOCATION;
    private static final String COARSE_LOCATION = Manifest.permission.ACCESS_COARSE_LOCATION;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1234;
    private static final float DEFAULT_ZOOM = 15f;
    private static final LatLngBounds LAT_LNG_BOUNDS = new LatLngBounds(
            new LatLng(-40, -168), new LatLng(71, 136));
    private static final int PLACE_PICKER_REQUEST = 1;


    //widges
    private AutoCompleteTextView mSearchText;
    private ImageView mGps, mInfo, mPlacePicker, mList, mAdd, mDone;
    private Button mClear;

    //variables
    private Boolean mLocationPermissionsGranted = false;
    private GoogleMap mMap;
    private FusedLocationProviderClient mFusedLocationProviderClient;
    private PlaceAutocompleteAdapter mPlaceAutocompleteAdapter;
    protected GeoDataClient mGeoDataClient;
    private GoogleApiClient mGoogleApiClient;
    private PlaceInfo mPlace;
    private Marker mMarker;
    private ArrayList<PlaceInfo> placeList = new ArrayList<PlaceInfo>();
    private List<Polyline> polylinePaths = new ArrayList<>();
    private ProgressDialog progressDialog;
    private ArrayList<PlaceInfo> trip = new ArrayList<>();
    private ArrayList<PlaceInfo> temp = new ArrayList<>();

    private static int count = 0;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);
        mSearchText = (AutoCompleteTextView) findViewById(R.id.input_search);
        mGps = (ImageView) findViewById(R.id.ic_gps);
        mInfo = (ImageView) findViewById(R.id.place_info);
        mPlacePicker = (ImageView) findViewById(R.id.place_picker);
        mList = (ImageView) findViewById(R.id.place_list);
        mAdd = (ImageView) findViewById(R.id.place_add);
        mDone = (ImageView) findViewById(R.id.place_done);
        mClear = (Button) findViewById(R.id.ic_clear);

        getLocationPermission();

    }

    @Override
    protected void onPause() {
        super.onPause();

        Log.d(TAG, "onPause()");

        SharedPreferences sharedPref = getPreferences(Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();

        for(PlaceInfo i: placeList) {
            int w = 1;
            editor.putString(Integer.toString(w) + "_name", i.getName());
            editor.putString(Integer.toString(w) + "_address", i.getAddress());
            editor.putString(Integer.toString(w) + "_phone", i.getPhoneNumber());
            editor.putString(Integer.toString(w) + "_id", i.getId());
 //         editor.putString(Integer.toString(w) + "_uri", i.getWebsiteUri().toString());
            editor.putFloat(Integer.toString(w) + "_lat", (float) i.getLatlng().latitude);
            editor.putFloat(Integer.toString(w) + "_lng", (float) i.getLatlng().longitude);
            editor.putFloat(Integer.toString(w) + "_rating", i.getRating());
            editor.putInt("count", count);


            Log.d(TAG, "Writing location: " + w + ". " +  i.getName() + '\n');
            w++;
        }

        //commit edits to persistent storage (apply does this in the background)
        editor.apply();
    }


    private void init() {
        Log.d(TAG, "init: initializing");


/*
        SharedPreferences sharedPref = getPreferences(Context.MODE_PRIVATE);

        int mCount = sharedPref.getInt("count", 0);
        count = mCount;

        for(int i = 0; i < mCount; i++) {

            LatLng ll = new LatLng(sharedPref.getFloat(Integer.toString(i+1) + "_lat",0),
                    sharedPref.getFloat(Integer.toString(i+1) + "_lng",0));
            Uri uri = Uri.parse("");

            PlaceInfo p = new PlaceInfo(sharedPref.getString(Integer.toString(i+1) + "_name",""),
                    sharedPref.getString(Integer.toString(i+1) + "_address",""),
                    sharedPref.getString(Integer.toString(i+1) + "_phone",""),
                    sharedPref.getString(Integer.toString(i+1) + "_id",""),
                    uri,
                    ll,
                    sharedPref.getFloat(Integer.toString(i+1) + "_rating",0), "");
            placeList.add(p);
        }
*/
        mGoogleApiClient = new GoogleApiClient
                .Builder(this)
                .addApi(Places.GEO_DATA_API)
                .addApi(Places.PLACE_DETECTION_API)
                .enableAutoManage(this, this)
                .build();

        // Construct a GeoDataClient.
        mGeoDataClient = Places.getGeoDataClient(this, null);
        mSearchText.setOnItemClickListener(mAutocompleteClickListener);
        mPlaceAutocompleteAdapter = new PlaceAutocompleteAdapter(this, mGeoDataClient, LAT_LNG_BOUNDS, null);
        mSearchText.setAdapter(mPlaceAutocompleteAdapter);
        mSearchText.setOnEditorActionListener(new TextView.OnEditorActionListener() {

            public boolean onEditorAction(TextView textView, int actionId, KeyEvent keyEvent) {
                if (actionId == EditorInfo.IME_ACTION_SEARCH
                        || actionId == EditorInfo.IME_ACTION_DONE
                        || keyEvent.getAction() == keyEvent.ACTION_DOWN
                        || keyEvent.getAction() == keyEvent.KEYCODE_ENTER) {

                    //execute our method for searching
                    geoLocate();
                }
                return false;
            }
        });

        //OnClickListener for the gps button
        mGps.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.d(TAG, "onClick: clicked gps icon");
                getDeviceLocation();
            }
        });

        //OnClickListener for info button
        mInfo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.d(TAG, "onClick: clicked place info");
                try {
                    if (mMarker.isInfoWindowShown()) {
                        mMarker.hideInfoWindow();
                    }
                    else {
                        Log.d(TAG, "onClick: place info: " + mPlace.toString());
                        mMarker.showInfoWindow();
                    }
                }
                catch (NullPointerException e) {
                    Log.e(TAG, "onClick: NullPointerException" + e.getMessage() );
                }
            }
        });

        mPlacePicker.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                PlacePicker.IntentBuilder builder = new PlacePicker.IntentBuilder();

                try {
                    startActivityForResult(builder.build(MapActivity.this), PLACE_PICKER_REQUEST);
                } catch (GooglePlayServicesRepairableException e) {
                    Log.e(TAG, "onClick: GooglePlayServicesRepairableException" + e.getMessage());
                } catch (GooglePlayServicesNotAvailableException e) {
                    Log.e(TAG, "onClick: GooglePlayServicesNotAvailableException" + e.getMessage());
                }
            }
        });

        //OnClickListener for add button
        mAdd.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mPlace != null) {
                    Log.d(TAG, "onClick: adding place to arraylist: " + mPlace.getName());
                    placeList.add(mPlace);
                    count++;
                    Toast.makeText(MapActivity.this, "Adding " + mPlace.getName() + " to trip."
                            , Toast.LENGTH_SHORT).show();
                }
            }
        });

        //OnClickListener for show list button
        mList.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                final AlertDialog.Builder listDialog = new AlertDialog.Builder(MapActivity.this);
                String pList = "";
                for (PlaceInfo p : placeList) {
                    pList = pList + p.getName() + "\n";
                }
                listDialog.setMessage(pList)
                        .setCancelable(true)
                        .setNegativeButton("Close", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        dialogInterface.cancel();
                    }
                });

                AlertDialog displayListDialog = listDialog.create();
                displayListDialog.setTitle("List of chosen places.");
                displayListDialog.show();
            }
        });

        //OnClickListener for Done button
        mDone.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                displayResult();
            }
        });

        //OnClickListener for clear text "x" button
        mClear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mSearchText.setText("");
            }
        });

        hideSoftKeyboard();
    }

    private void displayResult() {
        if (placeList.size() == 0) {
            Toast.makeText(MapActivity.this, "Add places to list first.", Toast.LENGTH_SHORT).show();
            return;
        }
        else  if (trip.size() == 0){
            findShortestPath();
        }

        final AlertDialog.Builder resultDialog = new AlertDialog.Builder(MapActivity.this);

        String shortestTrip = "";

        for (int i = 0; i < trip.size(); i++) {
            shortestTrip += Integer.toString(i + 1) + ". " + trip.get(i).getName() + "\n";
        }
        resultDialog.setMessage(shortestTrip)
                .setCancelable(true)
                .setNegativeButton("Close", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        dialogInterface.cancel();
                    }
                });

        AlertDialog displayListDialog = resultDialog.create();
        displayListDialog.setTitle("Your trip:");
        displayListDialog.show();

    }

    //method to find shortest path
    private void findShortestPath() {

        temp.addAll(placeList);
        trip.add(temp.get(0));
        temp.remove(0);

        while (temp.size() > 1) {
            findClosestPlace(trip);
        }

        trip.add(temp.get(0));

    }

    //find the closest place from placeList to last element of sorted, add that to sorted
    private void findClosestPlace(ArrayList<PlaceInfo> sorted) {

        double minDistance = -1;
        PlaceInfo currentClosest = null;

        for (int i = 0; i < temp.size(); i++) {
            if (sorted.get(sorted.size()-1) == temp.get(i)) {
                //do nothing
                return;
            }
            else {
                Log.d(TAG, "findShortestPath: Current min: " + minDistance);
                Log.d(TAG, "findShortestPath: Comparing " + sorted.get(sorted.size()-1).getName() + " and " + temp.get(i).getName());
                double distance = findDistance(sorted.get(sorted.size() - 1), temp.get(i));
                Log.d(TAG, "findShortestPath: distance between i and j: " + distance);
                if (minDistance == -1) {
                    minDistance = distance;
                    currentClosest = temp.get(i);
                    Log.d(TAG, "findShortestPath: new minDistance is first one");
                }
                else {
                    if (distance < minDistance) {
                        minDistance = distance;
                        currentClosest = temp.get(i);
                        Log.d(TAG, "findShortestPath: update new minDistance");
                    }
                }
            }
        }
        Log.d(TAG, "findShortestPath: adding to trip: " + currentClosest.getName());
        sorted.add(currentClosest);
        temp.remove(currentClosest);
    }

    private double findDistance(PlaceInfo start, PlaceInfo end) {
        Location origin = new Location("");
        origin.setLatitude(start.getLatlng().latitude);
        origin.setLongitude(start.getLatlng().longitude);
        Location dest = new Location("");
        dest.setLatitude(end.getLatlng().latitude);
        dest.setLongitude(end.getLatlng().longitude);
        float result = origin.distanceTo(dest);
        return result;
    }


    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == PLACE_PICKER_REQUEST) {
            if (resultCode == RESULT_OK) {
                Place place = PlacePicker.getPlace(this, data);

                PendingResult<PlaceBuffer> placeResult = Places.GeoDataApi.getPlaceById(mGoogleApiClient, place.getId());
                placeResult.setResultCallback(mUpdatePlaceDetailsCallback);
            }
        }
    }

    private void geoLocate() {
        Log.d(TAG, "geoLocate: geolocating");
        String searchString = mSearchText.getText().toString();
        Geocoder geocoder = new Geocoder(MapActivity.this);
        List<Address> list = new ArrayList<>();
        try {
            list = geocoder.getFromLocationName(searchString, 1);
        }
        catch (IOException e) {
            Log.e(TAG, "geoLocate: IOException" + e.getMessage());
        }
        if (list.size() > 0) {
            Address address = list.get(0);
            Log.d(TAG, "geoLocate: found a location: " + address.toString());
            moveCamera(new LatLng(address.getLatitude(), address.getLongitude()), DEFAULT_ZOOM,
                    address.getAddressLine(0));
        }
    }

    //method to get current location of device
    private void getDeviceLocation() {
        Log.d(TAG, "getDeviceLocation: getting the current device location");

        mFusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);

        try {
            if (mLocationPermissionsGranted) {
                final Task location = mFusedLocationProviderClient.getLastLocation();
                location.addOnCompleteListener(new OnCompleteListener() {
                    @Override
                    public void onComplete(@NonNull Task task) {
                        if (task.isSuccessful()) {
                            Log.d(TAG, "onComplete: found location");
                            Location currentLocation = (Location) task.getResult();
                            moveCamera(new LatLng(currentLocation.getLatitude(), currentLocation.getLongitude()),
                                    DEFAULT_ZOOM, "My Location");
                        }
                         else {
                            Log.d(TAG, "onComplete: current location is null");
                            Toast.makeText(MapActivity.this, "unable to get current location", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }
        } catch (SecurityException e) {
            Log.e(TAG, "getDeviceLocation: SecurityException" + e.getMessage());
        }
    }

    //method to zoom the map in chosen location
    private void moveCamera(LatLng latLng, float zoom, PlaceInfo placeInfo) {
        Log.d(TAG, "moveCamera: moving the camera");
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, zoom));

        mMap.clear();

        mMap.setInfoWindowAdapter(new CustomInfoWindowAdapter(MapActivity.this));

        if (placeInfo != null) {
            try {
                String snippet = "Address: " + placeInfo.getAddress() + "\n" +
                        "Phone Number: " + placeInfo.getPhoneNumber() + "\n" +
                        "Website: " + placeInfo.getWebsiteUri() + "\n" +
                        "Price Rating: " + placeInfo.getRating() + "\n";

                MarkerOptions options = new MarkerOptions()
                        .position(latLng)
                        .title(placeInfo.getName())
                        .snippet(snippet);
                mMarker = mMap.addMarker(options);
            }
            catch (NullPointerException e) {
                Log.e(TAG, "moveCamera: NullPointerException " + e.getMessage() );
            }
        }
        else {
            mMap.addMarker(new MarkerOptions().position(latLng));
        }

        hideSoftKeyboard();
    }

    //method to zoom the map in chosen location
    private void moveCamera(LatLng latLng, float zoom, String title) {
        Log.d(TAG, "moveCamera: moving the camera");
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, zoom));

            MarkerOptions options = new MarkerOptions()
                    .position(latLng)
                    .title(title);
            mMap.addMarker(options);

        hideSoftKeyboard();
    }
    
    private void initMap() {
        Log.d(TAG, "initMap: initializing map");
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);

        mapFragment.getMapAsync(MapActivity.this);
    }

    //check if location permission is granted or not
    private void getLocationPermission() {
        Log.d(TAG, "getLocationPermission: getting location permission");
        String[] permissions = {Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION};

        if (ContextCompat.checkSelfPermission(this.getApplicationContext(),FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            if (ContextCompat.checkSelfPermission(this.getApplicationContext(),COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                mLocationPermissionsGranted = true;
                initMap();
            }
            else {
                ActivityCompat.requestPermissions(this, permissions, LOCATION_PERMISSION_REQUEST_CODE);
            }
        }
        else {
            ActivityCompat.requestPermissions(this, permissions, LOCATION_PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        Log.d(TAG, "onRequestPermissionsResult: called.");
            mLocationPermissionsGranted = false;

            switch (requestCode) {
                case LOCATION_PERMISSION_REQUEST_CODE: {
                    if (grantResults.length > 0) {
                        for (int i = 0; i < grantResults.length; i++) {
                            if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                                mLocationPermissionsGranted = false;
                                Log.d(TAG, "onRequestPermissionsResult: permission failed");
                                return;
                            }
                        }
                        Log.d(TAG, "onRequestPermissionsResult: permission granted");
                        mLocationPermissionsGranted = true;
                        //initialize map
                        initMap();
                    }
                }
            }
    }

    private void hideSoftKeyboard() {
        this.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
    }

    /*

    ----------------- Google Places API autocomplete suggestions ------------------

     */

    private AdapterView.OnItemClickListener mAutocompleteClickListener = new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
            hideSoftKeyboard();

            final AutocompletePrediction item = mPlaceAutocompleteAdapter.getItem(i);
            final String placeId = item.getPlaceId();

            PendingResult<PlaceBuffer> placeResult = Places.GeoDataApi.getPlaceById(mGoogleApiClient, placeId);
            placeResult.setResultCallback(mUpdatePlaceDetailsCallback);
        }
    };

    private ResultCallback<PlaceBuffer> mUpdatePlaceDetailsCallback = new ResultCallback<PlaceBuffer>() {
        @Override
        public void onResult(@NonNull PlaceBuffer places) {
            if (!places.getStatus().isSuccess()) {
                Log.d(TAG, "onResult: place query did not complete successfully" + places.getStatus().toString());
                places.release();
                return;

            }
            final Place place = places.get(0);

            try {
                mPlace = new PlaceInfo();
                mPlace.setName(place.getName().toString());
                mPlace.setAddress(place.getAddress().toString());
                mPlace.setId(place.getId());
                mPlace.setLatlng(place.getLatLng());
                mPlace.setPhoneNumber(place.getPhoneNumber().toString());
                mPlace.setRating(place.getRating());
                mPlace.setWebsiteUri(place.getWebsiteUri());

                Log.d(TAG, "onResult: place details: " + mPlace.toString());
            }
            catch (NullPointerException e) {
                Log.e(TAG, "onResult: NullPointerException" + e.getMessage());
            }

            moveCamera(new LatLng(place.getViewport().getCenter().latitude,
                    place.getViewport().getCenter().longitude), DEFAULT_ZOOM, mPlace);
        }
    };
}
