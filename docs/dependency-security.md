# Java Dependency Security Scan

The `security` Maven profile runs OWASP Dependency-Check across the multi-module reactor:

```powershell
$env:JAVA_HOME = 'C:\Users\NJHTR\.jdks\ms-21.0.11'
& .\mvnw.cmd -B -ntp -Psecurity verify -DskipTests
```

The scan fails the build for vulnerabilities with CVSS 7.0 or higher. The local NVD database is stored under
`.security/` and is ignored by Git because it is a generated cache. Reports are written to
`target/dependency-check/`; CI uploads that directory and publishes SARIF for trusted repository events. Set the
repository secret `NVD_API_KEY` to reduce NVD rate limiting; the scan remains configured to work without it, subject
to NVD availability and slower updates.

## Exception policy

Do not suppress a finding merely to make CI green. A release exception must record:

- dependency and affected version;
- CVE/CVSS and reachable code path;
- mitigation or compensating control;
- owner and expiration date;
- target upgrade release.

Expired exceptions fail the release review. A dependency with a critical or high finding must be upgraded, removed,
isolated or explicitly approved by the security owner before production deployment.
