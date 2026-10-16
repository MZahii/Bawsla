import { Pipe, PipeTransform, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { CoursImageService } from '../services/cours-image.service';

/** <img [src]="cours | coursImage | async" alt="" /> : image du cours (avec le JWT), ou image par défaut. */
@Pipe({ name: 'coursImage' })
export class CoursImage implements PipeTransform {
  private readonly images = inject(CoursImageService);

  transform(cours: { imageUrl?: string | null } | null | undefined): Observable<string> {
    return this.images.url(cours);
  }
}
