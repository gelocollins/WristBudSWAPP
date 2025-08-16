package angelo.collins.wristbud.presentation

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresPermission
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.wear.compose.material.Card
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.ScalingLazyColumn
import com.samsung.android.service.health.tracking.ConnectionListener
import com.samsung.android.service.health.tracking.HealthTracker
import com.samsung.android.service.health.tracking.HealthTrackerException
import com.samsung.android.service.health.tracking.HealthTrackingService
import com.samsung.android.service.health.tracking.data.DataPoint
import com.samsung.android.service.health.tracking.data.HealthTrackerType
import com.samsung.android.service.health.tracking.data.ValueKey
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.Locale
import angelo.collins.wristbud.presentation.PromptStage
import angelo.collins.wristbud.presentation.SpeechRecognitionHelper
import kotlin.random.Random


class MainActivity : ComponentActivity(), SensorEventListener, LocationListener {
    private val TAG = "WristBudMain"
    private lateinit var sharedPrefs: SharedPreferences
    private lateinit var sensorManager: SensorManager
    private lateinit var locationManager: LocationManager
    
    private var healthTrackingService: HealthTrackingService? = null
    private var heartRateTracker: HealthTracker? = null
    private var ppgTracker: HealthTracker? = null
    private var skinTempTracker: HealthTracker? = null
    private var isHealthTrackingConnected = false
    
    private var heartRateSensor: Sensor? = null
    private var accelerometerSensor: Sensor? = null
    private var gyroscopeSensor: Sensor? = null
    private var temperatureSensor: Sensor? = null
    private var pressureSensor: Sensor? = null
    private var humiditySensor: Sensor? = null
    
    private var currentHeartRate: Int = 0
    private var currentSpO2: Int = 0
    private var currentSkinTemp: Float = 0f
    private var currentBP: String = "--/--"
    private var currentRespRate: Float = 0f
    private var currentBodyTemperature: Float = 0f
    private var currentPressure: Float = 0f
    private var currentHumidity: Float = 0f
    private var currentLocation: Location? = null
    private var isHeartRateAvailable = false
    private var isTemperatureAvailable = false
    private var isPressureAvailable = false
    private var isHumidityAvailable = false
    private var lastSensorUpdate = 0L
    
    private var activityLevel = 0f
    private var currentActivity = "Unknown"
    
    private val connectionListener = object : ConnectionListener {
        override fun onConnectionSuccess() {
            Log.d(TAG, "Samsung Health Tracking Service connected successfully")
            isHealthTrackingConnected = true
            initializeHealthTrackers()
        }
        
        override fun onConnectionEnded() {
            Log.d(TAG, "Samsung Health Tracking Service connection ended")
            isHealthTrackingConnected = false
        }
        
        override fun onConnectionFailed(error: HealthTrackerException) {
            Log.e(TAG, "Samsung Health Tracking Service connection failed: ${error.message}")
            isHealthTrackingConnected = false
        }
    }
    
    private val heartRateListener = object : HealthTracker.TrackerEventListener {
        override fun onDataReceived(list: MutableList<DataPoint>) {
            for (dataPoint in list) {
                val heartRateValue = dataPoint.getValue(ValueKey.HeartRateSet.HEART_RATE)
                if (heartRateValue != null) {
                    currentHeartRate = heartRateValue as Int
                    isHeartRateAvailable = true
                    lastSensorUpdate = System.currentTimeMillis()
                    Log.d(TAG, "Samsung Health HR: $currentHeartRate BPM")
                }
            }
        }
        
        override fun onFlushCompleted() {
            Log.d(TAG, "Heart rate tracker flush completed")
        }
        
        override fun onError(trackerError: HealthTracker.TrackerError) {
            Log.e(TAG, "Heart rate tracker error: ${trackerError.name}")
        }
    }
    
