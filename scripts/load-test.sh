#!/bin/bash

# Load Test Script for Voice Campaign Service
# Usage: ./load-test.sh [num_campaigns] [calls_per_campaign]

API_URL="${API_URL:-http://localhost:3000/api/v1}"
NUM_CAMPAIGNS="${1:-5}"
CALLS_PER_CAMPAIGN="${2:-100}"

echo "=============================================="
echo "Voice Campaign Load Test"
echo "=============================================="
echo "API URL: $API_URL"
echo "Campaigns to create: $NUM_CAMPAIGNS"
echo "Calls per campaign: $CALLS_PER_CAMPAIGN"
echo "=============================================="

# Function to generate phone numbers
generate_phones() {
    local count=$1
    local phones=""
    for i in $(seq 1 $count); do
        phones="$phones\"+1$(printf '%010d' $RANDOM$RANDOM)\","
    done
    echo "[${phones%,}]"
}

# Create campaigns
echo ""
echo "Creating $NUM_CAMPAIGNS campaigns..."
CAMPAIGN_IDS=()

for i in $(seq 1 $NUM_CAMPAIGNS); do
    echo -n "  Creating campaign $i/$NUM_CAMPAIGNS... "
    
    # Generate phone numbers for this campaign
    PHONES=$(generate_phones $CALLS_PER_CAMPAIGN)
    
    RESPONSE=$(curl -s -X POST "$API_URL/campaigns" \
        -H "Content-Type: application/json" \
        -d "{
            \"name\": \"Load Test Campaign $i\",
            \"description\": \"Auto-generated campaign for load testing\",
            \"phoneNumbers\": $PHONES,
            \"concurrencyLimit\": 20,
            \"priority\": $((($i % 10) + 1)),
            \"retryConfig\": {
                \"maxRetries\": 3,
                \"syncInitialBackoffMs\": 1000,
                \"syncBackoffMultiplier\": 2.0,
                \"callbackRetryDelayMs\": 5000,
                \"callbackTimeoutMs\": 30000
            }
        }")
    
    CAMPAIGN_ID=$(echo $RESPONSE | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)
    
    if [ -n "$CAMPAIGN_ID" ]; then
        CAMPAIGN_IDS+=("$CAMPAIGN_ID")
        echo "OK (ID: $CAMPAIGN_ID)"
    else
        echo "FAILED"
        echo "Response: $RESPONSE"
    fi
done

echo ""
echo "=============================================="
echo "Starting all campaigns..."
echo "=============================================="

for CAMPAIGN_ID in "${CAMPAIGN_IDS[@]}"; do
    echo -n "  Starting campaign $CAMPAIGN_ID... "
    RESPONSE=$(curl -s -X POST "$API_URL/campaigns/$CAMPAIGN_ID/start")
    STATUS=$(echo $RESPONSE | grep -o '"status":"[^"]*"' | head -1 | cut -d'"' -f4)
    echo "$STATUS"
done

echo ""
echo "=============================================="
echo "Load test initiated!"
echo "=============================================="
echo ""
echo "Open the UI to watch the simulation:"
echo "  http://localhost:3000"
echo ""
echo "Monitor logs:"
echo "  docker logs -f voice-campaign-api"
echo ""
echo "Check campaign status:"
for CAMPAIGN_ID in "${CAMPAIGN_IDS[@]}"; do
    echo "  curl $API_URL/campaigns/$CAMPAIGN_ID"
done
