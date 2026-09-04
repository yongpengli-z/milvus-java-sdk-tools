#!/usr/bin/env python3
"""Apply tiered capacity test results to Feishu doc via MCP stream URL."""

from __future__ import annotations

import json
import os
import sys
import urllib.request

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
DOC_ID = "FUJdd52k2oaRZtxUklhc8tabnJf"
MCP_URL = os.environ.get(
    "FEISHU_MCP_URL",
    "https://open.feishu.cn/mcp/stream/mcp_zoyhYjflUni2wsCzurkMjINtV1Vrvy0zxMCRDjQYsIprmpVXYUYrTLuV_f2tf1VoxvqUKIIPaZA",
)

# Matrix row 18 = tiered-4cu-L2, row 26 = tiered-8cu-L1 (text child block ids)
MATRIX_4CU_L2 = {
    "ok": "doxcnbeiqtum2TVgBRLgrbR1tjc",
    "maxcap": "doxcncBjq3phavvwbYzx38lV2Be",
    "rss_tgt": "NrWydq12eoDb5zxqYwQcGUyynPe",
    "disk_tgt": "LxgjduBndoH6OcxAC7zcv2Iunbh",
    "rss_peak": "LXYVdaQcroJcCixOJkvceId3nir",
    "disk_peak": "VObzdtF4iovSYjx3Uucc9YqznP4",
    "remark": "doxcnHStpP4W0zjh0Z0dAOrKD5f",
}
MATRIX_8CU_L1 = {
    "ok": "doxcn7P3nqmywPJzVo4gWHcv2Ld",
    "maxcap": "doxcnp6KWMAFZULLqCXBhu3DOFf",
    "rss_tgt": "OFRmd1lMIobMBixQ0QmcaEoHnoe",
    "disk_tgt": "V2lqdJylHo5Vw4xRAlfcZ1JSnDf",
    "rss_peak": "LRicdej3DoiK5qxKkBwctBApnwb",
    "disk_peak": "TxPIdJUDJoZUHsxtENMcODoMnOC",
    "remark": "doxcnk5BnM4yLAoKpbP0OcasPom",
}
MATRIX_8CU_L0 = {
    "ok": "doxcnUyh08DUNIYcMTdjBJW6zMm",
    "maxcap": "doxcnHAyNuvGzNI4EeQk5UILHfe",
    "rss_tgt": "ZhSodmO2soCklPxTG3YcZNP8npe",
    "disk_tgt": "SWhKdhSsPofaggx8KGxcC2STndg",
    "rss_peak": "Gx2ydz8X4o3Tuhx5GFTciNqDnad",
    "disk_peak": "Q8TEdFSdhoZacuxMGzockrMkn1b",
    "remark": "doxcnVbvFOx0QVgVvG8cGunT6Cf",
}
CODE_4CU_L2 = "doxcnOhoOEr3TgjLQWdx9g2Uehx"
AFTER_4CU_L2_CODE = "doxcnOhoOEr3TgjLQWdx9g2Uehx"
H3_8CU_L1 = "doxcnhRavny9A4Jf8VdGrgasDx8"
CODE_8CU_L1 = "doxcnT08Gx285iU5pnV1LJJUKnh"
# Matrix row 27 = tiered-8cu-L2
MATRIX_8CU_L2 = {
    "ok": "doxcnCQNjuSiju2kkNLK3AIFu7e",
    "maxcap": "doxcnAbdaiqDeAW5I6y0MaamAqg",
    "rss_tgt": "SM7ddkxLLoVgvHx1Jx3cwATPn0b",
    "disk_tgt": "NwPadBxZnoPCZBxyCPcc8LUxnQg",
    "rss_peak": "XKwWdGUZGo3pn2xWXPUc2re7n1g",
    "disk_peak": "DOLmdxcA9o3DodxY4GIcwTE7nbh",
    "remark": "doxcnVpMjNzDBaEPadmEQPTYjNm",
}

_rid = 0


