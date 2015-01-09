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

import java.util.ArrayList;
import java.util.Collection;


public class BeaconListAdapter extends BaseAdapter {

  ArrayList<Beacon> beacons;
  private LayoutInflater inflater;
  private Context c; 
  private ArrayList<Double> distances = new ArrayList<Double>();
  private SharedPreferences preferences;
  
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
   * Calculate the average of multiple measurements of the distance to a beacon.
   */
  
  private double average() {
	// This way it only remembers the last 5 measured distances.
	if (distances.size() > 5) {
	  distances.remove(0);
	}
	
	double total = 0;
	for (double element : distances) {
	  total += element;
	}
	
	double avg = total / distances.size();
	return avg;
  }
  
  private void bind(Beacon beacon, View view) {
    ViewHolder holder = (ViewHolder) view.getTag();
    
    Double dist = Utils.computeAccuracy(beacon);
    // In case of invalid measurements.
    if (dist != null) {
      distances.add(dist);
    }
    
    double avg_dist = average();
    int minor = beacon.getMinor();
    holder.uuidTextView.setText("UUID: " + beacon.getProximityUUID());
    holder.majorminorTextView.setText("Major (Minor): \t \t" + beacon.getMajor() + " (" + minor + ")");
    holder.distanceTextView.setText("Distance: \t \t \t \t \t \t" + avg_dist);
    holder.posTextView.setText("Position(x, y): \t \t" + "(" + preferences.getFloat("x"+minor, 0) + ", " + preferences.getFloat("y"+minor, 0) + ")");

  }

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

    ViewHolder(View view) {
      uuidTextView = (TextView) view.findViewWithTag("uuid");
      majorminorTextView = (TextView) view.findViewWithTag("majorminor");
      distanceTextView = (TextView) view.findViewWithTag("distance");
      posTextView = (TextView) view.findViewWithTag("position");
    }
  }
}
