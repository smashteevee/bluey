# bluey
Apple Watch BLE to MQTT Android app

Bluey is an Android app that detects nearby Apple Watches and other Bluetooth Low Energy (BLE) iBeacons. It "publishes" info on detected devices via [MQTT](https://mqtt.org/) events and was designed to work with Home Automation software like [Home Assistant](https://github.com/home-assistant) and [Mosquitto](https://mosquitto.org/). 

The appeal of presence detection with Apple Watches is that they are popular, usually worn around the house and emit BLE advertising packets one can passively detect. The issue is they're not easy to detect (more below).

## DISCLAIMER
I built this project for educational purposes and personal use in my home. Please do not be sketchy nor use it use it for scanning/tracking others. Please be respectful of privacy norms and laws in your area.

## Special thanks
I was inspired by the [ESPHome Apple Watch detection](https://github.com/dalehumby/ESPHome-Apple-Watch-detection) README from [dalehumby](https://github.com/dalehumby) which hints at how to detect known Apple Watches, and the [Blessed](https://github.com/weliem/blessed-android) library which BLE programming accessible to a noob like me.

## Goals
 * Home presence detection with Apple Watches - no need for buying iBeacons!
 * Simplicity - Just needs to work for my personal setup. OK if code is hacky and a mess
 * Tap into native code - I had scripted a [Tasker solution initially](https://www.nyctinker.com/post/ble-presence-detection-for-apple-watch-using-tasker) but was frustrated by its hackiness (I do have limits!)
 * Upcycling - Give life to an old Android phone with a broken screen

 
## 💡 The Idea
 * Apple devices [randomize MAC addresses](https://support.apple.com/guide/security/bluetooth-security-sec82597d97e/web) for privacy, hence you can't use them in BLE detection apps or firmware that rely on static MAC addresses
 * However, you can filter for Apple devices emitting (https://github.com/furiousMAC/continuity/blob/master/messages/nearby_action.md)[Nearby Info messages] in their BLE advertising packets.
 * Then, if you make a GATT connection to the device, you can read its public properties (ie, characteristics) to infer the Apple Watch model (This is also a limitation: If you live in a household or have close neighbors with the same Apple Watch model, you may get false positives)
 * Once you've "seen" the BLE device, you "cache" its MAC address and filter for it in subsequent scans until it changes again, where you begin the process again

 
## Key Features
 * Runs on Android 6 and up (API 23+)
 * Supports detection of Apple Watches by specifying models X through Y
 * Supports iBeacon detection through MAC address
 * Adjustable settings for BLE scan period and cool-off
 * MQTT TCP server support
 * Little performance hacks to get detection as fast as possible (eg, caching last known MAC of targeted Apple Watch)
 * Publishes MQTT events with customizable labels

 
## Uses
 
 ## Caveats
  * The code is pretty sloppy. Please don't
  * This works well enough for me; I don't plan to spend much time on its
  
 ## How to install
  * Side-load APK
  * Download the project and Build it
  
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
