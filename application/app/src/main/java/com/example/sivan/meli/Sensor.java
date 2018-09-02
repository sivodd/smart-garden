package com.example.sivan.meli;

import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBAttribute;
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBHashKey;
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBRangeKey;
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBTable;


@DynamoDBTable(tableName = "gardenTable")
public class Sensor {
    private String device;
    private String timestamp;
    private String temp;
    private int humidity;
    private int light;

    @DynamoDBRangeKey(attributeName = "timestamp") // changed DynamoDBIndexRange.
    public String getTimestamp() {
        return timestamp;
    }

    @DynamoDBHashKey(attributeName = "device_name") // changed DynamoDBIndexHashKey.
    public String getDevice() {
        return device;
    }

    @DynamoDBAttribute(attributeName = "temp")
    public String getTemp() {
        if (temp.equals("9223372036854776000"))
            temp="-1";
        return temp;
    }

    @DynamoDBAttribute(attributeName = "humidity")
    public int getHumidity() {
        return humidity;
    }

    @DynamoDBAttribute(attributeName = "light")
    public int getLight() {
        return light;
    }

    public void setDevice(String device) {
        this.device = device;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }
    public void setTemp(String temp) {
        this.temp = temp;
    }

    public void setHumidity(int humidity) {
        this.humidity = humidity;
    }

    public void setLight(int light) {
        this.light = light;
    }

}