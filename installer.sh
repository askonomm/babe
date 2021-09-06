#!/usr/bin/env bash

if [ "$(uname)" == "Darwin" ]; then
  curl -O -L https://github.com/askonomm/babe/releases/latest/download/babe-macos && \
  mv babe-macos babe && \
  chmod +x babe
else
  curl -O -L https://github.com/askonomm/babe/releases/latest/download/babe-linux && \
  mv babe-linux babe && \
  chmod +x babe
fi

while [[ "$#" -gt 0 ]]; do
  case $1 in
    -g|--global) global="true"; shift ;;
  esac
  shift
done

if [ "$global" == "true" ]; then
  sudo mv babe /usr/local/bin/babe
fi