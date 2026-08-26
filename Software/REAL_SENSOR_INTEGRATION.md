# Real Sensor Data Integration Guide

## Overview
The Compost Accelerator system now expects **real-time sensor data from IoT devices** instead of using mock/simulated data. The backend is configured to receive and store actual sensor readings in the database.

---

## API Endpoint for Sensor Data

### POST `/api/sensor-readings`
Submit real sensor measurements from your IoT device

**Base URL:** `http://localhost:8080/api` (or your backend server URL)

**Request Headers:**
```
Content-Type: application/json
```

**Request Body:**
```json
{
  "batchId": 1,
  "moistureLevel": 65.5,
  "gasLevel": 1050.3,
  "temperatureC": 38.2,
  "humidityLevel": 58.4
}
```

**Field Descriptions:**
| Field | Type | Description | Range |
|-------|------|-------------|-------|
| `batchId` | Integer | Active compost batch ID | 1-999 |
| `moistureLevel` | Decimal | Soil moisture percentage | 35.0 - 80.0 |
| `gasLevel` | Decimal | Gas sensor reading | 700.0 - 1600.0 |
| `temperatureC` | Decimal | Temperature in Celsius | 28.0 - 45.0 |
| `humidityLevel` | Decimal | Relative humidity percentage | 40.0 - 75.0 |

**Example cURL Request:**
```bash
curl -X POST http://localhost:8080/api/sensor-readings \
  -H "Content-Type: application/json" \
  -d '{
    "batchId": 1,
    "moistureLevel": 65.5,
    "gasLevel": 1050.3,
    "temperatureC": 38.2,
    "humidityLevel": 58.4
  }'
```

**Response (Success - 200):**
```json
{
  "readingId": 123,
  "batchId": 1,
  "moistureLevel": 65.5,
  "gasLevel": 1050.3,
  "temperatureC": 38.2,
  "humidityLevel": 58.4,
  "moistureStatus": "OPTIMAL",
  "gasStatus": "OPTIMAL",
  "temperatureStatus": "OPTIMAL",
  "humidityStatus": "OPTIMAL",
  "createdAt": "2026-07-03T10:30:45.000Z",
  "actuatorActions": []
}
```

---

## Arduino/ESP32 Implementation Example

