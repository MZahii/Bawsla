/**
 * DONNÉES DE DÉMONSTRATION du template d'écrans.
 *
 * Elles servent uniquement à montrer l'interface tant que les services ne renvoient pas
 * encore ces informations. Chaque module remplace l'import de ce fichier par l'appel à son
 * service (voir les commentaires « À brancher » dans les pages). Ne pas importer ce fichier
 * dans du code de production une fois le module branché.
 */
import { Niveau } from '../../features/cours/models/cours.model';
import { Role } from '../../shared/models/user.model';

// ---------------------------------------------------------------- Notions (module User / Quiz)

export interface Notion {
  id: string;
  nom: string;
  /** Maîtrise de 0 à 100, calculée à partir des quiz. */
  maitrise: number;
}

export const NOTIONS: Notion[] = [
  { id: 'sql', nom: 'Requêtes SQL', maitrise: 82 },
  { id: 'jointures', nom: 'Jointures', maitrise: 34 },
  { id: 'normalisation', nom: 'Normalisation', maitrise: 58 },
  { id: 'spring', nom: 'Spring Boot', maitrise: 71 },
  { id: 'rest', nom: 'API REST', maitrise: 88 },
  { id: 'angular', nom: 'Angular', maitrise: 63 },
  { id: 'git', nom: 'Git', maitrise: 92 },
  { id: 'docker', nom: 'Docker', maitrise: 41 },
];

/** La notion vers laquelle l'IA pointe, et pourquoi (module User, recommandation). */
export const CAP_IA = {
  notionId: 'jointures',
  raison: 'Tu as raté 4 questions sur 6 sur les jointures dans tes deux derniers quiz.',
};

// ---------------------------------------------------------------- Cours

export interface Chapitre {
  titre: string;
  duree: number; // minutes
  fait: boolean;
}

/** Examen final d'un cours : il se débloque quand tous les chapitres sont terminés et donne le certificat. */
export interface ExamenFinal {
  questions: number;
  /** Durée en minutes. */
  duree: number;
  /** Score minimal pour obtenir le certificat, en %. */
  seuil: number;
  /** Nombre de tentatives autorisées. */
  tentatives: number;
}

export interface CoursDemo {
  id: number;
  titre: string;
  description: string;
  categorie: string;
  niveau: Niveau;
  /** Formateur qui a publié le cours. */
  enseignant: string;
  /** Prix en dinars ; 0 = cours gratuit (l'examen final et le certificat restent inclus). */
  prix: number;
  /** L'apprenant connecté est-il inscrit à ce cours ? */
  inscrit: boolean;
  /** Pourcentage de progression de l'apprenant connecté. */
  progression: number;
  chapitres: Chapitre[];
  motsCles: string[];
  notions: string[];
  examen: ExamenFinal;
}

/** Libellé d'un prix : « Gratuit » ou « 79 DT ». */
export const libellePrix = (prix: number): string => (prix > 0 ? `${prix} DT` : 'Gratuit');

export const NIVEAU_RANG: Record<Niveau, 1 | 2 | 3> = { DEBUTANT: 1, INTERMEDIAIRE: 2, AVANCE: 3 };

