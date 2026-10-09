/**
 * DONNÉES DE DÉMONSTRATION (suite) : tout ce qu'il faut pour remplir les trois espaces
 * (étudiant, enseignant, admin) tant que les services ne renvoient pas encore ces informations.
 * Même règle que demo-data.ts : chaque module remplace ces imports par l'appel à son service.
 */
import { COURS, CoursDemo, LIBELLE_PROFIL, ProfilApprenant, libellePrix } from './demo-data';

// ---------------------------------------------------------------- Cours : métadonnées d'affichage

export interface CoursMeta {
  image: string;
  inscrits: number;
  note: number;
  avis: number;
  statut: 'PUBLIE' | 'BROUILLON';
  majLe: string;
}

const IMG = 'assets/demo/cours/';

export const COURS_META: Record<number, CoursMeta> = {
  1: { image: IMG + 'donnees.jpg', inscrits: 128, note: 4.7, avis: 54, statut: 'PUBLIE', majLe: '02/10/2026' },
  2: { image: IMG + 'backend.jpg', inscrits: 96, note: 4.5, avis: 31, statut: 'PUBLIE', majLe: '28/09/2026' },
  3: { image: IMG + 'frontend.jpg', inscrits: 143, note: 4.8, avis: 67, statut: 'PUBLIE', majLe: '05/10/2026' },
  4: { image: IMG + 'devops.jpg', inscrits: 71, note: 4.4, avis: 19, statut: 'PUBLIE', majLe: '21/09/2026' },
  5: { image: IMG + 'outils.jpg', inscrits: 154, note: 4.9, avis: 88, statut: 'PUBLIE', majLe: '14/09/2026' },
  6: { image: IMG + 'api.jpg', inscrits: 62, note: 4.3, avis: 12, statut: 'PUBLIE', majLe: '06/10/2026' },
  7: { image: IMG + 'c.jpg', inscrits: 0, note: 0, avis: 0, statut: 'BROUILLON', majLe: '07/10/2026' },
};

export const meta = (id: number): CoursMeta => COURS_META[id] ?? COURS_META[1];
export const dureeTotale = (c: CoursDemo): number => c.chapitres.reduce((t, ch) => t + ch.duree, 0);
export const formatDuree = (minutes: number): string =>
  minutes < 60 ? `${minutes} min` : `${Math.floor(minutes / 60)} h ${String(minutes % 60).padStart(2, '0')}`;

export const CATEGORIES = [
  { nom: 'Données', icone: 'database', description: 'SQL, modélisation, performances' },
  { nom: 'Back-end', icone: 'dns', description: 'Spring Boot, API, microservices' },
  { nom: 'Front-end', icone: 'web', description: 'Angular, formulaires, état' },
  { nom: 'DevOps', icone: 'deployed_code', description: 'Docker, CI, déploiement' },
  { nom: 'Outils', icone: 'build', description: 'Git, C, ligne de commande' },
].map((c) => ({ ...c, cours: COURS.filter((x) => x.categorie === c.nom).length }));

export const NIVEAUX = [
  { code: 'DEBUTANT', libelle: 'Débutant', description: 'Aucun prérequis' },
  { code: 'INTERMEDIAIRE', libelle: 'Intermédiaire', description: 'Bases acquises' },
  { code: 'AVANCE', libelle: 'Avancé', description: 'Pour approfondir' },
] as const;

export const LIBELLE_NIVEAU: Record<string, string> = {
  DEBUTANT: 'Débutant',
  INTERMEDIAIRE: 'Intermédiaire',
  AVANCE: 'Avancé',
};

// ---------------------------------------------------------------- Apprenant : activité, objectifs, rappels

/** Minutes d'étude par jour, 18 dernières semaines (lundi → dimanche), pour la carte d'activité. */
export const ACTIVITE_JOURS: number[] = Array.from({ length: 18 * 7 }, (_, i) => {
  const s = Math.sin(i * 1.7) * 0.5 + Math.cos(i * 0.43) * 0.5;
  const weekend = i % 7 >= 5;
  const v = Math.max(0, Math.round((s + 0.35) * (weekend ? 30 : 70)));
  return i > 18 * 7 - 4 ? 0 : v;
});

/** Minutes d'étude par semaine (8 dernières semaines). */
export const ACTIVITE_SEMAINES = {
  labels: ['S32', 'S33', 'S34', 'S35', 'S36', 'S37', 'S38', 'S39'],
  minutes: [95, 140, 120, 210, 185, 240, 205, 260],
};

