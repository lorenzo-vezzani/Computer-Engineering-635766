import json
import logging
import threading
from queue import Empty

from metrics import SystemMetrics


logger = logging.getLogger(__name__)


class Controller(threading.Thread):
    """
    HVAC energy controller.

    The local actuator decides whether an HVAC system has to be
    turned ON or OFF.

    The cloud controller is responsible only for allocating the
    available building power among the HVAC systems that are
    currently active.

    Power levels:
        LOW  = 33%
        MID  = 66%
        HIGH = 100%

    OFF is never sent by this controller. The actuator switches
    itself OFF when its local ML-based decision requires it.
    """

    LOW = "LOW"
    MID = "MID"
    HIGH = "HIGH"

    POWER_LEVELS = {
        LOW: 1 / 3,
        MID: 2 / 3,
        HIGH: 1.0,
    }

    def __init__(
        self,
        mqtt_client,
        controller_queue,
        shutdown_event,
        rooms_config,
        max_power,
    ):
        super().__init__(
            name="controller-thread",
            daemon=True
        )

        self.mqtt_client = mqtt_client
        self.controller_queue = controller_queue
        self.shutdown_event = shutdown_event

        # --------------------------------------------------------
        # Static room configuration
        # --------------------------------------------------------

        self.rooms_config = {
            room["id"]: room
            for room in rooms_config
        }

        # Maximum power that can be allocated to the whole building.
        self.max_power = max_power

        # --------------------------------------------------------
        # Dynamic device state
        # --------------------------------------------------------

        self._state_lock = threading.Lock()

        self._device_state = {}

        # --------------------------------------------------------
        # "t" / "et" CLI test sessions, keyed by room_id.
        # --------------------------------------------------------

        self._test_lock = threading.Lock()

        self.current_levels = {}

        self._active_tests = {}

    # ============================================================
    # PUBLIC STATUS
    # ============================================================

    def get_status(self):
        """
        Return a snapshot of the current state.

        Only the latest measurement for each room is stored.
        """

        with self._state_lock:
            return {
                room_id: dict(state)
                for room_id, state in self._device_state.items()
            }

    # ============================================================
    # MEASUREMENT STATE
    # ============================================================

    def _update_state(self, measurement):
        """
        Store only the latest measurement for a room.
        """

        room_id = measurement["room_id"]

        with self._state_lock:

            state = self._device_state.setdefault(
                room_id,
                {}
            )

            state.update(
                {
                    "timestamp": measurement["timestamp"],
                    "temperature": measurement.get("temperature"),
                    "humidity": measurement.get("humidity"),
                    "light": measurement.get("light"),
                    "co2": measurement.get("co2"),
                }
            )

    # ============================================================
    # HVAC ACTIVATION STATE
    # ============================================================

    def _update_activation_state(self, message, active):
        """
        Update the current HVAC activation state.

        start_activation:
            hvac_active = True
            initial level = HIGH

        end_activation:
            hvac_active = False
            level = OFF

        The OFF state is stored internally but is never sent
        by the controller to the actuator.
        """

        room_id = message["room_id"]

        with self._state_lock:

            state = self._device_state.setdefault(
                room_id,
                {}
            )

            state["hvac_active"] = active
            state["hvac_timestamp"] = message["timestamp"]

            if active:

                # When a local actuator switches ON, it starts
                # at HIGH as requested.
                state["hvac_power"] = self.HIGH

            else:

                state["hvac_power"] = "OFF"

    # ============================================================
    # TEST SESSIONS (CLI "t" / "et" commands)
    # ============================================================

    def start_test(self, room_id, packet_loss_percentage, adaptive_mode=True):

        room_id = str(room_id)

        with self._test_lock:
            self._active_tests[room_id] = SystemMetrics(
                room_id=room_id,
                packet_loss_percentage=packet_loss_percentage,
                adaptive_mode=adaptive_mode,
            )

    def end_test(self, room_id):
        """
        Close a test session and return the resulting metrics as a
        dict ready for Database.save_system_metrics(**result), or
        None if no test was active for that room.
        """

        room_id = str(room_id)

        with self._test_lock:
            test = self._active_tests.pop(room_id, None)

        if test is None:
            return None

        return test.finalize()

    def _record_test_measurement(self, message):

        room_id = str(message["room_id"])

        with self._test_lock:
            test = self._active_tests.get(room_id)

            if test is not None:
                test.process_sensor_message(message)


    def _record_test_activation_end(self, message):

        room_id = str(message["room_id"])

        with self._test_lock:
            test = self._active_tests.get(room_id)

            if test is not None:
                test.process_activation_end(
                    energy_consumed_wh=message.get("energy_consumed_wh"),
                    reason=message.get("reason"),
                )

    # ============================================================
    # POWER CALCULATION
    # ============================================================

    def _get_active_rooms(self):
        """
        Return the rooms whose HVAC is currently active.
        """

        active_rooms = []

        with self._state_lock:

            for room_id, state in self._device_state.items():

                if not state.get("hvac_active", False):
                    continue

                config = self.rooms_config.get(room_id)

                if config is None:
                    logger.warning(
                        "No configuration found for room %s",
                        room_id
                    )
                    continue

                active_rooms.append(
                    {
                        "room_id": room_id,
                        "priority": config["priority"],
                        "max_power": config["max_power"],
                    }
                )

        return active_rooms

    def _level_power(self, room):
        """
        Return the actual power associated with the current
        level of a room.
        """

        room_id = room["room_id"]

        with self._state_lock:
            state = self._device_state.get(room_id, {})

            level = state.get(
                "hvac_power",
                self.HIGH
            )

        if level == self.LOW:
            factor = self.POWER_LEVELS[self.LOW]

        elif level == self.MID:
            factor = self.POWER_LEVELS[self.MID]

        elif level == self.HIGH:
            factor = self.POWER_LEVELS[self.HIGH]

        else:
            factor = 0.0

        return room["max_power"] * factor

    # ============================================================
    # LEVEL SELECTION
    # ============================================================

    def _calculate_levels(self, active_rooms):
        """
        Calculate the HVAC power level assigned to each active room.

        If all HVAC systems can operate at HIGH without exceeding
        the building power limit, all rooms remain at HIGH.

        Otherwise power is allocated proportionally to room
        priorities.

        The resulting allocation is mapped to LOW / MID / HIGH.
        """

        if not active_rooms:
            return {}

        # --------------------------------------------------------
        # Case 1:
        # Everyone can operate at HIGH.
        # --------------------------------------------------------

        total_high_power = sum(
            room["max_power"]
            for room in active_rooms
        )

        if total_high_power <= self.max_power:
            levels = {
                room["room_id"]: self.HIGH
                for room in active_rooms
            }

        else:
            # ----------------------------------------------------
            # Case 2:
            # Building power is constrained.
            # ----------------------------------------------------

            total_priority = sum(
                room["priority"]
                for room in active_rooms
            )

            levels = {}

            for room in active_rooms:

                room_id = room["room_id"]
                priority = room["priority"]

                allocated_power = (
                    self.max_power
                    * priority
                    / total_priority
                )

                power_ratio = (
                    allocated_power
                    / room["max_power"]
                )

                if power_ratio < 0.50:
                    level = self.LOW

                elif power_ratio < 1:
                    level = self.MID

                else:
                    level = self.HIGH

                levels[room_id] = level

        # --------------------------------------------------------
        # Print only if the allocation changed.
        # --------------------------------------------------------

        # debug
        """
        if levels != self.current_levels:
            print(f"[CONTROLLER] Adjusted power levels: {levels}")
            self.current_levels = levels.copy()
        """

        return levels

    # ============================================================
    # MQTT COMMAND
    # ============================================================

    def _publish_power_command(self, room_id, level):
        """
        Publish a power allocation command to the actuator.
        """

        room = self.rooms_config[room_id]

        topic = room["hvac_control_topic"]

        payload = json.dumps(
            {
                "power": level
            }
        )

        self.mqtt_client.publish(
            topic,
            payload
        )

        logger.info(
            "Room %s: HVAC power set to %s",
            room_id,
            level
        )

    def _publish_threshold_command(self, room_id, threshold):
        room = self.rooms_config[room_id]
            
        topic = room["threshold_topic"]
            
        payload = json.dumps(
            {
                "threshold": threshold
            }
        )
            
        self.mqtt_client.publish(
            topic,
            payload
        )
            
        logger.info(
            "Room %s: Temperature threshold set to %s",
            room_id,
            threshold
        )

    # ============================================================
    # CONTROL
    # ============================================================

    def _control(self):
        """
        Allocate the available building power among active HVACs.

        The controller NEVER decides whether an HVAC should be ON
        or OFF.

        That decision is made locally by the actuator using ML.

        The controller only decides:
            LOW
            MID
            HIGH
        """

        active_rooms = self._get_active_rooms()

        if not active_rooms:
            return

        # --------------------------------------------------------
        # Calculate desired level for every active HVAC.
        # --------------------------------------------------------

        levels = self._calculate_levels(
            active_rooms
        )

        # --------------------------------------------------------
        # Apply only actual changes.
        # --------------------------------------------------------

        for room_id, new_level in levels.items():

            with self._state_lock:

                state = self._device_state.get(
                    room_id
                )

                if state is None:
                    continue

                current_level = state.get(
                    "hvac_power"
                )

                if current_level == new_level:
                    continue

                state["hvac_power"] = new_level

            # Publish outside the lock.
            self._publish_power_command(
                room_id,
                new_level
            )

    def set_threshold(self, room_id, threshold):

        self._publish_threshold_command(
            room_id,
            threshold
        )

    # ============================================================
    # THREAD
    # ============================================================

    def run(self):
        logger.info(
            "Controller thread started"
        )

        while (
            not self.shutdown_event.is_set()
            or not self.controller_queue.empty()
        ):

            try:

                message = self.controller_queue.get(
                    timeout=0.5
                )

            except Empty:

                continue

            try:

                message_type = message["type"]

                if message_type == "measurement":

                    # Only the latest measurement is kept.
                    self._update_state(
                        message
                    )

                    self._record_test_measurement(message)

                elif message_type == "test_measurement":

                    # Always-on side channel used only to feed
                    # comfort_level during a CLI test session; never
                    # touches the live device state.
                    self._record_test_measurement(message)

                elif message_type == "start_activation":

                    self._update_activation_state(
                        message,
                        True
                    )

                elif message_type == "end_activation":

                    self._update_activation_state(
                        message,
                        False
                    )

                    self._record_test_activation_end(message)

                else:

                    logger.warning(
                        "Unknown controller message type: %s",
                        message_type
                    )

                # ------------------------------------------------
                # Control is ALWAYS executed.
                # ------------------------------------------------

                self._control()

            except Exception:

                logger.exception(
                    "Controller error"
                )

            finally:

                self.controller_queue.task_done()

        logger.info(
            "Controller thread stopped"
        )