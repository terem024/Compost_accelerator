#include <Arduino.h>
#include <WiFi.h>
#include <WiFiClientSecure.h>
#include <HTTPClient.h>
#include <ArduinoJson.h>
#include <DHT.h>
#include <time.h>
#include "backend_ca.h"
#include "secrets.h"

//
// Configuration
//
#define BACKEND_PATH   "/api/sensor-readings"

#define ENABLE_RELAY_CONTROL true

#if ENABLE_RELAY_CONTROL
#define RELAY_IN1_PIN          26
#define RELAY_IN2_PIN          27
#define WATER_SPRAY_RELAY_PIN  RELAY_IN1_PIN
#define FAN_RELAY_PIN          RELAY_IN2_PIN
#define RELAY_TEST_MODE false
#define RELAY_TEST_INTERVAL_MS 1000UL
#define RELAY_ON_LEVEL  LOW
#define RELAY_OFF_LEVEL HIGH
#endif

#define DHT_PIN        33
#define DHT_TYPE       DHT22

#define SOIL_MOISTURE_1_PIN 34
#define SOIL_MOISTURE_2_PIN 35
#define MQ135_PIN           32

#define SENSOR_INTERVAL_MS      60000UL
#define WIFI_CONNECT_TIMEOUT_MS 15000UL
#define TIME_SYNC_TIMEOUT_MS    15000UL
#define HTTP_TIMEOUT_MS         10000
#define HTTP_RETRY_COUNT        3
#define HTTP_RETRY_DELAY_MS     5000UL
#define QUEUE_LENGTH            2

// ESP32 task stack sizes are bytes. TLS certificate checks exceed the old 8 KiB stack.
constexpr uint32_t SENDER_STACK_BYTES = 24 * 1024;

// ADC calibration for moisture sensors.
// Adjust if your sensor output curve differs.
constexpr int MOISTURE_ADC_WET = 1500;
constexpr int MOISTURE_ADC_DRY = 3300;
constexpr float MOISTURE_PERCENT_MIN = 0.0f;
constexpr float MOISTURE_PERCENT_MAX = 100.0f;

// Relative MQ135 reading for monitoring. This percentage is not a PPM value.
constexpr float GAS_PERCENT_MIN = 0.0f;
constexpr float GAS_PERCENT_MAX = 100.0f;

DHT dht(DHT_PIN, DHT_TYPE);

struct SensorPayload {
  float moisture1Raw;
  float moisture2Raw;
  float moisturePercent1;
  float moisturePercent2;
  float gasRaw;
  float gasPercent;
  float temperatureC;
  float humidityLevel;
};

static QueueHandle_t payloadQueue = nullptr;

#if ENABLE_RELAY_CONTROL
static void setRelay(uint8_t pin, bool active) {
  digitalWrite(pin, active ? RELAY_ON_LEVEL : RELAY_OFF_LEVEL);
}

static void initializeRelays() {
  // Load the inactive level before enabling output to avoid a startup pulse.
  digitalWrite(FAN_RELAY_PIN, RELAY_OFF_LEVEL);
  digitalWrite(WATER_SPRAY_RELAY_PIN, RELAY_OFF_LEVEL);
  pinMode(FAN_RELAY_PIN, OUTPUT);
  pinMode(WATER_SPRAY_RELAY_PIN, OUTPUT);
}

static void runRelaySelfTest() {
  static bool relaysActive = false;
  relaysActive = !relaysActive;

  setRelay(FAN_RELAY_PIN, relaysActive);
  setRelay(WATER_SPRAY_RELAY_PIN, relaysActive);

  Serial.printf("[RELAY TEST] FAN GPIO%d=%s, WATER_SPRAY GPIO%d=%s\n",
                FAN_RELAY_PIN,
                relaysActive ? "ON" : "OFF",
                WATER_SPRAY_RELAY_PIN,
                relaysActive ? "ON" : "OFF");

  delay(RELAY_TEST_INTERVAL_MS);
}

