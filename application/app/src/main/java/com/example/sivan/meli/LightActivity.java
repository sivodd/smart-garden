package com.example.sivan.meli;

import android.graphics.Color;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.util.Log;

import com.amazonaws.auth.CognitoCachingCredentialsProvider;
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBMapper;
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBQueryExpression;
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.QueryResultPage;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;

import com.jjoe64.graphview.DefaultLabelFormatter;
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.ValueDependentColor;
import com.jjoe64.graphview.series.BarGraphSeries;
import com.jjoe64.graphview.series.DataPoint;


import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;


public class LightActivity extends AppCompatActivity {

    BarGraphSeries<DataPoint> series;
    Locale locale = new Locale.Builder().setLanguage("iw").setRegion("IL").build();
    SimpleDateFormat sdf = new SimpleDateFormat("kk:mm", locale);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_light);
        Toolbar myToolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(myToolbar);
        getSupportActionBar().setTitle("       Light");



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
//        query the last 10 results of 'garden_station'
        DynamoDBQueryExpression<Sensor> queryExpression = new DynamoDBQueryExpression<Sensor>()
                .withHashKeyValues(sensorKey)
                .withScanIndexForward(false)
                .withLimit(10);

        QueryResultPage<Sensor> queryResult = mapper.queryPage(Sensor.class, queryExpression);
        List<Sensor> result = queryResult.getResults();

// graph
        int y;
        Date x;

        Sensor sensor_item;
        GraphView graph = (GraphView)findViewById(R.id.graph_light);
        series = new BarGraphSeries<>();
        int resultIndex = result.size() - 1;
        Log.d("RESULT SIZE", Integer.toString((result.size())));
        Sensor first = result.get(resultIndex);
        Calendar cal = Calendar.getInstance(locale);

        while (resultIndex >= 0){

            sensor_item = result.get(resultIndex);
            Log.d("senstor index", Integer.toString(resultIndex));
            Log.d("light", Integer.toString(sensor_item.getLight()));

            cal.setTimeInMillis(Long.parseLong(sensor_item.getTimestamp()));
            x = cal.getTime();
            y= (int) ((sensor_item.getLight()-0.5)*-2);
            Log.d("X", x.toString());
            Log.d("Y", Integer.toString((y)));

            series.appendData(new DataPoint(x, y), true, 100);
            resultIndex--;
        }
        Sensor last = result.get(resultIndex+1);
        graph.addSeries(series);
        graph.getViewport().setMinY(0);
        graph.getViewport().setYAxisBoundsManual(true);
        Calendar fcal = Calendar.getInstance(locale);
        fcal.setTimeInMillis(Long.parseLong(first.getTimestamp()));
        Date fdate = fcal.getTime();
        graph.getViewport().setMinX(fdate.getTime());
        Calendar lcal = Calendar.getInstance(locale);
        lcal.setTimeInMillis(Long.parseLong(last.getTimestamp()));
        Date ldate = lcal.getTime();
        graph.getViewport().setMinX(fdate.getTime());
        graph.getViewport().setMaxX(ldate.getTime());
        graph.getViewport().setXAxisBoundsManual(true);
        graph.getGridLabelRenderer().setHumanRounding(false);


        graph.addSeries(series);

        graph.getViewport().setMinY(-1);
        graph.getViewport().setMaxY(1);
        graph.getViewport().setYAxisBoundsManual(true);
        series.setValueDependentColor(new ValueDependentColor<DataPoint>() {
            @Override
            public int get(DataPoint data) {
                if (data.getY()==1)
                    return  Color.rgb(45,184,130);
                else return Color.rgb(9,36,26);
            }
        });

        series.setSpacing(50);

// draw values on top
//        series.setDrawValuesOnTop(true);
//        series.setValuesOnTopColor(Color.RED);
//series.setValuesOnTopSize(50);

        graph.getGridLabelRenderer().setLabelFormatter(new DefaultLabelFormatter(){

            @Override
            public String formatLabel(double value, boolean isValueX) {
                if (isValueX){
                    return sdf.format((new Date((long)value)));
                }
                else{
                    // custom label formatter to show light dark/sunny
                    if (value ==-1)
                        return "Dark";
                    else if (value == 1)
                        return "Sunny";

                    return "";
                }
            }
        });

    }
}
