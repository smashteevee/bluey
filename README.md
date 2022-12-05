# bluey
Apple Watch BLE to MQTT Android app

Bluey is an open-source Android app that detects nearby Bluetooth Low Energy (BLE) devices, including Apple Watches, and "forwards" their info as published MQTT events. It was designed for DIY home presence detection with popular, open-source solutions like Home Assistant and Mosquitto MQTT server. 

The main draw of detecting Apple Watches is that they are popular devices already worn by a lot of folks (no need to buy an iBeacon dongle) and BLE advertising packets are emitted for passive detection, so no need for additional battery-draining apps on the Watch.  

I built this for myself but sharing if useful for others.

## Goals
 * Basic home presence detection using Apple Watches without the need for additional software for the watch
 * Keep it simple (this is also because this is my first-ever Android app)
 * Flexibility: publishes MQTT events or broadcasts intents for Tasker
 * Breathe new life to an old Android phone instead of junking it
 
## The Idea 
 * Apple devices rotate MAC addresses, hence they aren't supported in many BLE presence detection apps that use a static list of MACs
 * However, if make a GATT connection, you can read its characteristics, including its Model series, to infer if it's the Apple Watch you care about
  * (This is also it's main limitation: If you live in a household or have closeby neighbors with the same Apple Watch model, you can't discern yours or will have false positives, though there are ways you can work with it)
 * Once you've found the device, you scan for that MAC address until it changes again, where you begin the process again

 
## Key Features
 * Runs on Android 6 and up (API 23+)
 * Supports detection of Apple Watches by specifying models X through Y
 * Supports BLE beacons through MAC addresses
 * Adjustable settings for scan period and cool-off
 * MQTT TCP server support
 * Little performance hacks to get detection as fast as possible (eg, caching last known MAC of targeted Apple Watch)
 
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
