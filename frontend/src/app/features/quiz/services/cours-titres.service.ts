import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { map } from 'rxjs';

import { API_BASE_URL } from '../../../core/config/api.config';
import { ApiResponse } from '../../../shared/models/api-response.model';

/** Ce dont le module Quiz a besoin d'un cours (contrat 1 / GET /api/cours). */
export interface CoursResume {
  id: number;
  titre: string;
  categorie: string | null;
  niveau: string | null;
  enseignantId: number;
}

/** Catalogue des cours, chargé une fois puis partagé par les pages du module Quiz. */
@Injectable({ providedIn: 'root' })
export class CoursTitresService {
  private readonly http = inject(HttpClient);
  private readonly liste = signal<CoursResume[]>([]);
  private charge = false;

  readonly cours = this.liste.asReadonly();
  private readonly parId = computed(() => new Map(this.liste().map((c) => [c.id, c])));

  /** Lance le chargement au premier appel ; les appels suivants ne refont pas la requête. */
  charger(): void {
    if (this.charge) return;
    this.charge = true;
    this.http
      .get<ApiResponse<CoursResume[]>>(`${API_BASE_URL}/cours`)
      .pipe(map((r) => r.data ?? []))
      .subscribe({ next: (c) => this.liste.set(c), error: () => (this.charge = false) });
  }

  get(id: number): CoursResume | undefined {
    return this.parId().get(id);
  }

  titre(id: number): string {
    return this.parId().get(id)?.titre ?? `Cours n° ${id}`;
  }
}