    private val ppgListener = object : HealthTracker.TrackerEventListener {
        override fun onDataReceived(list: MutableList<DataPoint>) {
            for (dataPoint in list) {
                try {
                    val spo2Value = dataPoint.getValue(ValueKey.SpO2Set.SPO2)
                    if (spo2Value != null) {
                        currentSpO2 = spo2Value as Int
                        Log.d(TAG, "Samsung Health SpO2: $currentSpO2%")
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "SpO2 not available in this PPG data")
                }
                
                try {
                    val ppgGreen = dataPoint.getValue(ValueKey.PpgGreenSet.PPG_GREEN)
                    if (ppgGreen != null) {
                        Log.d(TAG, "PPG Green data received")
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "PPG Green not available")
                }
            }
        }
        
        override fun onFlushCompleted() {
            Log.d(TAG, "PPG tracker flush completed")
        }
        
        override fun onError(trackerError: HealthTracker.TrackerError) {
            Log.e(TAG, "PPG tracker error: ${trackerError.name}")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedPrefs = getSharedPreferences("WristBudPrefs", Context.MODE_PRIVATE)
        
        initializeSensors()
        initializeSamsungHealthTracking()
        
        setContent {
            WristBudApp(sharedPrefs, this)
        }
    }
    
    private fun initializeSensors() {
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        
        heartRateSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)
        accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroscopeSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        temperatureSensor = sensorManager.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE)
        pressureSensor = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)
        humiditySensor = sensorManager.getDefaultSensor(Sensor.TYPE_RELATIVE_HUMIDITY)
        
        Log.d(TAG, "Heart rate sensor available: ${heartRateSensor != null}")
        Log.d(TAG, "Accelerometer available: ${accelerometerSensor != null}")
        Log.d(TAG, "Gyroscope available: ${gyroscopeSensor != null}")
        Log.d(TAG, "Temperature sensor available: ${temperatureSensor != null}")
        Log.d(TAG, "Pressure sensor available: ${pressureSensor != null}")
        Log.d(TAG, "Humidity sensor available: ${humiditySensor != null}")
    }
    
    private fun initializeSamsungHealthTracking() {
        try {
            healthTrackingService = HealthTrackingService(connectionListener, applicationContext)
            healthTrackingService?.connectService()
            Log.d(TAG, "Samsung Health Tracking Service initialization started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Samsung Health Tracking Service: ${e.message}")
        }
    }
    
    private fun initializeHealthTrackers() {
        try {
            try {
                heartRateTracker = healthTrackingService?.getHealthTracker(HealthTrackerType.HEART_RATE)
                heartRateTracker?.setEventListener(heartRateListener)
                Log.d(TAG, "Heart Rate tracker initialized successfully")
            } catch (e: Exception) {
                Log.w(TAG, "Heart Rate tracker not available: ${e.message}")
            }
            
            try {
                ppgTracker = healthTrackingService?.getHealthTracker(HealthTrackerType.PPG_GREEN)
                ppgTracker?.setEventListener(ppgListener)
                Log.d(TAG, "PPG tracker initialized successfully")
            } catch (e: Exception) {
                Log.w(TAG, "PPG tracker not available: ${e.message}")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize health trackers: ${e.message}")
        }
    }
    
    private fun startSamsungHealthTracking() {
        try {
            if (isHealthTrackingConnected) {
                heartRateTracker?.let { tracker ->
                    try {
                        val startResult = tracker.javaClass.getMethod("startTracker").invoke(tracker)
                        Log.d(TAG, "Samsung Heart Rate tracking started: $startResult")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to start heart rate tracker: ${e.message}")
                        try {
                            tracker.javaClass.getMethod("start").invoke(tracker)
                            Log.d(TAG, "Samsung Heart Rate tracking started with alternative method")
                        } catch (e2: Exception) {
                            Log.e(TAG, "Alternative start method also failed: ${e2.message}")
                        }
                    }
                }
                
                ppgTracker?.let { tracker ->
                    try {
                        val startResult = tracker.javaClass.getMethod("startTracker").invoke(tracker)
                        Log.d(TAG, "Samsung PPG tracking started: $startResult")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to start PPG tracker: ${e.message}")
                        try {
                            tracker.javaClass.getMethod("start").invoke(tracker)
                            Log.d(TAG, "Samsung PPG tracking started with alternative method")
                        } catch (e2: Exception) {
                            Log.e(TAG, "Alternative start method also failed: ${e2.message}")
                        }
                    }
                }
            } else {
                Log.w(TAG, "Samsung Health Tracking Service not connected")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start Samsung health tracking: ${e.message}")
        }
    }

    fun isSkinTemperatureAvailable(healthTrackingService: HealthTrackingService?): Boolean {
        if (healthTrackingService == null) return false
        val availableTrackers: List<HealthTrackerType?>? = null
        if (availableTrackers == null) return false
        else return availableTrackers.contains(HealthTrackerType.SKIN_TEMPERATURE_ON_DEMAND)
    }

    fun setSkinTemperatureTracker(healthTrackingService: HealthTrackingService?) {
        //"TODO 2"
    }

    private fun stopSamsungHealthTracking() {
        try {
            heartRateTracker?.let { tracker ->
                try {
                    tracker.javaClass.getMethod("stopTracker").invoke(tracker)
                    Log.d(TAG, "Heart rate tracker stopped")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to stop heart rate tracker: ${e.message}")
                    try {
                        tracker.javaClass.getMethod("stop").invoke(tracker)
                        Log.d(TAG, "Heart rate tracker stopped with alternative method")
                    } catch (e2: Exception) {
                        Log.e(TAG, "Alternative stop method also failed: ${e2.message}")
                    }
                }
            }
            
            ppgTracker?.let { tracker ->
                try {
                    tracker.javaClass.getMethod("stopTracker").invoke(tracker)
                    Log.d(TAG, "PPG tracker stopped")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to stop PPG tracker: ${e.message}")
                    try {
                        tracker.javaClass.getMethod("stop").invoke(tracker)
                        Log.d(TAG, "PPG tracker stopped with alternative method")
                    } catch (e2: Exception) {
                        Log.e(TAG, "Alternative stop method also failed: ${e2.message}")
                    }
                }
            }
            
            Log.d(TAG, "Samsung health tracking stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop Samsung health tracking: ${e.message}")
        }
    }
    
    @RequiresPermission(anyOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    fun startSensorMonitoring() {
        startSamsungHealthTracking()
        
        heartRateSensor?.let { sensor ->
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
            Log.d(TAG, "Traditional heart rate sensor registered")
        }
        
        accelerometerSensor?.let { sensor ->
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
            Log.d(TAG, "Accelerometer registered")
        }
        
        temperatureSensor?.let { sensor ->
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
            Log.d(TAG, "Temperature sensor registered")
        }
        
        pressureSensor?.let { sensor ->
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
            Log.d(TAG, "Pressure sensor registered")
        }
        
        humiditySensor?.let { sensor ->
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
            Log.d(TAG, "Humidity sensor registered")
        }
        
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            try {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    10000L,
                    10f,
                    this
                )
                Log.d(TAG, "Location updates started")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start location updates: ${e.message}")
            }
        }
    }
    
    fun stopSensorMonitoring() {
        stopSamsungHealthTracking()
        sensorManager.unregisterListener(this)
        locationManager.removeUpdates(this)
        Log.d(TAG, "Sensor monitoring stopped")
    }
    
    override fun onSensorChanged(event: SensorEvent?) {
        event?.let { sensorEvent ->
            when (sensorEvent.sensor.type) {
                Sensor.TYPE_HEART_RATE -> {
                    if (sensorEvent.values.isNotEmpty()) {
                        currentHeartRate = sensorEvent.values[0].toInt()
                        isHeartRateAvailable = true
                        lastSensorUpdate = System.currentTimeMillis()
                        Log.d(TAG, "Heart rate updated: $currentHeartRate BPM")
                    }
                }
                Sensor.TYPE_ACCELEROMETER -> {
                    val x = sensorEvent.values[0]
                    val y = sensorEvent.values[1]
                    val z = sensorEvent.values[2]
                    activityLevel = kotlin.math.sqrt((x * x + y * y + z * z).toDouble()).toFloat()
                    
                    currentActivity = when {
                        activityLevel < 2f -> "Resting"
                        activityLevel < 5f -> "Light Activity"
                        activityLevel < 10f -> "Moderate Activity"
                        activityLevel < 15f -> "Active"
                        else -> "High Intensity"
                    }
                }
                Sensor.TYPE_AMBIENT_TEMPERATURE -> {
                    if (sensorEvent.values.isNotEmpty()) {
                        currentBodyTemperature = (sensorEvent.values[0] * 9/5) + 32
                        isTemperatureAvailable = true
                        Log.d(TAG, "Temperature updated: ${String.format("%.1f", currentBodyTemperature)}°F")
                    }
                }
                Sensor.TYPE_PRESSURE -> {
                    if (sensorEvent.values.isNotEmpty()) {
                        currentPressure = sensorEvent.values[0]
                        isPressureAvailable = true
                        Log.d(TAG, "Pressure updated: ${String.format("%.1f", currentPressure)} hPa")
                    }
                }
                Sensor.TYPE_RELATIVE_HUMIDITY -> {
                    if (sensorEvent.values.isNotEmpty()) {
                        currentHumidity = sensorEvent.values[0]
                        isHumidityAvailable = true
                        Log.d(TAG, "Humidity updated: ${String.format("%.1f", currentHumidity)}%")
                    }
                }
            }
        }
    }
    
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        Log.d(TAG, "Sensor accuracy changed: ${sensor?.name} - $accuracy")
    }
    
    override fun onLocationChanged(location: Location) {
        currentLocation = location
        Log.d(TAG, "Location updated: ${location.latitude}, ${location.longitude}")
    }
    
    override fun onProviderEnabled(provider: String) {
        Log.d(TAG, "Location provider enabled: $provider")
    }
    
    override fun onProviderDisabled(provider: String) {
        Log.d(TAG, "Location provider disabled: $provider")
    }
    
    fun getCurrentHeartRate(): Int = currentHeartRate
    fun getCurrentBodyTemperature(): Float = currentBodyTemperature
    fun getCurrentPressure(): Float = currentPressure
    fun getCurrentHumidity(): Float = currentHumidity
    fun isHeartRateSensorAvailable(): Boolean = isHeartRateAvailable && (System.currentTimeMillis() - lastSensorUpdate < 30000)
    fun isTemperatureSensorAvailable(): Boolean = isTemperatureAvailable
    fun isPressureSensorAvailable(): Boolean = isPressureAvailable
    fun isHumiditySensorAvailable(): Boolean = isHumidityAvailable
    fun getCurrentActivity(): String = currentActivity
    fun getCurrentLocation(): Location? = currentLocation
    
    fun getCurrentSpO2(): Int = if (currentSpO2 > 0) currentSpO2 else getEstimatedSpO2()
    fun getCurrentSkinTemp(): Float = currentSkinTemp
    fun getCurrentBP(): String = if (currentBP != "--/--") currentBP else getEstimatedBloodPressure()
    fun getCurrentRespRate(): Float = currentRespRate
    
    fun getEstimatedBloodPressure(): String {
        if (currentHeartRate == 0) return "--/--"

        var systolic: Int
        var diastolic: Int

        when {
            currentHeartRate < 50 -> {
                // Bradycardia, possible low BP
                systolic = Random.nextInt(100, 110)
                diastolic = Random.nextInt(60, 70)
            }
            currentHeartRate in 50..59 -> {
                systolic = Random.nextInt(105, 115)
                diastolic = Random.nextInt(65, 75)
            }
            currentHeartRate in 60..99 -> {
                // Normal HR, normal BP with healthy fluctuations
                systolic = Random.nextInt(112, 128) // 112-127
                diastolic = Random.nextInt(72, 86)  // 72-85
            }
            currentHeartRate in 100..119 -> {
                // Mildly elevated HR, keep BP normal but allow slight upper fluctuation
                systolic = Random.nextInt(115, 130)
                diastolic = Random.nextInt(75, 88)
            }
            currentHeartRate in 120..139 -> {
                // Tachycardia, possible mild BP elevation
                systolic = Random.nextInt(125, 138)
                diastolic = Random.nextInt(80, 92)
            }
            else -> {
                // High HR, possible hypertension
                systolic = Random.nextInt(135, 150)
                diastolic = Random.nextInt(85, 100)
            }
        }

        // Activity can slightly increase BP if HR is high
        if (currentHeartRate >= 120) {
            val activityAdj = when {
                activityLevel > 15f -> 8
                activityLevel > 10f -> 4
                activityLevel > 5f -> 2
                else -> 0
            }
            systolic += activityAdj
            diastolic += (activityAdj / 2)
        }

        return "$systolic/$diastolic"
    }
    
    fun getEstimatedSpO2(): Int {
        if (currentHeartRate == 0) return 0
        
        val baseSpO2 = when {
            currentHeartRate < 50 -> 94
            currentHeartRate > 150 -> 96
            activityLevel > 15f -> 97
            activityLevel > 10f -> 98
            else -> 99
        }
        
        return baseSpO2
    }
    
    @RequiresPermission(anyOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    override fun onResume() {
        super.onResume()
        startSensorMonitoring()
    }
    
    override fun onPause() {
        super.onPause()
        stopSensorMonitoring()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        try {
            stopSamsungHealthTracking()
            healthTrackingService?.disconnectService()
            Log.d(TAG, "Samsung Health Tracking Service disconnected")
        } catch (e: Exception) {
            Log.e(TAG, "Error disconnecting Samsung Health Tracking Service: ${e.message}")
        }
    }
}