export const OBJECTIF_SEMAINE = { cible: 300, fait: 205 };

/**
 * Rappels personnels de l'apprenant (pas de calendrier de classe) : objectif de la semaine,
 * examens finaux disponibles, sessions en direct des cours suivis, quiz de chapitre à refaire.
 */
export const ECHEANCES = [
  { date: '09/10', jour: 'Jeu.', titre: 'Refaire le quiz « Les jointures SQL »', type: 'quiz', cours: 'Bases de données relationnelles' },
  { date: '11/10', jour: 'Sam.', titre: 'Objectif de la semaine : encore 95 min', type: 'rappel', cours: 'Tous mes cours' },
  { date: '14/10', jour: 'Mar.', titre: 'Session en direct : questions-réponses', type: 'live', cours: 'Microservices avec Spring Boot' },
  { date: '17/10', jour: 'Ven.', titre: 'Examen final disponible', type: 'examen', cours: 'Microservices avec Spring Boot' },
];

export const BADGES = [
  { icone: 'local_fire_department', titre: '7 jours d’affilée', obtenu: true },
  { icone: 'workspace_premium', titre: 'Premier cours terminé', obtenu: true },
  { icone: 'forum', titre: 'Meilleure réponse', obtenu: true },
  { icone: 'military_tech', titre: '100 % à un quiz', obtenu: true },
  { icone: 'school', titre: '3 certificats', obtenu: false },
  { icone: 'emoji_events', titre: '10 quiz réussis', obtenu: false },
];

export interface Certificat {
  numero: string;
  coursId: number;
  titulaire: string;
  date: string;
  /** Score obtenu à l'examen final, en %. */
  score: number;
  formateur: string;
  duree: string;
}

/** Certificats de l'apprenant connecté (obtenus en réussissant l'examen final d'un cours). */
export const CERTIFICATS: Certificat[] = [
  { numero: 'BWS-2026-0412', coursId: 5, titulaire: 'Malek Ouji', date: '29/09/2026', score: 92, formateur: 'Karim Zahi', duree: '1 h 40' },
];

/** Tous les certificats émis sur la plateforme (admin, page de vérification publique). */
export const CERTIFICATS_EMIS: Certificat[] = [
  ...CERTIFICATS,
  { numero: 'BWS-2026-0409', coursId: 3, titulaire: 'Sarra Trabelsi', date: '28/09/2026', score: 85, formateur: 'Rim Gafsi', duree: '2 h 20' },
  { numero: 'BWS-2026-0398', coursId: 1, titulaire: 'Amine Jlassi', date: '25/09/2026', score: 78, formateur: 'Imen Ayari', duree: '3 h 10' },
  { numero: 'BWS-2026-0391', coursId: 5, titulaire: 'Ines Mansouri', date: '24/09/2026', score: 96, formateur: 'Karim Zahi', duree: '1 h 40' },
  { numero: 'BWS-2026-0377', coursId: 2, titulaire: 'Khalil Haddad', date: '21/09/2026', score: 74, formateur: 'Sami Ben Ali', duree: '2 h 35' },
  { numero: 'BWS-2026-0362', coursId: 1, titulaire: 'Lina Ghribi', date: '18/09/2026', score: 88, formateur: 'Imen Ayari', duree: '3 h 10' },
  { numero: 'BWS-2026-0350', coursId: 3, titulaire: 'Omar Bouazizi', date: '15/09/2026', score: 71, formateur: 'Rim Gafsi', duree: '2 h 20' },
];

/** Aperçu : ajoute le certificat obtenu pendant la session (le vrai est créé par quiz-service). */
export function ajouterCertificat(coursId: number, score: number): Certificat {
  const c = COURS.find((x) => x.id === coursId)!;
  const cert: Certificat = {
    numero: 'BWS-2026-' + String(431 + CERTIFICATS.length).padStart(4, '0'),
    coursId,
    titulaire: 'Malek Ouji',
    date: new Date().toLocaleDateString('fr-FR'),
    score,
    formateur: c.enseignant,
    duree: formatDuree(dureeTotale(c)),
  };
  CERTIFICATS.unshift(cert);
  CERTIFICATS_EMIS.unshift(cert);
  return cert;
}

export const certificatParNumero = (numero: string): Certificat | undefined =>
  CERTIFICATS_EMIS.find((c) => c.numero.toLowerCase() === numero.trim().toLowerCase());

