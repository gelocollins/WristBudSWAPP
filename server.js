const express = require("express"),
  mysql = require("mysql2/promise"),
  bcrypt = require("bcrypt"),
  jwt = require("jsonwebtoken"),
  cors = require("cors"),
  bodyParser = require("body-parser"),
  os = require("os");

const app = express(),
  PORT = 5000,
  HOST = "0.0.0.0",
  JWT_SECRET = "abcd";

app.use(cors()), app.use(bodyParser.json()), app.use(express.json());

const dbConfig = {
    host: "localhost",
    user: "root",
    password: "",
    database: "wristbud",
    waitForConnections: true,
    connectionLimit: 10,
    queueLimit: 0,
  },
  pool = mysql.createPool(dbConfig),
  authenticateToken = (req, res, next) => {
    const authHeader = req.headers.authorization,
      token = authHeader && authHeader.split(" ")[1];
    if (!token) return res.status(401).json({ error: "Access token required" });
    jwt.verify(token, JWT_SECRET, (err, user) => {
      if (err)
        return res.status(403).json({ error: "Invalid or expired token" });
      (req.user = user), next();
    });
  },
  authenticateAdmin = async (req, res, next) => {
    try {
      const userId = req.user.userId;
      const [users] = await pool.execute(
        "SELECT id, email, role FROM users WHERE id = ?",
        [userId]
      );
      
      if (users.length === 0) {
        return res.status(404).json({ error: "User not found" });
      }
      
      const user = users[0];
      if (user.role !== 'admin') {
        return res.status(403).json({ error: "Admin access required" });
      }
      
      next();
    } catch (error) {
      console.error("Admin authentication error:", error);
      res.status(500).json({ error: "Internal server error" });
    }
  };

app.get("/health", (req, res) => {
  res.json({ status: "OK", timestamp: new Date().toISOString() });
});

app.get("/api", (req, res) => {
  res.json({
    message: "WristBud API Server",
    version: "1.0.0",
    endpoints: {
      health: "/health",
      auth: { register: "POST /api/register", login: "POST /api/login" },
      user: {
        profile: "GET /api/profile",
        updateProfile: "PUT /api/profile",
        settings: { get: "GET /api/settings", update: "PUT /api/settings" },
      },
      health_data: {
        update: "POST /api/update_health",
        get: "GET /api/health_data",
      },
      alerts: {
        emergency: "POST /api/emergency_alert",
        list: "GET /api/alerts",
      },
      demo: {
        critical: "POST /api/demo/critical",
        abnormal: "POST /api/demo/abnormal",
        normal: "POST /api/demo/normal",
      },
      admin: {
        users: "GET /api/admin/users",
        userHealth: "GET /api/admin/user/:userId/health",
        userAlerts: "GET /api/admin/user/:userId/alerts",
        criticalUsers: "GET /api/critical-users",
        smsStatus: "POST /api/sms-status",
      },
    },
    timestamp: new Date().toISOString(),
  });
});

app.post("/api/register", async (req, res) => {
  try {
    const {
      email,
      password,
      name,
      emergency_contact,
      emergency_phone,
    } = req.body;
    if (!email || !password || !name) {
      return res.status(400).json({ error: "Email, password, and name are required" });
    }
    const [existingUsers] = await pool.execute(
      "SELECT id FROM users WHERE email = ?",
      [email]
    );
    if (existingUsers.length > 0) {
      return res.status(409).json({ error: "User already exists" });
    }
    const saltRounds = 10;
    const hashedPassword = await bcrypt.hash(password, saltRounds);
    const [result] = await pool.execute(
      "INSERT INTO users (email, password, name, emergency_contact, emergency_phone, created_at) VALUES (?, ?, ?, ?, ?, NOW())",
      [
        email,
        hashedPassword,
        name,
        emergency_contact || null,
        emergency_phone || null,
      ]
    );
    if (!result.insertId) {
      return res.status(500).json({ error: "Failed to register user. Please try again." });
    }
    const token = jwt.sign({ userId: result.insertId, email: email }, JWT_SECRET, {
      expiresIn: "24h",
    });
    res.status(201).json({
      message: "User registered successfully",
      user_id: result.insertId,
      token: token,
      email: email,
    });
  } catch (error) {
    console.error("Registration error:", error);
    res.status(500).json({ error: "Internal server error" });
  }
});

