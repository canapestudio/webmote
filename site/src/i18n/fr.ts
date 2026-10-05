import type { Dictionary } from './en';

/**
 * French copy, same shape as en.ts. Write plain spaces before : ; ? ! » and after «:
 * the site swaps them for the right non-breaking spaces (see frenchSpacing in index.ts).
 * Wording follows the store listing (fastlane/metadata/android/fr-FR/full_description.txt).
 */
export const fr: Dictionary = {
  meta: {
    homeTitle: 'Webmote – Télécommande pour TV LG webOS, sur votre téléphone',
    homeDescription:
      'Webmote transforme votre téléphone Android en télécommande rapide et respectueuse de votre vie privée pour les TV LG sous webOS (2018 et plus) : tuiles, widgets, touches de volume.',
    privacyTitle: 'Politique de confidentialité – Webmote',
    privacyDescription:
      'Webmote ne collecte rien : ni compte, ni statistiques, ni rapport de plantage. Elle ne communique qu’avec vos TV, sur votre propre réseau.',
    notFoundTitle: 'Page introuvable – Webmote',
    ogLocale: 'fr_FR',
    ogImageAlt: 'Webmote : la télécommande de votre TV, sur votre téléphone.',
  },

  nav: {
    label: 'Navigation principale',
    home: 'Webmote, accueil',
    skip: 'Aller au contenu',
    features: 'Fonctions',
    privacy: 'Confidentialité',
    faq: 'Questions',
    switchTo: 'English',
    switchToShort: 'EN',
    switchToLabel: 'Read in English',
  },

  play: {
    soon: 'Bientôt sur Google Play',
    joinTest: 'Rejoindre le test fermé',
    badgeAlt: 'Disponible sur Google Play',
    attribution: 'Google Play et le logo Google Play sont des marques de Google LLC.',
    fdroid: 'Disponible sur F-Droid',
  },

  hero: {
    kicker: 'Télécommande pour TV LG webOS',
    titleA: 'La télécommande de votre TV,',
    titleB: 'sur votre téléphone.',
    lead: 'Webmote est une télécommande rapide et respectueuse de votre vie privée pour les TV LG sous webOS. Ouvrez l’appli et appuyez sur un bouton, ou ne l’ouvrez même pas.',
    facts: ['Gratuite', 'Android 8.0+', 'LG webOS 4+ (2018 et plus)'],
  },

  shots: {
    remote:
      'La télécommande de Webmote pour la TV Salon : pavé directionnel avec OK, boutons Retour, Accueil, Paramètres et Infos, et volume réglé sur 12.',
    remoteMedia:
      'La télécommande, plus bas : curseurs de volume et de luminosité, touches multimédia, et boutons 123, Clavier, Entrées et Image.',
    picture:
      'Le panneau Image : rétroéclairage (lumière OLED) à 80, luminosité à 50, contraste à 85, couleur à 50, et économie d’énergie désactivée.',
    touchpad:
      'L’onglet Pavé tactile : une grande zone tactile pour déplacer le pointeur, cliquer d’un appui et faire défiler à deux doigts, avec les boutons Retour et Accueil.',
    tvOff: 'La TV Salon est éteinte : Webmote affiche un seul bouton Allumer.',
  },

  anywhere: {
    kicker: 'Sans ouvrir l’appli',
    title: 'La télécommande la plus rapide, c’est celle qu’on n’ouvre pas.',
    lead: 'Coupez le son, réglez le volume ou éteignez la TV sans même chercher l’appli.',
    tiles: {
      title: 'Tuiles de réglages rapides',
      text: 'Alimentation, Sourdine, Écran éteint (le son continue) et Ouvrir la télécommande, d’un simple glissement vers le bas.',
      labels: ['Marche/arrêt TV', 'Sourdine TV', 'Écran TV éteint', 'Télécommande'],
      states: ['Allumée', 'Son activé', 'Écran allumé', 'Salon'],
    },
    widgets: {
      title: 'Widgets d’écran d’accueil',
      text: 'Une barre 4×1 avec alimentation, volume et sourdine, et une mini-télécommande 4×2 avec pavé directionnel.',
    },
    keys: {
      title: 'Les touches de volume du téléphone',
      text: 'Elles règlent le volume de la TV, même téléphone verrouillé. C’est désactivable.',
      tv: 'Salon',
    },
    notification: {
      title: 'Une notification tant que la TV est allumée',
      text: 'Volume −, sourdine, volume + et alimentation, directement dans vos notifications.',
      tv: 'Salon',
      state: 'Connecté',
      actions: ['Vol −', 'Muet', 'Vol +', 'Éteindre'],
    },
  },

  remote: {
    kicker: 'La télécommande',
    title: 'Tout sur un seul écran.',
    lead: 'Les boutons dont vous vous servez chaque soir, disposés comme sur une vraie télécommande, avec des curseurs qui suivent l’état réel de la TV.',
    features: [
      { icon: 'dpad', title: 'Pavé directionnel', text: 'OK, Retour, Accueil, Paramètres et Infos.' },
      { icon: 'volume', title: 'Le vrai volume', text: 'Un curseur qui suit le niveau réel de la TV, avec sourdine et ±.' },
      { icon: 'brightness', title: 'Luminosité', text: 'Rétroéclairage, ou lumière OLED sur les TV OLED.' },
      { icon: 'media', title: 'Touches multimédia', text: 'Lecture/pause, arrêt, retour et avance rapides.' },
      { icon: 'input', title: 'Entrées et pavé 123', text: 'HDMI 1, 2, 3…, chaînes, chiffres et touches de couleur.' },
      { icon: 'keyboard', title: 'Clavier en direct', text: 'Tapez sur votre téléphone, le texte s’affiche sur la TV au fur et à mesure.' },
    ],
  },

  picture: {
    kicker: 'Image',
    title: 'Réglez l’image sans fouiller dans les menus.',
    text: 'Luminosité, contraste, couleur et économie d’énergie, dans un panneau Image qui affiche les valeurs actuelles de la TV.',
    honest:
      'Si votre TV ignore les changements d’image, Webmote grise les réglages et explique pourquoi, au lieu de faire semblant qu’ils fonctionnent.',
  },

  touchpad: {
    kicker: 'Pavé tactile et applis',
    title: 'Un pointeur quand il le faut. Chaque appli à portée de doigt.',
    text: 'Déplacez le pointeur de la TV du bout du doigt, touchez pour cliquer et faites défiler à deux doigts.',
    apps: 'Lancez vos applis depuis la grille de celles installées sur la TV, et épinglez vos favorites en haut.',
  },

  power: {
    kicker: 'Allumage',
    title: 'TV éteinte ?\nUn appui, elle s’allume.',
    text: 'Quand la TV est éteinte, Webmote affiche un seul bouton Allumer. Un appui la réveille par Wake-on-LAN, et la télécommande apparaît dès qu’elle répond.',
    howTo: 'Comment l’autoriser sur votre TV',
  },

  tvs: {
    title: 'Toutes les TV de la maison',
    text: 'Enregistrez chaque TV, donnez-lui un nom comme « Salon » ou « Chambre », et passez de l’une à l’autre depuis la barre du haut.',
    list: ['Salon', 'Chambre'],
    active: 'TV active',
    add: 'Ajouter une TV',
  },

  privacy: {
    kicker: 'Respect de la vie privée',
    title: 'Ce qui se passe dans votre salon y reste.',
    lead: 'Webmote ne collecte rien. Elle communique avec vos TV, sur votre propre réseau, et avec rien d’autre.',
    points: [
      { icon: 'account', title: 'Pas de compte', text: 'Rien à créer. Installez l’appli, associez votre TV, c’est tout.' },
      { icon: 'analytics', title: 'Pas de statistiques', text: 'Ni suivi, ni rapport de plantage, ni code tiers qui envoie des données.' },
      { icon: 'wifi', title: 'Seulement votre TV', text: 'Les commandes vont directement à votre TV, par une connexion chiffrée sur votre réseau local.' },
      {
        icon: 'shield',
        title: 'Votre TV, vérifiée',
        text: 'Le certificat de sécurité de chaque TV est enregistré lors de l’association, pour qu’aucun autre appareil ne puisse se faire passer pour elle.',
      },
    ],
    policyLink: 'Lire la politique de confidentialité',
    siteNote: 'Ce site ne vous suit pas non plus : ni cookies, ni statistiques, rien de chargé depuis ailleurs.',
  },

  requirements: {
    kicker: 'Configuration requise',
    title: 'Ce qu’il vous faut',
    items: [
      { icon: 'tv', title: 'Une TV LG sous webOS 4 ou plus récent', text: 'Modèles 2018 et suivants.' },
      { icon: 'phone', title: 'Un téléphone Android', text: 'Android 8.0 ou plus récent.' },
      { icon: 'wifi', title: 'Le même réseau Wi-Fi', text: 'Votre téléphone et votre TV sur le même réseau domestique.' },
    ],
    wakeTitle: 'Pour allumer la TV depuis votre téléphone',
    wakeIntro: 'Autorisez la TV à être réveillée par le réseau. Selon le modèle :',
    wake: [
      { model: 'La plupart des modèles', action: 'Activez « Allumer via le Wi-Fi »', path: ['Paramètres', 'Général', 'Mobile TV On'] },
      { model: 'Modèles 2025 et suivants', action: 'Activez « Wake on LAN »', path: ['Assistance', 'Paramètres de contrôle IP'] },
    ],
    pathLabel: 'Chemin dans les menus',
    languages: 'Webmote parle français et anglais.',
  },

  faq: {
    kicker: 'Questions',
    title: 'Questions fréquentes',
    items: [
      {
        q: 'Avec quelles TV Webmote fonctionne-t-elle ?',
        a: 'Les TV LG sous webOS 4 ou plus récent, c’est-à-dire les TV connectées LG à partir de 2018. Les modèles LG plus anciens et les TV d’autres marques ne sont pas pris en charge.',
      },
      { q: 'Webmote est-elle gratuite ?', a: 'Oui. Webmote est gratuite, sans rien de verrouillé.' },
      {
        q: 'Faut-il créer un compte ?',
        a: 'Non. Il n’y a rien à créer : Webmote ne communique qu’avec votre TV, sur votre propre réseau.',
      },
      {
        q: 'Pourquoi Allumer ne réveille-t-il pas ma TV ?',
        a: 'La TV doit l’autoriser. Activez « Allumer via le Wi-Fi » (Paramètres › Général › Mobile TV On) ou, sur les modèles 2025 et suivants, « Wake on LAN » (Assistance › Paramètres de contrôle IP). Votre téléphone doit aussi être sur le même réseau que la TV.',
      },
      {
        q: 'Pourquoi certains réglages d’image sont-ils grisés ?',
        a: 'Certaines TV ignorent les changements d’image envoyés par les applis. Dans ce cas, Webmote grise les réglages et explique pourquoi, au lieu de faire semblant qu’ils fonctionnent.',
      },
      {
        q: 'Est-ce que ça marche téléphone verrouillé ?',
        a: 'Les touches de volume du téléphone continuent de régler le volume de la TV quand le téléphone est verrouillé ; c’est désactivable dans les paramètres de Webmote. Les tuiles, les widgets et la notification fonctionnent sans ouvrir l’appli.',
      },
      {
        q: 'Webmote est-elle faite par LG ?',
        a: 'Non. Webmote est une application indépendante de Canapé Studio. Elle n’est ni affiliée à LG Electronics, ni approuvée ou parrainée par cette société.',
      },
      { q: 'Existe-t-il une version iPhone ?', a: 'Non, Webmote est une application Android.' },
    ],
    whenItem: {
      q: 'Quand pourrai-je l’installer ?',
      a: 'Bientôt. Webmote passe par un test fermé sur Google Play avant d’être ouverte à tous. Envie de l’essayer en avance ? Écrivez à',
    },
  },

  closing: {
    title: 'Prenez votre téléphone, pas la télécommande.',
  },

  footer: {
    by: 'Une appli Canapé Studio',
    privacy: 'Politique de confidentialité',
    contact: 'Contact',
    disclaimer:
      'Webmote est une application indépendante. Elle n’est ni affiliée à LG Electronics, ni approuvée ou parrainée par cette société. LG et webOS sont des marques de LG Electronics Inc.',
    copyright: 'Canapé Studio',
    source: 'Code source',
    donate: 'Faire un don',
    licence: 'Webmote est un logiciel libre, publié sous licence publique générale GNU v3.',
  },

  privacyPage: {
    kicker: 'Politique de confidentialité',
    back: 'Retour à Webmote',
  },

  notFound: {
    title: 'Cette page n’existe pas.',
    text: 'Le lien est peut-être ancien, ou mal saisi.',
    home: 'Aller à l’accueil',
  },
};