/** Examen final : état pour l'apprenant connecté (verrouillé tant que les chapitres ne sont pas finis). */
export const ETAT_EXAMEN: Record<number, { tentativesFaites: number; meilleurScore: number | null }> = {
  1: { tentativesFaites: 0, meilleurScore: null },
  2: { tentativesFaites: 0, meilleurScore: null },
  3: { tentativesFaites: 0, meilleurScore: null },
  5: { tentativesFaites: 1, meilleurScore: 92 },
};

/** Historique des quiz ; « autres » = moyenne des autres apprenants du même cours. */
export const HISTORIQUE_QUIZ = [
  { quizId: 5, titre: 'Git en équipe', date: '29/09', score: 100, autres: 81 },
  { quizId: 2, titre: 'Eureka et la gateway', date: '01/10', score: 88, autres: 72 },
  { quizId: 4, titre: 'Normaliser un schéma', date: '03/10', score: 57, autres: 64 },
  { quizId: 1, titre: 'Les jointures SQL', date: '07/10', score: 40, autres: 58 },
];

export const NOTIFICATIONS = [
  { icone: 'forum', texte: 'Imen Ayari a répondu à ta question sur LEFT JOIN', quand: 'il y a 1 h', lue: false },
  { icone: 'live_tv', texte: 'Session en direct mardi : Microservices avec Spring Boot', quand: 'il y a 3 h', lue: false },
  { icone: 'event', texte: 'Plus que 95 min pour ton objectif de la semaine', quand: 'hier', lue: true },
  { icone: 'workspace_premium', texte: 'Tu as obtenu le badge « 7 jours d’affilée »', quand: 'lundi', lue: true },
];

export const TAGS_FORUM = ['sql', 'jointures', 'spring', 'eureka', 'angular', 'signals', 'docker', 'git'];

// ---------------------------------------------------------------- Formateur : apprenants inscrits, avis, quiz générés

export type Risque = 'faible' | 'moyen' | 'eleve';

/**
 * Une inscription d'un apprenant à un cours du formateur connecté. Pas de classe ni de groupe :
 * n'importe qui s'inscrit, et le risque d'abandon est calculé par inscription.
 */
export interface InscriptionApprenant {
  id: number;
  nom: string;
  email: string;
  profil: ProfilApprenant;
  coursId: number;
  cours: string;
  inscritLe: string;
  progression: number;
  moyenne: number;
  risque: Risque;
  derniereActivite: string;
  tendance: number[];
  certifie: boolean;
}

const NOMS = [
  'Malek Ouji', 'Mohamed Zahi', 'Yassine Chakroun', 'Hamza Belkhouja', 'Oumaima Gafsi', 'Nidhal Fahem',
  'Aziz Boulifa', 'Sarra Trabelsi', 'Amine Jlassi', 'Ines Mansouri', 'Khalil Haddad', 'Rania Kefi',
  'Omar Bouazizi', 'Lina Ghribi', 'Fares Hamdi', 'Nour Ben Salem', 'Wassim Toumi', 'Eya Khemiri',
];
const DOMAINES_EMAIL = ['gmail.com', 'outlook.com', 'yahoo.fr', 'proton.me'];
const PROFILS: ProfilApprenant[] = ['ETUDIANT', 'PRO', 'RECONVERSION', 'ETUDIANT', 'PRO', 'AUTRE'];
/** Cours publiés par le formateur connecté (Imen Ayari dans l'aperçu). */
const COURS_FORMATEUR = [1, 7, 3];

const email = (nom: string, i: number) =>
  nom.toLowerCase().replace(/ /g, '.').normalize('NFD').replace(/[\u0300-\u036f]/g, '') + '@' + DOMAINES_EMAIL[i % 4];

export const APPRENANTS: InscriptionApprenant[] = NOMS.map((nom, i) => {
  const moyenne = [72, 68, 41, 48, 52, 63, 77, 85, 59, 90, 66, 44, 71, 81, 55, 62, 38, 74][i];
  const risque: Risque = moyenne < 50 ? 'eleve' : moyenne < 60 ? 'moyen' : 'faible';
  const coursId = i % 3 === 1 ? 3 : 1;
  const progression = Math.min(100, Math.round(moyenne * 0.9 + (i % 4) * 6));
  return {
    id: i + 1,
    nom,
    email: email(nom, i),
    profil: PROFILS[i % PROFILS.length],
    coursId,
    cours: COURS.find((c) => c.id === coursId)!.titre,
    inscritLe: `${String(2 + ((i * 3) % 27)).padStart(2, '0')}/09/2026`,
    progression,
    moyenne,
    risque,
    derniereActivite: ['aujourd’hui', 'hier', 'il y a 3 j', 'il y a 6 j'][i % 4],
    tendance: [0, 1, 2, 3, 4, 5].map((k) => Math.max(20, Math.min(100, moyenne + Math.round(Math.sin(i + k) * 10) - (risque === 'eleve' ? k * 3 : 0)))),
    certifie: progression >= 95 && moyenne >= 70,
  };
});

