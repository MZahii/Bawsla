# Routes à ajouter dans CONTRATS_API.md (résumé automatique local)

À reprendre dans la PR socle qui met à jour `CONTRATS_API.md`.

## ai-service

| Route | Entrée | Sortie (`data`) |
|---|---|---|
| `POST /api/ai/cours/resume-local` | `{ "coursId": 1, "pages": [{ "numero": 1, "texte": "…" }], "longueur": "courte\|moyenne\|longue", "mode": "auto\|abstractif\|extractif" }` | `{ "langue": "fr\|en", "modele": "…", "modeUtilise": "abstractif\|extractif", "resume": "…", "phrases": [{ "texte": "…", "extraitSource": "…", "page": 3, "justifiee": true }], "scoreFidelite": 0-100, "scoreReformule": 0-100 ou absent, "alertes": ["…"], "dureeMs": 4200 }` |

Erreurs : `503` « Résumé local indisponible sur ce serveur » (interrupteur `RESUME_LOCAL_ACTIF=false`, ou mode `abstractif` sans torch/transformers) ; `400` langue non prise en charge (ni français ni anglais) ou cours sans texte.
`POST /api/ai/cours/audio` (existante) renvoie en plus l'en-tête `X-TTS-Duree-Ms` (durée réelle de l'audio).

## cours-service (propriétaire du cours ou ADMIN, sauf mention)

| Route | Rôle | Réponse |
|---|---|---|
| `POST /api/cours/{id}/resume-local/generer?longueur=&mode=` | propriétaire / ADMIN | 202, suivi `{ execution, enCours, etapes[TEXTE, RESUME, AUDIO] }` ; 409 si un résumé est déjà en cours ; 400 cours vide ou scanné |
| `GET /api/cours/{id}/resume-local/traitement` | propriétaire / ADMIN | suivi du dernier lancement |
| `GET /api/cours/{id}/resume-local` | tout utilisateur qui voit le cours | 200 ; `data` absent s'il n'y a rien. Formateur : le plus récent avec score, alertes, modèle, extraits, verdicts. Apprenant : le dernier `VALIDE` sans ces champs |
| `PATCH /api/cours/{id}/resume-local/valider` | propriétaire / ADMIN | résumé validé ; produit l'audio manquant en arrière-plan ; 404 s'il n'y a rien |
| `GET /api/cours/{id}/resume-local/audio` | même visibilité que le texte | audio (mp3 ou wav) ; 404 s'il n'y en a pas |

## Variables à ajouter dans `.env.example` (socle)

`RESUME_LOCAL_ACTIF=false`, `RESUME_LOCAL_MODELE_FR=moussaKam/barthez-orangesum-abstract`, `RESUME_LOCAL_MODELE_EN=sshleifer/distilbart-cnn-12-6`,
`RESUME_LOCAL_SEUIL=50`, `RESUME_LOCAL_SEUIL_PHRASE=55`, `RESUME_LOCAL_LIBERER_APRES_SECONDES=600`, `RESUME_LOCAL_MORCEAUX_MAX=12`.
