# Smart HVAC: A Climate Control System

Project for the **Internet of Things** course, MSc Computer Engineering, University of Pisa (A.Y. 2025/26).

**Author:** Lorenzo Vezzani
**Repository:** https://github.com/lorenzo-vezzani/smart-hvac-iot/

## Description

An IoT system that improves the energy efficiency of heating, ventilation and air conditioning (HVAC) in smart buildings. It combines environmental sensing, ML-based occupancy detection, a fall-back adaptive mechanism and cloud-based control.

**Use case:** a building with multiple rooms, each equipped with a sensor node and an HVAC actuator. Environmental data is used to detect occupancy and decide whether to run the HVAC, while the cloud coordinates the available energy budget among the rooms.

## Architecture

```
Sensor node ──MQTT──► Broker ◄──MQTT── Actuator node (on-device ML)
                        ▲
                        │ MQTT
                        ▼
        Cloud application (Dispatcher, Controller, MQTT client, DB, CLI)

IoT network (RPL) ── Border Router ── tunslip ── host machine
```

### Sensor node
Publishes temperature, humidity, light and CO2 every **60 s** (values are randomly generated, taking into account the room's HVAC state). Payloads are encoded in SenML.
States: `OFF → REGISTER → CONNECT → SUBSCRIBE → GENERATE ⇄ PUBLISH`. Each state has its own LED configuration; a second button press shuts the node down.

### Actuator node
Runs an occupancy-detection model on the received telemetry. The HVAC turns **on** when occupancy is predicted *and* temperature exceeds a threshold, and **off** when either condition stops holding. It reports every state change (including the energy consumed, in Wh, at shutdown) and listens for power-level and threshold commands from the cloud.
States: `OFF → REGISTER → CONNECT → SUBSCRIBE → SENSOR ⇄ INFERENCE`, plus `WATCHDOG` for the adaptive mechanism.

### Border router
The standard RPL Border Router from the official [Contiki-NG repository](https://github.com/contiki-ng/contiki-ng/tree/develop/examples/rpl-border-router).

### Cloud application
| Component | Role |
|---|---|
| **Dispatcher** | Routes incoming MQTT messages to the Controller, the database, or both |
| **Controller** | Monitors total requested power against the building limit. If enough power is available, all active HVACs run at `HIGH`; otherwise a **greedy, priority-based** allocation assigns `LOW`, `MID` or `HIGH` per room |
| **MQTT client** | Manages the broker connection, incoming messages and published commands |
| **Database** | Relational DB with tables `room`, `measurement` and `activation` |
| **CLI** | Runtime monitoring and control (see below) |

**CLI commands**

| Command | Description |
|---|---|
| `q`, `quit` | Terminate the application |
| `h`, `help` | List available commands |
| `s`, `status` | Show the status of all devices |
| `d [-s ROOM] [-p BEGIN END]` | Show stored measurements, optionally filtered by room and time interval |
| `ct <ROOM> <THRESHOLD>` | Change a room's HVAC temperature threshold |

## Communication

MQTT is used for its lightweight publish/subscribe model, which decouples sensors, actuators and cloud. Payloads are **JSON** (SenML for telemetry), chosen over XML for its lower verbosity.

| Topic | Publisher → Subscriber | Payload |
|---|---|---|
| `building/<room>/telemetry` | Sensor → Actuator, Cloud | `timestamp`, `temperature`, `humidity`, `light`, `co2`, `sequence` |
| `building/<room>/hvac/start` | Actuator → Cloud | HVAC activated |
| `building/<room>/hvac/end` | Actuator → Cloud | `energy_consumed_wh`, `reason` |
| `building/<room>/hvac/control` | Cloud → Actuator | `power`: `LOW` / `MID` / `HIGH` |
| `building/<room>/hvac/threshold` | Cloud → Actuator | `threshold`: temperature above which HVAC may start |

## Machine learning

Occupancy detection is a binary classification task on Temperature, Humidity, Light and CO2.

- **Model:** small DNN, two hidden ReLU layers (16 and 8 neurons) and a sigmoid output.
- **Dataset:** [Occupancy Detection (Kaggle)](https://www.kaggle.com/datasets/pooriamst/occupancy-detection)
- **Preprocessing:** chronological split (70% development / 30% test; development split again 85% train / 15% validation), `StandardScaler` fitted on the training set only, early stopping on validation loss.
- **Evaluation:** accuracy, precision, recall, F1, ROC-AUC and confusion matrix on the unseen test set. Results are in the [`ml`](https://github.com/lorenzo-vezzani/smart-hvac-iot/tree/main/ml) folder.
- **Deployment:** converted to C with [emlearn](https://github.com/emlearn/emlearn) as a header file integrated into the actuator firmware.

## Adaptive mechanism

The actuator tracks the freshness of the sensor's measurements (sampling every 60 s):

| State | Condition |
|---|---|
| **Fresh** | Within the expected sampling interval |
| **Stale** | No new data for 1.5 intervals (90 s); last value still usable |
| **Invalid** | No new data for 3 intervals (180 s); too old for control |

Every 60 s the actuator enters the `WATCHDOG` state and compares the current time with the last measurement's timestamp. If the data is invalid, the HVAC is switched off. This is motivated by the thermal inertia of rooms: missing one or two samples does not imply a significant change.

**Evaluation** (varying packet loss, adaptive vs non-adaptive; comfort measured as mean absolute deviation from an ideal 21 °C):
- Energy savings grow with packet loss: about 1% (20% loss), 32.5% (40%), 40.6% (60%) and 64.4% (80%).
- Comfort is only marginally affected: 71% (adaptive) vs 74% (non-adaptive).

## Running the project

The firmware can be run in two ways.

### Option 1: Cooja simulation
Use the **Cooja** network simulator (bundled with Contiki-NG) to emulate the border router, sensor and actuator nodes. **Tunslip** creates a virtual SLIP connection between the simulated border router and the host, so the cloud application can reach the simulated network.

### Option 2: Physical dongles
Flash the firmware onto **Nordic nRF52840 Dongles** (ARM Cortex-M4, IEEE 802.15.4) and run the nodes on real hardware, with the border router connected to the host.

In both cases:
1. Start an MQTT broker reachable by the nodes and the cloud application.
2. Start the border router and the sensor/actuator nodes (press the button on a node to start it).
3. Launch the cloud application and use its CLI to monitor the system.

> Exact build, flash and launch commands are not covered in the project documentation; add them here from the repository.

## Environment

- **Contiki-NG:** open-source OS for constrained IoT devices (IPv6, RPL, CoAP, MQTT)
- **Cooja:** Contiki-NG network simulator
- **Tunslip:** SLIP bridge between the border router and the host
- **Nordic nRF52840 Dongle:** physical test hardware

## Limitations and future work

- **Static configuration:** adding rooms, sensors or actuators requires manual changes; the cloud could provide dynamic configuration.
- **No actuator failure detection:** a failed actuator may stay on unnoticed; heartbeats or periodic status messages comparing requested and reported state would address this.
- Evaluation could be extended to larger deployments and simultaneous sensor and actuator failures.
