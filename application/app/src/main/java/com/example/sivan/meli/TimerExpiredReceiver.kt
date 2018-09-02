package com.example.sivan.meli

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.amazonaws.mobileconnectors.iot.AWSIotMqttQos
import com.example.sivan.meli.util.NotificationUtil
import com.example.sivan.meli.util.PrefUtil

class TimerExpiredReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        NotificationUtil.showTimerExpired(context)

        PrefUtil.setTimerState(TimerActivity.TimerState.Stopped, context)
        PrefUtil.setAlarmSetTime(0, context)

        val topic = "\$aws/things/esp8266_296496/shadow/update"
        val msg = "{\"state\":{\"desired\":{\"on\":false}}}"
        TimerActivity.mqttManager.publishString(msg, topic, AWSIotMqttQos.QOS0)
        TimerActivity.timer_state = "Off"

    }
}