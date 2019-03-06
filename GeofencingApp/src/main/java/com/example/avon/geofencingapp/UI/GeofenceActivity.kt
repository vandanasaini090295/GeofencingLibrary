package com.example.avon.geofencingapp.UI

import android.Manifest
import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.preference.PreferenceManager
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import android.widget.Toast
import com.example.avon.geofencingapp.Interface.NotificationInterface
import com.example.avon.geofencingapp.R
import com.example.avon.geofencingapp.Services.GeofenceTransitionService
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.location.*
import com.google.android.gms.maps.*
import com.google.android.gms.maps.MapFragment
import com.google.android.gms.maps.model.*
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.tasks.OnSuccessListener

/**
 * activity class for all the geofence creation purposes
 */
class GeofenceActivity : AppCompatActivity(), NotificationInterface{


    private val TAG = GeofenceActivity::class.java.simpleName
    private var map : GoogleMap? = null
    private var geofenceMarker : Marker? = null
    private val GEOFENCE_REQ_ID = "My Geofence"
    private val GEO_DURATION = 60 * 60 * 1000L
    private val GEOFENCE_REQ_CODE = 0
    private var geofenceLimits : Circle? = null
    private val GEOFENCE_RADIUS_DOUBLE = 500.0        //in metres
    private val KEY_GEOFENCE_LAT = "GEOFENCE LATITUDE"
    private val KEY_GEOFENCE_LONG = "GEOFENCE LONGITUDE"
    private lateinit var mContext: Context

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_geofence)
    }

    /**
     * creating a geofence
     * transition types: GEOFENCE_TRANSITION_ENTER :: triggering event when a user enters geofence
     * or GEOFENCE_TRANSITION_EXIT :: triggering event when a user exits geofence
     * or GEOFENCE_TRANSITION_DWELL :: triggering event when a user dwells in the geofence for quite an interval;
     * set the setLoiteringDelay property for the dwell period
     */
    private fun createGeofence(latLng : LatLng?, radius : Float) :  Geofence?{
        Log.d(TAG, "createGeofence()")
        var geofence : Geofence? = null
                if(null != latLng) {
            geofence = Geofence.Builder()
                    .setCircularRegion(latLng.latitude, latLng.longitude, radius)
                    .setRequestId(GEOFENCE_REQ_ID)
                    .setExpirationDuration(GEO_DURATION)
                    .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
                    .build()
        }
        return geofence
    }

    // create a geofencing request object
    private fun createGeofenceRequest(geofence : Geofence?) : GeofencingRequest{
        Log.d(TAG, "createGeofenceRequest")

        return GeofencingRequest.Builder()
                .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
                .addGeofence(geofence)
                .build()

    }

    // recovering last geofence marker
    fun recoverGeofenceMarker() {
        Log.d(TAG, getString(R.string.recoverGeofenceMarker))
        val prefs = getPreferences(Context.MODE_PRIVATE)
        if(prefs.contains(KEY_GEOFENCE_LAT) && prefs.contains(KEY_GEOFENCE_LONG)){
            val latitude = prefs.getLong(KEY_GEOFENCE_LAT, -1).toDouble()
            val longitude = prefs.getLong(KEY_GEOFENCE_LONG, -1).toDouble()
            val latLng = LatLng(latitude, longitude)
            markerForGeofence(map, latLng)
            drawGeofence()
        }
    }

    // creation of marker for geofence
    fun markerForGeofence(googleMap : GoogleMap?, latLng : LatLng?) {
        map = googleMap
        Log.d(TAG, "markerForGeofence()")
        val title = latLng?.latitude.toString() + ", " + latLng?.longitude.toString()
        var markerOptions : MarkerOptions? = null

        // define marker position
        if(null != latLng){
            markerOptions = MarkerOptions()
                    .position(latLng)
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE))
                    .title(title)
        }

        if(null != map){
            // remove last geofence marker
            if(null != geofenceMarker){
                geofenceMarker?.remove()
            }
            geofenceMarker = map?.addMarker(markerOptions)
        }

    }

    // check for location permissions
    private fun checkPermission(): Boolean {
        Log.d(TAG, "checkPermission()")
        return ContextCompat.checkSelfPermission(mContext, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    //create geofence pending intent
    private fun createGeofencePendingIntent() : PendingIntent{
        Log.d(TAG, "createGeofencePendingIntent")
        val intent = Intent(mContext, GeofenceTransitionService::class.java)
        return  PendingIntent.getService(mContext, GEOFENCE_REQ_CODE, intent, PendingIntent.FLAG_UPDATE_CURRENT)
    }

    //add the created geofencerequest to the device's monitoring list
    private fun addGeofence(geofencingRequest : GeofencingRequest){
        Log.d(TAG, "addGeofence()")
        if(checkPermission()){
            Log.d(TAG, "checkPermission in addGeofence()")
            val geofencingClient = LocationServices.getGeofencingClient(mContext)
            geofencingClient.addGeofences(geofencingRequest, createGeofencePendingIntent())
                    .addOnSuccessListener{
                        saveGeofence()
                        drawGeofence()
                    }
                    .addOnFailureListener {
                    }
        }
    }

    // saving geofence marker with shared prefs
    private fun saveGeofence() {
        Log.d(TAG, "saveGeofence()")
        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(mContext)
        val editor = sharedPrefs.edit()
        val latitude = geofenceMarker?.position?.latitude?.toLong() ?: -1L
        val longitude = geofenceMarker?.position?.longitude?.toLong() ?: -1L
        editor.putLong(KEY_GEOFENCE_LAT, latitude)
        editor.putLong(KEY_GEOFENCE_LONG, longitude)
        editor.apply();
    }

    // draw geofence circle on google map
    fun drawGeofence() {
        Log.d(TAG, "drawGeofence()")
        if(null != geofenceLimits){
            geofenceLimits?.remove()
        }

        val circleOptions : CircleOptions = CircleOptions()
                .center(geofenceMarker?.position)
                .strokeColor(Color.argb(50, 70, 70, 70))
                .fillColor(Color.argb(100, 150, 150, 150))
                .radius(GEOFENCE_RADIUS_DOUBLE)
        if(null != map)
            geofenceLimits = map?.addCircle(circleOptions)
    }

    /**
     * start geofence creation process
     * Radius : radius of the geofencing area
     * Latitude and Longitude : center coordinates of the geofencing area
     */
    fun startGeofence(context : Context, googleMap: GoogleMap?, radius: Float) {
        mContext = context
        map = googleMap
        Log.d(TAG, "startGeofence()")
        if (null != geofenceMarker) {
            val geofence = createGeofence(geofenceMarker?.position, radius)
            val geofencingRequest = createGeofenceRequest(geofence)
            addGeofence(geofencingRequest)
        } else {
            Log.e(TAG, "geofence marker is null")
        }
    }

    override fun sendNotification(message: String) {
    }
}
