/* USER CODE BEGIN Header */
/**
  ******************************************************************************
  * @file           : main.c
  * @brief          : OINSExpress — STM32F429I-DISC1
  *
  *  PFA 2026 — FST | Mrabet Islem & Merghni Ons
  *
  *  CORRECTIONS APPLIQUEES :
  *    1. Suppression du HAL_Delay(2000) bloquant dans la branche NORMAL
  *    2. Compute_State avec GATE STATIONNAIRE (anti-faux-positifs au repos)
  *    3. Confirmation temporelle (3 lectures consécutives) avant changement d'état
  *    4. Calibration MPU6050 au boot (offsets accéléromètre + gyroscope)
  *    5. Nettoyage commentaire orphelin + doublon LED
  ******************************************************************************
  */
/* USER CODE END Header */

#include "main.h"
/* USER CODE BEGIN Includes */
#include "mpu6050.h"
#include "ili9341.h"
#include <stdio.h>
#include <string.h>
#include <math.h>
/* USER CODE END Includes */

/* Private variables ---------------------------------------------------------*/
I2C_HandleTypeDef hi2c1;
SPI_HandleTypeDef hspi5;
UART_HandleTypeDef huart1;

/* USER CODE BEGIN PD */

/* ══════════════════════════════════════════════════════════════
 * PALETTE — Bleu marine fonce et derivees (RGB565)
 * ══════════════════════════════════════════════════════════════ */
#define COL_BG        0x08A5   /* #0A1428  fond principal (tres sombre) */
#define COL_HEADER    0x0D1F   /* #172554  bleu marine header           */
#define COL_SECTION   0x1108   /* #112244  fond titre de section        */
#define COL_BORDER    0x1A51   /* #1A4A8A  bordure / separateur         */
#define COL_VALUE     0x07FF   /* cyan vif - valeurs numeriques         */
#define COL_LABEL     0x7496   /* #7090B0  labels axes / unites         */
#define COL_WHITE     0xFFFF   /* blanc - titres                        */
#define COL_BAR_BG    0x0908   /* #0D2040  fond barre                   */
#define COL_BAR_ACC   0x041F   /* #0080FF  barre accelerometre          */
#define COL_BAR_GYR   0x0654   /* #00C8A0  barre gyroscope (teal)       */
#define COL_ORANGE    0xFB82   /* #F97316  orange accent                */
#define COL_NORMAL    0x0584   /* #10B981  NORMAL  (vert)               */
#define COL_RISKY     0xFD20   /* orange   RISQUE                       */
#define COL_DANGER    0xF800   /* rouge    DANGEREUX                    */

/* ── Positions Y du layout vertical (ecran 240x320) ────────── */
#define Y_STATE      31
#define Y_STATE_H    99
#define Y_ACC_TITLE  132
#define Y_ACC_X      149
#define Y_ACC_Y      172
#define Y_ACC_Z      195
#define Y_GYR_TITLE  219
#define Y_GYR_X      236
#define Y_GYR_Y      259
#define Y_GYR_Z      282
#define Y_TEMP       304

/* ── LEDs ───────────────────────────────────────────────────── */
#define LED_GREEN_PORT  GPIOG
#define LED_GREEN_PIN   GPIO_PIN_13
#define LED_RED_PORT    GPIOG
#define LED_RED_PIN     GPIO_PIN_14

/* ── Timings ────────────────────────────────────────────────── */
#define MPU_PERIOD_MS   50
#define ESP32_PERIOD_MS 200   /* 5 Hz */
#define LCD_PERIOD_MS   200

/* ── Seuils conduite ────────────────────────────────────────── */
#define THRESH_BRAKE       -1.5f
#define THRESH_ACCEL        1.5f
#define THRESH_TURN        80.0f

/* ── Gate stationnaire (anti-faux-positifs au repos) ───────── */
#define STAT_GYRO_MAG_MAX   8.0f    /* °/s : si gyro_mag < 8 → considéré immobile */
#define STAT_ACC_HORIZ_MAX  0.15f   /* g   : si acc horizontale < 0.15g → idem */

