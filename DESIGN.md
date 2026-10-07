# DESIGN.md — Bawsla (البوصلة)

> **La boussole du savoir.** Plateforme e-learning intelligente où l'IA guide chaque étudiant vers sa prochaine étape.

Ce fichier est la référence visuelle unique du projet. Il sert à trois choses :
1. Générer les écrans avec un outil de design IA (Stitch ou autre).
2. Donner le même cadre à l'agent IA de chaque membre (Claude Code, ChatGPT) quand il code le front.
3. Garantir que les 4 modules (User, Cours, Quiz, Forum) ressemblent à **une seule application**.

Règle d'équipe : on n'invente pas de couleur, de police ou d'espacement. Tout vient d'ici. Une modification de ce fichier passe par une pull request.

---

## 1. Concept et objectifs de design

### L'idée
Le savoir est un trésor, l'apprentissage est un voyage, et l'IA est la **boussole** qui indique le cap. Le logo réunit les trois symboles : la **rose des vents** (l'orientation), la **plume** (l'écriture, le savoir transmis) et le **livre ouvert** (le savoir partagé, ouvert à tous). La métaphore reste discrète : on construit un outil académique sérieux, pas un jeu.

### Objectifs
| Objectif | Ce que ça implique dans l'interface |
|---|---|
| **Guider** | Chaque écran répond à « que dois-je faire ensuite ? » : une action principale par écran, des recommandations visibles. |
| **Rendre l'IA transparente** | Tout contenu généré par l'IA est signalé, sourcé quand c'est possible, et modifiable par un humain (section 7). |
| **Motiver** | La progression est visible (barres, niveaux, notions maîtrisées) et la réussite est valorisée. |
| **Rester lisible** | Beaucoup de blanc, une hiérarchie claire, du texte confortable pour lire un cours long. |
| **Accueillir tout le monde** | Contraste WCAG AA minimum, navigation au clavier, textes arabes affichés correctement (RTL). |

### Personnalité
Académique · Chaleureuse · Fiable · Claire. Jamais infantilisante, jamais froide.

---

## 2. Couleurs

La palette vient directement du logo : **bleu nuit**, **bordeaux** et **or**, sur un fond blanc cassé couleur parchemin.

### Rôle de chaque couleur
- **Bleu nuit** : la structure et la confiance. Menu, titres, texte, boutons principaux.
- **Bordeaux** : l'accent de la marque, d'esprit universitaire. Élément actif du menu, liens importants, progression, badges de niveau, meilleure réponse du forum.
- **Or** : la couleur de l'**IA**, comme l'aiguille dorée de la boussole qui indique le cap. Tout ce que l'IA produit ou propose porte l'or, et presque rien d'autre ne le porte.

### Palette — thème clair (par défaut)

| Token | HEX | Usage |
|---|---|---|
| `--color-navy-900` | `#141C2C` | Menu latéral, en-tête sombre |
| `--color-navy-700` | `#1C2639` | Texte principal, titres, boutons principaux |
| `--color-navy-500` | `#2E3B55` | Survol des boutons, icônes |
| `--color-navy-100` | `#E8EBF1` | Fonds de sélection, lignes survolées |
| `--color-bordeaux-700` | `#5A1726` | Survol des éléments bordeaux |
| `--color-bordeaux-600` | `#6B1E2E` | Accent de marque : liens, menu actif, progression, badges |
| `--color-bordeaux-500` | `#8E2F40` | Variante claire (graphiques, illustrations) |
| `--color-bordeaux-100` | `#F6E8EB` | Fond de l'élément actif du menu, encarts d'accent |
| `--color-gold-500` | `#D4A440` | Fond des boutons IA, aiguille, puces IA |
| `--color-gold-700` | `#7E5A12` | Texte et icônes IA sur fond clair |
| `--color-gold-100` | `#FBF3DF` | Fond des encarts générés par l'IA |
| `--color-bg` | `#F8F6F1` | Fond de page (parchemin) |
| `--color-surface` | `#FFFFFF` | Cartes, modales, champs |
| `--color-border` | `#E4E1DA` | Bordures, séparateurs |
| `--color-text` | `#1C2639` | Texte principal |
| `--color-text-muted` | `#5B6475` | Texte secondaire, légendes |
| `--color-success` | `#1A7A4D` | Bonne réponse, validation |
| `--color-warning` | `#9A5B00` | Avertissement, quota bientôt atteint |
| `--color-error` | `#C62828` | Mauvaise réponse, erreur |
| `--color-info` | `#2F6FD0` | Information neutre |

