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
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.util.DisplayMetrics;
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

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
 
public class MapPosActivity extends Activity {
  private SharedPreferences preferences;
  private SharedPreferences.Editor editPref;	

  // Bluetooth and Beacon constants.
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
  private Boolean reset = false;
  private ImageView mapImage;
  
  float[] userPos = new float[]{0, 0, 0};
  int measurements = 0; 
  
  final ArrayList<Integer> minorValues = new ArrayList<Integer>();
  HashMap<Integer, ArrayList<Double>> distances = new HashMap<Integer, ArrayList<Double>>();
  HashMap<Integer, Double> distanceAvg = new HashMap<Integer, Double>();
  
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
    final Bitmap imageRaw = BitmapFactory.decodeResource(getResources(), R.drawable.raster);
    getScreenSizes(imageRaw);
    Bitmap imageScaled = Bitmap.createScaledBitmap(imageRaw, widthPixels, heightPixels, true);
    mapImage.setImageBitmap(imageScaled);
    
    workingBitmap = Bitmap.createScaledBitmap(imageRaw, widthPixels, heightPixels, true);
    
    // Reset the distances and measurement counter to default within the scanning method.
    final Button resetButton = (Button) findViewById(R.id.reset_pos);
    resetButton.setOnClickListener(new View.OnClickListener() {
		@Override
		public void onClick(View v) {
			reset = true;			
		}
	});
    
    // Button to get a dialog to change the map sizes.
    final Button changeMapSettings = (Button) findViewById(R.id.map_settings);
    