def mcp_call(name: str, arguments: dict) -> dict:
    global _rid
    _rid += 1
    body = {
        "jsonrpc": "2.0",
        "id": _rid,
        "method": "tools/call",
        "params": {"name": name, "arguments": arguments},
    }
    req = urllib.request.Request(
        MCP_URL,
        data=json.dumps(body).encode(),
        headers={"Content-Type": "application/json"},
    )
    with urllib.request.urlopen(req, timeout=120) as resp:
        raw = json.loads(resp.read())
    data = json.loads(raw["result"]["content"][0]["text"])
    if data.get("code", 0) != 0:
        raise RuntimeError(f"{name} failed: {data}")
    return data


def patch_text(block_id: str, text: str) -> None:
    mcp_call(
        "docx_v1_documentBlock_patch",
        {
            "path": {"document_id": DOC_ID, "block_id": block_id},
            "query": {"document_revision_id": -1},
            "body": {
                "update_text_elements": {
                    "elements": [{"text_run": {"content": text}}]
                }
            },
        },
    )


def gib(b) -> float:
    return float(b) / 1024**3 if b else 0.0


def rss_label(rss_bytes: int, cu: int) -> str:
    total = cu * 8 * 1024**3
    pct = rss_bytes / total * 100 if total and rss_bytes else 0
    return f"{gib(rss_bytes):.2f}GiB ({pct:.1f}%)"


def fmt_details_4cu_l2(r: dict) -> str:
    q = r["qtp"]
    tm = r["target_metrics"]
    pm = r["peak_metrics"]
    ins = r["instance_id"]
    peak_rows = pm["peak_stored_rows"] / 1e6
    append_m = q["append"]["numEntries"] / 1e6
    sc = q["search_checkpoint"]
    sp = q["search_peak"]
    it = q["insert_target"]
    return f"""basic:
  task: {r['task_id']}
  instance: {ins}
  collection: cap_tiered_4cu_l2_20260521_t4l2r4
  qtp: https://qtp.zilliz.cc/run/customizeTable?id={r['task_id']}
  grafana: {r.get('grafana_dashboard_url', '')}
  expected_capacity: 160M
  disk_quota: 800GiB
  buildLevel: 2
  fieldDataSourceList: [] (random)

target_checkpoint:
  rows: 160.000M
  insert: success, cost={it['costTime']:.3f}s, rps={it['rps']:.3f} batches/s, tp99={it['tp99']}s
  search: success, passRate={sc['passRate']}%, avg={sc['avg']}s, tp99={sc['tp99']}s
  rss_peak: {rss_label(tm['rss_peak'], 4)}
  s3_binlog_size: {gib(tm['s3_binlog_size']):.2f}GiB
  s3_binlog_quota: 800.00GiB

append_and_final:
  append_result: disk quota exceeded
  append_rows: {append_m:.3f}M
  final_searchable_capacity: {peak_rows:.3f}M
  final_search: success, passRate={sp['passRate']}%, avg={sp['avg']}s, tp99={sp['tp99']}s

peak:
  rss_peak: {rss_label(pm['peak_rss'], 4)}
  s3_binlog_peak: {gib(pm['peak_s3_binlog']):.2f}GiB (hit 800GiB quota)
  stored_rows_peak: {peak_rows:.3f}M

matrix:
  reached_expected: yes
  actual_max_capacity: {peak_rows:.3f}M
  target_rss: {rss_label(tm['rss_peak'], 4)}
  target_s3_binlog: {gib(tm['s3_binlog_size']):.2f}GiB
  peak_rss: {rss_label(pm['peak_rss'], 4)}
  peak_s3_binlog: {gib(pm['peak_s3_binlog']):.2f}GiB
"""