### Palette — thème sombre

| Token | HEX |
|---|---|
| `--color-bg` | `#141C2C` |
| `--color-surface` | `#1C2639` |
| `--color-border` | `#2E3B55` |
| `--color-text` | `#E8ECF3` |
| `--color-text-muted` | `#A3AEC2` |
| `--color-bordeaux-600` | `#D96B7E` |
| `--color-bordeaux-100` | `#3A1E28` |
| `--color-gold-500` | `#F2BE4A` |
| `--color-gold-700` | `#F2BE4A` |
| `--color-gold-100` | `#2F2817` |

Toutes les combinaisons texte/fond de ces tableaux ont été vérifiées : contraste d'au moins 4.5:1 (WCAG AA).

### Règles
- Proportion visée : 75 % neutres et parchemin, 15 % bleu nuit, 5 % bordeaux, 5 % or.
- Le **bordeaux** et le **rouge d'erreur** ne se côtoient jamais sans icône : une erreur a toujours l'icône `error` et un texte explicite.
- Une couleur ne porte jamais seule une information : une bonne réponse a aussi une icône ✓ et le mot « Correct ».
- Les dégradés du logo sont réservés au logo. L'interface est **en aplat**.

---

## 3. Typographie

| Rôle | Police | Remarque |
|---|---|---|
| Titres | **Poppins** (600, 700) | Même police que le nom « Bawsla » dans le logo |
| Texte | **Inter** (400, 500, 600) | Très lisible pour les cours longs |
| Arabe | **Cairo** (600, 700) | Même police que « البوصلة » dans le logo |
| Code | **JetBrains Mono** (400) | Extraits de code dans les cours, le quiz et le forum |

Toutes disponibles sur Google Fonts.

### Échelle

| Style | Taille / interligne | Graisse |
|---|---|---|
| Display | 40 / 48 px | Poppins 700 |
| H1 | 32 / 40 px | Poppins 700 |
| H2 | 24 / 32 px | Poppins 600 |
| H3 | 20 / 28 px | Poppins 600 |
| Body large (lecture de cours) | 18 / 30 px | Inter 400 |
| Body | 16 / 24 px | Inter 400 |
| Small | 14 / 20 px | Inter 400 |
| Caption, labels | 12 / 16 px | Inter 500 |

Largeur de lecture maximale : **72 caractères** (environ 720 px) pour le contenu d'un cours.

---

## 4. Espacement, formes, élévation

- **Grille de 8 px** : 4, 8, 12, 16, 24, 32, 48, 64.
- Marge intérieure d'une carte : 24 px. Espace entre cartes : 16 px (24 px sur desktop).
- **Rayons** : 8 px (champs, boutons), 12 px (cartes), 16 px (modales), 999 px (chips, badges).
- **Ombres** (douces, teintées de bleu nuit) :
  - `--shadow-sm` : `0 1px 2px rgba(20, 28, 44, 0.06)`
  - `--shadow-md` : `0 4px 12px rgba(20, 28, 44, 0.08)`
  - `--shadow-lg` : `0 12px 32px rgba(20, 28, 44, 0.12)` (modales)
- **Mise en page** : menu latéral fixe de 256 px (72 px replié) en `navy-900`, contenu centré de 1200 px maximum sur fond parchemin.
- **Points de rupture** : 600 px (mobile), 960 px (tablette), 1280 px (desktop). Sur mobile, le menu devient un tiroir.

---

## 5. Logo

### Fichiers (dossier `frontend/src/assets/brand/`)

