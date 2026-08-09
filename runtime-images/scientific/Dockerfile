# Build only from a generated, hash-locked wheelhouse.  No package index is
# contacted while the image is built from requirements.lock.
ARG PYTHON_BASE_DIGEST=sha256:57cd7c3a7a273101a6485ba99423ee568157882804b1124b4dd04266317710de
FROM python:3.12-slim@${PYTHON_BASE_DIGEST}

ENV PIP_DISABLE_PIP_VERSION_CHECK=1 \
    PIP_NO_CACHE_DIR=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    PYTHONPATH=/opt/reportai/site-packages \
    MPLCONFIGDIR=/tmp/matplotlib

RUN groupadd --gid 65532 reportai \
    && useradd --uid 65532 --gid 65532 --create-home --shell /usr/sbin/nologin reportai \
    && mkdir -p /opt/reportai/site-packages /opt/reportai/wheels /workspace \
    && chown -R 65532:65532 /opt/reportai /workspace

COPY requirements.lock /opt/reportai/requirements.lock
COPY wheelhouse/ /opt/reportai/wheels/

RUN python -m pip install --isolated --no-index --find-links=/opt/reportai/wheels \
      --require-hashes --target=/opt/reportai/site-packages \
      -r /opt/reportai/requirements.lock \
    && python - <<'PY'
import matplotlib, numpy, PIL, torch, torchvision, yaml
print("reportai scientific runtime smoke test passed")
PY

USER 65532:65532
WORKDIR /workspace
