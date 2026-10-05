# Release notes

## 0.1.0 (unreleased)

First version of Webmote, a remote for LG webOS TVs (webOS 4 / 2018 and later).

- Pairs over `wss://:3001` with an unsigned manifest (webOS 26 rejects the old signed one), by prompt
  or PIN, and pins each TV's certificate on first use.
- Remote tab: D-pad, Back/Home/Settings/Info, volume slider, mute, brightness, media keys, input
  picker, 123 sheet, live keyboard and the Picture sheet.
- Touchpad and Apps tabs, several TVs, Wake-on-LAN, the TV-off screen.
- Keep-alive service with a notification, phone volume keys, Quick Settings tiles and two widgets.
- English and French.

Before release, run the [manual test checklist](checklist.md) on the webOS 26 test TV and record
its findings here.

### Findings on the test TV

- 2026-10-05, Pixel 8 Pro, LG OLED42C54LA (webOS 10.3.1 release version): pairing, the remote, volume and
  picture control work. Wake-on-LAN failed while the phone ran a VPN (NordVPN), which routes
  255.255.255.255 into its tunnel; fixed by also sending to the TV network's broadcast address.
- The TV keeps accepting TCP on port 3001 while it's off (standby).
