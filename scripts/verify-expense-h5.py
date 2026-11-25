#!/usr/bin/env python3
"""Run only against the isolated local backend or H5 dev proxy."""
import datetime
import json
import sys
import urllib.error
import urllib.request
import uuid

PORTS = {"18125", "18126", "5173", "4173"}
port = sys.argv[1] if len(sys.argv) > 1 else "18125"
if port not in PORTS:
    raise SystemExit("The check only accepts known local test ports.")
endpoint = f"http://127.0.0.1:{port}/api/v1/expenses"
date = datetime.date.today().isoformat()
note = f"小簿验收-{uuid.uuid4().hex[:8]}"
draft = {"amountCents": 1230, "bookedOn": date, "categoryCode": "DINING", "note": note}


def call(suffix="", payload=None):
    req = urllib.request.Request(
        endpoint + suffix,
        data=json.dumps(payload, ensure_ascii=False).encode() if payload is not None else None,
        headers={"Content-Type": "application/json"},
    )
    try:
        with urllib.request.urlopen(req, timeout=12) as response:
            return response.status, json.load(response)
    except urllib.error.HTTPError as error:
        return error.code, json.load(error)


for invalid in (0, 1230.5):
    status, body = call(payload={**draft, "amountCents": invalid})
    assert status == 400 and body["success"] is False, (status, body)

status, saved = call(payload=draft)
assert status == 200 and saved["success"], (status, saved)
record = saved["data"]
assert all(record[key] == value for key, value in draft.items()), record
status, result = call(f"?month={date[:7]}")
assert status == 200 and result["success"], (status, result)
assert [row for row in result["data"] if row["note"] == note] == [record]
print(f"Expense persisted: {record['id']} | {date} | 1230 cents | {note}")