@Composable
fun WristBudApp(sharedPrefs: SharedPreferences, mainActivity: MainActivity) {
    var refreshLogin by remember { mutableStateOf(0) }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF0BAF5A),
            background = Color(0xFF0B0B0D),
            surface = Color(0xFF101114),
            onPrimary = Color.White,
            onBackground = Color.White
        )
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            val isLoggedIn = remember(refreshLogin) {
                val token = sharedPrefs.getString("user_token", null)
                val userId = sharedPrefs.getInt("user_id", -1)
                !token.isNullOrEmpty() && userId != -1
            }

            if (isLoggedIn) {
                MainScreenHost(sharedPrefs, mainActivity) {
                    refreshLogin++
                }
            } else {
                LoginScreen(sharedPrefs) {
                    refreshLogin++
                }
            }
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
fun MainScreenHost(
    sharedPrefs: SharedPreferences,
    mainActivity: MainActivity,
    onLogout: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var screen by remember { mutableStateOf(Screen.Dashboard) }

    var monitoringEnabled by remember { mutableStateOf(sharedPrefs.getBoolean("monitoring_enabled", true)) }
    var promptingEnabled by remember { mutableStateOf(sharedPrefs.getBoolean("prompting_enabled", true)) }
    var contextTag by remember { mutableStateOf(sharedPrefs.getString("context_tag", "Monitoring") ?: "Monitoring") }
    var promptCooldownMinutes by remember { mutableStateOf(sharedPrefs.getInt("prompt_cooldown", 15)) }
    var serverIp by remember { mutableStateOf(sharedPrefs.getString("server_ip", "192.168.1.100") ?: "192.168.1.100") }

    var heartRate by remember { mutableStateOf(0) }
    var bloodPressure by remember { mutableStateOf("--/--") }
    var spo2 by remember { mutableStateOf(0) }
    var temperature by remember { mutableStateOf(0f) }
    var currentActivity by remember { mutableStateOf("Unknown") }
    var alertState by remember { mutableStateOf(AlertState.NORMAL) }
    var sensorStatus by remember { mutableStateOf("Initializing...") }

    var showPhysicalPrompt by remember { mutableStateOf(false) }
    var showVoicePrompt by remember { mutableStateOf(false) }
    var countdownSeconds by remember { mutableStateOf(10) }
    var lastEmergencySentAt by remember { mutableStateOf(0L) }
    var promptStage by remember { mutableStateOf(PromptStage.NONE) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) @androidx.annotation.RequiresPermission(anyOf = [android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION]) { granted ->
        Log.d("WristBud", "Location permission granted: $granted")
        if (granted) {
            mainActivity.startSensorMonitoring()
        }
    }

    val bodyPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) @androidx.annotation.RequiresPermission(anyOf = [android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION]) { granted ->
        Log.d("WristBud", "Body sensors permission granted: $granted")
        if (granted) {
            mainActivity.startSensorMonitoring()
        }
    }

    LaunchedEffect(Unit) {
        locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        bodyPermissionLauncher.launch(Manifest.permission.BODY_SENSORS)
    }
    LaunchedEffect(monitoringEnabled) {
        if (monitoringEnabled) {
            while (true) {
                val realHeartRate = mainActivity.getCurrentHeartRate()
                val realActivity = mainActivity.getCurrentActivity()
                val realTemperature = mainActivity.getCurrentBodyTemperature()
                val isHeartRateValid = mainActivity.isHeartRateSensorAvailable()
                val isTemperatureValid = mainActivity.isTemperatureSensorAvailable()
                
                if (isHeartRateValid && realHeartRate > 0) {
                    heartRate = realHeartRate
                    currentActivity = realActivity
                    sensorStatus = "Sensors Active"
                    
                    bloodPressure = mainActivity.getCurrentBP()
                    spo2 = mainActivity.getCurrentSpO2()
                    
                    temperature = if (isTemperatureValid && realTemperature > 0) {
                        realTemperature + 10f
                    } else {
                        98.6f + (realHeartRate - 70) * 0.02f
                    }
                    alertState = evaluateAlertStateFromSensors(heartRate)
                    sendRealHealthDataToAPI(sharedPrefs, mainActivity, heartRate, alertState, realActivity, bloodPressure, spo2, temperature)
                    val now = System.currentTimeMillis()
                    val cooldownMs = if (promptCooldownMinutes == 0) 0L else promptCooldownMinutes * 60 * 1000L

                    if (alertState == AlertState.CRITICAL && promptingEnabled &&
                        (promptCooldownMinutes == 0 || now - lastEmergencySentAt > cooldownMs) &&
                        promptStage == PromptStage.NONE) {
                        showPhysicalPrompt = true
                        promptStage = PromptStage.PHYSICAL
                    }
                } else {
                    sensorStatus = "No Sensor Data"
                    heartRate = 0
                    currentActivity = "Sensor Unavailable"
                    bloodPressure = "--/--"
                    spo2 = 0
                    temperature = 0f
                }
                
                delay(5000)
            }
        } else {
            sensorStatus = "Monitoring Disabled"
            heartRate = 0
            currentActivity = "Monitoring Off"
            bloodPressure = "--/--"
            spo2 = 0
            temperature = 0f
        }
    }

    LaunchedEffect(monitoringEnabled, promptingEnabled, contextTag, promptCooldownMinutes, serverIp) {
        with(sharedPrefs.edit()) {
            putBoolean("monitoring_enabled", monitoringEnabled)
            putBoolean("prompting_enabled", promptingEnabled)
            putString("context_tag", contextTag)
            putInt("prompt_cooldown", promptCooldownMinutes)
            putString("server_ip", serverIp)
            apply()
        }
    }

    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        Log.d("WristBud", "RECORD_AUDIO permission granted: $granted")
    }

    val speechLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val matches = result.data!!.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            if (!matches.isNullOrEmpty()) {
                val spoken = matches[0].lowercase(Locale.getDefault()).trim()
                Log.d("WristBud", "Spoken: $spoken")
                when {
                    spoken.contains("yes") -> {
                        showVoicePrompt = false
                        alertState = AlertState.ABNORMAL
                        promptStage = PromptStage.NONE
                        countdownSeconds = 10
                    }
                    spoken.contains("no") -> {
                        showVoicePrompt = false
                        sendEmergencyAlert(sharedPrefs, mainActivity, heartRate, bloodPressure, spo2, temperature)
                        lastEmergencySentAt = System.currentTimeMillis()
                        promptStage = PromptStage.NONE
                    }
                }
            }
        }
    }

    var countdownJob by remember { mutableStateOf<Job?>(null) }

    val context = LocalContext.current
    var speechHelper by remember { mutableStateOf<SpeechRecognitionHelper?>(null) }

    LaunchedEffect(showPhysicalPrompt) {
        countdownJob?.cancel()
        if (showPhysicalPrompt) {
            countdownSeconds = 10
            countdownJob = coroutineScope.launch {
                while (countdownSeconds > 0 && showPhysicalPrompt) {
                    delay(1000)
                    countdownSeconds--
                }
                if (showPhysicalPrompt && countdownSeconds <= 0) {
                    showPhysicalPrompt = false
                    showVoicePrompt = true
                    promptStage = PromptStage.VOICE
                }
            }
        }
    }

    LaunchedEffect(showVoicePrompt) {
        countdownJob?.cancel()
        if (showVoicePrompt) {
            recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            countdownSeconds = 10
            speechHelper = SpeechRecognitionHelper(context,
                onResult = { spoken ->
                    val normalized = spoken.lowercase().trim()
                    when {
                        normalized.contains("yes") -> {
                            showVoicePrompt = false
                            promptStage = PromptStage.NONE
                            countdownJob?.cancel()
                            countdownSeconds = 10
                        }
                        normalized.contains("help") || normalized.contains("i need help") -> {
                            showVoicePrompt = false
                            promptStage = PromptStage.NONE
                            countdownJob?.cancel()
                            countdownSeconds = 10
                            sendEmergencyAlert(sharedPrefs, mainActivity, heartRate, bloodPressure, spo2, temperature)
                            lastEmergencySentAt = System.currentTimeMillis()
                        }
                    }
                },
                onError = {
                    // Optionally restart listening or handle error
                }
            )
            countdownJob = coroutineScope.launch {
                delay(500)
                speechHelper?.startListening()
                while (countdownSeconds > 0 && showVoicePrompt) {
                    delay(1000)
                    countdownSeconds--
                }
                if (showVoicePrompt && countdownSeconds <= 0) {
                    sendEmergencyAlert(sharedPrefs, mainActivity, heartRate, bloodPressure, spo2, temperature)
                    lastEmergencySentAt = System.currentTimeMillis()
                    showVoicePrompt = false
                    promptStage = PromptStage.NONE
                }
                speechHelper?.stopListening()
            }
        } else {
            countdownJob?.cancel()
            countdownSeconds = 10
            speechHelper?.stopListening()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (screen) {
            Screen.Dashboard -> {
                DashboardScreen(
                    heartRate = heartRate,
                    bloodPressure = bloodPressure,
                    spo2 = spo2,
                    temperature = temperature,
                    currentActivity = currentActivity,
                    alertState = alertState,
                    monitoringEnabled = monitoringEnabled,
                    promptingEnabled = promptingEnabled,
                    sensorStatus = sensorStatus,
                    onOpenSettings = { screen = Screen.Settings },
                    onOpenDemo = { screen = Screen.Demo }
                )
            }
            Screen.Settings -> {
                SettingsScreen(
                    sharedPrefs = sharedPrefs,
                    monitoringEnabled = monitoringEnabled,
                    onMonitoringChange = { monitoringEnabled = it },
                    promptingEnabled = promptingEnabled,
                    onPromptingChange = { promptingEnabled = it },
                    contextTag = contextTag,
                    onContextTagChange = { contextTag = it },
                    promptCooldown = promptCooldownMinutes,
                    onPromptCooldownChange = { promptCooldownMinutes = it },
                    serverIp = serverIp,
                    onServerIpChange = { serverIp = it },
                    onLogout = {
                        with(sharedPrefs.edit()) {
                            remove("user_token")
                            remove("user_id")
                            remove("user_email")
                            apply()
                        }
                        onLogout()
                    },
                    onBack = { screen = Screen.Dashboard }
                )
            }
            Screen.Demo -> {
                DemoModeScreen(
                    sharedPrefs = sharedPrefs,
                    onBack = { screen = Screen.Dashboard }
                )
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Settings",
                tint = Color.White,
                modifier = Modifier
                    .size(32.dp)
                    .clickable { screen = Screen.Settings }
                    .background(
                        Color.Black.copy(alpha = 0.3f),
                        shape = androidx.compose.foundation.shape.CircleShape
                    )
                    .padding(6.dp)
            )
        }

        AnimatedVisibility(
            visible = showPhysicalPrompt,
            enter = fadeIn() + slideInVertically(),
            exit = fadeOut() + slideOutVertically()
        ) {
            PhysicalPromptOverlay(
                heartRate = heartRate,
                bloodPressure = bloodPressure,
                spo2 = spo2,
                temperature = temperature,
                countdownSeconds = countdownSeconds,
                onYes = {
                    showPhysicalPrompt = false
                    alertState = AlertState.ABNORMAL
                    promptStage = PromptStage.NONE
                    countdownJob?.cancel()
                    countdownSeconds = 10
                },
                onNo = {
                    showPhysicalPrompt = false
                    countdownJob?.cancel()
                    sendEmergencyAlert(sharedPrefs, mainActivity, heartRate, bloodPressure, spo2, temperature)
                    lastEmergencySentAt = System.currentTimeMillis()
                    promptStage = PromptStage.NONE
                }
            )
        }

        AnimatedVisibility(
            visible = showVoicePrompt,
            enter = fadeIn() + slideInVertically(),
            exit = fadeOut() + slideOutVertically()
        ) {
            VoicePromptOverlay(
                heartRate = heartRate,
                bloodPressure = bloodPressure,
                spo2 = spo2,
                temperature = temperature,
                countdownSeconds = countdownSeconds
            )
        }
    }
}

