package uni.fmi.bachelors.weatherapp.activities

import android.Manifest
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.location.*
import com.google.gson.Gson
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import retrofit2.*
import retrofit2.converter.gson.GsonConverterFactory
import uni.fmi.bachelors.*
import uni.fmi.bachelors.weatherapp.Constants
import uni.fmi.bachelors.weatherapp.Models.WeatherResponse
import uni.fmi.bachelors.weatherapp.Network.WeatherService
import uni.fmi.bachelors.weatherapp.R
import uni.fmi.bachelors.weatherapp.databinding.ActivityMainBinding
import java.text.SimpleDateFormat
import java.util.*


class MainActivity : AppCompatActivity() {

    private lateinit var mFusedLocationClient : FusedLocationProviderClient

    private lateinit var binding: ActivityMainBinding

    private lateinit var mSharedPreferences: SharedPreferences

    private var mProgressDialog: Dialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        mSharedPreferences = getSharedPreferences(Constants.PREFERENCE_NAME, Context.MODE_PRIVATE)

        setupUI()

        if(!isLocationEnabled()){
            Toast.makeText(this,
                "Location service is turned off. Please turn it on.",
                Toast.LENGTH_SHORT
            ).show()
            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(intent)
        }else{
            Dexter.withContext(this).withPermissions(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ).withListener(object: MultiplePermissionsListener {
                override fun onPermissionsChecked(report: MultiplePermissionsReport?) {
                    if (report!!.areAllPermissionsGranted()) {
                        requestLocationData()
                    }
                    if (report.isAnyPermissionPermanentlyDenied) {
                        Toast.makeText(
                            this@MainActivity,
                            "You have denied location permission",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                override fun onPermissionRationaleShouldBeShown(
                    permissions: MutableList<PermissionRequest>?,
                    token: PermissionToken?
                ) {
                    showRationDialogForPermisions()
                }
            }).onSameThread().check()
        }
    }

    private fun isLocationEnabled(): Boolean{
        // provide access to location service
        val locationManager: LocationManager =
            getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    private fun showRationDialogForPermisions(){
        AlertDialog.Builder(this).setMessage("You have turned off permissions")
            .setPositiveButton("Go to Settings"){_,_->
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    val uri = Uri.fromParts("package",packageName,null)
                    intent.data = uri
                    startActivity(intent)
                }catch (e: ActivityNotFoundException){
                    e.printStackTrace()
                }
            }
            .setNegativeButton("Cancel"){dialog, _ ->
                dialog.dismiss()
            }.show()
    }

    @SuppressLint( "MissingPermission")
    private fun requestLocationData(){
        val mLocationRequest = LocationRequest.create().apply {
            interval = 1
            fastestInterval = 1
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
            maxWaitTime= 1000
        }
        mFusedLocationClient.requestLocationUpdates(mLocationRequest,mLocationCallback, Looper.getMainLooper())

    }

    private val mLocationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            val mLastLocation: Location = locationResult.lastLocation
            val latitude = mLastLocation.latitude
            Log.i("Current latitude","$latitude")
            val longitude = mLastLocation.longitude
            Log.i("Current longitude","$longitude")
            getLocationWeatherDetails(latitude,longitude)
        }

    }

