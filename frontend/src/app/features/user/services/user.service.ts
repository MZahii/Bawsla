import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

import { API_BASE_URL } from '../../../core/config/api.config';
import { ApiResponse } from '../../../shared/models/api-response.model';
import { Utilisateur, UtilisateurCreation, UtilisateurModification } from '../models/utilisateur.model';

/** Gestion des comptes par l'ADMIN (le backend renvoie 403 pour les autres rôles). */
@Injectable({ providedIn: 'root' })
export class UserService {
  private readonly http = inject(HttpClient);
  private readonly url = `${API_BASE_URL}/users`;

  /** Tous les comptes, les plus récents d'abord. */
  findAll(): Observable<Utilisateur[]> {
    return this.http.get<ApiResponse<Utilisateur[]>>(this.url).pipe(map((r) => r.data));
  }

  creer(body: UtilisateurCreation): Observable<Utilisateur> {
    return this.http.post<ApiResponse<Utilisateur>>(this.url, body).pipe(map((r) => r.data));
  }

  modifier(id: number, body: UtilisateurModification): Observable<Utilisateur> {
    return this.http.put<ApiResponse<Utilisateur>>(`${this.url}/${id}`, body).pipe(map((r) => r.data));
  }

  changerStatut(id: number, actif: boolean): Observable<Utilisateur> {
    return this.http.patch<ApiResponse<Utilisateur>>(`${this.url}/${id}/statut`, { actif }).pipe(map((r) => r.data));
  }

  /** Définit un nouveau mot de passe et débloque le compte s'il était verrouillé. */
  reinitialiserMotDePasse(id: number, motDePasse: string): Observable<void> {
    return this.http.put<ApiResponse<null>>(`${this.url}/${id}/mot-de-passe`, { motDePasse }).pipe(map(() => undefined));
  }
}
