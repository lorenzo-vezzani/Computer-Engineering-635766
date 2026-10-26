
#include <eml_net.h>
static const float occupancy_classifier_layer_0_biases[16] = { 0.080566f, 0.440643f, 0.061317f, -0.148109f, -0.026499f, 0.043921f, 0.132323f, 0.117780f, 0.350946f, 0.144926f, 0.073102f, -0.160485f, 0.467372f, 0.136754f, 0.393105f, 0.186768f };
static const float occupancy_classifier_layer_0_weights[64] = { -0.173598f, -0.028021f, -0.525026f, -0.335490f, 0.460025f, -0.043395f, 0.290974f, 0.122730f, -0.339434f, 0.545119f, -0.052656f, 0.379359f, 0.329351f, -0.069057f, -0.916768f, 0.336491f, 0.425370f, 0.008980f, -0.258539f, 0.608298f, 0.494173f, -0.536368f, -0.554246f, 0.688043f, 0.605587f, -1.086204f, -0.132960f, -0.105230f, -0.376689f, 0.506181f, 0.530703f, -0.213368f, 0.325633f, -1.158328f, 0.352024f, -0.388055f, 0.192984f, -0.435075f, 0.038251f, 0.570998f, 0.256704f, -0.193222f, -0.289695f, -0.049489f, -0.923866f, 0.500762f, 0.522961f, 0.624254f, 0.437168f, 0.572400f, -0.279168f, -0.387173f, 0.224409f, -0.595210f, 0.261554f, 0.287189f, -0.053321f, 0.049772f, -0.432558f, -0.401206f, 0.117352f, 0.145058f, -0.142431f, -0.381839f };
static const float occupancy_classifier_layer_1_biases[8] = { 0.061706f, -0.056070f, -0.070955f, 0.048357f, -0.023132f, 0.151742f, -0.108569f, 0.245319f };
static const float occupancy_classifier_layer_1_weights[128] = { 0.430380f, 0.092047f, 0.220811f, 0.094760f, -0.103838f, -0.137550f, 0.425661f, -0.001882f, 0.137487f, -0.462876f, 0.330633f, 0.829014f, -0.522180f, 1.166687f, -0.220542f, 0.903224f, 0.079232f, -0.492408f, -0.393211f, 0.488074f, 0.384323f, 0.229901f, -0.534336f, -0.303151f, -0.162601f, 0.053242f, 0.355010f, 0.097886f, 0.085038f, -0.246433f, 0.380112f, 0.594577f, -0.122495f, 0.098627f, -0.039326f, -0.222342f, 0.484584f, -0.264157f, 0.597605f, 0.088202f, 0.344252f, 0.358778f, 0.351158f, 0.557221f, -0.064417f, 0.603816f, -0.135111f, 0.107763f, -0.074030f, -0.426972f, -0.169817f, -0.159511f, 0.398994f, 0.361169f, -0.465381f, 0.517367f, 0.217044f, -0.468691f, 0.470461f, 0.150198f, 0.465559f, 0.112384f, -0.088275f, -0.034378f, 0.639546f, -0.030976f, -0.139005f, -0.338383f, 0.690363f, 0.186091f, -0.104628f, -0.246745f, -0.098787f, 0.143732f, -0.499947f, 0.063645f, 0.124667f, 0.469579f, -0.329408f, 0.714105f, -0.196708f, -0.450605f, -0.299627f, 0.067448f, 0.334970f, 0.488522f, 0.296626f, 0.682099f, -0.148803f, -0.119249f, 0.365607f, -0.318140f, 0.059712f, -0.019546f, -0.109075f, -0.288471f, -0.419097f, 0.185206f, -0.402732f, 0.572043f, 0.052508f, 0.573388f, 0.215293f, 0.885174f, 0.547009f, 0.154160f, -0.149510f, -0.223347f, 0.452536f, -0.468462f, -0.256750f, -0.381983f, 0.658122f, -0.145449f, -0.273907f, -0.041580f, 0.279183f, -0.430537f, -0.607247f, -0.942323f, 0.513380f, -0.076764f, 0.198887f, -0.369754f, -0.375823f, 0.333406f, 0.266448f, -0.095934f };
static const float occupancy_classifier_layer_2_biases[1] = { -0.028831f };
static const float occupancy_classifier_layer_2_weights[8] = { 0.984648f, 0.524345f, 0.625246f, -0.976490f, 0.550059f, -0.815025f, 0.362649f, -0.887471f };
static float occupancy_classifier_buf1[16];
static float occupancy_classifier_buf2[16];
static const EmlNetLayer occupancy_classifier_layers[3] = { 
{ 16, 4, occupancy_classifier_layer_0_weights, occupancy_classifier_layer_0_biases, EmlNetActivationRelu }, 
{ 8, 16, occupancy_classifier_layer_1_weights, occupancy_classifier_layer_1_biases, EmlNetActivationRelu }, 
{ 1, 8, occupancy_classifier_layer_2_weights, occupancy_classifier_layer_2_biases, EmlNetActivationLogistic } };
static EmlNet occupancy_classifier = { 3, occupancy_classifier_layers, occupancy_classifier_buf1, occupancy_classifier_buf2, 16 };

    int32_t
    occupancy_classifier_predict(const float *features, int32_t n_features)
    {
        return eml_net_predict(&occupancy_classifier, features, n_features);
    }
    

    int32_t
    occupancy_classifier_regress(const float *features, int32_t n_features, float *out, int32_t out_length)
    {
        return eml_net_regress(&occupancy_classifier, features, n_features, out, out_length);
    }
    

    float
    occupancy_classifier_regress1(const float *features, int32_t n_features)
    {
        return eml_net_regress1(&occupancy_classifier, features, n_features);
    }
    