### Basic Sensor Reading Code
```cpp
#include <WiFi.h>
#include <HTTPClient.h>
#include <ArduinoJson.h>

// WiFi Configuration
const char* ssid = "POCO";
const char* password = "00000000";

// Backend Server
const char* backend_url = "http://192.168.1.100:8080/api/sensor-readings";
const int BATCH_ID = 1;  // Your active batch ID

// Sensor Pins (adjust to your setup)
#define MOISTURE_PIN 34
#define GAS_PIN 35
#define TEMP_PIN 36
#define HUMIDITY_PIN 39

// Timing
unsigned long lastSensorTime = 0;
const unsigned long SENSOR_INTERVAL = 30000;  // 30 seconds

void setup() {
  Serial.begin(115200);
  connectToWiFi();
}

void loop() {
  if (WiFi.status() == WL_CONNECTED) {
    if (millis() - lastSensorTime >= SENSOR_INTERVAL) {
      readAndSubmitSensors();
      lastSensorTime = millis();
    }
  } else {
    connectToWiFi();
  }
  delay(1000);
}

void connectToWiFi() {
  Serial.println("Connecting to WiFi: " + String(ssid));
  WiFi.begin(ssid, password);
  
  int attempts = 0;
  while (WiFi.status() != WL_CONNECTED && attempts < 20) {
    delay(500);
    Serial.print(".");
    attempts++;
  }
  
  if (WiFi.status() == WL_CONNECTED) {
    Serial.println("\nWiFi connected!");
    Serial.println("IP address: " + WiFi.localIP().toString());
  }
}

void readAndSubmitSensors() {
  // Read sensor values from analog pins
  int moistureRaw = analogRead(MOISTURE_PIN);
  int gasRaw = analogRead(GAS_PIN);
  int tempRaw = analogRead(TEMP_PIN);
  int humidityRaw = analogRead(HUMIDITY_PIN);
  
  // Convert raw values to actual sensor readings
  // (calibration depends on your specific sensors)
  float moisture = mapMoisture(moistureRaw);        // 35-80%
  float gasLevel = mapGasLevel(gasRaw);             // 700-1600
  float temperature = mapTemperature(tempRaw);      // 28-45°C
  float humidity = mapHumidity(humidityRaw);        // 40-75%
  
  // Submit to backend
  submitSensorData(moisture, gasLevel, temperature, humidity);
}

void submitSensorData(float moisture, float gas, float temp, float humidity) {
  HTTPClient http;
  
  // Create JSON payload
  DynamicJsonDocument doc(512);
  doc["batchId"] = BATCH_ID;
  doc["moistureLevel"] = moisture;
  doc["gasLevel"] = gas;
  doc["temperatureC"] = temp;
  doc["humidityLevel"] = humidity;
  
  // Serialize to string
  String payload;
  serializeJson(doc, payload);
  
  // Send POST request
  http.begin(backend_url);
  http.addHeader("Content-Type", "application/json");
  
  int httpResponseCode = http.POST(payload);
  
  if (httpResponseCode == 200) {
    Serial.println("✓ Sensor data submitted successfully");
    Serial.println("Response: " + http.getString());
  } else {
    Serial.print("✗ Error submitting sensor data. HTTP code: ");
    Serial.println(httpResponseCode);
  }
  
  http.end();
}

// Sensor calibration functions (adjust based on your sensor specs)
float mapMoisture(int rawValue) {
  // Map ADC value (0-4095) to moisture percentage (35-80)
  return 35.0 + (rawValue / 4095.0) * 45.0;
}

float mapGasLevel(int rawValue) {
  // Map ADC value (0-4095) to gas level (700-1600)
  return 700.0 + (rawValue / 4095.0) * 900.0;
}

float mapTemperature(int rawValue) {
  // For temperature sensor (e.g., DHT22, LM35)
  // This example assumes DHT sensor; adjust as needed
  return 28.0 + (rawValue / 4095.0) * 17.0;
}

float mapHumidity(int rawValue) {
  // Map ADC value (0-4095) to humidity percentage (40-75)
  return 40.0 + (rawValue / 4095.0) * 35.0;
}
```

### With DHT Sensor Library (Recommended)
```cpp
#include <DHT.h>
#include <WiFi.h>
#include <HTTPClient.h>
#include <ArduinoJson.h>

#define DHTPIN 4
#define DHTTYPE DHT22
DHT dht(DHTPIN, DHTTYPE);

#define MOISTURE_PIN 34
#define GAS_PIN 35

const char* backend_url = "http://192.168.1.100:8080/api/sensor-readings";
const int BATCH_ID = 1;

void setup() {
  Serial.begin(115200);
  dht.begin();
  connectToWiFi();
}

void loop() {
  if (WiFi.status() == WL_CONNECTED) {
    // Read DHT sensors
    float humidity = dht.readHumidity();
    float temperature = dht.readTemperature();
    
    // Read analog sensors
    float moisture = mapMoisture(analogRead(MOISTURE_PIN));
    float gasLevel = mapGasLevel(analogRead(GAS_PIN));
    
    if (!isnan(humidity) && !isnan(temperature)) {
      submitSensorData(moisture, gasLevel, temperature, humidity);
    }
  }
  
  delay(30000);  // 30 seconds
}

// Rest of the code same as above...
```

---

## Database Schema

The sensor readings are stored in the `sensor_readings` table:

```sql
CREATE TABLE sensor_readings (
  reading_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  batch_id INT NOT NULL,
  moisture_level DECIMAL(5,2),
  gas_level DECIMAL(7,2),
  temperature_c DECIMAL(5,2),
  humidity_level DECIMAL(5,2),
  moisture_status VARCHAR(20),
  gas_status VARCHAR(20),
  temperature_status VARCHAR(20),
  humidity_status VARCHAR(20),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (batch_id) REFERENCES compost_batches(batch_id)
);
```

