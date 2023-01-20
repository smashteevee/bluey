# bluey
Apple Watch BLE to MQTT Android app

Bluey is an Android app that detects nearby Apple Watches and Bluetooth Low Energy (BLE) iBeacons. It "publishes" info on detected devices via [MQTT](https://mqtt.org/) to integrate with Home Automation software like [Home Assistant](https://github.com/home-assistant) and [Mosquitto](https://mosquitto.org/). 

The appeal of presence detection with Apple Watches is that they are popular, usually worn around the house and emit BLE advertising packets one can passively detect. The issue is they're not easy to detect (more below).

## :warning: ATTENTION/DISCLAIMER
I built this project for educational purposes and personal use in my home. Please do not be sketchy nor use it for scanning/tracking others. Please be respectful of privacy norms and laws in your area.

## :pray: Special thanks
I was inspired by the [ESPHome Apple Watch detection](https://github.com/dalehumby/ESPHome-Apple-Watch-detection) README from [dalehumby](https://github.com/dalehumby) which hints at how to detect known Apple Watches, and the [Blessed](https://github.com/weliem/blessed-android) library which BLE programming accessible to a noob like me.

## Goals
 * :watch: Home presence detection with Apple Watches - no need for dedicated iBeacons!
 * :ok_hand: Simplicity - Just needs to work for my personal setup. Hacky code and minimal UX are acceptable
 * :metal: Tap into native code - I had scripted a [Tasker solution initially](https://www.nyctinker.com/post/ble-presence-detection-for-apple-watch-using-tasker) but was frustrated by its limitations
 * :recycle: Upcycling - Give old Android phones some purpose

 
## 💡 The Idea
 * Apple devices [randomize MAC addresses](https://support.apple.com/guide/security/bluetooth-security-sec82597d97e/web) every ~40 minutes for privacy purposes, so you can't use common BLE detection solutions that scan for a static MAC address
 * However, you can scan for Apple devices (including Apple Watches) emitting (https://github.com/furiousMAC/continuity/blob/master/messages/nearby_action.md)[Nearby Info messages] in their BLE advertising packets.
 * Then, if you connect to the device, you can read its "characteristics" to identify the specific Apple Watch model and infer it's yours (This is also a limitation: If you live in a household or have neighbors with the same model, you may get false positives)
 * Once you've "seen" the BLE device, you "cache" its MAC address and look for it in subsequent scans. Whenever it's detected, you mark it as Nearby.  If not, you repeat the previous steps

 
## Key Features
 * Runs on Android 6 and up (API 23+)
 * Detect nearby Apple Watches by model
 * Detect nearby iBeacons by static MAC address
 * Adjustable settings for BLE scan period and cool-off
 * MQTT TCP server support
 * Low battery usage
 * Publishes MQTT events with customizable labels
 * Supports multiple instances running (eg, an Android placed in different rooms!)

 ## How to install
  * Download this project and build the APK in Android Studio, or
  * Install the latest unsigned APK (under [Releases](https://github.com/smashteevee/bluey/releases))
  
 ## How to use
 ### Basic setup in Settings
 <img src="https://user-images.githubusercontent.com/59382083/210033590-4cbd5c24-2c85-4af3-8f46-03933c79d533.png" width="240"/>
 
  1. In Settings, enter your MQTT server address (eg, tcp://192.168.86.101:1883), username and password
  2. Set a Device name to distinguish this Android phone instance from any others you might also run (eg, Kitchen)

 ### Specify Apple Watch and devices to detect in your list
  <img src="https://user-images.githubusercontent.com/59382083/213614225-2a450b91-b8d4-4fc0-bccf-589ae6f42c31.png" width="240"/>
  
  1. In main screen dropdown, select the Apple Watch model you'd like to detect by its "machine code" (see this [list](https://gist.github.com/adamawolf/3048717) to find yours), then click "Add iOS" to add it to your list
  2. (Optional) Enter in static MAC address(es) of iBeacons that you'd also like to detect and click "Add"
 
  <img src="https://user-images.githubusercontent.com/59382083/213615883-69e21001-6afa-49e8-93b7-e692df57834c.png" width="240"/>

  3. Note: Tap-and-hold any list items to delete them
  
 ### You're done - now what?
  * The app will automatically scan for the devices in your list periodically (then cool off), based on your settings. If it detects your designated Apple Watch or iBeacon nearby, it will publish an MQTT event to your configured server
  * Confirm MQTT messages are being sent by using your favorite MQTT monitoring tool, eg I use the MQTT tool in Home Assistant:
  
 <img src="https://user-images.githubusercontent.com/59382083/213615417-5e1d7347-29eb-44d6-8acd-65cbe7e280bc.png" width="240"/>
   
  * Create a MQTT platform sensor in Home Assistant or Node-red automation to trigger some magic based on the presence!
 
 ## MQTT Message format details
 The MQTT message that is published follows this format:
  * Topic: ```bluey/[Device name]/[MAC address | Apple Watch model machine code]```
  * JSON dictionary payload attributes
    * id: MAC Address of iBeacon or Apple Watch
    * rssi: RSSI detecting during BLE scan
    * distance: Estimated distance from detected device (m) - very inaccurate!
    * timeSinceScan: elapsed time (ms) between scan start and MQTT event publish

 The message may look like this for Topic: ```bluey/Bedroom2/Watch5,11```
 ```
 {
     "id": "55:55:55:C2:7E:55",
     "rssi": -58,
     "distance": 0.5779692884153314,
     "timeSinceScan": 17014
 }
```

  * Long hold on a device name to delete it from the list
  
 ## Caveats
  * Have not tested on other protocol MQTT servers (eg, TLS)
  * Be careful entering input; there is minimal error and input validation
  * The code is pretty sloppy. Please don't
  * This works well enough for me; I don't plan to spend much time on its
  * UX is crude - the MQTT password field isn't hidden
  * I was lazy and use really rudimentary code to check required permissions. It's recommended you 
  
  * This works well enough for me; I don't plan to spend much time on its
  
 ## Details
  * You cannot distinguish Apple Watches by MAC address (the main reason they are not widely supported in BLE MQTT gateways. You can distinguish by Model, however, which may work for your household, if it's unique. If multiple people in the house (or nearby apartments) wear the same model Apple Watch, you won't be able to distiguish them unfortunately
  * Runs as foreground service so can run with screen off
  * Uses the amazing BLESSED BLE library to abstract much of the Android BLE library
  * MQTT format is this. You can create a MQTT sensor in Home Assistant like this
  * Tested on: 
   * Pixel 2 XL (Android 11, API 30)
   * Nexus 4 (Lineage OS, Android 8.1, API 27)
   * Nexus 5 (Android 6.0, API 23)
  
 ## Tips
  * Battery Optimization - On some devices, you may need to turn off Battery Optimization to keep the App running when the device sleeps. If having issues with service running, look for tips in dontkillmyapp.com
  * Need to turn on Location, Wifi and Bluetooth scanning permissions, if not prompted
  * If running on phone that's always plugged in, recommend you have a timer or automate with smart plug to unplug daily and charge when low to avoid battery over-heating/puffiness
  * 
