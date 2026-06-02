/* ══════════════════════════════════════════════════════════════════
 * oinsexpress_esp32_final.ino — OINSExpress ESP32 Final
 * ══════════════════════════════════════════════════════════════════
 *
 * Projet PFA 2026 — FST
 * Mrabet Islem — Merghni Ons
 *
 * Rôles de l'ESP32 :
 *   1. Reçoit données JSON du STM32 par UART2 (115200 bps)
 *   2. Se connecte au WiFi
 *   3. Envoie les données au backend Spring Boot (REST API)
 *   4. Simule 2 livreurs pour la démonstration jury
 *
 * CÂBLAGE :
 *   ESP32 GPIO16 (RX2) ←── STM32 PA9(UART1 TX)
 *   ESP32 GPIO17 (TX2) ──→ STM32 PA10 (UART1 RX)
 *   ESP32 GND          ─── STM32 GND
 *   ESP32 VIN (5V)     ←── STM32 5V
 *
 * LIBRAIRIES À INSTALLER (Arduino IDE) :
 *   - "ArduinoJson" by Benoit Blanchon
 *   - "HTTPClient" (incluse dans ESP32)
 * ══════════════════════════════════════════════════════════════════
 */

#include <WiFi.h>
#include <HTTPClient.h>
#include <ArduinoJson.h>
#include <HardwareSerial.h>

// ══════════════════════════════════════════════════════════════════
// CONFIGURATION — MODIFIER ICI
// ══════════════════════════════════════════════════════════════════

// WiFi
const char* WIFI_SSID     = "TOPNET_92A0";       
const char* WIFI_PASSWORD = "obp5c2e93d"; 

// Backend OINSExpress
const char* BACKEND_URL = "http://192.168.1.11:10000";

// ══ Livreur 1 ══
const char* LIVREUR1_ID    = "LIV-001";
const char* LIVREUR1_EMAIL = "islemmrabet11@gmail.com";
const char* LIVREUR1_PASS  = "Azerty29";

// ══ Livreur 2 ══
const char* LIVREUR2_ID    = "LIV-004";
const char* LIVREUR2_EMAIL = "hou7.livreur@gmail.com";
const char* LIVREUR2_PASS  = "Livreur123";

// ══════════════════════════════════════════════════════════════════
// CONSTANTES
// ══════════════════════════════════════════════════════════════════

#define UART_BAUD        115200
#define UART_RX          16
#define UART_TX          17
#define LED_PIN          2
#define SEND_INTERVAL_MS 2000  // Envoi toutes les 2 secondes

// ══════════════════════════════════════════════════════════════════
// VARIABLES GLOBALES
// ══════════════════════════════════════════════════════════════════

HardwareSerial STM32Serial(2);

// Données reçues du STM32
struct VehicleData {
  double  lat     = 36.8065;   // Tunis par défaut
  double  lon     = 10.1815;
  float   vitesse = 0.0;
  String  etat    = "NORMAL";
  uint32_t events = 0;
  uint8_t sats    = 0;
  uint8_t fix     = 0;
  float   accX    = 0.0;     // pas d'accélération horizontale au repos
  float   accY    = 0.0;
  float   accZ    = -1.0;    // gravité réaliste à l'arrêt (en g)
  float   gyrX    = 0.0;
  float   gyrY    = 0.0;
  float   gyrZ    = 0.0;
  bool    valid   = true;  // true par défaut pour démo
} vehicleData;

String uartBuffer = "";

// Tokens JWT pour les 2 livreurs
String token1 = "";
String token2 = "";
bool token1Valid = false;
bool token2Valid = false;

unsigned long lastSend = 0;
unsigned long lastLed  = 0;
bool ledState = false;

// Compteur pour alterner entre les 2 livreurs
int sendCounter = 0;

// Décalage position pour livreur 2 (pour les séparer sur la carte)
float offsetLat = 0.008;  // ~800m de décalage
float offsetLon = 0.010;

// ══════════════════════════════════════════════════════════════════
// SETUP
// ══════════════════════════════════════════════════════════════════