    private fun getLocationWeatherDetails(lattitude: Double, longitude: Double){
        if(Constants.isNetworkAvailable(this)){
            showCustomProgressDialog()

            val retrofit: Retrofit = Retrofit.Builder().baseUrl(Constants.BASE_URL)
                .addConverterFactory(GsonConverterFactory.create()).build()

            val service: WeatherService = retrofit.create(WeatherService::class.java)

            val listCall: Call<WeatherResponse> = service.getWeather(
                lattitude, longitude, Constants.METRIC_UNIT, Constants.APP_ID
            )

            listCall.enqueue(object: Callback<WeatherResponse>{
                @SuppressLint("SetTextI18n")
                override fun onResponse(
                    call: Call<WeatherResponse>,
                    response: Response<WeatherResponse>
                ) {
                    hideCustomProgressDialog()
                    if (response.isSuccessful) {
                        hideCustomProgressDialog()
                        val weatherList: WeatherResponse? = response.body()
                        Log.i("Response result", "$weatherList")

                        val weatherResponseJsonString = Gson().toJson(weatherList)
                        val editor = mSharedPreferences.edit()
                        editor.putString(Constants.WEATHER_RESPONSE_DATA, weatherResponseJsonString)
                        editor.apply()

                        setupUI()

                    }else{
                        val rc = response.code()
                        when(rc) {
                            400 -> {Log.e("Error 400", "Bad Connection")}
                            404 -> {Log.e("Error 404", "Not Found")}
                            else -> {Log.e("Error","Basic Error")}
                        }
                    }
                }

                override fun onFailure(call: Call<WeatherResponse>, t: Throwable) {
                    Log.e("Error",t.message.toString())
                    hideCustomProgressDialog()
                }

            })
        }else{
            Toast.makeText(this@MainActivity, "No net", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showCustomProgressDialog(){
        mProgressDialog = Dialog(this)
        mProgressDialog!!.setContentView(R.layout.dialog_custom_progress)
        mProgressDialog!!.show()
    }
    private fun hideCustomProgressDialog(){
        if (mProgressDialog != null){
            mProgressDialog!!.dismiss()
        }
    }
    @SuppressLint("SetTextI18n")
    private fun setupUI() {
        val weatherResponseJsonString = mSharedPreferences.getString(Constants.WEATHER_RESPONSE_DATA,"")
        if (!weatherResponseJsonString.isNullOrEmpty()){
            val weatherList = Gson().fromJson(weatherResponseJsonString,WeatherResponse::class.java)

            for (z in weatherList.weather.indices){
                Log.i("Namee",weatherList.weather[z].main)

                binding.tvMain.text = weatherList.weather[z].main
                binding.tvMainDescription.text = weatherList.weather[z].description
                binding.tvTemp.text = weatherList.main.temp.toString() + getUnit(application.resources.configuration.locales.toString())
                binding.tvHumidity.text = weatherList.main.humidity.toString() + "%"
                binding.tvMin.text = weatherList.main.temp_min.toString() + " min"
                binding.tvMax.text = weatherList.main.temp_max.toString() + " max"
                binding.tvSpeed.text = weatherList.wind.speed.toString()
                binding.tvName.text = weatherList.name
                binding.tvCountry.text = weatherList.sys.country
                binding.tvSunriseTime.text = unixTime(weatherList.sys.sunrise.toLong())
                binding.tvSunsetTime.text = unixTime(weatherList.sys.sunset.toLong())
                when (weatherList.weather[z].icon) {

                    "01d" ->  binding.ivMain.setImageResource(R.drawable.sunny)
                    "02d" ->  binding.ivMain.setImageResource(R.drawable.cloud)
                    "03d" ->  binding.ivMain.setImageResource(R.drawable.cloud)
                    "04d" ->  binding.ivMain.setImageResource(R.drawable.cloud)
                    "10d" ->  binding.ivMain.setImageResource(R.drawable.rain)
                    "11d" ->  binding.ivMain.setImageResource(R.drawable.storm)
                    "04n" ->  binding.ivMain.setImageResource(R.drawable.cloud)
                    "13d" ->  binding.ivMain.setImageResource(R.drawable.snowflake)
                    "01n" ->  binding.ivMain.setImageResource(R.drawable.cloud)
                    "02n" ->  binding.ivMain.setImageResource(R.drawable.cloud)
                    "03n" ->  binding.ivMain.setImageResource(R.drawable.cloud)
                    "10n" ->  binding.ivMain.setImageResource(R.drawable.cloud)
                    "11n" ->  binding.ivMain.setImageResource(R.drawable.rain)
                    "13n" ->  binding.ivMain.setImageResource(R.drawable.snowflake)
                }
            }
        }
    }
    @Suppress("NAME_SHADOWING")
    private fun getUnit(value: String): String? {
        Log.i("unitttttt", value)
        var value = "°C"
        if ("US" == value || "LR" == value || "MM" == value) {
            value = "°F"
        }
        return value
    }

    private fun unixTime(timex: Long): String? {
        val date = Date(timex * 1000L)
        @SuppressLint("SimpleDateFormat") val sdf =
            SimpleDateFormat("HH:mm:ss")
        sdf.timeZone = TimeZone.getDefault()
        return sdf.format(date)
    }
}
