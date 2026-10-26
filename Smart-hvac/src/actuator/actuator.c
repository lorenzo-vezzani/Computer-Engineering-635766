#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdbool.h>

#include "contiki.h"
#include "net/ipv6/uip.h"
#include "net/ipv6/uip-icmp6.h"
#include "net/ipv6/sicslowpan.h"
#include "sys/etimer.h"
#include "mqtt.h"
#include "dev/leds.h"
#include "dev/button-hal.h"
#include "sys/log.h"

#include "actuator.h"
#include "ml.h"

#define DEFAULT_SAMPLING_RATE 60

/*
 * Adaptive behaviour:
 *
 * true  -> Fresh/Stale/Invalid + fallback enabled
 * false -> original behaviour
 *
 * This variable can be changed for the experimental comparison.
 */
static bool adaptive_mode = true;
static int sensor_status = SENSOR_INVALID;

/*
 * Time of the last valid sensor message.
 */
static clock_time_t last_sensor_message;

/*
 * Expected sensor transmission interval.
 *
 * Fixed to the default sensor sampling rate.
 *
 * NOTE: this is intentionally NOT re-estimated from the
 * interval actually observed between received messages.
 * Doing so made the value grow together with packet loss
 * (fewer messages get through -> larger observed interval),
 * which in turn inflated the STALE/INVALID thresholds and
 * delayed fault detection exactly when the link was least
 * reliable.
 */
static clock_time_t expected_sensor_interval = DEFAULT_SAMPLING_RATE * CLOCK_SECOND;

/*
 * Timer used to periodically check sensor freshness.
 */
static struct etimer sensor_watchdog_timer;

/*
 * Temperature threshold used by the local ML-based controller.
 */
static float threshold_temperature = 22.0f;

static void update_sensor_status(void)
{
    clock_time_t elapsed;
    clock_time_t stale_threshold;
    clock_time_t invalid_threshold;

    /*
     * If adaptive behaviour is disabled,
     * sensor freshness is not considered.
     */
    if(!adaptive_mode) {
        return;
    }

    elapsed = clock_time() - last_sensor_message;
    stale_threshold = (expected_sensor_interval * SENSOR_STALE_MULTIPLIER) / SENSOR_STALE_DIVISOR;
    invalid_threshold = expected_sensor_interval * SENSOR_INVALID_MULTIPLIER;

    /*
     * FRESH
     */
    if(elapsed < stale_threshold) {
        if(sensor_status != SENSOR_FRESH) {
            sensor_status = SENSOR_FRESH;
            LOG_INFO("Sensor status: FRESH\n");
        }
    }
    /*
     * STALE
     */
    else if(elapsed < invalid_threshold) {
        if(sensor_status != SENSOR_STALE) {
            sensor_status = SENSOR_STALE;
            LOG_WARN("Sensor status: STALE (no recent sensor data)\n");
        }
    }
    /*
     * INVALID
     */
    else {
        if(sensor_status != SENSOR_INVALID) {
            sensor_status = SENSOR_INVALID;
            LOG_ERR("Sensor status: INVALID (sensor failure suspected)\n");
        }
    }
}

/* ============================================================
 * FLOAT PRINTING HELPERS
 *
 * printf's floating-point conversion (%f / %.Nf) is unreliable on
 * some dongles even with the floating-point printf support flag
 * enabled. To avoid it entirely, every float that gets logged or
 * embedded in an MQTT JSON payload is first split into an integer
 * part and a fixed number of fractional decimal digits, then
 * printed as plain integers ("%d.%0*u") instead of relying on %f.
 * ============================================================ */

/*
 * Integer part of a float, truncated toward zero
 * (same digits printf would put before the decimal point).
 */
static int float_int_part(float value)
{
    return (int)value;
}

/*
 * Fractional part of a float as a non-negative integer with
 * "decimals" digits, rounded to the nearest unit.
 *
 * e.g. float_frac_part(3.14159f, 2) -> 14
 *      float_frac_part(-3.14159f, 2) -> 14  (the sign is carried
 *      by float_int_part(), same convention printf's %f uses)
 */
