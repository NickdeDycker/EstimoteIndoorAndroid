package nl.mprog.estimoteindoorandroid;

import com.estimote.sdk.Beacon;
import com.estimote.sdk.connection.BeaconConnection;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

public class SingleBeaconActivity extends Activity {
  private Beacon beacon;
  private BeaconConnection connection;
  private int power;
  private int interval;
	  
  @Override	  
  protected void onCreate(Bundle savedInstanceState) {
	super.onCreate(savedInstanceState);
	setContentView(R.layout.single_beacon_screen);
	getActionBar().setDisplayHomeAsUpEnabled(true);
	    
	final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
	final SharedPreferences.Editor editPref = preferences.edit();
	
	// Get beacon from the intent.
	beacon = getIntent().getParcelableExtra("getBeacon");
	
	// Set up a read/write connection to the beacon.
	connection = new BeaconConnection(this, beacon, createConnectionCallback());
	final int minorValue = beacon.getMinor();
	   
	final EditText inputXPos = (EditText) findViewById(R.id.pos_x);
	final EditText inputYPos = (EditText) findViewById(R.id.pos_y);
	final EditText inputZPos = (EditText) findViewById(R.id.pos_z);
	    
	// Set the default value of the input to the current to value.
	inputXPos.setText(Float.toString(preferences.getFloat("x"+minorValue, 0)), TextView.BufferType.EDITABLE);
	inputYPos.setText(Float.toString(preferences.getFloat("y"+minorValue, 0)), TextView.BufferType.EDITABLE);
	inputZPos.setText(Float.toString(preferences.getFloat("z"+minorValue, 0)), TextView.BufferType.EDITABLE);
	
	// A button to save the values entered in the EditTexts, automatically returns you to the list.
	final Button saveValues = (Button) findViewById(R.id.confirm_pos);  
	saveValues.setOnClickListener(new OnClickListener() {
	  @Override
	  public void onClick(View v) {
		// Save the changed x, y and z positions.
		editPref.putFloat("x"+minorValue, Float.valueOf(inputXPos.getText().toString())); 
		editPref.putFloat("y"+minorValue, Float.valueOf(inputYPos.getText().toString()));
		editPref.putFloat("z"+minorValue, Float.valueOf(inputZPos.getText().toString()));
		editPref.commit();
		        
		returnToList();
	  }
	});
  }
	  
  @Override
  protected void onResume() {
	super.onResume();
	if (!connection.isConnected()) {
	  connection.authenticate();
	}
  }

  @Override
  protected void onDestroy() {
	connection.close();
	super.onDestroy();
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
	if (item.getItemId() == android.R.id.home) {
	  finish();
	  return true;
	}
	return super.onOptionsItemSelected(item);
  }
  
  /*
   * Sets up a connection to the beacon and its events on authentication or its failure.
   */
  private BeaconConnection.ConnectionCallback createConnectionCallback() {
	return new BeaconConnection.ConnectionCallback() {
	  @Override 
	  public void onAuthenticated(final BeaconConnection.BeaconCharacteristics beaconChars) {
		runOnUiThread(new Runnable() {
		  @Override 
		  public void run() {
			// Set minor on click listener
		    TextView minorValue = (TextView) findViewById(R.id.minor);
		    TextView minorText = (TextView) findViewById(R.id.set_minor_text);
		    minorValue.setText("" + beacon.getMinor());
		    minorValue.setClickable(true);
		    minorText.setClickable(true);
		    minorValue.setOnClickListener(minorOnClickListener);
		    minorText.setOnClickListener(minorOnClickListener);
		    
		    // Set power on click listener
		    power = beaconChars.getBroadcastingPower();
		    TextView powerValue = (TextView) findViewById(R.id.power);
		    TextView powerText = (TextView) findViewById(R.id.set_power_text);
		    powerValue.setText("" + power);
		    powerValue.setClickable(true);
		    powerText.setClickable(true);
		    powerValue.setOnClickListener(powerOnClickListener);
		    powerText.setOnClickListener(powerOnClickListener);
		    
		    // Set interval on click listener
		    interval = beaconChars.getAdvertisingIntervalMillis();
		    TextView intervalValue = (TextView) findViewById(R.id.interval);
		    TextView intervalText = (TextView) findViewById(R.id.set_interval_text);
		    intervalValue.setText("" + interval);
		    intervalValue.setClickable(true);
		    intervalText.setClickable(true);		    
		    intervalValue.setOnClickListener(intervalOnClickListener);    
		    intervalText.setOnClickListener(intervalOnClickListener);
		  }
		});
	  }

	  @Override 
	  public void onAuthenticationError() {
		runOnUiThread(new Runnable() {
		  @Override 
		  public void run() {
			TextView minorValue = (TextView) findViewById(R.id.minor);
			TextView powerValue = (TextView) findViewById(R.id.power);
			TextView intervalValue = (TextView) findViewById(R.id.interval);
			minorValue.setText("Failed to authenticate");
			powerValue.setText("Failed to authenticate");
			intervalValue.setText("Failed to authenticate");
		  }
		});
	  }

	  @Override 
	  public void onDisconnected() {
	  }
	};
  }
  