void setup() {
  Serial.begin(115200);
  delay(500);

  pinMode(LED_PIN, OUTPUT);
  digitalWrite(LED_PIN, LOW);

  Serial.println("\n══════════════════════════════════════════");
  Serial.println("  OINSExpress ESP32 — 2 Livreurs");
  Serial.println("  PFA 2026 — FST");
  Serial.println("══════════════════════════════════════════");

  // UART vers STM32
  STM32Serial.begin(UART_BAUD, SERIAL_8N1, UART_RX, UART_TX);
  Serial.println("[OK] UART2 initialisé");

  // WiFi
  connectWiFi();

  // Authentification des 2 livreurs
  if (WiFi.status() == WL_CONNECTED) {
    Serial.println("\n── Authentification Livreur 1 ──");
    token1 = loginToBackend(LIVREUR1_EMAIL, LIVREUR1_PASS);
    token1Valid = (token1.length() > 0);

    delay(1000);

    Serial.println("\n── Authentification Livreur 2 ──");
    token2 = loginToBackend(LIVREUR2_EMAIL, LIVREUR2_PASS);
    token2Valid = (token2.length() > 0);
  }

  Serial.println("\n══════════════════════════════════════════");
  Serial.printf("  Livreur 1 (LIV-001) : %s\n", token1Valid ? "OK ✓" : "ECHEC ✗");
  Serial.printf("  Livreur 2 (LIV-004) : %s\n", token2Valid ? "OK ✓" : "ECHEC ✗");
  Serial.println("══════════════════════════════════════════\n");

  digitalWrite(LED_PIN, HIGH);
}

// ══════════════════════════════════════════════════════════════════
// LOOP
// ══════════════════════════════════════════════════════════════════

void loop() {
  // 1. Lire UART du STM32
  readFromSTM32();

  // 2. Envoi périodique
  if (millis() - lastSend >= SEND_INTERVAL_MS) {
    lastSend = millis();

    // Vérifier WiFi
    if (WiFi.status() != WL_CONNECTED) {
      Serial.println("[!] WiFi perdu - reconnexion...");
      connectWiFi();
      if (WiFi.status() == WL_CONNECTED) {
        token1 = loginToBackend(LIVREUR1_EMAIL, LIVREUR1_PASS);
        token1Valid = (token1.length() > 0);
        token2 = loginToBackend(LIVREUR2_EMAIL, LIVREUR2_PASS);
        token2Valid = (token2.length() > 0);
      }
    }

    sendCounter++;

    // Envoyer Livreur 1 (données STM32 réelles)
    if (token1Valid) {
      sendPosition(LIVREUR1_ID, token1,
                   vehicleData.lat,
                   vehicleData.lon,
                   vehicleData.vitesse,
                   vehicleData.etat);

      // ── Flask IA : classification conduite ──
      sendSensorData(LIVREUR1_ID, token1);
    }

    // Envoyer Livreur 2 (position légèrement décalée pour démo)
    if (token2Valid) {
      // Position simulée avec mouvement
      double lat2 = vehicleData.lat + offsetLat + (sin(sendCounter * 0.1) * 0.001);
      double lon2 = vehicleData.lon + offsetLon + (cos(sendCounter * 0.1) * 0.001);
      float speed2 = 30.0 + (sendCounter % 20);  // Vitesse variable 30-50 km/h

      sendPosition(LIVREUR2_ID, token2,
                   lat2, lon2,
                   speed2, "NORMAL");
    }

    // Envoyer alerte si événement IMU détecté
    if (vehicleData.events > 0 && token1Valid) {
      sendAlert(LIVREUR1_ID, token1);
    }
  }

  // 3. LED heartbeat
  if (millis() - lastLed >= 500) {
    lastLed = millis();
    ledState = !ledState;
    digitalWrite(LED_PIN, ledState);
  }

  delay(10);
}

// ══════════════════════════════════════════════════════════════════
// CONNEXION WIFI
// ══════════════════════════════════════════════════════════════════

void connectWiFi() {
  Serial.printf("\n── Connexion WiFi : %s\n", WIFI_SSID);

  /* Reset complet du stack WiFi avant toute tentative */
  WiFi.disconnect(true);
  WiFi.mode(WIFI_OFF);
  delay(200);
  WiFi.mode(WIFI_STA);
  delay(100);

  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);

  int tries = 0;
  while (WiFi.status() != WL_CONNECTED && tries < 40) {
    delay(500);
    Serial.print(".");
    tries++;
  }

  if (WiFi.status() == WL_CONNECTED) {
    Serial.println();
    Serial.printf("[OK] WiFi connecté ! IP: %s\n", WiFi.localIP().toString().c_str());
  } else {
    Serial.println("\n[X] WiFi échec - mode hors ligne");
  }
}

