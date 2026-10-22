Snapshots of real Sessionize API v2 "All" feeds (`https://sessionize.com/api/v2/{id}/view/All`),
taken 2026-10-22, with session descriptions, speaker bios, links and question answers removed.
Tests use them to check parsing, time zones, schedule grouping and the personal calendar against
real event structures:

- `agntcon-mcpcon-na-2026.json` — id `asqam7rn`, times in event-local time (America/Los_Angeles)
- `kotlinconf-2026.json` — id `2r0suvtg`, times in UTC (Europe/Berlin)

To refresh: download the feed, strip the same fields, keep the file name.
