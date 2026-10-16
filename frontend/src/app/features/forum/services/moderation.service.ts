import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

import { API_BASE_URL } from '../../../core/config/api.config';
import { ApiResponse } from '../../../shared/models/api-response.model';
import { User } from '../../../shared/models/user.model';
import {
  ActionModeration,
  AdminStatistiques,
  ApprenantBloque,
  CategorieModeration,
  CompteurNotifications,
  DecisionRequest,
  DiscussionDetail,
  HistoriqueMembre,
  JournalModeration,
  PageResponse,
  ParametresCours,
  ParametresForum,
  Sanction,
  SanctionRequest,
  Signalement,
  Statistiques,
  Supervision,
  Tendances,
} from '../models';
import { parametres } from './forum.service';

/** Filtres du journal de modération (admin). */
export interface FiltresJournal {
  acteur?: number;
  action?: ActionModeration;
  du?: string;
  au?: string;
  page?: number;
}

export type OutilFil = 'epingler' | 'verrouiller' | 'masquer' | 'retablir';

/**
 * Modération et pilotage : formateur du cours (file, décisions, réglages, tendances, outils d'un fil) et
 * administrateur (file globale, sanctions, réglages, journal, statistiques, supervision de La Vigie).
 * Les droits sont vérifiés par forum-service ; l'interface ne fait que masquer ce qui ne sert pas.
 */
@Injectable({ providedIn: 'root' })
export class ModerationForumService {
  private readonly http = inject(HttpClient);
  private readonly url = `${API_BASE_URL}/forum`;

  // ------------------------------------------------------------ Formateur du cours

  file(coursId: number, statut = 'OUVERT'): Observable<Signalement[]> {
    return this.get(`${this.url}/moderation/file`, { coursId, statut });
  }

  decider(id: number, corps: DecisionRequest): Observable<Signalement> {
    return this.post(`${this.url}/signalements/${id}/decision`, corps);
  }

  statistiques(coursId: number, periode = 30): Observable<Statistiques> {
    return this.get(`${this.url}/statistiques`, { coursId, periode });
  }

  tendances(coursId: number): Observable<Tendances> {
    return this.get(`${this.url}/tendances`, { coursId });
  }

  apprenantsBloques(coursId: number): Observable<ApprenantBloque[]> {
    return this.get(`${this.url}/cours/${coursId}/apprenants-bloques`);
  }

  ajouterFaq(coursId: number, discussionId: number): Observable<DiscussionDetail> {
    return this.post(`${this.url}/cours/${coursId}/faq`, { discussionId });
  }

  parametresCours(coursId: number): Observable<ParametresCours> {
    return this.get(`${this.url}/cours/${coursId}/parametres`);
  }

  modifierParametresCours(coursId: number, corps: ParametresCours): Observable<ParametresCours> {
    return this.http
      .put<ApiResponse<ParametresCours>>(`${this.url}/cours/${coursId}/parametres`, corps)
      .pipe(map((r) => r.data));
  }

  /** Outils d'un fil : epingler et verrouiller (POST active, DELETE désactive) ; masquer et retablir exigent un motif. */
  outilFil(id: number, outil: OutilFil, actif = true, motif?: string): Observable<DiscussionDetail> {
    const chemin = `${this.url}/discussions/${id}/${outil}`;
    if (!actif && (outil === 'epingler' || outil === 'verrouiller')) {
      return this.http.delete<ApiResponse<DiscussionDetail>>(chemin).pipe(map((r) => r.data));
    }
    return this.post(chemin, motif ? { motif } : {});
  }

  masquerMessage(type: 'reponses' | 'commentaires', id: number, masquer: boolean, motif: string): Observable<unknown> {
    return this.post(`${this.url}/${type}/${id}/${masquer ? 'masquer' : 'retablir'}`, { motif });
  }

  // ------------------------------------------------------------ Administrateur

  fileAdmin(statut: string, categorie: CategorieModeration | null, escalades: boolean): Observable<Signalement[]> {
    return this.get(`${this.url}/admin/moderation/file`, { statut, categorie, escalades });
  }

  compteurAdmin(): Observable<CompteurNotifications> {
    return this.get(`${this.url}/admin/moderation/compteur`);
  }

  sanctions(actives: boolean): Observable<Sanction[]> {
    return this.get(`${this.url}/admin/sanctions`, { actives });
  }

  sanctionner(corps: SanctionRequest): Observable<Sanction> {
    return this.post(`${this.url}/admin/sanctions`, corps);
  }

  lever(id: number): Observable<Sanction> {
    return this.http.delete<ApiResponse<Sanction>>(`${this.url}/admin/sanctions/${id}`).pipe(map((r) => r.data));
  }

  /** Membres de la plateforme pour la recherche par nom ou email (GET /api/users de user-service, réservé à l'ADMIN). */
  membres(): Observable<User[]> {
    return this.http.get<ApiResponse<User[]>>(`${API_BASE_URL}/users`).pipe(map((r) => r.data));
  }

  historique(userId: number): Observable<HistoriqueMembre> {
    return this.get(`${this.url}/admin/utilisateurs/${userId}/historique`);
  }

  parametresGlobaux(): Observable<ParametresForum> {
    return this.get(`${this.url}/admin/parametres`);
  }

  modifierParametresGlobaux(valeurs: Record<string, string>): Observable<ParametresForum> {
    return this.http
      .put<ApiResponse<ParametresForum>>(`${this.url}/admin/parametres`, { valeurs })
      .pipe(map((r) => r.data));
  }

  journal(filtres: FiltresJournal): Observable<PageResponse<JournalModeration>> {
    return this.get(`${this.url}/admin/journal`, filtres);
  }

  /** Export CSV : téléchargé par HttpClient (un simple lien n'enverrait pas le JWT). */
  journalCsv(filtres: FiltresJournal): Observable<Blob> {
    return this.http.get(`${this.url}/admin/journal/export`, { params: parametres(filtres), responseType: 'blob' });
  }

  statistiquesAdmin(periode = 30): Observable<AdminStatistiques> {
    return this.get(`${this.url}/admin/statistiques`, { periode });
  }

  supervision(periode = 30): Observable<Supervision> {
    return this.get(`${this.url}/admin/vigie/supervision`, { periode });
  }

  reindexerTout(): Observable<number> {
    return this.post(`${this.url}/admin/index/reindexer-tout`, {});
  }

  private get<T>(url: string, params: object = {}): Observable<T> {
    return this.http.get<ApiResponse<T>>(url, { params: parametres(params) }).pipe(map((r) => r.data));
  }

  private post<T>(url: string, corps: unknown): Observable<T> {
    return this.http.post<ApiResponse<T>>(url, corps).pipe(map((r) => r.data));
  }
}
