import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, map, of, shareReplay } from 'rxjs';

import { API_BASE_URL } from '../../../core/config/api.config';

/**
 * Photos de profil. La gateway exige le JWT, qu'un simple <img src> n'envoie pas : on charge donc l'image
 * en blob via HttpClient (l'intercepteur ajoute le token) et on la sert en object URL, mise en cache par
 * utilisateur et par version (une nouvelle photo change de version, donc d'URL).
 */
@Injectable({ providedIn: 'root' })
export class AvatarService {
  private readonly http = inject(HttpClient);
  private readonly cache = new Map<string, Observable<string | undefined>>();

  /** URL affichable de la photo, undefined sans photo (bw-avatar affiche alors les initiales). */
  url(userId: number, version: number | null | undefined): Observable<string | undefined> {
    if (!version) return of(undefined);
    const cle = `${userId}:${version}`;
    let obs = this.cache.get(cle);
    if (!obs) {
      obs = this.http.get(`${API_BASE_URL}/users/${userId}/avatar`, { params: { v: version }, responseType: 'blob' }).pipe(
        map((blob) => URL.createObjectURL(blob)),
        catchError(() => of(undefined)),
        shareReplay(1),
      );
      this.cache.set(cle, obs);
    }
    return obs;
  }
}
