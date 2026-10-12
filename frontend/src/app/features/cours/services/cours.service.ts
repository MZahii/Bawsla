import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

import { API_BASE_URL } from '../../../core/config/api.config';
import { ApiResponse } from '../../../shared/models/api-response.model';
import { Chapitre, ChapitreRequest, CorrectionExplicationRequest, Cours, CoursRequest, CoursTexte, DisponibiliteExplication, Explication, Fiche, FicheRequest, LangueExplication, ModeExplication, Niveau, StatutCours, Traitement } from '../models/cours.model';

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

  // ------------------------------------------------------------------ fiche de révision (IA)

  /** Lance la génération en arrière-plan (propriétaire ou ADMIN) ; suivre l'avancement avec traitement(). */
  genererFiche(coursId: number): Observable<Traitement> {
    return this.http.post<ApiResponse<Traitement>>(`${this.url}/${coursId}/fiche/generer`, {}).pipe(map((r) => r.data));
  }

  traitement(coursId: number): Observable<Traitement> {
    return this.http.get<ApiResponse<Traitement>>(`${this.url}/${coursId}/traitement`).pipe(map((r) => r.data));
  }

  /**
   * Formateur : la fiche la plus récente. Apprenant : la dernière fiche validée.
   * `null` quand il n'y en a pas (le serveur répond 200 sans `data`) : ce n'est pas une erreur.
   */
  fiche(coursId: number): Observable<Fiche | null> {
    return this.http.get<ApiResponse<Fiche | null>>(`${this.url}/${coursId}/fiche`).pipe(map((r) => r.data ?? null));
  }

  /** Enregistre la fiche corrigée ; le backend recalcule le score de fidélité. */
  corrigerFiche(coursId: number, body: FicheRequest): Observable<Fiche> {
    return this.http.put<ApiResponse<Fiche>>(`${this.url}/${coursId}/fiche`, body).pipe(map((r) => r.data));
  }

  validerFiche(coursId: number): Observable<Fiche> {
    return this.http.patch<ApiResponse<Fiche>>(`${this.url}/${coursId}/fiche/valider`, {}).pipe(map((r) => r.data));
  }

  /** MP3 du résumé. Il exige le JWT : on le récupère en blob (voir FicheAudio). */
  audio(coursId: number): Observable<Blob> {
    return this.http.get(`${this.url}/${coursId}/audio`, { responseType: 'blob' });
  }

  // ------------------------------------------------------------------ explication orale par l'IA

  private urlExplication(coursId: number, route: string, langue: LangueExplication, mode: ModeExplication): string {
    return `${this.url}/${coursId}/explication${route}?langue=${langue}&mode=${mode}`;
  }

  /** Lance le script en arrière-plan (ou la traduction pour `en`) ; suivre avec traitementExplication(). */
  genererExplication(coursId: number, langue: LangueExplication, mode: ModeExplication, duree: number): Observable<Traitement> {
    return this.http.post<ApiResponse<Traitement>>(`${this.urlExplication(coursId, '/generer', langue, mode)}&duree=${duree}`, {}).pipe(map((r) => r.data));
  }

  /** « Écouter un aperçu » : met la version la plus récente en voix, à la demande du formateur. */
  apercuExplication(coursId: number, langue: LangueExplication, mode: ModeExplication): Observable<Traitement> {
    return this.http.post<ApiResponse<Traitement>>(this.urlExplication(coursId, '/apercu', langue, mode), {}).pipe(map((r) => r.data));
  }

  traitementExplication(coursId: number, langue: LangueExplication, mode: ModeExplication): Observable<Traitement> {
    return this.http.get<ApiResponse<Traitement>>(this.urlExplication(coursId, '/traitement', langue, mode)).pipe(map((r) => r.data));
  }

  /** Formateur : version la plus récente. Apprenant : dernière version validée. `null` : rien à montrer. */
  explication(coursId: number, langue: LangueExplication, mode: ModeExplication): Observable<Explication | null> {
    return this.http.get<ApiResponse<Explication | null>>(this.urlExplication(coursId, '', langue, mode)).pipe(map((r) => r.data ?? null));
  }

  /** Combinaisons langue x mode disponibles (validées pour l'apprenant). */
  explicationsDisponibles(coursId: number): Observable<DisponibiliteExplication[]> {
    return this.http.get<ApiResponse<DisponibiliteExplication[]>>(`${this.url}/${coursId}/explication/disponibles`).pipe(map((r) => r.data ?? []));
  }

  corrigerExplication(coursId: number, langue: LangueExplication, mode: ModeExplication, body: CorrectionExplicationRequest): Observable<Explication> {
    return this.http.put<ApiResponse<Explication>>(this.urlExplication(coursId, '', langue, mode), body).pipe(map((r) => r.data));
  }

  validerExplication(coursId: number, langue: LangueExplication, mode: ModeExplication): Observable<Explication> {
    return this.http.patch<ApiResponse<Explication>>(this.urlExplication(coursId, '/valider', langue, mode), {}).pipe(map((r) => r.data));
  }

  /** Audio de l'explication : il exige le JWT, donc blob (voir ExplicationLecteurAudio). */
  audioExplication(coursId: number, langue: LangueExplication, mode: ModeExplication): Observable<Blob> {
    return this.http.get(this.urlExplication(coursId, '/audio', langue, mode), { responseType: 'blob' });
  }
}

function formulaire(fichier: File): FormData {
  const form = new FormData();
  form.append('fichier', fichier, fichier.name);
  return form;
}
