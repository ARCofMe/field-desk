# FieldDesk

FieldDesk is the Android technician app for ARCoM.

It is designed around the technician loop:

- today’s work
- active stop review
- notes and photos
- field exceptions and closeout actions

## Runtime Setup

FieldDesk does not use a repo `.env` file. Runtime configuration lives in the in-app Settings screen.

Required:

- backend mode: `Ops Hub` or `BlueFolder`
- base URL for the selected backend
- API key for the selected backend
- technician ID

Technician ID rules:

- with `Ops Hub`, use a BlueFolder technician id or a Discord-linked technician id
- with direct `BlueFolder`, use the BlueFolder technician id

Optional:

- technician name
- route origin and destination defaults
- theme mode
- photo auto-compress
- debug logging

## Build

```bash
./gradlew assembleDebug
```

## Test

```bash
./gradlew testDebugUnitTest
```

## Notes

- FieldDesk prefers Ops Hub as the backend path.
- When running through Ops Hub, the app receives normalized BlueFolder status semantics rather than relying only on tenant-specific raw status strings.
- Product naming is fixed:
  - `FieldDesk`
  - `RouteDesk`
  - `PartsDesk`
