# FieldDesk Walkthrough

This guide is for running FieldDesk confidently during a demo or live technician workflow.

## Purpose

FieldDesk is the technician surface in the OpsHub ecosystem.

Use it to:

- review today’s assigned work
- pick the next stop from the correct service-window order
- capture notes and photos during the stop
- hand off exceptions cleanly
- complete labor-ready closeout through Ops Hub

## Before You Start

Open `Settings` in the app and confirm:

- backend mode is correct
- base URL is correct
- API key is present
- technician ID is present
- technician name is present if you want cleaner presentation copy

Optional but useful:

- route origin and destination
- OpsHub, RouteDesk, and PartsDesk URLs
- auto-compress photos

Important backend caveat:

- If backend mode is `Ops Hub`, direct SR photo attach is available.
- If backend mode is direct `BlueFolder`, direct photo attach is not wired in the app. The Photos screen now tells you this explicitly.

## Main Navigation

Drawer / top-level destinations:

- `Today`
- `Queue`
- `Photos`
- `Notes`
- `Settings`

Primary workflow rule:

- Start from `Today` when working the next stop.
- Use `Queue` when you need the full day, routing context, or to override the default next stop.

## Recommended Demo Flow

### 1. Settings

Start here first if you are demoing to someone else.

Show:

- backend mode
- server URL and API key presence
- technician identity
- workspace launcher summary

What to say:

- FieldDesk stores runtime setup in-app, not in a repo `.env`.
- The ecosystem launcher count tells you immediately whether sibling app handoffs are ready.

### 2. Today

This is the best “home” screen for the product story.

What it shows:

- loaded queue count
- completed vs pending count in separate summary cards
- stop position as `Stop X of Y`
- active stop summary
- next-step guidance
- quick actions
- a `Refresh queue` action for live data checks without leaving the screen

How jobs are ordered:

- jobs are now sorted by service-window start time first
- examples: `8-10` before `10-12`, then `1-3`
- this same order feeds the routing flow
- the active stop prefers the earliest incomplete stop, not a completed early stop

What to verify:

- the active stop is the expected next stop
- the appointment window sequence makes sense
- the quick actions match the current job state
- refresh does not reorder the day incorrectly

### 3. Queue

Use `Queue` when you need to show the full day.

What to show:

- all stops for the selected date range
- the selected date type
- Google Maps route launch

Routing behavior:

- the queue is sorted through the same technician-flow ordering logic used by `Today`
- route launch uses that ordering before handing stops to Google Maps
- Google Maps route launch preserves the listed order instead of asking Maps to optimize the waypoints

### 4. Job Detail

Open a specific stop from `Today` or `Queue`.

This is the operational center for a single visit.

Show:

- customer and address context
- workflow actions
- notes and photos entry points
- closeout readiness

Use this screen when:

- you need to explain the current stop
- you want to branch into notes, photos, or closeout

### 5. Notes

The Notes screen is for structured technician context, not freeform comment soup.

Recommended pattern:

1. start from a selected job
2. choose a template or guided note path
3. save draft locally if you need to keep moving
4. sync the note through Ops Hub before closeout when possible

What to check:

- note draft exists
- sync state is clear
- the note explains diagnosis, blockers, and next action cleanly

### 6. Photos

The Photos screen is for guided proof capture.

Typical prompts:

- model / serial
- overview
- issue / part

Recommended pattern:

1. open from the current job
2. capture or select a photo
3. attach it to the SR if the backend supports direct upload
4. run compliance check when needed

Important behavior:

- In `Ops Hub` mode, SR photo attach is supported.
- In direct `BlueFolder` mode, the attach button is intentionally disabled and the UI says that Ops Hub is required for direct attach.
- failed photo attach messaging now shows the actual backend limitation instead of a vague retry-only message

### 7. Closeout

Closeout should be the last step after notes and photos are in acceptable shape.

Completed closeout includes:

- labor type
- work summary
- elapsed time
- signer name
- signature capture
- final outcome

Before closeout, verify:

- required photos are captured
- service note is complete
- final outcome is selected
- any failure or unable-to-complete reason is explicit

## Technician Workflow Pattern

Use this as the default real-world sequence:

1. Open `Today`
2. Confirm the active stop
3. Open the job
4. Navigate or call ahead if needed
5. Capture notes during or immediately after diagnosis
6. Capture required photos before leaving
7. Create parts or exception handoff if needed
8. Complete closeout only after notes and photos are defensible

## Demo Checklist

Before presenting, verify these on the device:

- app opens cleanly
- `Settings` shows a valid backend configuration
- `Today` loads jobs
- job order matches expected service windows
- `Today` highlights the earliest incomplete stop
- `Refresh queue` works from `Today`
- `Queue` route launch opens with the expected stop order
- `Photos` can capture or select a photo
- photo attach behavior matches the chosen backend mode
- `Notes` can save and sync
- `Closeout` preview and submit work for an Ops Hub-backed job

## Known Limits

- There is still no UI automation pass on-device; validation is build/test plus manual device checks.
- Direct BlueFolder backend mode still has feature gaps compared with Ops Hub mode, especially around photo attach and other direct workflow actions.
- OpsHub does not yet have its own separate operator frontend, so the ecosystem “brain” is represented as product context, not as a web app destination.

## Best Presentation Story

If you need a short narrative:

- OpsHub is the brain.
- RouteDesk handles dispatch and triage.
- PartsDesk handles parts operations.
- FieldDesk is the technician execution surface.
- The technician starts in `Today`, works the selected stop, captures notes and photos, and closes the loop back into Ops Hub.
