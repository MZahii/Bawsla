import { Component } from '@angular/core';

import { EmptyState } from '../../shared/components/empty-state/empty-state';

/**
 * Emplacement réservé (socle). La vraie page de profil d'apprentissage
 * (profilage IA, compatibilités, cours suggérés) appartient au module user
 * et remplacera cette route par une PR.
 */
@Component({
  selector: 'app-profile',
  imports: [EmptyState],
  template: `
    <bw-empty-state
      title="Ton profil d'apprentissage arrive bientôt."
      message="La boussole analysera ton parcours pour te suggérer les cours qui te correspondent."
    />
  `,
})
export class Profile {}
