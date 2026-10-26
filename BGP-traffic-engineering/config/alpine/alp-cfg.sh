#!/bin/bash


# VRF Management (if specified)
if [ -n "${CLAB_MGMT_VRF}" ]; then
    # NOTE: uncomment to see if default route will disappeared from default vrf route
    # ip route

    echo "======= VRF Management ======="
    
    ip link add "${CLAB_MGMT_VRF}" type vrf table 100
    ip link set "${CLAB_MGMT_VRF}" up

    ip link set eth0 master "${CLAB_MGMT_VRF}"

    ip route add default via ${CLAB_MGMT_GW} vrf "${CLAB_MGMT_VRF}"

    # NOTE: uncomment to see if default route has disappeared from default vrf route
    # ip route
fi

echo "======= Start ssh on default vrf ======="
/usr/sbin/sshd -o PidFile=/run/sshd_default.pid

if [ -n "${CLAB_MGMT_VRF}" ]; then
echo "======= Start ssh on mgmt vrf ======="
ip vrf exec "${CLAB_MGMT_VRF}" /usr/sbin/sshd -o PidFile=/run/sshd_mgmt.pid
fi

echo "======= Load startup config ======="
./conf.sh

echo "======= END ======="