| Fichier | Usage |
|---|---|
| `svg/bawsla-icon.svg` | Symbole seul, avec dégradés : page de connexion, accueil |
| `svg/bawsla-icon-flat.svg` | Symbole en aplat : menu latéral replié, petites tailles |
| `svg/bawsla-icon-dark.svg` | Symbole sur fond sombre (menu latéral) |
| `svg/bawsla-icon-mono.svg` | Une seule couleur : impression, filigrane |
| `svg/bawsla-icon-white.svg` | Tout blanc : sur photo ou fond coloré |
| `svg/bawsla-logo-horizontal.svg` (et `-dark`) | Symbole + « Bawsla » + « البوصلة » côte à côte : en-tête, rapport |
| `svg/bawsla-logo-stacked.svg` (et `-dark`) | Version verticale : page de connexion, couverture du rapport |
| `favicon.ico`, `svg/favicon.svg` | Onglet du navigateur |
| `png/apple-touch-icon-180.png`, `png/bawsla-icon-192.png`, `png/bawsla-icon-512.png` | Icônes d'application (PWA, mobile) |

### Règles d'usage
- **Zone de protection** : un espace vide égal à la largeur de la plume tout autour du logo.
- **Taille minimale** : 24 px pour le symbole seul, 120 px de large pour le logo horizontal.
- En dessous de 48 px, utiliser la version **en aplat** (`-flat`) : les dégradés deviennent flous.
- Sur le menu latéral sombre, utiliser la version **`-dark`**.
- Interdit : déformer, changer les couleurs, ajouter une ombre ou un contour, placer le logo en couleur sur un fond chargé.

### Iconographie
- **Material Symbols Rounded**, style outline, 24 px (20 px dans les listes denses).
- La **boussole** (`explore`) est l'icône de l'IA : elle accompagne chaque contenu généré.
- Icônes de module dans le menu : `dashboard` (Tableau de bord), `menu_book` (Cours), `quiz` (Quiz), `forum` (Forum), `person` (Profil).

---

## 6. Composants

### Boutons
| Variante | Style | Usage |
|---|---|---|
| Principal | Fond `navy-700`, texte blanc | L'action principale de l'écran (une seule) |
| Secondaire | Bordure `navy-700`, fond transparent, texte `navy-700` | Actions alternatives |
| Fantôme | Texte `bordeaux-600`, sans bordure | Actions tertiaires, « Annuler » |
| IA | Fond `gold-500`, texte `navy-700`, icône boussole | Lancer une génération IA (« Générer le résumé ») |
| Danger | Fond `error`, texte blanc | Suppression, toujours avec confirmation |

Hauteur 40 px, padding horizontal 16 px. États : survol (teinte plus foncée), focus (anneau 2 px `bordeaux-600`), désactivé (opacité 40 %), chargement (spinner + libellé conservé).

### Menu latéral
- Fond `navy-900`, logo `-dark` en haut, liens en blanc à 80 % d'opacité.
- Élément actif : fond blanc à 8 % d'opacité, texte blanc, barre verticale de 3 px en `gold-500` à gauche (l'aiguille de la boussole).

### Champs de formulaire
- Style Angular Material « outlined », libellé toujours visible, texte d'aide sous le champ.
- Focus en `navy-700`. Message d'erreur en `error`, avec une icône, qui explique comment corriger.

### Carte de cours
Vignette (16:9) · catégorie (chip) · badge de niveau · titre (H3) · description sur 2 lignes · barre de progression `bordeaux-600` · bouton « Continuer ».

### Carte de question (Quiz)
- Énoncé en Body large, 4 options sous forme de cartes cliquables (hauteur minimale 56 px).
- Badges en en-tête : niveau de Bloom (« Appliquer ») et compétence visée.
- Après réponse : bordure et icône `success` ou `error`, puis l'explication.

### Chips et badges
- Catégorie : fond `navy-100`, texte `navy-700`.
- Niveau : Débutant / Intermédiaire / Avancé, avec 1, 2 ou 3 points `bordeaux-600`.
- IA : fond `gold-100`, texte `gold-700`, icône boussole, libellé « Généré par l'IA ».
- Meilleure réponse (forum) : fond `bordeaux-100`, texte `bordeaux-600`, icône `verified`.

### Tuiles de statistiques (tableau de bord)
Valeur en H1 `navy-700`, libellé en Caption, petite tendance (↑ / ↓) colorée, sur une carte simple.

