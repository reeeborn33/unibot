#!/bin/bash
set -e

# ===== Fill in the following config before running =====
export WORLDONE_DB_URL="jdbc:postgresql://localhost:5432/worldone"
export WORLDONE_DB_USER="postgres"
export WORLDONE_DB_PASS=""
export LLM_API_KEY="${LLM_API_KEY:-}"
export LLM_BASE_URL="${LLM_BASE_URL:-}"
export LLM_MODEL="${LLM_MODEL:-}"

if [ -z "$WORLDONE_DB_PASS" ]; then
  echo "ERROR: WORLDONE_DB_PASS is required. Fill it in this script or set env var."
  exit 1
fi
if [ -z "$LLM_API_KEY" ]; then
  echo "ERROR: LLM_API_KEY is required. Fill it in this script or set env var."
  exit 1
fi
if [ -z "$LLM_BASE_URL" ]; then
  echo "ERROR: LLM_BASE_URL is required (e.g. https://api.deepseek.com/v1). Fill it in this script or set env var."
  exit 1
fi
if [ -z "$LLM_MODEL" ]; then
  echo "ERROR: LLM_MODEL is required (e.g. deepseek-chat). Fill it in this script or set env var."
  exit 1
fi

echo "Starting World-One..."
nohup java -jar world-one-1.0-SNAPSHOT.jar --server.port=8090 > world-one.log 2>&1 &
WORLD_PID=$!

echo "Waiting 5s..."
sleep 5

if ! ps -p $WORLD_PID > /dev/null 2>&1; then
  echo "ERROR: World-One failed to start. See world-one.log"
  cat world-one.log
  exit 1
fi

echo "Starting Memory-One..."
nohup java -jar memory-one-1.0-SNAPSHOT.jar --server.port=8091 > memory-one.log 2>&1 &
MEMORY_PID=$!

echo "Waiting 10s for services to start..."
sleep 10

if ! ps -p $MEMORY_PID > /dev/null 2>&1; then
  echo "ERROR: Memory-One failed to start. See memory-one.log"
  cat memory-one.log
  exit 1
fi

echo "Registering Memory-One..."
RESP=$(curl -s -X POST "http://localhost:8090/api/registry/install" \
     -H "Content-Type: application/json" \
     -d '{"app_id":"memory-one", "base_url":"http://localhost:8091"}')

if echo "$RESP" | grep -q "error"; then
  echo "ERROR: Memory-One registration failed"
  echo "$RESP"
  exit 1
fi

echo ""
echo "All services started! Visit http://localhost:8090"
echo "World-One PID: $WORLD_PID, log: world-one.log"
echo "Memory-One PID: $MEMORY_PID, log: memory-one.log"