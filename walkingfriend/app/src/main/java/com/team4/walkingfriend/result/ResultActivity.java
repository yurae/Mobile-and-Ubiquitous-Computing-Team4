package com.team4.walkingfriend.result;

import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP;

import static java.lang.Math.round;

import android.content.Intent;
import android.os.Bundle;


import androidx.appcompat.app.AppCompatActivity;

import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;


import com.bumptech.glide.Glide;
import com.team4.walkingfriend.DetectorActivity;
import com.team4.walkingfriend.MainActivity;
import com.team4.walkingfriend.R;
import com.team4.walkingfriend.maps.MapsActivity;


public class ResultActivity extends AppCompatActivity {

    Button endButton;
    TextView score_text;
    ImageView fairy_view;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_result);

        Bundle b = getIntent().getExtras();
        int coins = b.getInt("coins");

        score_text = findViewById(R.id.result_score_value);
        score_text.setText(coins + " coins");

        double distance = round(com.team4.walkingfriend.maps.MapsActivity.getUserDistance()/(double) 1000);
        double duration = round(com.team4.walkingfriend.maps.MapsActivity.getUserTimestamp()*100/(double) 6000);
        double speed = duration == 0? 0 : distance/(duration/60);

        score_text = findViewById(R.id.result_speed_value);
        score_text.setText(speed + " km/h");

        score_text = findViewById(R.id.result_time_value);
        score_text.setText(duration+ " min");

        fairy_view = findViewById(R.id.imageView);
        Glide.with(this).load(R.drawable.fairy_smile).into(fairy_view);

        endButton = findViewById(R.id.return_button);
        endButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(view.getContext(), MainActivity.class);
                intent.addFlags(FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
                finish();
            }
        });

    }

}