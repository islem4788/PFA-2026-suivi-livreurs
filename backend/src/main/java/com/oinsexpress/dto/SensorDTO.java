package com.oinsexpress.dto;

/**
 * SensorDTO — Données IMU envoyées par l'ESP32 (STM32 → UART → ESP32 → WiFi)
 *
 * Format JSON reçu :
 * {
 *   "ax": 0.012,
 *   "ay": -0.003,
 *   "az": 1.001,
 *   "gx": 0.5,
 *   "gy": -0.3,
 *   "gz": 0.1,
 *   "livreurId": "LIV-001"
 * }
 *
 * PFA 2026 — ISTIC
 * Mrabet Islem & Merghni Ons
 */
public class SensorDTO {

    /* ── Accéléromètre (en g) ── */
    private double ax;
    private double ay;
    private double az;

    /* ── Gyroscope (en deg/s) ── */
    private double gx;
    private double gy;
    private double gz;

    /* ── Identifiant livreur ── */
    private String livreurId;

    /* ── Position GPS (optionnelle) ── */
    private Double latitude;
    private Double longitude;

    /* ════════════════════════════
     *  Getters & Setters
     * ════════════════════════════ */

    public double getAx() { return ax; }
    public void setAx(double ax) { this.ax = ax; }

    public double getAy() { return ay; }
    public void setAy(double ay) { this.ay = ay; }

    public double getAz() { return az; }
    public void setAz(double az) { this.az = az; }

    public double getGx() { return gx; }
    public void setGx(double gx) { this.gx = gx; }

    public double getGy() { return gy; }
    public void setGy(double gy) { this.gy = gy; }

    public double getGz() { return gz; }
    public void setGz(double gz) { this.gz = gz; }

    public String getLivreurId() { return livreurId; }
    public void setLivreurId(String livreurId) { this.livreurId = livreurId; }

    public Double getLatitude()  { return latitude; }
    public void setLatitude(Double latitude)   { this.latitude  = latitude; }

    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }
}
