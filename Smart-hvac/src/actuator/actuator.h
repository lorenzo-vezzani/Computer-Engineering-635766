/* 
 * LOG SETTINGS 
 */
#define LOG_MODULE "HVAC-Actuator"
#define LOG_LEVEL LOG_LEVEL_INFO



/*
 * Sensor data validity states.
 */
#define SENSOR_FRESH  0
#define SENSOR_STALE  1
#define SENSOR_INVALID 2



/*
 * Sensor freshness thresholds.
 *
 * Based on the FIXED default sampling interval
 * (DEFAULT_SAMPLING_RATE), not on the interval actually
 * observed between received messages: with packet loss,
 * the observed interval grows and would make the thresholds
 * drift and become unreliable.
 *
 * FRESH:
 *   message received within 1 expected interval
 *   (no packet missed yet).
 *
 * STALE:
 *   1 expected interval has elapsed with no new message
 *   (1 packet missed) - message is late but still
 *   potentially usable.
 *
 * INVALID:
 *   3 expected intervals have elapsed with no new message
 *   (3 packets missed) - sensor failure suspected.
 */
#define SENSOR_STALE_MULTIPLIER    3
#define SENSOR_STALE_DIVISOR       2
#define SENSOR_INVALID_MULTIPLIER  3




/* ============================================================
 * MQTT CONFIGURATION
 * ============================================================ */

#define MQTT_CLIENT_BROKER_IP_ADDR "fd00::1"
#define MQTT_BROKER_PORT          1883

#define MQTT_CLIENT_ID            "actuator_" ROOM_ID_STR

#define MQTT_SENSOR_TOPIC         "building/" ROOM_ID_STR "/telemetry"
#define MQTT_HVAC_START_TOPIC     "building/" ROOM_ID_STR "/hvac/start"
#define MQTT_HVAC_END_TOPIC       "building/" ROOM_ID_STR "/hvac/end"
#define MQTT_HVAC_CONTROL_TOPIC   "building/" ROOM_ID_STR "/hvac/control"
#define MQTT_HVAC_THRESHOLD_TOPIC "building/" ROOM_ID_STR "/hvac/threshold"

#define MQTT_APP_BUFFER_SIZE      256

/*
* MQTT STATES
*/

#define STATE_INIT    		  0
#define STATE_NET_OK    	  1
#define STATE_CONNECTING      2
#define STATE_CONNECTED       3
#define STATE_SUBSCRIBED      4
#define STATE_DISCONNECTED    5

// Periodic timer to check the state of the MQTT client
#define STATE_MACHINE_PERIODIC     (CLOCK_SECOND >> 1)

/*
 * Maximum TCP segment size for outgoing segments of our socket.
 * This is also the size of the MQTT client's internal output buffer:
 * since we queue up to 3 SUBSCRIBE packets (one per topic of interest)
 * a small buffer can fill up before the stack has flushed the previous
 * packet, causing MQTT_STATUS_OUT_QUEUE_FULL on the following calls.
 */
#define MAX_TCP_SEGMENT_SIZE      128
#define CONFIG_IP_ADDR_STR_LEN    64

/*
 * Room identifier.
 *
 * Can be overridden at compile time, e.g.:
 *   make ... CFLAGS=-DROOM_ID=room2
 *
 * Note: no quotes needed/allowed on the command line -
 * ROOM_ID is a bare token, turned into a string literal
 * below via stringification. This avoids quote-escaping
 * issues in build chains that mangle nested quotes
 * (e.g. the Cooja simulator build).
 *
 * Defaults to room1 when not provided.
 */
#ifndef ROOM_ID
#define ROOM_ID room1
#endif

#define ROOM_ID_STRINGIFY(x) #x
#define ROOM_ID_TOSTRING(x)  ROOM_ID_STRINGIFY(x)
#define ROOM_ID_STR          ROOM_ID_TOSTRING(ROOM_ID)



/*
 * HVAC energy consumption.
 *
 * Power levels (default):
 *   HIGH -> 3 kW
 *   MID  -> 2 kW
 *   LOW  -> 1 kW
 */

#define HVAC_POWER_LOW 1
#define HVAC_POWER_MID 2
#define HVAC_POWER_HIGH 3

#ifndef HVAC_POWER_HIGH_KW
#define HVAC_POWER_HIGH_KW 3.0f
#endif

#define HVAC_POWER_MID_KW (HVAC_POWER_HIGH_KW * 2.0f / 3.0f)
#define HVAC_POWER_LOW_KW (HVAC_POWER_HIGH_KW / 3.0f)



/* ============================================================
 * LED PHASE INDICATORS
 * ============================================================ */


#define LED_NONE              0b0000 
#define LED_ALL               0b1111

#ifdef CONTIKI_TARGET_NRF52840

#undef LED_RED
#define LED_RED              0b0010

#undef LED_GREEN
#define LED_GREEN            0b0100

#undef LED_YELLOW
#define LED_YELLOW           0b0001

#undef LED_YELLOW_GREEN
#define LED_YELLOW_GREEN     0b0101

#undef LED_YELLOW_RED
#define LED_YELLOW_RED       0b0011

#else

#undef LED_GREEN
#define LED_GREEN            0b001

#undef LED_RED
#define LED_RED              0b010

#undef LED_YELLOW
#define LED_YELLOW           0b100

#undef LED_YELLOW_GREEN
#define LED_YELLOW_GREEN     0b101

#undef LED_YELLOW_RED
#define LED_YELLOW_RED       0b110

#endif


#define ACTUATOR_LED_OFF       LED_RED
#define ACTUATOR_LED_REGISTER  LED_YELLOW
#define ACTUATOR_LED_CONNECT   LED_YELLOW
#define ACTUATOR_LED_SUBSCRIBE LED_YELLOW
#define ACTUATOR_LED_SENSOR    LED_GREEN
#define ACTUATOR_LED_INFERENCE LED_YELLOW_GREEN
#define ACTUATOR_LED_WATCHDOG  LED_YELLOW_RED