export const coursDuFormateur = () => COURS.filter((c) => COURS_FORMATEUR.includes(c.id));

/** Statistiques par cours du formateur (tableau de bord du studio). */
export const STATS_COURS_FORMATEUR = [
  { coursId: 1, inscriptions: 128, nouvelles: 14, achevement: 38, note: 4.7, avis: 54, certificats: 31, revenus: 0 },
  { coursId: 3, inscriptions: 143, nouvelles: 22, achevement: 44, note: 4.8, avis: 67, certificats: 40, revenus: 0 },
  { coursId: 7, inscriptions: 0, nouvelles: 0, achevement: 0, note: 0, avis: 0, certificats: 0, revenus: 0 },
];

/** Inscriptions par semaine aux cours du formateur (8 dernières semaines). */
export const INSCRIPTIONS_SEMAINES = {
  labels: ['S32', 'S33', 'S34', 'S35', 'S36', 'S37', 'S38', 'S39'],
  valeurs: [12, 18, 15, 29, 41, 33, 38, 36],
};

export interface AvisCours {
  id: number;
  coursId: number;
  nom: string;
  note: number;
  quand: string;
  texte: string;
  reponse?: string;
}

/** Avis des apprenants (fiche de cours publique et page « Avis » du formateur). */
export const AVIS: AvisCours[] = [
  { id: 1, coursId: 1, nom: 'Sarra Trabelsi', note: 5, quand: 'il y a 2 jours', texte: 'Très clair, surtout la partie sur les jointures. Le résumé audio m’a bien aidée à réviser dans le métro.', reponse: 'Merci Sarra ! Un chapitre sur les sous-requêtes arrive bientôt.' },
  { id: 2, coursId: 1, nom: 'Amine Jlassi', note: 4, quand: 'il y a 5 jours', texte: 'Bon cours. Les quiz de fin de chapitre préparent bien à l’examen final. J’aurais aimé plus d’exercices pratiques.' },
  { id: 3, coursId: 3, nom: 'Ines Mansouri', note: 5, quand: 'il y a 1 semaine', texte: 'Parfait pour une reconversion : je partais de zéro et j’ai eu mon certificat en trois semaines.' },
  { id: 4, coursId: 1, nom: 'Wassim Toumi', note: 2, quand: 'il y a 1 semaine', texte: 'Le chapitre sur la normalisation va trop vite. Je me suis perdu à partir de la 3FN.' },
  { id: 5, coursId: 3, nom: 'Fares Hamdi', note: 4, quand: 'il y a 2 semaines', texte: 'Très bien expliqué. Le passage sur les signaux mériterait un exemple de plus.', reponse: 'Bonne remarque, j’ajoute un exemple de formulaire avec des signaux.' },
  { id: 6, coursId: 1, nom: 'Lina Ghribi', note: 5, quand: 'il y a 3 semaines', texte: 'Je l’utilise au travail tous les jours maintenant. L’examen final est exigeant mais juste.' },
];

export const REPARTITION_NOTES = [
  { etoiles: 5, part: 71 },
  { etoiles: 4, part: 20 },
  { etoiles: 3, part: 6 },
  { etoiles: 2, part: 2 },
  { etoiles: 1, part: 1 },
];

export const LIBELLE_RISQUE: Record<Risque, string> = { faible: 'Suivi normal', moyen: 'À surveiller', eleve: 'Risque d’abandon' };

/** Score moyen des apprenants par quiz de chapitre et à l'examen final (cours du formateur). */
export const SCORES_PAR_QUIZ = {
  labels: ['Jointures SQL', 'Normalisation', 'Index', 'Angular', 'Signaux', 'Examen final'],
  apprenants: [58, 64, 72, 69, 66, 76],
  objectif: 70,
};

export const BROUILLONS_IA = [
  { type: 'Résumé', titre: 'Chapitre « Les jointures »', cours: 'Bases de données relationnelles', quand: 'il y a 20 min' },
  { type: 'Quiz', titre: 'Normaliser un schéma (7 questions, 2 rejetées)', cours: 'Bases de données relationnelles', quand: 'il y a 2 h' },
  { type: 'Résumé', titre: 'Chapitre « Feign et les appels entre services »', cours: 'Microservices avec Spring Boot', quand: 'hier' },
];

