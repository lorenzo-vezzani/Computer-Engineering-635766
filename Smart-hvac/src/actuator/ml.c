#include "ml.h"
#include "occupancy_classifier.h"
#include "sys/log.h"

#define LOG_MODULE "ML"
#define LOG_LEVEL LOG_LEVEL_INFO

/* ============================================================
 * STANDARD SCALER
 * ============================================================ */

static const float scaler_mean[MODEL_INPUT_SIZE] = {
    /* Temperature */ 21.56f,
    /* Humidity    */ 24.92f,
    /* Light       */ 232.36f,
    /* CO2         */ 734.79f
};

static const float scaler_scale[MODEL_INPUT_SIZE] = {
    /* Temperature */ 1.1069f,
    /* Humidity    */ 2.6161f,
    /* Light       */ 244.7529f,
    /* CO2         */ 305.3185f
};

/* ============================================================
 * FEATURE NORMALIZATION
 * ============================================================ */

static void normalize_features(float *input)
{
    int i;

    /*
     * StandardScaler:
     * x_scaled = (x - mean) / scale
     */

    for(i = 0; i < MODEL_INPUT_SIZE; i++) {
        input[i] = (input[i] - scaler_mean[i]) / scaler_scale[i];
    }
}

/* ============================================================
 * ML INFERENCE
 * ============================================================ */

int
run_occupancy_inference(float temperature, float humidity, float light, float co2)
{
    float input[MODEL_INPUT_SIZE];

    int prediction;

    /*
     * BUILD INPUT VECTOR
     */

    input[0] = temperature;
    input[1] = humidity;
    input[2] = light;
    input[3] = co2;

    /*
     * NORMALIZATION
     */

    normalize_features(input);


    LOG_DBG(
        "ML input: "
        "[%.3f, %.3f, %.3f, %.3f]\n",
        input[0],
        input[1],
        input[2],
        input[3]
    );


    /*
     * EMLEARN INFERENCE
     */

    prediction = occupancy_classifier_predict(input, MODEL_INPUT_SIZE);

    LOG_INFO("Occupancy prediction: %d\n", prediction);

    return prediction;
}