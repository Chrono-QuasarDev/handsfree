#!/usr/bin/env bash
set -euo pipefail

MERGED_MANIFEST=""
if [ -d "app/build/intermediates/merged_manifests" ]; then
    MERGED_MANIFEST=$(find app/build/intermediates/merged_manifests -name "AndroidManifest.xml" | head -n 1 || true)
fi

if [ -n "$MERGED_MANIFEST" ] && [ -f "$MERGED_MANIFEST" ]; then
    MANIFEST="$MERGED_MANIFEST"
else
    if [ -n "${CI:-}" ] && { [ -z "$MERGED_MANIFEST" ] || [ ! -f "$MERGED_MANIFEST" ]; }; then
        echo "ERROR: merged manifest not found in CI — refusing to fall back to the source manifest"
        exit 1
    fi
    MANIFEST="app/src/main/AndroidManifest.xml"
fi

echo "Auditing permissions in: $MANIFEST"

PERMS=$(grep -oE 'uses-permission(-sdk-23)?[^>]*android:name="[^"]+"' "$MANIFEST" | sed -E 's/.*android:name="([^"]+)"/\1/' || true)

FAILED=0
for perm in $PERMS; do
    if [ "$perm" != "android.permission.INTERNET" ] && [ "$perm" != "com.handsfree.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION" ]; then
        echo "ERROR: Forbidden permission found: $perm"
        FAILED=1
    fi
done

if [ "$FAILED" -ne 0 ]; then
    exit 1
fi

echo "OK: Only allowed permission(s) present."
