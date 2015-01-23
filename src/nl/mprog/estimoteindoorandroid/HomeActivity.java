package nl.mprog.estimoteindoorandroid;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Parcel;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import com.estimote.sdk.Beacon;
import com.estimote.sdk.BeaconManager;
import com.estimote.sdk.Region;
import com.estimote.sdk.Utils;
import com.estimote.sdk.utils.L;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;

@SuppressLint("UseSparseArrays") 
public class HomeActivity extends Activity {

  // Bluetooth and beacon constants.
  private static final String TAG = HomeActivity.class.getSimpleName();

  public static final String EXTRAS_BEACON = "extrasBeacon";

  private static final int REQUEST_ENABLE_BT = 1234;
  private static final Region ALL_ESTIMOTE_BEACONS_REGION = new Region("rid", null, null, null);

  private Button beaconlistButton;
  
  private ArrayList<Integer> minorValues = new ArrayList<Integer>();
  private HashMap<Integer, ArrayList<Double>> distances = new HashMap<Integer, ArrayList<Double>>();
  
  private BeaconManager beaconManager;

  // TextView to show all the distances of the beacons.
  private TextView results;
  
  private SharedPreferences preferences;
  private SharedPreferences.Editor editPref;
  
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.home_screen);

    preferences = PreferenceManager.getDefaultSharedPreferences(this);
    editPref = preferences.edit();
    
    results = (TextView) findViewById(R.id.show_result);
    
    beaconlistButton = (Button) findViewById(R.id.beacon_list);
    
    // A button to BeaconListActivity, to get a list of all beacons.
    beaconlistButton.setOnClickListener(new View.OnClickListener() {
	  @Override
	  public void onClick(View v) {
		final Intent beaconListIntent = new Intent(HomeActivity.this, BeaconListActivity.class);
		
		// Stop ranging/searching for beacons.
		try {
		  beaconManager.stopRanging(ALL_ESTIMOTE_BEACONS_REGION);
		} catch (RemoteException e) {
		  Log.d(TAG, "Error while stopping ranging", e);
		}
		
		// A variable to trigger onStop or not.
		editPref.putBoolean("intent_stop", false);
		editPref.commit();

		startActivity(beaconListIntent);
	  }
    });

    final Button mapPosButton = (Button) findViewById(R.id.map_position);
    
    // A button to MapPosActivity, to see your location on a map.
    mapPosButton.setOnClickListener(new View.OnClickListener() {
	  @Override
	  public void onClick(View v) {
	    final Intent mapPosIntent = new Intent(HomeActivity.this, MapPosActivity.class);
		
	    // Stop ranging/searching for beacons.
		try {
		  beaconManager.stopRanging(ALL_ESTIMOTE_BEACONS_REGION);
		} catch (RemoteException e) {
		  Log.d(TAG, "Error while stopping ranging", e);
		}
		
		// A variable to trigger onStop or not.
		editPref.putBoolean("intent_stop", false);
		editPref.commit();
		
        startActivity(mapPosIntent);
	  }
    });
    
    final Button measureButton = (Button) findViewById(R.id.single_measure);
    
    // The button will calculate the average distance to each beacon and show this in a TextView.
    measureButton.setOnClickListener(new View.OnClickListener() {
	  @Override
	  public void onClick(View v) {
	    // Clear the TextView.
	    results.setText("");
	     
	    // Calculate the average for each beacon and add this to the TextView.
	    for (int i = 0; i < minorValues.size(); i ++) {
	      int minorVal = minorValues.get(i);
	      ArrayList<Double> dist = distances.get(minorVal);
	      double total = 0;
	      for (double element : dist) {
	    	total += element;
	      }
	      double avg = total / dist.size();
	      results.append("Minor: \t" + minorVal + "\t\t" + "  dist: \t" + avg + "\n");
	      
	    };
	    //results.append("\n" + distances.get(33028));
	  }
    });
    
    // Configure verbose debug logging.
    L.enableDebugLogging(true);

    // Configure BeaconManager.
    beaconManager = new BeaconManager(this);
    beaconManager.setForegroundScanPeriod(100, 0);
    beaconManager.setRangingListener(new BeaconManager.RangingListener() {
      @Override
      public void onBeaconsDiscovered(Region region, final List<Beacon> beacons) {
        // Note that results are not delivered on UI thread.
        runOnUiThread(new Runnable() {
          @Override
          public void run() {
            // Note that beacons reported here are already sorted by estimated
            // distance between device and beacon.
            beaconlistButton.setText("List of beacons (Found: " + beacons.size() + ")");
            results.setText("");
            // Add distances to the ArrayLists.
            for (int i = 0; i < beacons.size(); i++) {
              Beacon currentBeacon = beacons.get(i);
              Double distance = Utils.computeAccuracy(currentBeacon);
              int minor = currentBeacon.getMinor();
              if (minor == 33028) {
	              Parcel p1 = Parcel.obtain();
	              p1.writeString("This is a parcel");
	              currentBeacon.writeToParcel(p1 , 1);
              }
              // Check if the beacon is just found.
              if (!distances.containsKey(minor)) {
            	minorValues.add(minor);
            	distances.put(minor, new ArrayList<Double>());
              }
            	
              // Add the distance to the ArrayList and maintain a certain size.
              ArrayList<Double> distanceToBeacon = distances.get(minor);
              distanceToBeacon.add(distance);
              
              if (distanceToBeacon.size() > 100) {
            	distanceToBeacon.remove(0);
              }
              distances.put(minor, distanceToBeacon);
            }
          }
        });
      }
    });
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
    	beaconlistButton.setText("List of beacons (No Bluetooth)");
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
	// Quit asking for results.
	Boolean intentStop = preferences.getBoolean("intent_stop", true);
	if (intentStop) {
	  try {
	    beaconManager.stopRanging(ALL_ESTIMOTE_BEACONS_REGION);
	  } catch (RemoteException e) {
	    Log.d(TAG, "Error while stopping ranging", e);
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
        beaconlistButton.setText("List of beacons (No Bluetooth)");
      }
    }
    super.onActivityResult(requestCode, resultCode, data);
  }

  private void connectToService() {
	// Retrieve the beacons. 
	beaconlistButton.setText("List of beacons (Scanning...)");
    beaconManager.connect(new BeaconManager.ServiceReadyCallback() {
      @Override
      public void onServiceReady() {
        try {
          beaconManager.startRanging(ALL_ESTIMOTE_BEACONS_REGION);
        } catch (RemoteException e) {
          beaconlistButton.setText("List of beacons (Error)");
        }
      }
    });
  }

}