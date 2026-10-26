#!/bin/bash

BR_CORE='br-clab-core'

sudo ip link add $BR_CORE type bridge
sudo ip link set $BR_CORE up
echo "Bridge $BR_CORE is up"