    changeMapSettings.setOnClickListener(new View.OnClickListener() {
      @Override
	  public void onClick(View v) {
		// The LayoutInflater is needed for multiple inputs in the dialog.
		LayoutInflater dialogInflater = LayoutInflater.from(MapPosActivity.this);
						
		// Retrieve the minor of the beacon from the Adapter.
		final View textEntryView = dialogInflater.inflate(R.layout.settings_dialog_map, null);
						
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

    // In order to show the distances with less decimals.
    final DecimalFormat df = new DecimalFormat("#0.####");
    
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
        runOnUiThread(new Runnable() {
          @Override
          public void run() {
            getActionBar().setSubtitle("Found beacons(x): " + beaconsList.size());
            mapInfo.setText("The map size (x, y): \t" + mapXSize + ", " + mapYSize + "\n");
            
            // Reset everything to default values when the button is pressed.
            if (reset) {
              measurements = 0;
              distances = new HashMap<Integer, ArrayList<Double>>();
              distanceAvg = new HashMap<Integer, Double>();
              reset = false;
            }
            
            // Copy the workingBitmap for a clean sheet for the Canvas.
            final Bitmap mutableBitmap = workingBitmap.copy(Bitmap.Config.ARGB_8888, true);
            Canvas canvas = new Canvas(mutableBitmap);
            
            // Draw a circle for each beacon on the map
	        for (int i = 0; i < beaconsList.size(); i++) {
	          Beacon currentBeacon = beaconsList.get(i);
	          int minorVal = currentBeacon.getMinor();
	          // To prevent new beacons from showing up or invalid beacons from showing.
	          if (preferences.getFloat("x" + minorVal, -1) < 0) {
	        	  continue;
	          }
	          
              // Check if the beacon is just found. Double if-statements because of the reset button.
              if (!minorValues.contains(minorVal)) {
            	minorValues.add(minorVal);
              }
              if (!distances.containsKey(minorVal)) {
            	distances.put(minorVal, new ArrayList<Double>());
              }
              addDistance(currentBeacon, minorVal);

	          // Predefined variables for creating a picture with drawn circles on it.
	          float[] locationBeacon = getLocation(currentBeacon);
	          
	          // Draw a circle at the beacon position.
	  	      canvas.drawCircle(locationBeacon[0], locationBeacon[1], (float) 0.05 * widthPixels, paintBlue);   
	        }

	        // At least 40 measurements before calculating the user position.
	        if (measurements > 39) {
		      // Every second a new position measurement
		      int temp = measurements;
		      while (temp > 9) {
		    	temp -= 10;
		      }
		       
		      if (temp == 0) {
	        	userPos = userLocation(beaconsList);
		      }	   
		      // Add the user information to the TextView.
		      mapInfo.append("User Position (x, y):  " + userPos[0] + ", " + userPos[1] + "\n" + "Maximum error:  " + 
	        					userPos[2] + "\n");

		      canvas.drawCircle(widthPixels *(userPos[0]/mapXSize), heightPixels * (userPos[1]/mapYSize), 
	        					(float) (userPos[2]/mapXSize) * widthPixels, paintRed);
	        	
	        } else {
	          // A countdown counter.
	          mapInfo.append("Time until measurement: " + df.format(4 - measurements * 0.1));
	        }
            measurements += 1;     
	        mapImage.setImageBitmap(mutableBitmap);
          }
        });
      }
    });
  }
  
  /*
   * Algorithm to calculate the user location.
   */
  private float[] userLocation(List<Beacon> beaconsList) {
	float x_user = 0;
	float y_user = 0;
	float r_user = 1;
	  
	// Check for legitimate beacons, those who have been selected within the area.
	ArrayList<float[]> circleArray = new ArrayList<float[]>();
	for (int i = 0; i < beaconsList.size(); i++) {
	  int minorVal = beaconsList.get(i).getMinor();
	  float r;
	  try {
	    r = distanceAvg.get(minorVal).floatValue();
	  } catch (NullPointerException e) {
	    continue;
	  }
	  float x = preferences.getFloat("x" + minorVal, -1);
	  float y = preferences.getFloat("y" + minorVal, 0);
	  float z = preferences.getFloat("z" + minorVal, 0);
	  if (x >= 0 && y >= 0) {
   	    // Remove the height difference between the phone and beacon from the distance.
		r = (float) Math.sqrt((r * r) - (z * z));
		circleArray.add(new float[]{x, y, r});
		x_user += x;
		y_user += y;			  
	  } 
	}
	  
	// Only calculate the position when 2 or more beacons are available.
	float circleNumber = circleArray.size();
	if (circleNumber < 2) {
	  return new float[]{x_user, y_user, r_user};
	}
	  
	// The average position between all the valid circles.
	x_user = x_user / circleNumber;
	y_user /= circleNumber;

	float prev1Error = 0;
	float currentError = 1000000;

	// If the last error is the same as the current error no better value will be calculated.
	while (prev1Error != currentError) {
	  prev1Error = currentError;
	  // Calculate the position up, down, left and right of the current one to calculate its error there.
	  ArrayList<float[]> newPositions = new ArrayList<float[]>();
	  newPositions.add(new float[]{(float) (x_user + 0.1), y_user});
	  newPositions.add(new float[]{(float) (x_user - 0.1), y_user});
	  newPositions.add(new float[]{x_user, (float) (y_user + 0.1)});
	  newPositions.add(new float[]{x_user, (float) (y_user - 0.1)});
	  
	  // For each position in a direction calculate the error.
	  for (float[] direction : newPositions) {
		float error = 0;
		ArrayList<Float> dist = new ArrayList<Float>();
		  
		// The error on a certain position for each beacon.
		for (int i = 0; i < circleNumber; i++) {
		  float[] circlePos = circleArray.get(i);
		  dist.add((float) (Math.sqrt(Math.pow(circlePos[0] - direction[0], 2) + 
				  	Math.pow(circlePos[1] - direction[1], 2)) - circlePos[2]));
		  error += Math.pow(dist.get(dist.size() - 1), 2);
		}
		error = (float) Math.sqrt(error);
			  
		// If the error is smaller we take the values.
		if (error < currentError) {
		  Collections.sort(dist);
		  r_user = dist.get(dist.size() - 1);
		  x_user = direction[0];
		  y_user = direction[1];
		  currentError = error;
		}
	  }		  
	}
	return new float[]{x_user, y_user, r_user};
  }
  
  /*
   * Add a distance to its value and calculate the distance to the beacon over the multiple measurements.
   */
  private void addDistance(Beacon beacon, int minorVal) {
    // Add the distance to the ArrayList and maintain a certain size.
    double distance = Utils.computeAccuracy(beacon);
    ArrayList<Double> distanceToBeacon = distances.get(minorVal);
    distanceToBeacon.add(distance);
    if (distanceToBeacon.size() > 100) {
      distanceToBeacon.remove(0);
    }
    median(distanceToBeacon, minorVal);
  }  
  
  /*
   * Calculate the distance to beacon by taking the median.
   */
  private void median(ArrayList<Double> distanceToBeacon, int minorVal) {
	Collections.sort(distanceToBeacon);
	int half = (int) (0.5 * distanceToBeacon.size());
	if (distanceToBeacon.size() == 1) {
	  half = 0;
	}
	distanceAvg.put(minorVal, distanceToBeacon.get(half));
  }
  
  /*
   * Calculate the distance to beacon by taking the average.
   */
  private void average(ArrayList<Double> distanceToBeacon, int minorVal) {
	double total = 0;
		  
	for (double element : distanceToBeacon) {
		total += element;
	}
	distanceAvg.put(minorVal, total / distanceToBeacon.size());
  }
  
  /*
   * Retrieve and calculate the location of a beacon on the picture.
   */
  private float[] getLocation(Beacon beacon) {
	int minorVal = beacon.getMinor();
	  
	// Get the position of the beacon.
	float beaconX = preferences.getFloat("x" + minorVal, 0);
	float beaconY = preferences.getFloat("y" + minorVal, 0);

	// Calculate the ratio of the beacon position with the map
	float ratioX = beaconX / mapXSize;
	float ratioY = beaconY / mapYSize;
	  
	// Calculate the number of pixels it has to move.
	float locationPixelsX = (ratioX * widthPixels);
	float locationPixelsY = (ratioY * heightPixels);

	return new float[]{locationPixelsX, locationPixelsY}; 
  }
  
  /*
   * Get the sizes of the screen without the status bar.
   */
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
        }
      }
    });
  }

}
