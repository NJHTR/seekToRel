# Work Item Schema

Every task board entry must be representable by these fields:

```yaml
id: NEXUS-101
project: nexus
title: Durable task-event history
owner_group: backend
decision_owner: CTO
problem: User cannot reconstruct work after refresh
outcome_metric: history_reconnect_success_rate
scope:
  in: [postgres_event_store, bounded_history_api]
  out: [cross-region_event_streaming]
dependencies: []
data_classification: tenant-confidential
acceptance:
  - persisted events survive process restart
  - unauthorized tenant receives the same bounded not-found response
verification: [unit, integration, api-contract]
risk_ids: [R-002, R-004]
state: READY
```

The YAML is a documentation example, not a runtime configuration file. The
actual task system remains the repository/project tracker selected by the team;
this schema keeps its minimum information consistent.