static void pulseRequestedRelays(unsigned long fanDurationSeconds,
                                 unsigned long waterSprayDurationSeconds) {
  const unsigned long now = millis();
  const unsigned long fanOffAt = fanDurationSeconds > 0 ? now + (fanDurationSeconds * 1000UL) : 0;
  const unsigned long waterSprayOffAt = waterSprayDurationSeconds > 0 ? now + (waterSprayDurationSeconds * 1000UL) : 0;

  if (fanDurationSeconds > 0) {
    Serial.printf("[RELAY] FAN pulse on GPIO%d for %lu seconds\n", FAN_RELAY_PIN, fanDurationSeconds);
    setRelay(FAN_RELAY_PIN, true);
  }

  if (waterSprayDurationSeconds > 0) {
    Serial.printf("[RELAY] WATER_SPRAY pulse on GPIO%d for %lu seconds\n", WATER_SPRAY_RELAY_PIN, waterSprayDurationSeconds);
    setRelay(WATER_SPRAY_RELAY_PIN, true);
  }

  while ((fanOffAt > 0 && millis() < fanOffAt) ||
         (waterSprayOffAt > 0 && millis() < waterSprayOffAt)) {
    unsigned long currentTime = millis();

    if (fanOffAt > 0 && currentTime >= fanOffAt) {
      setRelay(FAN_RELAY_PIN, false);
    }

    if (waterSprayOffAt > 0 && currentTime >= waterSprayOffAt) {
      setRelay(WATER_SPRAY_RELAY_PIN, false);
    }

    vTaskDelay(pdMS_TO_TICKS(100));
  }

  setRelay(FAN_RELAY_PIN, false);
  setRelay(WATER_SPRAY_RELAY_PIN, false);
}

static void applyActuatorActions(const String &responseBody) {
  StaticJsonDocument<2048> doc;
  DeserializationError error = deserializeJson(doc, responseBody);

  if (error) {
    Serial.printf("[RELAY] Could not parse backend actuator response: %s\n", error.c_str());
    return;
  }

  JsonArray actions = doc["actuatorActions"].as<JsonArray>();
  if (actions.isNull() || actions.size() == 0) {
    Serial.println("[RELAY] No actuator actions requested by backend");
    return;
  }

  unsigned long fanDurationSeconds = 0;
  unsigned long waterSprayDurationSeconds = 0;

  for (JsonObject action : actions) {
    const char *actuatorType = action["actuatorType"] | "";
    const char *status = action["status"] | "";
    unsigned long durationSeconds = action["durationSeconds"] | 0;

    if (strcmp(status, "ON") != 0 || durationSeconds == 0) {
      continue;
    }

    if (strcmp(actuatorType, "FAN") == 0) {
      fanDurationSeconds = max(fanDurationSeconds, durationSeconds);
    } else if (strcmp(actuatorType, "WATER_SPRAY") == 0) {
      waterSprayDurationSeconds = max(waterSprayDurationSeconds, durationSeconds);
    }
  }

  pulseRequestedRelays(fanDurationSeconds, waterSprayDurationSeconds);
}
#endif

static float clampFloat(float value, float minValue, float maxValue) {
  if (value < minValue) return minValue;
  if (value > maxValue) return maxValue;
  return value;
}

static float mapFloat(float x, float inMin, float inMax, float outMin, float outMax) {
  if (inMax == inMin) return outMin;
  float mapped = (x - inMin) * (outMax - outMin) / (inMax - inMin) + outMin;
  return clampFloat(mapped, min(outMin, outMax), max(outMin, outMax));
}

static float calculateMoisturePercent(int adcValue) {
  float percent = mapFloat(static_cast<float>(adcValue),
                           static_cast<float>(MOISTURE_ADC_DRY),
                           static_cast<float>(MOISTURE_ADC_WET),
                           MOISTURE_PERCENT_MIN,
                           MOISTURE_PERCENT_MAX);
  return clampFloat(percent, MOISTURE_PERCENT_MIN, MOISTURE_PERCENT_MAX);
}

static float calculateGasPercent(int adcValue) {
  return mapFloat(static_cast<float>(adcValue), 0.0f, 4095.0f, GAS_PERCENT_MIN, GAS_PERCENT_MAX);
}

static bool readDHT22(float &outTemperature, float &outHumidity) {
  float humidity = dht.readHumidity();
  float temperature = dht.readTemperature();
  if (isnan(humidity) || isnan(temperature)) {
    return false;
  }
  outTemperature = temperature;
  outHumidity = humidity;
  return true;
}

static bool readSensors(SensorPayload &payload) {
  payload.moisture1Raw = analogRead(SOIL_MOISTURE_1_PIN);
  payload.moisture2Raw = analogRead(SOIL_MOISTURE_2_PIN);
  payload.gasRaw       = analogRead(MQ135_PIN);

  payload.moisturePercent1 = calculateMoisturePercent(static_cast<int>(payload.moisture1Raw));
  payload.moisturePercent2 = calculateMoisturePercent(static_cast<int>(payload.moisture2Raw));


  payload.gasPercent = calculateGasPercent(static_cast<int>(payload.gasRaw));

  if (!readDHT22(payload.temperatureC, payload.humidityLevel)) {
    Serial.println("[ERROR] DHT22 read failed or checksum invalid.");
    return false;
  }

  if (payload.moisture1Raw < 0 || payload.moisture1Raw > 4095 ||
      payload.moisture2Raw < 0 || payload.moisture2Raw > 4095 ||
      payload.gasRaw < 0 || payload.gasRaw > 4095) {
    Serial.println("[ERROR] Invalid ADC reading detected.");
    return false;
  }

  Serial.printf(
    "[SENSOR] Soil1=%d, Soil2=%d, Moisture1=%.1f%%, Moisture2=%.1f%%, "
    "MQ135=%d, Gas=%.1f%%, Temp=%.1f°C, Humidity=%.1f%%\n",
    static_cast<int>(payload.moisture1Raw),
    static_cast<int>(payload.moisture2Raw),
    payload.moisturePercent1,
    payload.moisturePercent2,
    static_cast<int>(payload.gasRaw),
    payload.gasPercent,
    payload.temperatureC,
    payload.humidityLevel
  );

  return true;
}

