# Alarum

An Android alarm that wakes you gently and, if you ignore it, stops being gentle.
Wired into Home Assistant, but never dependent on it.

## The rule everything else bends around

**The alarm fires and escalates with the network down, the broker dead, and HA offline.**

Home Assistant is an amplifier bolted onto a self-sufficient local alarm, never a
link in the chain. Every HA call is fire-and-forget on a separate scope with a
short timeout, and every failure is swallowed and logged.

## How the two halves split

| The app owns | Home Assistant owns |
|---|---|
| Scheduling, the audio ramp, vibration, screen, torch | What the lights, speakers and switches do |
| Dismissal challenges and the escalation timeline UI | Presence gating |
| Publishing state | Everything house-shaped |

Behaviour lives in automations so you can change what stage 3 does by editing an
automation, not by rebuilding an APK and waiting until tomorrow morning to find
out whether it worked.

## The entity contract

Treat these names as API — adding a field is safe, renaming one breaks someone's
automation.

| Entity | Meaning |
|---|---|
| `sensor.alarum_next_alarm` | timestamp of the next alarm |
| `binary_sensor.alarum_ringing` | on while it is going off |
| `sensor.alarum_stage` | `gentle` / `rising` / `insistent` / `hostile` / `idle`, every other field as an attribute |
| `sensor.alarum_stage_index` | 0-based, for `numeric_state` triggers |
| `sensor.alarum_elapsed` | seconds spent ringing |
| `sensor.alarum_last_dismissed` | timestamp you last shut it up |

`sensor.alarum_stage` and `binary_sensor.alarum_ringing` also carry a `preview`
attribute, true when the in-app previewer is driving them rather than a real
alarm. Condition on it in anything you would rather not have fire mid-afternoon:

```jinja
{{ not state_attr('sensor.alarum_stage', 'preview') }}
```

Home Assistant builds these ids from the **device name** plus the entity name and
ignores `object_id` for discovered entities, so the device name is load-bearing:
"Alarum" is what makes `sensor.alarum_stage`. Rename the device and every id moves
with it, and your automations stop matching.

Two routes, same entity ids, so an automation written against one keeps working
on the other:

- **MQTT** (preferred) — retained discovery, so the entities survive an HA
  restart. Connected only while ringing, plus a brief connect-publish-disconnect
  when the schedule changes. Nothing at idle.
- **REST fallback** — needs no broker and works immediately, but the entities are
  ephemeral and vanish on an HA restart until the app publishes again.

## Escalation profiles

A profile is a list of stages. Each stage has a duration, a synthesised tone with
start and end volume, a vibration pattern, screen brightness, torch strobe, a
list of things to say out loud, a dismissal method with difficulty, whether
snoozing is allowed, and optionally one HA script or scene to run on entry.

Dismissal is maths, a shake, or an NFC tag. Tap and long press are retired: one
thumb movement should not be able to end a thirteen-minute ramp, and a hand four
seconds out of sleep can manage one tap, which made every stage after the first
one theoretical. They still parse, because an unknown enum value fails the whole
file and the store treats a failed parse as "here are the defaults" — deleting
them would quietly replace every profile you had made. Anything still on one is
moved up on load: a tap becomes one easy sum, a long press becomes two.

The last stage has no duration — it sustains until the alarm is dealt with.

Three ship by default: *Gentle, then not*, *Sunrise only*, and *No messing about*.
All are editable, and none of them contain a single entity id, so the app is not
specific to any one house.

## Talking

A stage can carry a list of lines and a gap. The phone does not say them — it
publishes one at a time as `sensor.alarum_say`, and an automation decides which
speaker says it, in whose voice, at what volume. Same division as everything
else here, which is why this feature contains no entity id either.

Trigger on the **`say_seq` attribute**, not on the state. It is epoch
milliseconds and changes on every utterance, so two identical lines in a row are
two utterances; triggering on the text alone would silently swallow the second.

```yaml
- id: alarum_say
  alias: "Alarum - say it out loud"
  mode: queued
  trigger:
    - platform: state
      entity_id: sensor.alarum_say
      attribute: say_seq
  condition:
    - condition: template
      value_template: "{{ states('sensor.alarum_say') not in ['idle', 'unknown'] }}"
  action:
    - action: tts.speak
      target:
        entity_id: tts.google_en_com
      data:
        media_player_entity_id: media_player.bedroom
        message: "{{ states('sensor.alarum_say') }}"
```

The shipped profiles already talk at the sharp end. *Gentle, then not* stays
quiet through Gentle and Rising, turns argumentative at Insistent, and at
Hostile shuffles ten increasingly personal remarks every thirty seconds. *No
messing about* talks from the first stage. *Sunrise only* says nothing at all,
because the entire point of that one is that it does not shout at you.

The lines are sentences rather than barks, and deliberately so: a three-word
command is a jingle, and a jingle becomes wallpaper by the third repeat. Set
the gap longer than the longest line takes to say — the app measures it publish
to publish and has no idea when your speaker finished. A test pins that for the
shipped profiles at roughly fifteen characters a second.

