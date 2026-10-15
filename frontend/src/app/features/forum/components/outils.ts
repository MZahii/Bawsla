import { HttpErrorResponse } from '@angular/common/http';
import { Pipe, PipeTransform } from '@angular/core';

import { MotifSignalement, StatutDiscussion, TypeNotification } from '../models';

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
