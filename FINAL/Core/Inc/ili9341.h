/**
 * @file    ili9341.h
 * @brief   ILI9341 LCD Driver for STM32F429I-DISC1 (SPI5) — SAVER Project
 * @note    The DISC1 board has an ILI9341 240x320 TFT connected via SPI5
 *          PF7=SCK, PF9=MOSI, PC2=CS, PD13=DC
 */

#ifndef ILI9341_H
#define ILI9341_H

#include "stm32f4xx_hal.h"
#include <stdint.h>
#include <string.h>
#include <stdio.h>

/* ── Screen dimensions (landscape mode) ────────────────── */
#define LCD_WIDTH   240
#define LCD_HEIGHT  320

/* ── Colors (RGB565) ───────────────────────────────────── */
#define COLOR_BLACK       0x0000
#define COLOR_WHITE       0xFFFF
#define COLOR_RED         0xF800
#define COLOR_GREEN       0x07E0
#define COLOR_BLUE        0x001F
#define COLOR_YELLOW      0xFFE0
#define COLOR_CYAN        0x07FF
#define COLOR_MAGENTA     0xF81F
#define COLOR_ORANGE      0xFD20
#define COLOR_DARK_GREEN  0x03E0
#define COLOR_DARK_GRAY   0x7BEF
#define COLOR_LIGHT_GRAY  0xC618

/* ── Function Prototypes ───────────────────────────────── */

/**
 * @brief  Initialize ILI9341 display
 * @param  hspi  Pointer to SPI handle (SPI5)
 */
void LCD_Init(SPI_HandleTypeDef *hspi);

/**
 * @brief  Fill entire screen with a color
 */
void LCD_FillScreen(uint16_t color);

/**
 * @brief  Fill a rectangle
 */
void LCD_FillRect(uint16_t x, uint16_t y, uint16_t w, uint16_t h, uint16_t color);

/**
 * @brief  Draw a single character (8x16 font)
 */
void LCD_DrawChar(uint16_t x, uint16_t y, char c, uint16_t fg, uint16_t bg);

/**
 * @brief  Draw a string
 */
void LCD_DrawString(uint16_t x, uint16_t y, const char *str, uint16_t fg, uint16_t bg);

/**
 * @brief  Draw a large string (16x24 font, doubled size)
 */
void LCD_DrawStringLarge(uint16_t x, uint16_t y, const char *str, uint16_t fg, uint16_t bg);

/**
 * @brief  Set drawing window
 */
void LCD_SetWindow(uint16_t x0, uint16_t y0, uint16_t x1, uint16_t y1);

/**
 * @brief  Draw a horizontal line
 */
void LCD_DrawHLine(uint16_t x, uint16_t y, uint16_t w, uint16_t color);

/**
 * @brief  Draw a simple bar (for real-time bar graph)
 */
void LCD_DrawBar(uint16_t x, uint16_t y, uint16_t max_w, uint16_t h,
                 float value, float min_val, float max_val,
                 uint16_t fg, uint16_t bg);

#endif /* ILI9341_H */
