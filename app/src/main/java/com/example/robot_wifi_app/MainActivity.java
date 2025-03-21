package com.example.robot_wifi_app;

import android.os.Handler;
import android.view.MotionEvent;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.io.IOException;

import com.example.you_tube_wifi_app.R;
import com.google.android.material.slider.Slider;
import okhttp3.*;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;

public class MainActivity extends AppCompatActivity {
    Button btnLeft;
    Button btnRight;
    Button btnReverse;
    Slider slider;
    TextView txtRev;
    private boolean isForward = false;
    private int lastSpeed = 0;
    private long lastSentTime = 0;
    private static final long MIN_INTERVAL_MS = 700;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ConnectivityManager connManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkRequest.Builder builder = new NetworkRequest.Builder();
        builder.addTransportType(NetworkCapabilities.TRANSPORT_WIFI);
        builder.addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        NetworkRequest request = builder.build();
        connManager.requestNetwork(request, new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                connManager.bindProcessToNetwork(network);
            }
        });

        btnLeft = findViewById(R.id.btnLeft);
        btnRight = findViewById(R.id.btnRight);
        btnReverse = findViewById(R.id.btnReverse);
        slider = findViewById(R.id.sliderSpeed);
        txtRev = findViewById(R.id.textDirArduino);

        btnLeft.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        sendPostRequest("{\"side\": \"left\"}");
                        return true;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        sendPostRequest("{\"side\": \"normal\"}");
                        return true;

                    default:
                        return false;
                }
            }
        });


        btnRight.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        sendPostRequest("{\"side\": \"right\"}");
                        return true;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        sendPostRequest("{\"side\": \"normal\"}");
                        return true;

                    default:
                        return false;
                }
            }
        });

        btnReverse.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String direction = isForward ? "forward" : "back";
                isForward = !isForward;
                sendPostRequest("{\"direction\": \"" + direction + "\"}");
            }
        });

        slider.addOnChangeListener(new Slider.OnChangeListener() {
            @Override
            public void onValueChange(@NonNull @NotNull Slider slider, float value, boolean fromUser) {
                int speed = (int) value;
                long currentTime = System.currentTimeMillis();
                if (Math.abs(speed - lastSpeed) > 1 && (currentTime - lastSentTime) > MIN_INTERVAL_MS) {
                    sendPostRequest("{\"speed\": " + speed + "}");
                    lastSpeed = speed;
                    lastSentTime = currentTime;
                }
            }
        });
    }

    public void sendPostRequest(String json) {
        Log.d("POST Request", "JSON Sent: " + json);

        OkHttpClient client = new OkHttpClient();
        MediaType mediaType = MediaType.parse("application/json; charset=utf-8");
        RequestBody body = RequestBody.create(json, mediaType);

        Request request = new Request.Builder()
                .url("http://192.48.56.2/data")
                .post(body)
                .header("Connection", "close")
                .build();

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Log.d("POST Request", "Sending JSON: " + json);
                    Response response = client.newCall(request).execute();

                    if (response.isSuccessful()) {
                        String responseBody = response.body().string();
                        Log.d("Response Success", "Code: " + response.code() + " | Body: " + responseBody);

                        try {
                            JSONObject jsonResponse = new JSONObject(responseBody);
                            if (jsonResponse.has("direction")) {
                                String direction = jsonResponse.getString("direction");
                                runOnUiThread(() -> txtRev.setText(direction));
                            } else {
                                Log.d("Json missing", "Direction key missing");
                                runOnUiThread(() -> txtRev.setText("Direction key missing"));
                            }
                        } catch (Exception e) {
                            Log.e("JSON Parsing Error", "Failed to parse JSON", e);
                            runOnUiThread(() -> txtRev.setText("Invalid JSON response"));
                        }
                    } else {
                        Log.e("Response Error", "Code: " + response.code());
                        runOnUiThread(() -> txtRev.setText("Request failed: " + response.code()));
                    }

                } catch (IOException e) {
                    Log.e("POST Request Error", "Exception occurred", e);
                    runOnUiThread(()-> txtRev.setText("Request error"));
                }
            }
        }).start();
    }
}