static bool ensureWiFiConnected() {
  if (WiFi.status() == WL_CONNECTED) {
    return true;
  }

  Serial.printf("[WIFI] Connecting to %s\n", WIFI_SSID);
  WiFi.mode(WIFI_STA);
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);

  unsigned long start = millis();
  while (millis() - start < WIFI_CONNECT_TIMEOUT_MS) {
    if (WiFi.status() == WL_CONNECTED) {
      Serial.printf("[WIFI] Connected, IP=%s\n", WiFi.localIP().toString().c_str());
      return true;
    }
    vTaskDelay(pdMS_TO_TICKS(250));
  }

  Serial.println("[WIFI] Connection timed out");
  return false;
}

static bool ensureClockSynchronized() {
  // Certificate validity checks require a real clock after power-on.
  constexpr time_t MIN_VALID_TIME = 1767225600; // 2026-01-01 UTC
  if (time(nullptr) >= MIN_VALID_TIME) {
    return true;
  }

  Serial.println("[TIME] Synchronizing clock for HTTPS");
  configTime(0, 0, "pool.ntp.org", "time.google.com", "time.cloudflare.com");
  const unsigned long start = millis();
  while (millis() - start < TIME_SYNC_TIMEOUT_MS) {
    if (time(nullptr) >= MIN_VALID_TIME) {
      Serial.println("[TIME] Clock synchronized");
      return true;
    }
    vTaskDelay(pdMS_TO_TICKS(250));
  }

  Serial.println("[TIME] Clock sync timed out; HTTPS send skipped. Check WiFi internet access.");
  return false;
}

static bool sendReadingToBackend(const SensorPayload &payload) {
  if (!ensureWiFiConnected()) {
    return false;
  }

  StaticJsonDocument<256> doc;
  doc["moisturePercent1"] = payload.moisturePercent1;
  doc["moisturePercent2"] = payload.moisturePercent2;
  doc["moistureLevel"] = (payload.moisturePercent1 + payload.moisturePercent2) / 2.0f;
  doc["gasLevel"] = payload.gasPercent;
  doc["temperatureC"] = payload.temperatureC;
  doc["humidityLevel"] = payload.humidityLevel;

  String body;
  serializeJson(doc, body);

  const String url = String(BACKEND_URL) + BACKEND_PATH;
  const bool useHttps = url.startsWith("https://");
  if (!useHttps && !url.startsWith("http://")) {
    Serial.println("[HTTP] BACKEND_URL must start with http:// or https://");
    return false;
  }
  if (useHttps && !ensureClockSynchronized()) {
    return false;
  }
  bool success = false;

  for (int attempt = 1; attempt <= HTTP_RETRY_COUNT; ++attempt) {
    WiFiClient plainClient;
    WiFiClientSecure secureClient;
    if (useHttps) {
      secureClient.setCACert(BACKEND_ROOT_CA);
      secureClient.setHandshakeTimeout(15);
    }
    WiFiClient &client = useHttps ? static_cast<WiFiClient &>(secureClient) : plainClient;
    HTTPClient http;
    if (!http.begin(client, url)) {
      Serial.println("[HTTP] Could not initialize backend connection");
      return false;
    }
    http.setConnectTimeout(HTTP_TIMEOUT_MS);
    http.setTimeout(HTTP_TIMEOUT_MS);
    http.addHeader("Content-Type", "application/json");

    Serial.printf("[HTTP] POST %s (attempt %d)\n", url.c_str(), attempt);
    int httpCode = http.POST(body);

    if (httpCode == HTTP_CODE_OK) {
      String response = http.getString();
      Serial.printf("[HTTP] Success code=%d response=%s\n", httpCode, response.c_str());
#if ENABLE_RELAY_CONTROL
      applyActuatorActions(response);
#endif
      success = true;
      http.end();
      break;
    }

    if (httpCode > 0) {
      Serial.printf("[HTTP] Failed code=%d response=%s\n", httpCode, http.getString().c_str());
    } else {
      Serial.printf("[HTTP] Request error: %s\n", http.errorToString(httpCode).c_str());
    }

    http.end();
    if (attempt < HTTP_RETRY_COUNT) {
      Serial.printf("[HTTP] Retrying in %lu ms\n", HTTP_RETRY_DELAY_MS);
      vTaskDelay(pdMS_TO_TICKS(HTTP_RETRY_DELAY_MS));
    }
  }

  return success;
}

