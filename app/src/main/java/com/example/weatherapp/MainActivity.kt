package com.example.weatherapp

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.location.LocationRequest
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.example.weatherapp.Model.WeatherResponse
import com.example.weatherapp.Network.WeatherService
import com.example.weatherapp.databinding.ActivityMainBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import retrofit2.*
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private var binding: ActivityMainBinding?=null
    private lateinit var mFusedLocationClient: FusedLocationProviderClient
    private var mProgressDialog: Dialog?=null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding= ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding?.root)

        mFusedLocationClient=LocationServices.getFusedLocationProviderClient(this)


        if(!isLocationEnabled()){
            Toast.makeText(this,"Location turned off. Please turn it on", Toast.LENGTH_LONG).show()

            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(intent)
        }else{
            Dexter.withContext(this)
                .withPermissions(
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                ).withListener(object :MultiplePermissionsListener{
                    override fun onPermissionsChecked(report: MultiplePermissionsReport?) {

                        if(report!!.areAllPermissionsGranted()){
                            requestLocationData()
                        }
                        if (report.isAnyPermissionPermanentlyDenied){
                            Toast.makeText(this@MainActivity,"You have denied location permission. Please enable them as it is mandatory for app to work",
                                   Toast.LENGTH_LONG).show()
                        }
                    }

                    override fun onPermissionRationaleShouldBeShown(
                        permission: MutableList<PermissionRequest>?,
                        token: PermissionToken?
                    ) {
                        showRationalDialogForPermission()
                    }

                }).onSameThread()
                .check()
        }

    }

    @SuppressLint("MissingPermission")
    private fun requestLocationData(){
        val mLocationRequest= com.google.android.gms.location.LocationRequest()
        mLocationRequest.priority=LocationRequest.QUALITY_HIGH_ACCURACY
        mFusedLocationClient.requestLocationUpdates(
            mLocationRequest,mLocationCallback,
            Looper.myLooper()
        )

    }

    private val mLocationCallback=object : LocationCallback(){
        override fun onLocationResult(locationResult: LocationResult) {
            val mLastLocation: Location? =locationResult.lastLocation
            val latitude=mLastLocation?.latitude
//            Log.i("Latitude",latitude.toString())
            val longitude=mLastLocation?.longitude
//            Log.i("Longitude",longitude.toString())
            getLocationWeatherDetails(latitude!!,longitude!!)

        }
    }

    private fun getLocationWeatherDetails(latitude:Double,longitude:Double){
        if(Constance.isNetworkAvailable(this)){


            val listCall: Call<WeatherResponse> = WeatherService.weatherInstance.getWeather(
                latitude,longitude,Constance.METRIC_UNIT,Constance.APP_ID)
            showCustomProgressDialog()
            listCall.enqueue(object : Callback<WeatherResponse>{
                override fun onResponse(
                    call: Call<WeatherResponse>,
                    response: Response<WeatherResponse>
                ) {
                    if (response.isSuccessful){

                        hideProgressDialog()
                        val weatherList: WeatherResponse? =response.body()
                        setupUI(weatherList!!)
//                        Log.i("weather","$weatherList")
                    }else{
                        val rc=response.code()
                        when(rc){
                            400 -> {
                                Log.e("Error 400","Bad Connection")
                            }
                            404 ->{
                                Log.e("Error 404","Not Found")
                            }else ->{
                                Log.e("Error","Generic Error")
                            }
                        }
                    }
                }

                override fun onFailure(call: Call<WeatherResponse>, t: Throwable) {
                    hideProgressDialog()
                    Log.e("Errorr",t.message.toString())
                }

            })
        }else{
            Toast.makeText(this@MainActivity,"Turn on the internet connection",Toast.LENGTH_LONG).show()
        }
    }
    //Rational permission custom dialog
    private fun showRationalDialogForPermission(){
        AlertDialog.Builder(this)
            .setMessage("It Looks like you have turned off permissions required for this feature. It can be enable under Application Settings")
            .setPositiveButton("GO TO SETTINGS"){
                _,_->
                try{
                    val intent=Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    val uri= Uri.fromParts("package",packageName,null)
                    intent.data=uri
                    startActivity(intent)
                }catch (e: ActivityNotFoundException){
                    e.printStackTrace()
                }
            }
            .setNegativeButton("Cancel"){
                dialog,
                    _ ->
                dialog.dismiss()
            }.show()
    }
    private fun isLocationEnabled(): Boolean{
        val locationManager: LocationManager=getSystemService(Context.LOCATION_SERVICE) as LocationManager

        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                || locationManager.isProviderEnabled(
            LocationManager.NETWORK_PROVIDER)

    }

    // Custom dialog
   private fun showCustomProgressDialog(){
       mProgressDialog= Dialog(this)
       mProgressDialog!!.setContentView((R.layout.dialog_custom_progress))
       mProgressDialog!!.show()
   }

    private fun hideProgressDialog(){
        if(mProgressDialog !=null){
            mProgressDialog!!.dismiss()
        }
    }

    private fun setupUI(weatherList: WeatherResponse){
        for(i in weatherList.weather.indices){
            Log.i("Weather Name", weatherList.weather.toString())
            binding?.tvMain?.text=weatherList.weather[i].main
            binding?.tvMainDescription?.text=weatherList.weather[i].description
            binding?.tvTemp?.text=weatherList.main.temp.toString() + getUnit(application.resources.configuration.locales.toString())
            binding?.tvSunriseTime?.text=unixTime(weatherList.sys.sunrise)
            binding?.tvSunsetTime?.text=unixTime(weatherList.sys.sunset)
        }
    }

    private fun getUnit(value: String): String? {

        var value="°C"

        if("US"== value || "LR"==value || "MM"==value){
            value="°F"
        }
        return value
    }

    private fun unixTime(timex: Long): String?{
        val date=Date(timex* 1000L)
        val sdf=SimpleDateFormat("HH:mm", Locale.UK)
        sdf.timeZone= TimeZone.getDefault()
        return sdf.format(date)
    }

}