# LG webOS SSAP protocol research: primary-source notes for an Android (Kotlin) remote

Researched 2026-10-05. Everything below is quoted from the source files or issue threads linked next to it. Anything I could not confirm from a primary source is marked **UNCONFIRMED**.

---

## 0. Pinned source versions

| Project | Licence | Version used | Commit |
|---|---|---|---|
| aiowebostv | Apache-2.0 | **v0.10.0** (2026-08-28), same handshake as **v0.9.2** (2026-08-21, "Fix webOS 26 pairing handshake", PR #719). `main` on 2026-10-05 has no code changes since v0.10.0 (`compare v0.10.0...main` shows only CI and dependency files). | v0.10.0 = `36fbc433147dd17032f63e40ada57c439625e6ed`; v0.9.2 = `2eff73981b12b22e147ae224b34172a9eb05312c` |
| LGTVCompanion | MIT | **v5.7.0** (2026-08-17) | `368165b8acd499cac332392947fec51698a1a5c5` (manifest change itself: `7be8beff6f2c5fa44b835ce62facf75ce589b845`, "Restore access to getPointerSocket etc", 2026-08-16) |
| bscpylgtv | MIT | **v0.5.5** (tag commit 2026-09-30; its CHANGELOG says "2025-09-30") | `191e8957a164a7a15b0b687071b534394a161aed` |
| lgtv2 | MIT | **v2.0.2** (2026-09-05) | `b761e06d5c12c953e995a490f70769b1f3456680` |
| homebridge-webos-tv | MIT | **v2.5.0** (2026-09-11) | `a3cc4bcf8f79a01a34798228d78af0249eb197cb` |
| Home Assistant core `webostv` (consumer of aiowebostv) | Apache-2.0 | dev @ 2026-09-30 | `b7104788285aa2f1feaefc4e1f460603201d30c8` |
| Extra: ConnectSDK Android Core (Apache-2.0), used for SSDP, IME and PIN cross-checks | Apache-2.0 | master | `2f7ebbc7eb909321ff41b2c6bcdf6b0881427579` |

URL shorthands used below:
- `AW` = `https://github.com/home-assistant-libs/aiowebostv/blob/36fbc433147dd17032f63e40ada57c439625e6ed/`
- `LGTVC` = `https://github.com/JPersson77/LGTVCompanion/blob/368165b8acd499cac332392947fec51698a1a5c5/`
- `BSC` = `https://github.com/chros73/bscpylgtv/blob/191e8957a164a7a15b0b687071b534394a161aed/`
- `LGTV2` = `https://github.com/hobbyquaker/lgtv2/blob/b761e06d5c12c953e995a490f70769b1f3456680/`
- `HB` = `https://github.com/merdok/homebridge-webos-tv/blob/a3cc4bcf8f79a01a34798228d78af0249eb197cb/`
- `HA` = `https://github.com/home-assistant/core/blob/b7104788285aa2f1feaefc4e1f460603201d30c8/`
- `CSDK` = `https://github.com/ConnectSDK/Connect-SDK-Android-Core/blob/2f7ebbc7eb909321ff41b2c6bcdf6b0881427579/`

---

## TL;DR: findings and surprises

1. **webOS 26 / firmware 43.x rejects the old "LG Remote App" signed manifest** with
   `{"type":"error","id":"register_0","error":"403 Pairing rejected: blacklisted certificate detected","payload":{}}`. No prompt appears. LGTV Companion first saw this on **43.00.92** (April 2026, C2); G5, G6 and C6 sets saw it from **43.21.60** (August 2026).
2. **The fix used everywhere is a manifest with no `signed` or `signatures` block**, with every permission in the plain `manifest.permissions` array. The user grants these at the on-screen prompt. A signature-verified tier still exists, and you cannot get it back by listing the same permissions unsigned.
3. **Cost of going unsigned**: `com.webos.service.update/getCurrentSWInformation` returns `401 insufficient permissions`. Direct `ssap://settings/setSystemSettings` also returns 401, on webOS 26 and on older firmware too (aiowebostv #728, HA #180025). **Reading settings with `getSystemSettings` still works, and writing through the `createAlert` "luna" workaround still works.**
4. **Surprise: permissions are evaluated per connection, from the manifest you send with `register`.** They are not tied to the client key (aiowebostv #728). An old key from the signed manifest keeps working with the new unsigned manifest; HA users and LGTVC report no re-pairing needed. However, any key sent with the **signed** manifest is refused with the 403 on blacklisting firmware.
5. **Surprise: aiowebostv v0.10.0 still has the error-handling gap described in HA #172703.** If the first reply to `register` is an `error` and a client key is already stored, it does not raise and carries on. You then get `401 insufficient permissions (not registered)` on the next call. Your client should treat anything other than `registered` (after an optional `response`/`pairingType`) as failure and surface `error`.
6. **Surprise: homebridge-webos-tv 2.5.0 took a different route.** It ships a **different signed manifest** ("LG Mobile", serial `49d0233e26a3fc4d3113ddbc22a8686a`) that a user extracted from the decompiled LG ThinQ app (homebridge #574). It reportedly works on webOS 26. That is LG's signed blob taken from a decompiled app, so it carries legal and IP risk and LG could blacklist it next. I would not copy it.
7. **Surprise: lgtv2 2.0.2's fallback only deletes `signed`.** It keeps `signatures`, and pairing still works per its PRs. perseus177's controlled test in HA #172703 showed that the **`signatures` block** is what triggers the 403 when `signed` is present. The safe choice is to send neither.
8. On firmware 33.20.x and later, `hello` **must** contain `"payload": {}` or the TV does not answer (aiowebostv #500). `ssap://system/getSystemInfo` must be called **before** `register`; after registration it returns `401 insufficient permissions` (aiowebostv #501, HA #147557).
9. **Port 3000 (`ws://`) is dead on at least one webOS 26 set** (C6, 43.21.60): the TCP connection opens and is immediately closed. Use `wss://<tv>:3001` and trust the self-signed certificate.
10. The pointer-socket key name **`MENU` and `QMENU` are both annotated "Quick Settings Menu"** in aiowebostv and bscpylgtv. No source documents a key that opens the full settings. bscpylgtv launches the settings app instead: `com.palm.app.settings` with `{"target":"PictureMode"}`.
11. Picture values from `getSystemSettings` are **usually strings** (`"backlight": "100"`; homebridge `parseInt`s them). One webOS 25 G4 capture shows an **int** (`'backlight': 26`), so parse both. OLED sets use the key **`backlight`** for OLED light. There is no `oledLight` key in any source.
12. Screen off/on: on **webOS ≥5** use `ssap://com.webos.service.tvpower/power/turnOffScreen` and `turnOnScreen`. On **webOS 4.x** use `ssap://com.webos.service.tv.power/turnOffScreen` and `turnOnScreen` **with `{"standbyMode":"active"}`, which is mandatory there** (`POWER_ERROR_0003 "Invalid standbyMode !!"` without it). All are plain `ssap://`, not luna.
13. The DIAL SSDP response includes a **`WAKEUP: MAC=…;Timeout=60`** header, a second way to learn the Wake-on-LAN MAC besides `connectionmanager/getinfo`.
14. Android: `CHANGE_WIFI_MULTICAST_STATE` **is** one of the `connectedDevice` FGS prerequisite permissions. Google Play now requires **targetSdk 36** for new apps and updates (since 2026-08-31; extension to 2026-11-01 possible). Widget interactions are an explicit FGS-start exemption. **Quick Settings tiles are not named** in the exemption list (UNCONFIRMED). Android 17 (targetSdk 37) will gate LAN access behind `ACCESS_LOCAL_NETWORK`.

---

## 1. Handshake

### 1.1 Transport

- aiowebostv tries `ws://host:3000` first and falls back to `wss://host:3001` on `aiohttp.ClientConnectionError` or `aiohttp.WSServerHandshakeError`. TLS verification is off (`ssl=False`, "webOS uses self-signed certificates"). Source: `AW aiowebostv/webos_client.py` L137-176.
  ```python
  WS_PORT = 3000
  WSS_PORT = 3001
  ...
  try:
      uri = f"ws://{self.host}:{WS_PORT}"
      return await self._ws_connect(uri, MAIN_WS_MAX_MSG_SIZE)
  # ClientConnectionError is raised when firmware reject WS_PORT
  # WSServerHandshakeError is raised when firmware enforce using ssl
  except (aiohttp.ClientConnectionError, aiohttp.WSServerHandshakeError):
      uri = f"wss://{self.host}:{WSS_PORT}"
  ```
  `MAIN_WS_MAX_MSG_SIZE = 8 * 1024 * 1024  # 8MB, based on channel list size`. Heartbeat is 5 s.
- lgtv2 2.x tries `wss://host:3001` first, then `ws://host:3000` (`LGTV2 index.js`). Its README says: "Newer TVs (firmware from 2023 on) only accept **secure** websocket connections on port 3001 … TVs from before 2018 only offer `ws://<tv>:3000`."
- webOS 26 C6 (43.21.60): "Port 3000 is completely dead on this firmware — the TV accepts the TCP connection then immediately closes it … Only port 3001 (WSS) works." (zedr32, https://github.com/home-assistant/core/issues/172703#issuecomment-5343587724)
- Certificate pinning option (lgtv2 `LG_ISSUER_FINGERPRINTS`, `LGTV2 index.js` L36-49): every TV presents the same leaf "LGE TV SSG", issued by "LGE SSG Intermediate CA":
  ```js
  // LGE SSG Intermediate CA
  'E2:BD:64:64:D3:F5:1C:1B:95:B7:69:7D:9D:67:73:C3:3D:94:12:EB:A0:29:9C:56:8C:34:93:7D:3F:E6:8A:A0',
  // LGE TV SSG (leaf)
  '11:C5:B1:C5:90:77:50:AB:B9:DA:2A:66:65:CC:CE:2B:B2:88:A5:83:F4:5A:33:39:E7:1F:87:BF:2F:80:85:52',
  ```
  The comment says "seen on 2018-2023 models … firmware up to late 2025". Whether 2026 firmware still uses the same certificate is **UNCONFIRMED**.

### 1.2 `hello` (client → TV)

aiowebostv (`AW aiowebostv/webos_client.py` L184-196):
```python
await ws.send_json({"id": "hello", "type": "hello", "payload": {}})
...
if response["type"] == "hello":
    self.tv_info.hello = response["payload"]
else:
    error = f"Invalid response type {response}"
    raise WebOsTvCommandError(error)
```
Why `payload` is required (aiowebostv PR #500, https://github.com/home-assistant-libs/aiowebostv/pull/500): "New TV firmware (33.20.x) does not respond if there is no payload key in the hello request".

- bscpylgtv sends hello **without** `payload`, and only when `get_hello_info=True`: `await ws.send(json.dumps({"id": "hello", "type": "hello"}))` (`BSC bscpylgtv/webos_client.py` L234-243).
- LGTV Companion 5.7.0 never sends `hello`; it sends `register` straight after the WebSocket handshake (`LGTVC LGTV Companion Service/web_os_client.cpp` L520-525).
- ConnectSDK sends hello with app-info fields (`sdkVersion`, `deviceModel`, `OSVersion`, `resolution`, `appId`, `appName`, `appRegion`). It compares the returned `deviceUUID` with the stored one and clears the stored client key if they differ (`CSDK src/com/connectsdk/service/webos/WebOSTVServiceSocketClient.java` L409-433).

### 1.3 TV hello response (real captures)

webOS 26 (G5, 2026-08-18) from https://github.com/home-assistant/core/issues/172703#issuecomment-5333190080:
```
{'type': 'hello', 'payload': {'protocolVersion': 1, 'deviceType': 'tv', 'deviceOS': 'webOS', 'deviceOSVersion': '4.1.0', 'deviceOSReleaseVersion': '11.2.0', 'deviceUUID': '<redacted>', 'pairingTypes': ['PIN', 'PROMPT', 'COMBINED', 'LGSWITCH-PIN']}}
```
webOS 25 (2025-07-05) from https://github.com/home-assistant/core/issues/147557#issuecomment-3038907985:
```
{'type': 'hello', 'payload': {'protocolVersion': 1, 'deviceType': 'tv', 'deviceOS': 'webOS', 'deviceOSVersion': '4.1.0', 'deviceOSReleaseVersion': '10.2.0', 'deviceUUID': 'faef286a-2ba1-6198-9ce2-adbf2d60af53', 'pairingTypes': ['PIN', 'PROMPT', 'COMBINED', 'LGSWITCH-PIN']}}
```
webOS 6 (2024-11-30) from https://github.com/home-assistant/core/issues/131981:
```
{"type":"hello","payload":{"protocolVersion":1,"deviceType":"tv","deviceOS":"webOS","deviceOSVersion":"4.1.0","deviceOSReleaseVersion":"6.4.0","deviceUUID":"1e2aa17c-1d53-03db-7e5e-a779f6c18cc5","pairingTypes":["PIN","PROMPT","COMBINED"]}}
```
webOS 4.4.3 (C8), from the diagnostics in https://github.com/home-assistant/core/issues/183602: `deviceOSReleaseVersion: "4.4.3"`, `deviceOSVersion: "4.1.0"`.

Notes:
- `deviceOSVersion` is `"4.1.0"` on every capture from webOS 4 to webOS 26, so it is useless for version detection. **Use `deviceOSReleaseVersion`.** It is `11.x` on webOS 26 and `10.x` on webOS 25. For example, bscpylgtv's doc header "OLED C2 (2022) firmware v43.21.74, webOS v11.2.0", and "webOS 26 / 11.2.0-35" from HA #172703.
- `LGSWITCH-PIN` first appears on webOS 25/26. bscpylgtv's constant is `PAIRING_TYPES = ("PROMPT", "PIN", "COMBINED", "LGSWITCH-PIN")` (`BSC bscpylgtv/constants.py`).
- HA #183602 recommends `hello.deviceOSReleaseVersion` for version checks because `getCurrentSWInformation` (which homebridge parses `product_name` from) is unavailable.

### 1.4 Does aiowebostv call `ssap://system/getSystemInfo` before `register`? **Yes.**

`AW aiowebostv/webos_client.py` L198-216 and L362-365:
```python
async def _get_pre_reg_system_info(self, ws: ClientWebSocketResponse) -> None:
    """Get system info before registration.

    Newer webOS versions require system info to be retrieved before registration.
    """
    request = {
        "id": "get_sys_info",
        "type": "request",
        "uri": f"ssap://{ep.GET_SYSTEM_INFO}",
        "payload": {},
    }
    ...
    with suppress(WebOsTvResponseTypeError):
        self.tv_info.system = self._parse_response(response)
...
main_ws = await self._create_main_ws()
await self._get_hello_info(main_ws)
await self._get_pre_reg_system_info(main_ws)
await self._check_registration(main_ws)
```
Evidence that calling it after registration fails on webOS 25 (HA #147557): after `registered`, `ssap://system/getSystemInfo` returned `{"type":"error","id":1,"error":"401 insufficient permissions","payload":{}}`. Fixed by aiowebostv PR #501 (https://github.com/home-assistant-libs/aiowebostv/pull/501).

Pre-registration response, real (webOS 26 beta, https://github.com/home-assistant/core/issues/172703#issuecomment-4591674641):
```
{'type': 'response', 'id': 'get_sys_info', 'payload': {'returnValue': True, 'features': {'dvr': True}, 'receiverType': 'DVB', 'modelName': 'OLED65C25LB', 'serialNumber': '304MAYYDPF18', 'programMode': True}}
```

### 1.5 `register` message shape

aiowebostv (`AW aiowebostv/handshake.py`, plus `registration_msg()` at `AW aiowebostv/webos_client.py` L131-135):
```python
REGISTRATION_MESSAGE = {
    "type": "register",
    "id": "register_0",
    "payload": REGISTRATION_PAYLOAD,   # {"forcePairing": False, "manifest": {...}, "pairingType": "PROMPT"}
}
...
handshake = copy.deepcopy(REGISTRATION_MESSAGE)
handshake["payload"]["client-key"] = self.client_key
```
Note: when there is no stored key, aiowebostv and bscpylgtv send `"client-key": null`. LGTVC, lgtv2 and ConnectSDK **omit** the field. All four styles are used against real TVs.

bscpylgtv (`BSC bscpylgtv/webos_client.py` L194-206):
```python
return {
    "type": "register",
    "id": "register_0",
    "payload": {
        "client-key": self.client_key,
        "forcePairing": False,
        "manifest": self.manifest,
        "pairingType": self.pairing_type,
    },
}
```
The full manifests are in section 2.

---

## 2. Manifests

### 2.1 aiowebostv, current (v0.9.2 / v0.10.0 / main), full `register` message

Source: `AW aiowebostv/handshake.py`. This is the complete file content, rendered as JSON.
```json
{
  "type": "register",
  "id": "register_0",
  "payload": {
    "forcePairing": false,
    "manifest": {
      "appVersion": "1.1",
      "manifestVersion": 1,
      "permissions": [
        "APP_TO_APP",
        "CLOSE",
        "CONTROL_AUDIO",
        "CONTROL_DISPLAY",
        "CONTROL_INPUT_JOYSTICK",
        "CONTROL_INPUT_MEDIA_PLAYBACK",
        "CONTROL_INPUT_MEDIA_RECORDING",
        "CONTROL_INPUT_TEXT",
        "CONTROL_INPUT_TV",
        "CONTROL_MOUSE_AND_KEYBOARD",
        "CONTROL_POWER",
        "CONTROL_TV_SCREEN",
        "LAUNCH",
        "LAUNCH_WEBAPP",
        "READ_APP_STATUS",
        "READ_COUNTRY_INFO",
        "READ_CURRENT_CHANNEL",
        "READ_INPUT_DEVICE_LIST",
        "READ_INSTALLED_APPS",
        "READ_LGE_SDX",
        "READ_LGE_TV_INPUT_EVENTS",
        "READ_NETWORK_STATE",
        "READ_NOTIFICATIONS",
        "READ_POWER_STATE",
        "READ_RUNNING_APPS",
        "READ_SETTINGS",
        "READ_TV_CHANNEL_LIST",
        "READ_TV_CURRENT_TIME",
        "READ_UPDATE_INFO",
        "SEARCH",
        "TEST_OPEN",
        "TEST_PROTECTED",
        "TEST_SECURE",
        "UPDATE_FROM_REMOTE_APP",
        "WRITE_NOTIFICATION_ALERT",
        "WRITE_NOTIFICATION_TOAST",
        "WRITE_SETTINGS"
      ]
    },
    "pairingType": "PROMPT"
  }
}
```
- 37 permissions, `manifestVersion: 1`, `appVersion: "1.1"`. There are **no `signed`, `signatures`, `appId`, `vendorId`, `localizedAppNames` or `deviceName`** fields. The file's only other content is the docstring `"""webOS registration payload."""`.
- PR #719 diff (https://github.com/home-assistant-libs/aiowebostv/pull/719) removed `SIGNATURE`, `"signatures": [{"signature": SIGNATURE, "signatureVersion": 1}]` and the whole `"signed": {"appId": "com.lge.test", "created": "20140509", "localizedAppNames": {...}, "localizedVendorNames": {"": "LG Electronics"}, "permissions": [16 items], "serial": "2f930e2d2cfe083771f68e4fe7bb07", "vendorId": "com.lge"}` block. It merged the signed-only permissions into the plain list and sorted it alphabetically.
- PR #719 also made software info optional (`AW aiowebostv/webos_client.py` L271-273):
  ```python
  # Try to get software info, most likely to fail with new handshake
  with suppress(WebOsTvResponseTypeError):
      self.tv_info.software = await self.get_software_info()
  ```
- PR #719 body (thecode): "The following permissions existed only in the `signed` section: READ_LGE_SDX, READ_LGE_TV_INPUT_EVENTS, READ_NOTIFICATIONS, READ_TV_CURRENT_TIME, READ_UPDATE_INFO, SEARCH, TEST_SECURE, UPDATE_FROM_REMOTE_APP, WRITE_NOTIFICATION_ALERT, WRITE_SETTINGS … For `aiowebostv` existing methods the only permission that is missing is `READ_UPDATE_INFO` … I have tested all existing method that are in use by Home Assistant and they all work without the need to re-pair the TV." Captured error:
  ```
  recv: ... data='{"type":"error","id":1,"error":"401 insufficient permissions","payload":{}}'   (for ssap://com.webos.service.update/getCurrentSWInformation)
  ```

### 2.2 LGTV Companion 5.7.0 unsigned manifest (`LG_HANDSHAKE_*_V3`)

Source: `LGTVC Common/lg_api.h` L2-4 (comment: `// Even simpler manifest (signed section removed and permissions adjusted)`). Paired variant, decoded from the C string:
```json
{
  "type": "register",
  "id": "register_0",
  "payload": {
    "forcePairing": false,
    "pairingType": "PROMPT",
    "client-key": "#CLIENTKEY#",
    "manifest": {
      "manifestVersion": 1,
      "permissions": ["LAUNCH","LAUNCH_WEBAPP","APP_TO_APP","CLOSE","TEST_OPEN","TEST_PROTECTED","CONTROL_AUDIO","CONTROL_DISPLAY","CONTROL_INPUT_JOYSTICK","CONTROL_INPUT_MEDIA_RECORDING","CONTROL_INPUT_MEDIA_PLAYBACK","CONTROL_INPUT_TV","CONTROL_POWER","CONTROL_TV_SCREEN","READ_APP_STATUS","READ_CURRENT_CHANNEL","READ_INPUT_DEVICE_LIST","READ_NETWORK_STATE","READ_RUNNING_APPS","READ_TV_CHANNEL_LIST","WRITE_NOTIFICATION_TOAST","READ_POWER_STATE","READ_COUNTRY_INFO","READ_SETTINGS","CONTROL_INPUT_TEXT","CONTROL_MOUSE_AND_KEYBOARD","WRITE_SETTINGS","WRITE_NOTIFICATION_ALERT","READ_INSTALLED_APPS","READ_RUNNING_APPS","READ_UPDATE_INFO"]
    }
  }
}
```
- `LG_HANDSHAKE_NOTPAIRED_V3` is identical without `"client-key"`. **There is no `appVersion`.** It has 31 entries but 30 unique, because `READ_RUNNING_APPS` appears twice. The same JSON is published in the Aug 16 update of https://github.com/JPersson77/LGTVCompanion/issues/351.
- Selection logic (`LGTVC LGTV Companion Service/web_os_client.cpp` L170-176): `NOTPAIRED_V3` if the session key is empty, otherwise `PAIRED_V3` with `#CLIENTKEY#` replaced.
- History in the same file:
  - `V2` (v5.5.0, "Updated manifest (original manifest minus signature)"): kept a self-made `signed` block (`appId "com.lgtvc.app"`, `serial "lgtvc-webos26"`) but no `signatures`. Issue #351 says this pairs but "elevated permissions are not granted", so `getPointerInputSocket` returns 401.
  - `V1`: the original signed `com.lge.test` manifest.
- Issue #351 update (Aug 16 2026, JPersson77): "Removing the signed section from the manifest and including the required permissions in the generic (outer) permissions block seems to restore all necessary access. The design intent of the original manifest seems to be to allow LG first-party remote apps and certified partners to present a signed manifest and get the elevated permission set granted by signature verification with no prompt needed. The outer permissions is what the user accept with the on-screen prompt."
- till69 in #351 (https://github.com/JPersson77/LGTVCompanion/issues/351#issuecomment-5282632782): "With a simple manifest (no signed section), getPointerInputSocket is working again (needs permission CONTROL_MOUSE_AND_KEYBOARD)".

### 2.3 Union and differences

| | aiowebostv (37) | LGTVC 5.7.0 (30 unique) |
|---|---|---|
| Only in aiowebostv | `READ_LGE_SDX`, `READ_LGE_TV_INPUT_EVENTS`, `READ_NOTIFICATIONS`, `READ_TV_CURRENT_TIME`, `SEARCH`, `TEST_SECURE`, `UPDATE_FROM_REMOTE_APP` | |
| Only in LGTVC | | none |
| `appVersion` | `"1.1"` | absent |

**Union = exactly the aiowebostv list (37 entries):**
```json
["APP_TO_APP","CLOSE","CONTROL_AUDIO","CONTROL_DISPLAY","CONTROL_INPUT_JOYSTICK","CONTROL_INPUT_MEDIA_PLAYBACK","CONTROL_INPUT_MEDIA_RECORDING","CONTROL_INPUT_TEXT","CONTROL_INPUT_TV","CONTROL_MOUSE_AND_KEYBOARD","CONTROL_POWER","CONTROL_TV_SCREEN","LAUNCH","LAUNCH_WEBAPP","READ_APP_STATUS","READ_COUNTRY_INFO","READ_CURRENT_CHANNEL","READ_INPUT_DEVICE_LIST","READ_INSTALLED_APPS","READ_LGE_SDX","READ_LGE_TV_INPUT_EVENTS","READ_NETWORK_STATE","READ_NOTIFICATIONS","READ_POWER_STATE","READ_RUNNING_APPS","READ_SETTINGS","READ_TV_CHANNEL_LIST","READ_TV_CURRENT_TIME","READ_UPDATE_INFO","SEARCH","TEST_OPEN","TEST_PROTECTED","TEST_SECURE","UPDATE_FROM_REMOTE_APP","WRITE_NOTIFICATION_ALERT","WRITE_NOTIFICATION_TOAST","WRITE_SETTINGS"]
```
Specific permissions:
- `CONTROL_MOUSE_AND_KEYBOARD`: present in both (needed for `getPointerInputSocket`). Also present in bscpylgtv's `manifest-comp.json` and in lgtv2's fallback.
- `WRITE_SETTINGS`: present in both. Absent from bscpylgtv `manifest-comp.json` and from lgtv2's fallback.
- `WRITE_NOTIFICATION_ALERT`: present in both. Absent from bscpylgtv `manifest-comp.json` and from lgtv2's fallback.

Do the signed-only permissions do anything when listed unsigned? perseus177 (https://github.com/home-assistant/core/issues/172703#issuecomment-5333412891): "I re-paired with a manifest that keeps no signature but moves **all 16 `signed.permissions` into the unsigned `manifest.permissions`** array (27 → 37 entries …). Pairing succeeds, prompt shown, key issued — and the elevated permissions are still **not** granted: `com.webos.service.update/getCurrentSWInformation -> 401 insufficient permissions`." He also found that `listApps` works unsigned even though `READ_INSTALLED_APPS` was signed-only.

Whether `WRITE_NOTIFICATION_ALERT` is needed for the luna workaround is disputed. JDFS404 in bscpylgtv PR #9 first said it was required, then retracted that on 2026-09-19 (https://github.com/chros73/bscpylgtv/pull/9#issuecomment-5745052257): "I paired fresh with `manifest-comp.json` minus that permission (28 perms) … `set_settings picture {"backlight": 37} -> {'returnValue': True}` … So the 401 I reported earlier was another symptom of my stuck state". Recommendation: **include it anyway**, as aiowebostv and LGTVC do. It costs nothing.

### 2.4 Other current manifests, for reference

- bscpylgtv default `MANIFEST` (`BSC bscpylgtv/manifest.py`) is **still the old signed `com.lge.test` manifest**, plus `"deviceName": "bscpylgtv"` and `READ_STORAGE_DEVICE_LIST`. Its webOS 26 compatibility manifest is `BSC docs/manifests/manifest-comp.json`: unsigned, `appVersion "1.1"`, `manifestVersion 1`, `deviceName "bscpylgtv"`, 28 permissions. Selected with `-m`. Its README describes it as a "compatibility manifest for webOS26 and later versions that provides limited functionality".
- lgtv2 2.0.2 (`LGTV2 index.js` L19-25) sends the signed `pairing.json` first. On `/403.*blacklisted certificate detected/i` it retries with:
  ```js
  function unsignedPairing() {
      const pairing = JSON.parse(JSON.stringify(pairingTemplate));
      delete pairing.manifest.signed;
      pairing.manifest.appVersion = '1.0';
      pairing.manifest.permissions.push('CONTROL_INPUT_TEXT', 'CONTROL_MOUSE_AND_KEYBOARD');
      return pairing;
  }
  ```
  (`signatures` is **not** deleted; see TL;DR item 7.) The CHANGELOG for 2.0.2 says: "request `CONTROL_INPUT_TEXT` and `CONTROL_MOUSE_AND_KEYBOARD` too, so the pointer/button socket (`getPointerInputSocket`) no longer fails with `401 insufficient permissions` after unsigned pairing".
- homebridge-webos-tv 2.5.0 (`HB lib/ws/pairing.js`) uses a **different signed manifest**: `deviceName "LG Mobile"`, `allowFullPagePopup: true`, `allowFirstuseConnection: true`, `signed.created "20150330"`, `appId "com.lge.test"`, `serial "49d0233e26a3fc4d3113ddbc22a8686a"`, 42 signed permissions, and a new `signatures[0].signature`. Its JWS header is still `{"algorithm":"RSA-SHA256","keyId":"test-signing-cert","signatureVersion":1}`. Origin, from homebridge #574 (https://github.com/merdok/homebridge-webos-tv/issues/574#issuecomment-5302732612): "an updated registration manifest from the current LG ThinQ iOS client"; and #issuecomment-5557869060: "decompiling the Android app LGThinQ". Reported working on G5 43.21.71, C3 webOS 26, CX and C1. **Risk: LG proprietary signed blob, could be blacklisted next, IP concern.**

---

## 3. Pairing responses

### 3.1 PROMPT flow (real captures)

aiowebostv logic (`AW aiowebostv/webos_client.py` L218-245):
```python
await ws.send_json(self.registration_msg())
async with asyncio.timeout(RECEIVE_TIMEOUT):
    response = await ws.receive_json()
if (
    response["type"] == "response"
    and response["payload"]["pairingType"] == "PROMPT"
):
    response = await ws.receive_json(timeout=RECEIVE_TIMEOUT)
    ...
    if response["type"] == "error":
        raise WebOsTvPairError(response["error"])
    if response["type"] == "registered":
        self.client_key = response["payload"]["client-key"]

if not self.client_key:
    error = "Client key not set, pairing failed."
    raise WebOsTvPairError(error)
```
`RECEIVE_TIMEOUT = 10`. aiowebostv waits only **10 s** for the user to accept the prompt. Pick your own, longer, timeout.

Wire sequence, webOS 26 G5 with the unsigned manifest (megaoctet, https://github.com/home-assistant/core/issues/172703#issuecomment-5338791716):
```
>>> register sent
<<< {"type": "response", "id": "register",
     "payload": {"pairingType": "PROMPT", "returnValue": true}}
<<< {"type": "registered", "id": "register",
     "payload": {"client-key": "<redacted>"}}
```
(The `id` is echoed back. megaoctet's own client used `"register"` instead of `"register_0"`.)

With a **valid stored key** the TV answers `registered` directly, without the intermediate `response`. aiowebostv and bscpylgtv handle that because the first reply is not `response`/PROMPT, and the stored key is kept. The lgtv2 mock models it as `send(ws, {id, type: 'registered', payload: {'client-key': key}})` (`LGTV2 test/mock-tv.js`). That is a mock, but the behaviour is consistent with the real logs in HA #147557 and #131981.

### 3.2 User declines or times out

Real capture, webOS 6 (https://github.com/home-assistant/core/issues/131981):
```
19:46:07.295 recv(...): registration
19:48:07.307 pairing(10.10.27.142): type: error, error: 403 cancelled
exception(...): WebOsTvPairError('403 cancelled')
```
The 2-minute gap suggests that **"403 cancelled" is also what a prompt timeout returns**. Whether decline and timeout produce different strings is **UNCONFIRMED**. lgtv2's source comment: `// e.g. "403 cancelled" when the user declines on the TV` (`LGTV2 index.js` L686). Its mock sends `{id, type: 'error', error: '403 cancelled'}`.

LGTVC logs `"User rejected or cancelled the pairing prompt"` when `error` is empty, and otherwise logs the error text (`LGTVC .../web_os_client.cpp` L633-644).

### 3.3 webOS 26 signed-manifest rejection

```json
{"type": "error", "id": "register_0", "error": "403 Pairing rejected: blacklisted certificate detected", "payload": {}}
```
Sources: https://github.com/home-assistant/core/issues/172703#issuecomment-5269324233 (G6, 43.21.60) and https://github.com/JPersson77/LGTVCompanion/issues/347 (C2, 43.00.92): `< < < RECV < < <: {"type":"error","id":"register_0","error":"403 Pairing rejected: blacklisted certificate detected","payload":{}}`.

- It arrives within about 13 ms of the `register`, with no prompt shown. "all four advertised pairing types (PROMPT, PIN, COMBINED, LGSWITCH-PIN) rejected identically" (megaoctet, same thread).
- perseus177's controlled test: A, the stock manifest with `com.lge.test` and `signatures`, gives 403. B, `signatures` removed and `appId` still `com.lge.test`, pairs successfully. C, `signatures` kept with a neutral `appId`, gives 403. D, no `signatures`, pairs. Conclusion: "The trigger is **the signature block alone**."
- With a pre-update key plus the signed manifest: `403 … blacklisted` again, then `{"type":"error","id":"t1","error":"401 insufficient permissions (not registered)"}` on later requests. "The certificate check happens **before** the key is considered."
- If your client ignores the 403 and continues, as aiowebostv does when a stored key exists, the next request fails with `{"type":"error","id":0,"error":"401 insufficient permissions (not registered)","payload":{}}` (https://github.com/home-assistant/core/issues/172703#issuecomment-4591674641).

### 3.4 Invalid or rejected stored client key

There is no primary capture of a TV answering an unknown key. Code-level evidence:
- LGTVC treats a non-`registered`, non-`error` reply to `register_0` (that is, the intermediate `response` with `pairingType`) as "key invalid" and re-pairs (`LGTVC .../web_os_client.cpp` L645-654):
  ```cpp
  else // Device is unregistered or pairing key is invalid.
  {
      if(device_settings_.session_key != "") // Pairing key is invalid. Force re-pairing
      {
          WARNING("Pairing key was invalid. Re-pairing...");
          device_settings_.session_key = "";
          webos_handshake_ = tools::narrow(LG_HANDSHAKE_NOTPAIRED_V3);
          send(webos_handshake_);
  ```
- lgtv2 saves the new key when `res['client-key'] !== that.clientKey` (`LGTV2 index.js` L690-697). This implies the TV may prompt again and issue a different key.
- Inference, **UNCONFIRMED by capture**: an unknown key makes the TV fall back to the normal prompt flow (`response`/`pairingType`, then `registered` with a new key). It does not produce an error.
- Key reuse across manifest change is confirmed. On a non-blacklisted G4 (aiowebostv #728): "The same unchanged key is granted `WRITE_SETTINGS` when connecting via 0.9.1 and denied it via 0.10.0 … Permissions are evaluated per connection, from the manifest sent at register time — they are not baked into the client key." HA users on 2026.8.3 also confirm: "I did not need to do anything, the integration worked itself!"

### 3.5 PIN pairing

bscpylgtv is the only one of the five reference projects that implements it (v0.5.3, commit `a47a9d8f8b9254b08ac19c9247fd8068a3d926d0` "Add support for all available pairing types"). `BSC bscpylgtv/webos_client.py` L245-271:
```python
await ws.send(json.dumps(self.registration_msg()))   # payload.pairingType = "PIN" (or "LGSWITCH-PIN")
raw_response = await ws.recv()
response = json.loads(raw_response)

if response["type"] == "response" and response["payload"]["pairingType"] in [PAIRING_TYPES[0], PAIRING_TYPES[2]]:   # PROMPT, COMBINED
    raw_response = await ws.recv()
    ...
elif response["type"] == "response" and response["payload"]["pairingType"] in [PAIRING_TYPES[1], PAIRING_TYPES[3]]:   # PIN, LGSWITCH-PIN
    pin = input("Enter PIN: ")
    payload = {
        "type": "request",
        "id": "register_1",
        "uri": f"ssap://{ep.SET_PIN}",          # "pairing/setPin"
        "payload": {"pin": pin}
    }
    await ws.send(json.dumps(payload))
    raw_response = await ws.recv()
    response = json.loads(raw_response)
    if response["type"] == "registered":
        self.client_key = response["payload"]["client-key"]
```
ConnectSDK uses the same request (`CSDK .../WebOSTVServiceSocketClient.java` L641-690): `payload.put("pairingType", "PIN")` in register, then `{"type":"request","id":<n>,"uri":"ssap://pairing/setPin","payload":{"pin":"<pin>"}}`.

**UNCONFIRMED**: whether the TV sends a `response` to the `setPin` request before `registered` (bscpylgtv reads one message only), the PIN's type (string vs int), and whether PIN pairing works with an unsigned manifest on webOS 26. lgtv2's maintainer (https://github.com/hobbyquaker/lgtv2/issues/44#issuecomment-5370241796): "PIN pairing needs a second `setPin` step in the register flow that I cannot test".

---

## 4. Pointer input socket

### 4.1 Request and response

aiowebostv (`AW aiowebostv/webos_client.py` L247-256):
```python
sockres = await self.request(ep.INPUT_SOCKET)   # "com.webos.service.networkinput/getPointerInputSocket"
inputsockpath = sockres["socketPath"]
return await self._ws_connect(inputsockpath, INPUT_WS_MAX_MSG_SIZE)   # 8 KB, TLS verification off
```
Real responses:
- webOS 25 over wss (https://github.com/home-assistant/core/issues/147557#issuecomment-3038907985):
  `{"type":"response","id":0,"payload":{"returnValue":true,"socketPath":"wss://192.168.1.109:3001/resources/3f9df53e77530af1c35bb4a88b0136c255ac5b7d/netinput.pointer.sock"}}`
- webOS 6 over ws (https://github.com/home-assistant/core/issues/131981):
  `{"type":"response","id":0,"payload":{"socketPath":"ws://10.10.27.142:3000/resources/b9f6b99d01754b1bc9ce75e541db8de674da4378/netinput.pointer.sock","returnValue":true}}`

The scheme and port follow the main connection. Without `CONTROL_MOUSE_AND_KEYBOARD` the request returns `401 insufficient permissions` (lgtv2 PR #52, LGTVC #351). It is granted with the unsigned manifest on webOS 26 (perseus177's audit: "`getPointerInputSocket` ✅ socket path returned").

### 4.2 Message formats (text frames, LF line endings, terminated by an empty line)

aiowebostv (`AW aiowebostv/webos_client.py` L758-776), identical in bscpylgtv (`BSC bscpylgtv/webos_client.py` L841-864):
```python
message = f"type:button\nname:{name}\n\n"
message = f"type:move\ndx:{d_x}\ndy:{d_y}\ndown:{down}\n\n"   # down defaults to 0
message = "type:click\n\n"
message = f"type:scroll\ndx:{d_x}\ndy:{d_y}\n\n"
```
lgtv2 generic builder (`LGTV2 index.js` L290-305): `key:value` lines, "with an extra blank line to terminate", joined with `'\n'` plus `'\n\n'`.

LGTVC keep-alive on the pointer socket: `button_command = "type:ping\n\n";` (`LGTVC LGTV Companion Service/button_client.cpp` L217). Only `\n` is used, never `\r\n`. The TV sends nothing on this socket ("We are not expecting any messages from the input connection", aiowebostv L422-431).

### 4.3 Button names

aiowebostv `BUTTONS` (`AW aiowebostv/buttons.py`), verbatim:
```
LEFT RIGHT UP DOWN RED GREEN YELLOW BLUE CHANNELUP CHANNELDOWN VOLUMEUP VOLUMEDOWN PLAY PAUSE STOP REWIND FASTFORWARD ASTERISK BACK EXIT ENTER AMAZON NETFLIX 3D_MODE
AD  # Audio Description toggle
ASPECT_RATIO  # Quick Settings Menu - Aspect Ratio
CC  # Closed Captions
DASH  # Live TV
GUIDE
HOME  # Home Dashboard
INFO  # Info button
INPUT_HUB  # Home Dashboard
LIST  # Live TV
LIVE_ZOOM  # Live Zoom
MAGNIFIER_ZOOM  # Focus Zoom
MENU  # Quick Settings Menu
MUTE
MYAPPS  # Home Dashboard
POWER  # Power button
PROGRAM  # TV Guide
QMENU  # Quick Settings Menu
RECENT  # Home Dashboard - Recent Apps
RECORD
SAP  # Multi Audio Setting
SCREEN_REMOTE  # Screen Remote
TELETEXT TEXTOPTION 0 1 2 3 4 5 6 7 8 9
```
bscpylgtv adds the following (`BSC bscpylgtv/buttons.py`): `ADVANCE_SETTING`, `ALEXA`, `EMANUAL # User Guide`, `EZPIC # Pictore mode preset panel`, `EZ_ADJUST # EzAdjust Service Menu`, `EYE_Q # Energy saving panel`, `HCEC # SIMPLINK toggle`, `IN_START # InStart Service Menu`, `IVI`, `RECLIST`, `SEARCH`, `SOCCER`, `TIMER # Sleep Timer panel`, `TV`, `TWIN`, `UPDOWN # Always Ready app`, `USP`, `YANDEX`. Its `SCREEN_REMOTE` comment is "More Actions panel". LGTVC's list (`LGTVC Common/lg_api_buttons.h`) equals bscpylgtv's plus `BENDABLE`.

homebridge also lists `CLICK` (it sends `type:click`), `FAVORITES`, `FLASHBACK`, `GOTOPREV`, `GOTONEXT`, `EJECT`, `BS*`, `CS1*`, `CS2*`, `TER*`, `3DIGIT_INPUT`, `BML_DATA`, `JAPAN_DISPLAY` (`HB lib/LgTvController.js` L67).

Observations:
- **MENU vs QMENU**: both are annotated "Quick Settings Menu" in aiowebostv and bscpylgtv. No source names a button for the full settings menu. Alternative from `BSC README.md`: `launch_app_with_params com.palm.app.settings "{\"target\": \"PictureMode\"}"`, which is `ssap://system.launcher/launch` with `{"id":"com.palm.app.settings","params":{"target":"PictureMode"}}`. Other `target` values are **UNCONFIRMED**.
- Verified working on a C2 running webOS 26 with an unsigned manifest (https://github.com/home-assistant/core/issues/172703#issuecomment-4854461050): LEFT RIGHT DOWN UP HOME MENU BACK ENTER DASH INFO EXIT MUTE RED GREEN BLUE YELLOW VOLUMEUP VOLUMEDOWN CHANNELUP CHANNELDOWN PLAY PAUSE NETFLIX GUIDE AMAZON 0-9.
- Seen on the test TV (LG OLED42C54LA, webOS 10.3.1, unsigned manifest, 2026-10-06): IN_START opens the InStart service menu's code prompt. No code was entered.
- LGTV Companion's README note that virtual button presses were "deprecated and made obsolete by firmware changes from LG in 2026" was reversed in v5.7.0: "restored all functionality, including virtual button press functionality".

---

## 5. Volume

### 5.1 Reading volume (aiowebostv and bscpylgtv handle both shapes)

`AW aiowebostv/webos_client.py` L938-966:
```python
async def get_muted(self) -> bool | None:
    status = await self.get_audio_status()      # audio/getStatus
    return status.get("mute")
...
async def get_volume(self) -> int | None:
    res = await self.request(ep.GET_VOLUME)     # audio/getVolume
    return res.get("volumeStatus", res).get("volume")
...
await callback(payload.get("volumeStatus", payload).get("volume"))
```

### 5.2 Payload shapes (real captures)

- **New (webOS 5+), `audio/getVolume` subscription**, from https://github.com/openhab/openhab-addons/issues/9000 (2020 GX):
  ```json
  {"type":"response","id":2,"payload":{"volumeStatus":{"activeStatus":true,"adjustVolume":true,"maxVolume":100,"muteStatus":false,"volume":11,"soundOutput":"tv_speaker","cause":"volumeUp"},"returnValue":true,"callerId":"com.webos.platformstarfish"}}
  ```
  LG CX on webOS 5.0, from https://github.com/bendavid/aiopylgtv/issues/26: `{'volumeStatus': {'activeStatus': True, 'adjustVolume': True, 'maxVolume': 100, 'muteStatus': False, 'volume': 9, 'mode': 'normal', 'soundOutput': 'tv_speaker'}, 'returnValue': True, 'callerId': 'secondscreen.client'}`
- **Both shapes over one connection**, aiowebostv with soundbar output `lgSoundSync`, 2025-12-31 (https://github.com/home-assistant/core/issues/156874#issuecomment-3702518319):
  ```
  {"type":"response","id":5,"payload":{"volumeStatus":{"cause":"setVolume","mode":"normal","adjustVolume":true,"activeStatus":true,"muteStatus":false,"volume":25,"soundOutput":"lgSoundSync","maxVolume":100},"returnValue":true,"callerId":"secondscreen.client"}}
  {"type":"response","id":25,"payload":{"volume":25,"returnValue":true,"soundOutput":""}}                    <- reply to setVolume
  {"type":"response","id":9,"payload":{"volumeStatus":{...same...},"returnValue":true,"callerId":"secondscreen.client","mute":false,"volume":25}}
  ```
  One subscription carries only `volumeStatus`. The other carries `volumeStatus` plus flat `mute` and `volume`; that is the `audio/getStatus` shape that aiowebostv reads `mute` from. Mapping ids to URIs is my inference, because aiowebostv subscribes from an unordered set.
- **Old webOS** (quoted in https://github.com/hobbyquaker/node-red-contrib-lgtv/issues/51): `{"returnValue":true,"scenario":"mastervolume_tv_speaker","volume":98,"mute":false}`. lgtv2's normalizer comment (`LGTV2 index.js` L246-253): "Older firmware answers audio/getVolume with {volume, muted, changed: [...]}, newer firmware with {volumeStatus: {volume, muteStatus, ...}} and no `changed` array". Its 1.9.0 CHANGELOG adds that webOS 6.0 omits `subscribed` on subscription responses.
- Robust parse: volume = `volumeStatus.volume ?: volume`; muted = `volumeStatus.muteStatus ?: mute ?: muted`; output = `volumeStatus.soundOutput ?: soundOutput`. `maxVolume` and `adjustVolume` are present on newer firmware.

### 5.3 Soundbar or external output

- lgtv2 README table: "`ssap://audio/volumeUp` / `volumeDown` | needed for ARC/eARC soundbars that report `volume: -1`". homebridge clamps: `return this.volume < 0 ? 0 : this.volume;` (`HB lib/LgTvController.js` ~L650). **UNCONFIRMED**: I found no raw TV capture of `volume: -1`. Also unconfirmed whether `adjustVolume:false` appears in that case.
- HA disables absolute volume per output (`HA homeassistant/components/webostv/media_player.py` L121-131): `external_speaker` gets mute and step only; `lineout` gets neither; every other output also gets `VOLUME_SET`.
- aiowebostv delays consecutive steps for `SOUND_OUTPUTS_TO_DELAY_CONSECUTIVE_VOLUME_STEPS = {"external_arc"}` (`AW aiowebostv/webos_client.py` L36, L981-997).
- Sound output values from lgtv2's README: `tv_speaker`, `external_arc`, `external_optical`, `bt_soundbar`, `headphone`, `lineout`. Homebridge #128 adds `tv_external_speaker`, `tv_speaker_headphone`, `external_speaker`. HA #156874 shows `lgSoundSync`.
- Sound output endpoint: `ssap://com.webos.service.apiadapter/audio/getSoundOutput` (subscribe; payload `soundOutput`). Change it with `.../changeSoundOutput {"output": "<value>"}` (aiowebostv L1041-1056).

### 5.4 Write payloads

- `ssap://audio/setVolume` `{"volume": <int>}`. aiowebostv clamps with `max(0, volume)` (L968-971). Real reply: `{"volume":25,"returnValue":true,"soundOutput":""}`.
- `ssap://audio/setMute` `{"mute": <bool>}` (aiowebostv L951-953; lgtv2 README `{mute: true}`). LGTVC instead sends `{"mute":"true"}` (a string) to mute and `{}` to unmute (`LGTVC Common/lg_api.h` L48-49).
- `ssap://audio/volumeUp` and `volumeDown` take an empty payload.

---

## 6. Picture settings and the alert/"luna" workaround

### 6.1 bscpylgtv `luna_request`, verbatim (`BSC bscpylgtv/webos_client.py` L1299-1327)

```python
# Luna
async def luna_request(self, uri, params):
    """luna api call.
    @private"""
    # n.b. this is a hack which abuses the alert API
    # to call the internal luna API which is otherwise
    # not exposed through the websocket interface
    # An important limitation is that any returned
    # data is not accessible

    # set desired action for click, fail and close
    # for redundancy/robustness

    lunauri = f"luna://{uri}"

    buttons = [{"label": "", "onClick": lunauri, "params": params}]
    payload = {
        "message": " ",
        "buttons": buttons,
        "onclose": {"uri": lunauri, "params": params},
        "onfail": {"uri": lunauri, "params": params},
    }

    ret = await self.request(ep.CREATE_ALERT, payload)
    alertId = ret.get("alertId")
    if alertId is None:
        raise PyLGTVCmdException("Invalid alertId")

    return await self.request(ep.CLOSE_ALERT, payload={"alertId": alertId})
```
Endpoints: `CREATE_ALERT = "system.notifications/createAlert"`, `CLOSE_ALERT = "system.notifications/closeAlert"`, `LUNA_SET_SYSTEM_SETTINGS = "com.webos.settingsservice/setSystemSettings"`. Settings writes use `set_settings(category, settings, current_app=None)`, which builds `params = {"category": category, "settings": settings}` and adds `params["current_app"] = current_app` when given ("required by e.g. truMotionMode, aspectRatio setting").

Real wire capture through LGTVC (https://github.com/JPersson77/LGTVCompanion/issues/351#issuecomment-5308314909):
```
> {"id":"luna_request","payload":{"buttons":[{"label":"","onClick":"luna://com.webos.settingsservice/setSystemSettings","params":{"category":"picture","settings":{"brightness":"60"}}}],"message":" ","onclose":{"params":{"category":"picture","settings":{"brightness":"60"}},"uri":"luna://com.webos.settingsservice/setSystemSettings"},"onfail":{"params":{"category":"picture","settings":{"brightness":"60"}},"uri":"luna://com.webos.settingsservice/setSystemSettings"}},"type":"request","uri":"ssap://system.notifications/createAlert"}
< {"type":"response","id":"luna_request","payload":{"returnValue":true,"alertId":"com.webos.service.apiadapter.pub-1786895965205"}}
> {"type":"request","id":"closeLunaAlert","uri":"ssap://system.notifications/closeAlert","payload":{"alertId":"com.webos.service.apiadapter-1786895965205"}}
< {"type":"response","id":"closeLunaAlert","payload":{"returnValue":true}}
```
LGTVC rewrites the returned alertId. It takes the text after the first `-` and builds `com.webos.service.apiadapter-<suffix>`, dropping `.pub` (`LGTVC .../web_os_client.cpp` L848-868). bscpylgtv passes the alertId back unchanged. Both are reported working.

Other variants seen:
- homebridge `lunaSend` (`HB lib/LgTvController.js` L1187-1245) uses `title`, `modal: true`, `type: 'confirm'`, `isSysReq: true` and a button `{label:'Ok', focus:true, buttonType:'ok', onClick, params}`. It calls closeAlert only on webOS ≥4; below 4 it re-opens the alert and presses `ENTER`.
- The HA community variant has only `onclose` with an empty-label button: `{message: ' ', buttons: [{label: ''}], onclose: {uri: 'luna://com.webos.settingsservice/setSystemSettings', params: {...}}}`, then `closeAlert {alertId}`. Reported "completely hidden" (https://github.com/home-assistant/core/issues/180025#issuecomment-5536755593). Confirmed working on webOS 25 33.31.68 and webOS 26 (https://github.com/home-assistant-libs/aiowebostv/issues/728#issuecomment-5600279722).

Caveats:
- "`pictureMode` and `backlight` must be sent as **separate** luna calls. Changing the picture mode reloads that mode's own stored backlight, so a combined call silently loses the backlight value" (same aiowebostv #728 comment).
- A stuck-notification-service state can make `closeAlert` never reply and leave the alert on screen until someone presses OK (bscpylgtv PR #9). A reboot fixed it. The author's proposed fallback: time out after about 2 s, then send `ENTER` on the pointer socket.
- With QuickStart+ off the alert may flash briefly once per power-on (chros73, https://github.com/chros73/bscpylgtv/pull/9#issuecomment-5740809991).
- One failure seen with a minimal manifest (till69, OLED77C67LA 43.21.60): `{"type":"error","error":"500 Application error","payload":{"returnValue":false,"errorText":"Not allowed to call method specified in the uri: luna://com.webos.service.systemservice/setSystemSetting"}}`. Note the URI in that error is a **different** service. The same user later confirmed that LGTVC 5.7.0's `-backlight 50` works on that set.

### 6.2 Direct SSAP `settings/setSystemSettings` with the unsigned manifest returns 401

- From https://github.com/JPersson77/LGTVCompanion/issues/351#issuecomment-5308144154: `LGTVcli.exe -request_with_param settings/setSystemSettings {"category":"picture","settings":{"backlight":"10"}}` returned `{"Device1":{"error":"401 insufficient permissions"}}`.
- HA #180025 shows the same on C3, CX, C5 webOS 25 33.31.69, and G5, after aiowebostv 0.9.2: `{'type': 'error', 'id': 22, 'error': '401 insufficient permissions', 'payload': {}}`.
- **Use the luna alert path for writes.** bscpylgtv's public-API `set_system_settings` (ssap) is documented as "requires WebOS v9 (2024) or newer" for pictureMode, and it needs the signed tier.

### 6.3 `getSystemSettings` request and response

Request (bscpylgtv L1268-1290, homebridge L848-853):
```json
{"type":"request","id":"<n>","uri":"ssap://settings/getSystemSettings","payload":{"category":"picture","keys":["brightness","backlight","contrast","color"]}}
```
It can be **subscribed** (`type: "subscribe"`); homebridge and bscpylgtv both do. It works with the unsigned manifest on webOS 26 (perseus177's audit; HA #180025).

Response values:
- Usually strings. homebridge parses with `parseInt(res.settings.backlight)` and its comment shows `"backlight": "80"`, `"brightness": "50"`, `"color": "50"`, `"contrast": "80"` (`HB lib/LgTvController.js` L520-548, L1265+). bscpylgtv's G6 dump shows `"backlight": "100"`, `"brightness": "50"`, `"color": "50"`, `"contrast": "80"`, `"energySaving": "off"`, `"energySavingAutoMin": 5`, `"energySavingModified": "false"` (`BSC docs/available_settings_G6.md` ~L7205-7259).
- **But** a G4 (33.31.68) capture returned an int (https://github.com/home-assistant-libs/aiowebostv/issues/728): `READ : {'returnValue': True, ..., 'settings': {'pictureMode': 'filmMaker', 'backlight': 26}}`. **Parse both string and int.**
- Envelope: `payload.settings` holds a map of the requested keys, and `returnValue` is true. Other envelope fields were elided ("...") in that capture; **UNCONFIRMED**.
- Write values: both strings (`"60"`, LGTVC) and ints (`56`, HA; `{"backlight": 0}`, bscpylgtv README) are reported accepted.

Key names:
- Keys readable through the public API for `picture` on G6 (2026) include `brightness`, `backlight`, `contrast`, `color`, `energySaving`, `pictureMode`, `dynamicContrast`, `peakBrightness`, `gamma`, `blackLevel`, `colorGamut`, `hdrDynamicToneMapping`, `colorTemperature`, `sharpness`, `eyeComfortMode` and others (`BSC docs/available_settings_G6.md` L10505ff).
- Ranges on G6: `backlight`, `brightness` and `contrast` are `{"interval":1,"max":100,"min":0}`.
- **OLED uses `backlight`.** The G6, C2, CX and C9 OLED dumps all use `backlight`. `oledLight` appears nowhere in bscpylgtv, homebridge, aiowebostv, lgtv2 or LGTVC. bscpylgtv's `set_oled_light()` is a calibration-API helper that writes `BACKLIGHT_UI_DATA` (`BSC bscpylgtv/webos_client.py` L1631-1633).
- `energySaving` allowed values:
  - G6 (2026, webOS 11.1) and C2 (webOS 11.2): `["auto","off","min","med","max"]`.
  - C9 (2019, webOS 4.10) and CX (2020, webOS 5.6): `["auto","off","min","med","max","screen_off"]` (`BSC docs/available_settings_{G6,C2,C9,CX}.md`).
  - LGTVC's command table: `"Argument": "auto off min med max screen_off"` (`LGTVC Common/lg_api_commands.h`).

Other picture endpoints:
- bscpylgtv v0.5.5 adds `CURRENT_SYSTEM_SETTINGS = "settings/currentSystemSettings"` (`get_current_system_settings`, payload `{}`). chros73: "only available since webOS26" (https://github.com/chros73/bscpylgtv/pull/10).
- Per-input or per-mode luna categories: `"category": f"{category}${tv_input}.{pic_mode}.{stereoscopic}.x"` (bscpylgtv `set_picture_settings`).

### 6.4 Bonus: enable Wake-on-LAN through luna (LGTVC does this after pairing)

`LGTVC Common/lg_api.h` L54, sent on first successful pairing (`web_os_client.cpp` L607-612):
```json
{"id":"luna_request","payload":{"buttons":[{"label":"","onClick":"luna://com.webos.settingsservice/setSystemSettings","params":{"category":"network","settings":{"wolwowlOnOff":"true"}}}],"message":" ","onclose":{"params":{"category":"network","settings":{"wolwowlOnOff":"true"}},"uri":"luna://com.webos.settingsservice/setSystemSettings"},"onfail":{"params":{"category":"network","settings":{"wolwowlOnOff":"true"}},"uri":"luna://com.webos.settingsservice/setSystemSettings"}},"type":"request","uri":"ssap://system.notifications/createAlert"}
```

---

## 7. Screen off/on

All of these are plain **`ssap://`** requests. None of the five projects routes them through luna.

| URI | Payload | Used by | webOS |
|---|---|---|---|
| `ssap://com.webos.service.tvpower/power/turnOffScreen`, `.../turnOnScreen` | aiowebostv: `{}`; LGTVC: `{}`; bscpylgtv and homebridge: `{"standbyMode":"active"}` | aiowebostv `set_screen_state` (v0.9.0), LGTVC `JSON_BLANK`/`JSON_UNBLANK`, bscpylgtv default, homebridge for webOS ≥5 | ≥5 (homebridge: "alternative version, probably for webOS5+ TVs") |
| `ssap://com.webos.service.tv.power/turnOffScreen`, `.../turnOnScreen` | `{"standbyMode":"active"}` (**mandatory**) | bscpylgtv `TURN_*_SCREEN_WO4` ("endpoints below were removed at some point"), homebridge for webOS <5 | 4.x |
| `ssap://com.webos.service.panelcontroller/setScreenOnOff` | `{"OnOff": <bool>}` | bscpylgtv `toggle_screen` (v0.5.5): "Toggle TV Screen on / off. (It can behave differently than turn_screen_* methods)" | **UNCONFIRMED**; no version or permission info found |

Sources: `AW aiowebostv/endpoints.py` (`TURN_OFF_SCREEN = "com.webos.service.tvpower/power/turnOffScreen"`), `AW aiowebostv/webos_client.py` L905-909; `BSC bscpylgtv/webos_client.py` L980-1004; `HB lib/LgTvController.js` L31-34 and L1014-1034; `LGTVC Common/lg_api.h` L17-18 and L45-46.

bscpylgtv docstring: "standbyMode values: 'active' or 'passive', passive cannot turn screen back on, need to pull TV plug." homebridge's comment is the same: "passive stay on even when TV is turned off … currentyl need to pull tv plug". **Never send `passive`.**

webOS 4.4.3 (OLED55C8PLA) real captures (https://github.com/home-assistant/core/issues/183602):
```
send: {'type': 'request', 'uri': 'ssap://com.webos.service.tvpower/power/turnOffScreen', 'payload': {'standbyMode': 'active'}}
recv: {"type":"error","error":"404 no such service or method","payload":{}}
send: {'type': 'request', 'uri': 'ssap://com.webos.service.tv.power/turnOffScreen', 'payload': {'standbyMode': 'active'}}
recv: {"type":"response","payload":{"returnValue":true}}
send: {'type': 'request', 'uri': 'ssap://com.webos.service.tvpower/power/turnOnScreen', 'payload': {'standbyMode': 'active'}}
recv: {"type":"error","error":"500 Application error","payload":{"returnValue":false,"state":"Active","errorCode":"-102","errorText":"The current state must be 'Screen Off'"}}
send: {'type': 'request', 'uri': 'ssap://com.webos.service.tv.power/turnOnScreen', 'payload': {}}
recv: {"type":"error","error":"500 Application error","payload":{"returnValue":false,"errorText":"Invalid standbyMode !!","errorCode":"POWER_ERROR_0003"}}
send: {'type': 'request', 'uri': 'ssap://com.webos.service.tv.power/turnOnScreen', 'payload': {'standbyMode': 'active'}}
recv: {"type":"error","error":"500 Application error","payload":{"returnValue":false,"errorText":"UnKnown Error","errorCode":"POWER_ERROR_0005"}}   <- but the screen does turn on
```
The reporter's notes from the same issue:
- "No state notification is pushed by the TV when the screen is turned off/on … has to be tracked optimistically."
- "some C9 (webOS 4.5) users report tvpower working, so the real boundary may be 4.5 rather than 5 … try tvpower first and fall back to tv.power (with standbyMode) on '404 no such service or method'."

State tracking on webOS ≥5: `getPowerState` reports `"Screen Off"` (aiowebostv `_is_screen_on`: `power_state.get("state") != "Screen Off"`). Power states mapped by lgtv2: `Active`→on, `Active Standby`→standby, `Suspend`→off, `Screen Off`→screen_off, `Screen Saver`→screen_saver, `Power Off`→off (`LGTV2 index.js` L51-58). Payloads may carry `processing` (e.g. `"Request Power Off"`). LGTVC treats `state=="Active" && processing.empty()` as on.

webOS 26 with the unsigned manifest: LGTVC 5.7.0 uses `tvpower/power/turnOffScreen` as its core "blank screen" feature and claims "restored all functionality". A homebridge user reports screen on/off works on webOS 26 with its manifest. I found **no explicit capture** of `turnOffScreen` on webOS 26 with an unsigned key (**UNCONFIRMED**, though very likely fine).

Old alternative: `energySaving: "screen_off"` exists only on older models (C9, CX).

---

## 8. `ssap://com.webos.service.connectionmanager/getinfo`

Real response, LG C5 (aiowebostv PR #599, https://github.com/home-assistant-libs/aiowebostv/pull/599):
```
{'p2pInfo': {'macAddress': '0A:27:A8:9C:52:C8'},
 'returnValue': True,
 'subscribed': False,
 'wifiInfo': {'macAddress': '08:27:A8:9C:52:C8'},
 'wiredInfo': {'macAddress': '60:75:6C:27:A9:02'}}
```
The PR author notes: "my TV isn't connected to WiFi, but returns a WiFi MAC address anyway".

- Fields are `wiredInfo.macAddress`, `wifiInfo.macAddress` and `p2pInfo.macAddress`. It works with the unsigned manifest on webOS 26 (perseus177's audit) and on a C6 (bscpylgtv PR #10).
- Some TVs return 404. aiowebostv v0.9.1: "Suppress WebOsTvServiceNotFoundError when retrieving connection info" (`with suppress(WebOsTvServiceNotFoundError): self.tv_info.connection = await self.get_connection_info()`, L275-276).
- lgtv2 learns both MACs after pairing (`LGTV2 index.js` L416-438):
  ```js
  that.request('ssap://com.webos.service.connectionmanager/getinfo', (err, res) => {
      ...
      const learned = {
          wired: res.wiredInfo && res.wiredInfo.macAddress,
          wifi: res.wifiInfo && res.wifiInfo.macAddress,
      };
  ```
- WoL sends to **both** MACs. README: "the TV only reacts on the interface it uses, so both are sent". Defaults are `address '255.255.255.255'`, `port 9`, `count 3`, `interval 100 ms`. The packet is `6×0xFF + 16×MAC` (`LGTV2 index.js` L81-170).
- The TV setting must be enabled. lgtv2 README: "Settings → General → Mobile TV On / Turn on via Wi-Fi (2025+ models: Support → IP control settings → Wake on LAN)". homebridge #574 also notes that QuickStart+ off on a CX can make WoL from deep standby fail.
- The SSDP DIAL response also includes the MAC (section 11).

---

## 9. Apps, inputs, system info, power off

### 9.1 `ssap://com.webos.applicationManager/listLaunchPoints`

aiowebostv reads `payload["launchPoints"]` and keys apps by `id`. Subscription updates without `launchPoints` carry `change` and `id` (`AW aiowebostv/webos_client.py` L560-575):
```python
apps = payload.get("launchPoints")
if apps is not None: ...
else:
    change = payload["change"]
    app_id = payload["id"]
    if change == "removed":
        del self.tv_state.apps[app_id]
    else:
        self.tv_state.apps[app_id] = payload
```
Real launch point (2021, https://github.com/supersaiyanmode/PyWebOSTV/issues/69#issuecomment-986051038):
```
{'subscribed': False,
 'launchPoints': [
   {'systemApp': True, 'removable': False, 'relaunch': False, 'largeIcon': 'http://172.0.17.230:3000/resources/56b261a427827c78afecf92d780b4201b75e7ea2/icon_antenna.png', 'bgImages': [], 'userData': '', 'id': 'com.webos.app.livetv',
    'title': 'Live TV', 'bgColor': '', 'iconColor': '#a3c125', 'appDescription': '', 'lptype': 'default', 'params': {}, 'bgImage': '/usr/palm/applications/com.webos.app.livetv/assets/livetv.png',
    'unmovable': False, 'icon': 'http://172.0.17.230:3000/resources/56b261a427827c78afecf92d780b4201b75e7ea2/icon_antenna.png', 'launchPointId': 'com.webos.app.livetv_default', 'favicon': '', 'imageForRecents': '', 'tileSize': 'normal'}
 ],
 'caseDetail': {'change': ['NEWLIST_FROM_SDP']}}
```
(The user's paste had a stray `'` inside the URLs; removed here.)

- HA uses `largeIcon` when it starts with `http`, otherwise `icon` (`HA .../media_player.py` L165-170).
- Over wss the icon URLs are `https://<tv>:3001/resources/<hash>/<file>` (ConnectSDK #412: `"icon":"https:\/\/192.168.5.253:3001\/resources\/222fe8…\/AirPlay_Icon-77x77.png"`). Your image loader needs the same trust-all or pinned TLS settings.
- In `listApps` output, `largeIcon` can be a bare filename (`"largeIcon":"largeIcon.png"`) while `icon` is absolute.
- Since webOS 4.5, Live TV and inputs may be missing from launch points: "visibility set to false" (`HB lib/LgTvController.js` checkBasicInputs comment).

### 9.2 Launch

`ssap://system.launcher/launch` (aiowebostv L840-854):
```python
await self.request(ep.LAUNCH, {"id": app})
await self.request(ep.LAUNCH, {"id": app, "params": params})
await self.request(ep.LAUNCH, {"id": app, "contentId": content_id})
```
Close with `ssap://system.launcher/close {"id": app}`. The current app comes from `ssap://com.webos.applicationManager/getForegroundAppInfo` (subscribe; `payload.appId`, e.g. `com.webos.app.hdmi1`, `com.webos.app.livetv`).

### 9.3 `ssap://tv/getExternalInputList` and `ssap://tv/switchInput`

aiowebostv reads `payload["devices"]` and keys inputs by `appId` (L577-583, L912-931). Real response, OLED65B9PUA (https://github.com/klattimer/LGWebOSRemote/issues/53):
```json
{"type":"response","id":"input_0","payload":{"returnValue":true,"devices":[
 {"id":"AV_1","label":"AV","port":1,"connected":false,"appId":"com.webos.app.externalinput.av1","icon":"http://15.129.1.98:3000/resources/6868bcd250b05eab17c031f78950c1c35eb5e9e6/av.png","modified":false,"subList":[],"subCount":0,"favorite":false},
 {"id":"HDMI_1","label":"THE-THRONE","port":1,"connected":true,"appId":"com.webos.app.hdmi1","icon":"http://15.129.1.98:3000/resources/4057cf0526c1f5da08725218186baf330d5c6f6b/pc.png","modified":true,"lastUniqueId":255,"hdmiPlugIn":true,"dongleConnected":false,"subList":[],"subCount":0,"favorite":false},
 {"id":"HDMI_2","label":"THE-POWERHOUSE","port":2,"connected":true,"appId":"com.webos.app.hdmi2", ... "subList":[{"id":"URCU","serviceType":"audio","connectedInput":"ARC", ... "brandName":"Denon", ...}],"subCount":1,"favorite":false}, ...]}}
```
- Switch input: `ssap://tv/switchInput {"inputId": "HDMI_2"}` (aiowebostv `set_input` uses `{"inputId": input_id}`; lgtv2 README `{inputId: 'HDMI_2'}`).
- Alternative used by LGTVC: launch the input app, `system.launcher/launch {"id":"com.webos.app.hdmi<N>"}` (`LG_URI_PAYLOAD_SETHDMI`).
- HA selects sources by calling `launch_app(id)` for entries with `title` (apps) and `set_input(id)` for entries with `label` (inputs) (`HA .../media_player.py` L315-330).

### 9.4 `ssap://system/getSystemInfo`

Shown in 1.4. Fields: `returnValue`, `features{dvr}`, `receiverType` (`DVB`/`ATSC`), `modelName` (e.g. `OLED65G56LS.DEUQLJP`, `75UP7300PUC`), `serialNumber`, `programMode`. HA names the device `f"{DEFAULT_NAME} {model_name}"` (`HA .../config_flow.py` L104-108). **Call it before `register`.**

### 9.5 `ssap://system/turnOff`

aiowebostv sends it without waiting for a reply (L880-890):
```python
# if tv is shutting down and standby+ option is not enabled,
# response is unreliable, so don't wait for one,
await self.command("request", ep.POWER_OFF)
```
bscpylgtv does the same and first re-reads `getPowerState` to avoid toggling a TV that is off. Note that `system/turnOff` behaves as a toggle: LGTVC names it `powerToggle` and uses it to power on from "Active Standby".

Power on: `ssap://system/turnOn` exists but "this method does not work anymore on newer WebOS versions" (bscpylgtv). Use WoL.

---

## 10. IME (text input)

| URI | Payload | Source |
|---|---|---|
| `ssap://com.webos.service.ime/insertText` | `{"text": "...", "replace": false}` (bscpylgtv, bool) / `{text: 'abc', replace: 0}` (lgtv2 README, ConnectSDK, int) | `BSC bscpylgtv/webos_client.py` L1200-1202: `await self.request(ep.INSERT_TEXT, {"text": text, "replace": replace})`; `CSDK .../WebOSTVKeyboardInput.java` L111-122 |
| `ssap://com.webos.service.ime/deleteCharacters` | `{"count": N}` | lgtv2 README; ConnectSDK batches deletes: `payload.put("count", count)` (L96-105). bscpylgtv `send_delete_key` sends **no payload** (`{}`); what the TV does then is **UNCONFIRMED** |
| `ssap://com.webos.service.ime/sendEnterKey` | `{}` | bscpylgtv `send_enter_key`, ConnectSDK |
| `ssap://com.webos.service.ime/registerRemoteKeyboard` | subscribe; payload has `currentWidget{focus, contentType, predictionEnabled, correctionEnabled, autoCapitalization, hiddenText}` and `focusChanged` | `CSDK .../WebOSTVKeyboardInput.java` L44, L173-215 (use it to show or hide your keyboard) |

Permission: `CONTROL_INPUT_TEXT`. IME behaviour on webOS 26 with an unsigned manifest is **UNCONFIRMED** (not in perseus177's audit).

---

## 11. SSDP

### 11.1 M-SEARCH

ConnectSDK (`CSDK src/com/connectsdk/discovery/provider/ssdp/SSDPClient.java` L144-158), CRLF line endings:
```java
sb.append(MSEARCH + NEWLINE);                                   // "M-SEARCH * HTTP/1.1"
sb.append("HOST: " + MULTICAST_ADDRESS + ":" + PORT + NEWLINE); // 239.255.255.250:1900
sb.append("MAN: \"ssdp:discover\"" + NEWLINE);
sb.append("ST: ").append(ST).append(NEWLINE);
sb.append("MX: ").append(MX).append(NEWLINE);                    // MX = 5
...
sb.append(NEWLINE);
```
ST for webOS SSAP: `urn:lge-com:service:webos-second-screen:1`. This is the ConnectSDK `WebOSTVService.discoveryFilter()` and HA's matcher (`HA homeassistant/components/webostv/manifest.json`: `"ssdp": [{"st": "urn:lge-com:service:webos-second-screen:1"}]`).

### 11.2 LG TV responses (real, raw)

Captured in https://github.com/StevenLooman/async_upnp_client/issues/154 (SM8200PLA, 2023-01-24). The TV exposes several root devices, each with its **own UUID and port**.

webOS second-screen (the SSAP device):
```
HTTP/1.1 200 OK
Location: http://192.168.8.170:1048/
Cache-Control: max-age=1800
Server: WebOS/4.1.0 UPnP/1.0
EXT:
USN: uuid:be51ca22-39f2-4b0e-6276-1da38b37091a::urn:lge-com:service:webos-second-screen:1
ST: urn:lge-com:service:webos-second-screen:1
Date: Tue, 24 Jan 2023 20:26:58 GMT
```
DLNA MediaRenderer, which carries `DLNADeviceName.lge.com` (URL-encoded friendly name):
```
HTTP/1.1 200 OK
Location: http://192.168.8.170:1792/
Cache-Control: max-age=1800
Server: Linux/i686 UPnP/1,0 DLNADOC/1.50 LGE WebOS TV/Version 0.9
EXT:
USN: uuid:135f5df5-c5c5-572e-030d-be1a7940f080::urn:schemas-upnp-org:device:MediaRenderer:1
ST: urn:schemas-upnp-org:device:MediaRenderer:1
Date: Tue, 24 Jan 2023 20:26:59 GMT
DLNADeviceName.lge.com: %5bLG%5d%20webOS%20TV%20SM8200PLA
```
DIAL, which carries the **WoL MAC**:
```
HTTP/1.1 200 OK
Location: http://192.168.8.170:1492/
Cache-Control: max-age=1800
Server: WebOS/1.5 UPnP/1.0 webOSTV/1.0
EXT:
USN: uuid:b69e0487-4e90-4dc0-ac85-0f8f40755782::urn:dial-multiscreen-org:service:dial:1
ST: urn:dial-multiscreen-org:service:dial:1
Date: Tue, 24 Jan 2023 20:26:57 GMT
WAKEUP: MAC=2c:2b:f9:b1:b0:56;Timeout=60
```
Also seen in the same capture:
- `urn:lge:device:tv:1` and `urn:lge:service:virtualSvc:1` (Server `WebOS/4.0.0 UPnP/1.0`).
- A Netflix MDX device with `X-Friendly-Name: W0xHXSB3ZWJPUyBUViBTTTgyMDBQTEE=`, which is base64 for `[LG] webOS TV SM8200PLA`.

Another second-screen response, as parsed by HA (https://github.com/home-assistant/core/issues/53785): `{'location': 'http://192.168.xxx.xxx:1080/', 'Cache-Control': 'max-age=1800', 'Server': 'WebOS/4.1.0 UPnP/1.0', 'EXT': '', 'USN': 'uuid:…::urn:lge-com:service:webos-second-screen:1', 'ST': 'urn:lge-com:service:webos-second-screen:1', ...}`.

### 11.3 Getting the friendly name

1. **Preferred, and what HA does**: GET the second-screen `LOCATION` XML and read UPnP `friendlyName` and `UDN`. HA (`HA homeassistant/components/webostv/config_flow.py` L114-136):
   ```python
   host = urlparse(discovery_info.ssdp_location).hostname
   self._name = discovery_info.upnp.get(
       ATTR_UPNP_FRIENDLY_NAME, DEFAULT_NAME
   ).replace("[LG]", "LG")
   uuid = discovery_info.upnp[ATTR_UPNP_UDN]
   uuid = uuid.removeprefix("uuid:")
   await self.async_set_unique_id(uuid)
   ```
   The test fixture's friendly name is `f"[LG] webOS TV {TV_MODEL}"`. In the manual flow HA uses `hello["deviceUUID"]` as the unique id, which implies UDN equals `deviceUUID`. That equality is **UNCONFIRMED** by a side-by-side capture.
2. Alternatively, URL-decode the `DLNADeviceName.lge.com` header from the MediaRenderer response at the same IP. That device has a **different** UUID, so correlate by IP. The same header also appears on HTTP requests the TV makes as a DLNA client (UniversalMediaServer #1929).
3. LGTVC on Windows uses OS device enumeration and matches `"[LG]"` in `DEVPKEY_Device_FriendlyName` (`LGTVC LGTV Companion UI/lgtv_companion_ui.cpp` L579-588).
4. openHAB's maintainer notes that "the TV only response with urn:lge-com:service:webos-second-screen:1 when a specific search request goes out" (https://github.com/openhab/openhab-addons/issues/4423#issuecomment-449629986). So search for that ST explicitly, not only `ssdp:all`.

Android note: receiving SSDP multicast replies usually needs a `WifiManager.MulticastLock`, which requires `CHANGE_WIFI_MULTICAST_STATE`. That is general Android knowledge; I did not re-verify it on developer.android.com for this task.

---

## 12. Licence notices

| Project | LICENSE file | Exact copyright line(s) |
|---|---|---|
| aiowebostv | `LICENSE` (Apache-2.0, `AW LICENSE`) | **None.** The file is the stock Apache 2.0 text, and the appendix keeps the placeholder `Copyright [yyyy] [name of copyright owner]`. `pyproject.toml`: `license = "Apache-2.0"`, `authors = [{ name = "Home Assistant Team", email = "hello@home-assistant.io" }]`. **No `NOTICE` file**: raw `NOTICE` returns 404 at v0.10.0, and the root listing on main is `.github, .gitignore, .pre-commit-config.yaml, AI_POLICY.md, LICENSE, README.md, aiowebostv, examples, pyproject.toml, requirements*.txt`. The README says "Based on: `aiopylgtv` … `bscpylgtv`" (both MIT). |
| LGTVCompanion | `LICENSE` (MIT) | `Copyright (c) 2021-2026 Jörgen Persson` |
| bscpylgtv | `LICENSE.txt` (MIT, "The MIT License (MIT)") | `Copyright (c) 2017 Dennis Karpienski`<br>`Copyright (c) 2019 Josh Bendavid` |
| lgtv2 | `LICENSE` (MIT, "The MIT License (MIT)") | `Copyright (c) Sebastian Raff` (no year). The source header adds: `MIT (c) Sebastian Raff <hq@ccu.io> (https://github.com/hobbyquaker)` and "this is a fork of https://github.com/msloth/lgtv.js". |
| homebridge-webos-tv (bonus) | `LICENSE` (MIT) | `Copyright (c) 2026 Marcin`. Note that `lib/ws/weboswebsocket.js` is a copy of lgtv2 and keeps the "MIT (c) Sebastian Raff" header. |
| ConnectSDK (bonus) | Apache-2.0 | not checked in detail |

---

## 13. Android

### 13.1 `connectedDevice` FGS prerequisites (Android 14+)

Source: https://developer.android.com/develop/background-work/services/fgs/service-types ("Last updated 2026-10-01 UTC"):

> Beginning with Android 14 (API level 34), you must declare an appropriate service type for each foreground service. That means you must declare the service type in your app manifest, and also request the appropriate foreground service permission for that type (in addition to requesting the FOREGROUND_SERVICE permission). Furthermore, depending on the foreground service type, you might have to request runtime permissions before you launch the service.

> **Connected device** … `android:foregroundServiceType` `connectedDevice` · Permission to declare in your manifest `FOREGROUND_SERVICE_CONNECTED_DEVICE` · Constant to pass to startForeground() `FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE` · Runtime prerequisites: At least one of the following conditions must be true: Declare at least one of the following permissions in your manifest: `CHANGE_NETWORK_STATE`, `CHANGE_WIFI_STATE`, **`CHANGE_WIFI_MULTICAST_STATE`**, `NFC`, `TRANSMIT_IR` · Request and be granted at least one of the following runtime permissions: `BLUETOOTH_CONNECT`, `BLUETOOTH_ADVERTISE`, `BLUETOOTH_SCAN`, `UWB_RANGING` · Call `UsbManager.requestPermission()` · Description: Interactions with external devices that require a Bluetooth, NFC, IR, USB, or network connection.

**Yes, `CHANGE_WIFI_MULTICAST_STATE` is in the list**, and declaring it alone satisfies the prerequisite. Same page: "If your app targets Android 14 or higher, you'll need to declare your app's foreground service types in the Play Console's app content page (Policy > App content)."

### 13.2 Google Play target API (as of October 2026)

Source: https://developer.android.com/google/play/requirements/target-sdk ("Last updated 2026-10-01 UTC"):

> Starting August 31 2026: New apps and app updates must target Android 16 (API level 36) or higher to be submitted to Google Play; except for Wear OS and Android Automotive OS apps, which must target Android 15 (API level 35) or higher, and Android TV and Android XR apps, which must target Android 14 (API level 34) or higher. Existing apps must target Android 15 (API level 35) or higher to remain available to new users on devices running Android OS higher than your app's target API level.

Play Console help (https://support.google.com/googleplay/android-developer/answer/11926878) adds: "You will be able to request an extension to November 1, 2026 if you need more time to update your app."

**Answer: targetSdk 36 for new apps and updates.**

Related upcoming change (https://developer.android.com/privacy-and-security/local-network-permission, "Last updated 2026-10-02 UTC"):

> Starting in Android 17, local network protections are mandatory and enforced for apps targeting Android 17 or higher. … Target SDK 36 | 37 or higher · Permission: Temporarily used NEARBY_WIFI_DEVICES | ACCESS_LOCAL_NETWORK · Default Access: Local network access is open | Local network is blocked by default for all apps that update their target SDK

On Android 16 it is opt-in only. Plan for `ACCESS_LOCAL_NETWORK` when moving to targetSdk 37.

### 13.3 Starting an FGS from a TileService or app widget

Source: https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start ("Last updated 2026-10-01 UTC"):

> Apps that target Android 12 (API level 31) or higher can't start foreground services while the app is running in the background, except for a few special cases. … the system throws a ForegroundServiceStartNotAllowedException.

> **Exemptions from background start restrictions** — In the following situations, your app can start foreground services even while your app runs in the background: … **The user performs an action on a UI element related to your app. For example, they might interact with a bubble, notification, widget, or activity.** … The user turns off battery optimizations for your app. …

- **App widget PendingIntent: exempt.** "widget" is named explicitly. The while-in-use list also says "The service starts by interacting with app widgets", which is irrelevant to `connectedDevice`, since that type has no while-in-use permission.
- **TileService.onClick: not named** on the restrictions page, the Quick Settings tiles guide (https://developer.android.com/develop/ui/views/quicksettings-tiles; it only says onClick "can launch a dialog or activity, trigger background work, or change the state of your tile"), or the `TileService` reference page. Two arguments suggest it is allowed, but neither is documented for tiles: a tile is "a UI element related to your app", and the older background-limits definition (https://developer.android.com/about/versions/oreo/background) says "An app is considered to be in the foreground if … Another foreground app is connected to the app, either by binding to one of its services". SystemUI binds the TileService while listening. **UNCONFIRMED in docs; test on Android 14/15/16 and catch `ForegroundServiceStartNotAllowedException`.** A safe fallback is to launch a transparent activity via `startActivityAndCollapse(PendingIntent)`; the PendingIntent overload is the required form on API 34+, and the old Intent overload is deprecated. That last point is from general knowledge and was not re-fetched.

---

## 14. Open questions and UNCONFIRMED items (test on hardware)

1. Exact error text for a declined prompt versus a timed-out prompt. Both may be `403 cancelled`.
2. What the TV sends for an unknown or invalid stored `client-key` (probably a normal re-prompt).
3. PIN pairing: the reply sequence after `ssap://pairing/setPin`, whether `pin` must be a string, and whether it works unsigned on webOS 26.
4. `com.webos.service.panelcontroller/setScreenOnOff {"OnOff": bool}`: which webOS versions support it and which permission it needs.
5. `tvpower/power/turnOffScreen` on webOS 26 with an unsigned key. Very likely works (LGTVC relies on it) but there is no direct capture.
6. `volume: -1` for ARC/eARC outputs (README claim only), and whether `adjustVolume:false` accompanies it.
7. The full `getSystemSettings` response envelope beyond `returnValue` and `settings`, and string versus int per firmware.
8. Whether the SSDP UDN equals `hello.deviceUUID`.
9. Whether the 2018-2025 TLS leaf and intermediate fingerprints (lgtv2) still match webOS 26 sets.
10. FGS start from `TileService.onClick` on Android 14+.
11. Behaviour of homebridge's ThinQ-derived signed manifest on blacklisting firmware over time. Not recommended to copy.
