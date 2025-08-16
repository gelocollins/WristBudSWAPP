package angelo.collins.wristbud.presentation

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.ScalingLazyColumn
import androidx.wear.compose.material.rememberScalingLazyListState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

@Composable
fun LoginScreen(
    sharedPrefs: SharedPreferences,
    onLoginSuccess: () -> Unit = {}
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberScalingLazyListState()

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
            Spacer(modifier = Modifier.height(20.dp))
        }

        item {
            Text(
                "WristBud Login",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }

        item {
            Spacer(modifier = Modifier.height(10.dp))
        }

        item {
            OutlinedTextField(
                value = email,
                onValueChange = {
                    email = it
                    errorMessage = ""
                },
                label = { Text("Email") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedLabelColor = Color.White,
                    unfocusedLabelColor = Color.Gray,
                    focusedBorderColor = Color(0xFF0BAF5A),
                    unfocusedBorderColor = Color.Gray
                ),
                shape = RoundedCornerShape(8.dp),
                singleLine = true
            )
        }

        item {
            OutlinedTextField(
                value = password,
                onValueChange = {
                    password = it
                    errorMessage = ""
                },
                label = { Text("Password") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedLabelColor = Color.White,
                    unfocusedLabelColor = Color.Gray,
                    focusedBorderColor = Color(0xFF0BAF5A),
                    unfocusedBorderColor = Color.Gray
                ),
                shape = RoundedCornerShape(8.dp),
                singleLine = true
            )
        }

        if (errorMessage.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.Red.copy(alpha = 0.1f)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = errorMessage,
                        color = Color.Red,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(12.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        item {
            Button(
                onClick = {
                    if (email.isNotEmpty() && password.isNotEmpty()) {
                        isLoading = true
                        errorMessage = ""
                        loginUser(email, password, sharedPrefs, coroutineScope) { success, message ->
                            isLoading = false
                            if (success) {
                                onLoginSuccess()
                            } else {
                                errorMessage = message
                            }
                        }
                    } else {
                        errorMessage = "Please fill in all fields"
                    }
                },
                enabled = !isLoading && email.isNotEmpty() && password.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF0BAF5A),
                    disabledContainerColor = Color.Gray
                ),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                if (isLoading) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Text("Logging in...")
                    }
                } else {
                    Text("Login", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.Gray.copy(alpha = 0.1f)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "First time?",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Register on web first:",
                        color = Color.Gray,
                        fontSize = 10.sp
                    )
                    Text(
                        "http://10.0.2.2/wristbud/register.php",
                        color = Color(0xFF0BAF5A),
                        fontSize = 9.sp
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

fun loginUser(
    email: String,
    password: String,
    sharedPrefs: SharedPreferences,
    coroutineScope: CoroutineScope,
    callback: (Boolean, String) -> Unit
) {
    coroutineScope.launch(Dispatchers.IO) {
        try {
            val client = OkHttpClient.Builder()
                .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .build()

            val json = JSONObject().apply {
                put("email", email.trim())
                put("password", password)
            }

            val requestBody = json.toString().toRequestBody("application/json".toMediaType())
            val serverIp = sharedPrefs.getString("server_ip", "192.168.1.83") ?: "192.168.1.83"
            val request = Request.Builder()
                .url("http://$serverIp:5000/api/login")
                .post(requestBody)
                .addHeader("Content-Type", "application/json")
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            withContext(Dispatchers.Main) {
                try {
                    if (response.isSuccessful && responseBody != null) {
                        val jsonResponse = JSONObject(responseBody)
                        if (jsonResponse.has("user_id") && jsonResponse.has("token")) {
                            with(sharedPrefs.edit()) {
                                putString("user_token", jsonResponse.getString("token"))
                                putInt("user_id", jsonResponse.getInt("user_id"))
                                putString("user_email", jsonResponse.getString("email"))
                                putString("user_name", jsonResponse.optString("name", ""))
                                apply()
                            }
                            Log.d("WristBud", "Login successful for user: ${jsonResponse.getInt("user_id")}")
                            callback(true, jsonResponse.optString("message", "Login successful"))
                        } else {
                            val errorMsg = jsonResponse.optString("error", "Login failed")
                            Log.e("WristBud", "Login failed: $errorMsg")
                            callback(false, errorMsg)
                        }
                    } else {
                        if (responseBody != null) {
                            try {
                                val errorResponse = JSONObject(responseBody)
                                val errorMsg = errorResponse.optString("error", "Login failed")
                                Log.e("WristBud", "Login failed: $errorMsg")
                                callback(false, errorMsg)
                            } catch (e: Exception) {
                                Log.e("WristBud", "HTTP Error: ${response.code} - ${response.message}")
                                callback(false, "Server error: ${response.code}")
                            }
                        } else {
                            Log.e("WristBud", "HTTP Error: ${response.code} - ${response.message}")
                            callback(false, "Server error: ${response.code}")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("WristBud", "JSON parsing error: ${e.message}")
                    callback(false, "Invalid server response")
                }
            }
        } catch (e: IOException) {
            Log.e("WristBud", "Network error: ${e.message}")
            withContext(Dispatchers.Main) {
                callback(false, "Network error: Check your connection")
            }
        } catch (e: Exception) {
            Log.e("WristBud", "Unexpected error: ${e.message}")
            withContext(Dispatchers.Main) {
                callback(false, "Unexpected error occurred")
            }
        }
    }
}