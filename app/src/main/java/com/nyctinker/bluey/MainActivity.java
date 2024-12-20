package com.nyctinker.bluey;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONException;

import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class MainActivity extends AppCompatActivity {

    private RecyclerView bleItemRV;
    private EditText newMACTextField;
    private Spinner newAppleSpinnerDropdown;
    private Button newMACAddButton;
    private Button newIOSAddButton;
    private ArrayList<String> bleItemList;
    private BLEItemRVAdapter bleItemRVAdapter;
    private FloatingActionButton fabServiceControl;
    private boolean isServiceRunning     = false;
    private boolean isServiceRequested = false;
    private boolean isScanning = false;

    protected List<String> foundDevices = new ArrayList<>();
    public static final String TAG = "MainActivity";
    private static final int REQUEST_FOREGROUND_PERMISSIONS_CODE = 1;
    private static final int REQUEST_BACKGROUND_PERMISSIONS_CODE = 2; //  If your app targets SDK 30 or higher and requests location permissions, you must request background location permissions separately from foreground location permissions.



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        registerReceiver(locationServiceStateReceiver, new IntentFilter((LocationManager.MODE_CHANGED_ACTION)));
       // registerReceiver(beaconReceiver, new IntentFilter(BluetoothHandler.MEASUREMENT_BEACON));
        // Init elements
        bleItemRV = findViewById(R.id.idBLERVItems);
        newMACTextField = findViewById(R.id.idEdtAdd);
        newMACAddButton = findViewById(R.id.idBtnAdd);
        newAppleSpinnerDropdown = findViewById(R.id.idAppleModelSpinner);
        fabServiceControl = findViewById(R.id.fabServiceControl);

        // Create an ArrayAdapter using the string array and a default spinner layout
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.apple_model_array, android.R.layout.simple_spinner_item);
        // Specify the layout to use when the list of choices appears
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        // Apply the adapter to the spinner
        newAppleSpinnerDropdown.setAdapter(adapter);


        newIOSAddButton = findViewById(R.id.idBtnIOSAdd);
        // Load any last saved list of ble items to scan for
        bleItemList = getStringArrayPreference(getApplicationContext(), "ble_scan_list");


        // Tie BLEItemlist to adapter
        bleItemRVAdapter = new BLEItemRVAdapter(bleItemList, new BLEItemRVAdapter.OnItemLongClickListener() {
            @Override public void onItemLongClick(String item) {
                Toast.makeText(getApplicationContext(), "Deleting: " + item, Toast.LENGTH_SHORT).show();
                removeItem(item);
            }
        });



        // Set adapter to our recycler view
        bleItemRV.setAdapter(bleItemRVAdapter);

        // Add click listener for our add buttons
        newMACAddButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addItem(newMACTextField.getText().toString());
            }
        });
        newIOSAddButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addItem(newAppleSpinnerDropdown.getSelectedItem().toString());
            }
        });

        fabServiceControl.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) { // Android 6.0 and up requires runtime perms
                    // Check if permissions are already granted
                    if (checkPermissions()) {
                        // Permissions granted, proceed with starting/stopping the service
                        handleServiceControl();
                    } else {
                        // Permissions not granted, request them
                        requestPermissions();
                        isServiceRequested = true; // Set flag to indicate service request
                    }
                } else {
                    // For older SDK versions, permissions are granted at install time
                    handleServiceControl();
                }
        }});

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {
            case R.id.action_settings:
                // Settings item was selected
                // Open settings activity.
                Intent i = new Intent(MainActivity.this, SettingsActivity.class);
                startActivity(i);
            default:
                return super.onOptionsItemSelected(item);
        }
    }
    /*
       Convenience function to add item to RV
    */
    private void addItem(String item) {

        if (!item.isEmpty()) {

            bleItemList.add(item);
            // Notify adapter
            bleItemRVAdapter.notifyDataSetChanged();

            // Save to preferences
            setStringArrayPreference(getApplicationContext(), "ble_scan_list", bleItemList);

            // Update service with latest list
            updateBLEFilterList();


        }
    }

    /*
       Convenience function to add item to RV
    */
    private void removeItem(String item) {

        if (!item.isEmpty()) {

            bleItemList.remove(item);
            // Notify adapter
            bleItemRVAdapter.notifyDataSetChanged();

            // Save to preferences
            setStringArrayPreference(getApplicationContext(), "ble_scan_list", bleItemList);

            // Update service with latest list
            updateBLEFilterList();


        }
    }

    /*
     * Method to send the latest filterlist to Foreground service
     */
    private void updateBLEFilterList() {
        // Update service with latest list if it's running already
        if (isServiceRunning) {
            Intent intent = new Intent(this, BluetoothHandler.class);
            intent.setAction(BluetoothHandler.ACTION_UPDATE_FOREGROUND_SERVICE);
            intent.putStringArrayListExtra("BLEFilterList", bleItemList);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(locationServiceStateReceiver);
       // unregisterReceiver(beaconReceiver);

    }

    /*
    * Convenience method to serialize array to string in Preferences
     */
    public static void setStringArrayPreference(Context context, String key, ArrayList<String> values) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = prefs.edit();
        JSONArray a = new JSONArray();
        for (int i = 0; i < values.size(); i++) {
            a.put(values.get(i));
        }
        if (!values.isEmpty()) {
            editor.putString(key, a.toString());
        } else {
            editor.putString(key, null);
        }
        editor.commit();
    }

    /*
     * Convenience method to deserialize array to string from Preferences
     */
    public static ArrayList<String> getStringArrayPreference(Context context, String key) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String json = prefs.getString(key, null);
        ArrayList<String> devices = new ArrayList<String>();
        if (json != null) {
            try {
                JSONArray a = new JSONArray(json);
                for (int i = 0; i < a.length(); i++) {
                    String url = a.optString(i);
                    devices.add(url);
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return devices;
    }



    @Override
    protected void onResume() {
        super.onResume();


    }

   /* private void checkBlueToothHardware() {
        if (getBluetoothManager().getAdapter() != null) {
            if (!isBluetoothEnabled()) {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    return;
                }
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            } else {
                checkPermissions();
            }
        } else {
            Log.e(TAG, "This device has no Bluetooth hardware");
        }
    }*/

    private boolean isBluetoothEnabled() {
        BluetoothAdapter bluetoothAdapter = getBluetoothManager().getAdapter();
        if(bluetoothAdapter == null) return false;

        return bluetoothAdapter.isEnabled();
    }



    @NotNull
    private BluetoothManager getBluetoothManager() {
        return Objects.requireNonNull((BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE),"cannot get BluetoothManager");
    }

    private final BroadcastReceiver locationServiceStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action != null && action.equals(LocationManager.MODE_CHANGED_ACTION)) {
                boolean isEnabled = areLocationServicesEnabled();
                Log.d(TAG, "Location service state changed to:" + isEnabled);
               // checkPermissions();
            }
        }
    };



 /*   private BluetoothPeripheral getPeripheral(String peripheralAddress) {
        BluetoothCentralManager central = BluetoothHandler.getInstance(getApplicationContext()).central;  // TODO: replace
        return central.getPeripheral(peripheralAddress);
    }*/

    public List<String> getFoundDevices() {
        return foundDevices;
    }

    private boolean checkPermissions() {
        String[] requiredPermissions = getRequiredPermissions();
        for (String permission : requiredPermissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false; // If any permission is not granted, return false
            }
        }
        return true; // If all permissions are granted, return true
    }

    private String[] getMissingPermissions(String[] requiredPermissions) {
        List<String> missingPermissions = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            for (String requiredPermission : requiredPermissions) {
                if (getApplicationContext().checkSelfPermission(requiredPermission) != PackageManager.PERMISSION_GRANTED) {
                    missingPermissions.add(requiredPermission);
                }
            }
        }
        return missingPermissions.toArray(new String[0]);
    }

    private String[] getRequiredPermissions() {
        int targetSdkVersion = getApplicationInfo().targetSdkVersion;
        // Android 12+ requires ACCESS_BACKGROUND_LOCATION for background BLE scanning
        // However, we'll request it separately after foreground permissions are granted
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && targetSdkVersion >= Build.VERSION_CODES.S) {
            return new String[]{Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.ACCESS_FINE_LOCATION};
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && targetSdkVersion >= Build.VERSION_CODES.Q) {
            return new String[]{Manifest.permission.ACCESS_FINE_LOCATION};
        } else return new String[]{Manifest.permission.ACCESS_COARSE_LOCATION};
    }



    private boolean areLocationServicesEnabled() {
        LocationManager locationManager = (LocationManager) getApplicationContext().getSystemService(Context.LOCATION_SERVICE);
        if (locationManager == null) {
            Log.e(TAG, "could not get location manager");
            return false;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return locationManager.isLocationEnabled();
        } else {
            boolean isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
            boolean isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);

            return isGpsEnabled || isNetworkEnabled;
        }
    }

    private boolean checkLocationServices() {
        if (!areLocationServicesEnabled()) {
            new AlertDialog.Builder(MainActivity.this)
                    .setTitle("Location services are not enabled")
                    .setMessage("Scanning for Bluetooth peripherals requires locations services to be enabled.") // Want to enable?
                    .setPositiveButton("Enable", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialogInterface, int i) {
                            dialogInterface.cancel();
                            startActivity(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                        }
                    })
                    .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            // if this button is clicked, just close
                            // the dialog box and do nothing
                            dialog.cancel();
                        }
                    })
                    .create()
                    .show();
            return false;
        } else {
            return true;
        }
    }

    private void handleServiceControl() {
        Intent intent = null;
        if (checkLocationServices()) {
            intent = new Intent(this, BluetoothHandler.class);
            if (!isServiceRunning) {
                // Prep the intent to start the SErvice of BT handler
                intent.setAction(BluetoothHandler.ACTION_START_FOREGROUND_SERVICE);
                intent.putStringArrayListExtra("BLEFilterList", bleItemList);
                // set icon and flags
                fabServiceControl.setImageResource(R.drawable.baseline_pause_circle_24);
                isServiceRunning = true;
                isScanning = true;
            } else if (!isScanning) {
                intent.setAction(BluetoothHandler.ACTION_RESUME_SCAN);
                fabServiceControl.setImageResource(R.drawable.baseline_pause_circle_24);
                isScanning = true;
            } else {  // else if scanning, pause service
                intent.setAction(BluetoothHandler.ACTION_PAUSE_SCAN);
                fabServiceControl.setImageResource(R.drawable.baseline_play_circle_24);
                isScanning = false;
            }


        }

        if (intent != null) {
            //Send the intent to the service
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
        }

       /*
       if (!isServiceRunning) {

            // Check BT hardware is enabled
            if (isBluetoothEnabled()) {
                // Request permissions
                requestPermissions(getRequiredPermissions(), ACCESS_LOCATION_REQUEST);
                // Set flag
                isServiceRequested  = true;
            } else {
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Bluetooth hardware is required")
                        .setMessage("Please enable Bluetooth hardware")
                        .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialogInterface, int i) {
                                dialogInterface.cancel();

                            }
                        })
                        .create()
                        .show();

            }


        } else {
            // Stop service
            Intent intent = new Intent(MainActivity.this, BluetoothHandler.class);
            intent.setAction(BluetoothHandler.ACTION_STOP_FOREGROUND_SERVICE);

            stopService(intent);
            fabServiceControl.setImageResource(R.drawable.baseline_play_circle_24);
            isServiceRunning = false;

        }
        */
    }

    private void requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {  // SDK 23 and up require runtime permission granting
            boolean showRationale = false;
            for (String permission : getRequiredPermissions()) {
                if (ActivityCompat.shouldShowRequestPermissionRationale(this, permission)) {
                    showRationale = true;
                    break; // If rationale is needed for any permission, break the loop
                }
            }
            if (showRationale) {
                // Show rationale dialog
                new AlertDialog.Builder(this)
                        .setTitle(" Permission Required")
                        .setMessage("This app needs access to Bluetooth and location to scan for Bluetooth devices.")
                        .setPositiveButton("OK", (dialog, which) -> {
                            // Request permissions
                            ActivityCompat.requestPermissions(MainActivity.this,
                                    getRequiredPermissions(),
                                    REQUEST_FOREGROUND_PERMISSIONS_CODE);
                        })
                        .setNegativeButton("Cancel", (dialog, which) -> {
                            // Handle permission denial
                            // ...
                        })
                        .show();
            } else {
                // Request permissions directly
                ActivityCompat.requestPermissions(this,
                        getRequiredPermissions(),
                        REQUEST_FOREGROUND_PERMISSIONS_CODE);
            }
        } else {
            // Request permissions
            ActivityCompat.requestPermissions(this,
                    getRequiredPermissions(),
                    REQUEST_FOREGROUND_PERMISSIONS_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        switch (requestCode) {
            case REQUEST_FOREGROUND_PERMISSIONS_CODE: {

                // Check if all permission were granted
                boolean allForegroundPermissionsGranted = true;
                for (int result : grantResults) {
                    if (result != PackageManager.PERMISSION_GRANTED) {
                        allForegroundPermissionsGranted = false;
                        break;
                    }

                }

                // Foreground permissions granted, now request background permission if needed in SDK 34 and up

                if ((allForegroundPermissionsGranted)) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                        // Request background location permission
                        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION}, REQUEST_BACKGROUND_PERMISSIONS_CODE);
                    } else {
                        // Background permission not needed or already granted, proceed with service
                        handleServiceControl();
                        isServiceRequested = false;
                    }
                } else {
                    // Handle foreground permission denial
                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle("Bluetooth and Location permissions (All the Time) are required for scanning Bluetooth peripherals")
                            .setMessage("Please grant permissions")
                            .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialogInterface, int i) {
                                    dialogInterface.cancel();
                                    //
                                }
                            })
                            .create()
                            .show();
                }
                break;
            }
            case REQUEST_BACKGROUND_PERMISSIONS_CODE: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Background permission granted, proceed with service
                    handleServiceControl();
                    isServiceRequested = false;
                } else {
                    // Handle background permission denial
                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle("Background locations permissions are required for scanning Bluetooth peripherals")
                            .setMessage("Please grant permissions")
                            .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialogInterface, int i) {
                                    dialogInterface.cancel();
                                    //
                                }
                            })
                            .create()
                            .show();
                }
                break;
            }
        }
    }
}