#!/bin/bash

set -ex

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"

"$SCRIPT_DIR"/gradlew -Dquarkus.package.type=uber-jar :server:build
gcloud compute scp \
  --project positizing --zone us-central1-c \
  "$SCRIPT_DIR/"server/build/quarkus-build/gen/server-1.0.0-runner.jar positizing:/tmp/

gcloud compute ssh \
  --project positizing --zone us-central1-c \
  positizing --command '
  sudo -u positizing cp /opt/positizing/server-1.0.0-runner.jar /opt/positizing/server-1.0.0-runner.jar-old
  sudo -u positizing cp /tmp/server-1.0.0-runner.jar /opt/positizing/server-1.0.0-runner.jar
  sudo systemctl restart positizing
  sudo journalctl -eu positizing -n 50 --no-pager
  '
