# Journal des prompts

Le module « IA for Software Engineering » évalue **comment** l'IA a été utilisée pour développer le projet. Chaque membre tient le journal de **son** module :

```
prompts_journal/
├── user/   JOURNAL.md  captures/
├── cours/  JOURNAL.md  captures/
├── quiz/   JOURNAL.md  captures/
└── forum/  JOURNAL.md  captures/
```

## Règles

- Une entrée par prompt significatif (génération de code, débogage, revue, conception). Les petites questions de syntaxe ne comptent pas.
- Toujours noter l'**outil et le modèle**, le **prompt exact** (copié-collé), le **résultat** et **ce que tu as gardé, corrigé ou rejeté**, avec la raison.
- Captures dans `captures/`, nommées `AAAA-MM-JJ_NN_sujet.png` (ex. `2026-10-12_01_crud-cours.png`) et référencées depuis l'entrée.
- Ne jamais coller de secret (clé API, `.env`, token JWT) ni de donnée personnelle réelle dans un prompt ou une capture.
- On commite le journal **avec** le code qu'il a produit, dans la même PR.

## Modèle d'entrée

Copier ce bloc en haut de `JOURNAL.md` (les entrées les plus récentes en premier) :

```markdown
### NN — AAAA-MM-JJ — <sujet court>

- **Outil / modèle** : Claude Code (Claude Opus 5.5)
- **Objectif** : ce que je voulais obtenir
- **Contexte fourni** : fichiers, CLAUDE.md, CONTRATS_API.md…
- **Prompt** :
  > texte exact du prompt
- **Résultat** : ce que l'IA a produit (fichiers, résumé)
- **Vérification** : comment j'ai testé (build, curl, test unitaire…)
- **Gardé / modifié / rejeté** : et pourquoi
- **Captures** : `captures/AAAA-MM-JJ_NN_sujet.png`
- **Leçon** : ce que je referais autrement
```
