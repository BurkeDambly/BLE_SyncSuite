#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "esp_log.h"
#include "led_strip.h"

#define LED_GPIO 2
#define LED_COUNT 1

static const char *TAG = "WS2812";

void app_main(void)
{
    led_strip_handle_t strip;

    led_strip_config_t strip_config = {
        .strip_gpio_num = LED_GPIO,
        .max_leds = LED_COUNT,
        .led_pixel_format = LED_PIXEL_FORMAT_GRB,
        .led_model = LED_MODEL_WS2812,
        .flags.invert_out = false,
    };

    led_strip_rmt_config_t rmt_config = {
        .resolution_hz = 10 * 1000 * 1000, // 10 MHz
        .flags.with_dma = false,
    };

    ESP_ERROR_CHECK(led_strip_new_rmt_device(
        &strip_config, &rmt_config, &strip));

    while (1) {
        ESP_LOGI(TAG, "RED");
        led_strip_set_pixel(strip, 0, 255, 0, 0);
        led_strip_refresh(strip);
        vTaskDelay(pdMS_TO_TICKS(1000));

        ESP_LOGI(TAG, "GREEN");
        led_strip_set_pixel(strip, 0, 0, 255, 0);
        led_strip_refresh(strip);
        vTaskDelay(pdMS_TO_TICKS(1000));

        ESP_LOGI(TAG, "BLUE");
        led_strip_set_pixel(strip, 0, 0, 0, 255);
        led_strip_refresh(strip);
        vTaskDelay(pdMS_TO_TICKS(1000));

        led_strip_clear(strip);
        vTaskDelay(pdMS_TO_TICKS(500));
    }
}
