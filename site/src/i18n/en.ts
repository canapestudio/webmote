/**
 * English copy. The French dictionary (fr.ts) must have exactly the same shape: TypeScript checks it.
 * Wording follows the store listing (fastlane/metadata/android/en-US/full_description.txt).
 * Never claim "no ads" here: that wording was deliberately removed from the listing.
 */
export const en = {
  meta: {
    homeTitle: 'Webmote – A fast, private remote for LG webOS TVs',
    homeDescription:
      'Webmote turns your Android phone into a fast, private remote for LG TVs running webOS (2018 and later): tiles, widgets, volume keys and Wake-on-LAN. No account.',
    privacyTitle: 'Privacy policy – Webmote',
    privacyDescription:
      'Webmote collects nothing: no account, no analytics, no crash reporting. It talks only to your TVs, on your own network.',
    notFoundTitle: 'Page not found – Webmote',
    ogLocale: 'en_US',
    ogImageAlt: 'Webmote: your TV remote, right on your phone.',
  },

  nav: {
    label: 'Main',
    home: 'Webmote, home',
    skip: 'Skip to content',
    features: 'Features',
    privacy: 'Privacy',
    faq: 'FAQ',
    /** Shown on the English pages: a link to the French version, written in French. */
    switchTo: 'Français',
    switchToShort: 'FR',
    switchToLabel: 'Lire en français',
  },

  play: {
    soon: 'Coming soon to Google Play',
    joinTest: 'Join the closed test',
    badgeAlt: 'Get it on Google Play',
    attribution: 'Google Play and the Google Play logo are trademarks of Google LLC.',
    /** Only shown once OPEN_SOURCE.fdroid.live is true. */
    fdroid: 'Get it on F-Droid',
  },

  hero: {
    kicker: 'Remote for LG webOS TVs',
    titleA: 'Your TV remote,',
    titleB: 'right on your phone.',
    lead: 'Webmote is a fast, private remote for LG TVs running webOS. Open it and press a button, or don’t open it at all.',
    facts: ['Free', 'Android 8.0+', 'LG webOS 4+ (2018 and later)'],
  },

  shots: {
    remote:
      'Webmote’s remote for the Living room TV: a D-pad with OK, Back, Home, Settings and Info buttons, and volume controls showing level 12.',
    remoteMedia:
      'The remote scrolled down: volume and brightness sliders, media keys, and buttons for the 123 pad, keyboard, inputs and picture settings.',
    picture:
      'The Picture sheet: sliders for backlight (OLED light) at 80, brightness at 50, contrast at 85 and colour at 50, and energy saving set to Off.',
    touchpad:
      'The Touchpad tab: a large touch area that says drag to move the pointer, tap to click, drag with two fingers to scroll, with Back and Home buttons.',
    tvOff: 'The Living room TV is off: Webmote shows a single Turn on button.',
  },

  anywhere: {
    kicker: 'Without opening the app',
    title: 'The quickest remote is the one you don’t have to open.',
    lead: 'Mute, change the volume or switch the TV off from wherever your thumb already is.',
    tiles: {
      title: 'Quick Settings tiles',
      text: 'Power, Mute, Screen off (the sound keeps playing) and Open remote, one swipe down from anywhere.',
      labels: ['TV power', 'TV mute', 'TV screen off', 'Remote'],
      states: ['On', 'Sound on', 'Screen on', 'Living room'],
    },
    widgets: {
      title: 'Home-screen widgets',
      text: 'A 4×1 strip with power, volume and mute, and a 4×2 mini remote with a D-pad.',
    },
    keys: {
      title: 'Your phone’s volume keys',
      text: 'They change the TV’s volume, even with the phone locked. You can turn this off.',
      tv: 'Living room',
    },
    notification: {
      title: 'A notification while the TV is on',
      text: 'Volume down, mute, volume up and power, right in your notifications.',
      tv: 'Living room',
      state: 'Connected',
      actions: ['Vol −', 'Mute', 'Vol +', 'Turn off'],
    },
  },

  remote: {
    kicker: 'The remote',
    title: 'Everything on one screen.',
    lead: 'The buttons you reach for every evening, laid out like a real remote, with sliders that follow what the TV is actually doing.',
    features: [
      { icon: 'dpad', title: 'D-pad', text: 'OK, Back, Home, Settings and Info.' },
      { icon: 'volume', title: 'The real volume', text: 'A slider that follows the TV’s actual level, with mute and ±.' },
      { icon: 'brightness', title: 'Brightness', text: 'Backlight, or OLED light on OLED sets.' },
      { icon: 'media', title: 'Media keys', text: 'Play/pause, stop, rewind and fast-forward.' },
      { icon: 'input', title: 'Inputs and 123 pad', text: 'HDMI 1, 2, 3…, channels, numbers and the colour keys.' },
      { icon: 'keyboard', title: 'Live keyboard', text: 'Type on your phone and the text appears on the TV as you type.' },
    ],
  },

  picture: {
    kicker: 'Picture',
    title: 'Tune the picture without the menu maze.',
    text: 'Brightness, contrast, colour and energy saving, in a Picture sheet that shows the TV’s current values.',
    honest:
      'If your TV ignores picture changes, Webmote greys the controls out and tells you why, instead of pretending they work.',
  },

  touchpad: {
    kicker: 'Touchpad and apps',
    title: 'A pointer when you need one. Every app, one tap away.',
    text: 'Move the TV’s pointer with your finger, tap to click and scroll with two fingers.',
    apps: 'Launch apps from a grid of everything installed on your TV, and pin your favourites to the top.',
  },

  power: {
    kicker: 'Power',
    title: 'TV off?\nOne tap and it’s on.',
    text: 'When the TV is off, Webmote shows a single Turn on button. One tap wakes the TV with Wake-on-LAN, and the remote appears as soon as it answers.',
    howTo: 'How to allow it on your TV',
  },

  tvs: {
    title: 'Every TV in the house',
    text: 'Save each TV, give it a name like Living room or Bedroom, and switch between them from the top bar.',
    list: ['Living room', 'Bedroom'],
    active: 'Active TV',
    add: 'Add a TV',
  },

  privacy: {
    kicker: 'Private by design',
    title: 'What happens in your living room stays there.',
    lead: 'Webmote collects nothing. It talks to your TVs, on your own network, and to nothing else.',
    points: [
      { icon: 'account', title: 'No account', text: 'Nothing to sign up for. Install it, pair your TV, done.' },
      { icon: 'analytics', title: 'No analytics', text: 'No tracking, no crash reporting, no third-party code sending data anywhere.' },
      { icon: 'wifi', title: 'Only your TV', text: 'Commands go straight to your TV, over an encrypted connection on your home network.' },
      {
        icon: 'shield',
        title: 'Your TV, verified',
        text: 'Each TV’s security certificate is recorded when you pair it, so another device can’t pretend to be your TV.',
      },
    ],
    policyLink: 'Read the privacy policy',
    siteNote: 'This website doesn’t track you either: no cookies, no analytics, nothing loaded from anywhere else.',
  },

  requirements: {
    kicker: 'Requirements',
    title: 'What you need',
    items: [
      { icon: 'tv', title: 'An LG TV with webOS 4 or later', text: '2018 models onwards.' },
      { icon: 'phone', title: 'An Android phone', text: 'Android 8.0 or later.' },
      { icon: 'wifi', title: 'The same Wi-Fi network', text: 'Your phone and your TV on the same home network.' },
    ],
    wakeTitle: 'To turn the TV on from your phone',
    wakeIntro: 'Allow the TV to be woken over the network. Depending on the model:',
    wake: [
      { model: 'Most models', action: 'Turn on “Turn on via Wi-Fi”', path: ['Settings', 'General', 'Mobile TV On'] },
      { model: '2025 models and later', action: 'Turn on “Wake on LAN”', path: ['Support', 'IP control settings'] },
    ],
    pathLabel: 'Menu path',
    languages: 'Webmote speaks English and French.',
  },

  faq: {
    kicker: 'FAQ',
    title: 'Questions',
    items: [
      {
        q: 'Which TVs does Webmote work with?',
        a: 'LG TVs running webOS 4 or later, which means LG smart TVs from 2018 onwards. Older LG models, and TVs from other brands, aren’t supported.',
      },
      { q: 'Is Webmote free?', a: 'Yes. Webmote is free, with nothing locked.' },
      {
        q: 'Do I need an account?',
        a: 'No. There’s nothing to sign up for: Webmote talks only to your TV, on your own network.',
      },
      {
        q: 'Why doesn’t Turn on wake my TV?',
        a: 'The TV has to allow it. Turn on “Turn on via Wi-Fi” (Settings › General › Mobile TV On) or, on 2025 models and later, “Wake on LAN” (Support › IP control settings). Your phone also needs to be on the same network as the TV.',
      },
      {
        q: 'Why are some picture settings greyed out?',
        a: 'Some TVs ignore picture changes sent by apps. When yours does, Webmote greys the controls out and tells you why, instead of pretending they work.',
      },
      {
        q: 'Does it work with the phone locked?',
        a: 'Your phone’s volume keys keep changing the TV’s volume while the phone is locked; you can turn this off in Webmote’s settings. The tiles, widgets and notification work without opening the app.',
      },
      {
        q: 'Is Webmote made by LG?',
        a: 'No. Webmote is an independent app by Canapé Studio. It is not affiliated with, endorsed or sponsored by LG Electronics.',
      },
      { q: 'Is there an iPhone version?', a: 'No, Webmote is an Android app.' },
    ],
    /** Only shown while GOOGLE_PLAY.live is false. */
    whenItem: {
      q: 'When can I get it?',
      a: 'Soon. Webmote goes through a closed test on Google Play before it opens to everyone. Want to try it early? Write to',
    },
  },

  closing: {
    title: 'Pick up your phone, not the remote.',
  },

  footer: {
    by: 'A Canapé Studio app',
    legal: 'Legal notice',
    privacy: 'Privacy policy',
    contact: 'Contact',
    disclaimer:
      'Webmote is an independent app. It is not affiliated with, endorsed or sponsored by LG Electronics. LG and webOS are trademarks of LG Electronics Inc.',
    copyright: 'Canapé Studio',
    /** Only shown once OPEN_SOURCE.public is true. */
    source: 'Source code',
    donate: 'Donate',
    licence: 'Webmote is free software, released under the GNU General Public License v3.',
  },

  privacyPage: {
    kicker: 'Privacy policy',
    back: 'Back to Webmote',
  },

  notFound: {
    title: 'This page isn’t here.',
    text: 'The link may be old, or mistyped.',
    home: 'Go to the home page',
  },
};

export type Dictionary = typeof en;
