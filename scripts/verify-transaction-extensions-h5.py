"""Verify the first gift slice via the running local-expense HTTP entry point."""
import datetime
import http.cookiejar
import json
import sys
import urllib.error
import urllib.request
from urllib.parse import quote
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
    beta_ledger = call(beta, "POST", "/local-session", {"account": "beta"})["ledgerId"]
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
    assert call(alpha, "GET", monthly)["totalCount"] == before["totalCount"] + 1
    assert detail["extensions"] == [
        {
            "type": "GIFT",
            "detail": {
                "bookCode": "DAILY_SENT",
                "bookName": "日常送礼",
                "bookId": None,
                "contactId": None,
                "distinction": None,
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
    assert call(alpha, "GET", monthly)["totalCount"] == before["totalCount"]
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
    gifts = f"{root}/gifts"
    book = call(
        alpha, "POST", gifts + "/books",
        {"name": "活动验收", "direction": "INCOME", "defaultReason": "婚礼"},
        201,
    )
    contact_path = gifts + "/contacts"
    first_person = call(alpha, "POST", contact_path, {"name": "测试同名"}, 201)
    call(alpha, "POST", contact_path, {"name": "测试同名"}, 409)
    second_person = call(
        alpha, "POST", contact_path, {"name": "测试同名", "distinction": "同事"}, 201
    )
    assert first_person["id"] != second_person["id"]
    targeted = {
        **received,
        "extensions": [{
            **gift, "detail": {
                "bookId": book["id"], "contactId": second_person["id"], "reason": "婚礼"
            },
        }],
    }
    beta_path = f"/ledgers/{beta_ledger}/transactions"
    call(beta, "POST", beta_path, targeted, status=400)
    failed_contact = {
        **received, "extensions": [{**gift, "detail": {
            "personName": "不应遗留的亲友", "createContact": True, "reason": "字" * 61,
        }}],
    }
    call(alpha, "POST", path, failed_contact, status=400)
    assert all(
        item["name"] != "不应遗留的亲友"
        for item in call(alpha, "GET", contact_path)
    )
    recorded = call(alpha, "POST", path, targeted)
    book_filter = f"{gifts}/activity?bookId={book['id']}"
    person_filter = f"{gifts}/activity?contactId={second_person['id']}"
    assert [row["transactionId"] for row in call(alpha, "GET", book_filter)["entries"]] == [recorded["id"]]
    assert [row["transactionId"] for row in call(alpha, "GET", person_filter)["entries"]] == [recorded["id"]]
    assert call(alpha, "GET", f"{gifts}/activity?contactId={first_person['id']}")["entries"] == []
    call(beta, "GET", book_filter, status=403)
    locked = f"{gifts}/books/{book['id']}/archive"
    call(alpha, "PUT", locked, {"archived": True}, 204)
    call(alpha, "POST", path, targeted, status=400)
    linked = f"{path}/{recorded['id']}"
    assert not call(alpha, "GET", linked)["canModify"]
    call(alpha, "DELETE", linked + "?version=0", status=409)
    call(alpha, "PUT", locked, {"archived": False}, 204)
    assert call(alpha, "GET", linked)["canModify"]
    call(alpha, "DELETE", linked + "?version=0", status=204)
    assert call(alpha, "GET", book_filter)["entries"] == []
    call(alpha, "POST", linked + "/restore", {"version": 1}, 204)
    assert [row["transactionId"] for row in call(alpha, "GET", book_filter)["entries"]] == [recorded["id"]]
    # Over 100 real postings: first page totals must not depend on visible row count,
    # and a literal keyword containing percent/underscore must find the oldest row.
    large = call(alpha, "POST", gifts + "/books",
        {"name": "分页验收礼单", "direction": "INCOME"}, 201)
    large_filter = f"{gifts}/activity?bookId={large['id']}"
    def for_large(amount, day, note):
        return {
            **received, "amountCents": amount, "bookedOn": day, "note": note,
            "extensions": [{**gift, "detail": {"bookId": large["id"]}}],
        }
    oldest_day = (datetime.date.fromisoformat(today) - datetime.timedelta(days=10)).isoformat()
    oldest = call(alpha, "POST", path, for_large(777, oldest_day, "独特旧记录 %_小簿"))
    newer = {
        call(alpha, "POST", path, for_large(100, today, "最近流水"))["id"]
        for _ in range(102)
    }
    all_ids = set()
    for number in range(6):
        result = call(alpha, "GET", large_filter + f"&page={number}&pageSize=20")
        assert result["incomeCents"] == 10977 and result["expenseCents"] == 0
        assert result["totalCount"] == 103
        ids = {row["transactionId"] for row in result["entries"]}
        assert not all_ids.intersection(ids)
        all_ids.update(ids)
        assert len(result["entries"]) == (3 if number == 5 else 20)
    assert all_ids == newer | {oldest["id"]}
    unique = call(alpha, "GET", large_filter + "&keyword=" + quote("%_小簿"))
    assert unique["totalCount"] == 1 and unique["incomeCents"] == 777
    assert unique["entries"][0]["transactionId"] == oldest["id"]
    for bad_query in ("&page=-1", "&pageSize=101"):
        call(alpha, "GET", large_filter + bad_query, status=400)
    print("PASS: capabilities, single-entry gift write, rollback, version, old client, permissions, restore, directory, archive and a full 103-entry search / pagination")


if __name__ == "__main__":
    main()
