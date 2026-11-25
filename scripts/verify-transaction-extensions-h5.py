"""Verify the first gift slice via the running local-expense HTTP entry point."""
import datetime
import http.cookiejar
import json
import sys
import urllib.error
import urllib.request
from zoneinfo import ZoneInfo


def main():
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 18125
    base = f"http://127.0.0.1:{port}/api/v1"
    today = datetime.datetime.now(ZoneInfo("Asia/Shanghai")).date().isoformat()

    def session():
        return urllib.request.build_opener(
            urllib.request.ProxyHandler({}),
            urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()),
        )

    alpha, beta = session(), session()

    def call(client, method, path, data=None, status=200):
        req = urllib.request.Request(
            base + path,
            data=None if data is None else json.dumps(data).encode(),
            headers={"Content-Type": "application/json"},
            method=method,
        )
        try:
            response = client.open(req, timeout=10)
        except urllib.error.HTTPError as error:
            response = error
        with response:
            raw = response.read()
            body = json.loads(raw) if raw else None
            assert response.code == status, (method, path, response.code, body)
            return body.get("data") if body else None

    ledger = call(alpha, "POST", "/local-session", {"account": "alpha"})["ledgerId"]
    call(beta, "POST", "/local-session", {"account": "beta"})
    root = f"/ledgers/{ledger}"
    catalog = call(alpha, "GET", root + "/categories")
    mapped = {item["code"]: item["capabilities"] for item in catalog}
    assert mapped["GIFT_SENT"] == mapped["GIFT_RECEIVED"] == [
        {"type": "GIFT", "required": True}
    ]
    assert mapped["DINING"] == []
    path = root + "/transactions"
    monthly = f"{path}?month={today[:7]}"
    before = call(alpha, "GET", monthly)
    draft = {
        "direction": "EXPENSE",
        "amountCents": 50000,
        "categoryCode": "GIFT_SENT",
        "bookedOn": today,
        "note": "扩展验收示例",
    }
    gift = {"type": "GIFT", "operation": "UPSERT", "detail": {"reason": "婚礼"}}
    call(alpha, "POST", path, draft, status=400)
    for invalid in (
        {**gift, "type": "UNKNOWN"},
        {**gift, "detail": {"reason": "字" * 61}},
    ):
        call(alpha, "POST", path, {**draft, "extensions": [invalid]}, status=400)
    assert call(alpha, "GET", monthly) == before, "a failed child write left a ledger row"
    row = call(alpha, "POST", path, {**draft, "extensions": [gift]})
    url = f"{path}/{row['id']}"
    detail = call(alpha, "GET", url)
    assert detail["transaction"]["amountCents"] == 50000
    assert len(call(alpha, "GET", monthly)) == len(before) + 1
    assert detail["extensions"] == [
        {
            "type": "GIFT",
            "detail": {
                "bookCode": "DAILY_SENT",
                "bookName": "日常送礼",
                "personName": None,
                "reason": "婚礼",
            },
        }
    ]
    call(beta, "GET", url, status=403)

    version = detail["version"]
    changed = {**gift, "detail": {"personName": "测试亲友", "reason": "改事由"}}
    call(alpha, "PUT", url, {"version": version, "draft": {**draft, "extensions": [changed]}}, 204)
    saved = call(alpha, "GET", url)
    assert saved["version"] == version + 1
    assert saved["extensions"][0]["detail"]["personName"] == "测试亲友"
    call(alpha, "PUT", url, {"version": version, "draft": draft}, 409)

    version = saved["version"]
    edited = {**draft, "note": "旧客户端只改备注"}
    call(alpha, "PUT", url, {"version": version, "draft": edited}, 204)
    saved = call(alpha, "GET", url)
    assert saved["extensions"][0]["detail"]["reason"] == "改事由"
    version = saved["version"]
    call(alpha, "DELETE", f"{url}?version={version}", status=204)
    call(alpha, "GET", url, status=404)
    assert len(call(alpha, "GET", monthly)) == len(before)
    call(alpha, "POST", url + "/restore", {"version": version + 1}, 204)
    saved = call(alpha, "GET", url)
    assert saved["extensions"][0]["detail"]["reason"] == "改事由"

    ordinary = {**edited, "categoryCode": "DINING"}
    version = saved["version"]
    call(alpha, "PUT", url, {"version": version, "draft": ordinary}, 400)
    call(
        alpha,
        "PUT",
        url,
        {
            "version": version,
            "draft": {
                **ordinary,
                "extensions": [{"type": "GIFT", "operation": "REMOVE"}],
            },
        },
        204,
    )
    assert call(alpha, "GET", url)["extensions"] == []

    received = {**draft, "direction": "INCOME", "categoryCode": "GIFT_RECEIVED"}
    income = call(alpha, "POST", path, {**received, "extensions": [{**gift, "detail": {}}]})
    result = call(alpha, "GET", f"{path}/{income['id']}")["extensions"][0]["detail"]
    assert result["bookCode"] == "DAILY_RECEIVED" and result["personName"] is None
    print("PASS: capabilities, single-entry gift write, rollback, version, old client, permissions, restore and explicit removal")


if __name__ == "__main__":
    main()
