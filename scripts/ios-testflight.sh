#!/usr/bin/env bash
# Archives the iOS app for one conference and (optionally) uploads it to TestFlight.
#
#   scripts/ios-testflight.sh <event-id> <bundle-id> <display-name> <team-id> [--upload]
#   scripts/ios-testflight.sh agntcon-mcpcon-na-2026 com.muschenetz.agntcon AGNTCon 4M7MX9FB66 --upload
#
# The event id is a profile in app/shared/.../event/EventProfiles.kt. Signing is automatic with the
# Apple account signed in to Xcode; it registers the bundle id if needed. Uploading needs an app
# record with that bundle id in App Store Connect, and an App Store Connect API key (command-line
# uploads cannot use the Xcode account): set ASC_KEY_ID and ASC_ISSUER_ID, with the key at
# ~/.appstoreconnect/private_keys/AuthKey_<ASC_KEY_ID>.p8 (or ASC_KEY_PATH).
# Requires JDK 21 (JAVA_HOME or Homebrew openjdk@21).
set -euo pipefail

if [[ $# -lt 4 ]]; then
  sed -n '2,9p' "$0"
  exit 1
fi

EVENT=$1 BUNDLE_ID=$2 DISPLAY_NAME=$3 TEAM_ID=$4 UPLOAD=${5:-}
ROOT=$(cd "$(dirname "$0")/.." && pwd)
OUT=${OUT:-$ROOT/build/ios/$EVENT}
ARCHIVE=$OUT/$EVENT.xcarchive

if [[ -z "${JAVA_HOME:-}" || ! -x "$JAVA_HOME/bin/java" ]] || ! "$JAVA_HOME/bin/java" -version 2>&1 | grep -q '"21'; then
  JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
fi
export JAVA_HOME
# Read by the Gradle step inside the Xcode build: builds the Kotlin framework for this event
export ORG_GRADLE_PROJECT_event=$EVENT

mkdir -p "$OUT"

# Per-conference icon and launch screen, when the asset catalog has them
ASSETS="$ROOT/app/iosApp/iosApp/Assets.xcassets"
EVENT_SETTINGS=()
[[ -d "$ASSETS/AppIcon-$EVENT.appiconset" ]] && EVENT_SETTINGS+=(ASSETCATALOG_COMPILER_APPICON_NAME="AppIcon-$EVENT")
[[ -d "$ASSETS/$EVENT launch.imageset" ]] && EVENT_SETTINGS+=(EVENT_LAUNCH_IMAGE="$EVENT launch")
[[ -d "$ASSETS/$EVENT launch background.colorset" ]] && EVENT_SETTINGS+=(EVENT_LAUNCH_BACKGROUND="$EVENT launch background")

xcodebuild archive \
  -project "$ROOT/app/iosApp/KotlinConf.xcodeproj" \
  -scheme KotlinConfAppScheme \
  -configuration Release \
  -destination 'generic/platform=iOS' \
  -archivePath "$ARCHIVE" \
  -allowProvisioningUpdates \
  DEVELOPMENT_TEAM="$TEAM_ID" \
  CODE_SIGN_STYLE=Automatic \
  CODE_SIGN_IDENTITY="Apple Development" \
  PROVISIONING_PROFILE_SPECIFIER= \
  PRODUCT_BUNDLE_IDENTIFIER="$BUNDLE_ID" \
  INFOPLIST_KEY_CFBundleDisplayName="$DISPLAY_NAME" \
  ${EVENT_SETTINGS[@]+"${EVENT_SETTINGS[@]}"}

echo "Archive: $ARCHIVE"

[[ "$UPLOAD" == "--upload" ]] || exit 0

: "${ASC_KEY_ID:?Set ASC_KEY_ID (App Store Connect API key id) to upload}"
: "${ASC_ISSUER_ID:?Set ASC_ISSUER_ID (App Store Connect API issuer id) to upload}"
ASC_KEY_PATH=${ASC_KEY_PATH:-$HOME/.appstoreconnect/private_keys/AuthKey_$ASC_KEY_ID.p8}

cat > "$OUT/ExportOptions.plist" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>method</key><string>app-store-connect</string>
    <key>destination</key><string>upload</string>
    <key>teamID</key><string>$TEAM_ID</string>
    <key>signingStyle</key><string>automatic</string>
    <key>manageAppVersionAndBuildNumber</key><true/>
</dict>
</plist>
EOF

xcodebuild -exportArchive \
  -archivePath "$ARCHIVE" \
  -exportOptionsPlist "$OUT/ExportOptions.plist" \
  -exportPath "$OUT/export" \
  -allowProvisioningUpdates \
  -authenticationKeyPath "$ASC_KEY_PATH" \
  -authenticationKeyID "$ASC_KEY_ID" \
  -authenticationKeyIssuerID "$ASC_ISSUER_ID"
