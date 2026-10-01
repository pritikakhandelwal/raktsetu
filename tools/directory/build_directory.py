"""Builds RaktSetu's directory of Indian blood centres and hospitals from open government data.

Sources (Government of India open data, National Data Sharing and Accessibility Policy; served by Esri India's Living
Atlas):
  * Blood Bank Directory (National Health Portal), 2017: every licensed blood centre with its location.
  * Hospital Directory (National Health Portal / NIN health facilities): public health facilities with locations.
    Only facilities that are hospitals are kept; sub-centres, primary health centres and dispensaries do not
    transfuse blood.

Only the facility's name, kind, place and location are kept. The blood bank directory also lists nodal officers'
names, phone numbers and e-mail addresses; those are personal data and are deliberately not copied.

Usage (Python 3, standard library only):
    python tools/directory/build_directory.py
Writes raktsetu-server/src/main/resources/directory/india-facilities.csv.gz
"""

import csv
import gzip
import io
import json
import os
import re
import urllib.parse
import urllib.request

BLOOD_BANKS = "https://livingatlas.esri.in/server1/rest/services/Health/IN_BloodBankDirectory_2017/MapServer/0"
HOSPITALS = "https://livingatlas.esri.in/server/rest/services/LivingAtlas/IND_Hospital_Directory/MapServer/0"

HOSPITAL_TYPES = [
    "Community Health Center",
    "Sub-District Hospital",
    "District Hospital",
    "<100 Bedded Hospital",
    "Medical Colleges Hospital",
    "Civil Hospital/General Hospital",
    "Maternity Home",
    "100-500 Bedded Hospital",
    "Referral Hospital",
    "Women Hospital",
    ">500 Bedded Hospital",
]

OUT = os.path.join(os.path.dirname(__file__), "..", "..", "raktsetu-server", "src", "main", "resources", "directory",
                   "india-facilities.csv.gz")
COLUMNS = ["id", "kind", "name", "category", "state", "district", "city", "address", "pincode", "latitude",
           "longitude"]


def query(layer, where, fields):
    """Every matching record, fetched in pages of 1,000 by object id."""
    rows, last = [], -1
    while True:
        params = urllib.parse.urlencode({
            "where": f"({where}) AND objectid > {last}",
            "outFields": ",".join(fields),
            "orderByFields": "objectid",
            "resultRecordCount": 1000,
            "returnGeometry": "false",
            "f": "json",
        })
        with urllib.request.urlopen(f"{layer}/query?{params}", timeout=120) as r:
            data = json.load(r)
        if "error" in data:
            raise RuntimeError(data["error"])
        page = [f["attributes"] for f in data["features"]]
        if not page:
            return rows
        rows.extend(page)
        last = page[-1]["objectid"]


def clean(text, limit):
    if text is None:
        return ""
    text = re.sub(r"\s+", " ", str(text)).strip().strip(",").strip()
    return "" if text.upper() in ("NA", "N/A", "NULL", "0", "-") else text[:limit]


def title(text):
    """Names arrive in capitals or mixed case; keep mixed case, soften all-capitals."""
    return text.title() if text.isupper() else text


def located(lat, lon):
    """True for a plausible location inside India."""
    try:
        lat, lon = float(lat), float(lon)
    except (TypeError, ValueError):
        return False
    return 6.0 <= lat <= 37.5 and 68.0 <= lon <= 97.5


def main():
    out = []

    banks = query(BLOOD_BANKS, "1=1", ["objectid", "blood_bank_name", "state", "district", "city", "address",
                                       "pincode", "category", "latitude", "longitude"])
    for b in banks:
        name = title(clean(b["blood_bank_name"], 200))
        if not name or not located(b["latitude"], b["longitude"]):
            continue
        out.append({
            "id": f"BC{b['objectid']}", "kind": "BLOOD_CENTRE", "name": name,
            "category": clean(b["category"], 40), "state": title(clean(b["state"], 60)),
            "district": title(clean(b["district"], 60)), "city": title(clean(b["city"], 60)),
            "address": clean(b["address"], 300), "pincode": clean(b["pincode"], 10),
            "latitude": round(float(b["latitude"]), 6), "longitude": round(float(b["longitude"]), 6),
        })

    types = ", ".join("'" + t.replace("'", "''") + "'" for t in HOSPITAL_TYPES)
    hospitals = query(HOSPITALS, f"facility_type IN ({types})",
                      ["objectid", "health_facility_name", "facility_type", "state_name", "district_name",
                       "locality", "address", "posatalcode", "lat", "lon"])
    for h in hospitals:
        name = title(clean(h["health_facility_name"], 200))
        if not name or not located(h["lat"], h["lon"]):
            continue
        out.append({
            "id": f"HO{h['objectid']}", "kind": "HOSPITAL", "name": name,
            "category": clean(h["facility_type"], 40), "state": title(clean(h["state_name"], 60)),
            "district": title(clean(h["district_name"], 60)), "city": title(clean(h["locality"], 60)),
            "address": clean(h["address"], 300), "pincode": clean(h["posatalcode"], 10),
            "latitude": round(float(h["lat"]), 6), "longitude": round(float(h["lon"]), 6),
        })

    # The same facility listed twice in one district keeps its first entry.
    seen, unique = set(), []
    for row in out:
        key = (row["kind"], row["name"].lower(), row["district"].lower())
        if key not in seen:
            seen.add(key)
            unique.append(row)
    unique.sort(key=lambda r: (r["kind"], r["state"], r["district"], r["name"]))

    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    buffer = io.StringIO()
    writer = csv.DictWriter(buffer, fieldnames=COLUMNS, lineterminator="\n")
    writer.writeheader()
    writer.writerows(unique)
    with gzip.GzipFile(OUT, "wb", mtime=0) as f:
        f.write(buffer.getvalue().encode("utf-8"))
    kinds = {k: sum(1 for r in unique if r["kind"] == k) for k in ("BLOOD_CENTRE", "HOSPITAL")}
    print(f"{len(banks)} blood banks and {len(hospitals)} hospitals downloaded; kept {kinds}; wrote {OUT}")


if __name__ == "__main__":
    main()
