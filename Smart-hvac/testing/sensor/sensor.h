
/* 
 * LOG SETTINGS 
 */

#define LOG_MODULE "HVAC-Sensor"
#define LOG_LEVEL LOG_LEVEL_INFO



/* ============================================================
 * MQTT CONFIGURATION
 * ============================================================ */

/*---------------------------------------------------------------------------*/
/* MQTT broker address. */
#define MQTT_CLIENT_BROKER_IP_ADDR "fd00::1"
#define DEFAULT_BROKER_PORT         1883

#define MQTT_CLIENT_ID "sensor_" ROOM_ID_STR


/* Telemetry / HVAC command topics */
#define MQTT_TOPIC            "building/" ROOM_ID_STR "/telemetry"
#define MQTT_TOPIC_HVAC_ON    "building/" ROOM_ID_STR "/hvac/on"
#define MQTT_TOPIC_HVAC_OFF   "building/" ROOM_ID_STR "/hvac/off"
#define MQTT_TOPIC_HVAC_POWER "building/" ROOM_ID_STR "/hvac/power"


/*---------------------------------------------------------------------------*/
/* Maximum TCP segment size for outgoing segments of our socket.
 * This is also the size of the MQTT client's internal output buffer:
 * since we queue up to 3 SUBSCRIBE packets back-to-back (one per HVAC
 * command topic) before the stack has a chance to flush them, 32 bytes
 * is too small and causes MQTT_STATUS_OUT_QUEUE_FULL on the 2nd/3rd
 * subscribe. */
#define MAX_TCP_SEGMENT_SIZE    128
#define CONFIG_IP_ADDR_STR_LEN   64

/*---------------------------------------------------------------------------*/
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


// MQTT states
#define STATE_INIT    		  0
#define STATE_NET_OK    	  1
#define STATE_CONNECTING      2
#define STATE_CONNECTED       3
#define STATE_SUBSCRIBED      4
#define STATE_DISCONNECTED    5

// Periodic timer to check the state of the MQTT client
#define STATE_MACHINE_PERIODIC     (CLOCK_SECOND >> 1)



// Default config values
#define DEFAULT_PUBLISH_INTERVAL    (30 * CLOCK_SECOND)
#define DEFAULT_SAMPLING_RATE       60   /* seconds between telemetry publishes */



/* ============================================================
 * LED PHASE INDICATORS
 * ============================================================ */

/*
 * The LED mask identifies the current application phase.
 * A combination of LEDs is used because the number of physical
 * LEDs is smaller than the number of states.
 *
 * 1 -> waiting for connectivity / MQTT registration
 * 2 -> MQTT connecting
 * 3 -> MQTT subscribing
 * 4 -> sensor data generation
 * 5 -> MQTT publish
 * off -> disconnected
 */

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

#define SENSOR_LED_OFF       LED_RED
#define SENSOR_LED_REGISTER  LED_YELLOW
#define SENSOR_LED_CONNECT   LED_YELLOW
#define SENSOR_LED_SUBSCRIBE LED_YELLOW
#define SENSOR_LED_GENERATE  LED_GREEN
#define SENSOR_LED_PUBLISH   LED_YELLOW_GREEN
