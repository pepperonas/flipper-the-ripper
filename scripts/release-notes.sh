#!/usr/bin/env bash
# Prints the CHANGELOG.md section for one release, so the GitHub release page carries the notes
# that are actually written — instead of the one-line "Full Changelog" link it showed until 1.8.3.
#
#   scripts/release-notes.sh v1.9.0            # section "## [1.9.0] - …", body only
#   scripts/release-notes.sh v1.9.0 CHANGELOG.md
#
# Exits 1 with a message on stderr when the tag has no section: a release without notes is then a
# red workflow rather than an empty page nobody notices.
set -euo pipefail

tag="${1:?usage: release-notes.sh vX.Y.Z [CHANGELOG.md]}"
file="${2:-CHANGELOG.md}"
version="${tag#v}"

# From the matching "## [version]" heading (exclusive) to the next "## " heading (exclusive).
# awk rather than sed: the heading is matched as a fixed string, not a pattern, so a version like
# 1.9.0 cannot accidentally match 1.9.01.
section=$(awk -v h="## [$version] " '
  index($0, h) == 1 { grab = 1; next }
  grab && /^## / { exit }
  grab { print }
' "$file")

if [ -z "$(printf '%s' "$section" | tr -d '[:space:]')" ]; then
  echo "release-notes: no section '## [$version]' in $file" >&2
  exit 1
fi

# Trim leading/trailing blank lines.
printf '%s\n' "$section" | sed -e :a -e '/./,$!d;/^\n*$/{$d;N;ba' -e '}'
