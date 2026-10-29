#!/usr/bin/env bash
# End-to-end walk through the API against a running stack (docker compose up -d).
# search -> routes -> hold -> idempotent retry -> pay -> cancel/refund, then the async netbanking
# callback, a double-booking attempt and the search rate limit. Needs curl, jq, openssl.
# Tokens and secrets are never printed.
set -euo pipefail

BASE=${BASE:-http://127.0.0.1:7580}
MGMT=${MGMT:-http://127.0.0.1:7581}
ENV_FILE=${ENV_FILE:-$(dirname "$0")/../.env}
WEBHOOK_SECRET=${PAYMENT_WEBHOOK_SECRET:-$(grep -E '^PAYMENT_WEBHOOK_SECRET=' "$ENV_FILE" | cut -d= -f2-)}
DATE=$(date -d tomorrow +%F)

step() { printf '\n== %s\n' "$*"; }
uuid() { cat /proc/sys/kernel/random/uuid; }
api() {  # api METHOD PATH [JSON] [extra curl args...]
  local method=$1 path=$2 body=${3:-}; shift 3 || shift $#
  curl -sS -X "$method" "$BASE$path" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
       ${body:+-d "$body"} "$@"
}

step "readiness"
curl -sS "$MGMT/actuator/health/readiness"; echo

step "register a user"
EMAIL="demo+$(date +%s)@example.com"
TOKEN=$(curl -sS -X POST "$BASE/api/auth/register" -H 'Content-Type: application/json' \
  -d "{\"fullName\":\"Demo User\",\"email\":\"$EMAIL\",\"password\":\"demo-pass-123\",\"phone\":\"9876543210\"}" \
  | jq -r .accessToken)
echo "registered $EMAIL (token: ${#TOKEN} chars, not shown)"

step "direct flights DEL -> BOM on $DATE (cheapest first)"
curl -sS "$BASE/api/flights/search?from=DEL&to=BOM&date=$DATE" \
  | jq -c '.content[] | {flight: .flightNumber, dep: .departureTime, arr: .arrivalTime, fare: .farePerPassenger, seats: .availableSeats}'

step "routes DEL -> BLR with up to 1 stop"
ROUTES=$(curl -sS "$BASE/api/flights/routes?from=DEL&to=BLR&date=$DATE&maxStops=1")
echo "$ROUTES" | jq -c '.[] | {stops, via, dep: .departureTime, arr: .arrivalTime, minutes: .totalDurationMinutes, layover: .layoverMinutes, fare: .farePerPassenger, flights: [.legs[].flightNumber]}'

SCHEDULE=$(curl -sS "$BASE/api/flights/search?from=DEL&to=BOM&date=$DATE" | jq -r '.content[0].scheduleId')
SEATS=($(curl -sS "$BASE/api/schedules/$SCHEDULE/seats" \
  | jq -r '[.[] | select(.status=="AVAILABLE" and .seatClass=="ECONOMY")][0:3][] | .id'))

booking_body() {
  printf '{"scheduleId":%s,"passengers":[{"fullName":"%s","age":30,"gender":"MALE","seatId":%s}]}' "$SCHEDULE" "$1" "$2"
}

step "hold seat ${SEATS[0]} on schedule $SCHEDULE"
KEY=$(uuid)
B1=$(api POST /api/bookings "$(booking_body 'Asha Rao' "${SEATS[0]}")" -H "Idempotency-Key: $KEY")
BOOKING=$(echo "$B1" | jq -r .id)
echo "$B1" | jq -c '{id, pnr, status, totalAmount, holdExpiresAt}'

step "retry the same request with the same Idempotency-Key"
api POST /api/bookings "$(booking_body 'Asha Rao' "${SEATS[0]}")" -H "Idempotency-Key: $KEY" -D - -o /tmp/fb-replay.json \
  | grep -i '^idempotent-replayed' || true
jq -c '{id, pnr, status}' /tmp/fb-replay.json

step "a second hold on the same seat"
api POST /api/bookings "$(booking_body 'Other Person' "${SEATS[0]}")" -H "Idempotency-Key: $(uuid)" \
  | jq -c '{status, code, detail}'

step "pay with UPI"
PAYKEY=$(uuid)
api POST "/api/bookings/$BOOKING/payments" '{"method":"UPI","upiId":"asha@okbank"}' -H "Idempotency-Key: $PAYKEY" \
  | jq -c '{paymentId, status, amount, bookingStatus}'

step "retry the payment with the same key (no second charge)"
api POST "/api/bookings/$BOOKING/payments" '{"method":"UPI","upiId":"asha@okbank"}' -H "Idempotency-Key: $PAYKEY" \
  | jq -c '{paymentId, status, bookingStatus}'

step "cancel -> refund"
api POST "/api/bookings/$BOOKING/cancel" | jq -c '{pnr, status}'
echo -n "seat ${SEATS[0]} is now: "
curl -sS "$BASE/api/schedules/$SCHEDULE/seats" | jq -r ".[] | select(.id==${SEATS[0]}) | .status"

step "netbanking: payment stays pending until the gateway calls back"
B2=$(api POST /api/bookings "$(booking_body 'Ravi Iyer' "${SEATS[1]}")" -H "Idempotency-Key: $(uuid)")
BOOKING2=$(echo "$B2" | jq -r .id)
P2=$(api POST "/api/bookings/$BOOKING2/payments" '{"method":"NETBANKING"}' -H "Idempotency-Key: $(uuid)")
echo "$P2" | jq -c '{status, bookingStatus}'
REF=$(echo "$P2" | jq -r .gatewayReference)
CALLBACK="{\"gatewayReference\":\"$REF\",\"status\":\"SUCCESS\"}"
SIG=$(printf '%s' "$CALLBACK" | openssl dgst -sha256 -hmac "$WEBHOOK_SECRET" -hex | awk '{print $NF}')

echo -n "callback with a bad signature: "
curl -sS -o /dev/null -w '%{http_code}\n' -X POST "$BASE/api/payments/callback" \
  -H 'Content-Type: application/json' -H "X-Signature: deadbeef" -d "$CALLBACK"
echo -n "signed callback: "
curl -sS -X POST "$BASE/api/payments/callback" -H 'Content-Type: application/json' -H "X-Signature: $SIG" -d "$CALLBACK"; echo
echo -n "same callback delivered again: "
curl -sS -X POST "$BASE/api/payments/callback" -H 'Content-Type: application/json' -H "X-Signature: $SIG" -d "$CALLBACK"; echo
api GET "/api/bookings/$BOOKING2" | jq -c '{pnr, status}'

step "my bookings (page 0, size 5)"
api GET "/api/bookings?size=5" | jq -c '{totalElements, statuses: [.content[].status]}'

step "search rate limit: 40 quick requests from one client"
for i in $(seq 1 40); do
  curl -s -o /dev/null -w '%{http_code}\n' "$BASE/api/flights/search?from=DEL&to=BOM&date=$DATE"
done | sort | uniq -c

step "a few metrics"
curl -sS "$MGMT/actuator/prometheus" | grep -E '^(booking_transitions_total|payment_callbacks_total|search_rate_limited_total|outbox_pending)' || true