/* ── Confirmation temporelle ────────────────────────────────── */
#define CONFIRM_COUNT  3            /* 3 lectures consécutives (≈150 ms) */

/* USER CODE END PD */

/* USER CODE BEGIN PV */
static char      tx_buf[256];
static MPU6050_t mpu;

/* Offsets de calibration (mesurés au boot, capteur immobile) */
static float acc_off[3] = {0.0f, 0.0f, 0.0f};
static float gyr_off[3] = {0.0f, 0.0f, 0.0f};

typedef enum { STATE_NORMAL, STATE_RISKY, STATE_AGGRESSIVE } DriveState;
static DriveState currentState = STATE_NORMAL;
static DriveState lastState    = STATE_NORMAL;

/* Compteurs pour la confirmation temporelle (anti-faux-positifs) */
static uint8_t risky_counter      = 0;
static uint8_t aggressive_counter = 0;

/* USER CODE END PV */

void SystemClock_Config(void);
static void MX_GPIO_Init(void);
static void MX_SPI5_Init(void);
static void MX_I2C1_Init(void);
static void MX_USART1_UART_Init(void);

/* USER CODE BEGIN PFP */
static void Display_Init_Screen(void);
static void Display_Header(void);
static void Display_DriveState(DriveState state);
static void Display_IMU(MPU6050_t *d);
static void Draw_SectionTitle(uint16_t y, const char *title, uint16_t accent);
static void Draw_ValueRow(uint16_t y, char axis, float value, float vmax,
                          const char *unit, uint16_t bar_color);
static void Send_To_ESP32(MPU6050_t *d);
static DriveState Compute_State(MPU6050_t *d);
/* USER CODE END PFP */

/* USER CODE BEGIN 0 */

/* ── Ecran de demarrage ─────────────────────────────────────── */
static void Display_Init_Screen(void)
{
    LCD_FillScreen(COL_BG);

    /* Header */
    LCD_FillRect(0, 0, 240, 42, COL_HEADER);
    LCD_FillRect(0, 0, 5,   42, COL_ORANGE);
    LCD_DrawHLine(0, 42, 240, COL_BORDER);
    LCD_DrawStringLarge(12, 9, "OINSExpress", COL_WHITE, COL_HEADER);

    /* Titre central */
    LCD_DrawString(40, 110, "Systeme de livraison", COL_LABEL, COL_BG);
    LCD_DrawString(64, 130, "intelligente",         COL_VALUE, COL_BG);

    /* Barre de chargement */
    LCD_DrawString(60, 180, "Demarrage...", COL_VALUE, COL_BG);
    LCD_FillRect(16, 200, 208, 8, COL_BAR_BG);
    for (int i = 0; i <= 208; i += 16) {
        LCD_FillRect(16, 200, i, 8, COL_BAR_ACC);
        HAL_Delay(25);
    }
}

/* ── En-tete fixe (30px, sans texte secondaire) ─────────────── */
static void Display_Header(void)
{
    LCD_FillRect(0, 0, 240, 30, COL_HEADER);
    LCD_FillRect(0, 0, 5,   30, COL_ORANGE);
    LCD_DrawStringLarge(12, 3, "OINSExpress", COL_WHITE, COL_HEADER);
    LCD_DrawHLine(0, 30, 240, COL_BORDER);
}

/* ── Titre de section ───────────────────────────────────────── */
static void Draw_SectionTitle(uint16_t y, const char *title, uint16_t accent)
{
    LCD_FillRect(0, y, 240, 16, COL_SECTION);
    LCD_FillRect(0, y, 3,   16, accent);
    LCD_DrawString(8, y + 1, title, COL_WHITE, COL_SECTION);
}

