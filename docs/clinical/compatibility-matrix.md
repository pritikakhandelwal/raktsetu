# Compatibility matrix (generated)

Generated from the code by `ClinicalMatrixTest`. Do not edit by hand: the build fails if this file and the code disagree.

Rows are the **donor** unit's group, columns the **recipient's** group.

| Symbol | Meaning |
|---|---|
| `=` | Identical: the preferred match |
| `C` | Compatible: a different group that is safe |
| `W` | Allowed with a warning shown to staff |
| `.` | Incompatible: never issued |

## Whole blood

Shelf life 35 days; stored at 2 to 6 °C (CPDA-1).

| Donor \ Recipient | O- | O+ | A- | A+ | B- | B+ | AB- | AB+ |
|---|---|---|---|---|---|---|---|---|
| **O-** | = | . | . | . | . | . | . | . |
| **O+** | . | = | . | . | . | . | . | . |
| **A-** | . | . | = | . | . | . | . | . |
| **A+** | . | . | . | = | . | . | . | . |
| **B-** | . | . | . | . | = | . | . | . |
| **B+** | . | . | . | . | . | = | . | . |
| **AB-** | . | . | . | . | . | . | = | . |
| **AB+** | . | . | . | . | . | . | . | = |

Order in which donor groups are tried for each recipient (first = preferred):

| Recipient | Donor groups tried |
|---|---|
| O- | O- |
| O+ | O+ |
| A- | A- |
| A+ | A+ |
| B- | B- |
| B+ | B+ |
| AB- | AB- |
| AB+ | AB+ |

Warnings shown to staff (none if the list is empty):


## Red cell concentrate

Shelf life 42 days; stored at 2 to 6 °C (SAGM).

| Donor \ Recipient | O- | O+ | A- | A+ | B- | B+ | AB- | AB+ |
|---|---|---|---|---|---|---|---|---|
| **O-** | = | C | C | C | C | C | C | C |
| **O+** | . | = | . | C | . | C | . | C |
| **A-** | . | . | = | C | . | . | C | C |
| **A+** | . | . | . | = | . | . | . | C |
| **B-** | . | . | . | . | = | C | C | C |
| **B+** | . | . | . | . | . | = | . | C |
| **AB-** | . | . | . | . | . | . | = | C |
| **AB+** | . | . | . | . | . | . | . | = |

Order in which donor groups are tried for each recipient (first = preferred):

| Recipient | Donor groups tried |
|---|---|
| O- | O- |
| O+ | O+, O- |
| A- | A-, O- |
| A+ | A+, O+, A-, O- |
| B- | B-, O- |
| B+ | B+, O+, B-, O- |
| AB- | AB-, A-, B-, O- |
| AB+ | AB+, A+, B+, O+, A-, B-, AB-, O- |

Warnings shown to staff (none if the list is empty):


## Platelet concentrate

Shelf life 5 days; stored at 20 to 24 °C with continuous agitation.

| Donor \ Recipient | O- | O+ | A- | A+ | B- | B+ | AB- | AB+ |
|---|---|---|---|---|---|---|---|---|
| **O-** | = | C | W | W | W | W | W | W |
| **O+** | W | = | W | W | W | W | W | W |
| **A-** | W | W | = | C | W | W | W | W |
| **A+** | W | W | W | = | W | W | W | W |
| **B-** | W | W | W | W | = | C | W | W |
| **B+** | W | W | W | W | W | = | W | W |
| **AB-** | W | W | W | W | W | W | = | C |
| **AB+** | W | W | W | W | W | W | W | = |

Order in which donor groups are tried for each recipient (first = preferred):

