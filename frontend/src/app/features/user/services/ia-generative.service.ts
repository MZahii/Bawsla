import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

import { API_BASE_URL } from '../../../core/config/api.config';
import { ApiResponse } from '../../../shared/models/api-response.model';
import {
  ConseilIa,
  ContenuIa,
  ExplicationRisqueIa,
  PlanIa,
  RapportCohorteIa,
  SuggestionParcoursIa,
} from '../models/ia-generative.model';

/**
 * IA générative du module User, via user-service (qui rassemble les données et n'envoie au LLM ni nom ni email).
 * `dernier…` : le texte déjà généré (aucun appel au LLM, `null` s'il n'y en a pas). `generer…` : génère, ou renvoie
 * le texte gardé si les données n'ont pas changé. LLM indisponible : erreur 503 avec un message clair.
 */
@Injectable({ providedIn: 'root' })
export class IaGenerativeService {
  private readonly http = inject(HttpClient);
  private readonly users = `${API_BASE_URL}/users`;

  dernierConseil(): Observable<ContenuIa<ConseilIa> | null> {
    return this.lire<ConseilIa>(`${this.users}/me/ia/conseil`);
  }

  genererConseil(): Observable<ContenuIa<ConseilIa>> {
    return this.ecrire<ConseilIa>(`${this.users}/me/ia/conseil`);
  }

  dernierPlan(): Observable<ContenuIa<PlanIa> | null> {
    return this.lire<PlanIa>(`${this.users}/me/ia/plan`);
  }

  genererPlan(): Observable<ContenuIa<PlanIa>> {
    return this.ecrire<PlanIa>(`${this.users}/me/ia/plan`);
  }

  /** ENSEIGNANT ou ADMIN. */
  derniereExplication(etudiantId: number): Observable<ContenuIa<ExplicationRisqueIa> | null> {
    return this.lire<ExplicationRisqueIa>(`${this.users}/${etudiantId}/ia/explication`);
  }

  genererExplication(etudiantId: number): Observable<ContenuIa<ExplicationRisqueIa>> {
    return this.ecrire<ExplicationRisqueIa>(`${this.users}/${etudiantId}/ia/explication`);
  }

  /** ENSEIGNANT (ses cours) ou ADMIN (tous). */
  dernierRapport(): Observable<ContenuIa<RapportCohorteIa> | null> {
    return this.lire<RapportCohorteIa>(`${this.users}/formateur/apprenants/rapport`);
  }

  genererRapport(): Observable<ContenuIa<RapportCohorteIa>> {
    return this.ecrire<RapportCohorteIa>(`${this.users}/formateur/apprenants/rapport`);
  }

  /** Domaines et objectif suggérés à partir d'une phrase ; rien n'est enregistré. */
  suggererParcours(texte: string): Observable<SuggestionParcoursIa> {
    return this.http
      .post<ApiResponse<SuggestionParcoursIa>>(`${this.users}/me/parcours/suggestion`, { texte })
      .pipe(map((r) => r.data));
  }

  private lire<T>(url: string): Observable<ContenuIa<T> | null> {
    return this.http.get<ApiResponse<ContenuIa<T> | null>>(url).pipe(map((r) => r.data ?? null));
  }

  private ecrire<T>(url: string): Observable<ContenuIa<T>> {
    return this.http.post<ApiResponse<ContenuIa<T>>>(url, {}).pipe(map((r) => r.data));
  }
}