static unsigned int float_frac_part(float value, unsigned int decimals)
{
    float int_part;
    float frac;
    float scale;
    unsigned int i;

    int_part = (float)(int)value;
    frac = value - int_part;

    if(frac < 0.0f) {
        frac = -frac;
    }

    scale = 1.0f;
    for(i = 0; i < decimals; i++) {
        scale *= 10.0f;
    }

    return (unsigned int)(frac * scale + 0.5f);
}

static const char *broker_ip = MQTT_CLIENT_BROKER_IP_ADDR;
static char broker_address[CONFIG_IP_ADDR_STR_LEN];
mqtt_status_t status;

/* ============================================================
 * JSON PARSER - THRESHOLD
 * ============================================================ */

/*
 * Expected cloud threshold message:
 *
 * {
 *   "threshold":27.5
 * }
 *
 * The threshold represents the temperature above which
 * the local controller can activate the HVAC when
 * occupancy is detected.
 */
static int parse_threshold_message(char *payload)
{
    float threshold;
    int result;

    result = sscanf(payload, "{\"threshold\":%f}", &threshold);

    if(result != 1) {
        LOG_WARN("Invalid MQTT threshold payload\n");
        return 0;
    }

    /*
     * Basic sanity check.
     *
     * Avoid accepting physically unreasonable values.
     */
    if(threshold < 0.0f || threshold > 50.0f) {
        LOG_WARN("Invalid temperature threshold: %d.%02u C\n",
                 float_int_part(threshold), float_frac_part(threshold, 2));
        return 0;
    }

    threshold_temperature = threshold;
    LOG_INFO("HVAC temperature threshold changed to %d.%02u C\n",
             float_int_part(threshold_temperature), float_frac_part(threshold_temperature, 2));
    return 1;
}

/* ============================================================
 * SENSOR DATA
 * ============================================================ */

static float temperature;
static float humidity;
static float light;
static float co2;

/* ============================================================
 * HVAC
 * ============================================================ */

/*
 * hvac_power:
 *
 *   0   -> OFF
 *   1-100 -> percentage of maximum power
 *
 * The ML inference decides whether the HVAC must be ON or OFF.
 * The cloud can modify the power through MQTT_HVAC_CONTROL_TOPIC.
 */
static int hvac_power = 0;

/*
 * Previous HVAC state.
 *
 * 0 -> OFF
 * 1 -> ON
 *
 * This is used to publish start/end events only when
 * the HVAC state actually changes.
 */
static int hvac_on = 0;
static float hvac_energy_wh = 0.0f;
static clock_time_t hvac_last_power_change;

/* ============================================================
 * MQTT
 * ============================================================ */

PROCESS(hvac_actuator, "HVAC Actuator");
AUTOSTART_PROCESSES(&hvac_actuator);

/*---------------------------------------------------------------------------*/
/* Various MQTT connection states (same state machine used by the sensor). */
static uint8_t state;
static struct etimer periodic_timer;

/* Index of the next topic to subscribe to (0..2). */
static uint8_t sub_index;

/*---------------------------------------------------------------------------*/
static void 
set_actuator_phase(uint8_t phase)
{
    leds_off(LEDS_ALL);
    leds_on(phase);
}

/*---------------------------------------------------------------------------*/
static bool have_connectivity(void)
{
    if(uip_ds6_get_global(ADDR_PREFERRED) == NULL || uip_ds6_defrt_choose() == NULL) {
        return false;
    }
    return true;
}

/* ============================================================
 * JSON PARSER - SENSOR
 * ============================================================ */

