#!/usr/bin/env bash
set -euo pipefail

target=${1:?Pass the output directory}
mkdir -p "$target"
archive="$target/hdiffpatch-v4.12.2.zip"
curl --fail --location --silent --show-error --retry 3 \
  https://github.com/sisong/HDiffPatch/releases/download/v4.12.2/hdiffpatch_v4.12.2_bin_linux64.zip \
  --output "$archive"
printf '%s  %s\n' '432886341e6099adeb5223427454e9bc0b565f81edfbe2e3387f804611c07e4a' "$archive" \
  | sha256sum --check
unzip -o -j "$archive" '*/hdiffz' '*/hpatchz' -d "$target"
chmod +x "$target/hdiffz" "$target/hpatchz"