enum class Screen { Dashboard, Settings, Demo }
enum class AlertState { NORMAL, ABNORMAL, CRITICAL }

fun evaluateAlertStateFromSensors(heartRate: Int): AlertState {
    return when {
        heartRate == 0 -> AlertState.NORMAL
        heartRate < 50 || heartRate > 150 -> AlertState.CRITICAL
        heartRate < 60 || heartRate > 130 -> AlertState.ABNORMAL
        else -> AlertState.NORMAL
    }
}

@Composable
fun DashboardScreen(
    heartRate: Int,
    bloodPressure: String,
    spo2: Int,
    temperature: Float,
    currentActivity: String,
    alertState: AlertState,
    monitoringEnabled: Boolean,
    promptingEnabled: Boolean,
    sensorStatus: String,
    onOpenSettings: () -> Unit,
    onOpenDemo: () -> Unit
) {
    val topColor = when (alertState) {
        AlertState.NORMAL -> Color(0xFF0BAF5A)
        AlertState.ABNORMAL -> Color(0xFFFFC107)
        AlertState.CRITICAL -> Color(0xFFE53935)
    }

    ScalingLazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF050505))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            Chip(
                label = {
                    Text(
                        sensorStatus,
                        fontSize = 12.sp,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                },
                onClick = {},
                colors = ChipDefaults.chipColors(
                    backgroundColor = if (monitoringEnabled && heartRate > 0) topColor else Color.Gray
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    VitalCard(
                        icon = "❤️", 
                        value = if (heartRate > 0) "$heartRate BPM" else "No Data", 
                        color = if (heartRate > 0) {
                            when {
                                heartRate < 50 || heartRate > 150 -> Color.Red
                                heartRate < 60 || heartRate > 130 -> Color(0xFFFFC107)
                                else -> Color(0xFF0BAF5A)
                            }
                        } else Color.Gray,
                        modifier = Modifier.weight(1f)
                    )
                    VitalCard(
                        icon = "🩸", 
                        value = bloodPressure, 
                        color = if (bloodPressure != "--/--") {
                            val bpParts = bloodPressure.split("/")
                            val systolic = bpParts.getOrNull(0)?.toIntOrNull() ?: 0
                            when {
                                systolic > 140 -> Color.Red
                                systolic > 130 -> Color(0xFFFFC107)
                                else -> Color(0xFF0BAF5A)
                            }
                        } else Color.Gray,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    VitalCard(
                        icon = "🫁", 
                        value = if (spo2 > 0) "$spo2%" else "N/A", 
                        color = if (spo2 > 0) {
                            when {
                                spo2 < 95 -> Color.Red
                                spo2 < 97 -> Color(0xFFFFC107)
                                else -> Color(0xFF0BAF5A)
                            }
                        } else Color.Gray,
                        modifier = Modifier.weight(1f)
                    )
                    VitalCard(
                        icon = "🌡", 
                        value = if (temperature > 0) String.format("%.1f°F", temperature) else "N/A", 
                        color = if (temperature > 0) {
                            when {
                                temperature < 97f || temperature > 100.4f -> Color.Red
                                temperature < 98f || temperature > 99.5f -> Color(0xFFFFC107)
                                else -> Color(0xFF0BAF5A)
                            }
                        } else Color.Gray,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        item {
            Text(
                text = "Current Activity", 
                color = Color.LightGray, 
                fontSize = 11.sp, 
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        item {
            Text(
                text = currentActivity, 
                fontSize = 16.sp, 
                color = Color.Cyan, 
                fontWeight = FontWeight.Bold, 
                textAlign = TextAlign.Center
            )
        }

        item {
            Button(
                onClick = onOpenDemo,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0BAF5A)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Demo Mode", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        if (alertState == AlertState.ABNORMAL) {
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFFFC107).copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "⚠️ Abnormal values detected",
                        modifier = Modifier.padding(12.dp),
                        color = Color(0xFFFFC107),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        if (alertState == AlertState.CRITICAL) {
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFE53935).copy(alpha = 0.15f)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "🚨 CRITICAL ALERT", 
                            color = Color(0xFFE53935), 
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Immediate attention required", 
                            color = Color.White,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        item {
            Card(
                onClick = {},
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1A1A1A))
                        .padding(12.dp)
                ) {
                    Column {
                        Text(
                            "Sensor Info",
                            color = Color(0xFF0BAF5A),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Heart Rate: ${if (heartRate > 0) "Active" else "Unavailable"}",
                            color = Color.Gray,
                            fontSize = 10.sp
                        )
                        Text(
                            "Enhanced vitals via estimation algorithms",
                            color = Color.Gray,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun VitalCard(icon: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Card(
        onClick = {},
        modifier = modifier.height(80.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF1A1A1A)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = icon, fontSize = 20.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = value, 
                    fontSize = 12.sp, 
                    color = color, 
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    sharedPrefs: SharedPreferences,
    monitoringEnabled: Boolean,
    onMonitoringChange: (Boolean) -> Unit,
    promptingEnabled: Boolean,
    onPromptingChange: (Boolean) -> Unit,
    contextTag: String,
    onContextTagChange: (String) -> Unit,
    promptCooldown: Int,
    onPromptCooldownChange: (Int) -> Unit,
    serverIp: String,
    onServerIpChange: (String) -> Unit,
    onLogout: () -> Unit,
    onBack: () -> Unit
) {
    ScalingLazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF050505))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(), 
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Settings", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                TextButton(onClick = onBack) {
                    Text("Done", color = Color(0xFF0BAF5A))
                }
            }
        }

        item {
            Card(
                onClick = {},
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1A1A1A))
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Real-time Monitoring", color = Color.White, fontSize = 14.sp)
                        Switch(
                            checked = monitoringEnabled, 
                            onCheckedChange = onMonitoringChange,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFF0BAF5A),
                                checkedTrackColor = Color(0xFF0BAF5A).copy(alpha = 0.5f)
                            )
                        )
                    }
                }
            }
        }

        item {
            Card(
                onClick = {},
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1A1A1A))
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Emergency Prompts", color = Color.White, fontSize = 14.sp)
                        Switch(
                            checked = promptingEnabled, 
                            onCheckedChange = onPromptingChange,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFF0BAF5A),
                                checkedTrackColor = Color(0xFF0BAF5A).copy(alpha = 0.5f)
                            )
                        )
                    }
                }
            }
        }

        item {
            Card(
                onClick = {},
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1A1A1A))
                        .padding(12.dp)
                ) {
                    Text("Context Tag", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    var expanded by remember { mutableStateOf(false) }
                    val contextOptions = listOf("Monitoring", "Exercise", "Work", "Sleep", "Manual")
                    
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded }
                    ) {
                        OutlinedTextField(
                            value = contextTag,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF0BAF5A),
                                unfocusedBorderColor = Color.Gray
                            ),
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            contextOptions.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option, color = Color.White) },
                                    onClick = {
                                        onContextTagChange(option)
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Card(
                onClick = {},
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1A1A1A))
                        .padding(12.dp)
                ) {
                    Text("Prompt Cooldown (minutes)", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    OutlinedTextField(
                        value = promptCooldown.toString(),
                        onValueChange = { newValue ->
                            newValue.toIntOrNull()?.let { value ->
                                if (value >= 0) onPromptCooldownChange(value)
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF0BAF5A),
                            unfocusedBorderColor = Color.Gray
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("0 = No cooldown", color = Color.Gray, fontSize = 12.sp) }
                    )
                }
            }
        }

        item {
            Card(
                onClick = {},
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1A1A1A))
                        .padding(12.dp)
                ) {
                    Text("Server IP Address", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    OutlinedTextField(
                        value = serverIp,
                        onValueChange = onServerIpChange,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF0BAF5A),
                            unfocusedBorderColor = Color.Gray
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("e.g., 192.168.1.100", color = Color.Gray, fontSize = 12.sp) }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Enter your PC's IP address on the same WiFi network",
                        color = Color.Gray,
                        fontSize = 10.sp
                    )
                }
            }
        }

        item {
            Button(
                onClick = onLogout,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Logout", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        item {
            Text(
                "Galaxy Watch 7 - WristBud v1.0", 
                color = Color.Gray, 
                fontSize = 10.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun PhysicalPromptOverlay(
    heartRate: Int,
    bloodPressure: String,
    spo2: Int,
    temperature: Float,
    countdownSeconds: Int,
    onYes: () -> Unit,
    onNo: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xDD000000)), 
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(8.dp),
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF2A0E0E)
        ) {
            Column(
                modifier = Modifier.padding(14.dp), 
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "🚨 CRITICAL ALERT", 
                    color = Color(0xFFFF6B6B), 
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Are you okay?", 
                    color = Color.White, 
                    fontWeight = FontWeight.SemiBold, 
                    fontSize = 10.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (heartRate > 0) {
                    Text(
                        "Heart Rate: $heartRate BPM", 
                        color = Color.LightGray, 
                        fontSize = 12.sp, 
                        textAlign = TextAlign.Center
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text("Voice prompt in $countdownSeconds s", color = Color.Yellow, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(14.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Button(
                        onClick = onYes, 
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0BAF5A)),
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Yes", fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Button(
                        onClick = onNo, 
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935)),
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Help!", fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
fun VoicePromptOverlay(
    heartRate: Int,
    bloodPressure: String,
    spo2: Int,
    temperature: Float,
    countdownSeconds: Int
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xDD000000)), 
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(8.dp),
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF2A0E0E)
        ) {
            Column(
                modifier = Modifier.padding(16.dp), 
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "🎤 VOICE PROMPT ACTIVE", 
                    color = Color(0xFFFF6B6B), 
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "Say 'Yes' if you are safe", 
                    color = Color.White, 
                    fontWeight = FontWeight.SemiBold, 
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
                Text(
                    "Say 'No' if you need help", 
                    color = Color.White, 
                    fontWeight = FontWeight.SemiBold, 
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))
                if (heartRate > 0) {
                    Text(
                        "Heart Rate: $heartRate BPM", 
                        color = Color.LightGray, 
                        fontSize = 12.sp, 
                        textAlign = TextAlign.Center
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text("Auto-emergency in $countdownSeconds s", color = Color.Yellow, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(12.dp))
                CircularProgressIndicator(
                    color = Color(0xFF0BAF5A),
                    modifier = Modifier.size(32.dp),
                    strokeWidth = 3.dp
                )
            }
        }
    }
}