export interface QuestionGeneree {
  enonce: string;
  options: string[];
  bonne: number;
  bloom: string;
  difficulte: 'Facile' | 'Moyenne' | 'Difficile';
  rejetee?: string;
}

export const QUESTIONS_GENEREES: QuestionGeneree[] = [
  {
    enonce: 'Quelle forme normale impose que chaque attribut soit atomique ?',
    options: ['1FN', '2FN', '3FN', 'BCNF'],
    bonne: 0,
    bloom: 'Se souvenir',
    difficulte: 'Facile',
  },
  {
    enonce: 'Une table (num_commande, num_produit, nom_produit) avec la clé (num_commande, num_produit) viole quelle forme normale ?',
    options: ['1FN', '2FN', '3FN', 'Aucune'],
    bonne: 1,
    bloom: 'Analyser',
    difficulte: 'Moyenne',
  },
  {
    enonce: 'Quel est l’avantage principal de la normalisation ?',
    options: ['Requêtes plus rapides dans tous les cas', 'Moins de redondance et d’anomalies de mise à jour', 'Moins de tables', 'Aucun index nécessaire'],
    bonne: 1,
    bloom: 'Comprendre',
    difficulte: 'Facile',
  },
  {
    enonce: 'La 4FN traite quel type de dépendance ?',
    options: ['Fonctionnelle', 'Transitive', 'Multivaluée', 'Partielle'],
    bonne: 2,
    bloom: 'Se souvenir',
    difficulte: 'Difficile',
    rejetee: 'Hors du programme du chapitre (la 4FN n’est pas dans le cours).',
  },
  {
    enonce: 'Dénormaliser une table peut être utile pour…',
    options: ['Réduire les jointures en lecture', 'Supprimer les clés primaires', 'Éviter les index', 'Respecter la 3FN'],
    bonne: 0,
    bloom: 'Évaluer',
    difficulte: 'Moyenne',
  },
  {
    enonce: 'Quelle est la meilleure forme normale ?',
    options: ['1FN', '2FN', '3FN', 'Ça dépend du contexte'],
    bonne: 3,
    bloom: 'Évaluer',
    difficulte: 'Moyenne',
    rejetee: 'Réponses incohérentes entre les 5 générations (vérification Self-Consistency).',
  },
];

/** Calendrier du formateur : publications prévues, sessions en direct, avis et questions à traiter. */
export const EVENEMENTS_CALENDRIER = [
  { jour: 2, titre: 'Publication : chapitre « Index »', type: 'cours' },
  { jour: 6, titre: 'Nouveau quiz : Normalisation', type: 'quiz' },
  { jour: 9, titre: 'Répondre aux avis de la semaine', type: 'rappel' },
  { jour: 14, titre: 'Session en direct : questions SQL', type: 'live' },
  { jour: 17, titre: 'Mise à jour de l’examen final SQL', type: 'examen' },
  { jour: 21, titre: 'Publication : Programmation en C', type: 'cours' },
  { jour: 23, titre: 'Quiz Angular : nouvelles questions', type: 'quiz' },
  { jour: 28, titre: 'Session en direct : Angular et signaux', type: 'live' },
];

/** Agenda personnel de l'apprenant (vue mensuelle). */
export const EVENEMENTS_APPRENANT = [
  { jour: 4, titre: 'Objectif atteint : 300 min', type: 'rappel' },
  { jour: 9, titre: 'Refaire le quiz « Jointures »', type: 'quiz' },
  { jour: 11, titre: 'Fin de l’objectif de la semaine', type: 'rappel' },
  { jour: 14, titre: 'Live : Microservices (questions-réponses)', type: 'live' },
  { jour: 17, titre: 'Examen final : Microservices', type: 'examen' },
  { jour: 18, titre: 'Fin de l’objectif de la semaine', type: 'rappel' },
  { jour: 25, titre: 'Fin de l’objectif de la semaine', type: 'rappel' },
  { jour: 28, titre: 'Live : Angular et signaux', type: 'live' },
];

export const SIGNALEMENTS_FORUM = [
  { discussion: 'Réponses de l’examen final SQL à partager ?', motif: 'Partage de réponses d’examen', auteur: 'Wassim T.', quand: 'il y a 40 min' },
  { discussion: 'Publicité pour une formation externe', motif: 'Spam', auteur: 'Rania K.', quand: 'hier' },
];

