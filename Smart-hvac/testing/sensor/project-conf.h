#ifndef PROJECT_CONF_H_
#define PROJECT_CONF_H_

/* Enable TCP */
#define UIP_CONF_TCP 1

/* Increase buffer to accommodate MQTT CONNECT packet */
#undef UIP_CONF_BUFFER_SIZE
#define UIP_CONF_BUFFER_SIZE 1024

/* TCP receive window and MSS tuning */
#undef UIP_CONF_RECEIVE_WINDOW
#define UIP_CONF_RECEIVE_WINDOW 800
#define UIP_CONF_TCP_MSS 800

/* Make sure 6LoWPAN fragmentation is enabled */
#define SICSLOWPAN_CONF_FRAG 1

/* Connections */
#undef UIP_CONF_MAX_CONNECTIONS
#define UIP_CONF_MAX_CONNECTIONS 2

#endif /* PROJECT_CONF_H_ */