export const COURS: CoursDemo[] = [
  {
    id: 1,
    titre: 'Bases de données relationnelles',
    description:
      'Modéliser un schéma, écrire des requêtes SQL et comprendre comment le moteur les exécute.',
    categorie: 'Données',
    niveau: 'INTERMEDIAIRE',
    enseignant: 'Imen Ayari',
    prix: 0,
    inscrit: true,
    progression: 45,
    chapitres: [
      { titre: 'Le modèle relationnel', duree: 25, fait: true },
      { titre: 'Requêtes SELECT', duree: 30, fait: true },
      { titre: 'Les jointures', duree: 35, fait: false },
      { titre: 'Agrégations et GROUP BY', duree: 30, fait: false },
      { titre: 'Normalisation', duree: 40, fait: false },
      { titre: 'Index et performances', duree: 30, fait: false },
    ],
    motsCles: ['INNER JOIN', 'LEFT JOIN', 'clé étrangère', 'produit cartésien'],
    notions: ['sql', 'jointures', 'normalisation'],
    examen: { questions: 20, duree: 30, seuil: 70, tentatives: 3 },
  },
  {
    id: 2,
    titre: 'Microservices avec Spring Boot',
    description: 'Découper une application en services, les faire découvrir par Eureka et les exposer via une gateway.',
    categorie: 'Back-end',
    niveau: 'AVANCE',
    enseignant: 'Sami Ben Ali',
    prix: 79,
    inscrit: true,
    progression: 100,
    chapitres: [
      { titre: 'Pourquoi des microservices', duree: 20, fait: true },
      { titre: 'Eureka et la découverte', duree: 30, fait: true },
      { titre: 'API Gateway', duree: 35, fait: true },
      { titre: 'Feign et les appels entre services', duree: 30, fait: true },
      { titre: 'Sécuriser avec JWT', duree: 40, fait: true },
    ],
    motsCles: ['Eureka', 'Gateway', 'Feign', 'JWT'],
    notions: ['spring', 'rest'],
    examen: { questions: 25, duree: 40, seuil: 70, tentatives: 3 },
  },
  {
    id: 3,
    titre: 'Angular, de zéro aux signaux',
    description: 'Composants standalone, routage, formulaires réactifs et état avec les signaux.',
    categorie: 'Front-end',
    niveau: 'DEBUTANT',
    enseignant: 'Rim Gafsi',
    prix: 0,
    inscrit: true,
    progression: 20,
    chapitres: [
      { titre: 'Premier composant', duree: 20, fait: true },
      { titre: 'Templates et liaisons', duree: 25, fait: false },
      { titre: 'Routage', duree: 30, fait: false },
      { titre: 'Formulaires réactifs', duree: 35, fait: false },
      { titre: 'Signaux', duree: 30, fait: false },
    ],
    motsCles: ['standalone', 'router', 'signal', 'computed'],
    notions: ['angular'],
    examen: { questions: 20, duree: 30, seuil: 70, tentatives: 3 },
  },
  {
    id: 4,
    titre: 'Docker pour développeurs',
    description: 'Images, conteneurs, volumes et Docker Compose pour lancer une stack complète en une commande.',
    categorie: 'DevOps',
    niveau: 'DEBUTANT',
    enseignant: 'Karim Zahi',
    prix: 0,
    inscrit: false,
    progression: 0,
    chapitres: [
      { titre: 'Images et conteneurs', duree: 25, fait: false },
      { titre: 'Dockerfile', duree: 30, fait: false },
      { titre: 'Volumes et réseaux', duree: 25, fait: false },
      { titre: 'Docker Compose', duree: 35, fait: false },
    ],
    motsCles: ['image', 'conteneur', 'compose'],
    notions: ['docker'],
    examen: { questions: 15, duree: 25, seuil: 70, tentatives: 3 },
  },
  {
    id: 5,
    titre: 'Git en équipe',
    description: 'Branches, pull requests, résolution de conflits et bonnes pratiques de commit.',
    categorie: 'Outils',
    niveau: 'DEBUTANT',
    enseignant: 'Karim Zahi',
    prix: 0,
    inscrit: true,
    progression: 100,
    chapitres: [
      { titre: 'Commits et historique', duree: 20, fait: true },
      { titre: 'Branches', duree: 25, fait: true },
      { titre: 'Pull requests', duree: 25, fait: true },
      { titre: 'Conflits', duree: 30, fait: true },
    ],
    motsCles: ['branch', 'rebase', 'merge', 'PR'],
    notions: ['git'],
    examen: { questions: 15, duree: 20, seuil: 70, tentatives: 3 },
  },
  {
    id: 6,
    titre: 'Concevoir une API REST',
    description: 'Ressources, verbes HTTP, codes de retour, pagination et documentation OpenAPI.',
    categorie: 'Back-end',
    niveau: 'INTERMEDIAIRE',
    enseignant: 'Sami Ben Ali',
    prix: 49,
    inscrit: false,
    progression: 0,
    chapitres: [
      { titre: 'Ressources et URL', duree: 20, fait: false },
      { titre: 'Verbes et codes HTTP', duree: 25, fait: false },
      { titre: 'Pagination et filtres', duree: 25, fait: false },
      { titre: 'OpenAPI', duree: 30, fait: false },
    ],
    motsCles: ['GET', 'POST', '201', 'OpenAPI'],
    notions: ['rest'],
    examen: { questions: 20, duree: 30, seuil: 75, tentatives: 2 },
  },
  {
    id: 7,
    titre: 'Programmation en C',
    description: 'Variables, pointeurs, tableaux et allocation mémoire : les bases pour comprendre ce que fait la machine.',
    categorie: 'Outils',
    niveau: 'DEBUTANT',
    enseignant: 'Imen Ayari',
    prix: 39,
    inscrit: false,
    progression: 0,
    chapitres: [
      { titre: 'Types et variables', duree: 20, fait: false },
      { titre: 'Pointeurs', duree: 35, fait: false },
      { titre: 'Tableaux et chaînes', duree: 30, fait: false },
      { titre: 'malloc et free', duree: 30, fait: false },
      { titre: 'Fichiers', duree: 25, fait: false },
    ],
    motsCles: ['pointeur', 'malloc', 'tableau'],
    notions: [],
    examen: { questions: 20, duree: 30, seuil: 70, tentatives: 3 },
  },
];

