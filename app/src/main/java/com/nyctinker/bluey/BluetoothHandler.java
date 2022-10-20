package com.nyctinker.bluey;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;
import androidx.preference.PreferenceManager;

import com.welie.blessed.BluetoothBytesParser;
import com.welie.blessed.BluetoothCentralManager;
import com.welie.blessed.BluetoothCentralManagerCallback;
import com.welie.blessed.BluetoothPeripheral;
import com.welie.blessed.BluetoothPeripheralCallback;
import com.welie.blessed.ConnectionPriority;
import com.welie.blessed.ConnectionState;
import com.welie.blessed.GattStatus;
import com.welie.blessed.HciStatus;
import com.welie.blessed.ScanFailure;

import static com.welie.blessed.BluetoothBytesParser.FORMAT_UINT8;
import static com.welie.blessed.BluetoothBytesParser.bytes2String;


import org.eclipse.paho.client.mqttv3.DisconnectedBufferOptions;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import info.mqtt.android.service.MqttAndroidClient;

/*
* Foreground Service Class that handles BLE functions
*
* Borrows liberally from example code in BLESSED BLE library https://github.com/weliem/blessed-android
*/
public class BluetoothHandler extends Service {
    public static class BLEBeacon {


        public BLEBeacon(BluetoothPeripheral peripheral) {
            name = peripheral.getName();
            address = peripheral.getAddress();
        }

        public String name;
        public String address;
        public String modelName;
        public int rssi;
    }

    private static final String TAG = "BluetoothHandler";

    public static boolean isRunning = false;
    public static final String ACTION_START_FOREGROUND_SERVICE = "ACTION_START_FOREGROUND_SERVICE";
    public static final String ACTION_STOP_FOREGROUND_SERVICE = "ACTION_STOP_FOREGROUND_SERVICE";
    public static final String ACTION_UPDATE_FOREGROUND_SERVICE = "ACTION_UPDATE_FOREGROUND_SERVICE";
    private ArrayList<String> bleFilterList = new ArrayList<>();

    private final Queue<Runnable> commandQueue = new ConcurrentLinkedQueue<>();
    private boolean commandQueueBusy;
    Handler bleHandler = new Handler(Looper.getMainLooper());

    private static int bleScanCoolOffTimeSettingValue = 0; // Default - overridden in Settings
    private static int bleScanPeriodTimeSettingValue = 30000; // Default - overridden in Settings
    private final static int DELAY_BETWEEN_BT_COMMANDS = 2000; // MS Delay between BT/GATT commands to avoid race issues on older Android / BT- def. a hack
    private final static int COMMAND_WATCHDOG_WAKEUP = 240000; // MS Watchdog wake to kill hanging BLE (usually GATT) commands due to some BT Race conditions

    // Intent constants
    public static final String MEASUREMENT_BEACON = "blessed.measurement.beacon";
    public static final String MEASUREMENT_EXTRA_PERIPHERAL = "blessed.measurement.peripheral";

    // UUIDs for the Device Information service (DIS)
    private static final UUID DIS_SERVICE_UUID = UUID.fromString("0000180A-0000-1000-8000-00805f9b34fb");
    private static final UUID MANUFACTURER_NAME_CHARACTERISTIC_UUID = UUID.fromString("00002A29-0000-1000-8000-00805f9b34fb");
    private static final UUID MODEL_NUMBER_CHARACTERISTIC_UUID = UUID.fromString("00002A24-0000-1000-8000-00805f9b34fb");

    // UUIDs for the Battery Service (BAS)
    private static final UUID BTS_SERVICE_UUID = UUID.fromString("0000180F-0000-1000-8000-00805f9b34fb");
    private static final UUID BATTERY_LEVEL_CHARACTERISTIC_UUID = UUID.fromString("00002A19-0000-1000-8000-00805f9b34fb");
    private static final String CHANNEL_ID = "BLE Scan Channel";

    private static int lastRssi = 0;

    // Local variables
    public  BluetoothCentralManager central = null;
    private final Handler handler = new Handler();
    private @NotNull
    Map<String, BluetoothPeripheral> scannedIOSPeripherals = new ConcurrentHashMap<>();
    private @NotNull Map<String, BLEBeacon> foundDevices = new ConcurrentHashMap<>();
    private ArrayList<String> targetMACs = new ArrayList<>();
    private ArrayList<String> targetModels = new ArrayList<>();

