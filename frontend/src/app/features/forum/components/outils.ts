import { HttpErrorResponse } from '@angular/common/http';
import { Pipe, PipeTransform } from '@angular/core';

import { ActionModeration, MotifSignalement, StatutDiscussion, StatutSignalement, TypeNotification, TypeSanction } from '../models';

/**
 * Message à montrer à l'utilisateur pour une erreur HTTP : celui du backend (ApiError.message, déjà rédigé pour
 * l'humain), sinon une phrase utile (DESIGN.md §7.6). Jamais de code technique.
 */
export function messageErreur(erreur: unknown, defaut = 'Une erreur est survenue. Réessaie dans un instant.'): string {
  if (erreur instanceof HttpErrorResponse) {
    const message = (erreur.error as { message?: string } | null)?.message;
    if (erreur.status === 0) {
      return 'Le serveur ne répond pas. Vérifie ta connexion puis réessaie.';
    }
    if (erreur.status === 429 && !message) {
      return 'Tu as beaucoup publié en peu de temps. Réessaie un peu plus tard.';
    }
    if ((erreur.status === 502 || erreur.status === 503 || erreur.status === 504) && !message) {
      return 'L’IA met trop de temps à répondre. Réessaie dans un instant.';
    }
    // Erreur de validation : le détail du premier champ est plus utile que « Données invalides ».
    const details = (erreur.error as { details?: Record<string, string> } | null)?.details;
    const detail = details ? Object.values(details)[0] : undefined;
    if (erreur.status === 400 && detail) {
      return detail;
    }
    if (message) {
      return message;
    }
  }
  return defaut;
}

/** « il y a 5 min », « il y a 2 h », « hier », puis la date. */
export function tempsRelatif(iso: string | undefined | null, maintenant = Date.now()): string {
  if (!iso) {
    return '';
  }
  const date = new Date(iso);
  const secondes = Math.max(0, Math.round((maintenant - date.getTime()) / 1000));
  if (secondes < 60) {
    return 'à l’instant';
  }
  if (secondes < 3600) {
    return `il y a ${Math.floor(secondes / 60)} min`;
  }
  if (secondes < 86400) {
    return `il y a ${Math.floor(secondes / 3600)} h`;
  }
  if (secondes < 2 * 86400) {
    return 'hier';
  }
  if (secondes < 7 * 86400) {
    return `il y a ${Math.floor(secondes / 86400)} jours`;
  }
  return date.toLocaleDateString('fr-FR', { day: 'numeric', month: 'short', year: 'numeric' });
}

/** « 23 oct. 2026, 03:37 » (date et heure locales), partout où une date précise s'affiche. */
export function dateHeure(iso: string | undefined | null): string {
  if (!iso) {
    return '';
  }
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) {
    return '';
  }
  const jour = d.toLocaleDateString('fr-FR', { day: 'numeric', month: 'short', year: 'numeric' });
  const heure = d.toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit' });
  return `${jour}, ${heure}`;
}

