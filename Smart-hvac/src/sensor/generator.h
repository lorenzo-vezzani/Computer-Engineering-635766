#ifndef GENERATOR_H_
#define GENERATOR_H_

#include <stdint.h>
#include <stdbool.h>

/*
 * Simulated-environment occupancy state.
 */
extern int simulated_occupancy;

/*
 * Custom deterministic PRNG routines.
 */
void sim_rand_seed(uint32_t seed);
uint32_t sim_rand(void);

/*
 * Routine to simulate occupancy changes.
 */
void update_occupancy(void);

/*
 * Sensor generators for physical quantities.
 */
int temp_generator(int previous_temperature, bool hvac_on, int hvac_power);
int hum_generator(int previous_humidity, bool hvac_on, int hvac_power);
int light_generator(void);
int co2_generator(int previous_co2, bool hvac_on, int hvac_power);

#endif /* GENERATOR_H_ */