import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map, shareReplay } from 'rxjs';

import { API_BASE_URL } from '../../../core/config/api.config';
import { ApiResponse } from '../../../shared/models/api-response.model';
import {
  Activite,
  ClassementEntree,
  CommentaireRequest,
  CompteurNotifications,
  CoursOption,
  DiscussionDetail,
  DiscussionModificationRequest,
  DiscussionRequest,
  DiscussionResume,
  DiscussionsFiltres,
  Etiquette,
  NotificationForum,
  PageResponse,
  PourToi,
  ProfilForum,
  ReactionsResponse,
  Reponse,
  ReponseRequest,
  Reputation,
  SignalementRequest,
  Signalement,
  SimilairesRequest,
  SimilairesResponse,
  TypeReaction,
  Verification,
  VersionDiscussion,
  VoteRequest,
  VoteResponse,
} from '../models';

/** État de suivi renvoyé par suivre / favori. */
export interface EtatSuivi {
  discussionId: number;
  suivie: boolean;
  favori: boolean;
  nbAbonnes: number;
}

/** Paramètres HTTP à partir d'un objet (valeurs vides ignorées). */
export function parametres(valeurs: object): HttpParams {
  let params = new HttpParams();
  for (const [cle, valeur] of Object.entries(valeurs)) {
    if (valeur !== undefined && valeur !== null && valeur !== '') {
      params = params.set(cle, String(valeur));
    }
  }
  return params;
}

/**
 * Forum, côté membre : discussions, réponses, commentaires, votes, réactions, suivis, recherche, notifications,
 * réputation. Seule URL connue : la gateway (API_BASE_URL) ; le JWT est ajouté par l'intercepteur du socle.
 */
@Injectable({ providedIn: 'root' })
export class ForumService {
  private readonly http = inject(HttpClient);
  private readonly url = `${API_BASE_URL}/forum`;
  private coursCache?: Observable<CoursOption[]>;

  // ------------------------------------------------------------ Discussions

  lister(filtres: DiscussionsFiltres = {}): Observable<PageResponse<DiscussionResume>> {
    return this.get(`${this.url}/discussions`, parametres(filtres));
  }

  lire(id: number): Observable<DiscussionDetail> {
    return this.get(`${this.url}/discussions/${id}`);
  }

  publier(corps: DiscussionRequest): Observable<DiscussionDetail> {
    return this.post(`${this.url}/discussions`, corps);
  }

  modifier(id: number, corps: DiscussionModificationRequest): Observable<DiscussionDetail> {
    return this.put(`${this.url}/discussions/${id}`, corps);
  }

  supprimer(id: number): Observable<void> {
    return this.del(`${this.url}/discussions/${id}`);
  }

  versions(id: number): Observable<VersionDiscussion[]> {
    return this.get(`${this.url}/discussions/${id}/versions`);
  }

  accepter(id: number, reponseId: number): Observable<DiscussionDetail> {
    return this.post(`${this.url}/discussions/${id}/accepter/${reponseId}`, {});
  }

  retirerAcceptation(id: number): Observable<DiscussionDetail> {
    return this.del(`${this.url}/discussions/${id}/accepter`);
  }

  // ------------------------------------------------------------ Recherche

  similaires(corps: SimilairesRequest): Observable<SimilairesResponse> {
    return this.post(`${this.url}/discussions/similaires`, corps);
  }

  liees(id: number): Observable<SimilairesResponse> {
    return this.get(`${this.url}/discussions/${id}/liees`);
  }

  pourMoi(): Observable<PourToi> {
    return this.get(`${this.url}/pour-moi`);
  }

  etiquettes(coursId: number | null, q: string): Observable<Etiquette[]> {
    return this.get(`${this.url}/etiquettes`, parametres({ coursId, q }));
  }

  verifier(titre: string, contenu: string): Observable<Verification> {
    return this.post(`${this.url}/composer/verifier`, { titre, contenu });
  }

  // ------------------------------------------------------------ Réponses, commentaires, votes, réactions

  repondre(discussionId: number, corps: ReponseRequest): Observable<Reponse> {
    return this.post(`${this.url}/discussions/${discussionId}/reponses`, corps);
  }

  modifierReponse(id: number, contenu: string): Observable<Reponse> {
    return this.put(`${this.url}/reponses/${id}`, { contenu, anonyme: false });
  }

