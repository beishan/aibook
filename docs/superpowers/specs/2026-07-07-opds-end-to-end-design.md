# OPDS End-to-End Design

Date: 2026-07-07

## Goal

Make OPDS usable end to end for 汗牛充栋:

- Third-party OPDS 1.2 clients can authenticate, browse the private catalog, search, paginate, see covers when available, and download book files.
- The Android client can save an OPDS source, browse catalogs, recurse through catalog sections during sync, cache downloadable entries, and download books into the local library.
- OPDS 2.0 remains available as a JSON catalog for clients that support it, with correct response metadata and links.

This work focuses on OPDS catalog and download flows only. It does not include WebDAV, KOReader progress sync, annotations sync, or a new server-side OPDS source management UI.

## Current State

The backend already has:

- `OpdsController` under `/opds`.
- `OpdsService` for OPDS 1.2 Atom XML.
- `Opds2Service` for OPDS 2.0 JSON.
- `BasicAuthFilter` for `/opds`, `/webdav`, and `/api/sync`.
- `/opds/books/{id}/download` serving local book files.

The Android client already has:

- `OpdsCatalogService` for loading and downloading feeds.
- `OpdsFeedParser` for OPDS 1.2 Atom feeds.
- `OpdsConnectionRepository` for persisted source settings and sync state.
- `OpdsCatalogCacheRepository` for cached downloadable entries.
- `OpdsViewModel` for connection management, browsing, sync, and download actions.

The missing piece is hardening the existing pieces so they work reliably together and with real clients.

## Approach

Use the existing backend and Android architecture instead of creating parallel OPDS stacks.

Backend changes stay inside the existing OPDS controller/service/security surface. Android changes stay inside the existing OPDS network, repository, cache, and view model boundaries. Tests drive behavior before production code changes.

Recommended path:

1. Tighten backend OPDS compatibility.
2. Tighten Android parser, URL resolution, sync recursion, and download behavior.
3. Verify the full flow through tests and targeted manual commands.

Alternatives considered:

- Backend-only: fast, but Android remains only partly validated.
- Android-only: risks chasing client issues caused by server compatibility gaps.
- End-to-end: larger, but produces a feature users can actually connect to and download from. This is the chosen approach.

## Backend Design

### OPDS 1.2 Atom

`OpdsService` should continue to produce Atom XML manually, but the generated feeds need to be stable and client-friendly:

- Root feed contains `self`, `start`, and `search` links.
- Catalog entries use `rel="subsection"` for navigable sections.
- Publication entries use OPDS acquisition links with MIME types from `MimeTypeUtil`.
- Book entries include title, author, content or summary, updated time, cover image link, and thumbnail link when a cover URL exists.
- Pagination feeds include `self`, `start`, `next`, and `prev` as applicable.
- Search feed preserves the `query` parameter in pagination links.
- XML escaping covers all user-controlled string fields.

The OpenSearch description endpoint should be added at `/opds/search.xml` so clients can discover `/opds/search?query={searchTerms}`.

### OPDS 2.0 JSON

`Opds2Service` should continue returning maps serialized by Jackson, but controller responses should set `application/opds+json` explicitly.

Publication objects should include:

- `metadata.title`.
- Optional author, identifier, publisher, description, language, and rating.
- Acquisition links that point to the shared OPDS download endpoint.
- Cover links/images when available.

### Download

`OpdsController.downloadBook` should:

- Authorize access to the authenticated user's book only.
- Return 404 when the book or file is missing.
- Set the book MIME type.
- Set `Content-Length`.
- Set a content disposition that works for non-ASCII titles by including an RFC 5987 `filename*` value while keeping a simple fallback filename.

### Authentication

`BasicAuthFilter` already enforces Basic Auth on `/opds`. Tests should lock in:

- Unauthenticated `/opds` returns 401 with `WWW-Authenticate`.
- Valid Basic Auth reaches OPDS endpoints.
- Bearer token requests can continue to flow to JWT handling.