/** Valeur d'un champ datetime-local (« 2026-10-23T03:37 ») pour une date du serveur (LocalDateTime, heure locale). */
export function versChampDate(iso: string | undefined | null): string {
  const d = iso ? new Date(iso) : null;
  if (!d || Number.isNaN(d.getTime())) {
    return '';
  }
  const p = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:${p(d.getMinutes())}`;
}

/** Date LocalDateTime attendue par le serveur à partir d'un champ datetime-local. */
export function depuisChampDate(valeur: string | undefined | null): string | undefined {
  return valeur ? `${versChampDate(valeur)}:00` : undefined;
}

@Pipe({ name: 'dateHeure' })
export class DateHeurePipe implements PipeTransform {
  transform(iso: string | undefined | null): string {
    return dateHeure(iso);
  }
}

@Pipe({ name: 'tempsRelatif' })
export class TempsRelatifPipe implements PipeTransform {
  transform(iso: string | undefined | null): string {
    return tempsRelatif(iso);
  }
}

export const LIBELLES_STATUT: Record<StatutDiscussion, string> = {
  OUVERTE: 'Ouverte',
  RESOLUE: 'Résolue',
  FERMEE: 'Fermée',
};

export const MOTIFS: { code: MotifSignalement; libelle: string }[] = [
  { code: 'HORS_SUJET', libelle: 'Hors sujet' },
  { code: 'IRRESPECTUEUX', libelle: 'Irrespectueux' },
  { code: 'SPAM', libelle: 'Spam ou publicité' },
  { code: 'FUITE_EXAMEN', libelle: 'Partage de réponses d’examen' },
  { code: 'AUTRE', libelle: 'Autre' },
];

export const LIBELLES_CATEGORIE: Record<string, string> = {
  OK: 'Rien à signaler',
  HORS_SUJET: 'Hors sujet',
  IRRESPECTUEUX: 'Irrespectueux',
  SPAM: 'Spam',
  FUITE_EXAMEN: 'Fuite d’examen',
  INJECTION: 'Manipulation de l’IA',
  DONNEES_PERSONNELLES: 'Données personnelles',
};

export const LIBELLES_SANCTION: Record<TypeSanction, string> = {
  AVERTISSEMENT: 'Avertissement',
  LECTURE_SEULE: 'Lecture seule',
  SUSPENSION: 'Suspension',
};

export const LIBELLES_STATUT_SIGNALEMENT: Record<StatutSignalement, string> = {
  OUVERT: 'Ouvert',
  CONFIRME: 'Confirmé',
  REJETE: 'Rejeté',
};

export const LIBELLES_ACTION: Record<ActionModeration, string> = {
  MASQUER: 'Masquage',
  MASQUAGE_AUTO: 'Masquage automatique',
  RETABLIR: 'Rétablissement',
  VERROUILLER: 'Verrouillage',
  DEVERROUILLER: 'Déverrouillage',
  EPINGLER: 'Épinglage',
  DESEPINGLER: 'Désépinglage',
  DEPLACER: 'Déplacement',
  FUSIONNER: 'Fusion d’un doublon',
  MODIFIER_ETIQUETTES: 'Modification des étiquettes',
  SUPPRIMER: 'Suppression',
  DECIDER_SIGNALEMENT: 'Décision sur un signalement',
  ESCALADER: 'Transmission à l’administration',
  SANCTIONNER: 'Sanction',
  LEVER_SANCTION: 'Levée de sanction',
  VALIDER_VIGIE: 'Validation d’une réponse de La Vigie',
  CORRIGER_VIGIE: 'Correction d’une réponse de La Vigie',
  REJETER_VIGIE: 'Rejet d’une réponse de La Vigie',
  MODIFIER_PARAMETRES: 'Modification des réglages',
  FUSIONNER_ETIQUETTES: 'Fusion d’étiquettes',
  RENOMMER_ETIQUETTE: 'Renommage d’une étiquette',
};

/** Cibles écrites dans le journal par forum-service (JournalService.tracer). */
export const LIBELLES_CIBLE: Partial<Record<string, string>> = {
  DISCUSSION: 'Discussion',
  REPONSE: 'Réponse',
  COMMENTAIRE: 'Commentaire',
  REPONSE_IA: 'Réponse de La Vigie',
  SIGNALEMENT: 'Signalement',
  MEMBRE: 'Membre',
  COURS: 'Réglages du cours',
  PLATEFORME: 'Réglages de la plateforme',
  ETIQUETTE: 'Étiquette',
};

export const LIBELLES_ROLE: Partial<Record<string, string>> = {
  ADMIN: 'Administrateur',
  ENSEIGNANT: 'Formateur',
  ETUDIANT: 'Apprenant',
  IA: 'Modération automatique',
};

/** Tous les codes que le serveur peut placer en tête d'un motif du journal (« CONFIRME : … », « LECTURE_SEULE : … »). */
const CODES_MOTIF: Record<string, string> = {
  ...LIBELLES_STATUT_SIGNALEMENT,
  ...LIBELLES_SANCTION,
  ...LIBELLES_CATEGORIE,
};

/** Motif du journal lisible : le code en tête est remplacé par son libellé. */
export function motifLisible(motif: string | undefined | null): string {
  if (!motif) {
    return '';
  }
  const m = motif.match(/^([A-Z][A-Z_]+)\b(.*)$/s);
  return m && CODES_MOTIF[m[1]] ? CODES_MOTIF[m[1]] + m[2] : motif;
}

/** Icône et phrase de chaque type de notification. */
export const NOTIFICATIONS: Record<TypeNotification, { icone: string; texte: string }> = {
  REPONSE: { icone: 'chat', texte: 'a répondu' },
  COMMENTAIRE: { icone: 'mode_comment', texte: 'a commenté ton message' },
  MENTION: { icone: 'alternate_email', texte: 't’a mentionné' },
  REPONSE_ACCEPTEE: { icone: 'verified', texte: 'Ta réponse a été choisie comme meilleure réponse' },
  APPROBATION: { icone: 'workspace_premium', texte: 'Ta réponse a été approuvée par le formateur' },
  VIGIE_PRETE: { icone: 'explore', texte: 'La Vigie a répondu à ta question' },
  MESSAGE_MASQUE: { icone: 'visibility_off', texte: 'Un de tes messages a été masqué' },
  HORS_COURS: { icone: 'help_center', texte: 'Question hors du cours : à compléter' },
  DEMANDE_GENERALE: { icone: 'public', texte: 'Un apprenant a demandé une réponse générale' },
  SIGNALEMENT: { icone: 'flag', texte: 'Nouveau signalement à examiner' },
  SANCTION: { icone: 'gavel', texte: 'Décision de la modération' },
  BADGE: { icone: 'military_tech', texte: 'Nouveau badge obtenu' },
};

/** Libellé d'un taux 0..1 en pourcentage arrondi. */
export function pourcent(taux: number | undefined | null): string {
  return `${Math.round((taux ?? 0) * 100)} %`;
}
