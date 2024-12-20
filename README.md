# bluey
Apple Watch BLE to MQTT Android app

Bluey is an Android app that detects nearby Apple Watches and Bluetooth Low Energy (BLE) iBeacons. It "publishes" info on detected devices via [MQTT](https://mqtt.org/) to integrate with Home Automation software like [Home Assistant](https://github.com/home-assistant) and [Mosquitto](https://mosquitto.org/). 

The appeal of presence detection with Apple Watches is that they are popular, usually worn around the house and emit BLE advertising packets one can passively detect. The issue is they're not easy to detect (more below).

## :warning: ATTENTION/DISCLAIMER
I built this project for educational purposes and personal use in my home. Please do not be sketchy nor use it for scanning/tracking others. Please be respectful of privacy norms and laws in your area.

## :pray: Special thanks
I was inspired by the [ESPHome Apple Watch detection](https://github.com/dalehumby/ESPHome-Apple-Watch-detection) README from [dalehumby](https://github.com/dalehumby) which hints at how to detect known Apple Watches, and the [Blessed](https://github.com/weliem/blessed-android) library which BLE programming accessible to a noob like me.

## Goals
 * :watch: Home presence detection with Apple Watches - no need for iBeacons or extra apps on the Watch!
 * :ok_hand: Simplicity - Just needs to work for my personal setup, so I've hacked, harcoded and taken shortcuts here and there
 * :metal: Tap into native code - I had scripted a [Tasker solution initially](https://www.nyctinker.com/post/ble-presence-detection-for-apple-watch-using-tasker) but was frustrated by its limitations
 * :recycle: Upcycling - Give old Android phones a second life

 
## 💡 The Idea
 * Apple devices [randomize MAC addresses](https://support.apple.com/guide/security/bluetooth-security-sec82597d97e/web) every ~XX minutes for privacy purposes, so you can't use common BLE detection solutions that scan for a "fixed" MAC address
 * However, you can scan for Apple devices (including Apple Watches) which periodically emit (https://github.com/furiousMAC/continuity/blob/master/messages/nearby_action.md)[Nearby Info messages] in BLE advertising packets.
 * If you [GATT connect](https://learn.adafruit.com/introduction-to-bluetooth-low-energy/gatt) to the device, you can read its "characteristics" to infer the specific Apple Watch model is yours (This is also a limitation: If you live in a household or have neighbors with the same model, you may get false positives)
 * Once you've "seen" the Apple Watch, you can look for its last known MAC address in subsequent scans without connecting to it to detect if its Nearby.  If not, you repeat the previous steps to find its new MAC address - rinse and repeat
 * You can automate these steps using an App running in the background on an old Android (bluey)

 
## Key Features
 * Runs on Android 6 and up (API 23+)
 * Detect nearby Apple Watches by model
 * Detect nearby iBeacons by static MAC address
 * Adjustable settings for BLE scan period and cool-off
 * MQTT TCP server support
 * Runs "in the background" as a [foreground service](https://developer.android.com/guide/components/foreground-services), with low battery usage, even if your Android screen turns off
 * Publishes JSON events over MQTT with customizable labels
 * Supports multiple instances (eg, place Androids in different rooms)
 
 ## How to install
  * Download this project and build the APK in Android Studio with Gradle, or
  * Install the latest unsigned APK (under [Releases](https://github.com/smashteevee/bluey/releases))
  
 ## How to use
 ### Basic setup in Settings
 <img src="https://user-images.githubusercontent.com/59382083/210033590-4cbd5c24-2c85-4af3-8f46-03933c79d533.png" width="240"/>
 
  1. In Settings, enter your MQTT server address (eg, tcp://192.168.86.101:1883), username and password
  2. Set a Device name to distinguish this Android phone instance from any others you might also run (eg, Kitchen)

 ### Specify Apple Watch and devices to detect in your list
  <img src="https://user-images.githubusercontent.com/59382083/213614225-2a450b91-b8d4-4fc0-bccf-589ae6f42c31.png" width="240"/>
  
  1. In main screen dropdown, select the Apple Watch model you'd like to detect by its "machine code" (see this [list](https://gist.github.com/adamawolf/3048717) to find yours), then click "Add iOS" to add it to your list
  
   * For instance, the code for the "[Apple Watch SE 40mm case (GPS+Cellular)](https://gist.github.com/adamawolf/3048717#file-apple_mobile_device_types-txt-L169)" is ```Watch5,11```
   * You can also confirm what your watch's code is by using a mobile BLE scanner app (Eg, [Nrf Connect](https://play.google.com/store/apps/details?id=no.nordicsemi.android.mcp), making a GATT connection and reading its Device Make characteristics, like [this](https://static.wixstatic.com/media/1dde11_757a57b96230403a9114db4cc3db0b30~mv2.jpg/v1/fill/w_555,h_892,al_c,q_85,enc_auto/1dde11_757a57b96230403a9114db4cc3db0b30~mv2.jpg)
  
  2. (Optional) Enter in static MAC address(es) of iBeacons that you'd also like to detect and click "Add"
 
  <img src="https://user-images.githubusercontent.com/59382083/213615883-69e21001-6afa-49e8-93b7-e692df57834c.png" width="240"/>

  3. Click "Play" in the Floating Action Button in bottom right corner to begin BLE scanning in the background (you can pause it at anytime)
  4. Note: Tap-and-hold any list items to delete them from your scan filter
  
 ### You're done - now what?
  * Once you press "Play", the app will automatically scan for the devices in your filter list periodically (then cool off), based on your settings. If it detects your designated Apple Watch or iBeacon nearby, it will publish an MQTT event to your configured server
  * Confirm MQTT messages are being sent by using your favorite MQTT monitoring tool, eg I use the MQTT tool in Home Assistant:
  
 <img src="https://user-images.githubusercontent.com/59382083/213615417-5e1d7347-29eb-44d6-8acd-65cbe7e280bc.png" width="240"/>
   
  * Create a MQTT platform sensor in Home Assistant or Node-red automation based on the MQTT topic and payload
 
 ## How to use the MQTT Message 
 ### MQTT topic format details
 
 The MQTT message that is published follows this format:
  * Topic: ```bluey/[Device name]/[MAC address | Apple Watch model machine code]```
  * JSON dictionary payload attributes
    * ```id```: MAC Address of the iBeacon or Apple Watch
    * ```rssi```: RSSI detecting during BLE scan
    * ```distance```: Estimated distance from detected device (m) - very inaccurate!
    * ```timeSinceScan```: elapsed time (ms) between scan start and MQTT event publish

 Eg, The message may look like this for Topic: ```bluey/Bedroom2/Watch5,11```
 ```
 {
     "id": "55:55:55:C2:7E:55",
     "rssi": -58,
     "distance": 0.5779692884153314,
     "timeSinceScan": 17014
 }
```
### Creating a sensor in Home Assistant
 If you use Home Assistant, you can edit your configuration.yaml to create an [MQTT sensor](https://www.home-assistant.io/integrations/sensor.mqtt/) from this topic. Here we're creating an MQTT sensor that sets the value to "Nearby" when the watch is detected, and to "Unavailable" if 480 seconds pass without detecting it:
```
mqtt:
  sensor:
    - name: "Apple Watch SE"
      state_topic: "bluey/+/Watch5,11"
      value_template: >-
        Nearby
      json_attributes_topic: "bluey/+/Watch5,11"
      expire_after: 480
 ```
 In my setup, I use this sensor to feed into a Bayesian sensor (in combination with an [nmap tracker](https://www.home-assistant.io/integrations/nmap_tracker/) for an iPhone) that predicts whether my spouse is home or not. The Apple Watch sensor helps "smooth" out the values as "sleeping" iPhones often drop from the network when they doze:
 
  <img src="https://user-images.githubusercontent.com/59382083/214594330-c44fe5ff-227e-4412-bcf6-daaa778faa31.png" width="269"/>
 
 
 ## Known issues and Limitations
 * This was my first Android app and am just a hobby-coder, so I took a lot of shortcuts and copy-pasted example snippets to get things working; the code can be messy (but Gemini is helping a ton!)
 * ~~I haven't put in any visual feedback in the app showing what the app service is doing currently yet~~ I've added a rudimentary Scan/Resume button- you'll  have to debug from Android Studio ADB logs or rely on MQTT logging to better troubleshoot
 * The app prompts for specific but necessary permissions (Bluetooth scanning, Location, Background Scanning) based on Android version, but it's a [complex area](https://developer.android.com/training/permissions/requesting) and I may have missed cases for some versions, or particular makes. Please grant permissions when prompted for Location (most Precise) and background Bluetooth scanning (All the Time); you may need to manually grant in Permissions settings if I missed a particular permission your device requires
 * Turn off Battery Optimization for the app - Some devices may kill Bluey foreground service when the device sleeps; different manufacturers vary in aggressiveness. If you're seeing Bluey being killed after the phone starts dozing, follow the tips in [dontkillmyapp.com](dontkillmyapp.com) for your device 
 * Have not tested on other protocol MQTT servers (eg, TLS) - maybe they work?
 * Be careful entering input; there is minimal error and input validation so far
 * Yah, the UX is crude (eg, the MQTT password field isn't hidden)- sorry!
 
 ## Tested on: 
 * Pixel 3 (Android 12, API 31)
 * Pixel 2 XL (Android 11, API 30)
 * Nexus 4 (Lineage OS, Android 8.1, API 27)
 * Nexus 5 (Android 6.0, API 23)
  
 ## Tips
 * If running on an Android phone that's always plugged in, I recommend you have a timer or automate with smart plug to unplug daily and charge when low to avoid battery over-heating/puffiness
 * Because you can only infer an Apple Watch is yours by Model (Machine code), you can get false positives if neighbors or people in the home wear the same model. You can minimize this by adjusting the "RSSI filter" setting to a larger number to shrink your radius of detection (eg, adjust RSSI from -90 to -60 to detect only closer proximity, ie stronger signal)
 * You can increase the "BLE Scan Period" to scan for a longer period of time. This can help discovery of weaker signals or BLE iBeacons that emit less frequently. This will increase your battery usage however
 * You can adjust the "Cool-off Period" to set how long Bluey waits before it begins another BLE scan. Increasing this value can help reduce battery drain
