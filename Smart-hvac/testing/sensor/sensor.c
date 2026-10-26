#include "contiki.h"
#include "net/routing/routing.h"
#include "mqtt.h"
#include "net/ipv6/uip.h"
#include "net/ipv6/uip-icmp6.h"
#include "net/ipv6/sicslowpan.h"
#include "sys/etimer.h" 
#include "sys/ctimer.h"
#include "lib/sensors.h"
#include "dev/button-hal.h"
#include "dev/leds.h"
#include "os/sys/log.h"

#include "sensor.h"
#include "generator.h"
#include <string.h>
#include <strings.h>
#include <stdlib.h>
#include <stdbool.h>
/*---------------------------------------------------------------------------*/

#ifndef PACKET_LOSS_PERCENTAGE
#define PACKET_LOSS_PERCENTAGE 80
#endif

#if PACKET_LOSS_PERCENTAGE < 0 || PACKET_LOSS_PERCENTAGE > 100
#error "PACKET_LOSS_PERCENTAGE must be between 0 and 100"
#endif

/*
 * Seed for the simulated-environment PRNG (occupancy transitions,
 * sensor noise, packet-loss decisions).
 *
 * The whole simulation only ever draws from sim_rand(), and it does so
 * the same number of times per publish cycle regardless of HVAC/adaptive-mode state.
 * Seeding it to a fixed, known value makes the entire measurement sequence reproducible:
 * two runs started with the same RANDOM_SEED will see an IDENTICAL simulated room
 * (same occupancy changes, same noise, same dropped packets), and will differ ONLY
 * in how the actuator reacts to it.
 * This is what makes adaptive-mode-on vs. adaptive-mode-off comparisons fair,
 * and lets metrics.py regenerate the same sequence independently.
 *
 * Override at build time to run a different (but still reproducible) scenario, e.g.:
 *   make ... CFLAGS=-DRANDOM_SEED=42
 */
#ifndef RANDOM_SEED
#define RANDOM_SEED 12345
#endif

/*
 * Dedicated topic used only during a CLI "t"/"et" test session.
 *
 * Unlike MQTT_TOPIC, a message is published here on EVERY sampling
 * cycle, even when the "real" telemetry message for that same cycle
 * is dropped by the simulated packet loss (should_drop_packet()).
 * This gives the cloud side a reliable channel to evaluate
 * comfort_level regardless of the sensor malfunction being tested.
 */
#define MQTT_TOPIC_TEST_MEASUREMENTS "building/" ROOM_ID_STR "/hvac/test_measurements"

/*---------------------------------------------------------------------------*/
/* Various states */
static uint8_t state;

/*---------------------------------------------------------------------------*/
/* Packet dropping handling */
#define LOSS_SEED 98765

static uint32_t loss_rand_state;

void 
loss_rand_seed(uint32_t seed)
{
    loss_rand_state = seed;
}
uint32_t loss_rand(void)
{
    loss_rand_state =
        (1103515245UL * loss_rand_state + 12345UL) & 0x7FFFFFFFUL;

    return loss_rand_state;
}

static bool
should_drop_packet(void)
{
    if(PACKET_LOSS_PERCENTAGE == 0) {
        return false;
    }

    if(PACKET_LOSS_PERCENTAGE == 100) {
        return true;
    }

    return (loss_rand() % 100) < PACKET_LOSS_PERCENTAGE;
}

/*---------------------------------------------------------------------------*/
PROCESS_NAME(hvac_sensor);
AUTOSTART_PROCESSES(&hvac_sensor);

static const char *broker_ip = MQTT_CLIENT_BROKER_IP_ADDR;

/*---------------------------------------------------------------------------*/
/*
 * Buffers for Client ID.
 * Make sure it is large enough to hold the entire string
 */
#define BUFFER_SIZE 64
static char client_id[BUFFER_SIZE];

static struct etimer periodic_timer;

/* Counts periodic ticks so telemetry is only published every
 * DEFAULT_SAMPLING_RATE seconds instead of on every state-machine tick. */
static unsigned long publish_ticks;

/* Index of the next HVAC command topic to subscribe to (0..2). */
static uint8_t sub_index;

/*---------------------------------------------------------------------------*/
/*
 * The main MQTT buffer.
 */
#define APP_BUFFER_SIZE 256
static char app_buffer[APP_BUFFER_SIZE];
/*---------------------------------------------------------------------------*/
static struct mqtt_message *msg_ptr = 0;

static struct mqtt_connection conn;

mqtt_status_t status;
char broker_address[CONFIG_IP_ADDR_STR_LEN];

