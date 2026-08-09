param(
    [string]$Tag = "reportai/python-scientific:local",
    [string]$PythonBaseDigest = "sha256:57cd7c3a7a273101a6485ba99423ee568157882804b1124b4dd04266317710de"
)

$ErrorActionPreference = "Stop"
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\.."))
$imageDir = Join-Path $repoRoot "runtime-images\scientific"
$wheelhouse = Join-Path $imageDir "wheelhouse"
$lockFile = Join-Path $imageDir "requirements.lock"
$pytorchCpuIndex = "https://download.pytorch.org/whl/cpu"

if (-not (Test-Path (Join-Path $imageDir "requirements.in"))) {
    throw "requirements.in is missing"
}

New-Item -ItemType Directory -Force -Path $wheelhouse | Out-Null

# Resolve in an isolated builder so the host does not supply packages or pip
# configuration. pip-tools writes transitive pins and hashes.
docker run --rm -v "${imageDir}:/work" python:3.12-slim sh -c `
    "python -m pip install --disable-pip-version-check --no-cache-dir pip-tools==7.4.1 && cd /work && rm -f wheelhouse/*.whl requirements.lock requirements.resolved.lock dependency-report.json catalog-dependencies.json && pip-compile --allow-unsafe --generate-hashes --extra-index-url=$pytorchCpuIndex --output-file=requirements.resolved.lock requirements.in && python -m pip download --only-binary=:all: --require-hashes --extra-index-url=$pytorchCpuIndex --dest=wheelhouse -r requirements.resolved.lock && python -m pip install --disable-pip-version-check --no-cache-dir --ignore-installed --no-index --find-links=wheelhouse --dry-run --report=dependency-report.json -r requirements.resolved.lock && python generate_runtime_lock.py --dependency-report dependency-report.json"

$hasWheel = $null -ne (Get-ChildItem $wheelhouse -File | Select-Object -First 1)
if ((-not (Test-Path $lockFile)) -or (-not (Test-Path (Join-Path $imageDir "catalog-dependencies.json"))) -or (-not $hasWheel)) {
    throw "scientific lock file, dependency graph or wheelhouse was not generated"
}

docker build --pull=false --build-arg "PYTHON_BASE_DIGEST=$PythonBaseDigest" -t $Tag $imageDir
Write-Host "Built $Tag. Push it to the trusted registry before recording a repo@sha256 digest in the signed catalog."
