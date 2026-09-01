#!/usr/bin/env bash
#
# Fails when this repository's copy of VoteChanged.avsc differs from nabat-voting's.
#
# nabat-voting owns that schema: it produces the messages, its build holds the version history
# and the compatibility test, and its registration is what a registry accepts or refuses. This
# repository only reads the topic, so its copy exists to generate a class from — and a copy is
# a thing that goes stale.
#
# The registry catches a divergence at runtime, which is late and only in an environment that
# has one. This catches it in a build.
#
# Reads the source from the sibling checkout when there is one, which is the usual case on a
# developer's machine and works offline, and from GitHub otherwise — the case in CI, where only
# this repository is checked out.
set -euo pipefail

LOCAL_COPY="src/main/avro/VoteChanged.avsc"
SIBLING_SOURCE="../nabat-voting/src/main/avro/VoteChanged.avsc"
REMOTE_SOURCE="https://raw.githubusercontent.com/martog232/nabat-voting/main/src/main/avro/VoteChanged.avsc"

if [[ ! -f "$LOCAL_COPY" ]]; then
  echo "$LOCAL_COPY is missing — nothing generates the event class without it." >&2
  exit 1
fi

source_description=""
if [[ -f "$SIBLING_SOURCE" ]]; then
  source_file="$SIBLING_SOURCE"
  source_description="$SIBLING_SOURCE"
else
  source_file="$(mktemp)"
  trap 'rm -f "$source_file"' EXIT
  if ! curl -fsS "$REMOTE_SOURCE" -o "$source_file"; then
    echo "Could not read the schema from $REMOTE_SOURCE, and there is no sibling checkout." >&2
    exit 1
  fi
  source_description="$REMOTE_SOURCE"
fi

# Compared as parsed JSON with sorted keys: key order and indentation are not the contract, and
# a reformatted file is not a schema change.
#
# Whichever tool is present, and a hard failure when none is — the first version of this fell
# back through python3 and jq, had neither, and compared two empty strings. It reported a match
# for every possible pair of files, which is worse than no check at all.
if command -v node >/dev/null 2>&1; then
  normalise() {
    node -e 'const fs=require("fs");const s=o=>Array.isArray(o)?o.map(s):(o&&typeof o==="object"?Object.fromEntries(Object.keys(o).sort().map(k=>[k,s(o[k])])):o);process.stdout.write(JSON.stringify(s(JSON.parse(fs.readFileSync(process.argv[1],"utf8")))))' "$1"
  }
elif command -v jq >/dev/null 2>&1; then
  normalise() { jq -S -c . "$1"; }
elif command -v python3 >/dev/null 2>&1; then
  normalise() {
    python3 -c 'import json,sys; print(json.dumps(json.load(open(sys.argv[1])), sort_keys=True))' "$1"
  }
else
  echo "Need node, jq or python3 to compare the schemas; none is on PATH." >&2
  exit 1
fi

local_normalised="$(normalise "$LOCAL_COPY")"
source_normalised="$(normalise "$source_file")"

if [[ -z "$local_normalised" || -z "$source_normalised" ]]; then
  echo "One of the schemas normalised to nothing — refusing to report a match." >&2
  exit 1
fi

if [[ "$local_normalised" == "$source_normalised" ]]; then
  echo "$LOCAL_COPY matches $source_description"
  exit 0
fi

cat >&2 <<EOF
$LOCAL_COPY differs from $source_description.

nabat-voting owns this schema. Copy its version over rather than editing this one:

  cp $SIBLING_SOURCE $LOCAL_COPY

If the change is intentional, it belongs in nabat-voting first — that is where the
compatibility test and the version history live, and where a breaking change has to be
caught.
EOF
exit 1
