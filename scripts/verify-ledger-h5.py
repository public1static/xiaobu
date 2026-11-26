#!/usr/bin/env python3
"""Verify two isolated test identities against the local backend or H5 proxy."""
import datetime
import http.cookiejar
import json
import sys
import urllib.error
import urllib.request
import uuid
from urllib.parse import urlencode

PORTS = {"18125", "18126", "5173", "4173"}
port = sys.argv[1] if len(sys.argv) > 1 else "18125"
if port not in PORTS:
    raise SystemExit("The check only accepts known local test ports.")
endpoint = f"http://127.0.0.1:{port}/api/v1"
date = datetime.date.today().isoformat()
note = f"小簿验收-{uuid.uuid4().hex[:8]}"
draft = {"direction": "EXPENSE", "amountCents": 1230, "bookedOn": date, "categoryCode": "DINING", "note": note}


def client():
    return urllib.request.build_opener(
        urllib.request.ProxyHandler({}),
        urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()),
    )


def call(session, path, payload=None, method=None):
    req = urllib.request.Request(
        endpoint + path,
        data=json.dumps(payload, ensure_ascii=False).encode() if payload is not None else None,
        headers={"Content-Type": "application/json"},
        method=method,
    )
    try:
        with session.open(req, timeout=12) as response:
            if response.status == 204:
                return response.status, None
            return response.status, json.load(response)
    except urllib.error.HTTPError as error:
        return error.code, json.load(error)


def sign_in(session, account):
    status, response = call(session, "/local-session", {"account": account})
    assert status == 200 and response["success"], status
    ledger = response["data"]["ledgerId"]
    assert call(session, "/local-session")[1]["data"]["ledgerId"] == ledger
    return f"/ledgers/{ledger}/transactions"


alpha, beta, guest = client(), client(), client()
alpha_path, beta_path = sign_in(alpha, "alpha"), sign_in(beta, "beta")
assert alpha_path != beta_path
month = "?" + urlencode({"month": date[:7], "keyword": note})
assert call(guest, alpha_path + month)[0] == 401
assert call(guest, alpha_path, draft)[0] == 401
assert call(beta, alpha_path + month)[0] == 403
assert call(beta, alpha_path, draft)[0] == 403

for invalid in (0, 1230.5):
    status, body = call(alpha, alpha_path, {**draft, "amountCents": invalid})
    assert status == 400 and body["success"] is False, status

status, saved = call(alpha, alpha_path, {**draft, "actorId": "user_preview_beta", "ledgerId": "ignored"})
assert status == 200 and saved["success"], status
record = saved["data"]
assert all(record[key] == value for key, value in draft.items())
income_draft = {**draft, "direction": "INCOME", "categoryCode": "SALARY", "amountCents": 500000, "note": note + "-收入"}
assert call(alpha, alpha_path, {**draft, "direction": "INCOME"})[0] == 400
assert call(alpha, alpha_path, {**draft, "direction": 1})[0] == 400
status, income = call(alpha, alpha_path, income_draft)
assert status == 200 and income["data"]["amountCents"] == 500000
assert income["data"]["direction"] == "INCOME"
status, result = call(alpha, alpha_path + month)
assert status == 200 and result["success"]
assert [row for row in result["data"]["entries"] if row["note"] == note] == [record]
assert income["data"] in result["data"]["entries"]
status, result = call(beta, beta_path + month)
assert status == 200 and all(not row["note"].startswith(note) for row in result["data"]["entries"])
assert call(alpha, "/local-session", method="DELETE")[0] == 204
assert call(alpha, alpha_path + month)[0] == 401
assert sign_in(alpha, "alpha") == alpha_path
assert record in call(alpha, alpha_path + month)[1]["data"]["entries"]
print(f"Income and expense persisted and isolated: {record['id']} | {date} | 1230 cents | {note}")