// ---------------------------------------------------------------- Admin : plateforme, IA, audit

export const CROISSANCE = {
  labels: ['Nov.', 'Déc.', 'Janv.', 'Févr.', 'Mars', 'Avr.', 'Mai', 'Juin', 'Juil.', 'Août', 'Sept.', 'Oct.'],
  apprenants: [40, 52, 61, 75, 88, 97, 110, 118, 121, 126, 182, 214],
  formateurs: [4, 4, 5, 6, 6, 7, 7, 8, 8, 8, 11, 12],
};

/** Profils déclarés par les apprenants au questionnaire d'accueil. */
export const REPARTITION_PROFILS = {
  labels: Object.values(LIBELLE_PROFIL),
  valeurs: [96, 61, 38, 19],
};

/** Ventes des cours payants (paiement simulé dans l'aperçu). */
export const VENTES_MOIS = {
  labels: ['Mai', 'Juin', 'Juil.', 'Août', 'Sept.', 'Oct.'],
  montants: [790, 1185, 1027, 1422, 2370, 1659],
};

export const VENTES = [
  { ref: 'CMD-10482', quand: '08/10 21:14', acheteur: 'Khalil Haddad', coursId: 2, montant: 79, statut: 'PAYE' },
  { ref: 'CMD-10481', quand: '08/10 18:02', acheteur: 'Nour Ben Salem', coursId: 6, montant: 49, statut: 'PAYE' },
  { ref: 'CMD-10479', quand: '08/10 10:47', acheteur: 'Fares Hamdi', coursId: 2, montant: 79, statut: 'PAYE' },
  { ref: 'CMD-10476', quand: '07/10 22:30', acheteur: 'Eya Khemiri', coursId: 6, montant: 49, statut: 'REMBOURSE' },
  { ref: 'CMD-10471', quand: '07/10 16:12', acheteur: 'Omar Bouazizi', coursId: 2, montant: 79, statut: 'PAYE' },
  { ref: 'CMD-10468', quand: '06/10 09:55', acheteur: 'Rania Kefi', coursId: 6, montant: 49, statut: 'ECHEC' },
  { ref: 'CMD-10463', quand: '05/10 20:41', acheteur: 'Malek Ouji', coursId: 2, montant: 79, statut: 'PAYE' },
] as const;

export const LIBELLE_STATUT_VENTE: Record<string, string> = { PAYE: 'Payé', REMBOURSE: 'Remboursé', ECHEC: 'Échec' };

/** Demandes pour devenir formateur : n'importe qui peut postuler, l'admin valide. */
export const DEMANDES_FORMATEUR = [
  { id: 1, nom: 'Leila Mzoughi', email: 'leila.mzoughi@gmail.com', expertise: 'Data science, Python', experience: 'Data scientist depuis 6 ans', bio: 'J’analyse des données de santé et j’aimerais enseigner pandas et la visualisation à des débutants.', lien: 'linkedin.com/in/leila-mzoughi', quand: 'il y a 2 h' },
  { id: 2, nom: 'Youssef Ben Amor', email: 'y.benamor@outlook.com', expertise: 'Cybersécurité', experience: 'Pentester, 4 ans', bio: 'Je veux proposer un cours d’initiation à la sécurité web : OWASP, XSS, injections SQL.', lien: 'github.com/ybenamor', quand: 'hier' },
  { id: 3, nom: 'Mariem Chaabane', email: 'mariem.ch@gmail.com', expertise: 'UX design, Figma', experience: 'Designer produit, 8 ans', bio: 'Cours prévu : « Concevoir une interface claire », pour les développeurs qui veulent progresser en design.', lien: 'mariemch.design', quand: 'il y a 3 jours' },
];

export const ACTIVITE_MODULES = {
  labels: ['Lun.', 'Mar.', 'Mer.', 'Jeu.', 'Ven.', 'Sam.', 'Dim.'],
  cours: [320, 410, 380, 450, 390, 160, 120],
  quiz: [140, 190, 210, 230, 180, 70, 50],
  forum: [60, 85, 70, 95, 80, 30, 25],
};

export const REPARTITION_ROLES = { labels: ['Apprenants', 'Formateurs', 'Administrateurs'], valeurs: [214, 12, 2] };

