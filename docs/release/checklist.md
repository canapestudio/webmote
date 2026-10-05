# Manual test checklist

Run before each release on the real webOS 26 test TV, with a phone on the same Wi-Fi. The TV session
is covered by automated tests against the fake TV (`./gradlew :core:test`); this list covers what
they can't reach: the UI, tiles, widgets, the keep-alive service, the notification and the
MediaSession volume adapter. Record findings in the Notes column and in the [release notes](notes.md).

TV model / firmware tested: ________  Phone / Android version: ________  Date: ________

## 01 · Pairing by IP and volume

| ✓ | Check | Notes |
|---|---|---|
| ☐ | Fresh install: the app opens on the pairing screen. | |
| ☐ | Enter the TV's IP, tap Connect: the TV shows "Allow this device?"; the app says to accept it. | |
| ☐ | Accept with the physical remote: the remote appears. | |
| ☐ | The volume slider changes the TV's volume (with a soundbar: − and + in its place). | |
| ☐ | Close the app (swipe away), reopen: it reconnects with no prompt on the TV. | |
| ☐ | Decline the prompt on a second pairing attempt (Settings → Add a TV): the app says pairing was declined. | |

## 02 · Remote tab: D-pad and volume

| ✓ | Check | Notes |
|---|---|---|
| ☐ | D-pad Up/Down/Left/Right and OK move and select in the TV's menus. | |
| ☐ | Back (bottom left of the D-pad) and Home (bottom right) work. There's no Info key and no separate row of TV keys. | |
| ☐ | Settings (top right of the D-pad) opens the TV's quick settings. **Note which button name worked (QMENU or MENU).** | |
| ☐ | Every button press gives a haptic tick. | |
| ☐ | The volume slider shows the TV's level; dragging it changes the TV's volume smoothly. There are no separate − and + buttons. | |
| ☐ | Changing the volume with the physical remote moves the slider. | |
| ☐ | Mute (top left of the D-pad) toggles mute and shows when the sound is off, with a haptic tick; TalkBack reads whether it's on. | |
| ☐ | With sound on a soundbar / external output (if available): − and + take the slider's place, step the volume and repeat while held; Mute still works. | |
| ☐ | The tab doesn't scroll. On a small phone (about 360 × 640 dp, like the emulator's Small Phone) and a large one, at the default font size and at 200%, in English and French: the D-pad and its four keys, both sliders, the media row and the shortcuts all show at once, with nothing overlapping and no label cut off. It may scroll only in split screen, or on a small phone while a notice shows (button connection refused, picture changes ignored). **If it scrolls otherwise, note the phone, font size and navigation mode (gestures or buttons).** | |
| ☐ | When the TV refuses the button connection, the Remote tab and the 123 sheet show a short notice ("The TV won't take button presses…"), and the buttons around it keep a usable size on a small phone. | |
| ☐ | The D-pad is no larger than before (about 78% of the width, at most 280 dp). On a short screen it shrinks, and the four keys stay in the corners around it without touching the circle. | |
| ☐ | Developer options → Force RTL layout direction: Mute stays top left, Settings top right, Back bottom left and Home bottom right, like the arrows. | |
| ☐ | With auto-rotate on, turning the phone sideways keeps the app in portrait. | |

## 03 · Complete pairing

| ✓ | Check | Notes |
|---|---|---|
| ☐ | On first run the TV appears in the discovered list by name; tapping it pairs. | |
| ☐ | Refresh re-runs discovery. | |
| ☐ | Manual IP entry still works. | |
| ☐ | PIN pairing, if the TV offers it: the app asks for the PIN shown on the TV and pairs. | |
| ☐ | Re-pair from Settings: the TV prompts again and the remote works afterwards. | |
| ☐ | No location or nearby-devices permission was requested at any point. | |

## 04 · Several TVs

| ✓ | Check | Notes |
|---|---|---|
| ☐ | With one TV saved there is no switcher in the top bar. | |
| ☐ | Add a second TV (or the same TV again after forgetting): the switcher appears. | |
| ☐ | Switching TVs connects to the chosen one. | |
| ☐ | The app reopens on the last-used TV. | |
| ☐ | Rename a TV: the new name shows in the top bar, switcher and notification. | |
| ☐ | Forget the active TV: another TV becomes active, or the app returns to pairing. | |

## 05 · Power, Wake-on-LAN, TV-off screen

| ✓ | Check | Notes |
|---|---|---|
| ☐ | The power button turns the TV off; the app shows "TV is off". | |
| ☐ | Opening the app with the TV off shows "TV is off" with a Turn on button. | |
| ☐ | Turn on wakes the TV and the remote appears without another tap. **Note whether waking works over Wi-Fi, Ethernet, or both.** | |
| ☐ | With "Turn on via Wi-Fi" / Wake on LAN disabled on the TV: after ~20 s the hint appears; dismissing it means it never shows again. | |
| ☐ | Turning the TV on with the physical remote while "TV is off" is visible: the remote appears on its own. | |

## 06 · Picture control

| ✓ | Check | Notes |
|---|---|---|
| ☐ | The Brightness slider changes the backlight (OLED light on OLED sets). | |
| ☐ | The Picture sheet shows the TV's current brightness, contrast, colour and energy saving. | |
| ☐ | Dragging each slider changes the picture smoothly, without lag build-up. | |
| ☐ | Energy saving: Auto / Off / Min / Med / Max each apply. | |
| ☐ | **Note whether any alert pop-up flashes on the TV during picture changes.** | |
| ☐ | If picture changes don't apply on this TV: the controls grey out with an explanation. | |

## 07 · Extra remote controls

