package com.example.walking_friend_map;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.location.Location;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.ButtCap;
import com.google.android.gms.maps.model.CustomCap;
import com.google.android.gms.maps.model.JointType;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.maps.model.RoundCap;
import com.google.android.gms.maps.model.SquareCap;

import java.util.ArrayList;
import java.util.List;

public class RouteManager {
    private static final String TAG = "Route Manager";


    private static final int[] ROUTE_COLOR = {0xff17174a, 0x77c1cdc3, 0x77ffbe8c, 0x7756b390};
    private static final float ROUTE_WIDTH = 15;
    private static final float ROUTE_HIGHLIGHT_WIDTH = 30;

    private static List<Polyline> routes = new ArrayList<Polyline>();
    private static Polyline selected_route = null;
    private static Polyline user_path = null;

    private static LatLng[] route1 = {
            new LatLng(37.450424, 126.951976),
            new LatLng(37.450778, 126.952216),
            new LatLng(37.452025, 126.952803),
            new LatLng(37.452147, 126.952415),
            new LatLng(37.452259,126.952205),
            new LatLng(37.452457, 126.952019),
            new LatLng(37.452846, 126.952145),
            new LatLng(37.453335, 126.952084),
            new LatLng(37.453531, 126.952068)
    };

    private static LatLng[] route2 = {
            new LatLng(37.452025, 126.952803),
            new LatLng(37.452147, 126.952415),
            new LatLng(37.452259,126.952205),
            new LatLng(37.452457, 126.952019),
            new LatLng(37.452846, 126.952145),
            new LatLng(37.453335, 126.952084),
            new LatLng(37.453531, 126.952068),
            new LatLng(37.453375, 126.952284),
            new LatLng(37.453285, 126.952437),
            new LatLng(37.453230, 126.952659),
            new LatLng(37.453221, 126.952875),
            new LatLng(37.453249, 126.953001),
            new LatLng(37.453345, 126.953265),
            new LatLng(37.453565, 126.953512),
            new LatLng(37.452025, 126.952803)
    };

    public static void addRoutes(GoogleMap mMap) {
        // Add user path
        user_path = mMap.addPolyline(new PolylineOptions()
                .clickable(false));

        stylePolyline(user_path, 0, "user");

        // Polylines are useful to show a route or some other connection between points.
        routes.add(mMap.addPolyline(new PolylineOptions()
                .clickable(true)
                .add(route1)));

        routes.add(mMap.addPolyline(new PolylineOptions()
                .clickable(true)
                .add(route2)));

        routes.get(0).setTag("301-engineer");
        routes.get(1).setTag("300-300");

        stylePolyline(routes.get(0),1, "route");
        stylePolyline(routes.get(1),2, "route");

    }


    /**
     * Styles the polyline, based on type.
     * @param polyline The polyline object that needs styling.
     */
    private static void stylePolyline(Polyline polyline, int linenum, String type) {

        switch (type) {
            // If no type is given, allow the API to use the default.
            case "route":
                // style polyline
                polyline.setStartCap(new RoundCap());
                polyline.setEndCap(new RoundCap());
                polyline.setWidth(ROUTE_WIDTH);
                polyline.setColor(ROUTE_COLOR[linenum]);
                polyline.setJointType(JointType.ROUND);
                break;
            case "user":
                polyline.setStartCap(new RoundCap());
                polyline.setEndCap(new ButtCap());
                polyline.setWidth(ROUTE_WIDTH/2);
                polyline.setColor(ROUTE_COLOR[linenum]);
                polyline.setJointType(JointType.ROUND);
                break;
        }


    }

    public static void polylineClicked(Context mContext, Polyline route){
        // reset all route
        for(int idx=0; idx < routes.size(); idx++){
            routes.get(idx).setWidth(ROUTE_WIDTH);
            routes.get(idx).setColor(ROUTE_COLOR[idx+1]);
        }

        route.setWidth((ROUTE_HIGHLIGHT_WIDTH));
        route.setColor((route.getColor() % 0x1000000) +0xff000000);

        // mark route selected
        selected_route = route;

    }

    public static void onRouteSelected(Context mContext){

        if(selected_route==null){
            Toast.makeText(mContext, "Route not selected", Toast.LENGTH_SHORT).show();
            return;
        }

        // make all routes invisible
        for(Polyline route : routes){
            route.setVisible(false);
        }

        selected_route.setVisible(true);
        selected_route.setWidth((ROUTE_WIDTH));

    }

    public static void updateUserPath(Location lastLocation){
        Log.d(TAG, "############# get Update Location");

        LatLng newLoc = new LatLng(lastLocation.getLatitude(), lastLocation.getLongitude());

        List<LatLng> points = user_path.getPoints();
        points.add(newLoc);
        user_path.setPoints(points);

    }
}
