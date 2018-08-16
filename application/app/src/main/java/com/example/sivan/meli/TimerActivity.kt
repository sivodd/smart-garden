package com.example.sivan.meli

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import com.amazonaws.auth.CognitoCachingCredentialsProvider
import com.amazonaws.mobileconnectors.iot.*
import com.amazonaws.regions.Region
import com.amazonaws.regions.Regions
import com.amazonaws.services.iot.AWSIotClient
import com.amazonaws.services.iot.model.AttachPrincipalPolicyRequest
import com.amazonaws.services.iot.model.CreateKeysAndCertificateRequest
import com.amazonaws.services.iot.model.CreateKeysAndCertificateResult
import com.example.sivan.meli.util.NotificationUtil
import com.example.sivan.meli.util.PrefUtil
import kotlinx.android.synthetic.main.activity_timer.*
import kotlinx.android.synthetic.main.content_timer.*
import java.security.KeyStore
import java.util.*



class TimerActivity : AppCompatActivity() {

    // shadow pool init
    protected val LOG_TAG = TimerActivity::class.java.canonicalName

    // --- Constants to modify per your configuration ---

    // IoT endpoint
    // AWS Iot CLI describe-endpoint call returns: XXXXXXXXXX.iot.<region>.amazonaws.com
    private val CUSTOMER_SPECIFIC_ENDPOINT = "a2asku384vd80b.iot.us-east-1.amazonaws.com"
    // Cognito pool ID. For this app, pool needs to be unauthenticated pool with
    // AWS IoT permissions.
    private val COGNITO_POOL_ID = "us-east-1:1068ed1a-4660-425b-814f-c7b85d629bc3" //? not sure
    // Name of the AWS IoT policy to attach to a newly created certificate
    private val AWS_IOT_POLICY_NAME = "AndroidPubSub_policy" //i should check this

    // Region of AWS IoT
    private val MY_REGION = Regions.US_EAST_1
    // Filename of KeyStore file on the filesystem
    private val KEYSTORE_NAME = "iot_keystore"
    // Password for the private key in the KeyStore
    private val KEYSTORE_PASSWORD = "password"
    // Certificate and key aliases in the KeyStore
    private val CERTIFICATE_ID = "default"

    protected lateinit var shadow_credentialsProvider: CognitoCachingCredentialsProvider

    protected lateinit var clientId: String
    protected lateinit var mIotAndroidClient: AWSIotClient

    protected lateinit var keystorePath: String
    protected lateinit var keystoreName: String
    protected lateinit var keystorePassword: String

    internal var clientKeyStore: KeyStore? = null
    protected lateinit var certificateId: String


    companion object {
        fun setAlarm(context: Context, nowSeconds: Long, secondsRemaining: Long): Long {
            val wakeUpTime = (nowSeconds + secondsRemaining) * 1000
//          if i want to change timer to run for x seconds need to send data here..
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, TimerExpiredReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(context, 0, intent, 0)
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, wakeUpTime, pendingIntent)
            PrefUtil.setAlarmSetTime(nowSeconds, context)
            return wakeUpTime
        }

        fun removeAlarm(context: Context) {
            val intent = Intent(context, TimerExpiredReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(context, 0, intent, 0)
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.cancel(pendingIntent)
            PrefUtil.setAlarmSetTime(0, context)
        }

        val nowSeconds: Long
            get() = Calendar.getInstance().timeInMillis / 1000

        lateinit var mqttManager: AWSIotMqttManager

        lateinit var timer_state: String


    }

    enum class TimerState{
        Stopped, Paused, Running
    }

    private lateinit var timer: CountDownTimer
    private var timerLengthSeconds: Long = 0
    private var timerState = TimerState.Stopped