/* ── Ligne valeur + mini barre centree sur zero ─────────────── */
static void Draw_ValueRow(uint16_t y, char axis,
                          float value, float vmax,
                          const char *unit,
                          uint16_t bar_color)
{
    char buf[28];
    char lbl[2] = {axis, '\0'};

    LCD_FillRect(0, y, 240, 22, COL_BG);

    LCD_DrawString(6, y + 2, lbl, COL_LABEL, COL_BG);

    snprintf(buf, sizeof(buf), "%+7.3f %s", value, unit);
    LCD_DrawString(20, y + 2, buf, COL_VALUE, COL_BG);

    uint16_t bx  = 6;
    uint16_t bw  = 228;
    uint16_t bh  = 5;
    uint16_t by  = y + 16;
    uint16_t mid = bx + bw / 2;
    LCD_FillRect(bx, by, bw, bh, COL_BAR_BG);

    float clamped = value;
    if (clamped >  vmax) clamped =  vmax;
    if (clamped < -vmax) clamped = -vmax;
    int pix = (int)(clamped / vmax * (bw / 2));
    if (pix > 0) {
        LCD_FillRect(mid,       by, (uint16_t)pix,  bh, bar_color);
    } else if (pix < 0) {
        LCD_FillRect((uint16_t)(mid + pix), by, (uint16_t)(-pix), bh, bar_color);
    }

    LCD_FillRect(mid - 1, by, 2, bh, COL_BORDER);
}

/* ── Grande carte etat (inspiree app mobile) ────────────────── */
static void Display_DriveState(DriveState state)
{
    uint16_t bg_color, accent;
    const char *label;
    const char *msg;

    switch (state) {
        case STATE_AGGRESSIVE:
            bg_color = 0x6000;
            accent   = COL_DANGER;
            label    = "DANGEREUX";
            msg      = "Ralentissez !";
            break;
        case STATE_RISKY:
            bg_color = 0x4200;
            accent   = COL_RISKY;
            label    = "RISQUE";
            msg      = "Soyez prudent";
            break;
        default:
            bg_color = 0x0240;
            accent   = COL_NORMAL;
            label    = "NORMAL";
            msg      = "Tout va bien";
            break;
    }

    LCD_FillRect(0, Y_STATE, 240, Y_STATE_H, bg_color);
    LCD_FillRect(0, Y_STATE, 5, Y_STATE_H, accent);

    {
        const char *sub = "ETAT DE CONDUITE";
        int sx = (240 - (int)strlen(sub) * 8) / 2;
        LCD_DrawString((uint16_t)sx, Y_STATE + 10, sub, COL_WHITE, bg_color);
    }
    {
        int lx = (240 - (int)strlen(label) * 16) / 2;
        if (lx < 6) lx = 6;
        LCD_DrawStringLarge((uint16_t)lx, Y_STATE + 32, label, COL_WHITE, bg_color);
    }
    {
        int mx = (240 - (int)strlen(msg) * 8) / 2;
        if (mx < 6) mx = 6;
        LCD_DrawString((uint16_t)mx, Y_STATE + 72, msg, COL_WHITE, bg_color);
    }

    LCD_DrawHLine(0, Y_STATE + Y_STATE_H, 240, COL_BORDER);
}

