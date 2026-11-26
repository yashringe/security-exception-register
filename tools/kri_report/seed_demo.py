"""Fills a fresh register with realistic demo exceptions through the public API.

Every request goes through the same rules as a real user would hit (four-eyes, expiry limits),
so the script also works as a small smoke test. Standard library only.
"""

from __future__ import annotations

import argparse
import base64
import json
import sys
import urllib.error
import urllib.request
from datetime import date, timedelta
from typing import Any

PASSWORD = "demo"

# title, asset, policy, likelihood, impact, controls [(text, effectiveness)], days, outcome
DEMO = [
    ("Legacy settlement server cannot take OS patch", "SRV-LEG-042", "POL-PATCH-01 3.2", 3, 4,
     [("Host moved to an isolated VLAN, only two jump hosts allowed", "EFFECTIVE")], 60, "approve"),
    ("Admin console still without MFA", "APP-ADM-007", "POL-IAM-04 2.1", 4, 5,
     [("IP allow-list on the load balancer", "PARTIAL")], 25, "approve"),
    ("Vendor VPN account shared by three engineers", "ACC-VND-113", "POL-IAM-02 4.3", 5, 5,
     [("Session recording on the bastion host", "UNTESTED")], 14, "approve-twice"),
    ("TLS 1.0 on internal reporting endpoint", "APP-RPT-019", "POL-CRYPTO-01 1.4", 2, 3,
     [("Endpoint reachable from the reporting subnet only", "EFFECTIVE")], 10, "approve"),
    ("Logs of batch cluster not sent to SIEM", "CLU-BAT-002", "POL-LOG-03 2.2", 3, 3,
     [("Weekly manual log review by operations", "PARTIAL")], 120, "approve"),
    ("Unsupported Java 8 runtime on pricing tool", "APP-PRC-031", "POL-SW-05 3.1", 3, 4,
     [("Tool only runs on demand, no network listener", "PARTIAL")], 80, "submit"),
    ("Developer laptops without disk encryption", "END-DEV-ALL", "POL-END-01 1.2", 4, 4,
     [("Laptops never leave the office", "UNTESTED")], 60, "reject"),
    ("Test database holds masked production copy", "DB-TST-008", "POL-DATA-02 5.1", 2, 4,
     [("Masking job verified by data protection team", "EFFECTIVE")], 90, "close"),
]


class Api:
    def __init__(self, base_url: str) -> None:
        self.base = base_url.rstrip("/")

    def call(self, user: str, method: str, path: str, body: dict[str, Any] | None = None) -> Any:
        token = base64.b64encode(f"{user}:{PASSWORD}".encode()).decode()
        data = None if body is None else json.dumps(body).encode()
        req = urllib.request.Request(self.base + path, data=data, method=method, headers={
            "Authorization": f"Basic {token}", "Content-Type": "application/json"})
        try:
            with urllib.request.urlopen(req, timeout=15) as resp:  # noqa: S310
                text = resp.read().decode()
                return json.loads(text) if text else None
        except urllib.error.HTTPError as err:
            raise SystemExit(f"{method} {path} as {user} failed: {err.code} {err.read().decode()}") from err


def seed(api: Api) -> int:
    today = date.today()
    for title, asset, policy, likelihood, impact, controls, days, outcome in DEMO:
        e = api.call("alice", "POST", "/api/exceptions", {
            "title": title, "assetId": asset, "policyReference": policy,
            "justification": "Fix planned; see the linked change ticket.", "owner": "olivia",
            "likelihood": likelihood, "impact": impact,
            "controls": [{"description": d, "effectiveness": eff} for d, eff in controls],
            "requestedExpiry": (today + timedelta(days=days)).isoformat()})
        path = f"/api/exceptions/{e['id']}"
        api.call("alice", "POST", path + "/submit")
        if outcome in ("approve", "approve-twice", "close"):
            api.call("bob", "POST", path + "/approve", {"comment": "Accepted with the listed control"})
        if outcome == "approve-twice":
            api.call("carol", "POST", path + "/approve", {"comment": "Second approval for CRITICAL"})
        if outcome == "reject":
            api.call("carol", "POST", path + "/reject", {"comment": "Encryption can be rolled out now"})
        if outcome == "close":
            api.call("olivia", "POST", path + "/close", {"comment": "Remediated, masking verified"})
        print(f"{outcome:14} {title}")
    return len(DEMO)


def main(argv: list[str] | None = None) -> int:
    p = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    p.add_argument("--url", default="http://localhost:8080")
    args = p.parse_args(argv)
    n = seed(Api(args.url))
    print(f"seeded {n} exceptions")
    return 0


if __name__ == "__main__":
    sys.exit(main())
