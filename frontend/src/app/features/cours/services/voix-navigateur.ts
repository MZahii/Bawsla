import { Injectable, signal } from '@angular/core';

export type LangueVoix = 'fr' | 'en';

const CODE_LANGUE: Record<LangueVoix, string> = { fr: 'fr-FR', en: 'en-US' };
/** Les moteurs vocaux des navigateurs coupent les longs textes : on lit phrase par phrase. */
const LONGUEUR_MAX = 200;

/**
 * Lecture à voix haute par le navigateur (Web Speech API), en secours quand le serveur n'a pas d'audio.
 * Aucun texte ni aucune clé ne sort de l'ordinateur de l'utilisateur.
 */
@Injectable({ providedIn: 'root' })
export class VoixNavigateur {
  /** false sur un navigateur sans synthèse vocale : on n'affiche alors pas le bouton. */
  readonly disponible = typeof window !== 'undefined' && 'speechSynthesis' in window && typeof SpeechSynthesisUtterance !== 'undefined';
  readonly enLecture = signal(false);

  lire(texte: string, langue: LangueVoix = 'fr'): void {
    if (!this.disponible) return;
    this.arreter();
    const phrases = decouper(texte);
    if (!phrases.length) return;
    const code = CODE_LANGUE[langue];
    const voix = window.speechSynthesis.getVoices().find((v) => v.lang.toLowerCase().startsWith(langue));
    this.enLecture.set(true);
    phrases.forEach((phrase, i) => {
      const enonce = new SpeechSynthesisUtterance(phrase);
      enonce.lang = code;
      if (voix) enonce.voice = voix;
      if (i === phrases.length - 1) {
        enonce.onend = () => this.enLecture.set(false);
      }
      enonce.onerror = (e) => {
        // « interrupted » / « canceled » : l'utilisateur a arrêté la lecture, ce n'est pas une erreur.
        if (e.error !== 'interrupted' && e.error !== 'canceled') this.enLecture.set(false);
      };
      window.speechSynthesis.speak(enonce);
    });
  }

  arreter(): void {
    if (!this.disponible) return;
    window.speechSynthesis.cancel();
    this.enLecture.set(false);
  }
}

/** Phrases d'au plus 200 caractères (une phrase trop longue est coupée sur une virgule ou une espace). */
export function decouper(texte: string): string[] {
  const resultat: string[] = [];
  for (const phrase of texte.replace(/\s+/g, ' ').trim().split(/(?<=[.!?…])\s+/)) {
    let reste = phrase.trim();
    while (reste.length > LONGUEUR_MAX) {
      let coupe = Math.max(reste.lastIndexOf(', ', LONGUEUR_MAX), reste.lastIndexOf(' ', LONGUEUR_MAX));
      if (coupe < LONGUEUR_MAX / 2) coupe = LONGUEUR_MAX;
      resultat.push(reste.slice(0, coupe + 1).trim());
      reste = reste.slice(coupe + 1).trim();
    }
    if (reste) resultat.push(reste);
  }
  return resultat;
}