/* ── Affichage IMU — layout vertical sans footer ────────────── */
static void Display_IMU(MPU6050_t *d)
{
    char buf[32];

    Draw_SectionTitle(Y_ACC_TITLE, " ACCELEROMETRE (g)", COL_BAR_ACC);
    Draw_ValueRow(Y_ACC_X, 'X', d->Ax, 3.0f,   "g  ", COL_BAR_ACC);
    Draw_ValueRow(Y_ACC_Y, 'Y', d->Ay, 3.0f,   "g  ", COL_BAR_ACC);
    Draw_ValueRow(Y_ACC_Z, 'Z', d->Az, 3.0f,   "g  ", COL_BAR_ACC);

    LCD_DrawHLine(0, Y_GYR_TITLE - 1, 240, COL_BORDER);

    Draw_SectionTitle(Y_GYR_TITLE, " GYROSCOPE (deg/s)", COL_BAR_GYR);
    Draw_ValueRow(Y_GYR_X, 'X', d->Gx, 200.0f, "d/s", COL_BAR_GYR);
    Draw_ValueRow(Y_GYR_Y, 'Y', d->Gy, 200.0f, "d/s", COL_BAR_GYR);
    Draw_ValueRow(Y_GYR_Z, 'Z', d->Gz, 200.0f, "d/s", COL_BAR_GYR);

    LCD_DrawHLine(0, Y_TEMP - 1, 240, COL_BORDER);

    LCD_FillRect(0, Y_TEMP, 240, 16, COL_BG);
    snprintf(buf, sizeof(buf), "  Temp : %.1f C", d->Temperature);
    LCD_DrawString(0, Y_TEMP + 1, buf, COL_LABEL, COL_BG);
}

/* ── Calcul etat de conduite (avec GATE STATIONNAIRE) ──────── */
static DriveState Compute_State(MPU6050_t *d)
{
    float gx = fabsf(d->Gx);
    float gy = fabsf(d->Gy);
    float gz = fabsf(d->Gz);
    float ay = fabsf(d->Ay);
    float ax = d->Ax;

    /* Magnitudes globales */
    float gyro_mag  = sqrtf(gx*gx + gy*gy + gz*gz);
    float acc_horiz = sqrtf(ax*ax + ay*ay);

    /* ── 1) GATE STATIONNAIRE ──
     *    Si tout est très faible, le véhicule est à l'arrêt :
     *    on force NORMAL pour ignorer le bruit du capteur. */
    if (gyro_mag < STAT_GYRO_MAG_MAX && acc_horiz < STAT_ACC_HORIZ_MAX) {
        return STATE_NORMAL;
    }

    /* ── 2) Seuils dangereux ── */
    if (gz > 150.0f || ay > 2.5f) return STATE_AGGRESSIVE;
    if (gz > 80.0f  || ay > 1.5f) return STATE_RISKY;
    if (ax < THRESH_BRAKE || ax > THRESH_ACCEL) return STATE_RISKY;

    return STATE_NORMAL;
}

/* ── Envoi JSON → ESP32 via USART1 ─────────────────────────── */
static void Send_To_ESP32(MPU6050_t *d)
{
    int len = snprintf(tx_buf, sizeof(tx_buf),
        "{\"accX\":%.3f,\"accY\":%.3f,\"accZ\":%.3f,"
        "\"gyrX\":%.2f,\"gyrY\":%.2f,\"gyrZ\":%.2f}\n",
        d->Ax, d->Ay, d->Az,
        d->Gx, d->Gy, d->Gz
    );
    HAL_UART_Transmit(&huart1, (uint8_t*)tx_buf, len, 200);
}

/* USER CODE END 0 */

