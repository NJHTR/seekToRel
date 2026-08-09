# T090 Full Integration and Quality Gates

## Objective

Verify the complete system through module, contract, integration, replay, load, security, and failure-injection tests.

## Required gates

Build with the pinned Java/Maven toolchain, run focused and full tests, quality and dependency gates, persistence/outbox tests, sandbox tests, SSE replay tests, and a browser smoke path from upload to export.

## Performance and isolation

Measure bounded scheduler throughput, event fan-out, concurrent tenants, backpressure, object-storage latency, and Agent budget usage. Verify tenant isolation and no cross-run artifact access.

## Done when

The complete path is repeatable, observable, secure, load-tested, documented, and has no unowned TODOs in the constitution task set.
