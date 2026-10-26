#!/usr/bin/env bash
set -euo pipefail
mkdir -p beta-smoke
release_id=$(python3 -c 'import json; print(json.load(open("scripts/release/beta-config.json"))["releaseId"])')
gh api "repos/$GITHUB_REPOSITORY/releases/$release_id" > beta-smoke/release.json
python3 - <<'PY'
import json, os
c=json.load(open('scripts/release/beta-config.json'))
r=json.load(open('beta-smoke/release.json'))
if r['id'] != c['releaseId'] or r['tag_name'] != c['tag'] or not r['draft'] or not r['prerelease']:
    raise SystemExit('Only the configured unpublished beta draft may be tested')
if r['target_commitish'] != os.environ['GITHUB_SHA']:
    raise SystemExit('Draft release does not identify this exact source SHA')
allowed={c['apkName'], 'BETA_MANIFEST.json', 'SHA256SUMS.txt', 'SIGNING_CERTIFICATE_SHA256.txt'}
assets=r['assets']
if len(assets) != len(allowed) or {a['name'] for a in assets} != allowed:
    raise SystemExit('Draft must contain exactly four explicitly public assets, without duplicates or extras')
with open('beta-smoke/assets.tsv', 'w') as out:
    for a in assets:
        if a['state'] != 'uploaded' or a['size'] <= 0 or not isinstance(a['id'], int) or a['id'] <= 0:
            raise SystemExit('Draft asset is not fully uploaded')
        out.write(f'{a["id"]}\t{a["name"]}\n')
PY
while IFS=$'\t' read -r asset_id asset_name; do
  gh api -H 'Accept: application/octet-stream' \
    "repos/$GITHUB_REPOSITORY/releases/assets/$asset_id" > "beta-smoke/$asset_name"
done < beta-smoke/assets.tsv
apk=$(python3 -c 'import json; print(json.load(open("scripts/release/beta-config.json"))["apkName"])')
build_tools=$(find "$ANDROID_HOME/build-tools" -mindepth 1 -maxdepth 1 -type d | sort -V | tail -n 1)
"$build_tools/apksigner" verify --verbose --print-certs "beta-smoke/$apk" > beta-smoke/apksigner.txt
"$build_tools/aapt" dump badging "beta-smoke/$apk" > beta-smoke/badging.txt
python3 scripts/release/verify_beta.py --directory beta-smoke --source-sha "$GITHUB_SHA"