  /*
   * Start intent to go back to the list with beacons.
   */
  private void returnToList() {
      Intent intentSingleBeacon = new Intent(SingleBeaconActivity.this, BeaconListActivity.class);
      startActivity(intentSingleBeacon);
      finish();
  }
  
  
  /*
   * A OnClickListener to change the minor value.
   */
  View.OnClickListener minorOnClickListener = new View.OnClickListener() {
	@Override
    public void onClick(View v) {
	  // The LayoutInflater is needed for multiple inputs in the dialog.
	  LayoutInflater dialogInflater = LayoutInflater.from(SingleBeaconActivity.this);
						
	  // Retrieve the minor of the beacon from the Adapter.
	  final View textEntryView = dialogInflater.inflate(R.layout.settings_dialog, null);
						
	  final EditText inputEditText = (EditText) textEntryView.findViewById(R.id.value_edit);
					
	  // Set the default value of the input to the current to value.
	  inputEditText.setText(Float.toString(beacon.getMinor()), TextView.BufferType.EDITABLE);
						
	  AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(SingleBeaconActivity.this);
	  dialogBuilder.setTitle("Change Minor");
	  dialogBuilder.setView(textEntryView);
						
	  // Set up the OK and Cancel button
	  dialogBuilder.setPositiveButton("OK", new DialogInterface.OnClickListener() { 
		@Override
		public void onClick(DialogInterface dialog, int i) {
		  // Save the changed x and y positions.
		  connection.writeMinor(Integer.valueOf(inputEditText.getText().toString()), 
				  				new BeaconConnection.WriteCallback() {
			@Override 
			public void onSuccess() {
			}

			@Override 
			public void onError() {
			}
		  });
		  returnToList();
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
  };
  
  /*
   * A OnClickListener to change the power value.
   */
  View.OnClickListener powerOnClickListener = new View.OnClickListener() {
	@Override
    public void onClick(View v) {
	  // The LayoutInflater is needed for multiple inputs in the dialog.
	  LayoutInflater dialogInflater = LayoutInflater.from(SingleBeaconActivity.this);
						
	  // Retrieve the minor of the beacon from the Adapter.
	  final View textEntryView = dialogInflater.inflate(R.layout.settings_dialog, null);
						
	  final EditText inputEditText = (EditText) textEntryView.findViewById(R.id.value_edit);
					
	  // Set the default value of the input to the current to value.
	  inputEditText.setText(""+power, TextView.BufferType.EDITABLE);
						
	  AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(SingleBeaconActivity.this);
	  dialogBuilder.setTitle("Change Power (int dBm)");
	  dialogBuilder.setView(textEntryView);
						
	  // Set up the OK and Cancel button
	  dialogBuilder.setPositiveButton("OK", new DialogInterface.OnClickListener() { 
		@Override
		public void onClick(DialogInterface dialog, int i) {
		  connection.writeBroadcastingPower(Integer.valueOf(inputEditText.getText().toString()), 
				  				new BeaconConnection.WriteCallback() {
			@Override 
			public void onSuccess() {
			}

			@Override 
			public void onError() {
			}
		  });
		  returnToList();
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
  };
  
  /*
   * A OnClickListener to change the interval value.
   */
  View.OnClickListener intervalOnClickListener = new View.OnClickListener() {
	@Override
    public void onClick(View v) {
	  // The LayoutInflater is needed for multiple inputs in the dialog.
	  LayoutInflater dialogInflater = LayoutInflater.from(SingleBeaconActivity.this);
						
	  // Retrieve the minor of the beacon from the Adapter.
	  final View textEntryView = dialogInflater.inflate(R.layout.settings_dialog, null);
						
	  final EditText inputEditText = (EditText) textEntryView.findViewById(R.id.value_edit);
					
	  // Set the default value of the input to the current to value.
	  inputEditText.setText(""+interval, TextView.BufferType.EDITABLE);
						
	  AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(SingleBeaconActivity.this);
	  dialogBuilder.setTitle("Change Interval (ms)");
	  dialogBuilder.setView(textEntryView);
						
	  // Set up the OK and Cancel button
	  dialogBuilder.setPositiveButton("OK", new DialogInterface.OnClickListener() { 
		@Override
		public void onClick(DialogInterface dialog, int i) {
		  connection.writeAdvertisingInterval(Integer.valueOf(inputEditText.getText().toString()), 
				  				new BeaconConnection.WriteCallback() {
			@Override 
			public void onSuccess() {
			}

			@Override 
			public void onError() {
			}
		  });
		  returnToList();
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
  };
  
  
}