int main(void)
{
  HAL_Init();
  SystemClock_Config();
  MX_GPIO_Init();
  MX_SPI5_Init();
  MX_I2C1_Init();
  MX_USART1_UART_Init();

  /* USER CODE BEGIN 2 */
  LCD_Init(&hspi5);
  Display_Init_Screen();
  HAL_Delay(500);

  /* ─── Init MPU6500 ─── */
  if (MPU6050_Init(&hi2c1) != HAL_OK)
  {
      LCD_FillScreen(COL_DANGER);
      LCD_FillRect(0, 0, 240, 32, 0x5000);
      LCD_DrawStringLarge(8, 6,   "OINSExpress", COL_WHITE, 0x5000);
      LCD_DrawHLine(0, 32, 240, COL_ORANGE);
      LCD_DrawStringLarge(20, 80,  "ERREUR MPU", COL_WHITE, COL_DANGER);
      LCD_DrawString(20, 120, "Verifiez cablage I2C", COL_WHITE,  COL_DANGER);
      LCD_DrawString(20, 140, "PB6=SCL  PB7=SDA",    COL_ORANGE, COL_DANGER);
      HAL_GPIO_WritePin(LED_RED_PORT, LED_RED_PIN, GPIO_PIN_SET);
      while (1);
  }

  /* ════════ Calibration MPU6050 (véhicule IMMOBILE et à plat) ════════
   * 200 échantillons * 10 ms = 2 secondes. Ne PAS bouger le capteur ! */
  LCD_DrawString(20, 120, "Calibration MPU... ne bougez pas", COL_WHITE, COL_BG);
  for (int i = 0; i < 200; i++) {
      MPU6050_Read_All(&hi2c1, &mpu);
      acc_off[0] += mpu.Ax;
      acc_off[1] += mpu.Ay;
      acc_off[2] += (mpu.Az + 1.0f);   /* Az doit lire -1g à l'arrêt */
      gyr_off[0] += mpu.Gx;
      gyr_off[1] += mpu.Gy;
      gyr_off[2] += mpu.Gz;
      HAL_Delay(10);
  }
  for (int k = 0; k < 3; k++) {
      acc_off[k] /= 200.0f;
      gyr_off[k] /= 200.0f;
  }
  LCD_FillRect(0, 120, 240, 16, COL_BG);
  /* ═══════════════════════════════════════════════════════════════════ */

  HAL_GPIO_WritePin(LED_GREEN_PORT, LED_GREEN_PIN, GPIO_PIN_SET);
  LCD_FillScreen(COL_BG);
  Display_Header();
  Display_DriveState(STATE_NORMAL);

  uint32_t t_mpu   = HAL_GetTick();
  uint32_t t_esp32 = HAL_GetTick();
  uint32_t t_lcd   = HAL_GetTick();

  /* USER CODE END 2 */

  while (1)
  {
    uint32_t now = HAL_GetTick();

    /* ── Tâche 1 : Lecture MPU @ 20 Hz ── */
    if ((now - t_mpu) >= MPU_PERIOD_MS)
    {
        t_mpu = now;
        MPU6050_Read_All(&hi2c1, &mpu);

        /* ── Soustraction des offsets de calibration ── */
        mpu.Ax -= acc_off[0];
        mpu.Ay -= acc_off[1];
        mpu.Az -= acc_off[2];
        mpu.Gx -= gyr_off[0];
        mpu.Gy -= gyr_off[1];
        mpu.Gz -= gyr_off[2];

        /* ── Calcul de l'état brut ── */
        DriveState raw_state = Compute_State(&mpu);

        /* ── Confirmation temporelle (anti-faux-positifs) ──
         *    On exige CONFIRM_COUNT lectures consécutives avant
         *    de changer effectivement d'état. */
        if (raw_state == STATE_AGGRESSIVE) {
            if (aggressive_counter < 255) aggressive_counter++;
            risky_counter = 0;
        } else if (raw_state == STATE_RISKY) {
            if (risky_counter < 255) risky_counter++;
            aggressive_counter = 0;
        } else {
            risky_counter      = 0;
            aggressive_counter = 0;
        }

        if      (aggressive_counter >= CONFIRM_COUNT) currentState = STATE_AGGRESSIVE;
        else if (risky_counter      >= CONFIRM_COUNT) currentState = STATE_RISKY;
        else                                          currentState = STATE_NORMAL;

        /* ── Pilotage des LEDs ── */
        if (currentState == STATE_AGGRESSIVE) {
            HAL_GPIO_WritePin(LED_RED_PORT,   LED_RED_PIN,   GPIO_PIN_SET);
            HAL_GPIO_WritePin(LED_GREEN_PORT, LED_GREEN_PIN, GPIO_PIN_RESET);
        } else if (currentState == STATE_RISKY) {
            HAL_GPIO_TogglePin(LED_RED_PORT, LED_RED_PIN);
            HAL_GPIO_WritePin(LED_GREEN_PORT, LED_GREEN_PIN, GPIO_PIN_RESET);
        } else {
            HAL_GPIO_WritePin(LED_RED_PORT,   LED_RED_PIN,   GPIO_PIN_RESET);
            HAL_GPIO_WritePin(LED_GREEN_PORT, LED_GREEN_PIN, GPIO_PIN_SET);
            /* PAS de HAL_Delay ici : il bloquait le système 2 secondes
             * et provoquait l'oscillation NORMAL → RISKY → NORMAL. */
        }
    }

    /* ── Tâche 2 : Envoi ESP32 @ 5 Hz ── */
    if ((now - t_esp32) >= ESP32_PERIOD_MS)
    {
        t_esp32 = now;
        Send_To_ESP32(&mpu);
    }

    /* ── Tâche 3 : LCD @ 5 Hz ── */
    if ((now - t_lcd) >= LCD_PERIOD_MS)
    {
        t_lcd = now;
        if (currentState != lastState) {
            Display_DriveState(currentState);
            lastState = currentState;
        }
        Display_IMU(&mpu);
    }
  }
}

