#!/usr/bin/env bash
# Build reproducible Android CPython 3.13 parser wheels on cache miss.
# Never download artifacts from other Actions runs. Frozen parser sources untouched.
set -euo pipefail
ROOT="$PWD/android-native/app/python-wheels"
mkdir -p "$ROOT"
audit_wheels() {
  python3 - "$ROOT" <<'PY'
from pathlib import Path
from zipfile import ZipFile
import re, sys
root = Path(sys.argv[1])
wheels = sorted(root.glob("*.whl"))
assert len(wheels) == 4, f"Expected four native wheels, found {len(wheels)}"
for name, version in (("pydantic_core", "2.41.5"), ("pypdfium2", "5.14.0")):
    for abi in ("arm64_v8a", "x86_64"):
        matches = [p for p in wheels if p.name.lower().startswith(f"{name}-{version}-")
                   and re.search(r"android_\d+_" + abi + r"\.whl$", p.name)]
        assert len(matches) == 1, (name, version, abi, matches)
        with ZipFile(matches[0]) as archive:
            items = archive.namelist()
            assert any(".dist-info/WHEEL" in s for s in items)
            assert any(s.endswith(".so") for s in items), matches[0]
PY
  (cd "$ROOT" && sha256sum --check SHA256SUMS)
}
if [[ -f "$ROOT/SHA256SUMS" ]]; then
  audit_wheels
  echo "WHEELS_CACHE_VERIFIED=4"
  exit 0
fi
rm -f "$ROOT"/*.whl
python -m pip install --disable-pip-version-check 'cibuildwheel==3.4.0'
rustup target add aarch64-linux-android x86_64-linux-android
mkdir -p upstream
git clone --filter=blob:none https://github.com/pydantic/pydantic-core.git upstream/pydantic-core
git -C upstream/pydantic-core checkout --detach 52b821dfc304b061b3c2d528f91011229d25f0a1
test "$(git -C upstream/pydantic-core rev-parse HEAD)" = 52b821dfc304b061b3c2d528f91011229d25f0a1
CIBW_BUILD='cp313-*' CIBW_ARCHS_ANDROID='arm64_v8a x86_64' \
CIBW_BUILD_FRONTEND=build ANDROID_API_LEVEL=26 \
python -m cibuildwheel upstream/pydantic-core --platform android --output-dir "$ROOT"
git clone --filter=blob:none https://github.com/pypdfium2-team/pypdfium2.git upstream/pypdfium2
git -C upstream/pypdfium2 checkout --detach 21f85e273036f3bade4f67d896e1d05927e5f4b1
test "$(git -C upstream/pypdfium2 rev-parse HEAD)" = 21f85e273036f3bade4f67d896e1d05927e5f4b1
CIBW_BUILD='cp313-*' CIBW_ARCHS_ANDROID=x86_64 CIBW_BUILD_FRONTEND=build ANDROID_API_LEVEL=26 \
CIBW_BEFORE_BUILD='python -m pip install "setuptools>=70.1.0" "wheel!=0.38.0,!=0.38.1" "build==1.3.0" packaging' \
CIBW_TEST_COMMAND='python -c "import pypdfium2; import pypdfium2.raw; print(123)"' \
python -m cibuildwheel upstream/pypdfium2 --platform android --output-dir "$ROOT"
python -m pip download --no-deps --only-binary=:all: \
  --platform android_23_arm64_v8a --python-version 3.13 \
  --implementation py --abi none --dest "$ROOT" 'pypdfium2==5.14.0'
(cd "$ROOT" && sha256sum -- *.whl > SHA256SUMS)
audit_wheels
echo "WHEELS_SOURCE_BUILD_VERIFIED=4"
