import { Injectable } from '@angular/core';
import { MatPaginatorIntl } from '@angular/material/paginator';

/** Libellés français de la pagination des tableaux (mat-paginator). */
@Injectable()
export class PaginatorFr extends MatPaginatorIntl {
  override itemsPerPageLabel = 'Lignes par page';
  override nextPageLabel = 'Page suivante';
  override previousPageLabel = 'Page précédente';
  override firstPageLabel = 'Première page';
  override lastPageLabel = 'Dernière page';
  override getRangeLabel = (page: number, size: number, length: number): string => {
    if (length === 0) return '0 sur 0';
    const debut = page * size;
    return `${debut + 1} à ${Math.min(debut + size, length)} sur ${length}`;
  };
}
