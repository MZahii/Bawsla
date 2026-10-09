import { Component } from '@angular/core';

import { NavGroup, QuickAction, SideShell } from '../side/side-shell';

/** Studio formateur : menu latéral clair, actions « Créer ». */
@Component({
  selector: 'app-teacher-shell',
  imports: [SideShell],
  template: `<app-side-shell variant="light" espace="Studio formateur" home="/studio" [groups]="groups" [actions]="actions" searchPlaceholder="Rechercher un cours, un apprenant…" />`,
})
export class TeacherShell {
  protected readonly groups: NavGroup[] = [
    { titre: 'Pilotage', items: [
      { label: 'Tableau de bord', link: '/studio', icone: 'space_dashboard', exact: true },
      { label: 'Calendrier', link: '/studio/calendrier', icone: 'calendar_month' },
    ] },
    { titre: 'Contenu', items: [
      { label: 'Mes cours', link: '/studio/cours', icone: 'video_library' },
      { label: 'Quiz', link: '/studio/quiz', icone: 'quiz', exact: true },
      { label: 'Générer un quiz', link: '/studio/quiz/generer', icone: 'auto_awesome' },
    ] },
    { titre: 'Apprenants', items: [
      { label: 'Apprenants', link: '/studio/apprenants', icone: 'groups', compteur: 3 },
      { label: 'Avis', link: '/studio/avis', icone: 'reviews', compteur: 2 },
      { label: 'Forum', link: '/studio/forum', icone: 'forum', compteur: 2 },
    ] },
    { titre: 'Compte', items: [{ label: 'Profil', link: '/studio/profil', icone: 'person' }] },
  ];
  protected readonly actions: QuickAction[] = [
    { label: 'Nouveau cours', link: '/studio/cours/nouveau', icone: 'post_add' },
    { label: 'Générer un quiz', link: '/studio/quiz/generer', icone: 'auto_awesome' },
  ];
}
