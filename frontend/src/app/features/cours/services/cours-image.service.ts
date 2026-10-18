import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, map, of, shareReplay } from 'rxjs';

import { API_BASE_URL } from '../../../core/config/api.config';
import { imageParDefaut } from '../models/cours.constantes';

/**
 * Image de couverture d'un cours. La route d'image passe par la gateway, qui exige le JWT : une balise <img>
 * ne l'enverrait pas. On télécharge donc l'image avec HttpClient et on affiche une URL locale (blob:).
 * Le cache est indexé par imageUrl, qui change à chaque nouvelle image (?v=…).
 */
@Injectable({ providedIn: 'root' })
export class CoursImageService {
  private readonly http = inject(HttpClient);
  private readonly cache = new Map<string, Observable<string>>();

  /** URL affichable : l'image du cours, ou l'image par défaut s'il n'en a pas ou si le chargement échoue. */
  url(cours: { id?: number; titre?: string; imageUrl?: string | null } | null | undefined): Observable<string> {
    const route = cours?.imageUrl;
    const defaut = imageParDefaut(cours);
    if (!route) return of(defaut);
    let image$ = this.cache.get(route);
    if (!image$) {
      image$ = this.http.get(route.replace(/^\/api/, API_BASE_URL), { responseType: 'blob' }).pipe(
        map((blob) => URL.createObjectURL(blob)),
        catchError(() => of(defaut)),
        shareReplay(1),
      );
      this.cache.set(route, image$);
    }
    return image$;
  }
}
