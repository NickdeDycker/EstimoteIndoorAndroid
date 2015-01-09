# EstimoteIndoorAndroid
Estimote Indoor Location finder
=======================
This app is supposed is to find Estimote Beacons and return a somewhat reliable measurement for the distance.
Using multiple beacons, the app can also provide a location of the user if he/she is in range of at least 3 known beacons.
The app will first be developed to become aware of the possibilities of the beacon and its accuracy.

![App Layout](https://github.com/NickdeDycker/EstimoteIndoorAndroid/blob/master/App%20layout.png)

HomeActivity
=======================
-	Connect to the estimote beacons.
-	Show the number of found beacons in the first button, this button redirects to BeaconListActivity.
-	The second button goes to LocationActivity.
-	The third button shows the distances (and maybe some general info) of all the nearby beacons beneath it in a text box at the time of clicking. 

BeaconListActivity
=======================
-	Connect to the estimote beacons.
-	Show all the beacons in a list, using a ListAdapter. This will be similar as to how the picture will do it, but maybe in a different order (some of the data are really long strings).
-	Clicking on a list item (a beacon) allows the user to change certain settings (it sends the user to the BeaconPropertiesActivity) like the position it’s in or its major/minor. I’m not sure on how I am gonna implement this in the design. 

MapActivity
=======================
-	Connect to the estimote beacons.
-	Show a picture and specify its width and height in meters (as how big it would be in reality). Not sure if this will be hardcoded or not. If there’s time left a button will be implemented that can change the settings on this picture.
-	A dot shows the user location calculated by the distance of at least 3 beacons. If there aren’t enough beacons nearby a message will appear saying it’s not possible to find a location. 
-	Different colour dots or different shapes will show the positions of the beacons on the picture.
-	Using the pixel/meter ratio, the place on the picture can be defined. It is possible to place 2 imageviews on top of each other or to draw a circle on a picture using Bitmap.

Using the UUID of the beacons, or the major/minor combination, we will keep track of which beacon is which. Using SharedPreferences, we store the necessary data like x- and y-position. 

API’s :
=======================
-	SharedPreferences
-	Bitmap
-	Estimote SDK

Issues I found so far:
=======================
Security on the beacon: Using the Estimote app (the official in the Google play store for example) disables this beacon for any other app (It can’t be found by Bluetooth). The user can also change the settings of the beacon. (There's is something about a Cloud in which owners claim ownership but I haven't looked into this yet).

How to connect to the beacons: Is it possible to switch activity without having to find the beacons again? 

Power Management: Changing the settings of the Estimote (interval times, range) to save battery when it’s not being used. This however can go wrong by having multiple people scan at the same time. The range should always have a minimum value depending on the distances, setting the range on 15 meters for example it can’t actually change Beacon settings from this range (it will return an error). So once the beacons are placed a sufficient range has to be put in place and it shouldn’t be lowered, because you might not be able to reach it from the ground anymore.

How to connect to the estimate beacons in pseudo code.
=======================
**onCreate:**
-	Set up a BeaconManager
-	Public method “onBeaconsDiscovered” to retrieve the info.

**onDestroy:**
-	disconnect the BeaconManager

**onStart:**
-	Check if the phone provides Bluetooth Low Energy.
-	Check if the Bluetooth is enabled, if not ask the user to enable this.
-	Connect to the BeaconManager
-	Start ranging (requesting data from the beacon)

**onStop:**
-	Stop ranging (requesting data from the beacon)
