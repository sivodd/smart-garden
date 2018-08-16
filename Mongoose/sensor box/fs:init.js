// Load Mongoose OS API
load('api_gpio.js');
load('api_mqtt.js');
load('api_sys.js');
load('api_config.js');
load('api_dht.js');
load('api_timer.js');
load('api_adc.js');
load('api_i2c.js');

let pin = 0;   // GPIO 0 is typically a 'Flash' button
// GPIO pin which has a DHT sensor data wire connected
let dht_pin = 4;
// Initialize DHT library
let dht = DHT.create(dht_pin, DHT.DHT11);
let topic = 'mos/topic1';
let light_pin = 14;
GPIO.set_mode(light_pin, GPIO.MODE_INPUT);

Timer.set(600000 /* 10 min */, Timer.REPEAT, function() {
let message = JSON.stringify({
        "device" : "garden_station",
        "timestamp": Timer.now(),
        "temp": dht.getTemp(),
        "humidity": ADC.read(0),
        "light" : GPIO.read(light_pin),
    });

    let ok = MQTT.pub(topic, message, 1);
    print('Published:', ok ? 'yes' : 'no', 'topic:', topic, 'message:', message);
}, null);

GPIO.set_button_handler(pin, GPIO.PULL_UP, GPIO.INT_EDGE_NEG, 50, function(x) {
    let message = JSON.stringify({
        "device" : "garden_station",
        "timestamp": Timer.now(),
        "temp": dht.getTemp(),
        "humidity": ADC.read(0),
        "light" : GPIO.read(light_pin),
    });

    let ok = MQTT.pub(topic, message, 1);
    print('Published:', ok ? 'yes' : 'no', 'topic:', topic, 'message:', message);
}, true);

// MQTT.sub(topic, function(conn, topic, msg) {
//     print('Topic:', topic, 'message:', msg);
//     let obj = JSON.parse(msg);
//     let mode = obj.mode;
//     let timer = obj.timer;
//     if(timer === 'on'){
//         print('motor on');
//         //motor(timer);
//     }
// }, null);

print('Flash button is configured on GPIO pin ', pin);
print('Press the flash button now!');
