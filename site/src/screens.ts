/**
 * App screenshots, per language. Sources: fastlane/metadata/android/{en-US,fr-FR}/images/phoneScreenshots/
 * (copied here so astro:assets can resize them; regenerate with tools/shot.sh and copy again).
 */
import type { Lang } from './i18n';
import enRemote from './assets/screens/en/remote.png';
import enRemoteMedia from './assets/screens/en/remote-media.png';
import enPicture from './assets/screens/en/picture.png';
import enTouchpad from './assets/screens/en/touchpad.png';
import enTvOff from './assets/screens/en/tv-off.png';
import frRemote from './assets/screens/fr/remote.png';
import frRemoteMedia from './assets/screens/fr/remote-media.png';
import frPicture from './assets/screens/fr/picture.png';
import frTouchpad from './assets/screens/fr/touchpad.png';
import frTvOff from './assets/screens/fr/tv-off.png';

export const screens = {
  en: { remote: enRemote, remoteMedia: enRemoteMedia, picture: enPicture, touchpad: enTouchpad, tvOff: enTvOff },
  fr: { remote: frRemote, remoteMedia: frRemoteMedia, picture: frPicture, touchpad: frTouchpad, tvOff: frTvOff },
} satisfies Record<Lang, Record<string, ImageMetadata>>;