/** Recommandations IA du tableau de bord (3 cours + une raison en une ligne). */
export const RECOMMANDATIONS = [
  { coursId: 1, chapitre: 'Les jointures', raison: 'Parce que tu as eu des difficultés avec les jointures SQL.' },
  { coursId: 4, chapitre: 'Images et conteneurs', raison: 'Ton objectif est le développement back-end : Docker y est utilisé partout.' },
  { coursId: 2, chapitre: 'Examen final', raison: 'Tous les chapitres sont terminés : ton examen final et ton certificat t’attendent.' },
];

/** Extrait du chapitre en cours de lecture (le vrai contenu viendra du PDF du cours). */
export const EXTRAIT_CHAPITRE = {
  coursId: 1,
  index: 2,
  resume:
    'Une jointure combine les lignes de deux tables à partir d’une colonne commune. INNER JOIN ne garde que les lignes qui ont une correspondance des deux côtés ; LEFT JOIN garde toutes les lignes de la table de gauche, et met NULL quand il n’y a pas de correspondance.',
  paragraphes: [
    'Jusqu’ici, chaque requête lisait une seule table. Or l’information utile est presque toujours répartie : les étudiants dans une table, leurs inscriptions dans une autre, les cours dans une troisième. La jointure est l’opération qui les recolle.',
    'Pour joindre deux tables, on indique la colonne qui les relie, en général une clé étrangère. Le moteur parcourt les deux tables et assemble les lignes dont les valeurs correspondent.',
  ],
  code: `SELECT e.nom, c.titre
FROM etudiant e
INNER JOIN inscription i ON i.etudiant_id = e.id
INNER JOIN cours c       ON c.id = i.cours_id
WHERE c.categorie = 'Données';`,
  apresCode:
    'Si un étudiant n’est inscrit à aucun cours, il n’apparaît pas dans ce résultat. Pour le garder quand même, on remplace le premier INNER JOIN par un LEFT JOIN : ses colonnes de cours vaudront NULL.',
};

// ---------------------------------------------------------------- Quiz

export interface QuestionDemo {
  enonce: string;
  options: string[];
  bonne: number;
  explication: string;
  bloom: string;
  notion: string;
}

export interface QuizDemo {
  id: number;
  titre: string;
  coursId: number;
  questions: number;
  duree: number;
  /** Dernier score de l'étudiant, null s'il ne l'a jamais passé. */
  score: number | null;
  notions: string[];
}

