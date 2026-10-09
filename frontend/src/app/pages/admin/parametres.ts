import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatRadioModule } from '@angular/material/radio';
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTabsModule } from '@angular/material/tabs';

import { BwButton } from '../../shared';
import { PageHeader } from '../../shared/ui';

/**
 * Back-office : paramètres de la plateforme (général, inscriptions et formateurs, certificats,
 * paiements, IA, notifications, sécurité). Plateforme ouverte : pas d'établissement ni de domaine email imposé.
 * À brancher : endpoints de configuration (à décrire dans CONTRATS_API.md). Aucune clé API ici :
 * les secrets restent dans .env côté serveur.
 */
@Component({
  selector: 'app-parametres',
  imports: [FormsModule, MatTabsModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatSlideToggleModule, MatRadioModule, MatIconModule, BwButton, PageHeader],
  template: `
    <bw-page-header title="Paramètres" subtitle="Configuration de la plateforme." [crumbs]="[{ label: 'Admin', link: '/admin' }, { label: 'Paramètres' }]">
      <button type="button" bwButton="principal" (click)="enregistrer()">Enregistrer</button>
    </bw-page-header>
    <section class="card">
      <mat-tab-group animationDuration="0ms" mat-stretch-tabs="false">
        <mat-tab label="Général">
          <div class="form">
            <mat-form-field appearance="outline"><mat-label>Nom de la plateforme</mat-label><input matInput value="Bawsla" /></mat-form-field>
            <mat-form-field appearance="outline"><mat-label>Slogan</mat-label><input matInput value="La boussole du savoir" /></mat-form-field>
            <mat-form-field appearance="outline"><mat-label>Email de contact</mat-label><input matInput value="contact@bawsla.tn" /></mat-form-field>
            <mat-form-field appearance="outline"><mat-label>Devise</mat-label>
              <mat-select value="TND"><mat-option value="TND">Dinar tunisien (DT)</mat-option><mat-option value="EUR">Euro (€)</mat-option></mat-select>
            </mat-form-field>
            <mat-form-field appearance="outline"><mat-label>Langue par défaut</mat-label>
              <mat-select value="fr"><mat-option value="fr">Français</mat-option><mat-option value="ar">العربية</mat-option><mat-option value="en">English</mat-option></mat-select>
            </mat-form-field>
          </div>
        </mat-tab>
        <mat-tab label="Inscriptions et formateurs">
          <div class="form">
            <mat-slide-toggle [checked]="true">Inscriptions ouvertes à tous</mat-slide-toggle>
            <mat-slide-toggle [checked]="true">Proposer le questionnaire d’accueil aux nouveaux apprenants</mat-slide-toggle>
            <mat-slide-toggle [checked]="true">Exiger la confirmation de l’adresse email</mat-slide-toggle>
            <p class="lbl">Les demandes pour devenir formateur sont</p>
            <mat-radio-group value="valides" class="radios">
              <mat-radio-button value="valides">examinées par un administrateur</mat-radio-button>
              <mat-radio-button value="auto">acceptées automatiquement</mat-radio-button>
            </mat-radio-group>
            <mat-slide-toggle [checked]="true">Relire chaque nouveau cours avant sa première publication</mat-slide-toggle>
          </div>
        </mat-tab>
        <mat-tab label="Certificats">
          <div class="form">
            <mat-form-field appearance="outline"><mat-label>Seuil de réussite par défaut (%)</mat-label><input matInput type="number" value="70" /><mat-hint>Le formateur peut le changer pour son cours (entre 50 et 100 %)</mat-hint></mat-form-field>
            <mat-form-field appearance="outline"><mat-label>Tentatives par défaut à l’examen final</mat-label><input matInput type="number" value="3" /></mat-form-field>
            <mat-form-field appearance="outline"><mat-label>Préfixe des numéros</mat-label><input matInput value="BWS-2026-" /></mat-form-field>
            <mat-form-field appearance="outline"><mat-label>Signataire</mat-label><input matInput value="L’équipe Bawsla" /></mat-form-field>
            <mat-slide-toggle [checked]="true">Page publique de vérification (/verifier)</mat-slide-toggle>
            <mat-slide-toggle [checked]="true">Bouton « Ajouter sur LinkedIn »</mat-slide-toggle>
          </div>
        </mat-tab>
        <mat-tab label="Paiements">
          <div class="form">
            <mat-slide-toggle [checked]="true">Autoriser les cours payants</mat-slide-toggle>
            <mat-form-field appearance="outline"><mat-label>Commission de la plateforme (%)</mat-label><input matInput type="number" value="20" /></mat-form-field>
            <mat-form-field appearance="outline"><mat-label>Délai de remboursement (jours)</mat-label><input matInput type="number" value="14" /><mat-hint>Si l’apprenant a suivi moins de 20 % du cours</mat-hint></mat-form-field>
            <mat-form-field appearance="outline"><mat-label>Prestataire de paiement</mat-label>
              <mat-select value="simule"><mat-option value="simule">Simulé (démonstration)</mat-option><mat-option value="konnect">Konnect</mat-option><mat-option value="stripe">Stripe</mat-option></mat-select>
            </mat-form-field>
            <p class="muted small">Les clés du prestataire se configurent dans le fichier .env du serveur, jamais ici.</p>
          </div>
        </mat-tab>
        <mat-tab label="Intelligence artificielle">
          <div class="form">
            <mat-form-field appearance="outline"><mat-label>Quota mensuel (requêtes)</mat-label><input matInput type="number" value="10000" /></mat-form-field>
            <mat-form-field appearance="outline"><mat-label>Limite par utilisateur et par heure</mat-label><input matInput type="number" value="30" /></mat-form-field>
            <mat-slide-toggle [checked]="true">Masquer emails et téléphones avant tout envoi au modèle</mat-slide-toggle>
            <mat-slide-toggle [checked]="true">Exiger la validation du formateur pour les résumés et les quiz</mat-slide-toggle>
            <p class="muted small">La clé du fournisseur se configure dans le fichier .env du serveur, jamais ici.</p>
          </div>
        </mat-tab>
        <mat-tab label="Notifications">
          <div class="form">
            <mat-slide-toggle [checked]="true">Email de bienvenue</mat-slide-toggle>
            <mat-slide-toggle [checked]="true">Rappel de l’objectif de la semaine</mat-slide-toggle>
            <mat-slide-toggle [checked]="true">Email quand un examen final se débloque</mat-slide-toggle>
            <mat-slide-toggle [checked]="false">Résumé hebdomadaire aux formateurs (inscriptions, avis)</mat-slide-toggle>
            <mat-slide-toggle [checked]="true">Alerter le formateur quand un apprenant risque d’abandonner</mat-slide-toggle>
          </div>
        </mat-tab>
        <mat-tab label="Sécurité">
          <div class="form">
            <mat-form-field appearance="outline"><mat-label>Durée de session (heures)</mat-label><input matInput type="number" value="24" /></mat-form-field>
            <mat-form-field appearance="outline"><mat-label>Longueur minimale du mot de passe</mat-label><input matInput type="number" value="8" /></mat-form-field>
            <mat-slide-toggle [checked]="false">Double authentification pour les administrateurs</mat-slide-toggle>
          </div>
        </mat-tab>
      </mat-tab-group>
    </section>
  `,
  styles: `
    .form { display: flex; flex-direction: column; gap: var(--space-3); max-width: 560px; padding: var(--space-5); }
    .lbl { margin: var(--space-2) 0 0; font-weight: 600; }
    .radios { display: flex; flex-direction: column; }
  `,
})
export class Parametres {
  private readonly snack = inject(MatSnackBar);

  protected enregistrer(): void {
    this.snack.open('Paramètres enregistrés', 'OK', { duration: 2500 });
  }
}
