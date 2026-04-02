package com.marcin.robot_wifi_app;

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
import android.widget.Button;
import android.widget.TextView;

import java.io.IOException;

import com.google.android.material.slider.Slider;
import okhttp3.*;
import org.jetbrains.annotations.NotNull;
import org.json.JSONException;
import org.json.JSONObject;

public class MainActivity extends AppCompatActivity {
    private Button btnLeft;
    private Button btnRight;
    private Button btnReverse;
    private Slider sliderSpeed;
    private TextView txtDirectionStatus;

    private boolean isForward = false;
    private int lastSpeed = 0;
    private long lastSentTime = 0;

    private static final long MIN_INTERVAL_MS = 700;
    private static final String ROBOT_URL = "http://192.48.56.2/data";
    private static final MediaType JSON_TYPE = MediaType.parse("application/json; charset=utf-8");

    private final OkHttpClient httpClient = new OkHttpClient();

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initWiFiBinding();
        initUI();
        setupListeners();
    }

    private void initWiFiBinding() {

        ConnectivityManager connManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkRequest request = new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build();

        connManager.requestNetwork(request, new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                connManager.bindProcessToNetwork(network);
                Log.d("RobotWiFi", "Process bound to WiFi network");
            }
        });
    }

    private void initUI() {

        btnLeft = findViewById(R.id.btnLeft);
        btnRight = findViewById(R.id.btnRight);
        btnReverse = findViewById(R.id.btnReverse);
        sliderSpeed = findViewById(R.id.sliderSpeed);
        txtDirectionStatus = findViewById(R.id.textDirArduino);
    }

    private void setupListeners() {

        btnLeft.setOnTouchListener((v, event) -> handleSideControl("left", event));
        btnRight.setOnTouchListener((v, event) -> handleSideControl("right", event));

        btnReverse.setOnClickListener(v -> {
            isForward = !isForward;
            String directionValue = isForward ? "forward" : "back";
            sendRobotCommand("direction", directionValue);
        });

        sliderSpeed.addOnChangeListener((slider, value, fromUser) -> {
            int speed = (int) value;
            long currentTime = System.currentTimeMillis();
            if (Math.abs(speed - lastSpeed) > 1 && (currentTime - lastSentTime) > MIN_INTERVAL_MS) {
                sendRobotCommand("speed", speed);
                lastSpeed = speed;
                lastSentTime = currentTime;
            }
        });
    }

    private boolean handleSideControl(String side, MotionEvent event) {

        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            sendRobotCommand("side", side);
            return true;
        } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
            sendRobotCommand("side", "normal");
            return true;
        }
        return false;
    }

    private void sendRobotCommand(String key, Object value) {

        try {
            JSONObject json = new JSONObject();
            json.put(key, value);
            executePostRequest(json.toString());
        } catch (JSONException e) {
            Log.e("RobotAPI", "JSON Error", e);
        }
    }

    private void executePostRequest(String jsonBody) {

        RequestBody body = RequestBody.create(jsonBody, JSON_TYPE);
        Request request = new Request.Builder()
                .url(ROBOT_URL)
                .post(body)
                .header("Connection", "close")
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                updateStatusUI("Connection Error");
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        JSONObject jsonResp = new JSONObject(response.body().string());
                        String arduinoDir = jsonResp.optString("direction", "N/A");
                        updateStatusUI(arduinoDir);
                    } catch (Exception e) {
                        Log.e("RobotAPI", "Parse error");
                    }
                }
                response.close();
            }
        });
    }

    private void updateStatusUI(String text) {
        runOnUiThread(() -> txtDirectionStatus.setText(text.toUpperCase()));
    }
}