/*---------------------------------------------------------------------------*/
PROCESS(hvac_sensor, "HVAC Sensor");

/* ============================================================
 * HVAC STATE
 * ============================================================ */

/*
 * Current HVAC state.
 *
 * hvac_on:
 *   false -> HVAC is OFF
 *   true  -> HVAC is ON
 *
 * hvac_power:
 *   0   -> minimum power
 *   100 -> maximum power
 */
static bool hvac_on = false;
static int hvac_power = 0;

/*---------------------------------------------------------------------------*/
static void
set_sensor_phase(uint8_t phase)
{
  leds_off(LEDS_ALL);
  leds_on(phase);
}

/*---------------------------------------------------------------------------*/
static void
pub_handler(const char *topic, uint16_t topic_len, const uint8_t *chunk,
            uint16_t chunk_len)
{
  printf("Pub Handler: topic='%s' (len=%u), chunk_len=%u\n", topic,
          topic_len, chunk_len);

  /* -----------------------------------------------
   * HVAC ON
   * ----------------------------------------------- */
  if(strcmp(topic, MQTT_TOPIC_HVAC_ON) == 0) {
    hvac_on = true;

    /* If no power has been specified yet, use a default power of 50%. */
    if(hvac_power <= 0) {
      hvac_power = 50;
    }

    printf("HVAC ON - power=%d%%\n", hvac_power);
    return;
  }

  /* -----------------------------------------------
   * HVAC OFF
   * ----------------------------------------------- */
  if(strcmp(topic, MQTT_TOPIC_HVAC_OFF) == 0) {
    hvac_on = false;
    /* Keep the last power value so it can resume if switched ON again. */
    printf("HVAC OFF\n");
    return;
  }

  /* -----------------------------------------------
   * HVAC POWER
   * ----------------------------------------------- */
  if(strcmp(topic, MQTT_TOPIC_HVAC_POWER) == 0) {
    char power_buffer[16];
    int power;
    uint16_t payload_length = chunk_len;

    if(payload_length >= sizeof(power_buffer)) {
      payload_length = sizeof(power_buffer) - 1;
    }

    memcpy(power_buffer, chunk, payload_length);
    power_buffer[payload_length] = '\0';

    power = atoi(power_buffer);

    /* Clamp power to [0,100]. */
    if(power < 0) {
      power = 0;
    }
    if(power > 100) {
      power = 100;
    }

    hvac_power = power;

    /* A power command greater than zero implies the HVAC is active;
     * a power command of zero switches the HVAC off. */
    hvac_on = (hvac_power > 0);

    printf("HVAC POWER changed to %d%% - %s\n", hvac_power,
           hvac_on ? "ON" : "OFF");
    return;
  }
}

/*---------------------------------------------------------------------------*/
static void
mqtt_event(struct mqtt_connection *m, mqtt_event_t event, void *data)
{
  switch(event) {
  case MQTT_EVENT_CONNECTED: {
    printf("Application has a MQTT connection\n");
    state = STATE_CONNECTED;
    break;
  }
  case MQTT_EVENT_DISCONNECTED: {
    printf("MQTT Disconnect. Reason %u\n", *((mqtt_event_t *)data));
    state = STATE_DISCONNECTED;
    process_poll(&hvac_sensor);
    break;
  }
  case MQTT_EVENT_PUBLISH: {
    msg_ptr = data;
    pub_handler(msg_ptr->topic, strlen(msg_ptr->topic),
                msg_ptr->payload_chunk, msg_ptr->payload_length);
    break;
  }
  case MQTT_EVENT_SUBACK: {
#if MQTT_311
    mqtt_suback_event_t *suback_event = (mqtt_suback_event_t *)data;
    if(suback_event->success) {
      printf("Application is subscribed to topic successfully\n");
    } else {
      printf("Application failed to subscribe to topic (ret code %x)\n", suback_event->return_code);
    }
#else
    printf("Application is subscribed to topic successfully\n");
#endif
    break;
  }
  case MQTT_EVENT_UNSUBACK: {
    printf("Application is unsubscribed to topic successfully\n");
    break;
  }
  case MQTT_EVENT_PUBACK: {
    printf("Publishing complete.\n");
    break;
  }
  default:
    printf("Application got a unhandled MQTT event: %i\n", event);
    break;
  }
}

static bool
have_connectivity(void)
{
  if(uip_ds6_get_global(ADDR_PREFERRED) == NULL ||
     uip_ds6_defrt_choose() == NULL) {
    return false;
  }
  return true;
}