static int parse_sensor_message(char *payload)
{
    float temp;
    float hum;
    float lux;
    float carbon_dioxide;
    int sequence;
    int result;

    /*
     * Parses the SenML Pack (RFC 8428) JSON array published by
     * sensor.c, e.g.:
     *
     *   [{"n":"temperature","u":"Cel","v":22.0},
     *    {"n":"humidity","u":"%RH","v":45},
     *    {"n":"light","u":"lx","v":50},
     *    {"n":"co2","u":"ppm","v":450},
     *    {"n":"sequence","v":1}]
     *
     * NOTE: this is a fixed-order parser, not a general SenML
     * parser - it only accepts records in EXACTLY the order
     * sensor.c emits them in. If that order or the unit strings
     * ever change, this format string must be updated to match.
     */
    result = sscanf(
        payload,
        "[{\"n\":\"temperature\",\"u\":\"Cel\",\"v\":%f},"
        "{\"n\":\"humidity\",\"u\":\"%%RH\",\"v\":%f},"
        "{\"n\":\"light\",\"u\":\"lx\",\"v\":%f},"
        "{\"n\":\"co2\",\"u\":\"ppm\",\"v\":%f},"
        "{\"n\":\"sequence\",\"v\":%d}]",
        &temp, &hum, &lux, &carbon_dioxide, &sequence
    );

    if(result != 5) {
        LOG_WARN("Invalid MQTT sensor payload\n");
        return 0;
    }

    /*
     * Store sensor measurements.
     */
    temperature = temp;
    humidity = hum;
    light = lux;
    co2 = carbon_dioxide;

    /*
     * Update sensor timing information.
     *
     * expected_sensor_interval is intentionally left untouched
     * here (fixed to DEFAULT_SAMPLING_RATE): only the timestamp
     * of the last received message is updated, so that the
     * freshness thresholds stay based on the sensor's nominal
     * sampling rate rather than on the interval actually
     * observed after packet loss.
     */
    last_sensor_message = clock_time();

    /*
     * A valid sensor message immediately restores
     * the FRESH state.
     */
    if(adaptive_mode) {
        sensor_status = SENSOR_FRESH;
    }

    LOG_INFO(
        "Sensor data: T=%d.%02u C RH=%d.%02u %% Light=%d.%02u lux CO2=%d.%02u ppm\n",
        float_int_part(temperature), float_frac_part(temperature, 2),
        float_int_part(humidity), float_frac_part(humidity, 2),
        float_int_part(light), float_frac_part(light, 2),
        float_int_part(co2), float_frac_part(co2, 2)
    );

    if(adaptive_mode) {
        LOG_INFO("Sensor status: FRESH (expected interval: %lu s)\n", (unsigned long)(expected_sensor_interval / CLOCK_SECOND));
    }

    return 1;
}

static float get_hvac_power_kw(int power_level)
{
    if(power_level == HVAC_POWER_HIGH) {
        return HVAC_POWER_HIGH_KW;
    } 
    else if(power_level == HVAC_POWER_MID) {
        return HVAC_POWER_MID_KW;
    } 
    else if(power_level == HVAC_POWER_LOW) {
        return HVAC_POWER_LOW_KW;
    }
    
    return 0.0f;
}

/*
 * Publish an HVAC end event.
 *
 * This function is called ONLY when the HVAC changes
 * from ON to OFF.
 */
static void publish_hvac_end(struct mqtt_connection *m, const char *reason, float energy_consumed_wh)
{
    static char app_buffer[MQTT_APP_BUFFER_SIZE];
    static int sequence = 0;

    snprintf(
        app_buffer, MQTT_APP_BUFFER_SIZE,
        "{\"node_id\":\"" ROOM_ID_STR "\",\"sequence\":%d,\"reason\":\"%s\",\"energy_consumed_wh\":%d.%03u}",
        sequence++, reason,
        float_int_part(energy_consumed_wh), float_frac_part(energy_consumed_wh, 3)
    );

    mqtt_publish(m, NULL, MQTT_HVAC_END_TOPIC, (uint8_t *)app_buffer, strlen(app_buffer), MQTT_QOS_LEVEL_0, MQTT_RETAIN_OFF);
    LOG_INFO("HVAC END published: reason=%s\n", reason);
}

