import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, map, of, shareReplay } from 'rxjs';

import { API_BASE_URL } from '../../../core/config/api.config';
import { ApiResponse } from '../../../shared/models/api-response.model';
import { FORMATEUR_DEFAUT } from '../models/cours.constantes';

/** Informations publiques d'un utilisateur (contrat GET /api/users/{id}, voir CONTRATS_API.md). */
interface UtilisateurPublic {
  id: number;
  nom: string;
  prenom: string;
  role: string;
}

/**
 * Nom d'affichage d'un formateur à partir de son identifiant. Un appel par identifiant :
 * le résultat est gardé en cache, et un échec donne « Formateur » (jamais d'erreur à l'écran).
 */
@Injectable({ providedIn: 'root' })
export class FormateurService {
  private readonly http = inject(HttpClient);
  private readonly cache = new Map<number, Observable<string>>();

  nom(id: number): Observable<string> {
    let nom$ = this.cache.get(id);
    if (!nom$) {
      nom$ = this.http.get<ApiResponse<UtilisateurPublic>>(`${API_BASE_URL}/users/${id}`).pipe(
        map((r) => `${r.data.prenom} ${r.data.nom}`.trim() || FORMATEUR_DEFAUT),
        catchError(() => of(FORMATEUR_DEFAUT)),
        shareReplay(1),
      );
      this.cache.set(id, nom$);
    }
    return nom$;
  }
}
