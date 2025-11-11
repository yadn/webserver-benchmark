#!/bin/bash

PORT=8080
DURATION=60
CONNECTIONS=2000
OUT_DIR="out/production/webserver-benchmark"
JAVA_OPTS="${JAVA_OPTS:--Xms256m -Xmx256m -XX:+UseG1GC}"
RESULTS_DIR="results"

echo "Web Server Benchmark Comparison"
echo "-----------------"
echo "Duration: ${DURATION}s"
echo "Connections: ${CONNECTIONS}"
echo ""

# Initialize comparison CSV
mkdir -p "$RESULTS_DIR"
COMPARISON_CSV="$RESULTS_DIR/comparison.csv"
echo "name,connections,duration,requests_per_sec,lat_avg,lat_p50,lat_p75,lat_p90,lat_p99" > "$COMPARISON_CSV"

# Check if wrk is installed
if ! command -v wrk &> /dev/null; then
    echo "wrk not found. Install with: brew install wrk"
    exit 1
fi

# Readiness probe: wait until the server returns HTTP 200 or timeout
wait_ready() {
  local deadline=$((SECONDS + 15))
  while (( SECONDS < deadline )); do
    local code
    code=$(curl -s -o /dev/null -w "%{http_code}" "http://localhost:${PORT}/" || true)
    if [[ "$code" == "200" ]]; then
      return 0
    fi
    sleep 0.2
  done
  return 1
}

# Function to run benchmark
run_benchmark() {
    local NAME=$1
    local COMMAND=$2

    echo ""
    echo "Testing: $NAME"
    echo "-----------------------"

    # Start server
    $COMMAND &
    SERVER_PID=$!
    if wait_ready; then
      echo "[ready] $NAME is responding on port ${PORT} (pid=$SERVER_PID)"
    else
      echo "[error] $NAME did not become ready on port ${PORT} in time" >&2
      kill $SERVER_PID 2>/dev/null || true
      sleep 1
      return 1
    fi

    # Start lightweight CPU/memory sampler (1s interval)
    SAMPLE_LOG="$RESULTS_DIR/metrics_${NAME// /_}.log"
    echo "[sampler] logging CPU% and RSS to $SAMPLE_LOG"
    (
      while kill -0 "$SERVER_PID" 2>/dev/null; do
        printf '%s ' "$(date '+%H:%M:%S')"
        ps -o pid,pcpu,pmem,rss,comm -p "$SERVER_PID" | tail -n1
        sleep 1
      done
    ) >"$SAMPLE_LOG" 2>/dev/null &
    SAMPLER_PID=$!

    # Run benchmark (persist output)
    OUT_TXT="$RESULTS_DIR/wrk_${NAME// /_}.txt"
    wrk -t4 -c${CONNECTIONS} -d${DURATION}s --latency http://localhost:${PORT} | tee "$OUT_TXT"

    # Stop server
    kill $SERVER_PID 2>/dev/null
    sleep 2

    # Stop sampler
    if [[ -n "${SAMPLER_PID:-}" ]]; then
      kill "$SAMPLER_PID" 2>/dev/null || true
      wait "$SAMPLER_PID" 2>/dev/null || true
      unset SAMPLER_PID
    fi

    # Parse wrk output and append to comparison.csv
    local rps lat_avg p50 p75 p90 p99
    rps=$(grep -E "^Requests/sec" "$OUT_TXT" | awk '{print $2}')
    # Robustly capture the avg latency token from the 'Latency' line
    lat_avg=$(grep -m1 -E '^[[:space:]]*Latency' "$OUT_TXT" | awk '{print $2}')
    p50=$(grep -E "^\s*50%" "$OUT_TXT" | awk '{print $2}')
    p75=$(grep -E "^\s*75%" "$OUT_TXT" | awk '{print $2}')
    p90=$(grep -E "^\s*90%" "$OUT_TXT" | awk '{print $2}')
    p99=$(grep -E "^\s*99%" "$OUT_TXT" | awk '{print $2}')
    echo "${NAME//,/ },$CONNECTIONS,${DURATION},$rps,$lat_avg,$p50,$p75,$p90,$p99" >> "$COMPARISON_CSV"
    echo "Saved: $OUT_TXT, $SAMPLE_LOG (aggregated into $COMPARISON_CSV)"
}

# Compile all files
echo "Compiling servers..."
javac -d "$OUT_DIR" \
  src/SingleThreadServer.java \
  src/ThreadPoolServer.java \
  src/NIOServer.java \
  src/VirtualThreadPoolServer.java \
  src/HttpResponse.java
echo "Compilation complete!"

# Run benchmarks (normalize JVM with JAVA_OPTS)
run_benchmark "Single Thread" "java $JAVA_OPTS -cp $OUT_DIR SingleThreadServer"
run_benchmark "Thread Pool (10 threads)" "java $JAVA_OPTS -cp $OUT_DIR ThreadPoolServer 10"
run_benchmark "Thread Pool (50 threads)" "java $JAVA_OPTS -cp $OUT_DIR ThreadPoolServer 50"
run_benchmark "Virtual Thread Pool (per-task)" "java $JAVA_OPTS -cp $OUT_DIR VirtualThreadPoolServer"
run_benchmark "NIO Selector" "java $JAVA_OPTS -cp $OUT_DIR NIOServer"

echo ""
echo "Benchmark Complete!"
echo "--------------------"
