package com.team4.walkingfriend.result;

import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP;

import android.content.Intent;
import android.os.Bundle;


import androidx.appcompat.app.AppCompatActivity;

import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;


import com.team4.walkingfriend.DetectorActivity;
import com.team4.walkingfriend.MainActivity;
import com.team4.walkingfriend.R;



public class ResultActivity extends AppCompatActivity {

    Button endButton;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_result);

        endButton = findViewById(R.id.return_button);
        endButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(view.getContext(), MainActivity.class);
                intent.addFlags(FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
                finishAffinity();
            }
        });

    }

}