export const REQUETES_IA = {
  labels: ['24/09', '25/09', '26/09', '27/09', '28/09', '29/09', '30/09', '01/10', '02/10', '03/10', '04/10', '05/10', '06/10', '07/10'],
  cours: [42, 51, 48, 60, 72, 30, 22, 66, 71, 80, 77, 85, 41, 35],
  quiz: [30, 28, 35, 41, 39, 18, 12, 44, 52, 49, 60, 58, 25, 20],
  forum: [18, 22, 25, 21, 30, 9, 7, 28, 33, 35, 31, 40, 16, 14],
};

export const MODELES_IA = [
  { module: 'User', usage: 'Recommandation et risque d’échec', modele: 'Gradient boosting', metrique: 'Précision', valeur: 87, echantillon: '40 apprenants de test', etat: 'ok' },
  { module: 'Cours', usage: 'Résumé et audio', modele: 'LLM + synthèse vocale', metrique: 'Résumés validés', valeur: 92, echantillon: '25 chapitres', etat: 'ok' },
  { module: 'Quiz', usage: 'Génération de QCM', modele: 'LLM + Self-Consistency', metrique: 'Questions acceptées', valeur: 78, echantillon: '180 questions', etat: 'attention' },
  { module: 'Forum', usage: 'Réponses avec sources (RAG)', modele: 'Embeddings + LLM', metrique: 'Réponses jugées utiles', valeur: 81, echantillon: '62 votes', etat: 'ok' },
];

export const QUOTA_IA = { utilise: 6420, limite: 10000, cout: 4.8 };

export const ERREURS_IA = [
  { quand: '07/10 14:32', module: 'Quiz', type: 'Délai dépassé', detail: 'Génération de 10 questions > 30 s' },
  { quand: '07/10 11:05', module: 'Forum', type: 'Réponse invalide', detail: 'JSON mal formé, régénéré automatiquement' },
  { quand: '06/10 18:47', module: 'Cours', type: 'Quota', detail: 'Limite horaire atteinte (5 min)' },
  { quand: '05/10 09:12', module: 'Quiz', type: 'Délai dépassé', detail: 'Génération de 8 questions > 30 s' },
];

export const AUDIT = [
  { quand: '07/10 15:02', qui: 'Admin Bawsla', action: 'a désactivé le compte', cible: 'Nidhal Fahem', type: 'utilisateur' },
  { quand: '07/10 14:40', qui: 'Imen Ayari', action: 'a publié le cours', cible: 'Bases de données relationnelles', type: 'cours' },
  { quand: '07/10 12:18', qui: 'Imen Ayari', action: 'a validé le résumé IA', cible: 'Chapitre « Les jointures »', type: 'ia' },
  { quand: '07/10 10:03', qui: 'Sami Ben Ali', action: 'a rejeté 2 questions générées', cible: 'Quiz « Normaliser un schéma »', type: 'ia' },
  { quand: '06/10 17:55', qui: 'Admin Bawsla', action: 'a créé la catégorie', cible: 'DevOps', type: 'catalogue' },
  { quand: '06/10 16:21', qui: 'Admin Bawsla', action: 'a révoqué le certificat', cible: 'BWS-2026-0301 (fraude à l’examen)', type: 'certificat' },
  { quand: '06/10 09:47', qui: 'Système', action: 'a terminé l’entraînement du modèle', cible: 'Recommandation v3', type: 'ia' },
  { quand: '05/10 18:30', qui: 'Imen Ayari', action: 'a masqué une discussion', cible: 'Réponses de l’examen final SQL', type: 'forum' },
  { quand: '05/10 15:04', qui: 'Admin Bawsla', action: 'a validé la demande de formateur de', cible: 'Karim Zahi', type: 'utilisateur' },
  { quand: '05/10 11:12', qui: 'Admin Bawsla', action: 'a modifié le quota IA', cible: '10 000 requêtes / mois', type: 'parametres' },
];