// ══════════════════════════════════════════════════════════════════
// AUTHENTIFICATION JWT
// ══════════════════════════════════════════════════════════════════

String loginToBackend(const char* email, const char* password) {
  HTTPClient http;
  String url = String(BACKEND_URL) + "/api/auth/signin";

  http.begin(url);
  http.addHeader("Content-Type", "application/json");
  http.setTimeout(15000);

  DynamicJsonDocument doc(256);
  doc["email"]    = email;
  doc["password"] = password;
  String body;
  serializeJson(doc, body);

  int httpCode = http.POST(body);

  if (httpCode == 200) {
    String response = http.getString();
    DynamicJsonDocument resp(1024);
    deserializeJson(resp, response);
    String token = resp["token"].as<String>();
    Serial.printf("[OK] Token obtenu pour %s\n", email);
    http.end();
    return token;
  } else {
    Serial.printf("[X] Login échoué pour %s : HTTP %d\n", email, httpCode);
    http.end();
    return "";
  }
}

// ══════════════════════════════════════════════════════════════════
// ENVOI POSITION
// ══════════════════════════════════════════════════════════════════

void sendPosition(const char* livreurId, String token,
                  double lat, double lon,
                  float speed, String etat) {
  HTTPClient http;
  String url = String(BACKEND_URL) + "/api/positions";

  http.begin(url);
  http.addHeader("Content-Type", "application/json");
  http.addHeader("Authorization", "Bearer " + token);
  http.setTimeout(10000);

  // Convertir état
  String drivingState = "NORMAL";
  if (etat == "risky"  || vehicleData.events & 0x04) drivingState = "RISKY";
  if (etat == "danger" || vehicleData.events & 0x01 || vehicleData.events & 0x02) drivingState = "AGGRESSIVE";

  DynamicJsonDocument doc(512);
  doc["livreurId"]    = livreurId;
  doc["latitude"]     = lat;
  doc["longitude"]    = lon;
  doc["speed"]        = speed;
  doc["drivingState"] = drivingState;
  doc["status"]       = "ACTIVE";
  doc["accX"]         = vehicleData.accX;
  doc["accY"]         = vehicleData.accY;
  doc["accZ"]         = vehicleData.accZ;
  doc["gyrX"]         = vehicleData.gyrX;
  doc["gyrY"]         = vehicleData.gyrY;
  doc["gyrZ"]         = vehicleData.gyrZ;

  String body;
  serializeJson(doc, body);

  int httpCode = http.POST(body);

  if (httpCode == 200 || httpCode == 201) {
    Serial.printf("[OK] %s → lat=%.4f lon=%.4f v=%.1f\n",
                  livreurId, lat, lon, speed);
  } else if (httpCode == 401) {
    Serial.printf("[!] Token expiré pour %s\n", livreurId);
  } else {
    Serial.printf("[X] Envoi échoué %s : HTTP %d\n", livreurId, httpCode);
  }

  http.end();
}

// ══════════════════════════════════════════════════════════════════
// ENVOI DONNÉES IMU → Flask IA (/api/sensor/data)
// ══════════════════════════════════════════════════════════════════

void sendSensorData(const char* livreurId, String token) {
  HTTPClient http;
  String url = String(BACKEND_URL) + "/api/sensor/data";

  http.begin(url);
  http.addHeader("Content-Type", "application/json");
  http.addHeader("Authorization", "Bearer " + token);
  http.setTimeout(15000);  // Flask sur Render peut être lent au 1er appel

  DynamicJsonDocument doc(256);
  doc["livreurId"] = livreurId;
  doc["ax"]        = vehicleData.accX;
  doc["ay"]        = vehicleData.accY;
  doc["az"]        = vehicleData.accZ;
  doc["gx"]        = vehicleData.gyrX;
  doc["gy"]        = vehicleData.gyrY;
  doc["gz"]        = vehicleData.gyrZ;
  doc["latitude"]  = vehicleData.lat;
  doc["longitude"] = vehicleData.lon;

  String body;
  serializeJson(doc, body);

  int httpCode = http.POST(body);

  if (httpCode == 200) {
    String response = http.getString();
    DynamicJsonDocument resp(256);
    deserializeJson(resp, response);
    const char* state = resp["drivingState"] | "?";
    const char* msg   = resp["message"]      | "";
    Serial.printf("[IA] %s → %s | %s\n", livreurId, state, msg);
  } else {
    Serial.printf("[X] IA échouée %s : HTTP %d\n", livreurId, httpCode);
  }

  http.end();
}

