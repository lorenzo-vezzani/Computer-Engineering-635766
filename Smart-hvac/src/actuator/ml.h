#ifndef ML_H_
#define ML_H_

/* ============================================================
 * MODEL CONFIGURATION
 * ============================================================ */
/*
 * Feature order MUST match the Python training code:
 *
 * 0 -> Temperature
 * 1 -> Humidity
 * 2 -> Light
 * 3 -> CO2
 */
#define MODEL_INPUT_SIZE 4

/*
 * Normalizes input features and runs ML inference to predict occupancy.
 */
int run_occupancy_inference(float temperature, float humidity, float light, float co2);

#endif /* ML_H_ */