app.post("/api/login", async (req, res) => {
  try {
    const { email, password } = req.body;
    console.log("🔐 Login attempt for:", email);
    
    if (!email || !password)
      return res
        .status(400)
        .json({ error: "Email and password are required" });
    
    const [users] = await pool.execute(
      "SELECT id, email, password, name FROM users WHERE email = ?",
      [email]
    );
    
    if (users.length === 0) {
      console.log("❌ User not found:", email);
      return res.status(401).json({ error: "Invalid credentials" });
    }
    
    const user = users[0];
    console.log("👤 User found:", user.email, "ID:", user.id);
    console.log("🔑 Stored password hash:", user.password.substring(0, 20) + "...");
    
    let passwordMatch = false;
    
    if (user.password.startsWith('$2y$')) {
      console.log("🔍 Detected PHP password_hash format");
      const bcryptHash = user.password.replace('$2y$', '$2b$');
      passwordMatch = await bcrypt.compare(password, bcryptHash);
    } else if (user.password.startsWith('$2b$')) {
      console.log("🔍 Using bcrypt verification");
      passwordMatch = await bcrypt.compare(password, user.password);
    } else {
      console.log("🔍 Unknown password hash format");
      passwordMatch = false;
    }
    
    console.log("🔐 Password match result:", passwordMatch);
    
    if (!passwordMatch) {
      console.log("❌ Password verification failed");
      return res.status(401).json({ error: "Invalid credentials" });
    }
    
    const token = jwt.sign({ userId: user.id, email: user.email }, JWT_SECRET, {
      expiresIn: "24h",
    });
    
    console.log("✅ Login successful for user:", user.id);
    
    res.json({
      message: "Login successful",
      user_id: user.id,
      token: token,
      email: user.email,
      name: user.name,
    });
  } catch (error) {
    console.error("Login error:", error);
    res.status(500).json({ error: "Internal server error" });
  }
});

app.post("/api/update_health", authenticateToken, async (req, res) => {
  try {
    const {
        heart_rate: heart_rate,
        systolic: systolic,
        diastolic: diastolic,
        spo2: spo2,
        temperature: temperature,
        status: status,
        activity: activity,
        context_tag: context_tag,
        location_latitude: location_latitude,
        location_longitude: location_longitude,
        location_address: location_address,
      } = req.body,
      userId = req.user.userId;
    if (!(heart_rate && systolic && diastolic && spo2 && temperature))
      return res
        .status(400)
        .json({ error: "All health metrics are required" });
    const [result] = await pool.execute(
      "INSERT INTO health_data (user_id, heart_rate, systolic, diastolic, spo2, temperature, status, activity, context_tag, location_latitude, location_longitude, location_address, recorded_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())",
      [
        userId,
        heart_rate,
        systolic,
        diastolic,
        spo2,
        temperature,
        status || "normal",
        activity || null,
        context_tag || null,
        location_latitude || null,
        location_longitude || null,
        location_address || null,
      ]
    );
    if (status === "critical") {
      await pool.execute(
        "INSERT INTO alerts (user_id, alert_type, message, severity, location_latitude, location_longitude, location_address, created_at) VALUES (?, 'health_critical', ?, 'high', ?, ?, ?, NOW())",
        [
          userId,
          `Critical health values detected: HR=${heart_rate}, BP=${systolic}/${diastolic}, SpO2=${spo2}%, Temp=${temperature}°F`,
          location_latitude || null,
          location_longitude || null,
          location_address || null,
        ]
      );
    }
    res.json({
      message: "Health data updated successfully",
      data_id: result.insertId,
      timestamp: new Date().toISOString(),
    });
  } catch (error) {
    console.error("Health data update error:", error);
    res.status(500).json({ error: "Internal server error" });
  }
});

