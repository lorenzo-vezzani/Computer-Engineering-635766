CONTIKI := /home/lorenzo/contiki-ng

CONTIKI_PROJECT = actuator

all: $(CONTIKI_PROJECT)

MODULES += $(CONTIKI_NG_APP_LAYER_DIR)/mqtt

MODULES += os/services/shell

EMLEARN_DIR := /home/lorenzo/smart-hvac/src/actuator/venv/lib/python3.12/site-packages/emlearn
CFLAGS += -I$(EMLEARN_DIR)
CFLAGS += -Wno-error=unused-function
CFLAGS += -Wno-error=unused-variable

# Math library
TARGET_LIBFILES += -lm

include $(CONTIKI)/Makefile.dir-variables

-include $(CONTIKI)/Makefile.identify-target

MODULES_REL += arch/platform/$(TARGET)

LDFLAGS += -u _scanf_float -u _printf_float

PROJECT_SOURCEFILES += ml.c

include $(CONTIKI)/Makefile.include