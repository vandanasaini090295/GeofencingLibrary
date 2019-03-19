package com.example.avon.geofenceembedded

import android.Manifest
import android.app.FragmentManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.TaskStackBuilder
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.nfc.Tag
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v4.app.NotificationCompat
import android.support.v4.content.ContextCompat
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import com.example.avon.geofencingapp.Interface.NotificationInterface
import com.example.avon.geofencingapp.Services.GeofenceTransitionService
import com.example.avon.geofencingapp.UI.GeofenceActivity
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationListener
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapFragment
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.*
import com.google.android.gms.tasks.OnSuccessListener

class MainActivity : AppCompatActivity(),
        OnMapReadyCallback,
        GoogleMap.OnMarkerClickListener,
        GoogleMap.OnMapClickListener,
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        LocationListener,
        NotificationInterface{



    private val TAG : String = MainActivity::class.java.simpleName
    private lateinit var mapFragment: MapFragment
    private var map: GoogleMap? = null
    private var googleApiClient: GoogleApiClient? = null
    private val PERMISSION_REQUEST_CODE: Int = 49
    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient
    private var lastLocation : Location? = null
    private lateinit var locationRequest: LocationRequest
    private var locationMarker :Marker? = null
    private val UPDATE_INTERVAL = 1000L
    private val FASTEST_INTERVAL = 900L
    private lateinit var geofenceActivity : GeofenceActivity
    private val GEOFENCE_RADIUS_FLOAT = 500.0f
    private val GEOFENCE_NOTIFICATION_ID = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // initializing geofence activity of library module
        geofenceActivity = GeofenceActivity()

        //initialize googlemaps
        initGMaps()

        // create google Api client
        createGoogleApi()
    }



    // creating googleApiClient instance
    private fun createGoogleApi() {
        Log.d(TAG, "createGoogleApi")
        if(null == googleApiClient) {
            googleApiClient = GoogleApiClient.Builder(this)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(LocationServices.API)
                    .build()
        }
    }

    // initialize google maps
    private fun initGMaps() {
        mapFragment = fragmentManager.findFragmentById(R.id.map) as MapFragment

        // sets a callback object which is triggered when the GoogleMap instance is ready to be used
        mapFragment.getMapAsync(this)
    }



    // callback when map is ready
    override fun onMapReady(googleMap: GoogleMap?) {
        Log.d(TAG, "onMapReady()")
        map = googleMap
        map?.setOnMarkerClickListener(this)
        map?.setOnMapClickListener(this)
    }

    // callback when marker is touched
    override fun onMarkerClick(marker: Marker?): Boolean {
        Log.d(TAG, "onMarkerClick : " + marker?.position)
        return false
    }

    // callback when map is touched
    override fun onMapClick(latLng: LatLng?) {
        Log.d(TAG, "onMapClick()")
        geofenceActivity.markerForGeofence(map, latLng)
    }

    //GoogleApiClient connection callbacks connected
    override fun onConnected(p0: Bundle?) {
        Log.d(TAG, "onConnected()")
        getLastKnownLocation()
    }

    private fun getLastKnownLocation() {
        Log.d(TAG, "getLastKnownLocation()")
        if(checkPermission()){
            fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)
            fusedLocationProviderClient.lastLocation
                    .addOnSuccessListener(this, OnSuccessListener { location: Location? ->
                        if (null != location) {
                            lastLocation = location
                            Log.d(TAG, "LastKnown Location : " + " Long : " + lastLocation?.longitude
                                    + " Lat : " + lastLocation?.latitude)
                            writeLastLocation()
                            startLocationUpdates()
                        } else {
                            Log.d(TAG, "no location received yet")
                            startLocationUpdates()
                        }
                    })
        } else{
            askPermission()
        }
    }

    // ask for location permissions
    private fun askPermission() {
        Log.d(TAG, "askPermission")
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), PERMISSION_REQUEST_CODE)
    }

    // check for location permissions
    private fun checkPermission(): Boolean {
        Log.d(TAG, "checkPermission()")
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        Log.d(TAG, "onRequestPermissionResult")
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when(requestCode){
            PERMISSION_REQUEST_CODE -> {
                if(grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED){
                    getLastKnownLocation()
                } else{
                    Toast.makeText(this, "Permission Denied", Toast.LENGTH_LONG).show()
                }
            }
            else ->{
                Log.d(TAG, "request permission doesn't match")
            }
        }
    }

    //GoogleApiClient.ConnectionCallbacks suspended
    override fun onConnectionSuspended(p0: Int) {
        Log.w(TAG, "onConnectionSuspended()")
    }

    //GoogleApiClient.OnConnectionFailedListener failed
    override fun onConnectionFailed(p0: ConnectionResult) {
        Log.w(TAG, "onConnectionFailed()")
    }

    override fun onStart() {
        super.onStart()

        // calling googleApiClient connection on starting the activity
        googleApiClient?.connect()
    }

    override fun onStop() {
        super.onStop()

        //Disconnect GoogleApiClient connection when stopping activity
        googleApiClient?.disconnect()
    }


    // start location updates
    private fun startLocationUpdates() {
        Log.d(TAG, "startLocationUpdates()")
        locationRequest = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setInterval(UPDATE_INTERVAL)
                .setFastestInterval(FASTEST_INTERVAL)

        if(checkPermission()) {
            fusedLocationProviderClient.requestLocationUpdates(locationRequest, null)
        }
    }

    private fun writeLastLocation() {
        writeActualLocation(lastLocation)
    }

    override fun onLocationChanged(location: Location?) {
        Log.d(TAG, "onLocationChanged [" + location + "]")
        lastLocation = location
        writeActualLocation(lastLocation)
    }

    // write location coordinates on UI
    private fun writeActualLocation(lastLocation: Location?) {
        if(null != lastLocation?.latitude && null != lastLocation?.longitude)
            markerLocation(LatLng(lastLocation.latitude, lastLocation.longitude))
    }

    private fun markerLocation(latLng: LatLng?) {
        Log.d(TAG, "markerLocation(" + latLng + ")")
        val title = latLng?.latitude.toString() + ", " + latLng?.longitude.toString()
        var markerOptions : MarkerOptions? = null
        if(null != latLng) {
            markerOptions = MarkerOptions()
                    .title(title)
                    .position(latLng)
        }

        if(null != map){
            //remove the anterior marker
            if(null != locationMarker)
                locationMarker?.remove()

            locationMarker = map?.addMarker(markerOptions)
            val zoom = 14f
            val cameraUpdate = CameraUpdateFactory.newLatLngZoom(latLng, zoom)
            map?.animateCamera(cameraUpdate)

            val cameraPosition = CameraPosition.Builder()
                    .target(latLng)      // Sets the center of the map to location user
                    .zoom(14f)                   // Sets the zoom
                    .bearing(90f)                // Sets the orientation of the camera to east
                    .tilt(40f)                   // Sets the tilt of the camera to 30 degrees
                    .build();                   // Creates a CameraPosition from the builder
            map?.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
        }

    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        val inflator = menuInflater
        inflator.inflate(R.menu.menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        when(item?.itemId){
            R.id.geofence -> {
                if(checkPermission()) {
                    geofenceActivity.startGeofence(this, map, GEOFENCE_RADIUS_FLOAT)
                    return true
                } else{
                    askPermission()
                }
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun sendNotification(message: String) {
        Log.d(TAG, "sendNotification()" + message)

        //Intent to start the main activity
        val notificationIntent = Intent(this, MainActivity::class.java)

        val taskStackBuilder = TaskStackBuilder.create(this)
        taskStackBuilder.addParentStack(GeofenceActivity::class.java)
        taskStackBuilder.addNextIntent(notificationIntent)
        val notificationPendingIntent = taskStackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT)

        // creating and sending notification
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notificationBuilder = NotificationCompat.Builder(this, "NOTIF_CHANNEL_ID")
                .setSmallIcon(com.example.avon.geofencingapp.R.drawable.abc_ic_go_search_api_material)
                .setColor(Color.RED)
                .setContentTitle(message)
                .setContentText("Geofence Notification")
                .setDefaults(NotificationCompat.DEFAULT_LIGHTS)
                .setContentIntent(notificationPendingIntent)
                .setAutoCancel(true)

        notificationManager.notify(GEOFENCE_NOTIFICATION_ID, notificationBuilder.build())
    }


}
