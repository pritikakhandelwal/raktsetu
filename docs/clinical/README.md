# Clinical review of the transfusion rules

RaktSetu decides which blood units may go to which patients, and in what order it tries them. Before any clinical use,
a transfusion medicine specialist must check those rules. This folder is what they review.

| File | What it is |
|---|---|
| [compatibility-matrix.md](compatibility-matrix.md) | Every donor group against every recipient group for each component, the order donor groups are tried, and every warning staff see. **Generated from the code**: the build fails if it and the code disagree. |
| This file | Scope, where each rule lives in the code, the questions the reviewer must answer, and the sign-off record. |

## 1. Scope: what RaktSetu does and does not decide

RaktSetu is decision support for **stock**: which centre should send which units, and which units are at risk of
expiring. It does not replace any step of pre-transfusion testing.

- **It does not:** confirm the patient's ABO and RhD group, screen for antibodies, crossmatch, or check special
  requirements (irradiated, leucodepleted or CMV-negative units, neonatal rules). The issuing blood centre must still
  do all of these before a unit is given.
- **It assumes:** the patient's group entered by hospital staff is correct, and every unit was tested and labelled
  correctly when it was collected.
- **It is limited to:** the four components below and the eight ABO/RhD groups. Rare phenotypes (for example Bombay
  Oh) and other blood group systems are not represented (see question Q7).

## 2. The rules and where they are in the code

| Rule | Code | Tests |
|---|---|---|
| Red cells: the donor must not carry an ABO antigen the recipient lacks; RhD-negative recipients get RhD-negative cells only | `BloodGroup.canGiveRedCellsTo`, `RedCellConcentrate.compatibilityWith` | `CompatibilityTest` |
| Plasma: the reverse of red cells (AB is the universal plasma donor); RhD ignored | `BloodGroup.canGivePlasmaTo`, `FreshFrozenPlasma.compatibilityWith` | `CompatibilityTest` |
| Whole blood: group-identical only (ABO and RhD) | `WholeBlood.compatibilityWith` | `CompatibilityTest` |
| Platelets: ABO-identical preferred; every other group allowed with a warning; RhD-positive to RhD-negative warns about anti-D | `Platelets.compatibilityWith` | `CompatibilityTest` |
| Order donor groups are tried: safest level first, then the most plentiful group, keeping O-negative for last | `DonorPreference.forRecipient` | `RequestServiceTest` |
| Shelf lives: whole blood 35 days, red cells 42, platelets 5, plasma 365 | `getShelfLifeDays()` in each component class | `ExpiryTest` |
| An expired or incompatible unit is never issued, even when staff pick it by hand | `RequestService.issueUnit` | `RequestServiceTest` |
| Every issue of a non-identical group is written to the audit log with the reason | `RequestService.record` | `RequestServiceTest` |

## 3. Questions for the reviewer

Building the generated matrix raised these points. Each needs a clinical decision; the code will then be changed to
match, and the matrix regenerated.

| # | Question | What the code does now |
|---|---|---|
| Q1 | **Platelets for RhD-negative patients.** Should RhD-negative platelets always be tried before RhD-positive ones (at least for girls and women of childbearing potential)? | All non-identical platelets are equally "allowed with a warning" and are tried in order of how plentiful the group is. For an O-negative patient this tries A+, B+ and AB+ before A- and B-. |
| Q2 | **Platelets across ABO.** What order should ABO-non-identical platelets follow? For example, for a group A patient: AB, then O (plasma-incompatible), then B? Should group O platelets for non-O patients be limited to low-titre units, or to adults? | No distinction between major and minor ABO mismatch; order is by plentifulness. Antibody titre is not recorded. |
| Q3 | **Red cells for RhD-negative patients in an emergency.** When no RhD-negative red cells are available, may RhD-positive red cells be issued (for example to men, or to women past childbearing age) with clinician approval? | Never allowed. The request fails as unmet. |
| Q4 | **Whole blood.** Is group-identical (ABO and RhD) the right rule, or may RhD-negative whole blood go to an RhD-positive patient of the same ABO group? | Group-identical only. |
| Q5 | **Shelf lives and storage.** Do these match the Drugs and Cosmetics Rules, 1945 and your centre's SOPs? Red cells are assumed to be in SAGM (42 days); red cells in CPDA-1 last 35 days. Plasma is assumed to be at -30 °C or colder (1 year). | One shelf life per component; staff can enter a shorter expiry date for a unit. |
| Q6 | **Emergency O-negative.** Is keeping O-negative red cells as the last choice for non-O-negative patients the right policy? | O-negative is tried last for every other recipient. |
| Q7 | **Unrepresented patients.** How should the system handle patients with rare phenotypes (Bombay Oh), weak or partial D, known alloantibodies, or special requirements? | Not represented. A Bombay patient entered as group O would be offered group O red cells, which is unsafe. Proposed: a "special requirements" flag on the request that blocks automatic allocation and routes it to the blood centre. |
| Q8 | **Warning wording.** Are the warning texts in the matrix clear and correct for staff? | See the end of each component section in the matrix. |

## 4. Standards to review against

The reviewer should use the documents that govern their own centre. These are the usual references in India:

- The Drugs and Cosmetics Rules, 1945, provisions for blood centres and blood components.
- Directorate General of Health Services (DGHS), *Transfusion Medicine Technical Manual*.
- Guidelines from the National Blood Transfusion Council (NBTC).
- The reviewing hospital's own transfusion committee SOPs.

## 5. How a rule change is made

1. The reviewer records a decision against a question above (or a new one).
2. The rule is changed in the component class or `DonorPreference`, with a test for the decision.
3. The matrix is regenerated:
   `./mvnw -pl raktsetu-engine test -Dtest=ClinicalMatrixTest -Draktsetu.writeMatrix=true`
4. The pull request shows the change to `compatibility-matrix.md`, which the reviewer approves before it is merged.

## 6. Sign-off

| Date | Reviewer (name, qualification, institution) | Matrix version (commit) | Decision | Notes |
|---|---|---|---|---|
| | | | | |

Until this table has an approving entry, RaktSetu must be used only for demonstration, training and research, not for
issuing blood to patients.
