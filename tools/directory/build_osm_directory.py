"""Adds hospitals and blood banks from OpenStreetMap to RaktSetu's directory, chiefly the private hospitals that the
government's open data does not list.

Source: OpenStreetMap, (c) OpenStreetMap contributors, available under the Open Database License (ODbL 1.0,
https://opendatacommons.org/licenses/odbl/). The file this script writes is a derived database and is likewise
available under the ODbL.

Kept: places tagged amenity=hospital (with a name), healthcare=blood_bank or healthcare=blood_donation. Dropped:
places whose name or tags show they do not transfuse blood (clinics, pharmacies, dental, eye, AYUSH, veterinary,
diagnostic and similar), and places already in the government directory (same kind, within 500 m, similar name).
Each place gets a district and state from its own address tags or, failing that, from the nearest facility in the
government directory.

Run after build_directory.py (Python 3, standard library only):
    python tools/directory/build_osm_directory.py [saved-overpass-answer.json]
Writes raktsetu-server/src/main/resources/directory/osm-facilities.csv.gz
"""

import csv
import gzip
import io
import json
import math
import os
import re
import sys
import time
import urllib.parse
import urllib.request

HERE = os.path.dirname(__file__)
RESOURCES = os.path.join(HERE, "..", "..", "raktsetu-server", "src", "main", "resources", "directory")
GOVERNMENT = os.path.join(RESOURCES, "india-facilities.csv.gz")
OUT = os.path.join(RESOURCES, "osm-facilities.csv.gz")
COLUMNS = ["id", "kind", "name", "category", "state", "district", "city", "address", "pincode", "latitude",
           "longitude"]

SERVERS = ["https://overpass-api.de/api/interpreter", "https://overpass.kumi.systems/api/interpreter",
           "https://overpass.private.coffee/api/interpreter"]
QUERY = """[out:json][timeout:600];
area["ISO3166-1"="IN"][admin_level=2]->.in;
(
  nwr["amenity"="hospital"]["name"](area.in);
  nwr["healthcare"="blood_bank"]["name"](area.in);
  nwr["healthcare"="blood_donation"]["name"](area.in);
);
out center tags;"""

NOT_TRANSFUSING = re.compile(
    r"\b(clinic|pharmacy|pharma|medical store|medicals|chemist|druggist|dental|dentist|eye|optical|opticals|"
    r"netralaya|ayurved\w*|homo?e?opath\w*|unani|siddha|naturopath\w*|yoga|veterinary|vet|animal|pet|"
    r"diagnostic\w*|laborator\w*|lab|labs|pathology|scan|scans|imaging|x-ray|xray|physio\w*|dispensary|"
    r"de-?addiction|rehab\w*|psychiatr\w*|mental|skin|derma\w*|hair|cosmetic|ivf|fertility|ent)\b",
    re.IGNORECASE)
SKIP_HEALTHCARE = {"clinic", "alternative", "midwife", "dentist", "pharmacy", "laboratory", "physiotherapist",
                   "optometrist", "psychotherapist", "rehabilitation"}
GENERIC = {"hospital", "hospitals", "blood", "bank", "centre", "center", "the", "and", "of", "govt", "government",
           "general", "multi", "speciality", "specialty", "super", "care", "medical", "health", "nursing", "home",
           "memorial", "trust", "pvt", "ltd", "private", "limited", "&"}


def fetch():
    for attempt in range(3):
        for server in SERVERS:
            try:
                data = urllib.parse.urlencode({"data": QUERY}).encode()
                request = urllib.request.Request(server, data=data, headers={
                    "User-Agent": "RaktSetu directory builder (github.com/pritikakhandelwal/raktsetu)"})
                with urllib.request.urlopen(request, timeout=900) as r:
                    return json.loads(r.read().decode("utf-8"))["elements"]
            except Exception as e:  # busy servers answer with an error page; try the next one
                print(f"{server}: {e}")
        time.sleep(60)
    raise RuntimeError("Every Overpass server failed")


def clean(text, limit):
    text = re.sub(r"\?{2,}", " ", (text or "").replace("\ufffd", " "))
    return re.sub(r"\s+", " ", text).strip().strip(",").strip()[:limit]


def words(name):
    return {w for w in re.findall(r"[a-z0-9]+", name.lower()) if w not in GENERIC and len(w) > 1}


def similar(a, b):
    wa, wb = words(a), words(b)
    if not wa or not wb:
        return a.lower() == b.lower()
    return len(wa & wb) / min(len(wa), len(wb)) >= 0.5


def km(lat1, lon1, lat2, lon2):
    x = math.radians(lon2 - lon1) * math.cos(math.radians((lat1 + lat2) / 2))
    y = math.radians(lat2 - lat1)
    return 6371 * math.hypot(x, y)


