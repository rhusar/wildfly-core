#!/bin/bash

MANIFEST_GAV="org.jboss.eap.channels:eap-8.0"

usage() {
  echo "Usage: align-dependencies.sh [path/to/manifest.yaml]"
  echo
  echo "If no manifest path is given, manifest will be downloaded from a Maven "
  echo "repository using G:A '$MANIFEST_GAV'. The latest available "
  echo "manifest version is going to be used in this case."
}

if [ "$#" -eq 0 ]; then
  echo "Using latest available version of the $MANIFEST_GAV manifest."
  PARAMS="-DmanifestGAV=$MANIFEST_GAV"
elif [ "$#" -eq 1 ]; then
  if [ "$1" == "-h" ] || [ "$1" == "--help" ]; then
    usage
    exit 0
  fi
  PARAMS="-DmanifestFile=$1"
else
  echo "Error: Illegal number of parameters"
  echo
  usage
  exit 1
fi

mvn org.wildfly:wildfly-channel-maven-plugin:upgrade $PARAMS