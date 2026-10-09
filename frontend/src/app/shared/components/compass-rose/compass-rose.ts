import { Component, computed, input } from '@angular/core';

export interface RoseNotion {
  id: string;
  nom: string;
  /** Maîtrise de 0 à 100. */
  maitrise: number;
}

/** Seuils de maîtrise (même découpage partout : rose, légendes, profil). */
export const niveauMaitrise = (m: number): 'maitrisee' | 'en-cours' | 'a-revoir' =>
  m >= 75 ? 'maitrisee' : m >= 50 ? 'en-cours' : 'a-revoir';

export const LIBELLE_MAITRISE = { maitrisee: 'Maîtrisée', 'en-cours': 'En cours', 'a-revoir': 'À revoir' } as const;

const C = 200; // centre du viewBox 400 × 400
const R_MIN = 34; // longueur d'une branche à 0 %
const R_MAX = 150; // longueur d'une branche à 100 %

/**
 * Rose des notions : chaque branche est une notion, sa longueur montre la maîtrise.
 * L'aiguille dorée (l'or = l'IA) pointe vers la notion que l'IA recommande de travailler.
 *
 * <bw-compass-rose [notions]="notions" cap="jointures" />
 *
 * Le SVG est décoratif pour les lecteurs d'écran : son résumé est dans aria-label et la
 * liste détaillée doit être affichée à côté (légende du tableau de bord ou du profil).
 */
@Component({
  selector: 'bw-compass-rose',
  templateUrl: './compass-rose.html',
  styleUrl: './compass-rose.scss',
})
export class CompassRose {
  readonly notions = input.required<RoseNotion[]>();
  /** id de la notion visée par l'IA ; absent = pas d'aiguille. */
  readonly cap = input<string>();
  /** Affiche le nom des notions autour de la rose. */
  readonly labels = input(true);

  protected readonly rings = [25, 50, 75, 100].map((p) => ({ p, r: R_MIN + ((R_MAX - R_MIN) * p) / 100 }));
  protected readonly outer = R_MAX + 14;

  /** Graduations du cadran : tous les 10°, plus longues aux 8 directions. */
  protected readonly ticks = Array.from({ length: 36 }, (_, i) => {
    const a = (i * 10 * Math.PI) / 180;
    const long = i % 9 === 0;
    const r1 = R_MAX + 14;
    const r2 = r1 + (long ? 10 : 5);
    return {
      long,
      x1: C + r1 * Math.sin(a),
      y1: C - r1 * Math.cos(a),
      x2: C + r2 * Math.sin(a),
      y2: C - r2 * Math.cos(a),
    };
  });

  protected readonly petals = computed(() => {
    const list = this.notions();
    const n = list.length;
    return list.map((notion, i) => {
      const deg = (360 / n) * i;
      const len = R_MIN + ((R_MAX - R_MIN) * Math.max(0, Math.min(100, notion.maitrise))) / 100;
      const half = Math.min(22, 120 / n + 6); // demi-largeur de la branche
      const shoulder = len * 0.3;
      // Branche pointant vers le haut, puis tournée de `deg`.
      const tip = `${C},${C - len}`;
      const left = `${C - half},${C - shoulder}`;
      const right = `${C + half},${C - shoulder}`;
      const centre = `${C},${C}`;
      const labelR = R_MAX + 40;
      const rad = (deg * Math.PI) / 180;
      const lx = C + labelR * Math.sin(rad);
      const ly = C - labelR * Math.cos(rad);
      const anchor = Math.abs(Math.sin(rad)) < 0.2 ? 'middle' : Math.sin(rad) > 0 ? 'start' : 'end';
      return {
        ...notion,
        deg,
        etat: niveauMaitrise(notion.maitrise),
        lightHalf: `${centre} ${left} ${tip}`,
        darkHalf: `${centre} ${tip} ${right}`,
        lx,
        ly: ly + (Math.cos(rad) < -0.2 ? 10 : Math.cos(rad) > 0.2 ? -2 : 4),
        anchor,
      };
    });
  });

  protected readonly needle = computed(() => {
    const id = this.cap();
    const p = this.petals().find((x) => x.id === id);
    return p ? { deg: p.deg, nom: p.nom } : null;
  });

  protected readonly summary = computed(() => {
    const parts = this.notions().map((n) => `${n.nom} ${n.maitrise} %`);
    const needle = this.needle();
    return `Rose des notions. ${parts.join(', ')}.${needle ? ` La boussole pointe vers ${needle.nom}.` : ''}`;
  });

  protected readonly cx = C;
}