A list rather than one line: a single sentence repeated every thirty seconds
stops registering after the third time, which is exactly the failure mode the
app exists to avoid. Shuffle is exhaustive — every line before any repeat — and reseeded from the
moment the alarm started, so it is a different order every morning and a
different one per stage, while staying a stable permutation within one ring. Per stage, like everything
else, so the first one can be civil and the last one need not be. **Suggest
one** fills from a starter set that gets less polite as you keep pressing it,
and **Send one now** publishes a single line so you can check the automation
without waiting for a stage.

In the previewer, lines go out only while publishing is on, which already means
1×. A sentence takes as long as it takes, no matter how fast the app's clock is
running.

## Previewing one

The play button on a profile rehearses the whole ramp. It is on the profile row
in the list, and in the editor, where it runs the draft rather than what is on
disk, so you can hear a slider nudge before committing it.

Same engine, same speakers and motors as 07:00, on a clock you own. Pause it,
drag the scrubber, jump straight to the last stage, or run it at 60× so a
twenty-five minute wake-up takes twenty-five seconds. Mute silences the tone and
the vibration and leaves the screen and torch going, which is the half you can
stand to preview at a desk.

Turning on Home Assistant publishing drops the speed to 1×, and says so if you
put it back up. The house cannot be fast-forwarded: a light with a 290-second
transition does not care that the app's clock is at 10×, it just gets restarted
a tenth of the way in and looks like nothing happened. Preview the phone fast,
preview the house at the speed the house runs at.

A preview leaves nothing behind. Starting one photographs every `light.*` in
Home Assistant with `scene.create`, and stopping puts them all back with
`scene.turn_on`, so a rehearsal at three in the afternoon does not end with the
bedroom stuck at 70%. That needs the REST route configured, URL and token: MQTT
is publish-only and cannot read a light or call a service.

`binary_sensor.alarum_ringing` stays off for the whole preview, which is what
makes stopping inert. An on-to-off edge would fire your stand-down automation on
the way out, including anything it has queued behind a `delay:`, and that is not
something a preview gets to do to you. Stage state is what automations trigger
off anyway. When you do want to rehearse the dismissal, **Dismiss for real** in
the house card publishes the whole edge on purpose and leaves the lights where
that automation puts them.

Three more things are deliberately unfaithful, and the screen says so as it goes.

- The system alarm volume is never commandeered. A preview that pins your alarm
  stream to maximum and trusts a clean exit to put it back is a bad neighbour.
- A stage's HA script is not run. Publishing a state is a claim about the world,
  running a script is doing something to it.
- Everything published carries `preview: true`.

The preview stops when you leave the screen. That is the opposite of what a real
alarm should do, and exactly right for this one.

## Layout

```
domain/     pure Kotlin, no Android imports — the escalation engine, schedule
            maths and challenge generation all live here and are unit tested
data/       JSON-file persistence and settings
ha/         REST client, MQTT publisher, discovery payloads, the state contract
alarm/      AlarmManager scheduling, receivers, the ring foreground service
effect/     tone synthesis, audio, haptics, torch
ui/         Compose screens
homeassistant/  the automations, and the script that installs them
```

The tones are synthesised with `AudioTrack` rather than shipped as files, so the
volume ramp is genuinely continuous instead of crossfading clips, and the APK
carries no binary audio.

## Building

```sh
./gradlew :app:assembleDebug       # local build
./gradlew :app:testDebugUnitTest   # the domain tests
```

A local release build falls back to the debug key, so a locally built APK can
never install over a CI-built one. That is deliberate: only CI ships.

## Releasing

CI runs tests, lint and a debug build on every push and pull request. Pushing a
`v*` tag builds a signed release APK and publishes it as a GitHub release.

Bump `versionCode` and `versionName` in `app/build.gradle.kts`, then tag the
commit `v1.0.1` and push the tag. The release job refuses to run if the tag and
`versionName` disagree — otherwise the app would offer an update to the version
it is already running.

Four repository secrets are needed for signing:

| Secret | What |
|---|---|
| `RELEASE_KEYSTORE_B64` | the keystore, base64-encoded |
| `RELEASE_KEYSTORE_PASSWORD` | its password |
| `RELEASE_KEY_ALIAS` | the key alias |
| `RELEASE_KEY_PASSWORD` | the key's password |

## Updating

**Settings → Updates** asks GitHub for the newest release and, only if it beats
the running build, downloads that APK and installs it. Nothing checks or
installs on its own — an alarm clock that replaced itself unattended at 03:00
would be an inventive way to miss a morning.

## Gotchas worth knowing

- Uses `USE_EXACT_ALARM`, not `SCHEDULE_EXACT_ALARM` — a real alarm clock
  qualifies and needs no runtime grant.
- `AlarmManager.setAlarmClock` is the only API Android promises will fire on time
  in deep Doze.
- Some OEMs kill background apps anyway. Settings has a battery-optimisation
  exemption prompt; use it.
- In current Home Assistant, `light.turn_on` takes `color_temp_kelvin`. The old
  `kelvin` parameter now returns 400 and the light silently does nothing.
