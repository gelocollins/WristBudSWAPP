package angelo.collins.wristbud.presentation

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.speech.RecognizerIntent
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.ScalingLazyColumn
import androidx.wear.compose.material.rememberScalingLazyListState
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.*
import kotlin.random.Random
import androidx.activity.result.ActivityResult
import angelo.collins.wristbud.presentation.PromptStage
import angelo.collins.wristbud.presentation.PhysicalPromptOverlay
import angelo.collins.wristbud.presentation.VoicePromptOverlay

@Composable
fun DemoModeScreen(
    sharedPrefs: SharedPreferences,
    onBack: () -> Unit
) {
    val listState = rememberScalingLazyListState()
    var isGenerating by remember { mutableStateOf(false) }
    var lastDemoResult by remember { mutableStateOf("") }
    var showPhysicalPrompt by remember { mutableStateOf(false) }
    var showVoicePrompt by remember { mutableStateOf(false) }
    var countdownSeconds by remember { mutableStateOf(10) }
    var promptStage by remember { mutableStateOf(PromptStage.NONE) }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    val speechLauncher = activity?.let {
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                val matches = result.data!!.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                if (!matches.isNullOrEmpty()) {
                    val spoken = matches[0].lowercase(Locale.getDefault()).trim()
                    Log.d("WristBud", "DemoMode spoken: $spoken")
                    when {
                        spoken.contains("yes") -> {
                            showVoicePrompt = false
                            promptStage = PromptStage.NONE
                            countdownSeconds = 10
                        }
                        spoken.contains("no") -> {
                            showVoicePrompt = false
                            promptStage = PromptStage.NONE
                            countdownSeconds = 10
                            // Set status to critical for the last generated data (simulate)
                            lastDemoResult = lastDemoResult + "\n[CRITICAL CONFIRMED]"
                        }
                    }
                }
            }
        }
    }

    var countdownJob by remember { mutableStateOf<Job?>(null) }

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
            countdownSeconds = 10
            countdownJob = coroutineScope.launch {
                delay(500)
                if (speechLauncher != null) startDemoSpeechRecognition(speechLauncher)
                while (countdownSeconds > 0 && showVoicePrompt) {
                    delay(1000)
                    countdownSeconds--
                    if (countdownSeconds % 3 == 0 && showVoicePrompt && speechLauncher != null) {
                        startDemoSpeechRecognition(speechLauncher)
                    }
                }
                if (showVoicePrompt && countdownSeconds <= 0) {
                    showVoicePrompt = false
                    promptStage = PromptStage.NONE
                }
            }
        }
    }

    ScalingLazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF050505))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Demo Mode",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = onBack) {
                    Text("Back", color = Color(0xFF0BAF5A))
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Synthetic Health Data",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Generate realistic health scenarios for testing emergency response systems",
                        color = Color.Gray,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        item {
            Button(
                onClick = {
                    if (!isGenerating) {
                        isGenerating = true
                        generateDemoData(sharedPrefs, "critical") { result ->
                            lastDemoResult = result
                            isGenerating = false
                            showPhysicalPrompt = true
                            promptStage = PromptStage.PHYSICAL
                        }
                    }
                },
                enabled = !isGenerating,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFE53935),
                    disabledContainerColor = Color.Gray
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                if (isGenerating) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Text("Generating...")
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🚨 Critical Emergency", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text("HR: 180+ | BP: 190/120+ | SpO₂: <85%", fontSize = 10.sp)
                    }
                }
            }
        }

        item {
            Button(
                onClick = {
                    if (!isGenerating) {
                        isGenerating = true
                        generateDemoData(sharedPrefs, "abnormal") { result ->
                            lastDemoResult = result
                            isGenerating = false
                        }
                    }
                },
                enabled = !isGenerating,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFFC107),
                    disabledContainerColor = Color.Gray
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                if (isGenerating) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Text("Generating...")
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("⚠️ Abnormal Values", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                        Text("Moderate health concerns", fontSize = 10.sp, color = Color.Black)
                    }
                }
            }
        }

        item {
            Button(
                onClick = {
                    if (!isGenerating) {
                        isGenerating = true
                        generateDemoData(sharedPrefs, "normal") { result ->
                            lastDemoResult = result
                            isGenerating = false
                        }
                    }
                },
                enabled = !isGenerating,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF0BAF5A),
                    disabledContainerColor = Color.Gray
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                if (isGenerating) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Text("Generating...")
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("✅ Normal Values", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text("Healthy baseline data", fontSize = 10.sp)
                    }
                }
            }
        }

        if (lastDemoResult.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1B2A)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "Last Result:",
                            color = Color(0xFF0BAF5A),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            lastDemoResult,
                            color = Color.White,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A0D0D)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "⚠️ Demo Mode Info",
                        color = Color(0xFFFFC107),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "This generates synthetic health data for testing. Critical scenarios will trigger emergency protocols including location tracking and emergency contact notifications.",
                        color = Color.Gray,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }

    if (showPhysicalPrompt) {
        PhysicalPromptOverlay(
            heartRate = 180,
            bloodPressure = "190/120",
            spo2 = 80,
            temperature = 104f,
            countdownSeconds = countdownSeconds,
            onYes = {
                showPhysicalPrompt = false
                promptStage = PromptStage.NONE
                countdownJob?.cancel()
                countdownSeconds = 10
            },
            onNo = {
                showPhysicalPrompt = false
                showVoicePrompt = true
                promptStage = PromptStage.VOICE
                countdownJob?.cancel()
                countdownSeconds = 10
            }
        )
    }
    if (showVoicePrompt) {
        VoicePromptOverlay(
            heartRate = 180,
            bloodPressure = "190/120",
            spo2 = 80,
            temperature = 104f,
            countdownSeconds = countdownSeconds
        )
    }
}