### Retours utilisateur
- **Toast** (snackbar) pour les confirmations, 4 secondes.
- **Bannière** pour les problèmes qui durent (service IA indisponible).
- **États vides** : le symbole du logo en version mono à 10 % d'opacité, une phrase utile, un bouton d'action. Exemple : « Aucun quiz pour l'instant. Crée le premier ! »
- **Chargement** : squelettes (skeletons) pour les listes, jamais un écran blanc.

---

## 7. Règles pour l'IA dans l'interface

Ces règles traduisent directement les objectifs du cours : transparence, lutte contre les hallucinations, humain dans la boucle.

1. **Toujours signaler l'IA.** Tout contenu généré est dans un encart à fond `gold-100`, bordure gauche de 3 px `gold-500`, avec l'icône boussole et le libellé « Généré par l'IA ».
2. **Montrer les sources.** Quand l'IA s'appuie sur un cours (RAG du Forum), elle affiche « Source : [cours], [passage] » sous la réponse, avec un lien.
3. **L'humain valide.** Un contenu destiné aux étudiants (QCM, résumé) est d'abord un **brouillon** que l'enseignant peut modifier, puis publier. Boutons : « Modifier », « Régénérer », « Valider ».
4. **Montrer la confiance et les rejets.** Les questions rejetées par la vérification de cohérence (Self-Consistency) restent visibles pour l'enseignant, grisées, avec la raison.
5. **Expliquer l'attente.** Pendant une génération : « La boussole analyse ton cours… » avec une rotation lente de l'aiguille. Au-delà de 10 secondes, proposer d'annuler.
6. **Des erreurs humaines.** Pas de message technique. Exemples :
   - Délai dépassé : « L'IA met trop de temps à répondre. Réessaie dans un instant. »
   - Quota atteint : « La limite d'utilisation de l'IA est atteinte pour aujourd'hui. »
   - Réponse invalide : « L'IA a produit une réponse inutilisable. Clique sur Régénérer. »
7. **Jamais de donnée personnelle visible** dans un prompt montré à l'utilisateur ou dans les journaux affichés.

---

## 8. Motifs par module

### Module 1 — User (Machine Learning)
- Tableau de bord étudiant : progression globale, notions maîtrisées vs à revoir, encart IA **« Ta boussole te recommande »** avec 3 cours et la raison en une ligne (« Parce que tu as eu des difficultés avec les jointures SQL »).
- Profil d'apprentissage (résultat du clustering) présenté positivement : « Profil : Régulier », jamais « faible ».
- Le **risque d'échec** n'est visible que par l'enseignant et l'admin, formulé comme une alerte d'accompagnement : « 3 étudiants pourraient avoir besoin d'aide ».
- Écran admin : précision du modèle affichée simplement (« Précision : 87 % sur 40 étudiants de test »).

### Module 2 — Cours (IA générative multimodale)
- Page de cours : en haut, l'encart IA « Résumé » (dépliable) avec les mots-clés en chips et un **lecteur audio** compact (« Écouter le résumé »).
- Puis le contenu complet ou le PDF, en largeur de lecture.
- Côté enseignant : zone de dépôt du PDF, puis aperçu du résumé généré en mode brouillon.

### Module 3 — Quiz (prompt engineering avancé)
- Formulaire de génération : thème, compétences, niveau de Bloom (cases), difficulté, nombre de questions, puis bouton IA « Générer le quiz ».
- Liste des questions générées : acceptées en carte normale, rejetées en carte grisée avec la raison.
- Passage du quiz : une question par écran, barre de progression `bordeaux-600` en haut, pas de minuteur agressif.
- Résultat : note en grand, liste des erreurs, puis l'encart IA **« Remédiation »** : la notion mal comprise, une explication courte et un bouton « Essayer une question de rattrapage ».

### Module 4 — Forum (NLP + RAG)
- Pendant la saisie d'une question : panneau **« Questions similaires déjà posées »** (avec la mention « résolue » en vert).
- Fil de discussion : la première réponse de l'IA est un encart IA avec source et niveau de confiance, puis les réponses humaines. La meilleure réponse porte le badge bordeaux.
- Recherche en haut de page, filtres par cours.

---

## 9. Ton et rédaction (microcopy)

