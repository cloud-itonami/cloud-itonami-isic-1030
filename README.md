# cloud-itonami-isic-1030

Open Business Blueprint and working actor for **ISIC 1030**: processing
and preserving of fruit and vegetables — the production/food-manufacturing
(食) vertical of the 衣食住 scaffold batch (ADR-2607122200), Wave 3.

**Maturity: `:implemented`** — this repository provides a full working
actor: FruitVegetableOps-LLM with independent Governor, langgraph-clj
StateGraph, append-only audit ledger, and production-ready test suite.
Part of Wave 3 (production/robotics) rollout (ADR-2607121000). ISIC
division 10-12 (food) requires the robotics premise gate (ADR-2607011000)
— a real robot fleet and certified operator sign-off infrastructure.

## The implemented actor

**FruitVegetableOps-LLM ⊣ Fruit & Vegetable Processing Governor** — the
fleet-standard pattern: the advisor LLM drafts production schedules,
cold-chain monitoring, pest residue/spoilage assessments, and
harvest-to-shelf traceability; the independent
`:fruit-vegetable-processing-governor` (a keyword unique fleet-wide) gates
every action; physical-domain work (sorting, washing, chilling, packing)
is executed by robots under `kotoba-lang/robotics` safety classes, never
by the LLM directly. Food-safety-critical actions (contamination flags,
residue concerns) require human operator sign-off before commitment.

Operating states: `intake → schedule → process → inspect → package → audit`.

## Scope

Plant-operations coordination actor: back-office workflow support for
produce-processing plants. The actor supports batch logging, maintenance
scheduling, food-safety concern escalation, and shipment coordination.
DOES NOT directly control equipment or make food-safety certifications
(those remain exclusive to plant operators and regulatory bodies).

## Why open

AGPL-3.0-or-later, forkable by any qualified operator, so local food
processors retain full control of production and traceability data.
Part of the [cloud-itonami](https://itonami.cloud) open business fleet.
