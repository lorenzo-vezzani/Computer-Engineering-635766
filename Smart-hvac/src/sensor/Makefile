CONTIKI := /home/lorenzo/contiki-ng

CONTIKI_PROJECT = sensor

all: $(CONTIKI_PROJECT)

MODULES += $(CONTIKI_NG_APP_LAYER_DIR)/mqtt

MODULES += os/services/shell

TARGET_LIBFILES += -lm

include $(CONTIKI)/Makefile.dir-variables

-include $(CONTIKI)/Makefile.identify-target

PROJECT_SOURCEFILES += generator.c

LDFLAGS += -u _scanf_float -u _printf_float

MODULES_REL += arch/platform/$(TARGET)

include $(CONTIKI)/Makefile.include