| ✓ | Check | Notes |
|---|---|---|
| ☐ | Play/pause, stop, rewind and fast-forward (the media row) work in a video app. **Note how the play/pause toggle behaves (it alternates, starting with play).** | |
| ☐ | The input picker lists the TV's inputs and switches to the chosen one. | |
| ☐ | 123 sheet: digits, channel ± (on live TV) and the red/green/yellow/blue keys work. | |
| ☐ | Keyboard: in a TV search field, each typed letter appears as typed; backspace deletes; Enter submits. | |

## 08 · Touchpad and Apps tabs

| ✓ | Check | Notes |
|---|---|---|
| ☐ | Touchpad: dragging moves the TV's pointer; tapping clicks; two-finger drag scrolls. | |
| ☐ | Apps: the grid shows the TV's apps with icons; tapping launches the app. | |
| ☐ | Long-press pins an app to the top; it stays pinned after restarting the app; pins differ per TV. | |

## 09 · Keep-alive service, notification and volume keys

| ✓ | Check | Notes |
|---|---|---|
| ☐ | Opening the app shows the persistent notification with the TV's name and state. | |
| ☐ | Notification −, mute, + and power buttons work. | |
| ☐ | With the app closed, the phone's volume keys change the TV's volume, and the system volume panel shows the TV's level. | |
| ☐ | Same with the phone locked. | |
| ☐ | Settings → "Phone volume keys control the TV" off: the keys control the phone again. | |
| ☐ | Turning the TV off with the physical remote stops the service; the notification disappears. | |
| ☐ | Leaving the TV's Wi-Fi (turn Wi-Fi off) stops the service; the notification disappears. | |

## 10 · Quick Settings tiles and widgets

| ✓ | Check | Notes |
|---|---|---|
| ☐ | Power tile shows on/off and turns the TV off, and on again (Wake-on-LAN). | |
| ☐ | Mute tile shows the mute state and toggles it. | |
| ☐ | Screen off tile turns the picture off while audio keeps playing. | |
| ☐ | Open remote tile opens the app. | |
| ☐ | Tapping a tile with the app closed and the service stopped starts the service and performs the action. | |
| ☐ | 4×1 widget: power, volume −, mute, volume + work and the widget shows the TV's state. | |
| ☐ | 4×2 widget: D-pad, OK, Back, Home, volume ±, mute and power work. | |

## 11 · Diagnostics, licences, settings

| ✓ | Check | Notes |
|---|---|---|
| ☐ | Diagnostics shows the model, webOS version, MAC addresses and which features work; unlearned ones show as unknown. | |
| ☐ | The open-source licences screen lists the reference projects and libraries. | |
| ☐ | Haptics off: no button gives a haptic tick. | |
| ☐ | Touchpad sensitivity changes pointer speed and survives a restart. | |

## Protocol details to confirm on the test TV

The implementation follows the reference projects (`docs/research/webos-protocol.md`), but these
details were never captured on webOS 26 with an unsigned key. Note what you see; adjust the core
(and its fake TV) if reality differs.

| ✓ | Check | Notes |
|---|---|---|
| ☐ | Pairing: what an unknown or stale client key returns (an error, or a new prompt). | |
| ☐ | PIN pairing (if offered): reply order after `ssap://pairing/setPin`, string PIN accepted, wrong PIN's reply. | |
| ☐ | Discovery from the phone: the TV is listed by its real name (the MediaRenderer answer comes from the same IP). | |
| ☐ | Pointer socket: stays open when idle (LGTV Companion sends `type:ping`); scroll speed and direction feel right. | |
| ☐ | Volume: with a soundbar, the TV reports `volume: -1` or no level; `setMute` with a boolean works. | |
| ☐ | Keyboard: `insertText` with `replace: false` (boolean) works. | |
| ☐ | Icons: they load over `https://<tv>:3001` (Android blocks the plain `http://<tv>:3000` URLs). | |
| ☐ | Picture: the TV pushes the new value within ~2 s of a write (otherwise raise `pictureVerifyTimeout`, or controls grey out wrongly). | |
| ☐ | Picture: string values are accepted; passing the `…apiadapter.pub-…` alert ID back unchanged works; no stuck alert if `closeAlert` never answers. | |
| ☐ | Picture retry: whether `deviceOSReleaseVersion` changes with a firmware update (the retry keys on it). | |
| ☐ | Power off: the TV turns off even though the link closes right after `system/turnOff`. | |
| ☐ | Standby: how long port 3001 keeps answering after power off (with Quick Start+ on, the TV may never look off). | OLED42C54LA kept accepting TCP on 3001 for the 2+ minutes it was off. |
| ☐ | Wake: whether the 20 s `wakeTimeout` is right. Magic packets go to the TV network's broadcast address (e.g. 192.168.1.255) and to 255.255.255.255. | 2026-10-05, Pixel 8 Pro with NordVPN on, LG OLED42C54LA on Wi-Fi: 255.255.255.255 was routed into the VPN tunnel and never arrived; 192.168.1.255 woke the TV. |
| ☐ | Wake with a VPN on the phone (one that keeps the local network outside the tunnel). | Works since the subnet broadcast was added. |
| ☐ | Screen off: which method gets remembered (panelcontroller is tried first and unconfirmed); sound keeps playing; screen on works. | |
| ☐ | MAC capture: `connectionmanager/getinfo` answers with the unsigned manifest (Diagnostics shows both MACs). | |
| ☐ | Tiles: a tap with the app fully closed starts the service (relies on SystemUI's tile-click allow-list, not a documented API). | |
| ☐ | Volume keys: the phone routes keys to the remote MediaSession (some phones may require a media route). | |

## Throughout

| ✓ | Check | Notes |
|---|---|---|
| ☐ | Phone in French: the whole app, notification, tiles and widgets are in French. | |
| ☐ | Light and dark system theme both look right; dynamic colours follow the wallpaper (Android 12+). | |
