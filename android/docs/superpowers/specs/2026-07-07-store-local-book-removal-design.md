# Store Local Book Removal Design

## Goal

Allow users to remove locally imported books from the Android book store catalog without deleting the original file or stored metadata. If the book is currently on the shelf, removing it from the store also removes it from the shelf.

## Requirements

- Only local imported books can be removed from the store.
- Removing a local book from the store hides it from the store catalog.
- Removing a local book from the store also sets `shelved` to false.
- The underlying book file, metadata, reading progress, and database row are preserved.
- OPDS catalog entries are not removed by this action.
- Importing the same file again after removal restores the existing record to the store instead of returning only a duplicate result.

## Architecture

Add a boolean store visibility flag to `LocalBook` and `BookEntity`, persisted by Room. `BookRepository` owns store visibility changes and duplicate-import restoration. `StoreCatalog` filters hidden local books during aggregation. `StoreViewModel` exposes single and batch remove actions for local `StoreBook` items, and `BookStoreScreen` adds management UI for selecting local books and triggering removal.

## Testing

- Unit-test catalog aggregation so hidden local books are excluded while still usable as downloaded matches for OPDS entries if needed.
- Unit-test repository duplicate import behavior with an existing hidden book where feasible.
- Run store catalog tests and compile the Android app tests enough to catch Room/model wiring errors.