app.get("/api/health_data", authenticateToken, async (req, res) => {
  try {
    const userId = req.user.userId,
      limit = parseInt(req.query.limit) || 10,
      [healthData] = await pool.execute(
        "SELECT * FROM health_data WHERE user_id = ? ORDER BY recorded_at DESC LIMIT ?",
        [userId, limit]
      );
    res.json({ data: healthData, count: healthData.length });
  } catch (error) {
    console.error("Get health data error:", error);
    res.status(500).json({ error: "Internal server error" });
  }
});

app.post("/api/emergency_alert", authenticateToken, async (req, res) => {
  try {
    const {
        heart_rate: heart_rate,
        blood_pressure: blood_pressure,
        spo2: spo2,
        temperature: temperature,
        location: location,
        location_latitude: location_latitude,
        location_longitude: location_longitude,
        location_address: location_address,
        message: message,
      } = req.body,
      userId = req.user.userId,
      [users] = await pool.execute(
        "SELECT name, email, emergency_contact, emergency_phone FROM users WHERE id = ?",
        [userId]
      );
    if (users.length === 0)
      return res.status(404).json({ error: "User not found" });
    const user = users[0],
      alertMessage = `EMERGENCY ALERT for ${user.name}: HR=${heart_rate}, BP=${blood_pressure}, SpO2=${spo2}%, Temp=${temperature}°F. Location: ${location_address || location || 'Unknown'}. ${message || "Immediate assistance required."}`,
      [alertResult] = await pool.execute(
        "INSERT INTO alerts (user_id, alert_type, message, severity, location_latitude, location_longitude, location_address, created_at) VALUES (?, 'emergency', ?, 'critical', ?, ?, ?, NOW())",
        [userId, alertMessage, location_latitude || null, location_longitude || null, location_address || location || null]
      );
    await pool.execute(
      "INSERT INTO emergency_events (user_id, heart_rate, blood_pressure, spo2, temperature, location_latitude, location_longitude, location_address, message, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())",
      [
        userId,
        heart_rate,
        blood_pressure,
        spo2,
        temperature,
        location_latitude || null,
        location_longitude || null,
        location_address || location || null,
        message || "Auto-generated emergency alert",
      ]
    );
    console.log("🚨 EMERGENCY ALERT TRIGGERED:", {
      user: user.name,
      email: user.email,
      emergency_contact: user.emergency_contact,
      emergency_phone: user.emergency_phone,
      vitals: {
        heart_rate: heart_rate,
        blood_pressure: blood_pressure,
        spo2: spo2,
        temperature: temperature,
      },
      location: location_address || location || 'Unknown',
      timestamp: new Date().toISOString(),
    });
    res.json({
      message: "Emergency alert sent successfully",
      alert_id: alertResult.insertId,
      emergency_contact: user.emergency_contact,
      emergency_phone: user.emergency_phone,
      timestamp: new Date().toISOString(),
    });
  } catch (error) {
    console.error("Emergency alert error:", error);
    res.status(500).json({ error: "Internal server error" });
  }
});

app.get("/api/alerts", authenticateToken, async (req, res) => {
  try {
    const userId = req.user.userId,
      limit = parseInt(req.query.limit) || 20,
      [alerts] = await pool.execute(
        "SELECT * FROM alerts WHERE user_id = ? ORDER BY created_at DESC LIMIT ?",
        [userId, limit]
      );
    res.json({ alerts: alerts, count: alerts.length });
  } catch (error) {
    console.error("Get alerts error:", error);
    res.status(500).json({ error: "Internal server error" });
  }
});

