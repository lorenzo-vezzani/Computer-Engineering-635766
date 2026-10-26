"""
Per-room accumulator used to build a `system_metrics` row for one
test session (started with the CLI `t` command, closed with `et`).
"""

from datetime import datetime


# Ideal room temperature (°C) used to score comfort during a test.
# Kept constant for now; may become room/season dependent later.
IDEAL_TEMPERATURE = 21.0


class SystemMetrics:

    def __init__(self, room_id, packet_loss_percentage, adaptive_mode=True):
        self.room_id = room_id
        self.packet_loss_percentage = packet_loss_percentage
        self.adaptive_mode = adaptive_mode

        self.start_time = datetime.now()

        self.energy_consumed_wh = 0.0
        self._energy_samples = []

        self.fall_back_count = 0

        self.hvac_on = False
        self.hvac_power = 0
        self.hvac_start_time = None

        # Comfort tracking.
        #
        # Fed exclusively by "test_measurement" messages, which come
        # from the sensor's dedicated test_measurements MQTT topic
        # (building/<room>/hvac/test_measurements). Unlike the normal
        # telemetry topic, the sensor ALWAYS publishes there, even
        # when the "real" measurement for that same cycle is dropped
        # by the simulated packet loss. This is what lets
        # comfort_level be evaluated regardless of the sensor
        # malfunction being tested.
        self.comfort_level = 0.0
        self._temperature_samples = []

    # ------------------------------------------------------------
    # MQTT "test_measurement" messages
    # (always-on side channel; field: temperature)
    # ------------------------------------------------------------
    def process_sensor_message(self, message):
        """
        Process a test-measurement sample received during the test.
        """
        temperature = message.get("temperature")

        if temperature is not None:
            self._temperature_samples.append(temperature)

    # ------------------------------------------------------------
    # MQTT "end_activation" messages
    # (fields: energy_consumed_wh, reason)
    # ------------------------------------------------------------

    def process_activation_end(self, energy_consumed_wh, reason):
        """
        energy_consumed_wh: sample used to build the average energy
                          consumption for the whole test.
        reason:           "fallback" increments the fall back counter.
        """

        if energy_consumed_wh is not None:
            self._energy_samples.append(energy_consumed_wh)

        if reason == "fallback":
            self.fall_back_count += 1

    # ------------------------------------------------------------
    # DERIVED METRICS
    # ------------------------------------------------------------

    def finalize(self):
        """
        Close the test and return a dict ready to be passed to
        Database.save_system_metrics(**result).
        """

        if self._energy_samples:
            self.energy_consumed_wh = (
                sum(self._energy_samples) / len(self._energy_samples)
            )
        else:
            self.energy_consumed_wh = 0.0

        if self._temperature_samples:
            # Average absolute deviation from the ideal temperature,
            # in °C. 0 = perfect comfort, higher = further from ideal.
            self.comfort_level = sum(
                abs(sample - IDEAL_TEMPERATURE)
                for sample in self._temperature_samples
            ) / len(self._temperature_samples)
        else:
            self.comfort_level = 0.0

        duration_seconds = (
            datetime.now() - self.start_time
        ).total_seconds()

        return {
            "room_id": self.room_id,
            "adaptive_mode": self.adaptive_mode,
            "timestamp": self.start_time,
            "duration_seconds": duration_seconds,
            "energy_consumed_wh": self.energy_consumed_wh,
            "packet_loss_percentage": self.packet_loss_percentage,
            "fall_back_count": self.fall_back_count,
            "comfort_level": self.comfort_level,
        }
