package nl.mprog.estimoteindoorandroid;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;
import com.estimote.sdk.Beacon;
import com.estimote.sdk.Utils;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;

public class BeaconListAdapter extends BaseAdapter {
  private ArrayList<Beacon> beacons;
  private LayoutInflater inflater;
  private SharedPreferences preferences;
  Context c;

  private ArrayList<Integer> minorValues = new ArrayList<Integer>();
  private HashMap<Integer, ArrayList<Double>> distances = new HashMap<Integer, ArrayList<Double>>();
  
  public BeaconListAdapter(Context context) {
    this.inflater = LayoutInflater.from(context);
    this.beacons = new ArrayList<Beacon>();
    preferences = PreferenceManager.getDefaultSharedPreferences(context);
    c = context;
  }
  
  /*
   * Replaces the old list with new beacons.
   */
  public void replaceWith(Collection<Beacon> newBeacons) {
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
    view = inflater.inflate(R.layout.device_item, null);
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
	
	// This way it only remembers the last measured distances.
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
  
  /*
   * Set all values to the TextViews.
   */
  private void bind(Beacon beacon, View view) {
	int minor = beacon.getMinor();
    Double dist = Utils.computeAccuracy(beacon);
    DecimalFormat df = new DecimalFormat("#0.####");
    double avg_dist = average(minor, dist);
    final TextView uuidTextView = (TextView) view.findViewById(R.id.uuid);
    final TextView majorminorTextView = (TextView) view.findViewById(R.id.majorminor);
    final TextView distanceTextView = (TextView) view.findViewById(R.id.distance);
    final TextView posTextView = (TextView) view.findViewById(R.id.position);
    final TextView rssiTextView = (TextView) view.findViewById(R.id.rssi);
    uuidTextView.setText("UUID: " + beacon.getProximityUUID());
    majorminorTextView.setText("Major (Minor): \t \t" + beacon.getMajor() + " (" + minor + ")");
    distanceTextView.setText("Distance: \t \t \t \t \t \t" + df.format(avg_dist));
    posTextView.setText("Position(x, y, z): \t \t" + preferences.getFloat("x"+minor, -1) + ", " + 
    							preferences.getFloat("y"+minor, 0) + ", " + preferences.getFloat("z"+minor, 0));
    rssiTextView.setText("RSSI: " + beacon.getRssi() + "\t Measured Power: " + beacon.getMeasuredPower());
  }

}