app.post("/api/demo/critical", authenticateToken, async (req, res) => {
  try {
    const userId = req.user.userId;
    const { location_latitude, location_longitude, location_address } = req.body;
    
    const demoData = {
      heart_rate: 180 + Math.floor(20 * Math.random()),
      systolic: 180 + Math.floor(20 * Math.random()),
      diastolic: 110 + Math.floor(10 * Math.random()),
      spo2: 85 + Math.floor(5 * Math.random()),
      temperature: 103 + 2 * Math.random(),
      status: "critical",
      activity: "Demo Mode - Critical",
      context_tag: "Demo",
    };
    
    await pool.execute(
      "INSERT INTO health_data (user_id, heart_rate, systolic, diastolic, spo2, temperature, status, activity, context_tag, location_latitude, location_longitude, location_address, recorded_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())",
      [
        userId,
        demoData.heart_rate,
        demoData.systolic,
        demoData.diastolic,
        demoData.spo2,
        demoData.temperature,
        demoData.status,
        demoData.activity,
        demoData.context_tag,
        location_latitude || null,
        location_longitude || null,
        location_address || "Demo Location - Critical Emergency",
      ]
    );
    res.json({ message: "Critical demo data generated", data: demoData });
  } catch (error) {
    console.error("Demo critical error:", error);
    res.status(500).json({ error: "Internal server error" });
  }
});

app.post("/api/demo/abnormal", authenticateToken, async (req, res) => {
  try {
    const userId = req.user.userId;
    const { location_latitude, location_longitude, location_address } = req.body;
    
    const demoData = {
      heart_rate:
        Math.random() > 0.5
          ? 45 + Math.floor(5 * Math.random())
          : 140 + Math.floor(10 * Math.random()),
      systolic: 160 + Math.floor(10 * Math.random()),
      diastolic: 95 + Math.floor(5 * Math.random()),
      spo2: 90 + Math.floor(2 * Math.random()),
      temperature:
        Math.random() > 0.5
          ? 94 + 1 * Math.random()
          : 100.5 + 1 * Math.random(),
      status: "abnormal",
      activity: "Demo Mode - Abnormal",
      context_tag: "Demo",
    };
    
    await pool.execute(
      "INSERT INTO health_data (user_id, heart_rate, systolic, diastolic, spo2, temperature, status, activity, context_tag, location_latitude, location_longitude, location_address, recorded_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())",
      [
        userId,
        demoData.heart_rate,
        demoData.systolic,
        demoData.diastolic,
        demoData.spo2,
        demoData.temperature,
        demoData.status,
        demoData.activity,
        demoData.context_tag,
        location_latitude || null,
        location_longitude || null,
        location_address || "Demo Location - Abnormal Values",
      ]
    );
    res.json({ message: "Abnormal demo data generated", data: demoData });
  } catch (error) {
    console.error("Demo abnormal error:", error);
    res.status(500).json({ error: "Internal server error" });
  }
});

app.post("/api/demo/normal", authenticateToken, async (req, res) => {
  try {
    const userId = req.user.userId;
    const { location_latitude, location_longitude, location_address } = req.body;
    
    const demoData = {
      heart_rate: 60 + Math.floor(40 * Math.random()), 
      systolic: 110 + Math.floor(20 * Math.random()),
      diastolic: 70 + Math.floor(15 * Math.random()), 
      spo2: 95 + Math.floor(5 * Math.random()), 
      temperature: 97.5 + 1.5 * Math.random(), 
      status: "normal",
      activity: "Demo Mode - Normal Activity",
      context_tag: "Demo",
    };
    
    await pool.execute(
      "INSERT INTO health_data (user_id, heart_rate, systolic, diastolic, spo2, temperature, status, activity, context_tag, location_latitude, location_longitude, location_address, recorded_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())",
      [
        userId,
        demoData.heart_rate,
        demoData.systolic,
        demoData.diastolic,
        demoData.spo2,
        demoData.temperature,
        demoData.status,
        demoData.activity,
        demoData.context_tag,
        location_latitude || null,
        location_longitude || null,
        location_address || "Demo Location - Normal Activity",
      ]
    );
    res.json({ message: "Normal demo data generated", data: demoData });
  } catch (error) {
    console.error("Demo normal error:", error);
    res.status(500).json({ error: "Internal server error" });
  }
});

