//mongoose app
load('api_events.js');
load('api_net.js');
load('api_sys.js');
load('api_gpio.js');
load('api_config.js');
load('api_timer.js');
load('api_arduino_onewire.js');
load('api_esp8266.js');
load('api_pwm.js');

// Pin number assignment for TB6612FNG
let PWMA_PIN = 16; //D0
let AIN2_PIN = 14; //D5
let AIN1_PIN = 4; //D2
let STBY_PIN = 5; //D1

//ref from https://github.com/stylixboom/lr_motor/blob/master/app_l.js
GPIO.set_mode(PWMA_PIN, GPIO.MODE_OUTPUT);
GPIO.set_mode(AIN2_PIN, GPIO.MODE_OUTPUT);
GPIO.set_mode(AIN1_PIN, GPIO.MODE_OUTPUT);
GPIO.set_mode(STBY_PIN, GPIO.MODE_OUTPUT);

function pin_init() {
    GPIO.write(STBY_PIN, 0);
    GPIO.write(AIN1_PIN, 0);
    GPIO.write(AIN2_PIN, 0);
    GPIO.write(PWMA_PIN, 0);
    print("init");
    Sys.usleep(3000000);
}

function forword() {
    // ==== A ====
    GPIO.write(STBY_PIN, 1);
    GPIO.write(PWMA_PIN, 1);
    GPIO.write(AIN1_PIN, 0);
    GPIO.write(AIN2_PIN, 1);
    print("forword");
    Sys.usleep(3000000);
    brake();
}

function brake() { //LIBRARY SUORCE
    // ==== A ====
    GPIO.write(STBY_PIN, 1);
    GPIO.write(PWMA_PIN, 0);
    GPIO.write(AIN1_PIN, 1);
    GPIO.write(AIN2_PIN, 1); // OR 0
    print("brake");
    Sys.usleep(3000000);
}

function backword() {
    // ==== A ====
    GPIO.write(STBY_PIN, 1);
    GPIO.write(PWMA_PIN, 1);
    GPIO.write(AIN1_PIN, 1);
    GPIO.write(AIN2_PIN, 0);
    print("backword");
    Sys.usleep(3000000);
    brake();
}

load('api_config.js');
load('api_gpio.js');
load('api_shadow.js');

let led = Cfg.get('pins.led');  // Built-in LED GPIO number
let state = {on: false};        // Device state - LED on/off status

// Set up Shadow handler to synchronise device state with the shadow state
Shadow.addHandler(function(event, obj) {
  if (event === 'CONNECTED') {
    // Connected to shadow - report our current state.
    Shadow.update(0, state);
  } else if (event === 'UPDATE_DELTA') {
    // Got delta. Iterate over the delta keys, handle those we know about.
    print('Got delta:', JSON.stringify(obj));
    for (let key in obj) {
      if (key === 'on') {
        // Shadow wants us to change local state - do it.
        state.on = obj.on;
        GPIO.set_mode(led, GPIO.MODE_OUTPUT);
        GPIO.write(led, state.on ? 0 : 1);
        state.on ? forword() : backword();
        print('LED on ->', state.on);
      }
    }
    // Once we've done synchronising with the shadow, report our state.
    Shadow.update(0, state);
  }
});