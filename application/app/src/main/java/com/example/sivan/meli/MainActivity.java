package com.example.sivan.meli;

import android.content.Intent;
import android.os.StrictMode;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.CardView;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.amazonaws.auth.CognitoCachingCredentialsProvider;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.*;
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.*;

import java.util.List;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private CardView tempCard, humCard, timerCard, lightCard;
    private TextView temp, hum, timer, light;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        // added this if
        if (android.os.Build.VERSION.SDK_INT > 9) {
            StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
            StrictMode.setThreadPolicy(policy);
        }

        tempCard = (CardView) findViewById(R.id.temp_card);
        humCard = (CardView) findViewById(R.id.hum_card);
        lightCard = (CardView) findViewById(R.id.light_card);
        timerCard = (CardView) findViewById(R.id.timer_card);
        temp = (TextView) findViewById(R.id.textView1);
        hum = (TextView) findViewById(R.id.textView3);
        timer = (TextView) findViewById(R.id.textView7);
        light = (TextView) findViewById(R.id.textView5);

        Sensor Sensor;
        String temp_string;
        String hum_string;
        String light_string;

        CognitoCachingCredentialsProvider credentialsProvider = new CognitoCachingCredentialsProvider(
                getApplicationContext(),
                "us-east-1:732b9a49-4529-4b84-9a6a-20a12fd838d8", // Identity pool ID
                Regions.US_EAST_1 // Region
        );

        AmazonDynamoDBClient ddbClient = new AmazonDynamoDBClient(credentialsProvider);
        DynamoDBMapper mapper = new DynamoDBMapper(ddbClient);

        String device = "garden_station";
        Sensor sensorKey = new Sensor();
        sensorKey.setDevice(device);

        // query the last 10 results of 'garden_station'
        int limit = 10;
        DynamoDBQueryExpression<Sensor> queryExpression = new DynamoDBQueryExpression<Sensor>()
                .withHashKeyValues(sensorKey)
                .withScanIndexForward(false)
                .withLimit(limit);

        QueryResultPage<Sensor> queryResult = mapper.queryPage(Sensor.class, queryExpression);
        List<Sensor> result = queryResult.getResults();

        int resIndex = 0;
        Sensor = result.get(resIndex);
        temp_string = Sensor.getTemp();
        while (temp_string.equals("-1") && resIndex < limit){
            resIndex++;
            Sensor = result.get(resIndex);
            temp_string = Sensor.getTemp();
        }
        if (resIndex == limit)
            temp_string = "error";
        hum_string = Integer.toString(Sensor.getHumidity()*100/1024);
        if (Sensor.getLight()==0)
            light_string = "Sunny";
        else light_string = "Dark";


        temp_string = temp_string + "\u00b0";
        hum_string = hum_string + "%";

        temp.setText(temp_string);
        hum.setText(hum_string);
        light.setText(light_string);
        timer.setText("Off");


        tempCard.setOnClickListener(this);
        humCard.setOnClickListener(this);
        lightCard.setOnClickListener(this);
        timerCard.setOnClickListener(this);


    }

    @Override
    public void onClick(View v) {
        Intent i;

        switch (v.getId()) {
            case R.id.temp_card:
                i = new Intent(this, TempActivity.class);
                startActivity(i);
                break;
            case R.id.hum_card:
                i = new Intent(this, HumidityActivity.class);
                startActivity(i);
                break;
            case R.id.light_card:
                i = new Intent(this, LightActivity.class);
                startActivity(i);
                break;
            case R.id.timer_card:
                i = new Intent(this, TimerActivity.class);
                startActivityForResult(i, 999);
                break;
            default:
                break;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 999 && resultCode == RESULT_OK){
            String timer_string = data.getStringExtra("timer_state");
            timer.setText(data.getStringExtra("timer_state"));
            Log.d("MyApp",timer_string);
        }
    }
}