app.get("/api/critical-users", async (req, res) => {
  try {
    // Get latest health_data for each user
    const [criticalUsers] = await pool.execute(`
      SELECT u.id as user_id,
             u.name,
             u.email,
             u.emergency_contact,
             u.emergency_phone,
             hd.id as health_data_id,
             hd.heart_rate,
             CONCAT(hd.systolic, '/', hd.diastolic) as blood_pressure,
             hd.spo2,
             hd.temperature,
             hd.status,
             hd.recorded_at,
             hd.context_tag,
             hd.activity
      FROM users u
      INNER JOIN (
        SELECT h1.* FROM health_data h1
        INNER JOIN (
          SELECT user_id, MAX(recorded_at) as max_time
          FROM health_data
          GROUP BY user_id
        ) h2 ON h1.user_id = h2.user_id AND h1.recorded_at = h2.max_time
      ) hd ON u.id = hd.user_id
      WHERE hd.status = 'critical'
        AND u.emergency_phone IS NOT NULL
        AND u.emergency_phone != ''
      ORDER BY hd.recorded_at DESC
    `);
    res.json({
      users: criticalUsers,
      count: criticalUsers.length,
      timestamp: new Date().toISOString(),
    });
  } catch (error) {
    console.error("Get critical users error:", error);
    res.status(500).json({ error: "Internal server error" });
  }
});

app.post("/api/sms-status", async (req, res) => {
  try {
    const {
      user_id: user_id,
      alert_id: alert_id,
      phone_number: phone_number,
      status: status,
      timestamp: timestamp,
    } = req.body;
    await pool.execute(
      "INSERT INTO sms_log (user_id, alert_id, phone_number, status, sent_at) VALUES (?, ?, ?, ?, FROM_UNIXTIME(?))",
      [user_id, alert_id, phone_number, status, timestamp / 1000]
    );
    res.json({ message: "SMS status recorded successfully" });
  } catch (error) {
    console.error("SMS status error:", error);
    res.status(500).json({ error: "Internal server error" });
  }
});

app.get("/api/profile", authenticateToken, async (req, res) => {
  try {
    const userId = req.user.userId,
      [users] = await pool.execute(
        "SELECT id, email, name, emergency_contact, emergency_phone, created_at FROM users WHERE id = ?",
        [userId]
      );
    if (users.length === 0)
      return res.status(404).json({ error: "User not found" });
    res.json({ user: users[0] });
  } catch (error) {
    console.error("Get profile error:", error);
    res.status(500).json({ error: "Internal server error" });
  }
});

app.put("/api/profile", authenticateToken, async (req, res) => {
  try {
    const userId = req.user.userId;
    const sanitize = v => v === undefined ? null : v;
    const { name, emergency_contact, emergency_phone } = req.body;
    const [result] = await pool.execute(
      "UPDATE users SET name = ?, emergency_contact = ?, emergency_phone = ? WHERE id = ?",
      [
        sanitize(name),
        sanitize(emergency_contact),
        sanitize(emergency_phone),
        userId
      ]
    );
    res.json({ message: "Profile updated successfully" });
  } catch (error) {
    console.error("Update profile error:", error);
    res.status(500).json({ error: "Internal server error" });
  }
});

app.get("/api/settings", authenticateToken, async (req, res) => {
  try {
    const userId = req.user.userId;
    const [rows] = await pool.execute(
      "SELECT settings FROM user_settings WHERE user_id = ?",
      [userId]
    );
    if (rows.length === 0) {
      return res.json({ settings: {
        notifications: { email: true, push: false, healthAlerts: true },
        units: 'metric',
        theme: 'system',
      }});
    }
    res.json({ settings: JSON.parse(rows[0].settings) });
  } catch (error) {
    console.error("Get settings error:", error);
    res.status(500).json({ error: "Internal server error" });
  }
});

app.put("/api/settings", authenticateToken, async (req, res) => {
  try {
    const userId = req.user.userId;
    const settings = JSON.stringify(req.body);
    await pool.execute(
      `INSERT INTO user_settings (user_id, settings) VALUES (?, ?) ON DUPLICATE KEY UPDATE settings = VALUES(settings)`,
      [userId, settings]
    );
    res.json({ message: "Settings updated successfully" });
  } catch (error) {
    console.error("Update settings error:", error);
    res.status(500).json({ error: "Internal server error" });
  }
});

