#!/usr/bin/env bash
# Builds the AGNTCon app and uploads it to TestFlight. This repository is where the app is published from.
#
#   scripts/testflight.sh            archive only
#   scripts/testflight.sh --upload   archive and upload to TestFlight
#
# Needs ASC_KEY_ID and ASC_ISSUER_ID for the upload (App Store Connect API key, see ios-testflight.sh).
set -euo pipefail
exec "$(dirname "$0")/ios-testflight.sh" agntcon-mcpcon-na-2026 com.muschenetz.agntcon AGNTCon 4M7MX9FB66 "$@"
