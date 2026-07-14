#!/usr/bin/env bash

set -euo pipefail

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repository_root"

if command -v brew >/dev/null 2>&1; then
  export JAVA_HOME="$(brew --prefix openjdk@21)"
  export PATH="$JAVA_HOME/bin:$PATH"
fi

if ! command -v java >/dev/null 2>&1; then
  echo "Java 21 is required. Install it, then set JAVA_HOME before running this script."
  exit 1
fi

if [ -f secrets/service-1.env ]; then
  set -a
  # shellcheck disable=SC1091
  source secrets/service-1.env
  set +a
fi

cleanup() {
  kill "${frontend_pid:-}" "${gateway_pid:-}" "${service_one_pid:-}" "${service_two_pid:-}" "${service_three_pid:-}" 2>/dev/null || true
}

trap cleanup EXIT INT TERM

./gradlew bootJar

java -jar service-3/build/libs/service-3-0.0.1-SNAPSHOT.jar &
service_three_pid=$!
java -jar service-2/build/libs/service-2-0.0.1-SNAPSHOT.jar &
service_two_pid=$!
java -jar service-1/build/libs/service-1-0.0.1-SNAPSHOT.jar &
service_one_pid=$!
java -jar gateway/build/libs/gateway-0.0.1-SNAPSHOT.jar &
gateway_pid=$!

cd frontend
npm run dev -- --host 0.0.0.0 &
frontend_pid=$!

echo "React SPA: http://localhost:5173"
echo "Gateway:   http://localhost:8080"
echo "Press Ctrl+C in this terminal to stop all local applications."

wait "$frontend_pid"