export const QUIZ: QuizDemo[] = [
  { id: 1, titre: 'Les jointures SQL', coursId: 1, questions: 5, duree: 8, score: 40, notions: ['jointures', 'sql'] },
  { id: 2, titre: 'Eureka et la gateway', coursId: 2, questions: 8, duree: 12, score: 88, notions: ['spring', 'rest'] },
  { id: 3, titre: 'Premiers pas avec Angular', coursId: 3, questions: 6, duree: 10, score: null, notions: ['angular'] },
  { id: 4, titre: 'Normaliser un schéma', coursId: 1, questions: 7, duree: 12, score: 57, notions: ['normalisation'] },
  { id: 5, titre: 'Git en équipe', coursId: 5, questions: 10, duree: 15, score: 100, notions: ['git'] },
  { id: 6, titre: 'Dockerfile et Compose', coursId: 4, questions: 6, duree: 10, score: null, notions: ['docker'] },
];

export const QUESTIONS: QuestionDemo[] = [
  {
    enonce:
      'La table etudiant contient 120 lignes, la table inscription 300. Quelle jointure renvoie aussi les étudiants qui ne sont inscrits à aucun cours ?',
    options: ['INNER JOIN', 'LEFT JOIN etudiant → inscription', 'RIGHT JOIN etudiant → inscription', 'CROSS JOIN'],
    bonne: 1,
    explication:
      'LEFT JOIN garde toutes les lignes de la table de gauche (etudiant). Quand un étudiant n’a pas d’inscription, les colonnes d’inscription valent NULL.',
    bloom: 'Appliquer',
    notion: 'Jointures',
  },
  {
    enonce: 'Que produit une jointure sans condition ON entre deux tables de 10 et 20 lignes ?',
    options: ['10 lignes', '20 lignes', '30 lignes', '200 lignes'],
    bonne: 3,
    explication:
      'Sans condition, chaque ligne de la première table est associée à chaque ligne de la seconde : c’est le produit cartésien, 10 × 20 = 200 lignes.',
    bloom: 'Comprendre',
    notion: 'Jointures',
  },
  {
    enonce: 'Dans la requête « … FROM cours c INNER JOIN inscription i ON i.cours_id = c.id », quel rôle joue i.cours_id ?',
    options: ['Une clé primaire', 'Une clé étrangère', 'Un index unique', 'Un alias de table'],
    bonne: 1,
    explication: 'i.cours_id fait référence à la clé primaire c.id de la table cours : c’est une clé étrangère.',
    bloom: 'Se souvenir',
    notion: 'Requêtes SQL',
  },
  {
    enonce: 'Tu veux le nombre d’inscrits par cours, y compris les cours sans inscrit. Quelle requête choisis-tu ?',
    options: [
      'SELECT c.titre, COUNT(*) FROM cours c INNER JOIN inscription i … GROUP BY c.titre',
      'SELECT c.titre, COUNT(i.id) FROM cours c LEFT JOIN inscription i … GROUP BY c.titre',
      'SELECT c.titre, COUNT(*) FROM inscription i GROUP BY c.titre',
      'SELECT COUNT(DISTINCT c.id) FROM cours c',
    ],
    bonne: 1,
    explication:
      'Le LEFT JOIN garde les cours sans inscrit, et COUNT(i.id) ne compte pas les NULL : ces cours affichent donc 0 au lieu de 1.',
    bloom: 'Analyser',
    notion: 'Jointures',
  },
  {
    enonce: 'Quelle forme normale interdit qu’un attribut non clé dépende d’un autre attribut non clé ?',
    options: ['1FN', '2FN', '3FN', 'BCNF'],
    bonne: 2,
    explication: 'La troisième forme normale (3FN) élimine les dépendances transitives entre attributs non clés.',
    bloom: 'Se souvenir',
    notion: 'Normalisation',
  },
];