static void update_hvac_energy(void)
{
    clock_time_t now;
    clock_time_t elapsed;
    float power_kw;

    if(!hvac_on) {
        return;
    }

    now = clock_time();
    elapsed = now - hvac_last_power_change;
    power_kw = get_hvac_power_kw(hvac_power);

    /*
     * Energy [Wh] =
     *
     * Power [kW] * time [h] * 1000
     *
     * Since Contiki time is expressed in CLOCK_SECOND:
     *
     * elapsed / CLOCK_SECOND = elapsed time in seconds
     */
    hvac_energy_wh += power_kw * ((float)elapsed / CLOCK_SECOND) * (1000.0f / 3600.0f);
    hvac_last_power_change = now;
}

/* ============================================================
 * JSON PARSER - CONTROL
 * ============================================================ */

/*
 * Expected cloud control message:
 *
 * {
 *   "power":75
 * }
 *
 * Power is expressed as a percentage:
 *
 * 0   -> OFF
 * 100 -> maximum power
 */
static int parse_control_message(char *payload)
{
    char level[16];
    int result;
    int new_power;

    /*
     * Parse the JSON control message.
     *
     * Expected format:
     * {"power": "LOW"}
     * {"power": "MID"}
     * {"power": "HIGH"}
     */
    result = sscanf(payload, " { \"power\" : \"%15[^\"]\" } ", level);

    if(result != 1) {
        LOG_WARN("Invalid MQTT control payload: %s\n", payload);
        return 0;
    }

    /*
     * Convert textual power level into
     * internal numeric representation.
     */
    if(strcmp(level, "LOW") == 0) {
        new_power = HVAC_POWER_LOW;
    } else if(strcmp(level, "MID") == 0) {
        new_power = HVAC_POWER_MID;
    } else if(strcmp(level, "HIGH") == 0) {
        new_power = HVAC_POWER_HIGH;
    } else {
        LOG_WARN("Invalid HVAC power level: %s\n", level);
        return 0;
    }

    /*
     * If the HVAC is already ON and the power level
     * changes, first account for the time spent at
     * the previous power level.
     */
    if(hvac_on && new_power != hvac_power) {
        update_hvac_energy();
    }

    /*
     * Update the current power level.
     */
    hvac_power = new_power;

    /*
     * If the HVAC is ON, the new power level
     * becomes active starting from now.
     */
    if(hvac_on) {
        hvac_last_power_change = clock_time();
    }

    LOG_INFO("HVAC power set by cloud: %s (%d)\n", level, hvac_power);
    return 1;
}

static void apply_sensor_fallback(struct mqtt_connection *m)
{
    /*
     * Fallback policy:
     *
     * If the sensor data becomes INVALID,
     * the local actuator switches the HVAC OFF.
     *
     * This prevents the actuator from keeping the HVAC
     * running indefinitely based on obsolete sensor data.
     */
    if(sensor_status != SENSOR_INVALID) {
        return;
    }

    if(hvac_on) {
        /*
        * Account for the energy consumed before
        * switching the HVAC off.
        */
        update_hvac_energy();
        LOG_WARN("ADAPTIVE FALLBACK: sensor data INVALID -> HVAC OFF energy=%d.%03u Wh\n",
                 float_int_part(hvac_energy_wh), float_frac_part(hvac_energy_wh, 3));
        publish_hvac_end(m, "fallback", hvac_energy_wh);
        hvac_on = 0;
    }
}

/* ============================================================
 * MQTT HVAC EVENT
 * ============================================================ */

/*
 * Publish an HVAC start event.
 *
 * This function is called ONLY when the HVAC changes
 * from OFF to ON.
 */
static void publish_hvac_start(struct mqtt_connection *m)
{
    static char app_buffer[MQTT_APP_BUFFER_SIZE];
    static int sequence = 0;

    snprintf(
        app_buffer, MQTT_APP_BUFFER_SIZE,
        "{\"node_id\":\"" ROOM_ID_STR "\",\"sequence\":%d,\"power\":%d}",
        sequence++, hvac_power
    );

    mqtt_publish(m, NULL, MQTT_HVAC_START_TOPIC, (uint8_t *)app_buffer, strlen(app_buffer), MQTT_QOS_LEVEL_0, MQTT_RETAIN_OFF);
    LOG_INFO("HVAC START published: power-level=%d%%\n", hvac_power);
}

