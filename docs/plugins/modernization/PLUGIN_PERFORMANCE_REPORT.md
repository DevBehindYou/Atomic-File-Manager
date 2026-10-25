# Performance evidence

26October2026: no valid new modernization performance measurements. Debug test duration is not a user baseline; no speedup/p95/p99/memory/battery claim.

Required: exact source/build/ABI/API/device, release-like compilation/profile, input dimensions/pages/bytes/codecs/entries, cache/thermal state/trials/distribution, first content/frame/jank/RAM/CPU as relevant and APK/AAB/installed-size delta. Keep bounded metadata-on-demand/workers/decodes/staging/cancellation.

Workloads: Image4K/8K/panorama/longanimation/thousands-navigation; video seek/sync/tracks; audio start/background; PDF1000+pages/mixed sizes; editor longlines/largereadonly; Markdown AST/assets; document XML/assets; archives throughput/cancel/bombs. Pair with correctness/provider failures. Deferred benchmark permissions unchanged.