// ---------------------------------------------------------------- Forum

export interface ReponseDemo {
  auteur: string;
  role: Role;
  date: string;
  contenu: string;
  votes: number;
  meilleure?: boolean;
}

export interface DiscussionDemo {
  id: number;
  titre: string;
  contenu: string;
  coursId: number;
  auteur: string;
  date: string;
  resolue: boolean;
  reponses: number;
  vues: number;
}

export const DISCUSSIONS: DiscussionDemo[] = [
  {
    id: 1,
    titre: 'Pourquoi mon LEFT JOIN renvoie autant de lignes qu’un INNER JOIN ?',
    contenu:
      'J’ai remplacé INNER JOIN par LEFT JOIN pour garder les étudiants sans inscription, mais j’obtiens exactement le même nombre de lignes. J’ai pourtant des étudiants sans cours. Ma requête finit par WHERE c.categorie = \'Données\'.',
    coursId: 1,
    auteur: 'Yassine C.',
    date: 'il y a 2 h',
    resolue: true,
    reponses: 3,
    vues: 41,
  },
  {
    id: 2,
    titre: 'Eureka affiche mon service mais la gateway renvoie 503',
    contenu: 'Le service apparaît bien sur le tableau Eureka, mais tous les appels via la gateway échouent en 503.',
    coursId: 2,
    auteur: 'Hamza B.',
    date: 'il y a 5 h',
    resolue: false,
    reponses: 1,
    vues: 18,
  },
  {
    id: 3,
    titre: 'Différence entre signal() et BehaviorSubject ?',
    contenu: 'Dans quels cas garder RxJS plutôt que les signaux ?',
    coursId: 3,
    auteur: 'Oumaima G.',
    date: 'hier',
    resolue: true,
    reponses: 4,
    vues: 63,
  },
  {
    id: 4,
    titre: 'Faut-il un volume pour MySQL dans docker-compose ?',
    contenu: 'Mes données disparaissent à chaque docker compose down.',
    coursId: 4,
    auteur: 'Nidhal F.',
    date: 'il y a 2 jours',
    resolue: false,
    reponses: 0,
    vues: 9,
  },
  {
    id: 5,
    titre: 'Rebase ou merge pour mettre à jour ma branche feature ?',
    contenu: 'develop a avancé, je dois récupérer les changements.',
    coursId: 5,
    auteur: 'Aziz B.',
    date: 'il y a 3 jours',
    resolue: true,
    reponses: 6,
    vues: 112,
  },
];

/** Réponse de l'IA (RAG du Forum) pour la discussion 1, avec source et confiance. */
export const REPONSE_IA = {
  discussionId: 1,
  contenu:
    'Ta condition WHERE c.categorie = \'Données\' s’applique après la jointure. Pour les étudiants sans inscription, c.categorie vaut NULL, donc le WHERE les élimine : ton LEFT JOIN se comporte comme un INNER JOIN. Déplace la condition dans le ON de la jointure sur cours.',
  source: { coursId: 1, chapitre: 'Les jointures', passage: 'LEFT JOIN et conditions de filtre' },
  confiance: 86,
};

export const REPONSES: ReponseDemo[] = [
  {
    auteur: 'Imen Ayari',
    role: 'ENSEIGNANT',
    date: 'il y a 1 h',
    contenu:
      'L’IA a raison. Écris : LEFT JOIN cours c ON c.id = i.cours_id AND c.categorie = \'Données\'. Garde en tête la règle : un filtre sur la table de droite d’un LEFT JOIN va dans le ON, pas dans le WHERE.',
    votes: 12,
    meilleure: true,
  },
  {
    auteur: 'Oumaima G.',
    role: 'ETUDIANT',
    date: 'il y a 1 h',
    contenu: 'J’avais le même problème la semaine dernière. Tu peux aussi écrire WHERE c.categorie = \'Données\' OR c.id IS NULL, mais c’est moins lisible.',
    votes: 4,
  },
];

// ---------------------------------------------------------------- Activité et utilisateurs

