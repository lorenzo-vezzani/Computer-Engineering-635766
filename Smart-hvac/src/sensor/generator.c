#include "generator.h"
#include <stdlib.h>

#ifndef OCCUPANCY_CHANGE_PROBABILITY
#define OCCUPANCY_CHANGE_PROBABILITY 20
#endif

int simulated_occupancy = 0;

/*
 * Custom deterministic PRNG.
 *
 * This is deliberately NOT a call to random_rand()/random_init():
 * the underlying Contiki platform's random source is not guaranteed
 * to be reproducible bit-for-bit across builds, platforms, or even
 * two runs of the same build - which would make adaptive-mode-on
 * vs. adaptive-mode-off comparisons unfair.
 *
 * sim_rand() is a fully-specified 32-bit LCG (same constants as the
 * classic glibc TYPE_0 rand()):
 *
 *     state[n+1] = (1103515245 * state[n] + 12345) mod 2^31
 */
static uint32_t sim_rand_state;

void
sim_rand_seed(uint32_t seed)
{
    sim_rand_state = seed;
}

uint32_t
sim_rand(void)
{
    sim_rand_state = (1103515245UL * sim_rand_state + 12345UL) & 0x7FFFFFFFUL;
    return sim_rand_state;
}

void
update_occupancy(void)
{
    /*
     * Simulate occupancy changes.
     *
     * At every measurement cycle, there is a probability P
     * of changing the current simulated occupancy state.
     */
    if((sim_rand() % 100) < OCCUPANCY_CHANGE_PROBABILITY) {
        simulated_occupancy = !simulated_occupancy;
    }
}

/* ============================================================
 * SENSOR GENERATORS
 * ============================================================ */

/*
 * Temperature
 *
 * Temperature is represented in tenths of °C.
 *
 * 200 = 20.0 °C
 * 220 = 22.0 °C
 * 230 = 23.0 °C
 *
 * The temperature remains within the range used by the
 * occupancy detection dataset.
 */
int
temp_generator(int previous_temperature, bool hvac_on, int hvac_power)
{
    int target_temperature;
    int delta;
    int noise;

    /*
     * Simulated room conditions.
     *
     * Occupied rooms tend to become slightly warmer.
     * Both targets remain within the dataset range.
     */
    if(simulated_occupancy) {
        target_temperature = 223;   /* 22.3 °C */
    } else {
        target_temperature = 208;   /* 20.8 °C */
    }

    /*
     * HVAC cooling effect.
     *
     * The HVAC can lower the temperature, but we keep the
     * generated values inside a realistic range.
     */
    if(hvac_on) {
        target_temperature -= (hvac_power * 10 / 100);
    }

    /* Move gradually towards the target temperature. */
    delta = target_temperature - previous_temperature;

    /* Maximum variation per sample: 0.3 °C. */
    if(delta > 3) {
        delta = 3;
    }
    if(delta < -3) {
        delta = -3;
    }

    /* Random disturbance: -0.2 °C ... +0.2 °C */
    noise = (sim_rand() % 5) - 2;

    previous_temperature += delta + noise;

    /*
     * Keep temperature within the range represented
     * in the occupancy dataset.
     */
    if(previous_temperature < 200) {
        previous_temperature = 200;
    }
    
    if(previous_temperature > 230) {
        previous_temperature = 230;
    }

    return previous_temperature;
}

/*
 * Relative humidity
 *
 * 20 = 20%
 * 50 = 50%
 *
 * Occupancy increases humidity because of human presence.
 * HVAC operation slightly reduces humidity.
 */
int
hum_generator(int previous_humidity, bool hvac_on, int hvac_power)
{
    int target_humidity;
    int delta;
    int noise;

    /*
     * Empty room:
     *   target = 45%
     *
     * Occupied room:
     *   target = 55%
     */
    if(simulated_occupancy) {
        target_humidity = 27;
    } else {
        target_humidity = 24;
    }

    /*
     * HVAC removes some moisture from the room.
     */
    if(hvac_on) {
        target_humidity -= (hvac_power * 10 / 100);
    }

    delta = target_humidity - previous_humidity;

    /* Humidity changes more slowly than temperature. */
    if(delta > 2) {
        delta = 2;
    }
    if(delta < -2) {
        delta = -2;
    }

    noise = (sim_rand() % 3) - 1;

    previous_humidity += delta + noise;

    /* Physical limits. */
    if(previous_humidity < 20) {
        previous_humidity = 20;
    }
    if(previous_humidity > 90) {
        previous_humidity = 90;
    }

    return previous_humidity;
}

/*
 * Light
 *
 * Dataset unit: Lux
 *
 * Light is independent from the HVAC system.
 */
int
light_generator(void)
{
    if(simulated_occupancy) {
        return 300 + (sim_rand() % 401);   /* 300–700 lux */
    } else {
        return sim_rand() % 101;           /* 0–100 lux */
    }
}

/*
 * CO2
 *
 * Dataset unit: ppm
 *
 * CO2 is strongly influenced by occupancy.
 *
 * Empty room:
 *   target ~= 450 ppm
 *
 * Occupied room:
 *   target ~= 1200 ppm
 *
 * HVAC operation moves CO2 back towards the outdoor baseline.
 */
int
co2_generator(int previous_co2, bool hvac_on, int hvac_power)
{
    int target_co2;
    int delta;
    int noise;

    /* Outdoor / empty-room baseline. */
    const int ambient_co2 = 550;

    /*
     * Occupancy increases CO2 because of human respiration.
     */
    if(simulated_occupancy) {
        target_co2 = 1050;
    } else {
        target_co2 = 550;
    }

    /*
     * HVAC / ventilation reduces CO2 towards the outdoor baseline.
     *
     * At 0%:   no additional reduction
     * At 100%: strong reduction
     */
    if(hvac_on) {
        int hvac_reduction = hvac_power * 500 / 100;

        target_co2 -= hvac_reduction;

        if(target_co2 < ambient_co2) {
            target_co2 = ambient_co2;
        }
    }

    delta = target_co2 - previous_co2;

    /* CO2 changes relatively slowly. */
    if(delta > 100) {
        delta = 100;
    }
    if(delta < -100) {
        delta = -100;
    }

    /* Random disturbance: -20 ... +20 ppm */
    noise = (sim_rand() % 41) - 20;

    previous_co2 += delta + noise;

    /* Physical limits. */
    if(previous_co2 < 350) {
        previous_co2 = 350;
    }
    if(previous_co2 > 3000) {
        previous_co2 = 3000;
    }

    return previous_co2;
}