/* ============================================================
 * HVAC CONTROL
 * ============================================================ */

/*
 * The ML inference decides whether the HVAC should be ON/OFF.
 *
 * IMPORTANT:
 *
 * - If the requested state is equal to the current state,
 *   nothing is published.
 *
 * - OFF -> ON:
 *       HVAC is switched on
 *       hvac/start is published
 *
 * - ON -> OFF:
 *       HVAC is switched off
 *       hvac/end is published
 *
 * The actual power is NOT decided here.
 * The power is controlled by the cloud through the
 * MQTT_HVAC_CONTROL_TOPIC.
 */
static void control_hvac(struct mqtt_connection *m, int predicted_occupancy)
{
    int requested_on;

    /*
     * ML/local policy:
     *
     * HVAC ON only if:
     *
     *   occupancy == 1
     *
     * AND
     *
     *   temperature > threshold
     */

    // (threshold_temperature - 1) is the desidered temperature
    requested_on = (predicted_occupancy == 1 && temperature > (threshold_temperature - 1));

    /*
     * No state transition.
     *
     * Do NOT publish anything.
     */
    if(requested_on == hvac_on) {
        LOG_DBG("HVAC state unchanged: %s\n", hvac_on ? "ON" : "OFF");
        return;
    }

    /*
     * OFF -> ON
     */
    if(requested_on) {
        hvac_on = 1;

        /*  
        * Local ML controller starts the HVAC
        * at HIGH power.
        */
        hvac_power = HVAC_POWER_HIGH;

        /*
        * Start a new energy measurement.
        */
        hvac_energy_wh = 0.0f;
        hvac_last_power_change = clock_time();

        LOG_INFO("HVAC ON - occupied=%d temperature=%d.%02u C power=HIGH (%d)\n",
                 predicted_occupancy,
                 float_int_part(temperature), float_frac_part(temperature, 2),
                 hvac_power);
        publish_hvac_start(m);
    }
    /*
     * ON -> OFF
     */
    else {
        /*
        * Account for the energy consumed since the
        * last power change.
        */
        update_hvac_energy();
        LOG_INFO("HVAC OFF - occupied=%d temperature=%d.%02u C energy=%d.%03u Wh\n",
                 predicted_occupancy,
                 float_int_part(temperature), float_frac_part(temperature, 2),
                 float_int_part(hvac_energy_wh), float_frac_part(hvac_energy_wh, 3));
        publish_hvac_end(m, "control", hvac_energy_wh);
        hvac_on = 0;
    }
}

/* ============================================================
 * MQTT CALLBACK
 * ============================================================ */