export const JOURNAL = [
  { quand: 'Aujourd’hui', icone: 'quiz', texte: 'Quiz « Les jointures SQL » : 2 bonnes réponses sur 5' },
  { quand: 'Aujourd’hui', icone: 'menu_book', texte: 'Chapitre « Requêtes SELECT » terminé' },
  { quand: 'Hier', icone: 'forum', texte: 'Ta question sur LEFT JOIN a reçu la meilleure réponse' },
  { quand: 'Lundi', icone: 'workspace_premium', texte: 'Cours « Git en équipe » terminé' },
];

export const PROFIL_APPRENTISSAGE = {
  libelle: 'Régulier',
  description: 'Tu avances un peu chaque jour, surtout le soir. Les sessions courtes te réussissent.',
  serie: 6,
};

/** Profil déclaré par l'apprenant à l'inscription (questionnaire d'accueil, facultatif). */
export type ProfilApprenant = 'ETUDIANT' | 'PRO' | 'RECONVERSION' | 'AUTRE';

export const LIBELLE_PROFIL: Record<ProfilApprenant, string> = {
  ETUDIANT: 'Étudiant',
  PRO: 'Professionnel',
  RECONVERSION: 'En reconversion',
  AUTRE: 'Curieux',
};

export interface UtilisateurDemo {
  id: number;
  prenom: string;
  nom: string;
  email: string;
  role: Role;
  actif: boolean;
  inscrit: string;
  /** Profil de l'apprenant (vide pour les formateurs et l'admin). */
  profil?: ProfilApprenant;
  /** Visible seulement par le formateur et l'admin (DESIGN.md, section 8). */
  besoinAide?: boolean;
}

export const UTILISATEURS: UtilisateurDemo[] = [
  { id: 1, prenom: 'Malek', nom: 'Ouji', email: 'malek.ouji@gmail.com', role: 'ETUDIANT', actif: true, inscrit: '02/09/2026', profil: 'ETUDIANT' },
  { id: 2, prenom: 'Mohamed', nom: 'Zahi', email: 'm.zahi@outlook.com', role: 'ETUDIANT', actif: true, inscrit: '02/09/2026', profil: 'PRO' },
  { id: 3, prenom: 'Yassine', nom: 'Chakroun', email: 'yassine.chakroun@gmail.com', role: 'ETUDIANT', actif: true, inscrit: '03/09/2026', profil: 'RECONVERSION', besoinAide: true },
  { id: 4, prenom: 'Imen', nom: 'Ayari', email: 'imen.ayari@gmail.com', role: 'ENSEIGNANT', actif: true, inscrit: '28/08/2026' },
  { id: 5, prenom: 'Hamza', nom: 'Belkhouja', email: 'hamza.b@yahoo.fr', role: 'ETUDIANT', actif: true, inscrit: '04/09/2026', profil: 'ETUDIANT', besoinAide: true },
  { id: 6, prenom: 'Sami', nom: 'Ben Ali', email: 'sami.benali@proton.me', role: 'ENSEIGNANT', actif: true, inscrit: '28/08/2026' },
  { id: 7, prenom: 'Nidhal', nom: 'Fahem', email: 'nidhal.fahem@gmail.com', role: 'ETUDIANT', actif: false, inscrit: '10/09/2026', profil: 'AUTRE' },
  { id: 8, prenom: 'Oumaima', nom: 'Gafsi', email: 'oumaima.gafsi@gmail.com', role: 'ETUDIANT', actif: true, inscrit: '05/09/2026', profil: 'PRO', besoinAide: true },
  { id: 9, prenom: 'Admin', nom: 'Bawsla', email: 'admin@bawsla.tn', role: 'ADMIN', actif: true, inscrit: '28/08/2026' },
];

export const coursParId = (id: number): CoursDemo | undefined => COURS.find((c) => c.id === id);
export const notionParId = (id: string): Notion | undefined => NOTIONS.find((n) => n.id === id);