**Status Values:** `LOW`, `OPTIMAL`, `HIGH`

---

## Verifying the Connection

### 1. Check Backend is Running
```bash
# On Windows
curl http://localhost:8080/api/sensor-readings

# Should return empty array [] or 204 No Content
```

### 2. Submit Test Data
```bash
curl -X POST http://localhost:8080/api/sensor-readings \
  -H "Content-Type: application/json" \
  -d '{
    "batchId": 1,
    "moistureLevel": 60.5,
    "gasLevel": 1000.0,
    "temperatureC": 38.0,
    "humidityLevel": 55.0
  }'
```

**Expected Response:**
```json
{
  "readingId": 1,
  "batchId": 1,
  "moistureLevel": 60.5,
  "gasLevel": 1000.0,
  "temperatureC": 38.0,
  "humidityLevel": 55.0,
  "moistureStatus": "OPTIMAL",
  "gasStatus": "OPTIMAL",
  "temperatureStatus": "OPTIMAL",
  "humidityStatus": "OPTIMAL",
  "createdAt": "2026-07-03T10:30:45.000Z"
}
```

### 3. View in Dashboard
1. Open `http://localhost:5173/dashboard`
2. The latest sensor reading should appear in the sensor cards
3. Data updates every 30 seconds from the database

### 4. Query Database Directly
```sql
-- View all sensor readings
SELECT * FROM sensor_readings ORDER BY reading_id DESC LIMIT 10;

-- View latest reading for active batch
SELECT * FROM sensor_readings 
WHERE batch_id = (SELECT batch_id FROM compost_batches WHERE status = 'ACTIVE')
ORDER BY created_at DESC LIMIT 1;
```

---

## Troubleshooting

### Issue: Connection Refused (ESP32 → Backend)
- **Check:** Backend is running on port 8080
- **Check:** ESP32 is on same network as backend
- **Fix:** Update `backend_url` with correct IP address
- **Tip:** Use `ifconfig` (Linux) or `ipconfig` (Windows) to find backend IP

### Issue: 400 Bad Request
- **Check:** JSON payload format is correct
- **Check:** All required fields are present
- **Check:** Field values are within expected ranges
- **Debug:** Check backend logs for validation errors

### Issue: Data Not Appearing in Dashboard
- **Check:** Backend is receiving the data (check HTTP 200 response)
- **Check:** Batch ID matches an ACTIVE batch in database
- **Verify:** Database connection is configured correctly
- **Debug:** Query database directly to confirm data is stored

### Issue: Sensor Values Out of Range
- **Check:** Sensor calibration mappings are correct
- **Fix:** Verify sensor-to-analog pin connections
- **Calibrate:** Test sensors with known values first

---

## Configuration

### Enable/Disable Simulation (if needed)
Edit `backend/src/main/resources/application.properties`:
```properties
# Set to true to enable mock data generation (development only)
app.sensor.simulation.enabled=false
```

### Change Polling Interval
In `frontend/src/pages/Dashboard.jsx`:
```javascript
const DASHBOARD_POLL_INTERVAL_MS = 30000;  // Change this value (milliseconds)
```

---

## Next Steps

1. ✅ Set up your IoT sensors (temperature, humidity, moisture, gas)
2. ✅ Configure ESP32/Arduino with provided code
3. ✅ Test API endpoint with cURL
4. ✅ Verify data in dashboard
5. ✅ Monitor real-time sensor readings in database
6. 📊 Set up alerts and predictions (via AI Prediction page)

---

## Support

For issues or questions:
- Check backend logs: `tail -f backend/target/logs`
- Check browser console for frontend errors
- Verify database connectivity: `mysql -u root compost_system`
- Test ESP32 serial output for debugging messages
