# Zappix Android Store

Android TV / Google TV app store built for D-pad remotes (native Views/XML).

Sections: Free, Subscription, Adult 18+, Tools, and Updates. Adult, Tools and Updates only
appear when they have apps.

Live API: `https://panelsandapps.com/panels/Zappix/api/`

Package: `com.zappix.store`

The app loads its catalog from `apps.php`, downloads the selected APK, verifies it, and hands it
to Android's system package installer. Zappix does not bypass Android install protections.

## Catalog fields (`apps.php`)

| Field | Required | Notes |
|---|---|---|
| `id`, `name`, `download_url` | yes | Entries missing these are skipped. |
| `package_name` | yes, to install | Must match the APK exactly. Must be unique across the catalog. |
| `version_code` | recommended | Drives the Update badge. The downloaded APK must be at least this version. |
| `sha256` | optional | If set, the downloaded file must match it (lowercase hex). |
| `app_type` | optional | `free` (default), `subscription`, `adult`, `tools`. |
| `description`, `icon_url`, `version_name`, `price_label` | optional | |

## Self-update (`update.php`)

`success`, `version_code`, `version_name`, `apk_url`, `message`, `required`, and optionally `sha256`.
`version_code` must be the Gradle `versionCode` (not derived from the version name), and the APK
at `apk_url` must really be that version, signed with the permanent Zappix key. Use a new URL
(or `?v=<versionCode>`) for every release so CDNs never serve an old file.

## Building

CI (`.github/workflows/build-apk.yml`) builds a release APK signed with the permanent key from
repository secrets. Build with `-PZAPPIX_ALLOW_HTTP_DOWNLOADS=false` once every catalog link is https.