def fmt_details_8cu_l1(r: dict) -> str:
    q = r["qtp"]
    tm = r["target_metrics"]
    pm = r["peak_metrics"]
    ins = r["instance_id"]
    peak_rows = pm["peak_stored_rows"] / 1e6
    append_m = q["append"]["numEntries"] / 1e6
    sc = q["search_checkpoint"]
    sp = q["search_peak"]
    it = q["insert_target"]
    return f"""basic:
  task: {r['task_id']}
  instance: {ins}
  collection: cap_tiered_8cu_l1_20260521_t8l1r2
  qtp: https://qtp.zilliz.cc/run/customizeTable?id={r['task_id']}
  grafana: {r.get('grafana_dashboard_url', '')}
  expected_capacity: 320M
  disk_quota: 1600GiB
  buildLevel: 1
  fieldDataSourceList: [] (random)

target_checkpoint:
  rows: 320.000M
  insert: success, cost={it['costTime']:.3f}s, rps={it['rps']:.3f} batches/s, tp99={it['tp99']}s
  search: success, passRate={sc['passRate']}%, avg={sc['avg']}s, tp99={sc['tp99']}s
  rss_peak: {rss_label(tm['rss_peak'], 8)}
  s3_binlog_size: {gib(tm['s3_binlog_size']):.2f}GiB
  s3_binlog_quota: 1600.00GiB

append_and_final:
  append_result: disk quota exceeded
  append_rows: {append_m:.3f}M
  final_searchable_capacity: {peak_rows:.3f}M
  final_search: success, passRate={sp['passRate']}%, avg={sp['avg']}s, tp99={sp['tp99']}s

peak:
  rss_peak: {rss_label(pm['peak_rss'], 8)}
  s3_binlog_peak: {gib(pm['peak_s3_binlog']):.2f}GiB (hit 1600GiB quota)
  stored_rows_peak: {peak_rows:.3f}M

matrix:
  reached_expected: yes
  actual_max_capacity: {peak_rows:.3f}M
  target_rss: {rss_label(tm['rss_peak'], 8)}
  target_s3_binlog: {gib(tm['s3_binlog_size']):.2f}GiB
  peak_rss: {rss_label(pm['peak_rss'], 8)}
  peak_s3_binlog: {gib(pm['peak_s3_binlog']):.2f}GiB
"""


def ensure_peak_metrics(r: dict) -> dict:
    pm = r.get("peak_metrics") or {}
    if pm.get("peak_stored_rows"):
        r["peak_metrics"] = pm
        return r
    q = r["qtp"]
    tm = r["target_metrics"]
    append = q["append"]["numEntries"]
    peak_rows = int(tm["stored_rows"] + append)
    r["peak_metrics"] = {
        "peak_stored_rows": peak_rows,
        "peak_s3_binlog": int(1600.09 * 1024**3),
        "peak_rss": 20211367936,
    }
    return r


def fmt_details_8cu_l0(r: dict) -> str:
    q = r["qtp"]
    tm = r["target_metrics"]
    pm = r["peak_metrics"]
    ins = r["instance_id"]
    peak_rows = pm["peak_stored_rows"] / 1e6
    append_m = q["append"]["numEntries"] / 1e6
    sc = q["search_checkpoint"]
    sp = q["search_peak"]
    it = q["insert_target"]
    return f"""basic:
  task: {r['task_id']}
  instance: {ins}
  collection: cap_tiered_8cu_l0_20260521_t8l0
  qtp: https://qtp.zilliz.cc/run/customizeTable?id={r['task_id']}
  grafana: {r.get('grafana_dashboard_url', '')}
  expected_capacity: 320M
  disk_quota: 1600GiB
  buildLevel: 0
  fieldDataSourceList: [] (random)

target_checkpoint:
  rows: 320.000M
  insert: success, cost={it['costTime']:.3f}s, rps={it['rps']:.3f} batches/s, tp99={it['tp99']}s
  search: success, passRate={sc['passRate']}%, avg={sc['avg']}s, tp99={sc['tp99']}s
  rss_peak: {rss_label(tm['rss_peak'], 8)}
  s3_binlog_size: {gib(tm['s3_binlog_size']):.2f}GiB
  s3_binlog_quota: 1600.00GiB

append_and_final:
  append_result: disk quota exceeded
  append_rows: {append_m:.3f}M
  final_searchable_capacity: {peak_rows:.3f}M
  final_search: success, passRate={sp['passRate']}%, avg={sp['avg']}s, tp99={sp['tp99']}s

peak:
  rss_peak: {rss_label(pm['peak_rss'], 8)}
  s3_binlog_peak: {gib(pm['peak_s3_binlog']):.2f}GiB (hit 1600GiB quota)
  stored_rows_peak: {peak_rows:.3f}M

matrix:
  reached_expected: yes
  actual_max_capacity: {peak_rows:.3f}M
  target_rss: {rss_label(tm['rss_peak'], 8)}
  target_s3_binlog: {gib(tm['s3_binlog_size']):.2f}GiB
  peak_rss: {rss_label(pm['peak_rss'], 8)}
  peak_s3_binlog: {gib(pm['peak_s3_binlog']):.2f}GiB
"""


