<?php
require_once 'config/database.php';

$message = '';
$error = '';

if ($_POST) {
    $database = new Database();
    $db = $database->getConnection();
    
    $name = trim($_POST['name']);
    $email = trim($_POST['email']);
    $password = $_POST['password'];
    $confirm_password = $_POST['confirm_password'];
    $emergency_name = trim($_POST['emergency_name']);
    $emergency_phone = trim($_POST['emergency_phone']);
    
    // Validation
    if (empty($name) || empty($email) || empty($password) || empty($emergency_name) || empty($emergency_phone)) {
        $error = "All fields are required.";
    } elseif ($password !== $confirm_password) {
        $error = "Passwords do not match.";
    } elseif (strlen($password) < 6) {
        $error = "Password must be at least 6 characters long.";
    } elseif (!filter_var($email, FILTER_VALIDATE_EMAIL)) {
        $error = "Invalid email format.";
    } else {
        // Check if email already exists
        $query = "SELECT id FROM users WHERE email = :email";
        $stmt = $db->prepare($query);
        $stmt->bindParam(":email", $email);
        $stmt->execute();
        
        if ($stmt->rowCount() > 0) {
            $error = "Email already registered.";
        } else {
            // Insert new user - Updated to match schema.sql column names
            $query = "INSERT INTO users (name, email, password, emergency_contact, emergency_phone) 
                     VALUES (:name, :email, :password, :emergency_contact, :emergency_phone)";
            $stmt = $db->prepare($query);
            
            $hashed_password = password_hash($password, PASSWORD_DEFAULT);
            
            $stmt->bindParam(":name", $name);
            $stmt->bindParam(":email", $email);
            $stmt->bindParam(":password", $hashed_password);
            $stmt->bindParam(":emergency_contact", $emergency_name);
            $stmt->bindParam(":emergency_phone", $emergency_phone);
            
            if ($stmt->execute()) {
                $message = "Registration successful! You can now login to your WristBud app.";
            } else {
                $error = "Registration failed. Please try again.";
            }
        }
    }
}
?>

<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>WristBud Registration</title>
    <style>
        body {
            font-family: Arial, sans-serif;
            max-width: 500px;
            margin: 50px auto;
            padding: 20px;
            background-color: #f5f5f5;
        }
        .container {
            background: white;
            padding: 30px;
            border-radius: 10px;
            box-shadow: 0 2px 10px rgba(0,0,0,0.1);
        }
        h1 {
            color: #0BAF5A;
            text-align: center;
            margin-bottom: 30px;
        }
        .form-group {
            margin-bottom: 20px;
        }
        label {
            display: block;
            margin-bottom: 5px;
            font-weight: bold;
            color: #333;
        }
        input[type="text"], input[type="email"], input[type="password"], input[type="tel"] {
            width: 100%;
            padding: 12px;
            border: 1px solid #ddd;
            border-radius: 5px;
            font-size: 16px;
            box-sizing: border-box;
        }
        input[type="submit"] {
            width: 100%;
            padding: 12px;
            background-color: #0BAF5A;
            color: white;
            border: none;
            border-radius: 5px;
            font-size: 16px;
            cursor: pointer;
        }
        input[type="submit"]:hover {
            background-color: #098a47;
        }
        .message {
            padding: 10px;
            margin-bottom: 20px;
            border-radius: 5px;
        }
        .success {
            background-color: #d4edda;
            color: #155724;
            border: 1px solid #c3e6cb;
        }
        .error {
            background-color: #f8d7da;
            color: #721c24;
            border: 1px solid #f5c6cb;
        }
        .emergency-section {
            background-color: #fff3cd;
            padding: 15px;
            border-radius: 5px;
            margin-bottom: 20px;
        }
        .emergency-section h3 {
            margin-top: 0;
            color: #856404;
        }
        .info-section {
            background-color: #e7f3ff;
            padding: 15px;
            border-radius: 5px;
            margin-bottom: 20px;
            border-left: 4px solid #0BAF5A;
        }
        .info-section h3 {
            margin-top: 0;
            color: #0BAF5A;
        }
    </style>
</head>
<body>
    <div class="container">
        <h1>WristBud Registration</h1>
        
        <div class="info-section">
            <h3>Server Information</h3>
            <p><strong>Express Server:</strong> http://localhost:5000</p>
            <p><strong>API Endpoint:</strong> http://localhost:5000/api/</p>
            <p>Your WristBud app connects to the Express server for real-time health monitoring.</p>
        </div>
        
        <?php if ($message): ?>
            <div class="message success"><?php echo $message; ?></div>
        <?php endif; ?>
        
        <?php if ($error): ?>
            <div class="message error"><?php echo $error; ?></div>
        <?php endif; ?>
        
        <form method="POST">
            <div class="form-group">
                <label for="name">Full Name:</label>
                <input type="text" id="name" name="name" required value="<?php echo isset($_POST['name']) ? htmlspecialchars($_POST['name']) : ''; ?>">
            </div>
            
            <div class="form-group">
                <label for="email">Email:</label>
                <input type="email" id="email" name="email" required value="<?php echo isset($_POST['email']) ? htmlspecialchars($_POST['email']) : ''; ?>">
            </div>
            
            <div class="form-group">
                <label for="password">Password:</label>
                <input type="password" id="password" name="password" required minlength="6">
            </div>
            
            <div class="form-group">
                <label for="confirm_password">Confirm Password:</label>
                <input type="password" id="confirm_password" name="confirm_password" required minlength="6">
            </div>
            
            <div class="emergency-section">
                <h3>Emergency Contact Information</h3>
                <p>This contact will be notified in case of critical health alerts.</p>
                
                <div class="form-group">
                    <label for="emergency_name">Emergency Contact Name:</label>
                    <input type="text" id="emergency_name" name="emergency_name" required value="<?php echo isset($_POST['emergency_name']) ? htmlspecialchars($_POST['emergency_name']) : ''; ?>">
                </div>
                
                <div class="form-group">
                    <label for="emergency_phone">Emergency Contact Phone:</label>
                    <input type="tel" id="emergency_phone" name="emergency_phone" required value="<?php echo isset($_POST['emergency_phone']) ? htmlspecialchars($_POST['emergency_phone']) : ''; ?>">
                </div>
            </div>
            
            <input type="submit" value="Register">
        </form>
        
        <p style="text-align: center; margin-top: 20px; color: #666;">
            After registration, login using your WristBud smartwatch app.<br>
            <small>App connects to Express server at localhost:5000</small>
        </p>
    </div>
</body>
</html>