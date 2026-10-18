/**
 * Couleur d'un pourcentage, la même partout dans le module Quiz (apprenant et formateur) :
 * vert à partir du seuil de réussite par défaut (70 %), orange à partir de 50 %, rouge en dessous.
 */
export function tonScore(v: number | null | undefined): '' | 'ok' | 'warn' | 'ko' {
  if (v == null) return '';
  return v >= 70 ? 'ok' : v >= 50 ? 'warn' : 'ko';
}
