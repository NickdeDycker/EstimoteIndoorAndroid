package nl.mprog.estimoteindoorandroid;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;
import com.estimote.sdk.Beacon;
import com.estimote.sdk.Utils;
import com.estimote.sdk.connection.BeaconConnection;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;

@SuppressLint("UseSparseArrays") 
public class BeaconListAdapter extends BaseAdapter {

  ArrayList<Beacon> beacons;
  private LayoutInflater inflater;
  private SharedPreferences preferences;
  Context c;

  ArrayList<Integer> minorValues = new ArrayList<Integer>();
  HashMap<Integer, ArrayList<Double>> distances = new HashMap<Integer, ArrayList<Double>>();
  
  
  public BeaconListAdapter(Context context) {
    this.inflater = LayoutInflater.from(context);
    this.beacons = new ArrayList<Beacon>();
    preferences = PreferenceManager.getDefaultSharedPreferences(context);
    c = context;
  }

  public void replaceWith(Collection<Beacon> newBeacons) {
	// For when new beacons are within range.
    this.beacons.clear();
    this.beacons.addAll(newBeacons);
    notifyDataSetChanged();
  }

  @Override
  public int getCount() {
    return beacons.size();
  }

  @Override
  public Beacon getItem(int position) {
    return beacons.get(position);
  }

  @Override
  public long getItemId(int position) {
    return position;
  }

  @Override
  public View getView(int position, View view, ViewGroup parent) {
    view = inflateIfRequired(view, position, parent);
    bind(getItem(position), view);
    return view;
  }
  
  /*
   * Adds a distance and calculates the average of multiple measurements of the distance to a beacon.
   */
  
  private double average(int minor, double dist) {

	if (!distances.containsKey(minor)) {
	  distances.put(minor, new ArrayList<Double>());
	}
	
	ArrayList<Double> distanceArray = distances.get(minor);
	distanceArray.add(dist);
	
	// This way it only remembers the last 5 measured distances.
	if (distanceArray.size() > 100) {
	  distanceArray.remove(0);
	}
	
	// Sum up all the elements in the Array and divide by its size to get the average.
	double total = 0;
	for (double element : distanceArray) {
	  total += element;
	}
	
	double avg = total / distanceArray.size();
	return avg;
  }
  
  private void bind(Beacon beacon, View view) {
    final ViewHolder holder = (ViewHolder) view.getTag();
    
    int minor = beacon.getMinor();
    Double dist = Utils.computeAccuracy(beacon);
    DecimalFormat df = new DecimalFormat("#0.####");
    double avg_dist = average(minor, dist);

    holder.uuidTextView.setText("UUID: " + beacon.getProximityUUID());
    holder.majorminorTextView.setText("Major (Minor): \t \t" + beacon.getMajor() + " (" + minor + ")");
    holder.distanceTextView.setText("Distance: \t \t \t \t \t \t" + df.format(avg_dist));
    holder.posTextView.setText("Position(x, y, z): \t \t" + "(" + preferences.getFloat("x"+minor, -1) + ", " + 
    							preferences.getFloat("y"+minor, 0) + ", " + preferences.getFloat("z"+minor, 0) + ")");
    holder.rssiTextView.setText("RSSI: " + beacon.getRssi() + "\t Measured Power: " + beacon.getMeasuredPower());

    BeaconConnection connection = new BeaconConnection(c, beacon, new BeaconConnection.ConnectionCallback() {
        @Override 
        public void onAuthenticated(BeaconConnection.BeaconCharacteristics chars) {
        	holder.powerTextView.setText("Power works: " + chars.getBroadcastingPower());
        }

        @Override 
        public void onAuthenticationError() {
        	holder.powerTextView.setText("Power works1: ");
        }

        @Override 
        public void onDisconnected() {
        	holder.powerTextView.setText("Power works2: ");
        }
      });
      Log.d("connect", "connect: " + connection.isConnected());
      connection.authenticate();
      Log.d("connect", "connect2: " + connection.isConnected());
      connection.close();
    
  }

  @SuppressLint("InflateParams") 
  private View inflateIfRequired(View view, int position, ViewGroup parent) {
    if (view == null) {
      view = inflater.inflate(R.layout.device_item, null);
      view.setTag(new ViewHolder(view));
    }
    return view;
  }

  static class ViewHolder {
    final TextView uuidTextView;
    final TextView majorminorTextView;
    final TextView distanceTextView;
    final TextView posTextView;
    final TextView rssiTextView;
    final TextView powerTextView;

    ViewHolder(View view) {
      uuidTextView = (TextView) view.findViewWithTag("uuid");
      majorminorTextView = (TextView) view.findViewWithTag("majorminor");
      distanceTextView = (TextView) view.findViewWithTag("distance");
      posTextView = (TextView) view.findViewWithTag("position");
      rssiTextView = (TextView) view.findViewWithTag("rssi");
      powerTextView = (TextView) view.findViewWithTag("power");
    }
  }
}