app.get("/api/admin/users", async (req, res) => {
  try {
    console.log('🔍 Admin fetching all users with health history...');
    const [users] = await pool.execute(
      "SELECT id, name, email, emergency_contact, emergency_phone, created_at FROM users ORDER BY created_at DESC"
    );
    const usersWithHealth = await Promise.all(
      users.map(async (user) => {
        const [health_history] = await pool.execute(
          "SELECT * FROM health_data WHERE user_id = ? ORDER BY recorded_at DESC LIMIT 20",
          [user.id]
        );
        return { ...user, health_history };
      })
    );

    res.json({
      users: usersWithHealth,
      count: usersWithHealth.length
    });
  } catch (error) {
    console.error("Get all users with health history error:", error);
    res.status(500).json({ error: "Internal server error" });
  }
});

app.get("/api/admin/user/:userId/health", async (req, res) => {
  try {
    const userId = req.params.userId;
    console.log(`🔍 Admin fetching health data for user ${userId}...`);
    
    const [healthData] = await pool.execute(
      "SELECT * FROM health_data WHERE user_id = ? ORDER BY recorded_at DESC LIMIT 50",
      [userId]
    );
    
    console.log(`✅ Found ${healthData.length} health records for user ${userId}`);
    
    res.json({
      data: healthData,
      count: healthData.length
    });
  } catch (error) {
    console.error("Get user health data error:", error);
    res.status(500).json({ error: "Internal server error" });
  }
});

app.get("/api/admin/user/:userId/alerts", async (req, res) => {
  try {
    const userId = req.params.userId;
    console.log(`🔍 Admin fetching alerts for user ${userId}...`);
    
    const [alerts] = await pool.execute(
      "SELECT * FROM alerts WHERE user_id = ? ORDER BY created_at DESC LIMIT 20",
      [userId]
    );
    
    console.log(`✅ Found ${alerts.length} alerts for user ${userId}`);
    
    res.json({
      alerts: alerts,
      count: alerts.length
    });
  } catch (error) {
    console.error("Get user alerts error:", error);
    res.status(500).json({ error: "Internal server error" });
  }
});

const MS_PER_HOUR = 60 * 60 * 1000;
setInterval(async () => {
  try {
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    const todayStr = today.toISOString().slice(0, 10);
    const [result] = await pool.execute(
      "DELETE FROM health_data WHERE DATE(recorded_at) < ?",
      [todayStr]
    );
    if (result.affectedRows > 0) {
      console.log(`🧹 Deleted ${result.affectedRows} old health_data rows (not from today)`);
    }
  } catch (err) {
    console.error('Auto-cleanup error:', err);
  }
}, MS_PER_HOUR); 

app.use((err, req, res, next) => {
  console.error("Unhandled error:", err);
  res.status(500).json({ error: "Internal server error" });
});

app.use((req, res) => {
  res.status(404).json({ error: "Endpoint not found" });
});

app.listen(PORT, HOST, () => {
  const interfaces = os.networkInterfaces();
  const lanIps = [];
  for (const name of Object.keys(interfaces)) {
    for (const iface of interfaces[name]) {
      if (iface.family === 'IPv4' && !iface.internal) {
        lanIps.push(iface.address);
      }
    }
  }
  console.log(`🚀 WristBud Express Server running on ${HOST}:${PORT}`);
  lanIps.forEach(ip => {
    console.log(`🌐 LAN API:   http://${ip}:${PORT}/api/`);
    console.log(`🌐 LAN Health: http://${ip}:${PORT}/health`);
    console.log(`🌐 LAN Admin:  http://${ip}:${PORT}/api/admin/`);
  });
  console.log(`📊 Health API: http://localhost:${PORT}/api/`);
  console.log(`🏥 Health Check: http://localhost:${PORT}/health`);
  console.log(`👨‍💼 Admin API: http://localhost:${PORT}/api/admin/`);
});

process.on("SIGTERM", async () => {
  console.log("SIGTERM received, shutting down gracefully");
  await pool.end();
  process.exit(0);
});

process.on("SIGINT", async () => {
  console.log("SIGINT received, shutting down gracefully");
  await pool.end();
  process.exit(0);
});