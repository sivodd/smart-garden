package com.example.sivan.meli

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.amazonaws.mobileconnectors.iot.AWSIotMqttQos
import com.example.sivan.meli.util.NotificationUtil
import com.example.sivan.meli.util.PrefUtil

class TimerNotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action){
            AppConstants.ACTION_STOP -> {
                TimerActivity.removeAlarm(context)
                PrefUtil.setTimerState(TimerActivity.TimerState.Stopped, context)
                NotificationUtil.hideTimerNotification(context)
                val topic = "\$aws/things/esp8266_296496/shadow/update"
                val msg = "{\"state\":{\"desired\":{\"on\":false}}}"
                TimerActivity.mqttManager.publishString(msg, topic, AWSIotMqttQos.QOS0)

            }
            AppConstants.ACTION_PAUSE -> {
                var secondsRemaining = PrefUtil.getSecondsRemaining(context)
                val alarmSetTime = PrefUtil.getAlarmSetTime(context)
                val nowSeconds = TimerActivity.nowSeconds
                val topic = "\$aws/things/esp8266_296496/shadow/update"
                val msg = "{\"state\":{\"desired\":{\"on\":false}}}"

                TimerActivity.mqttManager.publishString(msg, topic, AWSIotMqttQos.QOS0)
                TimerActivity.timer_state = "Paused"

                secondsRemaining -= nowSeconds - alarmSetTime
                PrefUtil.setSecondsRemaining(secondsRemaining, context)

                TimerActivity.removeAlarm(context)
                PrefUtil.setTimerState(TimerActivity.TimerState.Paused, context)
                NotificationUtil.showTimerPaused(context)
            }
            AppConstants.ACTION_RESUME -> {
                val secondsRemaining = PrefUtil.getSecondsRemaining(context)
                val wakeUpTime = TimerActivity.setAlarm(context, TimerActivity.nowSeconds, secondsRemaining)
                PrefUtil.setTimerState(TimerActivity.TimerState.Running, context)
                NotificationUtil.showTimerRunning(context, wakeUpTime)
                val topic = "\$aws/things/esp8266_296496/shadow/update"
                val msg = "{\"state\":{\"desired\":{\"on\":true}}}"
                TimerActivity.mqttManager.publishString(msg, topic, AWSIotMqttQos.QOS0)
                TimerActivity.timer_state = "Running"
            }

            AppConstants.ACTION_START -> {
                val minutesRemaining = PrefUtil.getTimerLength(context)
                val secondsRemaining = minutesRemaining * 60L
                val wakeUpTime = TimerActivity.setAlarm(context, TimerActivity.nowSeconds, secondsRemaining)
                PrefUtil.setTimerState(TimerActivity.TimerState.Running, context)
                PrefUtil.setSecondsRemaining(secondsRemaining, context)
                NotificationUtil.showTimerRunning(context, wakeUpTime)
                val topic = "\$aws/things/esp8266_296496/shadow/update"
                val msg = "{\"state\":{\"desired\":{\"on\":true}}}"
                TimerActivity.mqttManager.publishString(msg, topic, AWSIotMqttQos.QOS0)
                TimerActivity.timer_state = "Running"
            }
        }
    }
}