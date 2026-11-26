"""Smoke-test server pagination, search, and period/day totals against the local ledger."""
import datetime
import http.cookiejar
import json
import sys
import urllib.error
import urllib.request
from urllib.parse import urlencode
from zoneinfo import ZoneInfo


def main():
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 18125
    base = f"http://127.0.0.1:{port}/api/v1"
    today = datetime.datetime.now(ZoneInfo("Asia/Shanghai")).date()
    yesterday = today - datetime.timedelta(days=1)

    def session():
        return urllib.request.build_opener(
            urllib.request.ProxyHandler({}),
            urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()),
        )

    def call(client, method, path, body=None, status=200):
        req = urllib.request.Request(
            base + path,
            data=None if body is None else json.dumps(body).encode(),
            headers={"Content-Type": "application/json"},
            method=method,
        )
        try:
            response = client.open(req, timeout=10)
        except urllib.error.HTTPError as error:
            response = error
        with response:
            raw = response.read()
            payload = json.loads(raw) if raw else None
            assert response.code == status, (method, path, response.code, payload)
            return payload.get("data") if payload else None

    alpha = session()
    beta = session()
    ledger = call(alpha, "POST", "/local-session", {"account": "alpha"})["ledgerId"]
    call(beta, "POST", "/local-session", {"account": "beta"})
    path = f"/ledgers/{ledger}/transactions"
    marker = "流水翻页验收%_"

    def record(direction, amount, day, category):
        return call(alpha, "POST", path, {
            "direction": direction,
            "amountCents": amount,
            "bookedOn": str(day),
            "categoryCode": category,
            "note": marker,
        })["id"]

    original = record("EXPENSE", 1500, yesterday, "DINING")
    refund = call(alpha, "POST", f"{path}/{original}/refunds", {
        "version": 0,
        "amountCents": 600,
        "receivedOn": str(today),
    }, 201)["id"]
    income_ids = [
        record("INCOME", 100, today, "SALARY") for _ in range(47)
    ]
    query = urlencode(
        {"from": str(yesterday), "through": str(today), "keyword": marker},
    )
    page_url = f"{path}?{query}&pageSize=20"
    pages = [
        call(alpha, "GET", f"{page_url}&page={page}") for page in range(4)
    ]
    assert [len(page["entries"]) for page in pages] == [20, 20, 9, 0]
    assert [item["page"] for item in pages] == [0, 1, 2, 3]
    expected_days = [
        {
            "bookedOn": str(today), "incomeCents": 4700,
            "expenseCents": -600, "entryCount": 48,
        },
        {
            "bookedOn": str(yesterday), "incomeCents": 0,
            "expenseCents": 1500, "entryCount": 1,
        },
    ]
    for page in pages:
        assert page["pageSize"] == 20
        assert page["incomeCents"] == 4700 and page["expenseCents"] == 900
        assert page["balanceCents"] == 3800 and page["totalCount"] == 49
        assert page["days"] == expected_days
    rows = [row for page in pages for row in page["entries"]]
    assert [row["id"] for row in rows] == list(reversed(income_ids)) + [refund, original]
    assert rows[-2]["originalTransactionId"] == original
    call(beta, "GET", page_url, status=403)
    call(alpha, "GET", f"{page_url}&page=-1", status=400)
    call(alpha, "GET", f"{path}?{query}&pageSize=101", status=400)
    print("PASS: 49 persisted rows, stable server pages, literal search, full day/period totals, refund and auth")


if __name__ == "__main__":
    main()
