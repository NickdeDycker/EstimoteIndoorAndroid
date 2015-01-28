package nl.mprog.estimoteindoorandroid;

import com.estimote.sdk.Beacon;
import com.estimote.sdk.connection.BeaconConnection;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

public class singleBeaconActivity extends Activity {
  Beacon beacon;
  BeaconConnection connection;
  float power;
  int minor;
	  
  @Override	  
  protected void onCreate(Bundle savedInstanceState) {
	super.onCreate(savedInstanceState);
	setContentView(R.layout.single_beacon_screen);
	getActionBar().setDisplayHomeAsUpEnabled(true);
	    
	final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
	final SharedPreferences.Editor editPref = preferences.edit();
	
	// Get beacon from the intent.
	beacon = getIntent().getParcelableExtra(BeaconListActivity.EXTRAS_BEACON);
	
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
		        
		final Intent beaconListIntent = new Intent(singleBeaconActivity.this, BeaconListActivity.class);
		startActivity(beaconListIntent);
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
			power = beaconChars.getBroadcastingPower();
		    minor = beacon.getMinor();
		    TextView minor_value = (TextView) findViewById(R.id.minor);
		    TextView power_value = (TextView) findViewById(R.id.power);
		    minor_value.setText("" + minor);
		    power_value.setText("" + power);
		  }
		});
	  }

	  @Override 
	  public void onAuthenticationError() {
		runOnUiThread(new Runnable() {
		  @Override 
		  public void run() {
			TextView minor_value = (TextView) findViewById(R.id.minor);
			TextView power_value = (TextView) findViewById(R.id.power);
			minor_value.setText("Failed to authenticate");
			power_value.setText("Failed to authenticate");
		  }
		});
	  }

	  @Override 
	  public void onDisconnected() {
	  }
	};
  }
  
}
