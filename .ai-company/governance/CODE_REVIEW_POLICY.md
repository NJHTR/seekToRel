# Code Review Policy

Every change goes through four lenses before merge:

1. **Design:** requirement, ownership boundary, API/data compatibility and ADR.
2. **Implementation:** correctness, readability, failure handling, concurrency,
   observability and test quality.
3. **Security:** authorization, tenant isolation, input limits, secrets,
   injection/SSRF/path traversal risks and dependency changes.
4. **Operations:** migration safety, rollback, metrics, alerts, resource bounds
   and deployment impact.

The author supplies a short change note and verification evidence. The reviewer
must identify findings by severity (`P0` release blocker, `P1` high risk, `P2`
normal, `P3` polish) and either request changes or approve explicitly. “Looks
good” without evidence is not an approval.

The author cannot self-approve a release-blocking security or data migration
change. At least one reviewer must be independent of the implementation team;
security-sensitive changes require the security role as an additional approver.
