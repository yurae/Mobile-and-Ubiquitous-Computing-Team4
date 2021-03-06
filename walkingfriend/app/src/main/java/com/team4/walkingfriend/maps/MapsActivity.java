package com.team4.walkingfriend.maps;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.maps.android.SphericalUtil;
import com.team4.walkingfriend.DetectorActivity;
import com.team4.walkingfriend.R;

import java.util.List;

public class MapsActivity extends FragmentActivity implements
        OnMapReadyCallback,
        ActivityCompat.OnRequestPermissionsResultCallback,
        GoogleMap.OnPolylineClickListener
        {

    private GoogleMap mMap;

    private boolean track_user_started = false;
    FusedLocationProviderClient mFusedLocationClient;
    private HandlerThread addressHandlerThread;
    private Handler addressHandler;
    protected Location mLastLocation;

    private LocationRequest mLocationRequest;
    private LocationCallback mLocationCallback;

    private static final long UPDATE_INTERVAL_IN_MILLISECONDS = 10000;
    private static final long FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS = UPDATE_INTERVAL_IN_MILLISECONDS / 2;



    /**
     * Flag indicating whether a requested permission has been denied after returning in
     */
    private boolean PermissionGranted = true;
    private static final int PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 1;

    private static final String TAG = MapsActivity.class.getSimpleName();

    /**
     * views
     */
    Button startButton;
    Button endButton;
    LinearLayout info;
    TextView info_title;
    TextView info_distance;
    TextView info_level;

    private static Long timestamp;
    private static float LastUserSpeed;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_maps);

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        // Construct a FusedLocationProviderClient
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        createLocationCallback();
        createLocationRequest();


        // Obtain button fragments
        startButton = findViewById(R.id.Startbutton);
//        endButton = findViewById(R.id.Endbutton);

        startButton.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view){
                view.setVisibility(View.INVISIBLE);
                track_user_started = true;
                com.team4.walkingfriend.maps.RouteManager.onRouteSelected(view.getContext());
                getUpdateLocation();

                timestamp = System.currentTimeMillis()/1000; // walking start time

                Intent intent = new Intent(view.getContext(), DetectorActivity.class);
                Bundle b = new Bundle();
                b.putString("title", info_title.getText().toString()); //Your id
                b.putString("distance", info_distance.getText().toString());
                b.putString("level", info_level.getText().toString());
                intent.putExtras(b); //Put your id to your next Intent
                startActivity(intent);
            }
        });

//        endButton.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View view) {
//                endButton.setVisibility(View.INVISIBLE);
//                track_user_started = false;
//                stopUpdateLocation();
//                finishAffinity();
//            }
//        });

        // Obtain Text view for route info
        info = findViewById(R.id.info);
        info_title = findViewById(R.id.info_title);
        info_distance = findViewById(R.id.info_distance);
        info_level = findViewById(R.id.info_level);

        info.setVisibility(View.INVISIBLE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "############# Destroy");
        finishAffinity();
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(TAG, "############# Stop");
    }


    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {


        // Set map display options
        try {
            // Customise the styling of the base map using a JSON object defined
            // in a raw resource file.
            boolean success = googleMap.setMapStyle(
                    MapStyleOptions.loadRawResourceStyle(
                            this, R.raw.style_json));

            if (!success) {
                Log.e(TAG, "Style parsing failed.");
            }
        } catch (Resources.NotFoundException e) {
            Log.e(TAG, "Can't find style. Error: ", e);
        }

        // Set center of the map
        mMap = googleMap;
        LatLng snu = new LatLng(37.458868, 126.953317);
//        mMap.addMarker(new MarkerOptions().position(snu).title("Marker in snu center"));
        mMap.moveCamera(CameraUpdateFactory.newLatLng(snu));


        // Add polylines to the map.
        com.team4.walkingfriend.maps.RouteManager.addRoutes(mMap);
        // Set listeners for click events.
        mMap.setOnPolylineClickListener(this);
        mMap.setMyLocationEnabled(true);
    }



    @Override
    protected void onStart() {
        super.onStart();
        if (!checkPermissions()) {
            requestPermissions();
        } else {
        }
    }

    private boolean checkPermissions() {
        int permissionState = ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION);
        return permissionState == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermissions() {
        ActivityCompat.requestPermissions(MapsActivity.this,
                new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION},
                34);
    }

    @SuppressWarnings("MissingPermission")
    private void getUpdateLocation() {
        Log.d(TAG, "############# get Update Location");
        mFusedLocationClient.requestLocationUpdates(mLocationRequest,
                mLocationCallback, Looper.myLooper());
        if(mMap != null) {
            if (mLastLocation != null) {
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(
                        new LatLng(mLastLocation.getLatitude(),
                                mLastLocation.getLongitude()), 15));
            }
        }
    }

    private void stopUpdateLocation() {
        // It is a good practice to remove location requests when the activity is in a paused or
        // stopped state. Doing so helps battery performance and is especially
        // recommended in applications that request frequent location updates.
        mFusedLocationClient.removeLocationUpdates(mLocationCallback)
                .addOnCompleteListener(this, new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {

                    }
                });
    }

    private void createLocationRequest() {
        Log.d(TAG, "############# createLocationrequest in");

        mLocationRequest = LocationRequest.create();

        // Sets the desired interval for active location updates. This interval is
        // inexact. You may not receive updates at all if no location sources are available, or
        // you may receive them slower than requested. You may also receive updates faster than
        // requested if other applications are requesting location at a faster interval.
        mLocationRequest.setInterval(UPDATE_INTERVAL_IN_MILLISECONDS);

        // Sets the fastest rate for active location updates. This interval is exact, and your
        // application will never receive updates faster than this value.
        mLocationRequest.setFastestInterval(FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS);

        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
    }

    /**
     * Creates a callback for receiving location events.
     */
    private void createLocationCallback() {
        Log.d(TAG, "############# create location callback");

        mLocationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                super.onLocationResult(locationResult);

                mLastLocation = locationResult.getLastLocation();
                //  mLastUpdateTime = DateFormat.getTimeInstance().format(new Date());
                LastUserSpeed = mLastLocation.getSpeed();
                updateLocationUI();
            }
        };
    }

    private void updateLocationUI(){
        Log.d(TAG, "############# Update Location UI");

        LatLng LastLatLng = new LatLng(mLastLocation.getLatitude(), mLastLocation.getLongitude());

        com.team4.walkingfriend.maps.RouteManager.updateUserPath(mLastLocation);
    }

    @Override
    public void onPolylineClick(Polyline polyline) {
        com.team4.walkingfriend.maps.RouteManager.polylineClicked(this, polyline);

        updateInfoText(polyline);
    }

    public void updateInfoText(Polyline selected_route){
        info.setVisibility(View.VISIBLE);

        String route_title = ""+selected_route.getTag();
        double route_distance = getDistance(selected_route);

        info_title.setText(route_title);
        info_distance.setText("Distance : "+ String.format("%.2f", route_distance)+"m");
        info_level.setText("Level : ???????????????");
    }
    private double getDistance(Polyline route){
        List<LatLng> Points = route.getPoints();
        double distance = SphericalUtil.computeLength(Points);

        return distance;
    }
    public static double getUserDistance(){
        List<LatLng>  Points = RouteManager.user_path.getPoints();
        double distance = SphericalUtil.computeLength(Points);

        return distance;
    }
    public static double getUserTimestamp(){
        long timetamp_now = System.currentTimeMillis()/1000;
        return timetamp_now-timestamp;
    }
    public static float getUserSpeed(){
        return LastUserSpeed;
    }

}