// ══════════════════════════════════════════════════════════════════
// ENVOI ALERTE
// ══════════════════════════════════════════════════════════════════

void sendAlert(const char* livreurId, String token) {
  HTTPClient http;
  String url = String(BACKEND_URL) + "/api/traffic";

  http.begin(url);
  http.addHeader("Content-Type", "application/json");
  http.addHeader("Authorization", "Bearer " + token);
  http.setTimeout(10000);

  String alertType = "ACCIDENT";
  String description = "Anomalie IMU détectée";

  if (vehicleData.events & 0x01) {
    alertType   = "ACCIDENT";
    description = "Freinage brusque détecté";
  } else if (vehicleData.events & 0x02) {
    alertType   = "ROAD_BLOCKED";
    description = "Accélération violente détectée";
  } else if (vehicleData.events & 0x04) {
    alertType   = "JAM";
    description = "Virage dangereux détecté";
  }

  DynamicJsonDocument doc(512);
  doc["livreurId"]   = livreurId;
  doc["type"]        = alertType;
  doc["description"] = description;
  doc["latitude"]    = vehicleData.lat;
  doc["longitude"]   = vehicleData.lon;

  String body;
  serializeJson(doc, body);

  int httpCode = http.POST(body);

  if (httpCode == 200 || httpCode == 201) {
    Serial.printf("[OK] Alerte envoyée : %s\n", description.c_str());
  } else {
    Serial.printf("[X] Alerte échouée : HTTP %d\n", httpCode);
  }

  http.end();
}

// ══════════════════════════════════════════════════════════════════
// RÉCEPTION UART DU STM32
// ══════════════════════════════════════════════════════════════════

void readFromSTM32() {
  while (STM32Serial.available()) {
    char c = STM32Serial.read();

    if (c == '\n') {
      if (uartBuffer.length() > 2 && uartBuffer.startsWith("{")) {
        parseJSON(uartBuffer);
      }
      uartBuffer = "";
    } else if (c != '\r') {
      uartBuffer += c;
      if (uartBuffer.length() > 512) uartBuffer = "";
    }
  }
}

void parseJSON(String jsonStr) {
  DynamicJsonDocument doc(512);
  DeserializationError err = deserializeJson(doc, jsonStr);

  if (err) {
    Serial.printf("[X] JSON invalide : %s | data=%s\n", err.c_str(), jsonStr.c_str());
    return;
  }

  // Mise à jour IMU (toujours présents depuis STM32)
  if (doc.containsKey("accX")) vehicleData.accX = doc["accX"].as<float>();
  if (doc.containsKey("accY")) vehicleData.accY = doc["accY"].as<float>();
  if (doc.containsKey("accZ")) vehicleData.accZ = doc["accZ"].as<float>();
  if (doc.containsKey("gyrX")) vehicleData.gyrX = doc["gyrX"].as<float>();
  if (doc.containsKey("gyrY")) vehicleData.gyrY = doc["gyrY"].as<float>();
  if (doc.containsKey("gyrZ")) vehicleData.gyrZ = doc["gyrZ"].as<float>();

  // Mise à jour GPS (optionnels — gardent la dernière valeur si absents)
  if (doc.containsKey("lat"))    vehicleData.lat     = doc["lat"].as<double>();
  if (doc.containsKey("lon"))    vehicleData.lon     = doc["lon"].as<double>();
  if (doc.containsKey("speed"))  vehicleData.vitesse = doc["speed"].as<float>();
  if (doc.containsKey("state"))  vehicleData.etat    = doc["state"].as<String>();
  if (doc.containsKey("events")) vehicleData.events  = doc["events"].as<uint32_t>();
  if (doc.containsKey("sats"))   vehicleData.sats    = doc["sats"].as<uint8_t>();
  if (doc.containsKey("fix"))    vehicleData.fix     = doc["fix"].as<uint8_t>();

  vehicleData.valid = true;

  Serial.printf("[STM32 RX] accX=%.3f accY=%.3f accZ=%.3f | gyrX=%.2f gyrY=%.2f gyrZ=%.2f | etat=%s\n",
                vehicleData.accX, vehicleData.accY, vehicleData.accZ,
                vehicleData.gyrX, vehicleData.gyrY, vehicleData.gyrZ,
                vehicleData.etat.c_str());
}
