package nl.mprog.estimoteindoorandroid;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import com.estimote.sdk.Beacon;
import com.estimote.sdk.BeaconManager;
import com.estimote.sdk.Region;
import com.estimote.sdk.Utils;
import com.estimote.sdk.utils.L;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@SuppressLint({ "InflateParams", "UseSparseArrays" }) 
public class MapPosActivity extends Activity {
	
  private SharedPreferences preferences;
  private SharedPreferences.Editor editPref;	

  // Bluetooth and Beacon constants.
  private static final String TAG = BeaconListActivity.class.getSimpleName();

  public static final String EXTRAS_BEACON = "extrasBeacon";

  private static final int REQUEST_ENABLE_BT = 1234;
  private static final Region ALL_ESTIMOTE_BEACONS_REGION = new Region("rid", null, null, null);

  private BeaconManager beaconManager;

  // Image of the map, used to draw the Canvas.
  private Bitmap workingBitmap;
  
  // Size of the map/picture in meters.
  private float mapXSize;
  private float mapYSize;
  
  // Size of the map/picture in pixels.
  private int widthPixels;
  private int heightPixels;
  
  private ImageView mapImage;
  
  final ArrayList<Integer> minorValues = new ArrayList<Integer>();
  final HashMap<Integer, ArrayList<Double>> distances = new HashMap<Integer, ArrayList<Double>>();
  
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.map_position);
    getActionBar().setDisplayHomeAsUpEnabled(true);

    preferences = PreferenceManager.getDefaultSharedPreferences(this);
    editPref = preferences.edit();

    // Retrieve the size of the map in meters.
    mapXSize = preferences.getFloat("map_x", 10);
    mapYSize = preferences.getFloat("map_y", 10);
    
    // Display the map size above the map.
    final TextView mapInfo = (TextView) findViewById(R.id.map_size);
    mapInfo.setText("\nThe map size (x, y): \t" + mapXSize + ", " + mapYSize + "\n");
    
    mapImage = (ImageView) findViewById(R.id.map_picture);
    mapImage.setAdjustViewBounds(true);
    
    // Set a scaled version of the map to the ImageView. 
    // NOTE TO SELF: DOESNT WORK PERFECT WITH LONGER PICTURES/BIGGER HEIGHT.
    final Bitmap imageRaw = BitmapFactory.decodeResource(getResources(), R.drawable.raster);
    getScreenSizes(imageRaw);
    Bitmap imageScaled = Bitmap.createScaledBitmap(imageRaw, widthPixels, heightPixels, true);
    mapImage.setImageBitmap(imageScaled);
    
    workingBitmap = Bitmap.createScaledBitmap(imageRaw, widthPixels, heightPixels, true);
    
    // Button to get a dialog to change the map sizes.
    final Button changeMapSettings = (Button) findViewById(R.id.map_settings);
    
    changeMapSettings.setOnClickListener(new View.OnClickListener() {
      @Override
	  public void onClick(View v) {
		// The LayoutInflater is needed for multiple inputs in the dialog.
		LayoutInflater dialogInflater = LayoutInflater.from(MapPosActivity.this);
						
		// Retrieve the minor of the beacon from the Adapter.
		final View textEntryView = dialogInflater.inflate(R.layout.settings_dialog, null);
						
		final EditText inputXPos = (EditText) textEntryView.findViewById(R.id.pos_x);
		final EditText inputYPos = (EditText) textEntryView.findViewById(R.id.pos_y);
					
		// Set the default value of the input to the current to value.
		inputXPos.setText(Float.toString(preferences.getFloat("map_x", 10)), TextView.BufferType.EDITABLE);
		inputYPos.setText(Float.toString(preferences.getFloat("map_y", 10)), TextView.BufferType.EDITABLE);
						
		AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(MapPosActivity.this);
		dialogBuilder.setTitle("Change Settings: ");
		dialogBuilder.setView(textEntryView);
						
		// Set up the OK and Cancel button
		dialogBuilder.setPositiveButton("OK", new DialogInterface.OnClickListener() { 
		  @Override
		  public void onClick(DialogInterface dialog, int i) {
			// Save the changed x and y positions.
			mapXSize = Float.valueOf(inputXPos.getText().toString());
			mapYSize = Float.valueOf(inputYPos.getText().toString());
			editPref.putFloat("map_x", mapXSize); 
			editPref.putFloat("map_y", mapYSize);
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

    // Blue paint to show beacons on the map.
    final Paint paintBlue = new Paint();
    paintBlue.setAntiAlias(true);
    paintBlue.setColor(Color.BLUE);
    paintBlue.setAlpha(125);
    
    // Red paint to show the user on the map.
    final Paint paintRed = new Paint();
    paintRed.setAntiAlias(true);
    paintRed.setColor(Color.RED);
    paintRed.setAlpha(125);
    
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
            getActionBar().setSubtitle("Found beacons(x): " + beaconsList.size());
            
            // Copy the workingBitmap for a clean sheet for the Canvas.
            final Bitmap mutableBitmap = workingBitmap.copy(Bitmap.Config.ARGB_8888, true);
            Canvas canvas = new Canvas(mutableBitmap);
            
	        for (int i = 0; i < beaconsList.size(); i++) {
	          Beacon currentBeacon = beaconsList.get(i);
	          int minorVal = currentBeacon.getMinor();
	          
	          // To prevent new beacons from showing up.
	          if (preferences.getFloat("x" + minorVal, -1) < 0) {
	        	  continue;
	          }
	          
              // Check if the beacon is just found.
              if (!minorValues.contains(minorVal)) {
            	minorValues.add(minorVal);
            	distances.put(minorVal, new ArrayList<Double>());
              }
            	
              // Add the distance to the ArrayList and maintain a certain size.
	          double distance = Utils.computeAccuracy(currentBeacon);
              ArrayList<Double> distanceToBeacon = distances.get(minorVal);
              distanceToBeacon.add(distance);
              if (distanceToBeacon.size() > 20) {
            	distanceToBeacon.remove(0);
              }
              
              // Predefined variables for creating a picture with drawn circles on it.
              float[] locationBeacon = getLocation(currentBeacon);
              
              // Draw a circle with the distance to the user as radius.
              double distanceAverage = average(distances.get(minorVal));
  	          canvas.drawCircle(locationBeacon[0], locationBeacon[1], (float) (distanceAverage/mapXSize) * widthPixels, paintBlue);
  	          
  	          mapInfo.setText("distance: " + distanceAverage);                  
	        }
            
	        mapImage.setImageBitmap(mutableBitmap);
          }
        });
      }
    });
  }

  private double average(ArrayList<Double> distanceArray) {
	double total = 0;
	for (double element : distanceArray) {
	  total += element;
	}
	
	double avg = total / distanceArray.size();
	return avg;
  }
  
  private float[] getLocation(Beacon beacon) {
	int minorVal = beacon.getMinor();
	  
	// Get the position of the beacon.
	float beaconX = preferences.getFloat("x" + minorVal, 0);
	float beaconY = preferences.getFloat("y" + minorVal, 0);

	// Calculate the ratio of the beacon position with the map
	float ratioX = beaconX / mapXSize;
	float ratioY = beaconY / mapYSize;
	  
	if (ratioX > 1) {
	  ratioX = 1;
	}
	if (ratioY > 1) {
	  ratioY = 1;
	}
	  
	// Calculate the number of pixels it has to move.
	float locationPixelsX = (ratioX * widthPixels);
	float locationPixelsY = (ratioY * heightPixels);
	
	if (locationPixelsX == 0) {
		locationPixelsX = 1;
	}
	if (locationPixelsY == 0) {
		locationPixelsY = 1;
	}
	return new float[]{locationPixelsX, locationPixelsY}; 
  }
  
  private void getScreenSizes(Bitmap image_raw) {
  	
    // Retrieve the height of the status bar
    int statusbar_height = getStatusBarHeight();
      
    // Correction for the padding. 2 pixels above and below each ImageView
    int padding_height = 0;
      
    // Retrieve screen size
    DisplayMetrics metrics = new DisplayMetrics();
    getWindowManager().getDefaultDisplay().getMetrics(metrics);
    int height_screen = metrics.heightPixels - statusbar_height - padding_height;
    int width_screen = (int) (metrics.widthPixels * 0.9);  
     
    // Retrieve picture size and calculate ratio
    int height_image = image_raw.getHeight();
    int width_image = image_raw.getWidth();
    float ratio = (float) (height_image) / width_image;
      
    // Scaling depending on the lowest ratio width/height between image and picture.
    if (ratio < (float) (height_screen) / width_screen) {
      widthPixels = width_screen;
      heightPixels = (int) (height_image * (float) (width_screen) / width_image);
    } else {
      widthPixels = (int) (width_image * (float) (height_screen) / height_image);
      heightPixels = height_screen;
    }  
  }
  
  /*
   * Retrieves the height of the status bar. Default value is 0 in case nothing is found.
   */
  private int getStatusBarHeight() {
  	int status_bar_height = 0;
	int resource_id = getResources().getIdentifier("status_bar_height", "dimen", "android");
	if (resource_id > 0) {
	  status_bar_height = getResources().getDimensionPixelSize(resource_id);
	}
	return status_bar_height;
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
	  final Intent intent = new Intent(MapPosActivity.this, HomeActivity.class);
		
	  try {
		beaconManager.stopRanging(ALL_ESTIMOTE_BEACONS_REGION);
	  } catch (RemoteException e) {
		Log.d(TAG, "Error while stopping ranging", e);
	  }
		
	  editPref.putBoolean("intent_stop", false);
	  editPref.commit();
	  
      startActivity(intent);
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
        getActionBar().setSubtitle("Bluetooth not enabled");
      }
    }
    super.onActivityResult(requestCode, resultCode, data);
  }

  private void connectToService() {
	// Retrieve the beacons. 
    getActionBar().setSubtitle("Scanning...");
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
