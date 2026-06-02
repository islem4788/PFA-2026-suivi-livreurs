"""
OINSExpress — Microservice Flask XGBoost HYBRIDE (ML + Règles déterministes)
=============================================================================
Modèle entraîné sur données réelles (driver behavior dataset).
Classification binaire : NORMAL | AGGRESSIVE (avec 3 niveaux de sévérité)

ARCHITECTURE HYBRIDE (PFA 2026):
  - Couche 1 : XGBoost (58 features) — détecte virages agressifs, patterns complexes
  - Couche 2 : Règles déterministes sur AccX et JerkMag — détecte freinages/accélérations purs
  - Décision : score final = max(score_ml, score_rules)
  Justification : le modèle ML est très efficace sur AGGRESSIVE (virages)
  mais moins sur VERY_AGGRESSIVE (freinages purs). Les règles comblent ce
  gap, comme dans les systèmes ADAS industriels.

Endpoints :
  GET  /health   → statut du service
  POST /predict  → classification IMU temps réel
  POST /reset/<livreurId> → réinitialise le buffer d'un livreur

Format réponse /predict :
{
  "drivingState": "NORMAL" | "AGGRESSIVE",
  "severity":     "NONE" | "MEDIUM" | "HIGH",
  "message":      "...",
  "proba":        0.08,          // score final (max ML/rules)
  "proba_ml":     0.05,          // score modèle ML seul
  "proba_rules":  0.08,          // score règles seul
  "trigger":      "ml" | "rules",// quelle couche a déclenché
  "bufferSize":   5
}
"""

import os
import json
import logging
import numpy as np
import xgboost as xgb
from flask import Flask, request, jsonify
from features import FeatureExtractor
from pathlib import Path

logging.basicConfig(level=logging.INFO,
                    format='%(asctime)s [%(levelname)s] %(message)s')
log = logging.getLogger(__name__)

app = Flask(__name__)
MODEL_DIR = Path(__file__).parent / "models"

# ══════════════════════════════════════════════════════
# SEUILS DES RÈGLES DÉTERMINISTES
# ══════════════════════════════════════════════════════
# Calibrés depuis les statistiques du dataset jair-jr :
#   Classe 4 (VERY_AGGRESSIVE) AccX range : [-0.08, +0.75]  std=0.26
#
# Justification physique (littérature automobile) :
#   Freinage d'urgence voiture ≈ -0.5g à -0.8g
#   Accélération brusque voiture ≈ +0.4g à +0.7g

# Seuils calibrés sur le dataset jair-jr (analyse statistique des 4 classes) :
#   - Au seuil 0.65g sur AccX_max : 0% de FPR sur classes SLOW+NORMAL, recall 55% sur VERY_AGGR
#   - Au seuil 0.70g : 0% FPR, recall 19% (plus conservateur)
# Le jerk a été testé mais n'est pas discriminant dans ce dataset.

RULE_BRAKE_THRESHOLD      = -0.45   # AccX < -0.45g → freinage agressif (peu de freinages dans dataset)
RULE_ACCEL_THRESHOLD      = +0.65   # AccX > +0.65g → accélération agressive (calibré sur classe 4)
RULE_BRAKE_CRITICAL       = -0.70   # freinage très brusque → HIGH
RULE_ACCEL_CRITICAL       = +0.75   # accélération très brusque → HIGH
RULE_MIN_BUFFER           =  3      # lectures min avant d'évaluer
RULE_CONSECUTIVE_REQUIRED =  3      # lectures consécutives pour confirmer (anti-faux-positifs)


def load_artifacts():
    booster = xgb.Booster()
    booster.load_model(str(MODEL_DIR / "xgb_driver_behavior.json"))
    log.info("✅ Modèle XGBoost chargé")

    feature_cols = json.loads((MODEL_DIR / "feature_cols.json").read_text())
    log.info("✅ %d features chargées", len(feature_cols))

    sp = json.loads((MODEL_DIR / "scaler_params.json").read_text())
    scaler_mean  = np.array(sp["mean_"],  dtype=np.float64)
    scaler_scale = np.array(sp["scale_"], dtype=np.float64)
    log.info("✅ Scaler chargé")

    th = json.loads((MODEL_DIR / "alert_thresholds.json").read_text())
    seuil_warning  = float(th.get("warning",  0.50))
    seuil_critical = float(th.get("critical", 0.90))
    log.info("✅ Seuils ML : WARNING=%.2f CRITICAL=%.2f", seuil_warning, seuil_critical)
    log.info("✅ Seuils RÈGLES : freinage=%.2fg accel=%.2fg",
             RULE_BRAKE_THRESHOLD, RULE_ACCEL_THRESHOLD)

    le = json.loads((MODEL_DIR / "label_encoder.json").read_text())
    classes = le["classes_"]
    idx_aggr = classes.index("AGGRESSIVE")

    return booster, feature_cols, scaler_mean, scaler_scale, \
           seuil_warning, seuil_critical, idx_aggr


