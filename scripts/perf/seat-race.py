#!/usr/bin/env python3
"""N users try to hold the same seat at the same moment. Exactly one should win."""
import json, os, sys, threading, time, urllib.error, urllib.request, uuid
from datetime import date, timedelta

BASE = os.environ.get("BASE", "http://127.0.0.1:7580")
N = int(sys.argv[1]) if len(sys.argv) > 1 else 50

def call(method, path, body=None, headers=None):
    req = urllib.request.Request(BASE + path, method=method, data=json.dumps(body).encode() if body else None,
                                 headers={"Content-Type": "application/json", **(headers or {})})
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            return r.status, json.loads(r.read() or b"null")
    except urllib.error.HTTPError as e:
        return e.code, json.loads(e.read() or b"null")

tokens = [call("POST", "/api/auth/register", {"fullName": f"Racer {i}", "email": f"race-{uuid.uuid4()}@example.com",
                                              "password": "race-pass-123", "phone": "9876543210"})[1]["accessToken"]
          for i in range(N)]
day = (date.today() + timedelta(days=6)).isoformat()
schedule = call("GET", f"/api/flights/search?from=MAA&to=HYD&date={day}")[1]["content"][0]["scheduleId"]
seat = next(s for s in call("GET", f"/api/schedules/{schedule}/seats")[1]
            if s["status"] == "AVAILABLE" and s["seatClass"] == "ECONOMY")

results, start = [], threading.Barrier(N)
def racer(tok):
    start.wait()
    status, _ = call("POST", "/api/bookings", {"scheduleId": schedule, "passengers": [
        {"fullName": "Racer", "age": 28, "gender": "MALE", "seatId": seat["id"]}]},
        {"Authorization": f"Bearer {tok}", "Idempotency-Key": str(uuid.uuid4())})
    results.append(status)

threads = [threading.Thread(target=racer, args=(t,)) for t in tokens]
t0 = time.perf_counter()
for t in threads: t.start()
for t in threads: t.join()
elapsed = time.perf_counter() - t0
print(f"{N} concurrent holds on seat {seat['seatNumber']} (schedule {schedule}) in {elapsed*1000:.0f} ms:",
      {s: results.count(s) for s in sorted(set(results))})
