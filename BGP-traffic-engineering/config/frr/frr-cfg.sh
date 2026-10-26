#!/bin/bash

# ================ Main script execution

echo "======= FRR initialization ======="

# Start watchfrr
/usr/lib/frr/frrinit.sh start

# Configuration is automatically loaded:
# vtysh -f /etc/frr/frr.conf


# VRF Management (if specified)
if [ -n "${CLAB_MGMT_VRF}" ]; then
    # NOTE: uncomment to see if default route will disappeared from default vrf route
    # ip route

    echo "======= VRF Management ======="
    ip link add "${CLAB_MGMT_VRF}" type vrf table 100
    ip link set "${CLAB_MGMT_VRF}" up
    echo "vrf ${CLAB_MGMT_VRF} created and set up"

    ip link set eth0 master "${CLAB_MGMT_VRF}"
    echo "eth0 assigned to vrf ${CLAB_MGMT_VRF}"

    ip route add default via ${CLAB_MGMT_GW} vrf "${CLAB_MGMT_VRF}"
    echo "default route via vrf ${CLAB_MGMT_VRF} (GW ${CLAB_MGMT_GW})"

    # NOTE: uncomment to see if default route has disappeared from default vrf route
    # ip route
fi

# Activate Internet node Loopback interfaces (if specified)
if [ -n "${INT_LO_IFS}" ]; then
    echo "======= Loopback interfaces (1 to 16) ======="
    for i in $(seq 1 16); do
        ip link add lo$i type dummy
        ip link set lo$i up
    done
fi

echo "======= Start ssh on default vrf ======="
/usr/sbin/sshd -o PidFile=/run/sshd_default.pid

if [ -n "${CLAB_MGMT_VRF}" ]; then
echo "======= Start ssh on mgmt vrf ======="
ip vrf exec "${CLAB_MGMT_VRF}" /usr/sbin/sshd -o PidFile=/run/sshd_mgmt.pid
fi

echo "======= END ======="

