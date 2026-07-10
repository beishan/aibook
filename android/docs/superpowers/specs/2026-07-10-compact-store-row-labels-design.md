# Compact Store Row Labels Design

## Goal

Reduce the horizontal space used by metadata and the shelf action in the Android book store's compact list view, while keeping each value recognizable and accessible.

## Scope

- Change only the compact list row in `BookStoreScreen`.
- Keep grid, small-grid, covered-list, detail, and OPDS-specific screens unchanged.
- Do not change `StoreBook` or other domain models.

## Display Rules

- Show the book title first, followed by compact metadata in the same row layout.
- Convert every non-blank format label to its lowercase first character: `EPUB` becomes `e`, `TXT` becomes `t`, `PDF` becomes `p`, and `MOBI` becomes `m`.
- Use an empty format label when the source format is blank, so no misleading marker is invented.
- Show local source as `本` and OPDS source as `O`, based on `StoreItemKind` rather than the user-facing source name.
- For local books outside management mode, show `入架` when the book can be added and `✓` when it is already on the shelf.
- Preserve the existing click behavior: `入架` adds the local book, while `✓` remains disabled.
- Preserve full accessibility descriptions: `加入书架` and `已在书架`.
- OPDS download states and management-mode selection behavior retain their existing semantics. Only local shelf labels are shortened by this change.
- Button feedback must not add shadows or press-projection effects, in accordance with the Android UI rules.

## Implementation Shape

Add small, internal pure formatting functions near the compact-row implementation for format, source, and local shelf labels. `StoreCompactListItem` uses these helpers and a compact-only action composable. Reusing the general `StoreBookAction` is avoided because its full labels are still required by the other layouts.

## Testing

- Unit-test format abbreviation for EPUB, TXT, another supported format, mixed case, and blank input.
- Unit-test source abbreviation for local and OPDS items.
- Unit-test local shelf visual and accessibility labels for shelved and unshelved states.
- Run the focused store unit tests and the relevant Android module compilation/test task.

## Acceptance Criteria

- An EPUB local book not on the shelf shows `e`, `本`, and `入架` in the compact row.
- A TXT OPDS book shows `t` and `O` in the compact row.
- A shelved local book shows `✓`, with `已在书架` available to accessibility services.
- Other formats use their lowercase first character.
- Other book-store layouts retain their current full labels and actions.