- Langue de l'interface : **français**, avec le **tutoiement** pour les étudiants, cohérent partout.
- Phrases courtes, verbes d'action : « Continuer le cours », « Lancer le quiz », « Poser une question ».
- La métaphore de la boussole n'apparaît qu'aux moments clés : recommandations, chargement IA, page d'accueil.
- Célébrer sans exagérer : « Bravo, tu maîtrises les jointures SQL ! » plutôt qu'une avalanche d'emojis.

---

## 10. Accessibilité

- Contraste minimum **4.5:1** pour le texte, 3:1 pour les icônes et les bordures de champs.
- Tout est utilisable au clavier, avec un focus visible.
- Toutes les images et icônes porteuses de sens ont un texte alternatif. Le logo : `alt="Bawsla"`.
- Textes en arabe : `dir="auto"` ou `dir="rtl"`, et police Cairo.
- Respect de `prefers-reduced-motion` : l'animation de la boussole est désactivée si l'utilisateur le demande.
- Zones cliquables d'au moins 44 × 44 px.

---

## 11. Mise en œuvre dans Angular

Les tokens vivent dans `frontend/src/styles/_tokens.scss` et sont importés une seule fois dans `styles.scss`.

```scss
:root {
  --color-navy-900: #141C2C;
  --color-navy-700: #1C2639;
  --color-navy-500: #2E3B55;
  --color-navy-100: #E8EBF1;
  --color-bordeaux-700: #5A1726;
  --color-bordeaux-600: #6B1E2E;
  --color-bordeaux-500: #8E2F40;
  --color-bordeaux-100: #F6E8EB;
  --color-gold-500: #D4A440;
  --color-gold-700: #7E5A12;
  --color-gold-100: #FBF3DF;
  --color-bg: #F8F6F1;
  --color-surface: #FFFFFF;
  --color-border: #E4E1DA;
  --color-text: #1C2639;
  --color-text-muted: #5B6475;
  --color-success: #1A7A4D;
  --color-warning: #9A5B00;
  --color-error: #C62828;
  --color-info: #2F6FD0;

  --font-heading: 'Poppins', sans-serif;
  --font-body: 'Inter', sans-serif;
  --font-arabic: 'Cairo', sans-serif;
  --font-code: 'JetBrains Mono', monospace;

  --radius-sm: 8px;
  --radius-md: 12px;
  --radius-lg: 16px;
  --radius-pill: 999px;

  --shadow-sm: 0 1px 2px rgba(20, 28, 44, 0.06);
  --shadow-md: 0 4px 12px rgba(20, 28, 44, 0.08);
  --shadow-lg: 0 12px 32px rgba(20, 28, 44, 0.12);
}

[data-theme="dark"] {
  --color-bg: #141C2C;
  --color-surface: #1C2639;
  --color-border: #2E3B55;
  --color-text: #E8ECF3;
  --color-text-muted: #A3AEC2;
  --color-bordeaux-600: #D96B7E;
  --color-bordeaux-100: #3A1E28;
  --color-gold-500: #F2BE4A;
  --color-gold-700: #F2BE4A;
  --color-gold-100: #2F2817;
}
```

- Thème Angular Material : couleur primaire `navy-700`, couleur secondaire `bordeaux-600`, couleur tertiaire `gold-500`.
- Les composants partagés (logo, encart IA, carte de cours, tuile de stat, état vide) sont créés **une seule fois** dans `src/app/shared/` et réutilisés par tous les modules.
- Dans les composants, on utilise uniquement les variables (`var(--color-gold-700)`), jamais un code HEX en dur.

---

## 12. À faire / À éviter

| ✅ À faire | ❌ À éviter |
|---|---|
| Une action principale par écran | Plusieurs boutons principaux côte à côte |
| Signaler et sourcer chaque contenu IA | Présenter une réponse IA comme une vérité |
| L'or uniquement pour l'IA | L'or pour des boutons ordinaires ou de la décoration |
| Le bordeaux pour l'accent et la progression | Le bordeaux pour signaler une erreur |
| Une interface en aplat | Des dégradés ailleurs que dans le logo |
| Des messages d'erreur qui disent quoi faire | « Erreur 500 », « Something went wrong » |
| Réutiliser les composants de `shared/` | Recréer sa propre carte dans son module |
| Une métaphore de la boussole discrète | Une gamification envahissante |