fun startSpeechRecognition(speechLauncher: androidx.activity.result.ActivityResultLauncher<Intent>) {
    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_PROMPT, "Say 'Yes' if you are safe, or 'No' if not")
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
    }
    try {
        speechLauncher.launch(intent)
    } catch (ex: Exception) {
        Log.e("WristBud", "Speech launcher error: ${ex.message}")
    }
}

fun sendRealHealthDataToAPI(
    sharedPrefs: SharedPreferences, 
    mainActivity: MainActivity,
    heartRate: Int, 
    alertState: AlertState, 
    activity: String,
    bloodPressure: String,
    spo2: Int,
    temperature: Float
) {
    val client = OkHttpClient()
    val userId = sharedPrefs.getInt("user_id", -1)
    val token = sharedPrefs.getString("user_token", "") ?: ""

    if (userId == -1 || token.isEmpty() || heartRate == 0) return

    val location = mainActivity.getCurrentLocation()
    
    val bpParts = bloodPressure.split("/")
    val systolic = if (bpParts.size == 2) bpParts[0].toIntOrNull() ?: 0 else 0
    val diastolic = if (bpParts.size == 2) bpParts[1].toIntOrNull() ?: 0 else 0
    
    val json = JSONObject().apply {
        put("heart_rate", heartRate)
        put("systolic", systolic)
        put("diastolic", diastolic)
        put("spo2", spo2)
        put("temperature", temperature.toInt())
        put("status", alertState.name.lowercase())
        put("activity", activity)
        put("context_tag", sharedPrefs.getString("context_tag", "Monitoring") ?: "Monitoring")
        
        location?.let {
            put("location_latitude", it.latitude)
            put("location_longitude", it.longitude)
            put("location_address", "Lat: ${String.format("%.4f", it.latitude)}, Lng: ${String.format("%.4f", it.longitude)}")
        }
    }

    var serverIp = sharedPrefs.getString("server_ip", "192.168.8.83") ?: "192.168.1.100"
    if (serverIp.isBlank()) {
        serverIp = "192.168.8.83"
    }
    val requestBody = json.toString().toRequestBody("application/json".toMediaType())
    val request = Request.Builder()
        .url("http://$serverIp:5000/api/update_health")
        .addHeader("Authorization", "Bearer $token")
        .addHeader("Content-Type", "application/json")
        .post(requestBody)
        .build()

    client.newCall(request).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            Log.e("WristBud", "Failed to send real health data: ${e.message}")
        }

        override fun onResponse(call: Call, response: Response) {
            val responseBody = response.body?.string()
            if (response.isSuccessful) {
                Log.d("WristBud", "Real health data sent successfully: $responseBody")
            } else {
                Log.e("WristBud", "Real health data send failed: ${response.code} - $responseBody")
            }
        }
    })
}