class Grid:
    """Government directory entries bucketed into 0.1-degree cells, for nearest-neighbour look-ups."""

    def __init__(self, rows):
        self.cells = {}
        for r in rows:
            self.cells.setdefault(self.key(float(r["latitude"]), float(r["longitude"])), []).append(r)

    @staticmethod
    def key(lat, lon):
        return round(lat * 10), round(lon * 10)

    def near(self, lat, lon, radius_cells):
        cy, cx = self.key(lat, lon)
        for dy in range(-radius_cells, radius_cells + 1):
            for dx in range(-radius_cells, radius_cells + 1):
                yield from self.cells.get((cy + dy, cx + dx), [])

    def nearest(self, lat, lon):
        for radius in (1, 3, 6):
            found = list(self.near(lat, lon, radius))
            if found:
                best = min(found, key=lambda r: km(lat, lon, float(r["latitude"]), float(r["longitude"])))
                if km(lat, lon, float(best["latitude"]), float(best["longitude"])) <= 60:
                    return best
        return None


def main():
    government = list(csv.DictReader(gzip.open(GOVERNMENT, "rt", encoding="utf-8")))
    grid = Grid(government)
    if len(sys.argv) > 1:
        with open(sys.argv[1], encoding="utf-8") as f:
            elements = json.load(f)["elements"]
    else:
        elements = fetch()

    out = []
    kept = {}  # grid cell -> places kept there, to drop OpenStreetMap's own duplicates
    dropped = {"no location": 0, "not transfusing": 0, "in government list": 0, "duplicate": 0}
    for e in elements:
        tags = e.get("tags", {})
        lat = e.get("lat", e.get("center", {}).get("lat"))
        lon = e.get("lon", e.get("center", {}).get("lon"))
        name = clean(tags.get("name:en") or tags.get("name"), 200)
        if lat is None or lon is None or not name or not (6 <= lat <= 37.5 and 68 <= lon <= 97.5):
            dropped["no location"] += 1
            continue
        blood = tags.get("healthcare") in ("blood_bank", "blood_donation")
        if not blood and (tags.get("healthcare") in SKIP_HEALTHCARE or NOT_TRANSFUSING.search(name)):
            dropped["not transfusing"] += 1
            continue
        kind = "BLOOD_CENTRE" if blood else "HOSPITAL"
        if any(r["kind"] == kind and km(lat, lon, float(r["latitude"]), float(r["longitude"])) <= 0.5
               and similar(name, r["name"]) for r in grid.near(lat, lon, 1)):
            dropped["in government list"] += 1
            continue
        cy, cx = Grid.key(lat, lon)
        if any(p[0] == kind and km(lat, lon, p[1], p[2]) <= 0.3 and similar(name, p[3])
               for dy in (-1, 0, 1) for dx in (-1, 0, 1) for p in kept.get((cy + dy, cx + dx), [])):
            dropped["duplicate"] += 1
            continue
        kept.setdefault((cy, cx), []).append((kind, lat, lon, name))

        nearest = grid.nearest(lat, lon)
        state = clean(tags.get("addr:state"), 60) or (nearest["state"] if nearest else "")
        district = clean(tags.get("addr:district"), 60) or (nearest["district"] if nearest else "")
        city = clean(tags.get("addr:city") or tags.get("addr:town") or tags.get("addr:village"), 60)
        address = clean(", ".join(t for t in (tags.get("addr:housenumber"), tags.get("addr:street"),
                                              tags.get("addr:suburb") or tags.get("addr:place")) if t), 300)
        owner = (tags.get("operator:type") or tags.get("ownership") or "").lower()
        category = {"private": "Private", "government": "Government", "public": "Government",
                    "charity": "Charity", "ngo": "Charity", "religious": "Charity"}.get(owner, "")
        category = clean(f"{category} {'blood bank' if blood else 'hospital'} (OpenStreetMap)".strip(), 40)
        out.append({
            "id": f"OSM{e['type'][0]}{e['id']}", "kind": kind, "name": name, "category": category,
            "state": state, "district": district, "city": city if kind == "BLOOD_CENTRE" else "",
            "address": address, "pincode": clean(tags.get("addr:postcode"), 10),
            "latitude": round(lat, 6), "longitude": round(lon, 6),
        })

    out.sort(key=lambda r: (r["kind"], r["state"], r["district"], r["name"]))
    buffer = io.StringIO()
    writer = csv.DictWriter(buffer, fieldnames=COLUMNS, lineterminator="\n")
    writer.writeheader()
    writer.writerows(out)
    with gzip.GzipFile(OUT, "wb", mtime=0) as f:
        f.write(buffer.getvalue().encode("utf-8"))
    kinds = {k: sum(1 for r in out if r["kind"] == k) for k in ("BLOOD_CENTRE", "HOSPITAL")}
    print(f"{len(elements)} OpenStreetMap places; kept {kinds}; dropped {dropped}; wrote {OUT}")


if __name__ == "__main__":
    main()
