# ESP32 DevKit Real Sensor Configuration Guide

## Current Sensor Connections

Your firmware (`Firmware/src/main.cpp`) is configured with 4 real sensors:

| Sensor | Pin | Type | Data Sent |
|--------|-----|------|-----------|
| DHT22 | 33 | Temperature & Humidity | `temperatureC`, `humidityLevel` |
| Soil Moisture 1 | 34 | Capacitive/Resistive Moisture | `moistureLevel` (averaged) |
| Soil Moisture 2 | 35 | Capacitive/Resistive Moisture | `moistureLevel` (averaged) |
| MQ135 | 32 | Air Quality/Gas | `gasLevel` (converted to index) |

## Firmware Configuration (IMPORTANT)

Before uploading the firmware, update these values in `Firmware/src/main.cpp` lines 8-12:

```cpp
#define WIFI_SSID      "YOUR_WIFI_NETWORK"     // WiFi network name
#define WIFI_PASSWORD  "YOUR_WIFI_PASSWORD"     // WiFi password
#define BACKEND_URL    "http://YOUR_IP_ADDRESS" // Backend server IP
#define BACKEND_PATH   "/api/sensor-readings"   // Keep as-is
```

### Finding Your Backend IP Address

**Option 1: Using Local IP (Recommended for testing)**
```powershell
# On Windows, in PowerShell:
ipconfig
# Look for "IPv4 Address" under your active network adapter (e.g., 192.168.x.x)
```

**Option 2: Using localhost with mDNS**
If ESP32 is on same network as backend:
```cpp
#define BACKEND_URL "http://compost-system.local" // If backend supports mDNS
```

### Moisture Sensor Calibration

The firmware uses these ADC calibration values (lines 29-31):
```cpp
constexpr int MOISTURE_ADC_WET = 1500;   // ADC reading when wet
constexpr int MOISTURE_ADC_DRY = 3300;   // ADC reading when dry
```

**To calibrate for your sensors:**
1. Dip sensor 1 in water → note the ADC value printed to Serial Monitor
2. Let sensor dry completely → note the ADC value
3. Update `MOISTURE_ADC_WET` and `MOISTURE_ADC_DRY` with your readings
4. Recompile and upload

## Dashboard Real Data Flow

✅ **The frontend Dashboard is already configured for real sensor data:**

1. Dashboard polls backend every **30 seconds** 
2. Backend returns latest reading from database
3. Display shows:
   - Temperature (°C)
   - Moisture level (%)
   - Gas level (index, 0-2000)
   - Humidity (%)
4. Status: "Live database readings are active" when receiving data

**If you see "Waiting for database sensor data":**
- Check ESP32 is powered and connected to WiFi
- Verify backend is running on the configured IP/port
- Check backend URL in firmware matches your setup
- Monitor Serial output from ESP32 for connection errors

## Testing the Connection

### Step 1: Monitor ESP32 Output
```bash
cd Firmware
python -m platformio device monitor --baud 115200
```

You should see output like:
```
[WIFI] Connecting to YOUR_WIFI_NETWORK
[WIFI] Connected, IP=192.168.x.x
[SENSOR] Soil1=2450, Soil2=2400, Moisture1=45.2%, ...
[HTTP] POST http://YOUR_IP:8080/api/sensor-readings (attempt 1)
[HTTP] Success code=200 response=...
```

### Step 2: Check Backend is Running
```bash
curl http://YOUR_BACKEND_IP:8080/api/sensor-readings/latest
# Should return JSON with sensor data if readings exist
```

### Step 3: View Dashboard
1. Open frontend: `http://localhost:5173`
2. Navigate to Dashboard
3. Should show "Live database readings are active"
4. Sensor values update every 30 seconds

## Troubleshooting

### ESP32 Won't Connect to WiFi
- Verify SSID and password are correct (case-sensitive)
- Check if 5GHz WiFi - ESP32 only supports 2.4GHz
- Power cycle ESP32
- Check WiFi signal strength near device

### Backend URL Not Reachable
- Ensure backend is running: `cd backend && ./mvnw spring-boot:run`
- Verify IP address is correct: `ipconfig` on Windows
- Check firewall isn't blocking port 8080
- Ensure ESP32 and backend are on same network

### Sensor Readings Not Appearing in Database
- Check ESP32 serial monitor for HTTP errors
- Verify backend database is initialized
- Check backend application.properties has correct database credentials

### Incorrect Sensor Values
- Check wiring matches pin definitions
- For moisture sensors: calibrate using the ADC values
- For MQ135: may need warm-up time, allow 5+ minutes
- For DHT22: ensure it's not too hot/cold (operating range: -40°C to +80°C)

## API Endpoint Reference

The ESP32 sends data to:
```
POST http://YOUR_BACKEND_IP:8080/api/sensor-readings

Request body:
{
  "moistureLevel": 65.5,
  "gasLevel": 1200,
  "temperatureC": 32.5,
  "humidityLevel": 75.3
}

Response:
{
  "readingId": 123,
  "batchId": 1,
  "moistureLevel": 65.5,
  "gasLevel": 1200,
  "temperatureC": 32.5,
  "humidityLevel": 75.3,
  "createdAt": "2026-07-03T10:30:00Z"
}
```

## Next Steps

1. ✅ Firmware is already compiled and ready
2. 📝 **Update WiFi & Backend URL in firmware**
3. 🔌 **Connect sensors to ESP32 pins**
4. 📤 **Upload firmware**: `python -m platformio run --target upload`
5. 📊 **Verify data appears in Dashboard**

Good luck! Your system will then be running with real sensor data.
