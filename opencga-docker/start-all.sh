#!/bin/bash

docker network create  --subnet 172.18.0.0/24 opencga-network

sudo docker run --net opencga-network --ip 172.18.0.2 -d opencb/mongodb
sudo docker run --net opencga-network --ip 172.18.0.5 -d opencb/opencga_install
sudo docker run --net opencga-network --ip 172.18.0.10 -d opencb/opencga_fitnesse 
