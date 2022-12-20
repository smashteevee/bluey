# bluey
Apple Watch BLE to MQTT Android app

Bluey is an Android app that detects nearby Apple Watches and other Bluetooth Low Energy (BLE) iBeacons. It "publishes" their info as MQTT events and was designed for DIY home presence detection solutions like Home Assistant and Mosquitto MQTT server. 

The main draw of presence detection with Apple Watches is that they are popular devices, usually worn around the house and emit BLE advertising packets one can use for passive detection. The issue is they're not easy to detect.

I built this for educational purposes and personal use and sharing if it can help others. I was inspired by the [ESPHome Apple Watch detection](https://github.com/dalehumby/ESPHome-Apple-Watch-detection) README from [dalehumby](https://github.com/dalehumby) which hints at how to detect Apple Watches, and the [Blessed](https://github.com/weliem/blessed-android) library which made getting started with BLE simple.

## Goals
 * Home presence detection with Apple Watches - no need for buying iBeacons!
 * Simplicity - Just needs to work. Am also constrained by my limited Android coding experience (this is my first!)
 * Tap into the power of native code - I had scripted a [Tasker solution initially](https://www.nyctinker.com/post/ble-presence-detection-for-apple-watch-using-tasker) but was frustrated by the hackiness of simulating screen touches
 * Upcycling - Give life to an old Android phone with a broken screen

 
## 💡 The Idea
 * Apple devices rotate MAC addresses for privacy, hence you can't use them in BLE detection apps or firmware that rely on static MAC addresses
 * However, you can filter for Apple devices emitting (https://github.com/furiousMAC/continuity/blob/master/messages/nearby_action.md)[Nearby Info messages] in their BLE advertising packets.
 * Then, if you make a GATT connection to the device, you can read its public properties (ie, characteristics) to infer the Apple Watch model
  * (This is also a limitation: If you live in a household or have close neighbors with the same Apple Watch model, you can't discern yours, though there are ways you can work with it)
 * Once you've confirmed the BLE device, you scan for that MAC address until it changes again, where you begin the process again

 
## Key Features
 * Runs on Android 6 and up (API 23+)
 * Supports detection of Apple Watches by specifying models X through Y
 * Supports BLE beacons through MAC addresses
 * Adjustable settings for scan period and cool-off
 * MQTT TCP server support
 * Little performance hacks to get detection as fast as possible (eg, caching last known MAC of targeted Apple Watch)
 *  * Flexibility: publishes MQTT events or broadcasts intents for Tasker

 
## Uses
 
 ## Caveats
  * The code is pretty sloppy. Please don't
  * This works well enough for me; I don't plan to spend much time on its
  
 ## How to install
  * Side-load APK
  * Build it
  
 ## How to use
  * Enter in MQTT server details
  * Enter in MAC address of devices that you'd like to detect
  * Select Apple Watch model(s) you'd like to detect
  * Check out how the MQTT messages are sent (go to HA MQTT monitor to see), then create a MQTT platform sensor in Home Assistant or write a Node-red automation to detect
  * Long hold on a device name to delete it from the list
  
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
  * YMMV - May need to turn off Battery Optimization to keep running when device is sleep. If having issues with service running, look for tips in dontkillmyapp.com
  * Need to turn on Location, Wifi and Bluetooth scanning permissions, if not prompted
  * If running on phone that's always plugged in, recommend you have a timer or automate with smart plug to unplug daily and charge when low to avoid battery over-heating/puffiness
  * 