void sensorTask(void *param) {
  SensorPayload payload;

  while (true) {
    if (readSensors(payload)) {
      if (xQueueSend(payloadQueue, &payload, 0) != pdTRUE) {
        Serial.println("[QUEUE] Queue full, dropping current payload");
      }
    } else {
      Serial.println("[SENSOR] Skipping send due to invalid sensor data");
    }

    vTaskDelay(pdMS_TO_TICKS(SENSOR_INTERVAL_MS));
  }
}

void senderTask(void *param) {
  SensorPayload payload;

  while (true) {
    if (xQueueReceive(payloadQueue, &payload, portMAX_DELAY) == pdTRUE) {
      if (!sendReadingToBackend(payload)) {
        Serial.println("[HTTP] Failed to send reading after retries");
      }
      Serial.printf("[MEMORY] SenderTask minimum free stack=%u bytes, free heap=%u bytes\n",
                    static_cast<unsigned int>(uxTaskGetStackHighWaterMark(nullptr)),
                    static_cast<unsigned int>(ESP.getFreeHeap()));
    }
  }
}

void setup() {
  Serial.begin(115200);
  delay(1000);
  Serial.printf("[SETUP] Backend URL=%s%s\n", BACKEND_URL, BACKEND_PATH);
  Serial.printf("[SETUP] Sensor interval=%lu ms\n", SENSOR_INTERVAL_MS);

#if ENABLE_RELAY_CONTROL
  initializeRelays();
  Serial.printf("[SETUP] Relay mapping: IN1/GPIO%d -> PUMP, IN2/GPIO%d -> FAN\n",
                WATER_SPRAY_RELAY_PIN,
                FAN_RELAY_PIN);

  if (RELAY_TEST_MODE) {
    Serial.println("[SETUP] Relay self-test mode enabled");
    Serial.println("[SETUP] IN1/GPIO26 pump and IN2/GPIO27 fan will toggle every second");
    return;
  }
#endif

  analogReadResolution(12);
  analogSetPinAttenuation(SOIL_MOISTURE_1_PIN, ADC_11db);
  analogSetPinAttenuation(SOIL_MOISTURE_2_PIN, ADC_11db);
  analogSetPinAttenuation(MQ135_PIN, ADC_11db);
  Serial.printf("[SETUP] Sensor mapping: Soil1=GPIO%d, Soil2=GPIO%d, MQ135=GPIO%d, DHT22=GPIO%d\n",
                SOIL_MOISTURE_1_PIN,
                SOIL_MOISTURE_2_PIN,
                MQ135_PIN,
                DHT_PIN);

  dht.begin();

  payloadQueue = xQueueCreate(QUEUE_LENGTH, sizeof(SensorPayload));
  if (payloadQueue == nullptr) {
    Serial.println("[ERROR] Failed to create payload queue");
    while (true) {
      vTaskDelay(pdMS_TO_TICKS(1000));
    }
  }

  Serial.printf("[SETUP] SenderTask stack=%u bytes\n", static_cast<unsigned int>(SENDER_STACK_BYTES));
  TaskHandle_t senderHandle = nullptr;
  const BaseType_t senderCreated = xTaskCreatePinnedToCore(
      senderTask, "SenderTask", SENDER_STACK_BYTES, nullptr, 1, &senderHandle, 1);
  const BaseType_t sensorCreated = senderCreated == pdPASS
      ? xTaskCreatePinnedToCore(sensorTask, "SensorTask", 4096, nullptr, 1, nullptr, 1)
      : pdFAIL;
  if (senderCreated != pdPASS || sensorCreated != pdPASS) {
    if (senderHandle != nullptr) {
      vTaskDelete(senderHandle);
    }
    Serial.println("[ERROR] Not enough memory to start sensor/sender tasks. Relays remain off.");
    while (true) {
      vTaskDelay(pdMS_TO_TICKS(1000));
    }
  }

  Serial.println("[SETUP] Firmware initialized");
}

void loop() {
#if ENABLE_RELAY_CONTROL
  if (RELAY_TEST_MODE) {
    runRelaySelfTest();
    return;
  }
#endif

  vTaskDelay(pdMS_TO_TICKS(1000));
}
