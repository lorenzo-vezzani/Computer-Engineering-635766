#!/bin/bash

FRR_SSH_NAME="frr-ssh:10.4.1"
ALP_SSH_NAME="alpine-ssh:3.19.1"
PYT_SSH_NAME="python-ssh:3.12-alpine"

echo "Building FRR-SSH image"
docker build -t $FRR_SSH_NAME ./config/frr
echo -e "Container image built: $FRR_SSH_NAME \n"

echo "Building Alpine-SSH image"
docker build -t $ALP_SSH_NAME ./config/alpine
echo -e "Container image built: $ALP_SSH_NAME \n"

echo "Building Python-SSH-Alpine image"
docker build -t $PYT_SSH_NAME ./config/mngr
echo -e "Container image built: $PYT_SSH_NAME \n"