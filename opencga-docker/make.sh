#!/bin/bash

NO_CACHE="false"
MONGO_VERSION="3.4"

if [ "$1" == "--help" ] || [ "$1" == "-h" ]
    then 
	echo " 
	       This utility script accepts two arguments:

               First  argument Boolean [true/false]    :     no-cache to build images
	       Second argument Double [X.X]            :     mongodb version 

               Example : make.sh true 3.4
	       Default : make.sh (make.sh false 3.4)

               After successful run, user can access the Swagger and FitNesse :

	       172.18.0.5:8080/opencga/webservices          [Swagger]
	       172.18.0.9:7070                              [FitNesse]

	       "
	exit
fi

if  [  "$1" == "true" ]
    then 
	NO_CACHE="$1"
elif [ ! -z "$2" ]
   then
	MONGO_VERSION="$2"
fi

docker network create  --subnet 172.18.0.0/24 opencga-network

sudo docker build -t opencb/mongodb            -f mongo/$MONGO_VERSION/Dockerfile.mongodb                .  --no-cache=$NO_CACHE 

sudo docker stop mongodb
sudo docker rm mongodb

sudo docker run --name mongodb --net opencga-network --ip 172.18.0.2 -d opencb/mongodb

sudo docker build -t opencb/java8              -f Dockerfile.java8     			                 .  --no-cache=$NO_CACHE
sudo docker build -t opencb/opencga_build      -f Dockerfile.opencga_build       		         .  --no-cache=$NO_CACHE
sudo docker build -t opencb/opencga_tomcat     -f Dockerfile.opencga_tomcat                              .  --no-cache=$NO_CACHE
sudo docker build -t opencb/opencga_deploy     -f Dockerfile.opencga_deploy                              .  --no-cache=$NO_CACHE
sudo docker build -t opencb/opencga_install    -f Dockerfile.opencga_install --network opencga-network   .  --no-cache=$NO_CACHE
sudo docker build -t opencb/opencga_fitnesse   -f Dockerfile.opencga_fitNesse                            .  --no-cache=$NO_CACHE

sudo docker stop opencga
sudo docker stop fitnesse

sudo docker rm   opencga
sudo docker rm   fitnesse 

sudo docker run --name opencga --net opencga-network --ip 172.18.0.5 -d opencb/opencga_install
sudo docker run --name fitnesse --net opencga-network --ip 172.18.0.9 -d opencb/opencga_fitnesse 
#sudo docker run --name fitnesse -w /appl/opencga/build/test --net opencga-network --ip 172.18.0.9 -d opencb/opencga_fitnesse bin/opencga-fitnesse.sh
 
