import json
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent))

import kri_report as k  # noqa: E402

SAMPLE = HERE.parent / "sample_data.json"


def data():
    return k.load_file(SAMPLE)


def test_expiring_soon_is_sorted_and_limited_to_window():
    rows = k.expiring_soon(data(), 14)
    assert [r["assetId"] for r in rows] == ["ACC-VND-113", "APP-RPT-019", "APP-ADM-007"]
    assert rows[0]["daysLeft"] == 7
    assert all(r["status"] == "APPROVED" for r in rows)


def test_expired_and_waiting_lists():
    assert [e["assetId"] for e in k.expired(data())] == ["CLU-BAT-002", "JOB-NGT-017"]
    w = k.waiting(data())
    assert w[0]["assetId"] == "APP-PRC-031" and w[0]["daysWaiting"] == 14


def test_red_kri_gives_exit_code_2(tmp_path):
    out = tmp_path / "r.html"
    assert k.main(["--input", str(SAMPLE), "--out", str(out)]) == 2
    assert "Open exceptions by residual rating" in out.read_text(encoding="utf-8")


def test_all_green_gives_exit_code_0(tmp_path):
    raw = json.loads(SAMPLE.read_text(encoding="utf-8"))
    for ind in raw["kri"]["indicators"]:
        ind["status"] = "GREEN"
    src = tmp_path / "green.json"
    src.write_text(json.dumps(raw), encoding="utf-8")
    assert k.main(["--input", str(src), "--out", str(tmp_path / "r.html")]) == 0


def test_user_text_is_escaped():
    d = data()
    d.exceptions[0]["title"] = '<script>alert("x")</script>'
    page = k.render(d)
    assert "<script>alert" not in page
    assert "&lt;script&gt;" in page


def test_missing_input_is_exit_code_1(tmp_path):
    assert k.main(["--input", str(tmp_path / "nope.json"), "--out", str(tmp_path / "r.html")]) == 1


def test_bars_scale_to_largest_count():
    svg = k.rating_bars({"LOW": 1, "MEDIUM": 4, "HIGH": 0, "CRITICAL": 2})
    assert svg.count("<rect") == 4
    assert 'width="0.0"' in svg  # HIGH has no bar
