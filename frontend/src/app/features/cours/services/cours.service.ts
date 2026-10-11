import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

import { API_BASE_URL } from '../../../core/config/api.config';
import { ApiResponse } from '../../../shared/models/api-response.model';
import { Chapitre, ChapitreRequest, Cours, CoursRequest, CoursTexte, Niveau, StatutCours } from '../models/cours.model';

/** Appels vers cours-service (via la gateway) : cours, chapitres, statut, fichiers et texte du PDF. */
@Injectable({ providedIn: 'root' })
export class CoursService {
  private readonly http = inject(HttpClient);
  private readonly url = `${API_BASE_URL}/cours`;

  // ------------------------------------------------------------------ cours

  findAll(filters: { categorie?: string; niveau?: Niveau } = {}): Observable<Cours[]> {
    let params = new HttpParams();
    if (filters.categorie) params = params.set('categorie', filters.categorie);
    if (filters.niveau) params = params.set('niveau', filters.niveau);
    return this.http.get<ApiResponse<Cours[]>>(this.url, { params }).pipe(map((r) => r.data));
  }

  findById(id: number): Observable<Cours> {
    return this.http.get<ApiResponse<Cours>>(`${this.url}/${id}`).pipe(map((r) => r.data));
  }

  create(body: CoursRequest): Observable<Cours> {
    return this.http.post<ApiResponse<Cours>>(this.url, body).pipe(map((r) => r.data));
  }

  update(id: number, body: CoursRequest): Observable<Cours> {
    return this.http.put<ApiResponse<Cours>>(`${this.url}/${id}`, body).pipe(map((r) => r.data));
  }

  delete(id: number): Observable<void> {
    return this.http.delete<ApiResponse<void>>(`${this.url}/${id}`).pipe(map(() => undefined));
  }

  /** Publier (au moins un chapitre requis), dépublier (BROUILLON) ou archiver. */
  changerStatut(id: number, statut: StatutCours): Observable<Cours> {
    return this.http.patch<ApiResponse<Cours>>(`${this.url}/${id}/statut`, { statut }).pipe(map((r) => r.data));
  }

  // ------------------------------------------------------------------ chapitres

  chapitres(coursId: number): Observable<Chapitre[]> {
    return this.http.get<ApiResponse<Chapitre[]>>(`${this.url}/${coursId}/chapitres`).pipe(map((r) => r.data));
  }

  creerChapitre(coursId: number, body: ChapitreRequest): Observable<Chapitre> {
    return this.http.post<ApiResponse<Chapitre>>(`${this.url}/${coursId}/chapitres`, body).pipe(map((r) => r.data));
  }

  modifierChapitre(coursId: number, chapitreId: number, body: ChapitreRequest): Observable<Chapitre> {
    return this.http.put<ApiResponse<Chapitre>>(`${this.url}/${coursId}/chapitres/${chapitreId}`, body).pipe(map((r) => r.data));
  }

  supprimerChapitre(coursId: number, chapitreId: number): Observable<void> {
    return this.http.delete<ApiResponse<void>>(`${this.url}/${coursId}/chapitres/${chapitreId}`).pipe(map(() => undefined));
  }

  /** `ids` : tous les chapitres du cours, dans le nouvel ordre. */
  ordonnerChapitres(coursId: number, ids: number[]): Observable<Chapitre[]> {
    return this.http.patch<ApiResponse<Chapitre[]>>(`${this.url}/${coursId}/chapitres/ordre`, { ids }).pipe(map((r) => r.data));
  }

  // ------------------------------------------------------------------ fichiers

  /** JPG ou PNG, 2 Mo maximum. */
  envoyerImage(coursId: number, fichier: File): Observable<Cours> {
    return this.http.post<ApiResponse<Cours>>(`${this.url}/${coursId}/image`, formulaire(fichier)).pipe(map((r) => r.data));
  }

  /** PDF, 20 Mo maximum. Le backend en extrait le texte. */
  envoyerPdf(coursId: number, fichier: File): Observable<Cours> {
    return this.http.post<ApiResponse<Cours>>(`${this.url}/${coursId}/pdf`, formulaire(fichier)).pipe(map((r) => r.data));
  }

  /** Le PDF exige le JWT : on le récupère en blob (un lien direct n'enverrait pas l'en-tête). */
  pdf(coursId: number): Observable<Blob> {
    return this.http.get(`${this.url}/${coursId}/pdf`, { responseType: 'blob' });
  }

  texte(coursId: number): Observable<CoursTexte> {
    return this.http.get<ApiResponse<CoursTexte>>(`${this.url}/${coursId}/texte`).pipe(map((r) => r.data));
  }
}

function formulaire(fichier: File): FormData {
  const form = new FormData();
  form.append('fichier', fichier, fichier.name);
  return form;
}