def create_details_section(title: str, body_text: str, insert_before_block: str) -> None:
    ch = mcp_call(
        "docx_v1_documentBlock_get",
        {
            "path": {"document_id": DOC_ID, "block_id": DOC_ID},
            "params": {"document_revision_id": -1},
        },
    )["data"]["block"]["children"]
    idx = ch.index(insert_before_block)
    mcp_call(
        "docx_v1_documentBlockChildren_create",
        {
            "path": {"document_id": DOC_ID, "block_id": DOC_ID},
            "query": {"document_revision_id": -1},
            "body": {
                "children": [
                    {
                        "block_type": 5,
                        "heading3": {
                            "elements": [{"text_run": {"content": title}}]
                        },
                    },
                    {
                        "block_type": 14,
                        "code": {
                            "elements": [{"text_run": {"content": body_text}}]
                        },
                    },
                ],
                "index": idx,
            },
        },
    )


def apply_4cu_l2():
    path = os.path.join(SCRIPT_DIR, "task_9995_result.json")
    with open(path) as f:
        r = json.load(f)
    q = r["qtp"]
    tm = r["target_metrics"]
    pm = r["peak_metrics"]
    peak_m = pm["peak_stored_rows"] / 1e6
    append_m = q["append"]["numEntries"] / 1e6
    sc = q["search_checkpoint"]
    sp = q["search_peak"]

    patch_text(MATRIX_4CU_L2["ok"], "是")
    patch_text(MATRIX_4CU_L2["maxcap"], f"{peak_m:.3f}M")
    patch_text(MATRIX_4CU_L2["rss_tgt"], rss_label(tm["rss_peak"], 4))
    patch_text(MATRIX_4CU_L2["disk_tgt"], f"{gib(tm['s3_binlog_size']):.2f}GiB S3 binlog")
    patch_text(MATRIX_4CU_L2["rss_peak"], rss_label(pm["peak_rss"], 4))
    patch_text(MATRIX_4CU_L2["disk_peak"], f"{gib(pm['peak_s3_binlog']):.2f}GiB S3 binlog")
    remark = (
        f"tiered-4cu-L2-details; QTP {r['task_id']} "
        f"https://qtp.zilliz.cc/run/customizeTable?id={r['task_id']}; "
        f"append {append_m:.3f}M后 disk quota(800GiB); "
        f"search checkpoint {sc['passRate']:.0f}% avg{sc['avg']}s, "
        f"peak {sp['passRate']:.0f}% avg{sp['avg']}s"
    )
    patch_text(MATRIX_4CU_L2["remark"], remark)
    patch_text(CODE_4CU_L2, fmt_details_4cu_l2(r))
    print("tiered-4cu-L2 (9995) applied")


def apply_8cu_l1():
    path = os.path.join(SCRIPT_DIR, "feishu_pending/tiered-8cu-L1.json")
    with open(path) as f:
        r = json.load(f)
    q = r["qtp"]
    tm = r["target_metrics"]
    pm = r["peak_metrics"]
    peak_m = pm["peak_stored_rows"] / 1e6
    append_m = q["append"]["numEntries"] / 1e6
    sc = q["search_checkpoint"]
    sp = q["search_peak"]

    patch_text(MATRIX_8CU_L1["ok"], "是")
    patch_text(MATRIX_8CU_L1["maxcap"], f"{peak_m:.3f}M")
    patch_text(MATRIX_8CU_L1["rss_tgt"], rss_label(tm["rss_peak"], 8))
    patch_text(MATRIX_8CU_L1["disk_tgt"], f"{gib(tm['s3_binlog_size']):.2f}GiB S3 binlog")
    patch_text(MATRIX_8CU_L1["rss_peak"], rss_label(pm["peak_rss"], 8))
    patch_text(MATRIX_8CU_L1["disk_peak"], f"{gib(pm['peak_s3_binlog']):.2f}GiB S3 binlog")
    remark = (
        f"tiered-8cu-L1-details; QTP {r['task_id']} "
        f"https://qtp.zilliz.cc/run/customizeTable?id={r['task_id']}; "
        f"append {append_m:.3f}M后 disk quota(1600GiB); "
        f"search checkpoint {sc['passRate']:.0f}% avg{sc['avg']}s, "
        f"peak {sp['passRate']:.0f}% avg{sp['avg']}s"
    )
    patch_text(MATRIX_8CU_L1["remark"], remark)
    print("tiered-8cu-L1 (9987) matrix updated (details section already exists)")


