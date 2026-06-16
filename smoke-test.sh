#!/bin/bash
# Bash Smoke Test Script for README Editor
# -----------------------------------------------------------------------------

URL="http://localhost:8080/api/health"
MAX_ATTEMPTS=5
DELAY_SEC=3

echo "================================================="
echo "🔥 Running Smoke Tests for README Editor"
echo "================================================="
echo "Target URL: $URL"

for ((i=1; i<=MAX_ATTEMPTS; i++)); do
    echo "Attempt $i of $MAX_ATTEMPTS: Checking health check endpoint..."
    RESPONSE=$(curl -s -m 5 "$URL")
    
    if [ $? -eq 0 ] && [ ! -z "$RESPONSE" ]; then
        STATUS=$(echo "$RESPONSE" | grep -o '"status":"[^"]*' | grep -o '[^"]*$')
        REDIS=$(echo "$RESPONSE" | grep -o '"redis":"[^"]*' | grep -o '[^"]*$')
        
        if [ "$STATUS" == "UP" ]; then
            echo "✅ Health Check passed!"
            echo "Server status: $STATUS"
            echo "Redis connection: $REDIS"
            
            echo "Checking static assets..."
            INDEX_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -m 5 "http://localhost:8080/index.html")
            
            if [ "$INDEX_STATUS" == "200" ]; then
                echo "✅ Static assets verified successfully!"
                echo "================================================="
                echo "🚀 SMOKE TESTS PASSED SUCCESSFULLY!"
                echo "================================================="
                exit 0
            else
                echo "❌ Static assets returned HTTP status: $INDEX_STATUS"
            fi
        else
            echo "❌ Health Check returned unexpected status: $STATUS"
        fi
    else
        echo "⚠️ Attempt $i failed to reach server."
    fi
    
    if [ $i -lt $MAX_ATTEMPTS ]; then
        echo "Waiting $DELAY_SEC seconds before retrying..."
        sleep $DELAY_SEC
    fi
done

echo "================================================="
echo "❌ SMOKE TESTS FAILED!"
echo "================================================="
exit 1
