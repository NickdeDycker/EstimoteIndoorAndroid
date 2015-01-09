package nl.mprog.estimoteindoorandroid;

/* on Destroy - disconnect BeaconManager
 * onStart - Check if bluetooth enabled, else ask the user to enable it. Connect to service. 
 * onStop - Stop ranging
 * 
 * 
 */

/* onDestroy - check
 * onStop - Check (catch exception)
 * onStart - Check (Bluetooth, start ranging)
 * 
 */

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import com.estimote.sdk.Beacon;
import com.estimote.sdk.BeaconManager;
import com.estimote.sdk.Region;
import com.estimote.sdk.utils.L;

import java.util.Collections;
import java.util.List;

public class BeaconListActivity extends Activity {
	
  private SharedPreferences preferences;
  private SharedPreferences.Editor editPref;	
  
  // Bluetooth and Beacon constants.
  private static final String TAG = BeaconListActivity.class.getSimpleName();

  public static final String EXTRAS_BEACON = "extrasBeacon";

  private static final int REQUEST_ENABLE_BT = 1234;
  private static final Region ALL_ESTIMOTE_BEACONS_REGION = new Region("rid", null, null, null);

  private BeaconManager beaconManager;
  private BeaconListAdapter listViewAdapter;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.main);
    getActionBar().setDisplayHomeAsUpEnabled(true);

    preferences = PreferenceManager.getDefaultSharedPreferences(this);
    editPref = preferences.edit();
    
    // Set the custom ListView Adapter
    listViewAdapter = new BeaconListAdapter(this);
    ListView list = (ListView) findViewById(R.id.device_list);
    list.setAdapter(listViewAdapter);
    list.setClickable(true);

    list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
      public void onItemClick(AdapterView<?> parentView, View childView, int position, long id) {
			
	    // The LayoutInflater is needed for multiple inputs in the dialog.
        LayoutInflater dialogInflater = LayoutInflater.from(BeaconListActivity.this);
			
	    // Retrieve the minor of the beacon from the Adapter.
	    final int minorValue = listViewAdapter.beacons.get(position).getMinor();
	    final View textEntryView = dialogInflater.inflate(R.layout.settings_dialog, null);
				
	    final EditText inputXPos = (EditText) textEntryView.findViewById(R.id.pos_x);
	    final EditText inputYPos = (EditText) textEntryView.findViewById(R.id.pos_y);
			
	    // Set the default value of the input to the current to value.
	    inputXPos.setText(Float.toString(preferences.getFloat("x"+minorValue, 0)), TextView.BufferType.EDITABLE);
	    inputYPos.setText(Float.toString(preferences.getFloat("y"+minorValue, 0)), TextView.BufferType.EDITABLE);
		
	    AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(BeaconListActivity.this);
	    dialogBuilder.setTitle("Change Settings: ");
	    dialogBuilder.setView(textEntryView);
				
	    // Set up the OK and Cancel button
	    dialogBuilder.setPositiveButton("OK", new DialogInterface.OnClickListener() { 
		  @Override
		  public void onClick(DialogInterface dialog, int i) {
		    // Save the changed x and y positions.
		    editPref.putFloat("x"+minorValue, Float.valueOf(inputXPos.getText().toString())); 
	        editPref.putFloat("y"+minorValue, Float.valueOf(inputYPos.getText().toString()));
	        editPref.commit();
		  }
	    });
	    dialogBuilder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
	      @Override
	      public void onClick(DialogInterface dialog, int i) {
	    	dialog.cancel();
	      }
	    });
	
	    dialogBuilder.show();
      }
    });
    
    // Configure verbose debug logging.
    L.enableDebugLogging(true);

    // Configure BeaconManager.
    beaconManager = new BeaconManager(this);
    beaconManager.setRangingListener(new BeaconManager.RangingListener() {
      @Override
      public void onBeaconsDiscovered(Region region, final List<Beacon> beaconsList) {
        // Note that results are not delivered on UI thread.
        runOnUiThread(new Runnable() {
          @Override
          public void run() {
            // Note that beacons reported here are already sorted by estimated
            // distance between device and beacon.
            getActionBar().setSubtitle("Found beacons: " + beaconsList.size());
            listViewAdapter.replaceWith(beaconsList);
          }
        });
      }
    });
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
	if (item.getItemId() == android.R.id.home) {
	  final Intent intent = new Intent(BeaconListActivity.this, HomeActivity.class);
	  BeaconListActivity.this.finish();
	  // A timer
	  final Handler handler = new Handler();
	  handler.postDelayed(new Runnable() {
	    @Override
	    public void run() {
	      startActivity(intent);
	    }
	  }, 100);  
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
	// Quit asking for results.
    try {
      beaconManager.stopRanging(ALL_ESTIMOTE_BEACONS_REGION);
    } catch (RemoteException e) {
      Log.d(TAG, "Error while stopping ranging", e);
    }

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
          Log.e(TAG, "Cannot start ranging", e);
        }
      }
    });
  }

}