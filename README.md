# Jake's Hubitat Add Ons (with Jonathan's Modificaitons)

This repo holds Apps and Device for the Hubitat platform. It started due to a selfish need to control Wyze Color Bulbs. It may end there, it may not.

## Current Packages

* [**WyzeHub**](https://github.com/fieldsjm/Hubitat-2/tree/master/WyzeHub) - An _unofficial_ Hubitat implementation for Wyze devices.


## Original Packages from Jake

* [**WyzeHub**](https://github.com/jakelehner/Hubitat/tree/master/WyzeHub) - An _unofficial_ Hubitat implementation for Wyze devices.

## Fork changes

This is a fork of [fieldsjm/Hubitat-2](https://github.com/fieldsjm/Hubitat-2) (itself a fork of
[jakelehner/Hubitat](https://github.com/jakelehner/Hubitat)), maintained for one hub. It carries a
deliberate behavioural divergence from upstream.

**The camera group's switch means notifications, not camera power.**

Upstream, `WyzeHub Camera Group`'s `on()` / `off()` fan out `childDevice.on()` / `off()`, which power
the cameras up and down. That makes the obvious automation — "mute the cameras while we're home" —
silently power them off instead, so they stop detecting motion and stop recording events entirely.

In this fork:

| | Upstream | This fork |
|---|---|---|
| `on()` / `off()` | camera power | `setAllNotifications` on every child |
| `switch`, `allOn`, `allOff` | derived from children's power | derived from children's `notifications_enabled` |
| camera power | `on()` / `off()` | `setCameraPower(true\|false)` |
| power visibility | `switch` | `camerasOn` (`true` / `false` / `partial`) |
| `notificationsOn` | — | mirrors `switch`, explicitly named |

`wyzehub-camera-driver.groovy` gets a one-line companion change: it already notified its parent group
when its power `switch` changed; it now also notifies when `notifications_enabled` changes, so the
group's notification-derived switch updates immediately instead of on the next 120s poll.

Both changed drivers have `importUrl` repointed at this fork, so importing them will not pull
upstream's version back over the change.

**Migrating from upstream:** if mode rules were previously toggling the group switch, the cameras are
probably sitting powered off. Nothing here will power them back on — run `setCameraPower` → `true`
once from the group device page, and check `camerasOn` reads `true`.

Everything else in this repo is unmodified from upstream.

### Syncing with upstream

The clone is configured to track `upstream` (fieldsjm/Hubitat-2) for reads and `origin` (this fork)
for writes, and to **rebase** rather than merge — so the fork changes stay as a clean set of commits
on top of upstream instead of accumulating merge bubbles.

```bash
git pull            # rebases master onto upstream/master
git push            # goes to origin (this fork)
```

Set up by:

```bash
git remote add upstream https://github.com/fieldsjm/Hubitat-2.git
git remote set-url --push upstream DISABLED    # never write to upstream
git config pull.rebase true
git config rebase.autoStash true
git config remote.pushDefault origin
git config branch.master.remote upstream
git config branch.master.merge refs/heads/master
git config branch.autoSetupRebase always
```

If upstream ever touches the two changed drivers, the rebase will conflict there — that is the point.
Resolve toward keeping the notification behaviour, and re-check that `importUrl` still points at this
fork afterwards.

#### Automated

[`.github/workflows/upstream-sync.yml`](.github/workflows/upstream-sync.yml) does the same thing
weekly (Mondays 08:00 UTC, plus a manual **Run workflow** button):

| Outcome | What happens |
|---|---|
| Upstream unchanged | Exits quietly |
| Clean rebase | Force-pushes `master` with `--force-with-lease` |
| Conflict | Aborts, leaves `master` untouched, opens/refreshes an issue, fails the run |
| Upstream touched a forked driver | Opens an issue even on a clean rebase |

That last row is the one that matters. `importUrl` points at this fork's `master`, so anything landing
there reaches the hub on the next **Import**. A clean rebase means git had no textual conflict — not
that upstream's change is compatible with the notification behaviour. When upstream edits one of the
forked drivers, read the result before importing.

The workflow rewrites `master` history on purpose. That is inherent to keeping a divergent fork
rebased rather than merged; `--force-with-lease` means a concurrent push fails the job instead of
being clobbered.

> **Issues must stay enabled on this repo.** GitHub disables issues on forks by default, and both
> alerting paths above are `gh issue create`. With issues off, the sync still rebases and pushes but
> loses every warning — silently, and exactly when something needs attention. Enable with
> `gh repo edit <owner>/<repo> --enable-issues`.