booster, FEATURE_COLS, SCALER_MEAN, SCALER_SCALE, \
    SEUIL_WARNING, SEUIL_CRITICAL, IDX_AGGR = load_artifacts()

extractor = FeatureExtractor(window=20)
_rule_counters = {}


def features_to_vector(feat_dict):
    return np.array([[feat_dict.get(c, 0.0) for c in FEATURE_COLS]], dtype=np.float64)


def scale(x):
    return (x - SCALER_MEAN) / (SCALER_SCALE + 1e-12)


def rule_based_score(livreur_id, feat_dict, buf_size):
    """
    Évalue les règles déterministes sur les features rolling.

    Retourne (score ∈ [0,1], raison textuelle).
    Exige RULE_CONSECUTIVE_REQUIRED lectures consécutives au-dessus du seuil
    pour confirmer (anti-faux-positifs sur nids-de-poule).
    """
    if buf_size < RULE_MIN_BUFFER:
        return 0.0, ""

    if livreur_id not in _rule_counters:
        _rule_counters[livreur_id] = {'brake': 0, 'accel': 0}
    counters = _rule_counters[livreur_id]

    acc_x_min = feat_dict.get('AccX_roll_min', 0.0)
    acc_x_max = feat_dict.get('AccX_roll_max', 0.0)

    # ── Règle 1 : Freinage ──
    brake_score = 0.0
    if acc_x_min < RULE_BRAKE_CRITICAL:
        counters['brake'] += 1
        brake_score = 0.95
    elif acc_x_min < RULE_BRAKE_THRESHOLD:
        counters['brake'] += 1
        ratio = (acc_x_min - RULE_BRAKE_THRESHOLD) / (RULE_BRAKE_CRITICAL - RULE_BRAKE_THRESHOLD)
        ratio = max(0.0, min(1.0, ratio))
        brake_score = 0.65 + 0.30 * ratio
    else:
        counters['brake'] = 0

    # ── Règle 2 : Accélération ──
    accel_score = 0.0
    if acc_x_max > RULE_ACCEL_CRITICAL:
        counters['accel'] += 1
        accel_score = 0.95
    elif acc_x_max > RULE_ACCEL_THRESHOLD:
        counters['accel'] += 1
        ratio = (acc_x_max - RULE_ACCEL_THRESHOLD) / (RULE_ACCEL_CRITICAL - RULE_ACCEL_THRESHOLD)
        ratio = max(0.0, min(1.0, ratio))
        accel_score = 0.65 + 0.30 * ratio
    else:
        counters['accel'] = 0

    # ── Confirmation temporelle : exiger N consécutives (anti-faux-positifs) ──
    confirmed_brake = brake_score if counters['brake'] >= RULE_CONSECUTIVE_REQUIRED else brake_score * 0.5
    confirmed_accel = accel_score if counters['accel'] >= RULE_CONSECUTIVE_REQUIRED else accel_score * 0.5

    scores = {'brake': confirmed_brake, 'accel': confirmed_accel}
    best_rule = max(scores, key=scores.get)
    best_score = scores[best_rule]

    reasons = {
        'brake': f"Freinage brusque (AccX_min={acc_x_min:.2f}g)",
        'accel': f"Accélération brusque (AccX_max={acc_x_max:.2f}g)",
    }
    reason = reasons[best_rule] if best_score > 0.5 else ""

    return float(best_score), reason


