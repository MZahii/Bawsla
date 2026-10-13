import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

import { API_BASE_URL } from '../../../core/config/api.config';
import { ApiResponse } from '../../../shared/models/api-response.model';
import {
  DemandeFormateur, DemandeFormateurEnvoi, Parcours, ProfilModification, Utilisateur, UtilisateurCreation,
  UtilisateurModification,
} from '../models/utilisateur.model';

/** Appels du module User : son propre compte (/me), demandes formateur et gestion des comptes par l'ADMIN. */
@Injectable({ providedIn: 'root' })
export class UserService {
  private readonly http = inject(HttpClient);
  private readonly url = `${API_BASE_URL}/users`;

  // ---------- Mon compte ----------

  moi(): Observable<Utilisateur> {
    return this.http.get<ApiResponse<Utilisateur>>(`${this.url}/me`).pipe(map((r) => r.data));
  }

  modifierMonProfil(body: ProfilModification): Observable<Utilisateur> {
    return this.http.put<ApiResponse<Utilisateur>>(`${this.url}/me`, body).pipe(map((r) => r.data));
  }

  changerMonMotDePasse(ancienMotDePasse: string, nouveauMotDePasse: string): Observable<void> {
    return this.http
      .put<ApiResponse<null>>(`${this.url}/me/mot-de-passe`, { ancienMotDePasse, nouveauMotDePasse })
      .pipe(map(() => undefined));
  }

  /** « Supprimer mon compte » (désactivation) ; le mot de passe confirme. */
  desactiverMonCompte(motDePasse: string): Observable<void> {
    return this.http.post<ApiResponse<null>>(`${this.url}/me/desactivation`, { motDePasse }).pipe(map(() => undefined));
  }

  monParcours(): Observable<Parcours> {
    return this.http.get<ApiResponse<Parcours>>(`${this.url}/me/parcours`).pipe(map((r) => r.data));
  }

  enregistrerMonParcours(body: Parcours): Observable<Parcours> {
    return this.http.put<ApiResponse<Parcours>>(`${this.url}/me/parcours`, body).pipe(map((r) => r.data));
  }

  /** Photo de profil (PNG, JPEG ou WebP, 2 Mo maximum). */
  envoyerAvatar(fichier: File): Observable<Utilisateur> {
    const form = new FormData();
    form.append('fichier', fichier);
    return this.http.post<ApiResponse<Utilisateur>>(`${this.url}/me/avatar`, form).pipe(map((r) => r.data));
  }

  supprimerAvatar(): Observable<Utilisateur> {
    return this.http.delete<ApiResponse<Utilisateur>>(`${this.url}/me/avatar`).pipe(map((r) => r.data));
  }

  // ---------- Demande formateur (demandeur) ----------

  /** Ma dernière demande, null si je n'en ai jamais fait. */
  maDemandeFormateur(): Observable<DemandeFormateur | null> {
    return this.http.get<ApiResponse<DemandeFormateur | undefined>>(`${this.url}/me/demande-formateur`).pipe(map((r) => r.data ?? null));
  }

  demanderAEtreFormateur(body: DemandeFormateurEnvoi): Observable<DemandeFormateur> {
    return this.http.post<ApiResponse<DemandeFormateur>>(`${this.url}/me/demande-formateur`, body).pipe(map((r) => r.data));
  }

  // ---------- Administration (le backend renvoie 403 aux autres rôles) ----------

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

  demandesFormateur(): Observable<DemandeFormateur[]> {
    return this.http.get<ApiResponse<DemandeFormateur[]>>(`${this.url}/demandes-formateur`).pipe(map((r) => r.data));
  }

  accepterDemande(id: number): Observable<DemandeFormateur> {
    return this.http.post<ApiResponse<DemandeFormateur>>(`${this.url}/demandes-formateur/${id}/accepter`, {}).pipe(map((r) => r.data));
  }

  refuserDemande(id: number, motif: string): Observable<DemandeFormateur> {
    return this.http.post<ApiResponse<DemandeFormateur>>(`${this.url}/demandes-formateur/${id}/refuser`, { motif }).pipe(map((r) => r.data));
  }
}