    Instant lastCommandStart = null;


    // MQTT variables
   // private static final String mqttServerURI = "tcp://192.168.86.230:1883";
    //private static final String mqttServerUsername = "mqtt_client";
   // private static final String mqttServerPassword = "Mqtt3dl3p";
    private static String lastMqttServerUsername = "";
    private static String lastMqttServerPassword = "";
    private MqttAndroidClient mqttAndroidClient = null;


    // Helper
    private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();
    private static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars);
    }

    private void loadSettings() {

        Log.i(TAG, "Loading Settings...");
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        bleScanPeriodTimeSettingValue = Integer.parseInt(prefs.getString("ble_scan_period", String.valueOf(bleScanPeriodTimeSettingValue))); // TODO: FIX HARDCODED HORRIBLE HACK
        bleScanCoolOffTimeSettingValue = Integer.parseInt(prefs.getString("cool_off_period", String.valueOf(bleScanCoolOffTimeSettingValue))); // TODO: Fix hack for reading integer being saved as strings in prefs
        Log.d(TAG, "Setting: BLE Scan period: " + bleScanPeriodTimeSettingValue);
        Log.d(TAG, "Setting: Cool Off Period: " + bleScanCoolOffTimeSettingValue);

    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "My foreground service onCreate().");


        // Create BluetoothCentral if it doesn't exist
        if (central == null) {
            central = new BluetoothCentralManager(getApplicationContext(), bluetoothCentralManagerCallback, new Handler());
        }

        // Load settings
        loadSettings();

        // initalize MQTT connection
        initializeMQTT();
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if(intent != null)
        {
            String action = intent.getAction();

            switch (action)
            {
                case ACTION_START_FOREGROUND_SERVICE:
                    if (!isRunning) {
                        startForegroundService();
                    }
                    Toast.makeText(getApplicationContext(), "Foreground service start command received.", Toast.LENGTH_LONG).show();
                    break;
                case ACTION_STOP_FOREGROUND_SERVICE:
                    stopForegroundService();
                    Toast.makeText(getApplicationContext(), "Foreground service stop command received.", Toast.LENGTH_LONG).show();
                    break;

                case ACTION_UPDATE_FOREGROUND_SERVICE:
                    Toast.makeText(getApplicationContext(), "Foreground service update command received.", Toast.LENGTH_LONG).show();
                    updateForegroundService(intent);
                    break;
            }
        }
        super.onStartCommand(intent, flags, startId);
        return android.app.Service.START_STICKY;

    }

    /* Used to build and start foreground service. */
    private void startForegroundService()
    {
        Log.d(TAG, "Start foreground service.");
        isRunning = true;

        // Create notification default intent.
        Intent intent = new Intent();
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, 0);

        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = getString(R.string.channel_name);
            String description = getString(R.string.channel_description);
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }

        // Create notification builder.

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID);

        // Make notification show big text.
        NotificationCompat.BigTextStyle bigTextStyle = new NotificationCompat.BigTextStyle();
        bigTextStyle.setBigContentTitle("blescan implemented by foreground service.");
        bigTextStyle.bigText("Android foreground service is a android service which can run in foreground always, it can be controlled by user via notification.");
        // Set big text style.
        builder.setStyle(bigTextStyle);

        builder.setWhen(System.currentTimeMillis());


        // Make head-up notification.
        builder.setFullScreenIntent(pendingIntent, true);

        // Add Play button intent in notification.
       /* Intent playIntent = new Intent(this, MyForeGroundService.class);
        playIntent.setAction(ACTION_PLAY);
        PendingIntent pendingPlayIntent = PendingIntent.getService(this, 0, playIntent, 0);
        NotificationCompat.Action playAction = new NotificationCompat.Action(android.R.drawable.ic_media_play, "Play", pendingPlayIntent);
        builder.addAction(playAction);

        // Add Pause button intent in notification.
        Intent pauseIntent = new Intent(this, MyForeGroundService.class);
        pauseIntent.setAction(ACTION_PAUSE);
        PendingIntent pendingPrevIntent = PendingIntent.getService(this, 0, pauseIntent, 0);
        NotificationCompat.Action prevAction = new NotificationCompat.Action(android.R.drawable.ic_media_pause, "Pause", pendingPrevIntent);
        builder.addAction(prevAction);*/

        // Build the notification.
        Notification notification = builder.build();

        // Start foreground service.
        startForeground(1, notification);

        // Start Scan for peripherals with a certain service UUIDs
        central.startPairingPopupHack();

        // Scan for all
        startScan();

    }

    // Update parameters for BLE scanning service from Frontend activity
    private void updateForegroundService(Intent intent) {
        Log.d(TAG, "Updating Foreground service with new list");
        // Update filter list
        bleFilterList = intent.getStringArrayListExtra("BLEFilterList");
    }
    private void stopForegroundService()
    {
        Log.d(TAG, "Stop foreground service.");
        isRunning = false;

        // Stop foreground service and remove the notification.
        stopForeground(true);

        // Stop the foreground service.
        stopSelf();
    }

   /* private void createNotificationChannel() {
        NotificationChannel serviceChannel = new NotificationChannel(
                CHANNEL_ID,
                "Foreground Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT);

        getSystemService(NotificationManager.class).createNotificationChannel(serviceChannel);
    }*/

    // Callback for peripherals
    private final BluetoothPeripheralCallback peripheralCallback = new BluetoothPeripheralCallback() {
        @Override
        public void onServicesDiscovered(@NotNull BluetoothPeripheral peripheral) {
            // Request a higher MTU, iOS always asks for 185
            peripheral.requestMtu(185);

            // Request a new connection priority
            peripheral.requestConnectionPriority(ConnectionPriority.HIGH); // TODO: Recondier if we need

            // Read manufacturer and model number from the Device Information Service
            peripheral.readCharacteristic(DIS_SERVICE_UUID, MANUFACTURER_NAME_CHARACTERISTIC_UUID);
            peripheral.readCharacteristic(DIS_SERVICE_UUID, MODEL_NUMBER_CHARACTERISTIC_UUID);

        }

        @Override
        public void onNotificationStateUpdate(@NotNull BluetoothPeripheral peripheral, @NotNull BluetoothGattCharacteristic characteristic, @NotNull GattStatus status) {
            if (status == GattStatus.SUCCESS) {
                final boolean isNotifying = peripheral.isNotifying(characteristic);
                Log.d(TAG, "SUCCESS: Notify set to " + isNotifying + " for " + characteristic.getUuid());

            } else {

                Log.e(TAG, "ERROR: Changing notification state failed for " + characteristic.getUuid() + "(" + status + ")");
                // Bail
                completedCommand();
            }
        }

        @Override
        public void onCharacteristicWrite(@NotNull BluetoothPeripheral peripheral, @NotNull byte[] value, @NotNull BluetoothGattCharacteristic characteristic, @NotNull GattStatus status) {
            if (status == GattStatus.SUCCESS) {
                Log.d(TAG, "SUCCESS: Writing " + bytes2String(value) + " to " + characteristic.getUuid());
            } else {
                Log.d(TAG, "ERROR: Failed writing " + bytes2String(value) + " to " +  characteristic.getUuid() + "(" + status + ")");
            }
        }

        @Override
        public void onReadRemoteRssi(@NotNull BluetoothPeripheral peripheral, int rssi, @NotNull GattStatus status) {

            if (status == GattStatus.SUCCESS) {
                Log.d(TAG, "SUCCESS: Reading " + rssi + " from " + peripheral.getAddress());
                lastRssi = rssi;
            } else {
                Log.e(TAG, "ERROR: Failed reading RSSI");
            }

        }

        @Override
        public void onCharacteristicUpdate(@NotNull BluetoothPeripheral peripheral, @NotNull byte[] value, @NotNull BluetoothGattCharacteristic characteristic, @NotNull GattStatus status) {
            if (status != GattStatus.SUCCESS) {
                Log.e(TAG, "onCharacteristicsUpdate error: " + status);
                // Exit command
                completedCommand();
                return;
            }

            UUID characteristicUUID = characteristic.getUuid();
            BluetoothBytesParser parser = new BluetoothBytesParser(value);

            if (characteristicUUID.equals(BATTERY_LEVEL_CHARACTERISTIC_UUID)) {
                int batteryLevel = parser.getIntValue(FORMAT_UINT8);
                Log.d(TAG, "Received battery level " + batteryLevel);
            } else if (characteristicUUID.equals(MANUFACTURER_NAME_CHARACTERISTIC_UUID)) {
                String manufacturer = parser.getStringValue(0);
                Log.d(TAG, "Received manufacturer: " + manufacturer);
            } else if (characteristicUUID.equals(MODEL_NUMBER_CHARACTERISTIC_UUID)) {
                String modelNumber = parser.getStringValue(0);
                Log.d(TAG, "Received modelnumber: " + modelNumber);



                // Notify Main Activity UI
               // Intent intent = new Intent(MEASUREMENT_BEACON);
                //intent.putExtra(MEASUREMENT_EXTRA_PERIPHERAL, modelNumber);
                //getApplicationContext().sendBroadcast(intent);

                // Add it to our collection, if it was a target Model
                if (targetModels.contains(modelNumber)) {
                    BLEBeacon bleBeacon = new BLEBeacon(peripheral);
                    bleBeacon.modelName = modelNumber;
                    // TODO get RSSI in a less hacky way;
                    bleBeacon.rssi = lastRssi;
                    foundDevices.put(peripheral.getAddress(), bleBeacon);
                }


                // }

                // Done with the command as we've gotten model number
                completedCommand();


            }
        }

        @Override
        public void onMtuChanged(@NotNull BluetoothPeripheral peripheral, int mtu, @NotNull GattStatus status) {
            Log.d(TAG, "new MTU set: " + mtu);
        }

      /*  private void sendMeasurement(@NotNull Intent intent, @NotNull BluetoothPeripheral peripheral ) {
            intent.putExtra(MEASUREMENT_EXTRA_PERIPHERAL, peripheral.getAddress());
            getApplicationContext().sendBroadcast(intent);
        }*/


    };

    // Callback for central
    private final BluetoothCentralManagerCallback bluetoothCentralManagerCallback = new BluetoothCentralManagerCallback() {

        @Override
        public void onConnectedPeripheral(@NotNull BluetoothPeripheral peripheral) {
            Log.d(TAG, "connected to " + peripheral.getName());

            // Don't close out current GATT command as we await service discovery

            // Queue remote read
            peripheral.readRemoteRssi();
        }

        @Override
        public void onConnectionFailed(@NotNull BluetoothPeripheral peripheral, final @NotNull HciStatus status) {
            Log.e(TAG, "connection " + peripheral.getName() + " failed with status " + status);
            // Close out the in-progress Connect command
            completedCommand();
        }

        @Override
        public void onDisconnectedPeripheral(@NotNull final BluetoothPeripheral peripheral, final @NotNull HciStatus status) {
            Log.d(TAG, "disconnected " + peripheral.getName() + " with status " +  status);

            // Close out the Connect command
            completedCommand();

        }

        @Override
        public void onDiscoveredPeripheral(@NotNull BluetoothPeripheral peripheral, @NotNull ScanResult scanResult) {

            byte[] appleData = scanResult.getScanRecord().getManufacturerSpecificData(0x004c); // filter to apple data
            if (appleData != null && appleData.length > 4) {

                // FIlter to Nearby Info message per https://github.com/furiousMAC/continuity/blob/master/messages/nearby_info.md
                // extract Nearby Info payload
                if (appleData[0] == 0x10 && appleData[1] == 0x05) {
                    Log.d(TAG,"Found Apple device with Nearby Info: " + peripheral.getAddress() );
                    Log.d(TAG, "Scan contains mfg data: " + bytesToHex(appleData));
                    Log.d(TAG, "Nearby info Status Flag is: " + appleData[2]);
                    Log.d(TAG, "Nearby info Action is: " + appleData[3]);

                    // Add to our collection of iOS Devices with Nearby Info for later processing
                    scannedIOSPeripherals.put(peripheral.getAddress(), peripheral);

                    // GATT connect to get Model, Make info
                    //central.connectPeripheral(peripheral, peripheralCallback);
                }
            } else if ( targetMACs.contains(peripheral.getAddress())) {
                Log.d(TAG, "Found targeted MAC address:");
                Log.d(TAG, scanResult.toString());

                // TODO: add to method
                // Add it to our collection to send mqtt for
                BLEBeacon bleBeacon = new BLEBeacon(peripheral);
                bleBeacon.modelName = null; // null => regular beacon
                bleBeacon.rssi = scanResult.getRssi();
                foundDevices.put(peripheral.getAddress(), bleBeacon);

            } else {
                    Log.d(TAG, "Found other device:");
                    Log.d(TAG, scanResult.toString());


                    // TODO: end scan earleir after finding known devices (otherwise timeout)

            }



        }

        @Override
        public void onBluetoothAdapterStateChanged(int state) {
            Log.d(TAG, "bluetooth adapter changed state to " + state);
            if (state == BluetoothAdapter.STATE_ON) {
                // Bluetooth is on now, start scanning again
                // Scan for peripherals with a certain service UUIDs
                central.startPairingPopupHack();
                //startScan();
            }
        }

        @Override
        public void onScanFailed(@NotNull ScanFailure scanFailure) {
            Log.e(TAG, "scanning failed with error " + scanFailure);
            // TODO: Close and re-open adapter as hack
        }
    };

    // Method to turn off BLE scan
    private void stopScan() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                Log.i(TAG, "Stopping Scan...");
                // Stop BLE scan
                central.stopScan();

                // Signal we're done with previous queued Scan command
                completedCommand();

                // Processing of GATT connections
                processScannedPeripherals();

                // Process for sending found devices to MQTT
                processMQTTMessages();

                // Cool off before next scan
                coolOff();

                startScan();
            }
        }, bleScanPeriodTimeSettingValue);  // How long BLE scan period is
    }

    /*
        Cool off between MQTT processing and next BLE scan
     */
    private void coolOff() {
        // Queue Cool off
        Boolean result = commandQueue.add(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "Cooling off before scan.");
                // Kick off delay before moving to scan
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {

                        Log.i(TAG, "Ending Cool Off period");
                        // We done, complete the command
                        completedCommand();

                    }
                }, bleScanCoolOffTimeSettingValue); // Cool off period before starting next scan

            }
        });

        if(result) {
            // Queue up to receive next command
            nextCommand();
        } else {
            Log.e(TAG, "ERROR: Could not enqueue Cool Off command");
        }
    }

    /*
        Method to re-init and connect to MQTT server
     */
    private void initializeMQTT() {

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        String mqttServerURI = prefs.getString("mqtt_server_address", "");
        String mqttServerUsername = prefs.getString("mqtt_username", "");
        String mqttServerPassword = prefs.getString("mqtt_password", "");

        Log.d(TAG, "Loading Setting: MQTT server: " + mqttServerURI);
        Log.d(TAG, "Loading Setting: MQTT username " + mqttServerUsername);
        Log.d(TAG, "Loading Setting: MQTT password " + mqttServerPassword);

        // Re-initialize any existing MQTT connection if it exists but URI, mqtt username or pw have changed
        if (mqttAndroidClient != null && ((!mqttAndroidClient.getServerURI().matches(mqttServerURI)) ||
                (!lastMqttServerUsername.matches(mqttServerUsername)) ||
                (!lastMqttServerPassword.matches(mqttServerPassword)))){
            mqttAndroidClient.unregisterResources();
            mqttAndroidClient.close();
            mqttAndroidClient.disconnect();
            mqttAndroidClient.setCallback(null);
            mqttAndroidClient = null;

        }

        // Track the most recent values of MQTT settings
        lastMqttServerUsername = mqttServerUsername;
        lastMqttServerPassword = mqttServerPassword;

        // Setup callbacks if needed  // TODO: Change to better regexp validation
        if (mqttAndroidClient == null &&
                (mqttServerURI.contains("tcp://") ||
                        mqttServerURI.contains("ws://")
                ) &&
                (mqttServerUsername.length() > 0) && (mqttServerPassword.length() > 0)){

            mqttAndroidClient = new MqttAndroidClient(getApplicationContext(), mqttServerURI, mqttServerUsername + "@" + "bluey");

            mqttAndroidClient.setCallback(new MqttCallbackExtended() {
                @Override
                public void connectComplete(boolean reconnect, String serverURI) {
                    if (reconnect) {
                        Log.i(TAG, "MQTT Reconnected to : " + serverURI);
                    } else {
                        Log.i(TAG, "MQTT Connected to: " + serverURI);
                    }
                }

                @Override
                public void connectionLost(Throwable cause) {
                    Log.d(TAG, "The MQTT Connection was lost.");
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) throws Exception {
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {
                }
            });
        }

        // Attempt connect if not already
        if (mqttAndroidClient != null && !mqttAndroidClient.isConnected()) {
            MqttConnectOptions mqttConnectOptions = new MqttConnectOptions();
            mqttConnectOptions.setAutomaticReconnect(true);
            mqttConnectOptions.setCleanSession(true);
            mqttConnectOptions.setUserName(mqttServerUsername);
            mqttConnectOptions.setPassword(mqttServerPassword.toCharArray());

            try {
                mqttAndroidClient.connect(mqttConnectOptions, null, new IMqttActionListener() {
                    @Override
                    public void onSuccess(IMqttToken asyncActionToken) {
                        try {
                            asyncActionToken.getSessionPresent();
                        } catch (Exception e) {
                            String message = e.getMessage();
                            Log.e(TAG, "MQTT error message is null " + String.valueOf(message == null));
                        }

                        Toast.makeText(getApplicationContext(), "connected", Toast.LENGTH_SHORT).show();
                        DisconnectedBufferOptions disconnectedBufferOptions = new DisconnectedBufferOptions();
                        disconnectedBufferOptions.setBufferEnabled(true);
                        disconnectedBufferOptions.setBufferSize(100);
                        disconnectedBufferOptions.setPersistBuffer(false);
                        disconnectedBufferOptions.setDeleteOldestMessages(false);
                        mqttAndroidClient.setBufferOpts(disconnectedBufferOptions);
                    }

                    @Override
                    public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                        Log.e(TAG, "Failed to connect to: " + mqttServerURI + " - " + exception.getMessage());
                    }
                });
            } catch (Exception ex) {
                Log.e(TAG, "MQTT excpetion: " + ex.getMessage());
            }
        }

    }
    private void processMQTTMessages() {
        // TODO: Refactor
        // Enqueue the MQTT processing
        Log.d(TAG, "Enqueuing mqtt process...");
        Boolean result = commandQueue.add(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "process devices to send MQTT...");

                // For every found device
                for (Map.Entry<String, BLEBeacon> entry : foundDevices.entrySet()) {
                    String key = entry.getKey().toString();
                    BLEBeacon foundDevice = entry.getValue();

                    // Generate JSON string like
                    // topic: bluey/Watch5,11 (if apple model) or topic: bluey/[mac_address]
                    // message: { "id":"[mac address]", "rssi":-84}
                    String modelName = !(TextUtils.isEmpty(foundDevice.modelName)) ? foundDevice.modelName : foundDevice.address;
                    String topic = "bluey/" + modelName;
                    JSONObject payload = new JSONObject();

                    try {
                        payload.put("id", foundDevice.address);
                        payload.put("rssi", foundDevice.rssi);
                        // TODO: Replace experimental hack for distance
                        payload.put("distance", Math.pow(10, (-63 - (foundDevice.rssi))/(10*2.1) ));
                        String message = payload.toString();

                        mqttAndroidClient.publish(topic, message.getBytes(),0,false);
                        Toast.makeText(getApplicationContext(), "published mqtt: " + topic + ": " + message, Toast.LENGTH_LONG).show();
                        Log.i(TAG, "publishing MQTT : " + topic + ": " + message);

                    } catch ( Exception e) {
                        Log.e(TAG, e.toString());
                    }
                }


                // TODO:  Add completed done command in MQTT callback

                // TODO: Cleanup method
                scannedIOSPeripherals.clear();
                foundDevices.clear();
                lastRssi = 0;

                // We done, complete the command
                completedCommand();
            }
        });

        if(result) {
            // Queue up to receive next command
            nextCommand();
        } else {
            Log.e(TAG, "ERROR: Could not enqueue MQTT processing command");
        }



    }

    /**
     * Convenience function for validating a MAC address
     */
    private boolean validateMAC(String mac) {
        Pattern p = Pattern.compile("^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$");
        Matcher m = p.matcher(mac);
        return m.find();
    }

    /*
    *   Convenience function for validating Apple Model string per https://gist.github.com/adamawolf/3048717
     */
    private boolean validateModel(String model) {
        Pattern p = Pattern.compile("^[0-9A-Za-z]+,[0-9]+$");
        Matcher m = p.matcher(model);
        return m.find();
    }

    /**
     * Convenience function to unblock a hanging GATT command that doesn't return after awhile
     * by signalling to end the current command
     */

    private void setupWatchDog() {
        // Kicks off watchdog to wake up later to check for hanging commands
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "Watchdog waking...");

                // If it's been awhile since the last command kicked off, let's
                // Signal we're done with previous command
                if (lastCommandStart != null && Duration.between(lastCommandStart, Instant.now()).toMillis() > 240000) {
                    Log.d(TAG, "Watchdog ending command");
                    completedCommand();
                }

            }
        }, COMMAND_WATCHDOG_WAKEUP);  // Time before watchdog wakes

    }


    // Method for processing scanned devices by GATT connecting
    private void processScannedPeripherals() {
        boolean result;
        // TODO
        // TODO: For any detected target MAC addresss devices, fire off MQTT
        Log.d(TAG, "processing scanned peripheraps...");
        // TODO: For scanned IOS devices with nearby Info, see if detected any last known address, then fire off MQTT

        // If none, then we connect to each find read characteristic Model Number
        for (Map.Entry<String, BluetoothPeripheral> entry : scannedIOSPeripherals.entrySet()) {
            final String key = entry.getKey().toString();
            final BluetoothPeripheral iosBluetoothPeripheral = entry.getValue();

            Log.d(TAG, "processing: " + iosBluetoothPeripheral.getAddress());

            // Copy into our lightweight Hash of iOS peripherals for convenience


            // Enqueue the connect command now
            result = commandQueue.add(new Runnable() {
                @Override
                public void run() {
                    Log.d(TAG, "connecting to peripheral..." + key + " with state: " + iosBluetoothPeripheral.getState().toString());
                    if (iosBluetoothPeripheral.getState() != ConnectionState.CONNECTED && iosBluetoothPeripheral.getState() != ConnectionState.CONNECTING) {
                        central.connectPeripheral(iosBluetoothPeripheral, peripheralCallback);
                        setupWatchDog();
                    }
                }
            });

            if(result) {
                // Queue up to receive next command
                nextCommand();

                // TODO: put in method
                // Then Enqueue its Disconnect command to follow after it's done
                result = commandQueue.add(new Runnable() {
                    @Override
                    public void run() {
                        // Only attempt disconnect if it's clear it's not already disconnected
                        if (iosBluetoothPeripheral.getState() != ConnectionState.DISCONNECTED)/*central.getConnectedPeripherals().contains(iosBluetoothPeripheral))*/ {
                            Log.d(TAG, "disconnecting peripheral..." + key + " with state: " + iosBluetoothPeripheral.getState().toString());
                            central.cancelConnection(iosBluetoothPeripheral);
                        } else {
                            // Close out the Disconnect command as we're skipping it
                            completedCommand();
                        }
                    }
                });

                if(result) {
                    // Queue up to receive next command
                    nextCommand();
                } else {
                    Log.e(TAG, "ERROR: Could not enqueue Disconnect Peripheral command");
                }

            } else {
                Log.e(TAG, "ERROR: Could not enqueue Connect Peripheral command");
            }



        }





    }

    // Method for Command queue from https://medium.com/@martijn.van.welie/making-android-ble-work-part-3-117d3a8aee23
    private void nextCommand() {
        // If there is still a command being executed then bail out
        if(commandQueueBusy) {
            return;
        }

        // Check if we still have a valid BLE Central object
        if (central == null) {
            Log.e(TAG, "ERROR: Central is 'null', clearing command queue");
            commandQueue.clear();
            commandQueueBusy = false;
            return;
        }

        // Execute the next command in the queue
        if (commandQueue.size() > 0) {
            final Runnable bluetoothCommand = commandQueue.peek();
            commandQueueBusy = true;
            //nrTries = 0;

            Log.d(TAG, "Running next command...");
            bleHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    try {
                        lastCommandStart = Instant.now();
                        bluetoothCommand.run();
                    } catch (Exception ex) {
                        lastCommandStart = null;
                        Log.e(TAG, "ERROR: Command exception" + ex);
                    }
                }
            }, DELAY_BETWEEN_BT_COMMANDS);
           // });
        }
    }

    // Method to complete command per https://medium.com/@martijn.van.welie/making-android-ble-work-part-3-117d3a8aee23
    private void completedCommand() {
        Log.d(TAG, "Completing command...");
        commandQueueBusy = false;
        lastCommandStart = null;
        //isRetrying = false;
        commandQueue.poll();
        nextCommand();
    }

    /*
        Method for initiating BLE Scan based on filters
     */
    private void startScan() {

        // Enqueue the scan command now
        Boolean result = commandQueue.add(new Runnable() {
            @Override
            public void run() {
                //    central.scanForPeripheralsWithServices(new UUID[]{BLP_SERVICE_UUID, HTS_SERVICE_UUID, HRS_SERVICE_UUID, PLX_SERVICE_UUID, WSS_SERVICE_UUID, GLUCOSE_SERVICE_UUID});

                // Prepare to stop after period
                Log.i(TAG, "Starting Scan...");
                // Queue up stop scan command via post delay
                stopScan();

                // Initialize filters based on the BLE beacon filters entered
                targetModels.clear();
                targetMACs.clear();

                // Load latest settings before scan
               loadSettings();

               // Re-ini MQTT connection if settings have changed
                initializeMQTT();

                // Apply multiple scan filters: Apple devices using partial mfger data mask, and allowlisted beacons MAC
                final List<ScanFilter> filters = new ArrayList<>();

                for (String bleItem : bleFilterList) {
                    Log.d(TAG, "Processing " + bleItem);
                    if (validateMAC(bleItem)) {
                        // If it's a valid MAC address add to MAC scan filter
                        ScanFilter filterMac = new ScanFilter.Builder().setDeviceAddress(bleItem).build();
                        filters.add(filterMac);
                        if (!targetMACs.contains(bleItem)) {
                            targetMACs.add(bleItem);
                        }
                        Log.i(TAG, "Adding MAC: " + bleItem);
                    } else if (validateModel(bleItem)) {
                        // Else if valid Apple Model string, add to Apple models to target
                        if (!targetModels.contains(bleItem)) {
                            targetModels.add(bleItem);
                        }
                    }
                }
                // Setup Apple mfger Scan filter if 1 or more Apple Models entered
                if (targetModels.size() > 0) {
                    ScanFilter appleFilter = new ScanFilter.Builder().setManufacturerData(0x4C, new byte[] {}).build();
                    filters.add(appleFilter);
                }

                // Start scan if anything to scan
                if (filters.size() > 0) {
                    central.scanForPeripheralsUsingFilters(filters);
                }
            }
        });

        if(result) {
            // Queue up to receive next command
            nextCommand();
        } else {
            Log.e(TAG, "ERROR: Could not enqueue Start Scan command");
        }


       /* handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                //    central.scanForPeripheralsWithServices(new UUID[]{BLP_SERVICE_UUID, HTS_SERVICE_UUID, HRS_SERVICE_UUID, PLX_SERVICE_UUID, WSS_SERVICE_UUID, GLUCOSE_SERVICE_UUID});

                // Prepare to stop after period
                Log.i(TAG, "Starting Scan...");
                // Queue up stop scan command
                stopScan();

                // TODO:  Scan for Apple devices using partial mfger data mask, and allowlisted beacons MAC
                final List<ScanFilter> filters = new ArrayList<>();
                ScanFilter filter = new ScanFilter.Builder().setManufacturerData(0x4C, new byte[] {}).build();
                //ScanFilter filterMac = new ScanFilter.Builder().setDeviceAddress("DD:34:02:05:5F:06").build();
                filters.add(filter);
                central.scanForPeripheralsUsingFilters(filters);

            }
        },BLE_SCAN_COOL_OFF_TIME); // Cool off period before starting next scan

        */
    }

}
