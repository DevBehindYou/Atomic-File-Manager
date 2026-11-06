#!/usr/bin/env bash
set -euo pipefail

case "${1:-comparison}" in
  comparison) modes=(normal isolated) ;;
  full-trace) modes=(full-trace) ;;
  *) printf 'Usage: %s [comparison|full-trace]\n' "$0" >&2; exit 2 ;;
esac

# Always run from the repository, and create evidence only inside its ignored build directory.
cd "$(dirname "${BASH_SOURCE[0]}")/../.."
evidence="$PWD/build/native-sqlite-diagnostic"
mkdir -p "$evidence"
{
  printf 'source='; git rev-parse HEAD
  printf 'runner OS: '; uname -a
  printf 'JDK:\n'; java -version 2>&1
  printf 'system: '; cat /etc/os-release
  printf '\nwrapper:\n'; cat gradle/wrapper/gradle-wrapper.properties
} > "$evidence/environment.txt"

filters=(
  --tests 'com.devbehindyou.atomicfilemanager.data.database.HiddenFilesDatabaseHelperTest'
  --tests 'com.devbehindyou.atomicfilemanager.data.database.TransferBubbleDatabaseHelperTest'
  --tests 'com.devbehindyou.atomicfilemanager.data.database.room.AtomicDatabaseMigrationTest'
  --tests 'com.devbehindyou.atomicfilemanager.data.database.room.OperationJournalTest'
  --tests 'com.devbehindyou.atomicfilemanager.data.database.room.SearchIndexReconciliationTest'
  --tests 'com.devbehindyou.atomicfilemanager.domain.repository.TransferBubbleRepositoryTest'
)
failed=0
for mode in "${modes[@]}"; do
  destination="$evidence/$mode"
  mkdir -p "$destination"
  date -u +'%Y-%m-%dT%H:%M:%SZ' > "$destination/started-at.txt"
  gradle_args=(--console=plain --info --stacktrace)
  test_filters=()
  if [[ "$mode" != full-trace ]]; then
    gradle_args+=(--no-daemon --max-workers=1)
    test_filters=("${filters[@]}")
  fi
  # Keep collecting the requested evidence if a comparison leg fails.
  set +e
  ./gradlew "${gradle_args[@]}" \
    -I scripts/diagnostics/native-sqlite.init.gradle \
    "-Datomic.sqlite.diagnostic.mode=$mode" \
    :app:testDebugUnitTest "${test_filters[@]}" 2>&1 | tee "$destination/gradle.log"
  pipeline=("${PIPESTATUS[@]}")
  result=${pipeline[0]}
  log_result=${pipeline[1]}
  set -e
  printf '%s\n' "$result" > "$destination/exit-code.txt"
  if [[ "$result" -ne 0 || "$log_result" -ne 0 ]]; then failed=1; fi
  # Init script assigns fresh per-mode html/xml destinations, avoiding stale report reuse.
  date -u +'%Y-%m-%dT%H:%M:%SZ' > "$destination/completed-at.txt"
done
for mode in "${modes[@]}"; do
  printf '%s=%s\n' "$mode" "$(cat "$evidence/$mode/exit-code.txt")"
done > "$evidence/outcomes.txt"
exit "$failed"