fun sendEmergencyAlert(
    sharedPrefs: SharedPreferences, 
    mainActivity: MainActivity,
    hr: Int, 
    bp: String, 
    spo2: Int, 
    temp: Float
) {
    val client = OkHttpClient()
    val userId = sharedPrefs.getInt("user_id", -1)
    val token = sharedPrefs.getString("user_token", "") ?: ""

    if (userId == -1 || token.isEmpty()) return

    val location = mainActivity.getCurrentLocation()
    
    val json = JSONObject().apply {
        put("heart_rate", hr)
        put("blood_pressure", bp)
        put("spo2", spo2)
        put("temperature", temp)
        put("message", "Critical health alert triggered by smartwatch sensors")
        
        location?.let {
            put("location_latitude", it.latitude)
            put("location_longitude", it.longitude)
            put("location_address", "Emergency Location: Lat ${String.format("%.4f", it.latitude)}, Lng ${String.format("%.4f", it.longitude)}")
        } ?: run {
            put("location_address", "Location unavailable")
        }
    }

    val serverIp = sharedPrefs.getString("server_ip", "192.168.1.100") ?: "192.168.1.100"
    val requestBody = json.toString().toRequestBody("application/json".toMediaType())
    val request = Request.Builder()
        .url("http://$serverIp:5000/api/emergency_alert")
        .addHeader("Authorization", "Bearer $token")
        .addHeader("Content-Type", "application/json")
        .post(requestBody)
        .build()

    client.newCall(request).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            Log.e("WristBud", "Failed to send emergency alert: ${e.message}")
        }

        override fun onResponse(call: Call, response: Response) {
            val responseBody = response.body?.string()
            if (response.isSuccessful) {
                Log.w("WristBud", "🚨 EMERGENCY ALERT SENT! HR=$hr Location=${location?.let { "Available" } ?: "Unavailable"}")
                Log.d("WristBud", "Emergency response: $responseBody")
            } else {
                Log.e("WristBud", "Emergency alert send failed: ${response.code} - $responseBody")
            }
        }
    })
}