export const UTILISATEURS_ADMIN = [
  ...NOMS.map((nom, i) => ({
    id: i + 1,
    nom,
    email: APPRENANTS[i].email,
    role: 'ETUDIANT' as const,
    profil: LIBELLE_PROFIL[APPRENANTS[i].profil],
    actif: i !== 5,
    inscrit: `${String(2 + (i % 20)).padStart(2, '0')}/09/2026`,
    derniereConnexion: ['aujourd’hui', 'hier', 'il y a 3 j', 'il y a 2 sem.'][i % 4],
  })),
  { id: 101, nom: 'Imen Ayari', email: 'imen.ayari@gmail.com', role: 'ENSEIGNANT' as const, profil: 'Bases de données', actif: true, inscrit: '28/08/2026', derniereConnexion: 'aujourd’hui' },
  { id: 102, nom: 'Sami Ben Ali', email: 'sami.benali@proton.me', role: 'ENSEIGNANT' as const, profil: 'Back-end Java', actif: true, inscrit: '28/08/2026', derniereConnexion: 'hier' },
  { id: 103, nom: 'Rim Gafsi', email: 'rim.gafsi@gmail.com', role: 'ENSEIGNANT' as const, profil: 'Front-end', actif: true, inscrit: '30/08/2026', derniereConnexion: 'il y a 3 j' },
  { id: 104, nom: 'Karim Zahi', email: 'karim.zahi@outlook.com', role: 'ENSEIGNANT' as const, profil: 'DevOps', actif: false, inscrit: '01/09/2026', derniereConnexion: 'il y a 1 mois' },
  { id: 201, nom: 'Admin Bawsla', email: 'admin@bawsla.tn', role: 'ADMIN' as const, profil: '', actif: true, inscrit: '28/08/2026', derniereConnexion: 'aujourd’hui' },
];

export const FAQ = [
  {
    q: 'Bawsla est-il gratuit ?',
    r: 'La plupart des cours sont gratuits, examen final et certificat compris. Certains cours avancés sont payants : le prix est affiché sur la fiche du cours, et tu paies une seule fois pour un accès sans limite de durée.',
  },
  {
    q: 'À qui s’adresse Bawsla ?',
    r: 'À tout le monde : étudiants, professionnels qui veulent progresser, personnes en reconversion ou simples curieux. Il n’y a ni classe ni calendrier imposé : tu choisis tes cours et tu avances à ton rythme.',
  },
  {
    q: 'Comment j’obtiens un certificat ?',
    r: 'Termine tous les chapitres d’un cours : l’examen final se débloque. Si tu atteins le score demandé (en général 70 %), ton certificat est créé tout de suite, avec un numéro que n’importe qui peut vérifier sur le site.',
  },
  {
    q: 'Comment l’IA choisit-elle les cours qu’elle me recommande ?',
    r: 'Au début, à partir de ce que tu as indiqué à l’inscription (ton objectif, tes centres d’intérêt). Ensuite, à partir de tes résultats aux quiz : elle repère les notions où tu as le plus d’erreurs et te propose les chapitres qui les expliquent.',
  },
  {
    q: 'Les réponses de l’IA sont-elles fiables ?',
    r: 'Elles sont toujours signalées, citent le passage du cours utilisé et peuvent être corrigées par le formateur. Les résumés et quiz générés ne sont visibles qu’après sa validation.',
  },
  {
    q: 'Je veux enseigner, comment publier un cours ?',
    r: 'Crée un compte formateur et présente ton domaine d’expertise. Après validation de ton profil par l’équipe Bawsla, tu publies tes cours depuis ton studio, gratuits ou payants.',
  },
];

/** Choix du questionnaire d'accueil (inscription d'un apprenant). */
export const ONBOARDING = {
  profils: [
    { code: 'ETUDIANT' as ProfilApprenant, icone: 'school', titre: 'Étudiant', texte: 'Je suis à l’université ou au lycée' },
    { code: 'PRO' as ProfilApprenant, icone: 'work', titre: 'Professionnel', texte: 'Je veux progresser dans mon métier' },
    { code: 'RECONVERSION' as ProfilApprenant, icone: 'swap_horiz', titre: 'En reconversion', texte: 'Je change de voie vers l’informatique' },
    { code: 'AUTRE' as ProfilApprenant, icone: 'emoji_objects', titre: 'Curieux', texte: 'J’apprends pour le plaisir' },
  ],
  objectifs: ['Trouver un premier emploi', 'Évoluer dans mon poste', 'Réussir mes études', 'Lancer un projet personnel', 'Simplement apprendre'],
  temps: ['Moins de 1 h', '1 à 3 h', '3 à 5 h', 'Plus de 5 h'],
};

/** Données d'affichage d'une carte de cours (bw-course-tile). */
export const tuile = (c: CoursDemo, avecProgression = false) => ({
  id: c.id,
  titre: c.titre,
  description: c.description,
  categorie: c.categorie,
  niveau: LIBELLE_NIVEAU[c.niveau],
  enseignant: c.enseignant,
  image: meta(c.id).image,
  note: meta(c.id).note || undefined,
  avis: meta(c.id).avis,
  duree: formatDuree(dureeTotale(c)),
  chapitres: c.chapitres.length,
  progression: avecProgression ? c.progression : 0,
  prix: libellePrix(c.prix),
});