def classify(p_final):
    if p_final < SEUIL_WARNING:
        return "NORMAL", "NONE", "Conduite normale"
    elif p_final < SEUIL_CRITICAL:
        return "AGGRESSIVE", "MEDIUM", "Conduite agressive détectée — surveillance recommandée"
    else:
        return "AGGRESSIVE", "HIGH", "Conduite TRÈS agressive — intervention urgente"


@app.route('/health', methods=['GET'])
def health():
    return jsonify({
        'status':       'ok',
        'model':        'XGBoost-OINSExpress-Hybrid-ML+Rules',
        'features':     len(FEATURE_COLS),
        'architecture': 'hybrid (ML XGBoost + deterministic rules)',
        'thresholds':   {
            'ml_warning':    SEUIL_WARNING,
            'ml_critical':   SEUIL_CRITICAL,
            'rule_brake':    RULE_BRAKE_THRESHOLD,
            'rule_accel':    RULE_ACCEL_THRESHOLD,
        },
    })


@app.route('/predict', methods=['POST'])
def predict():
    data = request.get_json(silent=True)
    if not data:
        return jsonify({'error': 'Corps JSON requis'}), 400

    livreur_id = data.get('livreurId', 'unknown')
    raw_imu    = data.get('raw_imu', {})

    if not raw_imu:
        return jsonify({'error': 'raw_imu manquant'}), 400

    # 1. Buffer
    extractor.add_reading(livreur_id, raw_imu)
    buf_size = extractor.buffer_size(livreur_id)

    # 2. Features
    feat_dict = extractor.get_features(livreur_id)
    # Gate stationnaire : court-circuite l'IA si le véhicule est manifestement immobile
    # Seuils assouplis pour tolerer le bruit du capteur MPU6050 et les vibrations du moteur
    acc_mag_mean   = feat_dict.get('AccMag_roll_mean', 1.0)
    gyro_mag_mean  = feat_dict.get('GyroMag_roll_mean', 0.0)
    acc_mag_std    = feat_dict.get('AccMag_roll_std', 0.0)

    if abs(acc_mag_mean - 1.0) < 0.08 and gyro_mag_mean < 8.0 and acc_mag_std < 0.05:
        return jsonify({
            'drivingState': 'NORMAL', 'severity': 'NONE',
            'message': 'Véhicule à l\'arrêt',
            'proba': 0.0, 'proba_ml': 0.0, 'proba_rules': 0.0,
            'trigger': 'stationary_gate', 'bufferSize': buf_size,
    })
    if not feat_dict:
        return jsonify({'error': 'Buffer vide'}), 400

    # 3. ML prediction
    x_raw    = features_to_vector(feat_dict)
    x_scaled = scale(x_raw)
    dmatrix  = xgb.DMatrix(x_scaled)
    proba_raw = booster.predict(dmatrix)[0]

    if hasattr(proba_raw, '__len__'):
        p_ml = float(proba_raw[IDX_AGGR])
    else:
        p_ml = float(1.0 - proba_raw)

    # 4. Rules
    p_rules, rule_reason = rule_based_score(livreur_id, feat_dict, buf_size)

    # 5. Fusion = max
    p_final = max(p_ml, p_rules)
    trigger = "rules" if p_rules > p_ml else "ml"

    # 6. Classification
    state, severity, message = classify(p_final)
    if trigger == "rules" and p_rules >= SEUIL_WARNING and rule_reason:
        message = f"{message} | {rule_reason}"

    log.info("[%s] buf=%d  P(ML)=%.3f  P(Rules)=%.3f  → P=%.3f  %s  (%s)",
             livreur_id, buf_size, p_ml, p_rules, p_final, state, trigger)

    return jsonify({
        'drivingState': state,
        'severity':     severity,
        'message':      message,
        'proba':        round(p_final, 4),
        'proba_ml':     round(p_ml, 4),
        'proba_rules':  round(p_rules, 4),
        'trigger':      trigger,
        'bufferSize':   buf_size,
    })


@app.route('/reset/<livreur_id>', methods=['POST'])
def reset_livreur(livreur_id):
    if livreur_id in extractor._buffers:
        del extractor._buffers[livreur_id]
    if livreur_id in _rule_counters:
        del _rule_counters[livreur_id]
    return jsonify({'reset': livreur_id, 'status': 'ok'})


if __name__ == '__main__':
    port = int(os.environ.get('PORT', 5000))
    app.run(host='0.0.0.0', port=port, debug=False)