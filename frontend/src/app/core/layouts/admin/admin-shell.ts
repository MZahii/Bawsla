import { Component } from '@angular/core';

import { NavGroup, SideShell } from '../side/side-shell';

/** Back-office administrateur : menu latéral sombre et compact, pages denses. */
@Component({
  selector: 'app-admin-shell',
  imports: [SideShell],
  template: `<app-side-shell variant="dark" espace="Administration" home="/admin" [groups]="groups" searchPlaceholder="Rechercher un utilisateur, un cours, un certificat…" />`,
})
export class AdminShell {
  protected readonly groups: NavGroup[] = [
    { titre: 'Vue d’ensemble', items: [{ label: 'Tableau de bord', link: '/admin', icone: 'monitoring', exact: true }] },
    { titre: 'Gestion', items: [
      { label: 'Utilisateurs', link: '/admin/utilisateurs', icone: 'manage_accounts' },
      { label: 'Formateurs', link: '/admin/formateurs', icone: 'how_to_reg', compteur: 3 },
      { label: 'Catalogue', link: '/admin/catalogue', icone: 'category' },
      { label: 'Certificats', link: '/admin/certificats', icone: 'workspace_premium' },
      { label: 'Ventes', link: '/admin/ventes', icone: 'payments' },
    ] },
    { titre: 'Intelligence artificielle', items: [{ label: 'Supervision IA', link: '/admin/ia', icone: 'psychology', compteur: 4 }] },
    { titre: 'Système', items: [
      { label: 'Journal d’audit', link: '/admin/audit', icone: 'history' },
      { label: 'Paramètres', link: '/admin/parametres', icone: 'tune' },
      { label: 'Composants', link: '/admin/composants', icone: 'widgets' },
    ] },
  ];
}