| Recipient | Donor groups tried |
|---|---|
| O- | O-, A+, B+, AB+, O+, A-, B-, AB- |
| O+ | O+, O-, A+, B+, AB+, A-, B-, AB- |
| A- | A-, A+, B+, AB+, O+, B-, AB-, O- |
| A+ | A+, A-, B+, AB+, O+, B-, AB-, O- |
| B- | B-, A+, B+, AB+, O+, A-, AB-, O- |
| B+ | B+, B-, A+, AB+, O+, A-, AB-, O- |
| AB- | AB-, A+, B+, AB+, O+, A-, B-, O- |
| AB+ | AB+, AB-, A+, B+, O+, A-, B-, O- |

Warnings shown to staff (none if the list is empty):

- O- to A-: ABO-non-identical platelets (O- for A-); ABO-identical preferred
- O- to A+: ABO-non-identical platelets (O- for A+); ABO-identical preferred
- O- to B-: ABO-non-identical platelets (O- for B-); ABO-identical preferred
- O- to B+: ABO-non-identical platelets (O- for B+); ABO-identical preferred
- O- to AB-: ABO-non-identical platelets (O- for AB-); ABO-identical preferred
- O- to AB+: ABO-non-identical platelets (O- for AB+); ABO-identical preferred
- O+ to O-: RhD-positive platelets for an RhD-negative recipient: consider anti-D prophylaxis
- O+ to A-: ABO-non-identical platelets (O+ for A-); ABO-identical preferred; RhD-positive platelets for an RhD-negative recipient: consider anti-D prophylaxis
- O+ to A+: ABO-non-identical platelets (O+ for A+); ABO-identical preferred
- O+ to B-: ABO-non-identical platelets (O+ for B-); ABO-identical preferred; RhD-positive platelets for an RhD-negative recipient: consider anti-D prophylaxis
- O+ to B+: ABO-non-identical platelets (O+ for B+); ABO-identical preferred
- O+ to AB-: ABO-non-identical platelets (O+ for AB-); ABO-identical preferred; RhD-positive platelets for an RhD-negative recipient: consider anti-D prophylaxis
- O+ to AB+: ABO-non-identical platelets (O+ for AB+); ABO-identical preferred
- A- to O-: ABO-non-identical platelets (A- for O-); ABO-identical preferred
- A- to O+: ABO-non-identical platelets (A- for O+); ABO-identical preferred
- A- to B-: ABO-non-identical platelets (A- for B-); ABO-identical preferred
- A- to B+: ABO-non-identical platelets (A- for B+); ABO-identical preferred
- A- to AB-: ABO-non-identical platelets (A- for AB-); ABO-identical preferred
- A- to AB+: ABO-non-identical platelets (A- for AB+); ABO-identical preferred
- A+ to O-: ABO-non-identical platelets (A+ for O-); ABO-identical preferred; RhD-positive platelets for an RhD-negative recipient: consider anti-D prophylaxis
- A+ to O+: ABO-non-identical platelets (A+ for O+); ABO-identical preferred
- A+ to A-: RhD-positive platelets for an RhD-negative recipient: consider anti-D prophylaxis
- A+ to B-: ABO-non-identical platelets (A+ for B-); ABO-identical preferred; RhD-positive platelets for an RhD-negative recipient: consider anti-D prophylaxis
- A+ to B+: ABO-non-identical platelets (A+ for B+); ABO-identical preferred
- A+ to AB-: ABO-non-identical platelets (A+ for AB-); ABO-identical preferred; RhD-positive platelets for an RhD-negative recipient: consider anti-D prophylaxis
- A+ to AB+: ABO-non-identical platelets (A+ for AB+); ABO-identical preferred
- B- to O-: ABO-non-identical platelets (B- for O-); ABO-identical preferred
- B- to O+: ABO-non-identical platelets (B- for O+); ABO-identical preferred
- B- to A-: ABO-non-identical platelets (B- for A-); ABO-identical preferred
- B- to A+: ABO-non-identical platelets (B- for A+); ABO-identical preferred
- B- to AB-: ABO-non-identical platelets (B- for AB-); ABO-identical preferred
- B- to AB+: ABO-non-identical platelets (B- for AB+); ABO-identical preferred
- B+ to O-: ABO-non-identical platelets (B+ for O-); ABO-identical preferred; RhD-positive platelets for an RhD-negative recipient: consider anti-D prophylaxis
- B+ to O+: ABO-non-identical platelets (B+ for O+); ABO-identical preferred
- B+ to A-: ABO-non-identical platelets (B+ for A-); ABO-identical preferred; RhD-positive platelets for an RhD-negative recipient: consider anti-D prophylaxis
- B+ to A+: ABO-non-identical platelets (B+ for A+); ABO-identical preferred
- B+ to B-: RhD-positive platelets for an RhD-negative recipient: consider anti-D prophylaxis
- B+ to AB-: ABO-non-identical platelets (B+ for AB-); ABO-identical preferred; RhD-positive platelets for an RhD-negative recipient: consider anti-D prophylaxis
- B+ to AB+: ABO-non-identical platelets (B+ for AB+); ABO-identical preferred
- AB- to O-: ABO-non-identical platelets (AB- for O-); ABO-identical preferred
- AB- to O+: ABO-non-identical platelets (AB- for O+); ABO-identical preferred
- AB- to A-: ABO-non-identical platelets (AB- for A-); ABO-identical preferred
- AB- to A+: ABO-non-identical platelets (AB- for A+); ABO-identical preferred
- AB- to B-: ABO-non-identical platelets (AB- for B-); ABO-identical preferred
- AB- to B+: ABO-non-identical platelets (AB- for B+); ABO-identical preferred
- AB+ to O-: ABO-non-identical platelets (AB+ for O-); ABO-identical preferred; RhD-positive platelets for an RhD-negative recipient: consider anti-D prophylaxis
- AB+ to O+: ABO-non-identical platelets (AB+ for O+); ABO-identical preferred
- AB+ to A-: ABO-non-identical platelets (AB+ for A-); ABO-identical preferred; RhD-positive platelets for an RhD-negative recipient: consider anti-D prophylaxis
- AB+ to A+: ABO-non-identical platelets (AB+ for A+); ABO-identical preferred
- AB+ to B-: ABO-non-identical platelets (AB+ for B-); ABO-identical preferred; RhD-positive platelets for an RhD-negative recipient: consider anti-D prophylaxis
- AB+ to B+: ABO-non-identical platelets (AB+ for B+); ABO-identical preferred
- AB+ to AB-: RhD-positive platelets for an RhD-negative recipient: consider anti-D prophylaxis