void SystemClock_Config(void)
{
  RCC_OscInitTypeDef RCC_OscInitStruct = {0};
  RCC_ClkInitTypeDef RCC_ClkInitStruct = {0};
  __HAL_RCC_PWR_CLK_ENABLE();
  __HAL_PWR_VOLTAGESCALING_CONFIG(PWR_REGULATOR_VOLTAGE_SCALE1);
  RCC_OscInitStruct.OscillatorType = RCC_OSCILLATORTYPE_HSE;
  RCC_OscInitStruct.HSEState = RCC_HSE_ON;
  RCC_OscInitStruct.PLL.PLLState = RCC_PLL_ON;
  RCC_OscInitStruct.PLL.PLLSource = RCC_PLLSOURCE_HSE;
  RCC_OscInitStruct.PLL.PLLM = 4;
  RCC_OscInitStruct.PLL.PLLN = 180;
  RCC_OscInitStruct.PLL.PLLP = RCC_PLLP_DIV2;
  RCC_OscInitStruct.PLL.PLLQ = 3;
  if (HAL_RCC_OscConfig(&RCC_OscInitStruct) != HAL_OK) Error_Handler();
  if (HAL_PWREx_EnableOverDrive() != HAL_OK) Error_Handler();
  RCC_ClkInitStruct.ClockType = RCC_CLOCKTYPE_HCLK|RCC_CLOCKTYPE_SYSCLK
                              |RCC_CLOCKTYPE_PCLK1|RCC_CLOCKTYPE_PCLK2;
  RCC_ClkInitStruct.SYSCLKSource = RCC_SYSCLKSOURCE_PLLCLK;
  RCC_ClkInitStruct.AHBCLKDivider = RCC_SYSCLK_DIV1;
  RCC_ClkInitStruct.APB1CLKDivider = RCC_HCLK_DIV4;
  RCC_ClkInitStruct.APB2CLKDivider = RCC_HCLK_DIV2;
  if (HAL_RCC_ClockConfig(&RCC_ClkInitStruct, FLASH_LATENCY_5) != HAL_OK) Error_Handler();
}

static void MX_I2C1_Init(void)
{
  hi2c1.Instance = I2C1;
  hi2c1.Init.ClockSpeed = 100000;
  hi2c1.Init.DutyCycle = I2C_DUTYCYCLE_2;
  hi2c1.Init.OwnAddress1 = 0;
  hi2c1.Init.AddressingMode = I2C_ADDRESSINGMODE_7BIT;
  hi2c1.Init.DualAddressMode = I2C_DUALADDRESS_DISABLE;
  hi2c1.Init.OwnAddress2 = 0;
  hi2c1.Init.GeneralCallMode = I2C_GENERALCALL_DISABLE;
  hi2c1.Init.NoStretchMode = I2C_NOSTRETCH_DISABLE;
  if (HAL_I2C_Init(&hi2c1) != HAL_OK) Error_Handler();
  if (HAL_I2CEx_ConfigAnalogFilter(&hi2c1, I2C_ANALOGFILTER_ENABLE) != HAL_OK) Error_Handler();
  if (HAL_I2CEx_ConfigDigitalFilter(&hi2c1, 0) != HAL_OK) Error_Handler();
}