  supprimerReponse(id: number): Observable<void> {
    return this.del(`${this.url}/reponses/${id}`);
  }

  approuver(id: number, oui: boolean): Observable<Reponse> {
    return oui ? this.post(`${this.url}/reponses/${id}/approbation`, {}) : this.del(`${this.url}/reponses/${id}/approbation`);
  }

  commenter(cible: { discussionId?: number; reponseId?: number }, corps: CommentaireRequest): Observable<unknown> {
    const chemin = cible.reponseId ? `reponses/${cible.reponseId}` : `discussions/${cible.discussionId}`;
    return this.post(`${this.url}/${chemin}/commentaires`, corps);
  }

  voter(corps: VoteRequest): Observable<VoteResponse> {
    return this.put(`${this.url}/votes`, corps);
  }

  reagir(reponseId: number, type: TypeReaction, oui: boolean): Observable<ReactionsResponse> {
    const chemin = `${this.url}/reponses/${reponseId}/reactions/${type}`;
    return oui ? this.put(chemin, {}) : this.del(chemin);
  }

  signaler(corps: SignalementRequest): Observable<Signalement> {
    return this.post(`${this.url}/signalements`, corps);
  }

  // ------------------------------------------------------------ Suivi, favoris, lu

  suivre(id: number, oui: boolean): Observable<EtatSuivi> {
    return oui ? this.post(`${this.url}/discussions/${id}/suivre`, {}) : this.del(`${this.url}/discussions/${id}/suivre`);
  }

  favori(id: number, oui: boolean): Observable<EtatSuivi> {
    return oui ? this.post(`${this.url}/discussions/${id}/favori`, {}) : this.del(`${this.url}/discussions/${id}/favori`);
  }

  toutMarquerLu(coursId: number | null): Observable<number> {
    return this.http
      .post<ApiResponse<number>>(`${this.url}/discussions/lu`, {}, { params: parametres({ coursId }) })
      .pipe(map((r) => r.data));
  }

  reponseTrouvee(id: number): Observable<void> {
    return this.post(`${this.url}/discussions/${id}/reponse-trouvee`, {});
  }

  // ------------------------------------------------------------ Notifications, profil, réputation

  notifications(nonLues: boolean, page = 0): Observable<PageResponse<NotificationForum>> {
    return this.get(`${this.url}/notifications`, parametres({ nonLues, page }));
  }

  compteur(): Observable<CompteurNotifications> {
    return this.get(`${this.url}/notifications/compteur`);
  }

  marquerLues(ids?: number[]): Observable<CompteurNotifications> {
    return this.post(`${this.url}/notifications/lues`, ids ?? null);
  }

  profil(userId: number): Observable<ProfilForum> {
    return this.get(`${this.url}/profils/${userId}`);
  }

  activite(): Observable<Activite> {
    return this.get(`${this.url}/moi/activite`);
  }

  reputation(): Observable<Reputation> {
    return this.get(`${this.url}/moi/reputation`);
  }

  classement(coursId: number, periode: 'semaine' | 'mois' = 'semaine'): Observable<ClassementEntree[]> {
    return this.get(`${this.url}/classement`, parametres({ coursId, periode }));
  }

  // ------------------------------------------------------------ Cours (contrat 1, lecture seule)

  /** Cours du catalogue, mis en cache pour la session (sélecteurs du forum). */
  cours(): Observable<CoursOption[]> {
    this.coursCache ??= this.http.get<ApiResponse<CoursOption[]>>(`${API_BASE_URL}/cours`).pipe(
      map((r) => (r.data ?? []).map((c) => ({ id: c.id, titre: c.titre, enseignantId: c.enseignantId }))),
      shareReplay(1),
    );
    return this.coursCache;
  }

  // ------------------------------------------------------------ Aides HTTP

  private get<T>(url: string, params?: HttpParams): Observable<T> {
    return this.http.get<ApiResponse<T>>(url, { params }).pipe(map((r) => r.data));
  }

  private post<T>(url: string, corps: unknown): Observable<T> {
    return this.http.post<ApiResponse<T>>(url, corps).pipe(map((r) => r.data));
  }

  private put<T>(url: string, corps: unknown): Observable<T> {
    return this.http.put<ApiResponse<T>>(url, corps).pipe(map((r) => r.data));
  }

  private del<T>(url: string): Observable<T> {
    return this.http.delete<ApiResponse<T>>(url).pipe(map((r) => r.data));
  }
}
