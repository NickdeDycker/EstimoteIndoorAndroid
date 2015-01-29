package nl.mprog.estimoteindoorandroid;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;

import com.estimote.sdk.Beacon;
import com.estimote.sdk.BeaconManager;
import com.estimote.sdk.Region;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BeaconListActivity extends Activity {	
  // Bluetooth and Beacon constants. 
  private static final int REQUEST_ENABLE_BT = 1234;
  private static final Region ALL_ESTIMOTE_BEACONS_REGION = new Region("rid", null, null, null);

  private BeaconManager beaconManager;
  private BeaconListAdapter listViewAdapter;

  private SharedPreferences preferences;
  private SharedPreferences.Editor editPref;
  
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.main);
    getActionBar().setDisplayHomeAsUpEnabled(true);

    preferences = PreferenceManager.getDefaultSharedPreferences(this);
    editPref = preferences.edit();
    
    // Set the custom ListView Adapter
    listViewAdapter = new BeaconListAdapter(this);
    ListView beaconListView = (ListView) findViewById(R.id.device_list);
    beaconListView.setAdapter(listViewAdapter);
    beaconListView.setClickable(true);

    // Set a dialogAlerter to change values of a beacon.
    beaconListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
      public void onItemClick(AdapterView<?> parentView, View childView, int position, long id) {
        Intent intentSingleBeacon = new Intent(BeaconListActivity.this, SingleBeaconActivity.class);
        intentSingleBeacon.putExtra("getBeacon", listViewAdapter.getItem(position));
        startActivity(intentSingleBeacon);
      }
    });

    // Configure BeaconManager.
    beaconManager = new BeaconManager(this);
    beaconManager.setRangingListener(new BeaconManager.RangingListener() {
      @Override
      public void onBeaconsDiscovered(Region region, final List<Beacon> beaconsList) {
        runOnUiThread(new Runnable() {
          @Override
          public void run() {
            // The beacons are sorted based on distance from the user.
            getActionBar().setSubtitle("Found beacons: " + beaconsList.size());
            List<Beacon> sortedBeaconList = sortBeaconsOnMinor(beaconsList);
            // Refresh the listViewAdapter with new beacons.
            listViewAdapter.replaceWith(sortedBeaconList);
          }
        });
      }
    });
  }

  /*
   * Sort the beacons based on their minor. 
   */
  private List<Beacon> sortBeaconsOnMinor(List<Beacon> beaconList) {
	  Map<Integer, Beacon> map1 = new HashMap<Integer, Beacon>();
	  List<Integer> minorValueList = new ArrayList<Integer>();
	  for (int i = 0; i < beaconList.size(); i++) {
		  minorValueList.add(beaconList.get(i).getMinor());
		  map1.put(minorValueList.get(i), beaconList.get(i));
	  }
	  // Sort the list with minor values and rebuild the beacon list using the HashMap.
	  Collections.sort(minorValueList);
	  List<Beacon> sortedBeaconList = new ArrayList<Beacon>();	
	  for (int i = 0; i < beaconList.size(); i++) {
		  sortedBeaconList.add(map1.get(minorValueList.get(i)));
	  }
	  return sortedBeaconList;
  }
  
  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.scan_menu, menu);
    MenuItem refreshItem = menu.findItem(R.id.refresh);
    refreshItem.setActionView(R.layout.actionbar_indeterminate_progress);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
	// Set the return button.
	if (item.getItemId() == android.R.id.home) {
	  final Intent intentHome = new Intent(BeaconListActivity.this, HomeActivity.class);
		
	  // Stop ranging or searching for beacons.
	  try {
		beaconManager.stopRanging(ALL_ESTIMOTE_BEACONS_REGION);
	  } catch (RemoteException e) {
	  }
		
	  // A variable to trigger onStop or not.
	  editPref.putBoolean("intent_stop", false);
	  editPref.commit();
	  
	  startActivity(intentHome);
	  return true;
	}
    return super.onOptionsItemSelected(item);
  }

  @Override
  protected void onDestroy() {
    beaconManager.disconnect();
    super.onDestroy();
  }

  @Override
  protected void onStart() {
    super.onStart();
    // Check if device supports Bluetooth Low Energy.
    if (!beaconManager.hasBluetooth()) {
      getActionBar().setSubtitle("Device does not have Bluetooth Low Energy");
      return;
    }

    // If Bluetooth is not enabled, let user enable it.
    if (!beaconManager.isBluetoothEnabled()) {
      Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
      startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
    } else {
      connectToService();
    }
  }

  @Override
  protected void onStop() {
	// Quit asking for results, unless a intent has been started.
	Boolean intentStop = preferences.getBoolean("intent_stop", true);
	if (intentStop) {
	  try {
	    beaconManager.stopRanging(ALL_ESTIMOTE_BEACONS_REGION);
	  } catch (RemoteException e) {
	  }
	}
	editPref.remove("intent_stop");
	editPref.commit();
    super.onStop();
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
	// Request Bluetooth.
    if (requestCode == REQUEST_ENABLE_BT) {
      if (resultCode == Activity.RESULT_OK) {
        connectToService();
      } else {
        getActionBar().setSubtitle("Bluetooth not enabled");
      }
    }
    super.onActivityResult(requestCode, resultCode, data);
  }

  private void connectToService() {
	// Retrieve the beacons. 
    getActionBar().setSubtitle("Scanning...");
    listViewAdapter.replaceWith(Collections.<Beacon>emptyList());
    beaconManager.connect(new BeaconManager.ServiceReadyCallback() {
      @Override
      public void onServiceReady() {
        try {
          beaconManager.startRanging(ALL_ESTIMOTE_BEACONS_REGION);
        } catch (RemoteException e) {
          getActionBar().setSubtitle("Can't start ranging");
        }
      }
    });
  }

}