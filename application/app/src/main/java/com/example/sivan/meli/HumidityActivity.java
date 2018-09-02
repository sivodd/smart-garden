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
import java.util.List;
import java.util.Locale;

public class HumidityActivity extends AppCompatActivity {

    LineGraphSeries<DataPoint> series;
    Locale locale = new Locale.Builder().setLanguage("iw").setRegion("IL").build();
    SimpleDateFormat sdf = new SimpleDateFormat("kk:mm", locale);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_humidity);
        Toolbar myToolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(myToolbar);
        getSupportActionBar().setTitle("       Humidity");



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
                .withLimit(180);

        QueryResultPage<Sensor> queryResult = mapper.queryPage(Sensor.class, queryExpression);
        List<Sensor> result = queryResult.getResults();
//        DynamoDBScanExpression scanExpression = new DynamoDBScanExpression();
////        DynamoDBQueryExpression queryExpression = new DynamoDBQueryExpression().withScanIndexForward(false);
//
//
////        PaginatedQueryList<Sensor> result = mapper.query(Sensor.class, queryExpression);
//        PaginatedScanList<Sensor> result = mapper.scan(Sensor.class, scanExpression);

// graph
        long two_hours = (120 * 60 * 1000);
        int y;
        Date x, prev;
        int resultIndex = result.size() - 1;
        Sensor sensor_item;
        GraphView graph = (GraphView) findViewById(R.id.hum_graph);
        series = new LineGraphSeries<DataPoint>();
        Sensor first = result.get(resultIndex);

//        initiate prev
        Calendar cali = Calendar.getInstance(locale);
        cali.setTimeInMillis(Long.parseLong(first.getTimestamp()));
        cali.add(Calendar.HOUR_OF_DAY, 1);
        prev = cali.getTime();

//        Iterator<Sensor> iterator = result.iterator();
//        ArrayList<Sensor> sensorList = ArrayList<Sensor>(result.size());
//        ListIterator listIterator = result.listIterator(result.size());
        while (resultIndex >= 0){

            sensor_item = result.get(resultIndex);

//            sensor_item = iterator.next();
            Calendar cal = Calendar.getInstance(locale);
            cal.setTimeInMillis(Long.parseLong(sensor_item.getTimestamp()));
            Log.d("MyApp",sensor_item.getDevice() + cal.getTime() + cal);
            cal.add(Calendar.HOUR_OF_DAY, 1);
            //print the time and device sampled to log
            x = cal.getTime();
            if (x.getTime()-two_hours >= prev.getTime()) {
                y = (1024 - sensor_item.getHumidity()) * 100 / 1024;
                series.appendData(new DataPoint(x, y), true, 100);
                prev = x;
            }
            resultIndex--;

        }
        Sensor last = result.get(resultIndex+1);
        graph.addSeries(series);
        graph.getViewport().setMinY(0);
        graph.getViewport().setYAxisBoundsManual(true);
        Calendar fcal = Calendar.getInstance(locale);
        fcal.setTimeInMillis(Long.parseLong(first.getTimestamp()));
        fcal.add(Calendar.HOUR_OF_DAY, 1);
        Date fdate = fcal.getTime();
        graph.getViewport().setMinX(fdate.getTime());
        Calendar lcal = Calendar.getInstance(locale);
        lcal.setTimeInMillis(Long.parseLong(last.getTimestamp()));
        lcal.add(Calendar.HOUR_OF_DAY, 1);
        Date ldate = lcal.getTime();
        graph.getViewport().setMinX(fdate.getTime());
        graph.getViewport().setMaxX(ldate.getTime());
        graph.getViewport().setXAxisBoundsManual(true);
        graph.getGridLabelRenderer().setHumanRounding(false);
        // set date label formatter
        graph.getGridLabelRenderer().setLabelFormatter(new DefaultLabelFormatter(){
            @Override
            public String formatLabel(double value, boolean isValueX) {
                if (isValueX){
                    return sdf.format((new Date((long)value)));
                }
                else{
                    // custom label formatter to show Humidity "%"
                    return super.formatLabel(value, isValueX) + " %";
                }
            }
        });


    }
}