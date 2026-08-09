# T070 Aggregation, Final Review, and Export

## Objective

Aggregate parallel results, detect conflicts, map evidence into the canonical document, run final completeness and safety reviews, and export review-gated DOCX/PDF.

## Invariants

No unsupported conclusion is written into the release. Every output references code, data, run, and evaluation versions. Export is impossible before the final gate.

## Tests and integration

Test missing evidence, conflicting metrics, partial completion, unsafe artifact, export retry, and tenant-scoped download.

## Done when

The exported file is reproducible from recorded source, plan, runs, reviews, and artifacts.