## Fresh frozen plasma

Shelf life 365 days; stored at -30 °C or colder; use within 6 hours of thawing.

| Donor \ Recipient | O- | O+ | A- | A+ | B- | B+ | AB- | AB+ |
|---|---|---|---|---|---|---|---|---|
| **O-** | = | = | . | . | . | . | . | . |
| **O+** | = | = | . | . | . | . | . | . |
| **A-** | C | C | = | = | . | . | . | . |
| **A+** | C | C | = | = | . | . | . | . |
| **B-** | C | C | . | . | = | = | . | . |
| **B+** | C | C | . | . | = | = | . | . |
| **AB-** | C | C | C | C | C | C | = | = |
| **AB+** | C | C | C | C | C | C | = | = |

Order in which donor groups are tried for each recipient (first = preferred):

| Recipient | Donor groups tried |
|---|---|
| O- | O+, O-, A+, B+, AB+, A-, B-, AB- |
| O+ | O+, O-, A+, B+, AB+, A-, B-, AB- |
| A- | A+, A-, AB+, AB- |
| A+ | A+, A-, AB+, AB- |
| B- | B+, B-, AB+, AB- |
| B+ | B+, B-, AB+, AB- |
| AB- | AB+, AB- |
| AB+ | AB+, AB- |

Warnings shown to staff (none if the list is empty):

