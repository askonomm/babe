#!/usr/bin/env bash

if [ "$(uname)" == "Darwin" ]; then
  curl -O -L https://github.com/askonomm/babe/releases/latest/download/babe-macos && \
  mv babe-macos babe && \
  chmod +x babe
  sudo mv babe /usr/local/bin/babe
else
  curl -O -L https://github.com/askonomm/babe/releases/latest/download/babe-linux && \
  mv babe-linux babe && \
  chmod +x babe
  sudo mv babe /usr/local/bin/babe
fi