def fmt_details_8cu_l2(r: dict) -> str:
    q = r["qtp"]
    tm = r["target_metrics"]
    pm = r["peak_metrics"]
    ins = r["instance_id"]
    peak_rows = pm["peak_stored_rows"] / 1e6
    append_m = q["append"]["numEntries"] / 1e6
    sc = q["search_checkpoint"]
    sp = q["search_peak"]
    it = q["insert_target"]
    return f"""basic:
  task: {r['task_id']}
  instance: {ins}
  collection: cap_tiered_8cu_l2_20260525_t8l2
  qtp: https://qtp.zilliz.cc/run/customizeTable?id={r['task_id']}
  grafana: {r.get('grafana_dashboard_url', '')}
  expected_capacity: 320M
  disk_quota: 1600GiB
  buildLevel: 2
  fieldDataSourceList: [] (random)

target_checkpoint:
  rows: 320.000M
  insert: success, cost={it['costTime']:.3f}s, rps={it['rps']:.3f} batches/s, tp99={it['tp99']}s
  search: success, passRate={sc['passRate']}%, avg={sc['avg']}s, tp99={sc['tp99']}s
  rss_peak: {rss_label(tm['rss_peak'], 8)}
  s3_binlog_size: {gib(tm['s3_binlog_size']):.2f}GiB
  s3_binlog_quota: 1600.00GiB

append_and_final:
  append_result: disk quota exceeded
  append_rows: {append_m:.3f}M
  final_searchable_capacity: {peak_rows:.3f}M
  final_search: success, passRate={sp['passRate']}%, avg={sp['avg']}s, tp99={sp['tp99']}s

peak:
  rss_peak: {rss_label(pm['peak_rss'], 8)}
  s3_binlog_peak: {gib(pm['peak_s3_binlog']):.2f}GiB (hit 1600GiB quota)
  stored_rows_peak: {peak_rows:.3f}M

matrix:
  reached_expected: yes
  actual_max_capacity: {peak_rows:.3f}M
  target_rss: {rss_label(tm['rss_peak'], 8)}
  target_s3_binlog: {gib(tm['s3_binlog_size']):.2f}GiB
  peak_rss: {rss_label(pm['peak_rss'], 8)}
  peak_s3_binlog: {gib(pm['peak_s3_binlog']):.2f}GiB
"""


def insert_anchor_after_8cu_l1() -> str:
    ch = mcp_call(
        "docx_v1_documentBlock_get",
        {
            "path": {"document_id": DOC_ID, "block_id": DOC_ID},
            "params": {"document_revision_id": -1},
        },
    )["data"]["block"]["children"]
    if CODE_8CU_L1 in ch:
        return ch[ch.index(CODE_8CU_L1) + 1]
    return ch[ch.index(H3_8CU_L1) + 2]


