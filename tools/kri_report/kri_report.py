"""Weekly KRI report for the security exception register.

Reads the register's API (or a saved JSON file) and writes one self-contained HTML page:
traffic-light KRIs, open exceptions by residual rating, and the lists a risk team chases every week
(expiring soon, expired but not fixed, waiting too long for a decision).

Exit codes: 0 = no red KRI, 2 = at least one red KRI (so a cron job or pipeline can alert), 1 = error.
Standard library only, so it runs anywhere Python 3.10+ is installed.
"""

from __future__ import annotations

import argparse
import base64
import html
import json
import sys
import urllib.request
from dataclasses import dataclass
from datetime import date, datetime, timezone
from pathlib import Path
from typing import Any

RATINGS = ["LOW", "MEDIUM", "HIGH", "CRITICAL"]
RATING_COLOURS = {"LOW": "#4c9a6a", "MEDIUM": "#d4a72c", "HIGH": "#d9822b", "CRITICAL": "#c2412d"}
STATUS_COLOURS = {"GREEN": "#2e7d4f", "AMBER": "#b7791f", "RED": "#b42318"}


@dataclass(frozen=True)
class RegisterData:
    kri: dict[str, Any]
    exceptions: list[dict[str, Any]]


# --------------------------------------------------------------------------- loading


def fetch(base_url: str, user: str, password: str, timeout: float = 15) -> RegisterData:
    token = base64.b64encode(f"{user}:{password}".encode()).decode()

    def get(path: str) -> Any:
        req = urllib.request.Request(base_url.rstrip("/") + path, headers={
            "Authorization": f"Basic {token}", "Accept": "application/json"})
        with urllib.request.urlopen(req, timeout=timeout) as resp:  # noqa: S310 (URL comes from the operator)
            return json.load(resp)

    return RegisterData(kri=get("/api/kri"), exceptions=get("/api/exceptions"))


def load_file(path: Path) -> RegisterData:
    raw = json.loads(path.read_text(encoding="utf-8"))
    return RegisterData(kri=raw["kri"], exceptions=raw["exceptions"])


# --------------------------------------------------------------------------- analysis


def as_of(data: RegisterData) -> date:
    return date.fromisoformat(data.kri["asOf"])


def expiring_soon(data: RegisterData, days: int) -> list[dict[str, Any]]:
    """Approved exceptions that run out within `days`: the owners should fix the gap or ask for renewal."""
    today = as_of(data)
    rows = []
    for e in data.exceptions:
        if e["status"] != "APPROVED":
            continue
        left = (date.fromisoformat(e["requestedExpiry"]) - today).days
        if 0 <= left <= days:
            rows.append({**e, "daysLeft": left})
    return sorted(rows, key=lambda r: r["daysLeft"])


def expired(data: RegisterData) -> list[dict[str, Any]]:
    return sorted((e for e in data.exceptions if e["status"] == "EXPIRED"), key=lambda e: e["requestedExpiry"])


def waiting(data: RegisterData) -> list[dict[str, Any]]:
    today = as_of(data)
    rows = []
    for e in data.exceptions:
        if e["status"] == "SUBMITTED" and e.get("submittedAt"):
            submitted = datetime.fromisoformat(e["submittedAt"].replace("Z", "+00:00")).date()
            rows.append({**e, "daysWaiting": (today - submitted).days})
    return sorted(rows, key=lambda r: -r["daysWaiting"])


def has_red(data: RegisterData) -> bool:
    return any(k["status"] == "RED" for k in data.kri["indicators"])


# --------------------------------------------------------------------------- rendering


def esc(value: Any) -> str:
    return html.escape("" if value is None else str(value), quote=True)


def fmt_value(k: dict[str, Any]) -> str:
    v = k["value"]
    if k["unit"] == "percent":
        return f"{v:.1f} %"
    return str(int(v)) if float(v).is_integer() else f"{v:.1f}"