    private var secondsRemaining: Long = 0
    private var connected: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_timer)
        setSupportActionBar(toolbar)
        supportActionBar?.setIcon(R.drawable.ic_timer)
        supportActionBar?.title = "      Timer"

        fab_start.setOnClickListener{v ->
            if(!connected) {
                val text = "firs press connect"
                val duration = Toast.LENGTH_SHORT
                val toast = Toast.makeText(applicationContext, text, duration)
                toast.show()
            } else{
                startTimer()
                timerState =  TimerState.Running
                updateButtons()
                val topic = "\$aws/things/esp8266_296496/shadow/update"
                val msg = "{\"state\":{\"desired\":{\"on\":true}}}" // ADD THE TIME HERE

                try {
                    mqttManager.publishString(msg, topic, AWSIotMqttQos.QOS0)
                } catch (e: Exception) {
                    Log.e(LOG_TAG, "Publish error.", e)
                }
            }

        }

        fab_pause.setOnClickListener { v ->
            if(!connected) {
                val text = "firs press connect"
                val duration = Toast.LENGTH_SHORT
                val toast = Toast.makeText(applicationContext, text, duration)
                toast.show()
            }else{
                timer.cancel()
                timerState = TimerState.Paused
                updateButtons()

                val topic = "\$aws/things/esp8266_296496/shadow/update"
                val msg = "{\"state\":{\"desired\":{\"on\":false}}}" // ADD THE TIME HERE - needs to pause

                try {
                    mqttManager.publishString(msg, topic, AWSIotMqttQos.QOS0)
                } catch (e: Exception) {
                    Log.e(LOG_TAG, "Publish error.", e)
                }
            }
        }

        fab_stop.setOnClickListener { v ->
            if(!connected) {
                val text = "firs press connect"
                val duration = Toast.LENGTH_SHORT
                val toast = Toast.makeText(applicationContext, text, duration)
                toast.show()
            }
            else{
                timer.cancel()
                onTimerFinished()
            }
        }

        // shadow init
        // MQTT client IDs are required to be unique per AWS IoT account.
        // This UUID is "practically unique" but does not _guarantee_
        // uniqueness.
        clientId = UUID.randomUUID().toString()

        // Initialize the AWS Cognito credentials provider
        shadow_credentialsProvider = CognitoCachingCredentialsProvider(
                applicationContext, // context
                COGNITO_POOL_ID, // Identity Pool ID
                MY_REGION // Region
        )
        val region = Region.getRegion(MY_REGION)

        // MQTT Client
        mqttManager = AWSIotMqttManager(clientId, CUSTOMER_SPECIFIC_ENDPOINT)

        // Set keepalive to 10 seconds.  Will recognize disconnects more quickly but will also send
        // MQTT pings every 10 seconds.
        mqttManager.keepAlive = 10

        // Set Last Will and Testament for MQTT.  On an unclean disconnect (loss of connection)
        // AWS IoT will publish this message to alert other clients.
        val lwt = AWSIotMqttLastWillAndTestament("my/lwt/topic",
                "Android client lost connection", AWSIotMqttQos.QOS0)
        mqttManager.mqttLastWillAndTestament = lwt

        // IoT Client (for creation of certificate if needed)
        mIotAndroidClient = AWSIotClient(shadow_credentialsProvider)
        mIotAndroidClient.setRegion(region)

        keystorePath = filesDir.path
        keystoreName = KEYSTORE_NAME
        keystorePassword = KEYSTORE_PASSWORD
        certificateId = CERTIFICATE_ID


        simpleSwitch.setOnCheckedChangeListener{_,isChecked ->
            if(isChecked){
                PrefUtil.setConnectionState(true, this)
                Log.d(LOG_TAG, "clientId = " + clientId)

                try {
                    mqttManager.connect(clientKeyStore) { status, throwable ->
                        Log.d(LOG_TAG, "Status = " + status.toString())

                        runOnUiThread {
                            if (status == AWSIotMqttClientStatusCallback.AWSIotMqttClientStatus.Connecting) {
                                val text = "Connecting..."
                                val duration = Toast.LENGTH_SHORT
                                val toast = Toast.makeText(applicationContext, text, duration)
                                toast.show()

                            } else if (status == AWSIotMqttClientStatusCallback.AWSIotMqttClientStatus.Connected) {
                                val text = "Connected"
                                val duration = Toast.LENGTH_SHORT
                                val toast = Toast.makeText(applicationContext, text, duration)
                                toast.show()
                                connected = true

                            } else if (status == AWSIotMqttClientStatusCallback.AWSIotMqttClientStatus.Reconnecting) {
                                if (throwable != null) {
                                    Log.e(LOG_TAG, "Connection error.", throwable)
                                }
                                val text = "Reconnecting"
                                val duration = Toast.LENGTH_SHORT
                                val toast = Toast.makeText(applicationContext, text, duration)
                                toast.show()
                            } else if (status == AWSIotMqttClientStatusCallback.AWSIotMqttClientStatus.ConnectionLost) {
                                if (throwable != null) {
                                    Log.e(LOG_TAG, "Connection error.", throwable)
                                }
                                val text = "Disconnected"
                                val duration = Toast.LENGTH_SHORT
                                val toast = Toast.makeText(applicationContext, text, duration)
                                toast.show()
                                connected = false
                            } else {
                                val text = "Disconnected"
                                val duration = Toast.LENGTH_SHORT
                                val toast = Toast.makeText(applicationContext, text, duration)
                                toast.show()
                                connected = false

                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(LOG_TAG, "Connection error.", e)
                    val text = "Error! " + e.message
                    val duration = Toast.LENGTH_SHORT
                    val toast = Toast.makeText(applicationContext, text, duration)
                    toast.show()
                }

            }else{
                try {
                    mqttManager.disconnect()
                } catch (e: Exception) {
                    Log.e(LOG_TAG, "Disconnect error.", e)
                }
            }
        }
        // To load cert/key from keystore on filesystem
        try {
            if (AWSIotKeystoreHelper.isKeystorePresent(keystorePath, keystoreName)!!) {
                if (AWSIotKeystoreHelper.keystoreContainsAlias(certificateId, keystorePath,
                        keystoreName, keystorePassword)!!) {
                    Log.i(LOG_TAG, "Certificate " + certificateId
                            + " found in keystore - using for MQTT.")
                    // load keystore from file into memory to pass on connection
                    clientKeyStore = AWSIotKeystoreHelper.getIotKeystore(certificateId,
                            keystorePath, keystoreName, keystorePassword)
                    fab_start.isEnabled = true
                } else {
                    Log.i(LOG_TAG, "Key/cert $certificateId not found in keystore.")
                }
            } else {
                Log.i(LOG_TAG, "Keystore $keystorePath/$keystoreName not found.")
            }
        } catch (e: Exception) {
            Log.e(LOG_TAG, "An error occurred retrieving cert/key from keystore.", e)
        }


        if (clientKeyStore == null) {
            Log.i(LOG_TAG, "Cert/key was not found in keystore - creating new key and certificate.")

            Thread(Runnable {
                try {
                    // Create a new private key and certificate. This call
                    // creates both on the server and returns them to the
                    // device.
                    val createKeysAndCertificateRequest = CreateKeysAndCertificateRequest()
                    createKeysAndCertificateRequest.setAsActive = true
                    val createKeysAndCertificateResult: CreateKeysAndCertificateResult
                    createKeysAndCertificateResult = mIotAndroidClient.createKeysAndCertificate(createKeysAndCertificateRequest)
                    Log.i(LOG_TAG,
                            "Cert ID: " +
                                    createKeysAndCertificateResult.certificateId +
                                    " created.")

                    // store in keystore for use in MQTT client
                    // saved as alias "default" so a new certificate isn't
                    // generated each run of this application
                    AWSIotKeystoreHelper.saveCertificateAndPrivateKey(certificateId,
                            createKeysAndCertificateResult.certificatePem,
                            createKeysAndCertificateResult.keyPair.privateKey,
                            keystorePath, keystoreName, keystorePassword)

                    // load keystore from file into memory to pass on
                    // connection
                    clientKeyStore = AWSIotKeystoreHelper.getIotKeystore(certificateId,
                            keystorePath, keystoreName, keystorePassword)

                    // Attach a policy to the newly created certificate.
                    // This flow assumes the policy was already created in
                    // AWS IoT and we are now just attaching it to the
                    // certificate.
                    val policyAttachRequest = AttachPrincipalPolicyRequest()
                    policyAttachRequest.policyName = AWS_IOT_POLICY_NAME
                    policyAttachRequest.principal = createKeysAndCertificateResult
                            .certificateArn
                    mIotAndroidClient.attachPrincipalPolicy(policyAttachRequest)

                    runOnUiThread { fab_start.isEnabled = true }
                } catch (e: Exception) {
                    Log.e(LOG_TAG,
                            "Exception occurred when generating new private key and certificate.",
                            e)
                }
            }).start()
//            end of pubsub init
        }


    }

    override fun onResume() {
        super.onResume()

        initTimer()

        removeAlarm(this)
        NotificationUtil.hideTimerNotification(this)
    }

    override fun onPause() { // called when timer goes into background.
        super.onPause()

        if (timerState == TimerState.Running){
            timer.cancel()
            val wakeUpTime = setAlarm(this, nowSeconds, secondsRemaining)
            NotificationUtil.showTimerRunning(this, wakeUpTime)
        }
        else if (timerState == TimerState.Paused){
            NotificationUtil.showTimerPaused(this)
        }

        PrefUtil.setPreviousTimerLengthSeconds(timerLengthSeconds, this)
        PrefUtil.setSecondsRemaining(secondsRemaining, this)
        PrefUtil.setTimerState(timerState, this)
        PrefUtil.setConnectionState(connected, this)
    }

    private fun initTimer(){
        timerState = PrefUtil.getTimerState(this)

        //we don't want to change the length of the timer which is already running
        //if the length was changed in settings while it was backgrounded
        if (timerState == TimerState.Stopped)
            setNewTimerLength()
        else
            setPreviousTimerLength()

        secondsRemaining = if (timerState == TimerState.Running || timerState == TimerState.Paused)
            PrefUtil.getSecondsRemaining(this)
        else
            timerLengthSeconds

        val alarmSetTime = PrefUtil.getAlarmSetTime(this)
        if (alarmSetTime > 0)
            secondsRemaining -= nowSeconds - alarmSetTime

        if (secondsRemaining <= 0)
            onTimerFinished()
        else if (timerState == TimerState.Running)
            startTimer()

        updateButtons()
        updateCountdownUI()
        connected = PrefUtil.getConnectionState(this)
        simpleSwitch.isChecked = connected

    }

    private fun onTimerFinished(){
        timerState = TimerState.Stopped

        //set the length of the timer to be the one set in SettingsActivity
        //if the length was changed when the timer was running
        setNewTimerLength()

        progress_countdown.progress = 0

        PrefUtil.setSecondsRemaining(timerLengthSeconds, this)
        secondsRemaining = timerLengthSeconds

        updateButtons()
        updateCountdownUI()
        val topic = "\$aws/things/esp8266_296496/shadow/update"
        val msg = "{\"state\":{\"desired\":{\"on\":false}}}" // set the timer to 0

        try {
            mqttManager.publishString(msg, topic, AWSIotMqttQos.QOS0)
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Publish error.", e)
        }
    }

    private fun startTimer(){
        timerState = TimerState.Running

        timer = object : CountDownTimer(secondsRemaining * 1000, 1000) {
            override fun onFinish() {
                onTimerFinished()
            }


            override fun onTick(millisUntilFinished: Long) {
                secondsRemaining = millisUntilFinished / 1000
                updateCountdownUI()
            }

        }.start()
    }

    private fun setNewTimerLength(){
        val lengthInMinutes = PrefUtil.getTimerLength(this)
        timerLengthSeconds = (lengthInMinutes * 60L)
        progress_countdown.max = timerLengthSeconds.toInt()
    }

    private fun setPreviousTimerLength(){
        timerLengthSeconds = PrefUtil.getPreviousTimerLengthSeconds(this)
        progress_countdown.max = timerLengthSeconds.toInt()
    }

    private fun updateCountdownUI(){
        val minutesUntilFinished = secondsRemaining / 60
        val secondsInMinuteUntilFinished = secondsRemaining - minutesUntilFinished * 60
        val secondsStr = secondsInMinuteUntilFinished.toString()
        textView_countdown.text = "$minutesUntilFinished:${if (secondsStr.length == 2) secondsStr else "0" + secondsStr}"
        progress_countdown.progress = (timerLengthSeconds - secondsRemaining).toInt()
    }

    private fun updateButtons(){
        when (timerState) {
            TimerState.Running ->{
                fab_start.isEnabled = false
                fab_pause.isEnabled = true
                fab_stop.isEnabled = true
                timer_state = "Running"
            }
            TimerState.Stopped -> {
                fab_start.isEnabled = true
                fab_pause.isEnabled = false
                fab_stop.isEnabled = false
                timer_state = "Off"
            }
            TimerState.Paused -> {
                fab_start.isEnabled = true
                fab_pause.isEnabled = false
                fab_stop.isEnabled = true
                timer_state = "Paused"
            }
        }
    }



    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_timer, menu)
        return true
    }


    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_settings -> {
                val intent = Intent(this, SettingsActivity::class.java)
                startActivity(intent)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onBackPressed(){
        val intent = Intent()
        intent.putExtra("timer_state", timer_state)
        setResult(RESULT_OK, intent)
        finish()
    }

}