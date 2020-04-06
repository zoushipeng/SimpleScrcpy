package com.example.simplescrcpy;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public class FunctionActivity extends AppCompatActivity implements View.OnClickListener {
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_func);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()){
            case R.id.CameraCapture:
                startActivity(new Intent(FunctionActivity.this, CameraCaptureActivity.class));
                break;
            case R.id.H264Socket:
                startActivity(new Intent(FunctionActivity.this, H264SocketActivity.class));
                break;
            case R.id.ContinuousCapture:
                startActivity(new Intent(FunctionActivity.this, ContinuousCaptureActivity.class));
                break;
            case R.id.RecordFBO:
                startActivity(new Intent(FunctionActivity.this, RecordFBOActivity.class));
                break;
        }
    }
}
