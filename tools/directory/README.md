# Facility directory

The hospitals and blood centres offered on RaktSetu's "Request an account" form. They are reference data: a facility
joins the network only when an administrator approves someone from it (or adds it on the Facilities page).

| File | Built by | Contents | Licence |
|---|---|---|---|
| `india-facilities.csv.gz` | `build_directory.py` | 2,408 licensed blood centres (government, private, charity) and 6,504 government hospitals, National Health Portal | Government Open Data License – India (NDSAP) |
| `osm-facilities.csv.gz` | `build_osm_directory.py` | About 43,000 further hospitals and blood banks, chiefly private, from OpenStreetMap | © OpenStreetMap contributors, [ODbL 1.0](https://opendatacommons.org/licenses/odbl/) |

Both files are in `raktsetu-server/src/main/resources/directory/` and are loaded by migration V12.

- **No personal data.** The blood bank source lists nodal officers' names, phones and e-mails; they are not copied.
- **Filtering.** OpenStreetMap places whose name or tags show they do not transfuse blood (clinics, pharmacies, dental,
  eye, AYUSH, diagnostic and similar) are dropped, as are those already in the government list.
- **Updating.** Run `python tools/directory/build_directory.py`, then `python tools/directory/build_osm_directory.py`
  (Python 3, standard library only), and add a migration that reloads the directory, as V12 does.
- **ODbL.** `osm-facilities.csv.gz` is a derived database and is available under the ODbL; the request form credits
  OpenStreetMap contributors.
