package com.team4.walkingfriend;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import com.team4.walkingfriend.maps.MapsActivity;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    public void onPlayClicked(View view){
//        Intent intent = new Intent(this, DetectorActivity.class);
        Intent intent = new Intent(this, MapsActivity.class);
        startActivity(intent);
    }
}
