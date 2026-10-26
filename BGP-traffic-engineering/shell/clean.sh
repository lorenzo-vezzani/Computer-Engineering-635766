#bin/bash

sudo containerlab destroy --cleanup -t acn.clab.yml

# Print container possibly left out
docker ps -a | grep acn-prj | awk '{print $1}' | xargs -r docker rm -f
docker volume ls | grep acn-prj | awk '{print $2}' | xargs -r docker volume rm
ip netns | grep clab
ip netns | grep acn-prj