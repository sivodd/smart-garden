package com.example.sivan.meli;

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
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;


public class TempActivity extends AppCompatActivity {

    LineGraphSeries<DataPoint> series;
    Locale locale = new Locale.Builder().setLanguage("iw").setRegion("IL").build();
    SimpleDateFormat sdf = new SimpleDateFormat("kk:mm", locale);


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_temp);
        Toolbar myToolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(myToolbar);
        getSupportActionBar().setTitle("       Temperature");



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
        GraphView graph = (GraphView) findViewById(R.id.graph);
        series = new LineGraphSeries<DataPoint>();
        Iterator iterator = result.iterator();
        int resultIndex = result.size() - 1;
        Sensor first = result.get(resultIndex);
        while (resultIndex >= 0){

            sensor_item = result.get(resultIndex);
            Calendar cal = Calendar.getInstance(locale);
            cal.setTimeInMillis(Long.parseLong(sensor_item.getTimestamp()));
            Log.d("MyApp",sensor_item.getDevice() + cal.getTime());
            //print the time and device sampled to log
            x = cal.getTime();
            y= Integer.parseInt(sensor_item.getTemp());
            if (y != -1) {
                series.appendData(new DataPoint(x, y), true, 100);
            }
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
        graph.getViewport().setMinY(0);
        graph.getViewport().setYAxisBoundsManual(true);

        // set date label formatter
        graph.getGridLabelRenderer().setLabelFormatter(new DefaultLabelFormatter(){
            @Override
            public String formatLabel(double value, boolean isValueX) {
                if (isValueX){
                    return sdf.format((new Date((long)value)));
                }
                else{
                // custom label formatter to show TEMP "O"
                return super.formatLabel(value, isValueX) + " \u00b0";
                }
            }
        });
    }
}