static void mqtt_event(struct mqtt_connection *m, mqtt_event_t event, void *data)
{   
    switch(event) {

        /* --------------------------------------------------------
         * CONNECTED
         * -------------------------------------------------------- */
        case MQTT_EVENT_CONNECTED:
            LOG_INFO("MQTT connected\n");
            state = STATE_CONNECTED;
            break;

        /* --------------------------------------------------------
         * DISCONNECTED
         * -------------------------------------------------------- */
        case MQTT_EVENT_DISCONNECTED:
            LOG_WARN("MQTT disconnected\n");
            state = STATE_DISCONNECTED;
            process_poll(&hvac_actuator);
            break;

        /* --------------------------------------------------------
         * PUBLISH
         * -------------------------------------------------------- */
        case MQTT_EVENT_PUBLISH: {
            struct mqtt_message *msg = (struct mqtt_message *)data;

            if(msg == NULL) {
                break;
            }

            /* ====================================================
             * SENSOR MESSAGE
             * ==================================================== */
            if(strcmp(msg->topic, MQTT_SENSOR_TOPIC) == 0) {
                static char payload_buffer[MQTT_APP_BUFFER_SIZE];
                int len;
                int predicted_occupancy;

                /* ------------------------------------------------
                 * COPY PAYLOAD
                 * ------------------------------------------------ */
                len = msg->payload_chunk_length;
                if(len >= MQTT_APP_BUFFER_SIZE) {
                    len = MQTT_APP_BUFFER_SIZE - 1;
                }

                memcpy(payload_buffer, msg->payload_chunk, len);
                payload_buffer[len] = '\0';

                LOG_DBG("MQTT sensor message received: %s\n", payload_buffer);

                /* ------------------------------------------------
                 * PARSE SENSOR DATA
                 * ------------------------------------------------ */
                if(!parse_sensor_message(payload_buffer)) {
                    break;
                }

                /* ------------------------------------------------
                 * ADAPTIVE BEHAVIOUR
                 *
                 * If the sensor data is STALE, do not perform
                 * a new ML-based HVAC decision using obsolete data.
                 * ------------------------------------------------ */
                if(adaptive_mode && sensor_status == SENSOR_STALE) {
                    LOG_WARN("Adaptive behavior: STALE sensor data -> holding current HVAC state\n");
                    break;
                }

                /* ------------------------------------------------
                 * FRESH DATA
                 *
                 * Normal ML inference and HVAC control.
                 * ------------------------------------------------ */
                set_actuator_phase(ACTUATOR_LED_INFERENCE);
                predicted_occupancy = run_occupancy_inference(temperature, humidity, light, co2);

                control_hvac(m, predicted_occupancy);
                set_actuator_phase(ACTUATOR_LED_SENSOR);
                break;
            }

            /* ====================================================
             * CLOUD CONTROL MESSAGE
             * ==================================================== */
            else if(strcmp(msg->topic, MQTT_HVAC_CONTROL_TOPIC) == 0) {
                static char payload_buffer[MQTT_APP_BUFFER_SIZE];
                int len;

                /* ------------------------------------------------
                 * COPY PAYLOAD
                 * ------------------------------------------------ */
                len = msg->payload_chunk_length;
                if(len >= MQTT_APP_BUFFER_SIZE) {
                    len = MQTT_APP_BUFFER_SIZE - 1;
                }

                memcpy(payload_buffer, msg->payload_chunk, len);
                payload_buffer[len] = '\0';

                LOG_DBG("MQTT control message received: %s\n", payload_buffer);

                /* ------------------------------------------------
                 * APPLY CLOUD POWER
                 * ------------------------------------------------ */
                parse_control_message(payload_buffer);
                break;
            }

            /* ====================================================
             * CLOUD THRESHOLD MESSAGE
             * ==================================================== */
            else if(strcmp(msg->topic, MQTT_HVAC_THRESHOLD_TOPIC) == 0) {
                static char payload_buffer[MQTT_APP_BUFFER_SIZE];
                int len;

                /* ------------------------------------------------
                 * COPY PAYLOAD
                 * ------------------------------------------------ */
                len = msg->payload_chunk_length;
                if(len >= MQTT_APP_BUFFER_SIZE) {
                    len = MQTT_APP_BUFFER_SIZE - 1;
                }

                memcpy(payload_buffer, msg->payload_chunk, len);
                payload_buffer[len] = '\0';

                LOG_DBG("MQTT threshold message received: %s\n", payload_buffer);

                /* ------------------------------------------------
                 * APPLY NEW TEMPERATURE THRESHOLD
                 * ------------------------------------------------ */
                parse_threshold_message(payload_buffer);
                break;
            }

            /* ====================================================
             * UNKNOWN TOPIC
             * ==================================================== */
            else {
                LOG_WARN("MQTT message received on unknown topic\n");
            }
            break;
        }

        /* --------------------------------------------------------
         * SUBACK
         * -------------------------------------------------------- */
        case MQTT_EVENT_SUBACK:
            LOG_DBG("MQTT subscription acknowledged\n");
            break;

        /* --------------------------------------------------------
         * PUBACK
         * -------------------------------------------------------- */
        case MQTT_EVENT_PUBACK:
            LOG_DBG("MQTT publish acknowledged\n");
            break;

        /* --------------------------------------------------------
         * DEFAULT
         * -------------------------------------------------------- */
        default:
            break;
    }
}