def rating_bars(counts: dict[str, int]) -> str:
    """Horizontal bar chart as inline SVG, one bar per rating, labelled directly."""
    width, bar_h, gap, label_w = 520, 30, 12, 90
    biggest = max([counts.get(r, 0) for r in RATINGS] + [1])
    parts = [f'<svg viewBox="0 0 {width} {len(RATINGS) * (bar_h + gap)}" role="img" '
             f'aria-label="Open exceptions by residual rating" class="bars">']
    for i, r in enumerate(RATINGS):
        n = counts.get(r, 0)
        y = i * (bar_h + gap)
        w = 0 if n == 0 else max(4, (width - label_w - 40) * n / biggest)
        parts.append(f'<text x="0" y="{y + bar_h * 0.68}" class="lbl">{r.title()}</text>')
        parts.append(f'<rect x="{label_w}" y="{y}" width="{w:.1f}" height="{bar_h}" rx="3" '
                     f'fill="{RATING_COLOURS[r]}"/>')
        parts.append(f'<text x="{label_w + w + 8:.1f}" y="{y + bar_h * 0.68}" class="num">{n}</text>')
    parts.append("</svg>")
    return "".join(parts)


def table(rows: list[dict[str, Any]], columns: list[tuple[str, str]], empty: str) -> str:
    if not rows:
        return f'<p class="empty">{esc(empty)}</p>'
    head = "".join(f"<th>{esc(title)}</th>" for _, title in columns)
    body = []
    for r in rows:
        cells = []
        for key, _ in columns:
            v = r.get(key)
            if key == "residualRating":
                cells.append(f'<td><span class="pill" style="background:{RATING_COLOURS.get(v, "#777")}">'
                             f"{esc(v)}</span></td>")
            else:
                cells.append(f"<td>{esc(v)}</td>")
        body.append("<tr>" + "".join(cells) + "</tr>")
    return f"<table><thead><tr>{head}</tr></thead><tbody>{''.join(body)}</tbody></table>"