private fun generateDemoData(
    sharedPrefs: SharedPreferences,
    mode: String,
    onResult: (String) -> Unit
) {
    CoroutineScope(Dispatchers.IO).launch {
        try {
            val client = OkHttpClient.Builder()
                .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .build()

            val userId = sharedPrefs.getInt("user_id", -1)
            val token = sharedPrefs.getString("user_token", "") ?: ""

            if (userId == -1 || token.isEmpty()) {
                onResult("Error: Not logged in")
                return@launch
            }

            var serverIp = sharedPrefs.getString("server_ip", "192.168.8.38") ?: "192.168.1.100"
            if (serverIp.isBlank()) {
                serverIp = "192.168.8.83"
            }
            val endpoint = when (mode) {
                "critical" -> "http://$serverIp:5000/api/demo/critical"
                "abnormal" -> "http://$serverIp:5000/api/demo/abnormal"
                "normal" -> "http://$serverIp:5000/api/demo/normal"
                else -> {
                    onResult("Error: Invalid mode")
                    return@launch
                }
            }

            // Use fixed synthetic location
            val (latitude, longitude, address) = generateSyntheticLocation()
            
            val requestBody = JSONObject().apply {
                put("location_latitude", latitude)
                put("location_longitude", longitude)
                put("location_address", address)
                put("demo_mode", true)
            }.toString().toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url(endpoint)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Content-Type", "application/json")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (response.isSuccessful && responseBody != null) {
                val jsonResponse = JSONObject(responseBody)
                val message = jsonResponse.optString("message", "Demo data generated")
                val data = jsonResponse.optJSONObject("data")
                
                val resultText = if (data != null) {
                    "✅ $message\n" +
                    "HR: ${data.optInt("heart_rate")} BPM\n" +
                    "BP: ${data.optInt("systolic")}/${data.optInt("diastolic")} mmHg\n" +
                    "SpO₂: ${data.optInt("spo2")}%\n" +
                    "Temp: ${String.format("%.1f", data.optDouble("temperature"))}°F\n" +
                    "Location: $address ($latitude, $longitude)"
                } else {
                    "✅ $message"
                }
                
                Log.d("WristBud", "Demo mode $mode: $resultText")
                onResult(resultText)
            } else {
                val errorMsg = "Failed: ${response.code} - $responseBody"
                Log.e("WristBud", "Demo mode error: $errorMsg")
                onResult(errorMsg)
            }
        } catch (e: IOException) {
            val errorMsg = "Network error: ${e.message}"
            Log.e("WristBud", "Demo mode network error: $errorMsg")
            onResult(errorMsg)
        } catch (e: Exception) {
            val errorMsg = "Error: ${e.message}"
            Log.e("WristBud", "Demo mode error: $errorMsg")
            onResult(errorMsg)
        }
    }
}

private fun generateSyntheticLocation(): Triple<Double, Double, String> {
    // Always return the specified location for demo
    val latitude = 13.138375115992103
    val longitude = 123.73876824232873
    val address = "AMA legazpi city albay branch"
    return Triple(latitude, longitude, address)
}

// Data classes for demo mode
data class DemoHealthData(
    val heartRate: Int,
    val systolic: Int,
    val diastolic: Int,
    val spo2: Int,
    val temperature: Float,
    val status: String,
    val activity: String,
    val location: String
)

object DemoDataGenerator {
    fun generateCriticalData(): DemoHealthData {
        return DemoHealthData(
            heartRate = Random.nextInt(180, 200),
            systolic = Random.nextInt(180, 200),
            diastolic = Random.nextInt(110, 130),
            spo2 = Random.nextInt(75, 85),
            temperature = Random.nextFloat() * 2 + 103f, // 103-105°F
            status = "critical",
            activity = "Demo Mode - Critical Emergency",
            location = generateSyntheticLocation().third
        )
    }
    
    fun generateAbnormalData(): DemoHealthData {
        return DemoHealthData(
            heartRate = if (Random.nextBoolean()) Random.nextInt(45, 55) else Random.nextInt(140, 160),
            systolic = Random.nextInt(160, 180),
            diastolic = Random.nextInt(95, 110),
            spo2 = Random.nextInt(88, 92),
            temperature = if (Random.nextBoolean()) Random.nextFloat() + 94f else Random.nextFloat() + 100.5f,
            status = "abnormal",
            activity = "Demo Mode - Abnormal Values",
            location = generateSyntheticLocation().third
        )
    }
    
    fun generateNormalData(): DemoHealthData {
        return DemoHealthData(
            heartRate = Random.nextInt(60, 100),
            systolic = Random.nextInt(110, 130),
            diastolic = Random.nextInt(70, 85),
            spo2 = Random.nextInt(95, 100),
            temperature = Random.nextFloat() * 1.5f + 97.5f, // 97.5-99°F
            status = "normal",
            activity = "Demo Mode - Normal Activity",
            location = generateSyntheticLocation().third
        )
    }
}

private fun startDemoSpeechRecognition(speechLauncher: androidx.activity.result.ActivityResultLauncher<Intent>) {
    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        putExtra(RecognizerIntent.EXTRA_PROMPT, "Say 'Yes' if you are safe, or 'No' if not")
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
    }
    speechLauncher.launch(intent)
}