/* ============================================================
 * MAIN PROCESS
 * ============================================================ */

PROCESS_THREAD(hvac_actuator, ev, data)
{
    static struct mqtt_connection conn;

    PROCESS_BEGIN();

    leds_init();
    button_hal_init();

OFF_STATE:

    /*
     * ----------------------------------------------------------
     * OFF STATE
     * ----------------------------------------------------------
     */
    set_actuator_phase(ACTUATOR_LED_OFF);

    LOG_INFO("Waiting for start button\n");
    PROCESS_WAIT_EVENT_UNTIL(ev == button_hal_press_event);

    LOG_INFO("Start button pressed; starting actuator\n");

    /*
     * ----------------------------------------------------------
     * INITIAL VALUES
     * ----------------------------------------------------------
     */
    temperature = 0.0f;
    humidity = 0.0f;
    light = 0.0f;
    co2 = 0.0f;

    hvac_power = 0;
    hvac_on = 0;
    hvac_energy_wh = 0.0f;
    hvac_last_power_change = clock_time();

    sensor_status = SENSOR_INVALID;
    last_sensor_message = clock_time();
    expected_sensor_interval = DEFAULT_SAMPLING_RATE * CLOCK_SECOND;

    sub_index = 0;

    /*
     * ----------------------------------------------------------
     * MQTT REGISTRATION
     * ----------------------------------------------------------
     */
    LOG_INFO("Registering MQTT client\n");
    set_actuator_phase(ACTUATOR_LED_REGISTER);

    mqtt_register(&conn,
                  &hvac_actuator,
                  MQTT_CLIENT_ID,
                  mqtt_event,
                  MAX_TCP_SEGMENT_SIZE);

    state = STATE_INIT;

    /*
     * ----------------------------------------------------------
     * TIMERS
     * ----------------------------------------------------------
     */

    etimer_set(&periodic_timer, STATE_MACHINE_PERIODIC);

    /*
     * ----------------------------------------------------------
     * MAIN LOOP
     * ----------------------------------------------------------
     */
    while(1) {

        PROCESS_YIELD();

        /*
         * ------------------------------------------------------
         * SHUTDOWN / RETURN TO OFF STATE
         * ------------------------------------------------------
         */
        if(ev == button_hal_press_event) {

            LOG_INFO("Shutdown button pressed\n");

            /*
             * Stop timers before returning to OFF.
             */
            etimer_stop(&periodic_timer);
            etimer_stop(&sensor_watchdog_timer);

            /*
             * Never leave the HVAC logically ON.
             * Account for the energy consumed so far and
             * publish a proper end event.
             */
            if(hvac_on) {

                update_hvac_energy();

                publish_hvac_end(&conn,
                                 "shutdown",
                                 hvac_energy_wh);

                hvac_on = 0;
            }

            hvac_power = 0;

            /*
             * Disconnect from MQTT.
             */
            mqtt_disconnect(&conn);

            /*
             * Reset LEDs.
             */
            leds_off(LEDS_ALL);

            /*
             * Reset state variables so that the next start
             * behaves exactly like the first one.
             */
            state = STATE_INIT;
            sub_index = 0;

            temperature = 0.0f;
            humidity = 0.0f;
            light = 0.0f;
            co2 = 0.0f;

            hvac_power = 0;
            hvac_on = 0;
            hvac_energy_wh = 0.0f;

            sensor_status = SENSOR_INVALID;
            last_sensor_message = clock_time();
            expected_sensor_interval =
                DEFAULT_SAMPLING_RATE * CLOCK_SECOND;

            LOG_INFO("System shutdown complete\n");
            LOG_INFO("Returning to OFF state\n");

            /*
             * The process remains alive.
             * Go back to the button wait.
             */
            goto OFF_STATE;
        }

        /*
         * ------------------------------------------------------
         * MQTT CONNECTION STATE MACHINE
         * ------------------------------------------------------
         */
        if((ev == PROCESS_EVENT_TIMER &&
            data == &periodic_timer) ||
           ev == PROCESS_EVENT_POLL) {

            if(state == STATE_INIT) {

                if(have_connectivity() == true) {
                    state = STATE_NET_OK;
                }
            }

            if(state == STATE_NET_OK) {

                LOG_INFO("Connecting to MQTT broker\n");

                set_actuator_phase(ACTUATOR_LED_CONNECT);

                memcpy(broker_address,
                       broker_ip,
                       strlen(broker_ip) + 1);

                mqtt_connect(&conn,
                             broker_address,
                             MQTT_BROKER_PORT,
                             180,
                             MQTT_CLEAN_SESSION_ON);

                state = STATE_CONNECTING;
            }

            if(state == STATE_CONNECTED) {

                set_actuator_phase(ACTUATOR_LED_SUBSCRIBE);

                /*
                 * Subscribe to one topic per tick.
                 */
                switch(sub_index) {

                    case 0:
                        LOG_INFO("Subscribing to %s\n",
                                 MQTT_SENSOR_TOPIC);

                        status = mqtt_subscribe(
                            &conn,
                            NULL,
                            MQTT_SENSOR_TOPIC,
                            MQTT_QOS_LEVEL_0);
                        break;

                    case 1:
                        LOG_INFO("Subscribing to %s\n",
                                 MQTT_HVAC_CONTROL_TOPIC);

                        status = mqtt_subscribe(
                            &conn,
                            NULL,
                            MQTT_HVAC_CONTROL_TOPIC,
                            MQTT_QOS_LEVEL_0);
                        break;

                    case 2:
                        LOG_INFO("Subscribing to %s\n",
                                 MQTT_HVAC_THRESHOLD_TOPIC);

                        status = mqtt_subscribe(
                            &conn,
                            NULL,
                            MQTT_HVAC_THRESHOLD_TOPIC,
                            MQTT_QOS_LEVEL_0);
                        break;

                    default:
                        status = MQTT_STATUS_OK;
                        break;
                }

                if(status == MQTT_STATUS_OUT_QUEUE_FULL) {

                    LOG_ERR(
                        "Tried to subscribe but command queue "
                        "was full, will retry\n");
                }
                else {
                    sub_index++;
                }

                if(sub_index >= 3) {

                    LOG_INFO(
                        "MQTT subscriptions requested\n");

                    /*
                     * Start sensor watchdog.
                     */
                    etimer_set(&sensor_watchdog_timer,
                               CLOCK_SECOND);

                    set_actuator_phase(
                        ACTUATOR_LED_SENSOR);

                    state = STATE_SUBSCRIBED;
                }
            }

            if(state == STATE_DISCONNECTED) {

                LOG_WARN(
                    "Disconnected from MQTT broker\n");

                /*
                 * Recover from error.
                 */
                set_actuator_phase(LED_NONE);

                sub_index = 0;
                state = STATE_INIT;
            }

            /*
             * Restart MQTT state-machine timer.
             */
            etimer_set(&periodic_timer,
                       STATE_MACHINE_PERIODIC);
        }

        /*
         * ------------------------------------------------------
         * SENSOR WATCHDOG
         * ------------------------------------------------------
         */
        if(ev == PROCESS_EVENT_TIMER &&
           data == &sensor_watchdog_timer) {

            set_actuator_phase(
                ACTUATOR_LED_WATCHDOG);

            if(adaptive_mode) {

                update_sensor_status();

                /*
                 * Apply fallback if the sensor has
                 * become invalid.
                 */
                if(sensor_status == SENSOR_INVALID) {
                    apply_sensor_fallback(&conn);
                }
            }

            /*
             * Restart watchdog.
             */
            etimer_set(&sensor_watchdog_timer,
                       CLOCK_SECOND);

            set_actuator_phase(
                ACTUATOR_LED_SENSOR);
        }
    }

    PROCESS_END();
}