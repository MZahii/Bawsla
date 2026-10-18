/**
 * Domaines du parcours = matières des cours (liste commune des modules Cours et Quiz, sans « Autre ») : un domaine
 * choisi correspond ainsi à la catégorie des cours recommandés. Miroir de ListesParcours.DOMAINES (user-service).
 */
export const DOMAINES_PARCOURS = [
  'Informatique', 'Mathématiques', 'Physique-Chimie', 'Sciences de la vie et de la Terre', 'Histoire', 'Géographie',
  'Langues', 'Économie et gestion', 'Psychologie', 'Philosophie', 'Arts',
];

/** Profils du questionnaire d'accueil (codes envoyés à user-service). */
export const PROFILS_PARCOURS = [
  { code: 'ETUDIANT', icone: 'school', titre: 'Étudiant', texte: 'Je suis à l’université ou au lycée' },
  { code: 'PRO', icone: 'work', titre: 'Professionnel', texte: 'Je veux progresser dans mon métier' },
  { code: 'RECONVERSION', icone: 'swap_horiz', titre: 'En reconversion', texte: 'Je change de voie' },
  { code: 'AUTRE', icone: 'emoji_objects', titre: 'Curieux', texte: 'J’apprends pour le plaisir' },
] as const;

/** Miroir de ListesParcours.OBJECTIFS (user-service). */
export const OBJECTIFS_PARCOURS = [
  'Trouver un premier emploi', 'Évoluer dans mon poste', 'Réussir mes études', 'Lancer un projet personnel', 'Simplement apprendre',
];

export const TEMPS_PARCOURS = ['Moins de 1 h', '1 à 3 h', '3 à 5 h', 'Plus de 5 h'];