static void MX_SPI5_Init(void)
{
  hspi5.Instance = SPI5;
  hspi5.Init.Mode = SPI_MODE_MASTER;
  hspi5.Init.Direction = SPI_DIRECTION_2LINES;
  hspi5.Init.DataSize = SPI_DATASIZE_8BIT;
  hspi5.Init.CLKPolarity = SPI_POLARITY_LOW;
  hspi5.Init.CLKPhase = SPI_PHASE_1EDGE;
  hspi5.Init.NSS = SPI_NSS_SOFT;
  hspi5.Init.BaudRatePrescaler = SPI_BAUDRATEPRESCALER_16;
  hspi5.Init.FirstBit = SPI_FIRSTBIT_MSB;
  hspi5.Init.TIMode = SPI_TIMODE_DISABLE;
  hspi5.Init.CRCCalculation = SPI_CRCCALCULATION_DISABLE;
  hspi5.Init.CRCPolynomial = 10;
  if (HAL_SPI_Init(&hspi5) != HAL_OK) Error_Handler();
}

static void MX_USART1_UART_Init(void)
{
  huart1.Instance = USART1;
  huart1.Init.BaudRate = 115200;
  huart1.Init.WordLength = UART_WORDLENGTH_8B;
  huart1.Init.StopBits = UART_STOPBITS_1;
  huart1.Init.Parity = UART_PARITY_NONE;
  huart1.Init.Mode = UART_MODE_TX_RX;
  huart1.Init.HwFlowCtl = UART_HWCONTROL_NONE;
  huart1.Init.OverSampling = UART_OVERSAMPLING_16;
  if (HAL_UART_Init(&huart1) != HAL_OK) Error_Handler();
}

static void MX_GPIO_Init(void)
{
  GPIO_InitTypeDef GPIO_InitStruct = {0};
  __HAL_RCC_GPIOF_CLK_ENABLE();
  __HAL_RCC_GPIOH_CLK_ENABLE();
  __HAL_RCC_GPIOC_CLK_ENABLE();
  __HAL_RCC_GPIOD_CLK_ENABLE();
  __HAL_RCC_GPIOA_CLK_ENABLE();
  __HAL_RCC_GPIOG_CLK_ENABLE();
  __HAL_RCC_GPIOB_CLK_ENABLE();
  HAL_GPIO_WritePin(LCD_CS_GPIO_Port, LCD_CS_Pin, GPIO_PIN_SET);
  HAL_GPIO_WritePin(LCD_DC_GPIO_Port, LCD_DC_Pin, GPIO_PIN_RESET);
  HAL_GPIO_WritePin(GPIOG, GPIO_PIN_13|GPIO_PIN_14, GPIO_PIN_RESET);
  GPIO_InitStruct.Pin = LCD_CS_Pin;
  GPIO_InitStruct.Mode = GPIO_MODE_OUTPUT_PP;
  GPIO_InitStruct.Pull = GPIO_NOPULL;
  GPIO_InitStruct.Speed = GPIO_SPEED_FREQ_LOW;
  HAL_GPIO_Init(LCD_CS_GPIO_Port, &GPIO_InitStruct);
  GPIO_InitStruct.Pin = LCD_DC_Pin;
  HAL_GPIO_Init(LCD_DC_GPIO_Port, &GPIO_InitStruct);
  GPIO_InitStruct.Pin = GPIO_PIN_13|GPIO_PIN_14;
  HAL_GPIO_Init(GPIOG, &GPIO_InitStruct);
}

void Error_Handler(void)
{
  __disable_irq();
  while (1) {}
}

#ifdef USE_FULL_ASSERT
void assert_failed(uint8_t *file, uint32_t line) {}
#endif
