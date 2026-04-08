# FieldDesk

Android technician app for ARCoM field workflows.

## Focus

- next-job execution first
- one-tap access to photos, notes, and field exceptions
- Ops Hub as the preferred backend path, with BlueFolder-direct still available for compatibility
- BlueFolder-aware SR status semantics when running against Ops Hub, so the app can distinguish closed, quote-blocked, parts-active, waiting-customer, scheduling, and review states without hardcoded tenant string logic

## Runtime Setup

FieldDesk does not use a repo `.env` file. Runtime config is stored in the in-app Settings screen.

Required to operate:

- backend mode: `Ops Hub` or `BlueFolder`
- active base URL for the selected backend
- active API key for the selected backend
- technician ID

Optional:

- technician name
- route origin / destination preferences
- theme mode
- photo auto-compress
- debug logging

## Build

```bash
cd /home/ner0tic/Documents/Projects/ARCoM/ARCoMTechApp
./gradlew assembleDebug
```

## Test

```bash
cd /home/ner0tic/Documents/Projects/ARCoM/ARCoMTechApp
./gradlew testDebugUnitTest
```

## Branding

- technician app name: `FieldDesk`
- dispatch app name: `RouteDesk`
- parts app name: `PartsDesk`