## Android Design

### Feed Loading

`OpdsCatalogService` remains the single network entry point:

- `load(connection, null)` fetches `connection.baseUrl`.
- `load(connection, href)` resolves relative and absolute links against the connection.
- Basic Auth header is attached when username and password are present.

`OpdsFeedParser` should parse the OPDS 1.2 fields needed for browsing and downloads:

- Feed title.
- Entry title.
- Author name.
- Summary or content text.
- Acquisition link.
- Alternate or subsection navigation link.
- Cover image or thumbnail link.

### Sync

`OpdsSyncCollector` should recurse through navigable catalog entries and collect only entries with acquisition links.

Guardrails:

- Track visited URLs or hrefs to avoid loops.
- Count catalog sections separately from downloadable entries.
- Keep failures visible through `OpdsConnectionRepository.markSyncFailed`.

`OpdsViewModel.syncConnection` should:

- Mark the source as syncing.
- Use `OpdsSyncCollector`.
- Replace cached entries for the source on success.
- Store last synced time and book count.
- Store the error message on failure.

### Download

`OpdsViewModel.downloadEntry` should:

- Download the acquisition link through `OpdsCatalogService`.
- Generate a stable file name with `OpdsDownloadNamer`.
- Import bytes through `BookRepository.importDownloadedBook`.
- Surface added, duplicate, unsupported format, restored, and failed results as user-facing status or error messages.

### UI Boundary

No major visual redesign is planned. The existing OPDS screen should keep its current structure and become backed by reliable data and state transitions.

## Data Flow

1. User creates an Android OPDS connection with base URL and optional credentials.
2. Android saves the connection and loads the root feed.
3. Backend authenticates via Basic Auth and returns OPDS 1.2 Atom XML.
4. Android parses entries into navigable catalog sections and downloadable book entries.
5. User browses manually, or sync recursively collects all acquisition entries.
6. Android caches downloadable entries per connection.
7. User downloads an entry.
8. Backend validates ownership and streams the book file.
9. Android imports the file into the local library and reports the result.

## Error Handling

Backend:

- Missing authentication: 401.
- Missing book or missing file: 404.
- Invalid or unsupported book format: still serve with `application/octet-stream` fallback from `MimeTypeUtil`.

Android:

- Network and parsing errors become connection failure messages.
- Sync failures update the connection sync state to `FAILED`.
- Download failures clear the active downloading title and show an error.
- Disabled sources cannot be browsed or synced.

## Testing

Follow test-driven development:

1. Write failing tests for the next behavior.
2. Verify they fail for the expected reason.
3. Implement the smallest production change.
4. Verify the targeted tests pass.
5. Run the relevant module test suite.

Backend tests:

- OPDS 1.2 XML includes root navigation, search description link, acquisition links, escaped text, pagination, and query-preserving search pagination.
- OPDS 2.0 responses expose `application/opds+json` and expected publication links.
- Download response includes MIME type, length, and encoded filename.
- Basic Auth protects `/opds`.

Android tests:

- Parser handles `summary` and `content`, acquisition links, subsection links, and cover links.
- Request factory resolves relative and absolute URLs and builds Basic Auth headers.
- Sync collector recurses through catalogs, avoids loops, and returns downloadable entries.
- View model/repository behavior updates sync status and download results.

## Non-Goals

- OPDS 2.0 parsing on Android.
- WebDAV implementation.
- KOReader progress/highlight sync.
- Server-side management UI for OPDS sources.
- Multi-user sharing beyond existing per-user book authorization.

## Acceptance Criteria

- A valid user can connect to `/opds` with Basic Auth from an OPDS client.
- Root, format, favorites, reading, books, and search feeds are browseable.
- Search discovery is exposed via `/opds/search.xml`.
- Book download links stream the correct user's files with usable filenames.
- Android can browse the root feed, open subsections, sync downloadable entries, cache them, and download a selected book into the local library.
- Targeted backend and Android tests pass.
