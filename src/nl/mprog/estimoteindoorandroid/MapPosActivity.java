package nl.mprog.estimoteindoorandroid;

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
import android.os.Handler;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import com.estimote.sdk.Beacon;
import com.estimote.sdk.BeaconManager;
import com.estimote.sdk.Region;
import com.estimote.sdk.utils.L;

import java.util.ArrayList;
import java.util.List;

public class MapPosActivity extends Activity {
	
  private SharedPreferences preferences;
  private SharedPreferences.Editor editPref;	
  
  // Bluetooth and Beacon constants.
  private static final String TAG = BeaconListActivity.class.getSimpleName();

  public static final String EXTRAS_BEACON = "extrasBeacon";

  private static final int REQUEST_ENABLE_BT = 1234;
  private static final Region ALL_ESTIMOTE_BEACONS_REGION = new Region("rid", null, null, null);

  private BeaconManager beaconManager;

  private int widthPixels;
  private int heightPixels;
  private ImageView mapImage;
  
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.map_position);
    getActionBar().setDisplayHomeAsUpEnabled(true);

    preferences = PreferenceManager.getDefaultSharedPreferences(this);
    editPref = preferences.edit();

    // Retrieve the size of the map/raster/picture.
    final float mapXSize = preferences.getFloat("map_x", 10);
    final float mapYSize = preferences.getFloat("map_y", 10);
    
    // Display the map size above the map.
    final TextView mapInfo = (TextView) findViewById(R.id.map_size);
    mapInfo.setText("\nThe map size (x, y): \t" + mapXSize + ", " + mapYSize + "\n");
    
    mapImage = (ImageView) findViewById(R.id.map_picture);
    mapImage.setAdjustViewBounds(true);
    
    // Set a scaled version of the map to the ImageView. 
    // DOESNT WORK PERFECT WITH LONGER PICTURES/BIGGER HEIGHT.
    final Bitmap imageRaw = BitmapFactory.decodeResource(getResources(), R.drawable.raster);
    getScreenSizes(imageRaw);
    Bitmap imageScaled = Bitmap.createScaledBitmap(imageRaw, widthPixels, heightPixels, true);
    mapImage.setImageBitmap(imageScaled);
    
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
			editPref.putFloat("map_x", Float.valueOf(inputXPos.getText().toString())); 
			editPref.putFloat("map_y", Float.valueOf(inputYPos.getText().toString()));
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
    
    // Red paint to show the user on the map.
    final Paint paintRed = new Paint();
    paintRed.setAntiAlias(true);
    paintRed.setColor(Color.RED);
    
    // Predefined variables for creating a picture with drawn circles on it.
    Bitmap workingBitmap = Bitmap.createScaledBitmap(imageRaw, widthPixels, heightPixels, true);
    final Bitmap mutableBitmap = workingBitmap.copy(Bitmap.Config.ARGB_8888, true);
    
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
            
            // Draw circles for each beacon.
	        Canvas canvas = new Canvas(mutableBitmap);
	        for (int i = 0; i < beaconsList.size(); i++) {
	          float[] locationBeacon = getLocation(beaconsList.get(i));
	          canvas.drawCircle(locationBeacon[0], locationBeacon[1], 10, paintBlue);
	        }
	        /*
	        float pictureX = locationBeacon[0] / (preferences.getFloat("x" + beaconsList.get(0).getMinor(), 0) / mapXSize);
	        float pictureY = locationBeacon[0] / (preferences.getFloat("y" + beaconsList.get(0).getMinor(), 0) / mapYSize);
	        mapInfo.setText("Beacon pos: " + locationBeacon[0] + " and " + locationBeacon[1] + " (" + 
	        	preferences.getFloat("x" + beaconsList.get(0).getMinor(), 0) + ", " + preferences.getFloat("y" + beaconsList.get(0).getMinor(), 0) +
	        	"\nMap Size: " + mapXSize + " and " + mapYSize + "\nPicture dimensions are: " + pictureX + " and " + pictureY);
			*/
            
            // Method about measuring position of user if 3 beacons are near
	        mapImage.setImageBitmap(mutableBitmap);
          }
        });
      }
    });
  }

  private float[] getLocation(Beacon beacon) {
	int minorVal = beacon.getMinor();
	  
	// Get the position of the beacon.
	float beaconX = preferences.getFloat("x" + minorVal, 0);
	float beaconY = preferences.getFloat("y" + minorVal, 0);
	 
	// Get the sizes of the map
	float mapXSize = preferences.getFloat("map_x", 10);
	float mapYSize = preferences.getFloat("map_y", 10);
	  
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
	  MapPosActivity.this.finish();
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