def render(data: RegisterData, expiring_days: int = 14) -> str:
    kri = data.kri
    kri_rows = "".join(
        f'<tr><td>{esc(k["name"])}</td><td class="r">{esc(fmt_value(k))}</td>'
        f'<td><span class="rag" style="background:{STATUS_COLOURS[k["status"]]}">{esc(k["status"])}</span></td>'
        f'<td class="muted">amber from {esc(k["amberFrom"])}, red from {esc(k["redFrom"])}</td></tr>'
        for k in kri["indicators"])
    reds = sum(1 for k in kri["indicators"] if k["status"] == "RED")
    soon = expiring_soon(data, expiring_days)
    cols_common = [("title", "Exception"), ("assetId", "Asset"), ("owner", "Owner"),
                   ("residualRating", "Residual")]

    return f"""<!doctype html>
<html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Security Exception KRIs – {esc(kri["asOf"])}</title>
<style>
  :root {{ --ink:#1d2433; --muted:#5b6476; --line:#e3e6ec; --bg:#f6f7f9; --card:#fff; }}
  * {{ box-sizing:border-box; }}
  body {{ margin:0; font:15px/1.45 system-ui,-apple-system,"Segoe UI",Roboto,sans-serif; color:var(--ink);
         background:var(--bg); }}
  main {{ max-width:1040px; margin:0 auto; padding:28px 20px 48px; }}
  h1 {{ font-size:24px; margin:0 0 4px; }} h2 {{ font-size:17px; margin:0 0 12px; }}
  .sub {{ color:var(--muted); margin:0 0 22px; }}
  .tiles {{ display:grid; grid-template-columns:repeat(auto-fit,minmax(180px,1fr)); gap:12px; margin-bottom:18px; }}
  .tile, .card {{ background:var(--card); border:1px solid var(--line); border-radius:10px; padding:16px 18px; }}
  .tile b {{ display:block; font-size:30px; font-variant-numeric:tabular-nums; }}
  .tile span {{ color:var(--muted); font-size:13px; }}
  .grid {{ display:grid; grid-template-columns:1.4fr 1fr; gap:14px; margin-bottom:14px; align-items:start; }}
  @media (max-width:820px) {{ .grid {{ grid-template-columns:1fr; }} }}
  table {{ width:100%; border-collapse:collapse; font-size:14px; }}
  th, td {{ text-align:left; padding:7px 8px; border-bottom:1px solid var(--line); vertical-align:top; }}
  th {{ font-size:12px; text-transform:uppercase; letter-spacing:.04em; color:var(--muted); font-weight:600; }}
  td.r {{ text-align:right; font-variant-numeric:tabular-nums; white-space:nowrap; }}
  .muted {{ color:var(--muted); font-size:12.5px; }}
  .rag, .pill {{ color:#fff; border-radius:999px; padding:2px 9px; font-size:12px; font-weight:600; }}
  .bars {{ width:100%; height:auto; }} .bars .lbl {{ font-size:17px; fill:var(--muted); }}
  .bars .num {{ font-size:17px; font-weight:600; fill:var(--ink); }}
  .card + .card {{ margin-top:14px; }} .empty {{ color:var(--muted); margin:0; }}
  footer {{ color:var(--muted); font-size:12px; margin-top:22px; }}
</style></head>
<body><main>
<h1>Security exception register – key risk indicators</h1>
<p class="sub">As of {esc(kri["asOf"])} · generated {esc(datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M UTC"))}</p>

<div class="tiles">
  <div class="tile"><b>{esc(kri["totalOpen"])}</b><span>open exceptions (submitted, approved, expired)</span></div>
  <div class="tile"><b>{esc(kri["expiringSoon"])}</b><span>approved, expiring within 30 days</span></div>
  <div class="tile"><b>{reds}</b><span>KRIs in red</span></div>
</div>

<div class="grid">
  <section class="card"><h2>Indicators</h2>
    <table><thead><tr><th>Indicator</th><th class="r">Value</th><th>Status</th><th>Thresholds</th></tr></thead>
    <tbody>{kri_rows}</tbody></table></section>
  <section class="card"><h2>Open exceptions by residual rating</h2>{rating_bars(kri["openByResidualRating"])}</section>
</div>

<section class="card"><h2>Expiring in the next {expiring_days} days – remind the owners</h2>
{table(soon, cols_common + [("requestedExpiry", "Expires"), ("daysLeft", "Days left")],
       "Nothing expires in this window.")}</section>
<section class="card"><h2>Expired, gap not closed</h2>
{table(expired(data), cols_common + [("requestedExpiry", "Expired on"), ("renewalCount", "Renewals")],
       "No expired exceptions.")}</section>
<section class="card"><h2>Waiting for a risk decision</h2>
{table(waiting(data), cols_common + [("requester", "Requester"), ("daysWaiting", "Days waiting")],
       "No requests are waiting.")}</section>

<footer>Thresholds are demo values; set them from your risk appetite statement.
Source: security-exception-register /api/kri and /api/exceptions.</footer>
</main></body></html>
"""


# --------------------------------------------------------------------------- CLI


def main(argv: list[str] | None = None) -> int:
    p = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    src = p.add_mutually_exclusive_group(required=True)
    src.add_argument("--url", help="base URL of the register, e.g. http://localhost:8080")
    src.add_argument("--input", type=Path, help="JSON file with {'kri': ..., 'exceptions': [...]}")
    p.add_argument("--user", default="dave", help="read-only user (default: the demo auditor)")
    p.add_argument("--password", default="demo")
    p.add_argument("--out", type=Path, default=Path("report.html"))
    p.add_argument("--expiring-days", type=int, default=14)
    args = p.parse_args(argv)

    try:
        data = fetch(args.url, args.user, args.password) if args.url else load_file(args.input)
        args.out.write_text(render(data, args.expiring_days), encoding="utf-8")
    except Exception as exc:  # noqa: BLE001 (report any failure as exit code 1)
        print(f"error: {exc}", file=sys.stderr)
        return 1

    print(f"wrote {args.out}")
    if has_red(data):
        print("at least one KRI is red", file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    sys.exit(main())