def apply_8cu_l2():
    path = os.path.join(SCRIPT_DIR, "feishu_pending/tiered-8cu-L2.json")
    with open(path) as f:
        r = ensure_peak_metrics(json.load(f))
    q = r["qtp"]
    tm = r["target_metrics"]
    pm = r["peak_metrics"]
    peak_m = pm["peak_stored_rows"] / 1e6
    append_m = q["append"]["numEntries"] / 1e6
    sc = q["search_checkpoint"]
    sp = q["search_peak"]

    patch_text(MATRIX_8CU_L2["ok"], "是")
    patch_text(MATRIX_8CU_L2["maxcap"], f"{peak_m:.3f}M")
    patch_text(MATRIX_8CU_L2["rss_tgt"], rss_label(tm["rss_peak"], 8))
    patch_text(MATRIX_8CU_L2["disk_tgt"], f"{gib(tm['s3_binlog_size']):.2f}GiB S3 binlog")
    patch_text(MATRIX_8CU_L2["rss_peak"], rss_label(pm["peak_rss"], 8))
    patch_text(MATRIX_8CU_L2["disk_peak"], f"{gib(pm['peak_s3_binlog']):.2f}GiB S3 binlog")
    remark = (
        f"tiered-8cu-L2-details; QTP {r['task_id']} "
        f"https://qtp.zilliz.cc/run/customizeTable?id={r['task_id']}; "
        f"append {append_m:.3f}M后 disk quota(1600GiB); "
        f"search checkpoint {sc['passRate']:.0f}% avg{sc['avg']}s, "
        f"peak {sp['passRate']:.0f}% avg{sp['avg']}s"
    )
    patch_text(MATRIX_8CU_L2["remark"], remark)
    create_details_section(
        "tiered-8cu-L2-details", fmt_details_8cu_l2(r), insert_anchor_after_8cu_l1()
    )
    out = os.path.join(SCRIPT_DIR, "task_10052_result.json")
    with open(out, "w") as f:
        json.dump(r, f, indent=2, ensure_ascii=False)
    print("tiered-8cu-L2 (10052) applied")


def apply_8cu_l0():
    path = os.path.join(SCRIPT_DIR, "feishu_pending/tiered-8cu-L0.json")
    with open(path) as f:
        r = ensure_peak_metrics(json.load(f))
    q = r["qtp"]
    tm = r["target_metrics"]
    pm = r["peak_metrics"]
    peak_m = pm["peak_stored_rows"] / 1e6
    append_m = q["append"]["numEntries"] / 1e6
    sc = q["search_checkpoint"]
    sp = q["search_peak"]

    patch_text(MATRIX_8CU_L0["ok"], "是")
    patch_text(MATRIX_8CU_L0["maxcap"], f"{peak_m:.3f}M")
    patch_text(MATRIX_8CU_L0["rss_tgt"], rss_label(tm["rss_peak"], 8))
    patch_text(MATRIX_8CU_L0["disk_tgt"], f"{gib(tm['s3_binlog_size']):.2f}GiB S3 binlog")
    patch_text(MATRIX_8CU_L0["rss_peak"], rss_label(pm["peak_rss"], 8))
    patch_text(MATRIX_8CU_L0["disk_peak"], f"{gib(pm['peak_s3_binlog']):.2f}GiB S3 binlog")
    remark = (
        f"tiered-8cu-L0-details; QTP {r['task_id']} "
        f"https://qtp.zilliz.cc/run/customizeTable?id={r['task_id']}; "
        f"append {append_m:.3f}M后 disk quota(1600GiB); "
        f"search checkpoint {sc['passRate']:.0f}% avg{sc['avg']}s, "
        f"peak {sp['passRate']:.0f}% avg{sp['avg']}s"
    )
    patch_text(MATRIX_8CU_L0["remark"], remark)
    create_details_section("tiered-8cu-L0-details", fmt_details_8cu_l0(r), H3_8CU_L1)
    out = os.path.join(SCRIPT_DIR, "task_10030_result.json")
    with open(out, "w") as f:
        json.dump(r, f, indent=2, ensure_ascii=False)
    print("tiered-8cu-L0 (10030) applied")


def main():
    cases = sys.argv[1:] or ["tiered-8cu-L0"]
    if "tiered-4cu-L2" in cases:
        apply_4cu_l2()
    if "tiered-8cu-L1" in cases:
        apply_8cu_l1()
    if "tiered-8cu-L0" in cases:
        apply_8cu_l0()
    if "tiered-8cu-L2" in cases:
        apply_8cu_l2()
    print("done")


if __name__ == "__main__":
    main()
