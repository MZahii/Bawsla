/**
 * Réponses au questionnaire d'accueil de l'apprenant (toutes facultatives).
 * Elles servent aux premières recommandations de l'IA, avant que l'apprenant ait passé des quiz.
 *
 * À brancher : PUT /api/users/me/profil (user-service) au lieu du stockage local, puis transmission
 * au module IA SANS donnée personnelle : seuls profil, objectif, domaines et temps sont envoyés.
 */
export interface ReponsesOnboarding {
  profil: string | null;
  objectif: string | null;
  domaines: string[];
  temps: string | null;
  /** true si l'apprenant a passé le questionnaire sans répondre. */
  passe: boolean;
}

const CLE = 'bawsla.onboarding';

export function lireOnboarding(): ReponsesOnboarding | null {
  try {
    const v = localStorage.getItem(CLE);
    return v ? (JSON.parse(v) as ReponsesOnboarding) : null;
  } catch {
    return null;
  }
}

export function enregistrerOnboarding(r: ReponsesOnboarding): void {
  try {
    localStorage.setItem(CLE, JSON.stringify(r));
  } catch {
    /* stockage indisponible : les recommandations restent génériques */
  }
}