/*---------------------------------------------------------------------------*/
PROCESS_THREAD(hvac_sensor, ev, data)
{
  static int temperature;
  static int humidity;
  static int light;
  static int co2;
  static unsigned int sequence;

  PROCESS_BEGIN();

  printf("HVAC Sensor Process\n");

  leds_init();
  button_hal_init();

OFF_STATE:

  set_sensor_phase(SENSOR_LED_OFF);
  LOG_INFO("Waiting for start button\n");
  PROCESS_WAIT_EVENT_UNTIL(ev == button_hal_press_event);
  LOG_INFO("Start button pressed; starting sensor\n");

  /*
   * Re-seed on every start-button press (not just once at boot) so
   * that a fresh "t"/"et" test session always begins the simulated
   * scenario from the exact same point, independently of anything
   * that happened - or any sim_rand() calls that were made - in
   * previous runs.
   */
  sim_rand_seed(RANDOM_SEED);
  loss_rand_seed(LOSS_SEED);
  LOG_INFO("PRNG seeded with RANDOM_SEED=%u\n", (unsigned)RANDOM_SEED);

  /* Initial sensor values */
  temperature = 220; /* 22.0 °C */
  humidity = 45;      /* 45% */
  light = 50;          /* 50 lux */
  co2 = 450;           /* 450 ppm */
  sequence = 0;
  publish_ticks = 0;
  sub_index = 0;

  // Initialize the ClientID
  snprintf(client_id, BUFFER_SIZE, "%s", MQTT_CLIENT_ID);

  // Broker registration
  set_sensor_phase(SENSOR_LED_REGISTER);
  mqtt_register(&conn, &hvac_sensor, client_id, mqtt_event,
                  MAX_TCP_SEGMENT_SIZE);

  state = STATE_INIT;

  // Initialize periodic timer to check the status
  etimer_set(&periodic_timer, STATE_MACHINE_PERIODIC);

  /* Main loop */
  while(1) {

    PROCESS_YIELD();

    if(ev == button_hal_press_event) {

      LOG_INFO("Shutdown button pressed\n");

      /*
       * Stop the periodic timer before returning
       * to the OFF state.
       */
      etimer_stop(&periodic_timer);

      /*
       * Disconnect from MQTT broker.
       */
      mqtt_disconnect(&conn);

      /*
       * Turn all LEDs off.
       */
      leds_off(LEDS_ALL);

      /*
       * Reset the state machine.
       */
      state = STATE_INIT;

      /*
       * Keep the process alive and return to OFF.
       */
      goto OFF_STATE;
    }

    if((ev == PROCESS_EVENT_TIMER && data == &periodic_timer) ||
	      ev == PROCESS_EVENT_POLL){

		  if(state==STATE_INIT){
			 if(have_connectivity()==true)
				 state = STATE_NET_OK;
		  }

		  if(state == STATE_NET_OK){
			  // Connect to MQTT server
			  printf("Connecting!\n");
			  set_sensor_phase(SENSOR_LED_CONNECT);

			  memcpy(broker_address, broker_ip, strlen(broker_ip));

			  mqtt_connect(&conn, broker_address, DEFAULT_BROKER_PORT,
						   (DEFAULT_PUBLISH_INTERVAL * 3) / CLOCK_SECOND,
						   MQTT_CLEAN_SESSION_ON);
			  state = STATE_CONNECTING;
		  }

		  if(state==STATE_CONNECTED){

			  set_sensor_phase(SENSOR_LED_SUBSCRIBE);

			  /* Subscribe to the HVAC command topics one at a time,
			   * one per state-machine tick, instead of queueing all
			   * three SUBSCRIBE packets back-to-back: with a small
			   * output buffer the stack may not have flushed the
			   * first packet yet, causing MQTT_STATUS_OUT_QUEUE_FULL
			   * on the following subscribe calls. */
			  switch(sub_index) {
          case 0:
            printf("Subscribing to %s\n", MQTT_TOPIC_HVAC_ON);
            status = mqtt_subscribe(&conn, NULL, MQTT_TOPIC_HVAC_ON, MQTT_QOS_LEVEL_0);
            break;

          case 1:
            printf("Subscribing to %s\n", MQTT_TOPIC_HVAC_OFF);
            status = mqtt_subscribe(&conn, NULL, MQTT_TOPIC_HVAC_OFF, MQTT_QOS_LEVEL_0);
            break;

          case 2:
            printf("Subscribing to %s\n", MQTT_TOPIC_HVAC_POWER);
            status = mqtt_subscribe(&conn, NULL, MQTT_TOPIC_HVAC_POWER, MQTT_QOS_LEVEL_0);
            break;
          
          default:
            status = MQTT_STATUS_OK;
            break;
			  }

			  if(status == MQTT_STATUS_OUT_QUEUE_FULL) {
          /* Queue still busy: retry the same subscription on
          * the next tick instead of aborting the process. */
          LOG_ERR("Tried to subscribe but command queue was full, will retry\n");
			  } 
        else {
				  sub_index++;
			  }

			  if(sub_index >= 3) {
          state = STATE_SUBSCRIBED;
          set_sensor_phase(SENSOR_LED_GENERATE);
			  }
		  }

		if(state == STATE_SUBSCRIBED){

      publish_ticks++;

      /* Only publish once every DEFAULT_SAMPLING_RATE seconds,
      * even though the state machine itself is polled twice
      * per second (STATE_MACHINE_PERIODIC). */
      if(publish_ticks >= (unsigned long)(DEFAULT_SAMPLING_RATE * CLOCK_SECOND / STATE_MACHINE_PERIODIC)) {

        publish_ticks = 0;

        /*
        * Simulate occupancy changes.
        */
        int prev_occupancy = simulated_occupancy;
        update_occupancy();
        if(simulated_occupancy != prev_occupancy) {
            LOG_INFO("Simulated occupancy changed: %s\n",
                    simulated_occupancy ? "OCCUPIED" : "EMPTY");
        }

        /* Generate environmental measurements */
        set_sensor_phase(SENSOR_LED_GENERATE);

        temperature = temp_generator(temperature, hvac_on, hvac_power);
        humidity = hum_generator(humidity, hvac_on, hvac_power);
        light = light_generator();
        co2 = co2_generator(co2, hvac_on, hvac_power);

        sequence++;

        /*
         * SenML Pack (RFC 8428) encoding.
         *
         * Fixed record order: temperature, humidity, light, co2,
         * sequence. This is NOT a general-purpose SenML producer -
         * actuator.c's parse_sensor_message() parses this exact
         * layout with sscanf(), so the order/units below must stay
         * in sync with that function.
         *
         * "bn"/"bt" (base name / base time) are intentionally
         * omitted: the room is already identified by the MQTT topic,
         * and the sensor has no RTC to provide a meaningful epoch
         * time, so the cloud side timestamps messages on arrival.
         */
        snprintf(app_buffer, APP_BUFFER_SIZE,
            "["
            "{\"n\":\"temperature\",\"u\":\"Cel\",\"v\":%d.%d},"
            "{\"n\":\"humidity\",\"u\":\"%%RH\",\"v\":%d},"
            "{\"n\":\"light\",\"u\":\"lx\",\"v\":%d},"
            "{\"n\":\"co2\",\"u\":\"ppm\",\"v\":%d},"
            "{\"n\":\"sequence\",\"v\":%u}"
            "]",
            temperature / 10, abs(temperature % 10),
            humidity,
            light,
            co2,
            sequence);

        /* Always publish to the dedicated test-measurements topic,
         * regardless of the packet-loss decision below. This is the
         * side channel a CLI test session relies on to evaluate
         * comfort_level even when the "real" telemetry message for
         * this cycle is about to be dropped. */
        if(should_drop_packet()) {

            LOG_INFO(
                "Telemetry DROPPED (configured packet loss: %d%%): %s\n",
                PACKET_LOSS_PERCENTAGE,
                app_buffer
            );

          // only testing
          mqtt_publish(&conn, NULL, MQTT_TOPIC_TEST_MEASUREMENTS, (uint8_t *)app_buffer,
                strlen(app_buffer), MQTT_QOS_LEVEL_0, MQTT_RETAIN_OFF);

        } else {

            set_sensor_phase(SENSOR_LED_PUBLISH);

            mqtt_publish(&conn, NULL, MQTT_TOPIC, (uint8_t *)app_buffer,
                  strlen(app_buffer), MQTT_QOS_LEVEL_0, MQTT_RETAIN_OFF);

            LOG_INFO("Telemetry published: %s\n", app_buffer);
        
            set_sensor_phase(SENSOR_LED_GENERATE);
        }
    }

		} else if ( state == STATE_DISCONNECTED ) {
		   LOG_ERR("Disconnected form MQTT broker\n");
		   // Recover from error
		   set_sensor_phase(LED_NONE);
		   sub_index = 0;
		   state = STATE_INIT;
		}

		etimer_set(&periodic_timer, STATE_MACHINE_PERIODIC);

    }

  }

  PROCESS_END();
}