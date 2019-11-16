package com.computingforgeeks.trucksend

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.location.Address
import android.location.Geocoder
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.computingforgeeks.trucksend.models.Directions
import com.computingforgeeks.trucksend.networking.RetrofitClient
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.Autocomplete
import com.google.android.libraries.places.widget.AutocompleteActivity
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode
import com.google.maps.android.PolyUtil
import com.karumi.dexter.Dexter
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionDeniedResponse
import com.karumi.dexter.listener.PermissionGrantedResponse
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.single.PermissionListener

import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.content_main.*
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.IOException
import java.util.*

class MainActivity : AppCompatActivity(), OnMapReadyCallback, PermissionListener,
    GoogleMap.OnMarkerDragListener {

    companion object {
        const val REQUEST_CHECK_SETTINGS = 43
        const val CURRENT_PLACE_AUTOCOMPLETE_REQUEST_CODE = 53
        const val DESTINATION_PLACE_AUTOCOMPLETE_REQUEST_CODE = 63
    }

    private lateinit var googleMap: GoogleMap
    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient
    private var startMarker: Marker? = null
    private var finishMarker: Marker? = null
    private var searchType: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment?
        mapFragment!!.getMapAsync(this)
        fusedLocationProviderClient = FusedLocationProviderClient(this)

        setStartPoint.setOnClickListener {
            searchType = 1
            givePermission()
        }

        setFinishPoint.setOnClickListener {
            searchType = 2
            givePermission()
        }

    }

    override fun onMapReady(map: GoogleMap?) {
        googleMap = map?: return
        if (isPermissionGiven()){
            googleMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(this, R.raw.style_map))
            googleMap.isMyLocationEnabled = true
            googleMap.uiSettings.isMyLocationButtonEnabled = true
            googleMap.uiSettings.isZoomControlsEnabled = true
            getCurrentLocation()
        } else {
            givePermission()
        }

        googleMap.setOnMarkerDragListener(this)
    }

    override fun onMarkerDragEnd(p0: Marker?) {
        if (p0 == startMarker){
            setStartLocation(p0!!.position.latitude, p0.position.longitude, "")
        } else if (p0 == finishMarker){
            setFinishLocation(p0!!.position.latitude, p0.position.longitude, "")
        }
    }

    override fun onMarkerDragStart(p0: Marker?) {
        Toast.makeText(this, "Changing location", Toast.LENGTH_SHORT).show()
    }

    override fun onMarkerDrag(p0: Marker?) {}

    private fun isPermissionGiven(): Boolean{
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    private fun givePermission() {
        Dexter.withActivity(this)
            .withPermission(Manifest.permission.ACCESS_FINE_LOCATION)
            .withListener(this)
            .check()
    }

    override fun onPermissionGranted(response: PermissionGrantedResponse?) {
        if (searchType == 0){
            getCurrentLocation()
        } else {
            Toast.makeText(this, resources.getString(R.string.loading), Toast.LENGTH_LONG).show()

            if (!Places.isInitialized()) {
                Places.initialize(applicationContext, BuildConfig.ApiKeyMap)
            }

            val fields = listOf(Place.Field.ID, Place.Field.NAME, Place.Field.LAT_LNG)
            val intent = Autocomplete.IntentBuilder(AutocompleteActivityMode.OVERLAY, fields).build(this)
            if (searchType == 1){
                startActivityForResult(intent, CURRENT_PLACE_AUTOCOMPLETE_REQUEST_CODE)
            } else {
                startActivityForResult(intent, DESTINATION_PLACE_AUTOCOMPLETE_REQUEST_CODE)
            }
        }
    }

    override fun onPermissionRationaleShouldBeShown(
        permission: PermissionRequest?,
        token: PermissionToken?
    ) {
        token!!.continuePermissionRequest()
    }

    override fun onPermissionDenied(response: PermissionDeniedResponse?) {
        Toast.makeText(this, "Permission required for showing location", Toast.LENGTH_LONG).show()
        finish()
    }

    private fun getCurrentLocation() {

        val locationRequest = LocationRequest()
        locationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        locationRequest.interval = (10 * 1000).toLong()
        locationRequest.fastestInterval = 2000

        val builder = LocationSettingsRequest.Builder()
        builder.addLocationRequest(locationRequest)
        val locationSettingsRequest = builder.build()

        val result = LocationServices.getSettingsClient(this).checkLocationSettings(locationSettingsRequest)
        result.addOnCompleteListener { task ->
            try {
                val response = task.getResult(ApiException::class.java)
                if (response!!.locationSettingsStates.isLocationPresent){
                    getLastLocation()
                }
            } catch (exception: ApiException) {
                when (exception.statusCode) {
                    LocationSettingsStatusCodes.RESOLUTION_REQUIRED -> try {
                        val resolvable = exception as ResolvableApiException
                        resolvable.startResolutionForResult(this, REQUEST_CHECK_SETTINGS)
                    } catch (e: IntentSender.SendIntentException) {
                    } catch (e: ClassCastException) {
                    }

                    LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE -> { }
                }
            }
        }
    }

    private fun getLastLocation() {
        fusedLocationProviderClient.lastLocation
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful && task.result != null) {
                    val mLastLocation = task.result
                    setStartLocation(mLastLocation!!.latitude, mLastLocation.longitude, "")
                } else {
                    Toast.makeText(this, "No current location found", Toast.LENGTH_LONG).show()
                }
            }
    }

    private fun setStartLocation(lat: Double, lng: Double, addr: String){

        var address = "Current address"
        if (addr.isEmpty()){
            val gcd = Geocoder(this, Locale.getDefault())
            val addresses: List<Address>
            try {
                addresses = gcd.getFromLocation(lat, lng, 1)
                if (addresses.isNotEmpty()) {
                    address = addresses[0].getAddressLine(0)
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        } else {
            address = addr
        }

        val icon = BitmapDescriptorFactory.fromBitmap(BitmapFactory.decodeResource(this.resources, R.drawable.ic_pickup))
        startMarker = googleMap.addMarker(
            MarkerOptions()
                .position(LatLng(lat, lng))
                .title("Start Location")
                .snippet("Near $address")
                .icon(icon)
                .draggable(true)
        )

        val cameraPosition = CameraPosition.Builder()
            .target(LatLng(lat, lng))
            .zoom(17f)
            .build()
        googleMap.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPosition))

        fromLocationTxt.text = String.format("From: Near %s", address)

        if (finishMarker != null){
            callDirectionsApi()
        }
    }

    private fun setFinishLocation(lat: Double, lng: Double, addr: String){

        var address = "Destination address"
        if (addr.isEmpty()){
            val gcd = Geocoder(this, Locale.getDefault())
            val addresses: List<Address>
            try {
                addresses = gcd.getFromLocation(lat, lng, 1)
                if (addresses.isNotEmpty()) {
                    address = addresses[0].getAddressLine(0)
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        } else {
            address = addr
        }


        val icon = BitmapDescriptorFactory.fromBitmap(BitmapFactory.decodeResource(this.resources, R.drawable.drop_pin))
        finishMarker = googleMap.addMarker(
            MarkerOptions()
                .position(LatLng(lat, lng))
                .title("Finish Location")
                .snippet("Near $address")
                .icon(icon)
                .draggable(true)
        )

        val cameraPosition = CameraPosition.Builder()
            .target(LatLng(lat, lng))
            .zoom(17f)
            .build()
        googleMap.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPosition))

        toLocationTxt.text = String.format("To: Near %s", address)

        if (startMarker != null){
            callDirectionsApi()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == CURRENT_PLACE_AUTOCOMPLETE_REQUEST_CODE){
            when (resultCode) {
                AutocompleteActivity.RESULT_OK -> {
                    val place = Autocomplete.getPlaceFromIntent(data!!)

                    val latLng = place.latLng.toString()
                    val location = latLng.substring(latLng.indexOf("(") + 1, latLng.indexOf(")"))
                    val loc = location.split(",")

                    setStartLocation(loc[0].toDouble(), loc[1].toDouble(), place.name!!)
                }
                AutocompleteActivity.RESULT_ERROR -> {
                    val status = Autocomplete.getStatusFromIntent(intent)
                    Toast.makeText(this, status.statusMessage, Toast.LENGTH_SHORT).show()
                }
                AutocompleteActivity.RESULT_CANCELED -> Toast.makeText(this, getString(R.string.request_cancelled), Toast.LENGTH_SHORT).show()
            }
        } else if (requestCode == DESTINATION_PLACE_AUTOCOMPLETE_REQUEST_CODE){
            when (resultCode) {
                AutocompleteActivity.RESULT_OK -> {
                    val place = Autocomplete.getPlaceFromIntent(data!!)

                    val latLng = place.latLng.toString()
                    val location = latLng.substring(latLng.indexOf("(") + 1, latLng.indexOf(")"))
                    val loc = location.split(",")

                    setFinishLocation(loc[0].toDouble(), loc[1].toDouble(), place.name!!)
                }
                AutocompleteActivity.RESULT_ERROR -> {
                    val status = Autocomplete.getStatusFromIntent(intent)
                    Toast.makeText(this, status.statusMessage, Toast.LENGTH_SHORT).show()
                }
                AutocompleteActivity.RESULT_CANCELED -> Toast.makeText(this, getString(R.string.request_cancelled), Toast.LENGTH_SHORT).show()
            }
        } else if (requestCode == REQUEST_CHECK_SETTINGS){
            if (resultCode == Activity.RESULT_OK) {
                getCurrentLocation()
            }
        }

    }

    private fun callDirectionsApi(){

        progressBar.visibility = View.VISIBLE

        // Origin of route
        val origin = startMarker!!.position.latitude.toString() + "," + startMarker!!.position.longitude.toString()

        // Destination of route
        val dest = finishMarker!!.position.latitude.toString() + "," + finishMarker!!.position.longitude.toString()

        val directionsCall = RetrofitClient.retrofitClient().getDirections(origin, dest,
            "Driving", BuildConfig.ApiKeyMap)
        directionsCall.enqueue(object : Callback<Directions> {
            override fun onResponse(call: Call<Directions>, response: Response<Directions>) {
                val directions = response.body()!!
                if (directions.status == "OK") {
                    val time = directions.routes[0].legs[0].duration.text
                    val distance = directions.routes[0].legs[0].distance.text
                    Toast.makeText(this@MainActivity, "Time: $time Distance: $distance", Toast.LENGTH_LONG).show()
                    drawPolyline(directions.routes[0].overviewPolyline.points)
                } else {
                    Toast.makeText(this@MainActivity, directions.status, Toast.LENGTH_LONG).show()
                }
                progressBar.visibility = View.GONE
            }

            override fun onFailure(call: Call<Directions>, t: Throwable) {
                progressBar.visibility = View.GONE
                Toast.makeText(this@MainActivity, t.message, Toast.LENGTH_LONG).show()
            }
        })
    }

    private fun drawPolyline(points: String){

        val polylineOptions = PolylineOptions()
        polylineOptions.geodesic(true)
        polylineOptions.color(ContextCompat.getColor(this, R.color.colorPrimary))
        polylineOptions.startCap(SquareCap())
        polylineOptions.endCap(SquareCap())

        val pointsList = PolyUtil.decode(points)
        for (point in pointsList) {
            polylineOptions.add(point)
        }

        googleMap.addPolyline(polylineOptions)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> true
            else -> super.onOptionsItemSelected(item)
        }
    }
}
