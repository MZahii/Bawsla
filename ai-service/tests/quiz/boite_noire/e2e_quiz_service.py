"""Test de bout en bout, boîte noire, du module Quiz à travers la gateway (stack Docker démarrée).

Aucun accès au code ni à la base : uniquement les routes publiques /api/** et les comptes de démonstration.
Parcours : formateur (cours, quiz, examen, publication) -> apprenant (passage, tentative, remédiation IA,
rattrapage, examen, certificat) -> vérification publique sans compte -> contrôles d'accès.

Lancer :  ai-service/.venv/Scripts/python ai-service/tests/quiz/boite_noire/e2e_quiz_service.py
Le résultat est écrit dans resultats/e2e_quiz_service.json (aucun jeton ni mot de passe n'y figure).
"""
import json
import sys
import time
from pathlib import Path

import httpx

BASE = "http://localhost:8080/api"
FORMATEUR = {"email": "formateur.demo@bawsla.tn", "motDePasse": "Formateur123!"}
APPRENANT = {"email": "apprenant.demo@bawsla.tn", "motDePasse": "Apprenant123!"}
SORTIE = Path(__file__).parent / "resultats" / "e2e_quiz_service.json"

client = httpx.Client(timeout=90, trust_env=False)
etapes: list[dict] = []


def verifier(nom: str, condition: bool, detail: str = "") -> None:
    etapes.append({"etape": nom, "ok": bool(condition), "detail": detail})
    print(("OK   " if condition else "ECHEC") + f" {nom}" + (f" ({detail})" if detail else ""))


def connexion(compte: dict) -> tuple[dict, int]:
    data = client.post(f"{BASE}/auth/login", json=compte).json()["data"]
    return {"Authorization": f"Bearer {data['token']}"}, data["user"]["id"]


def question(n: int, competence: str, bloom: str) -> dict:
    return {
        "enonce": f"Question {n} : quelle est la complexité d'une recherche dichotomique ?",
        "niveauBloom": bloom,
        "competence": competence,
        "explication": "On divise l'intervalle par deux à chaque étape.",
        "choix": [
            {"texte": "O(log n)", "estCorrecte": True},
            {"texte": "O(n)", "estCorrecte": False, "erreurAssociee": "Confond avec la recherche séquentielle"},
            {"texte": "O(n²)", "estCorrecte": False, "erreurAssociee": "Confond avec un tri naïf"},
            {"texte": "O(1)", "estCorrecte": False, "erreurAssociee": "Confond avec l'accès indexé"},
        ],
    }


def main() -> int:
    hf, _ = connexion(FORMATEUR)
    ha, id_apprenant = connexion(APPRENANT)
    suffixe = time.strftime("%H%M%S")

    # 1. Formateur : un cours (cours-service) publié avec un chapitre
    r = client.post(f"{BASE}/cours", headers=hf, json={
        "titre": f"Algorithmique : recherche et tri ({suffixe})", "description": "Cours de test E2E du module Quiz",
        "categorie": "Informatique", "niveau": "DEBUTANT", "prix": 0})
    verifier("cours créé (cours-service)", r.status_code == 201, str(r.status_code))
    cours_id = r.json()["data"]["id"]
    client.post(f"{BASE}/cours/{cours_id}/chapitres", headers=hf,
                json={"titre": "Recherche dichotomique", "contenu": "Diviser pour régner.", "dureeMinutes": 20})
    r = client.patch(f"{BASE}/cours/{cours_id}/statut", headers=hf, json={"statut": "PUBLIE"})
    verifier("cours publié", r.status_code == 200, str(r.status_code))

    # 2. Quiz de chapitre et examen final
    quiz = {"titre": "Quiz : recherche dichotomique", "coursId": cours_id, "type": "QUIZ", "difficulte": "FACILE",
            "seuilReussite": 60, "tentativesMax": 0, "dureeMinutes": 10,
            "questions": [question(1, "Complexité logarithmique", "COMPRENDRE"),
                          question(2, "Recherche dichotomique", "APPLIQUER")]}
    r = client.post(f"{BASE}/quiz", headers=hf, json=quiz)
    verifier("quiz créé", r.status_code in (200, 201), str(r.status_code))
    quiz_id = r.json()["data"]["id"]
    r = client.post(f"{BASE}/quiz", headers=ha, json=quiz)
    verifier("apprenant ne peut pas créer de quiz", r.status_code == 403, str(r.status_code))
    r = client.post(f"{BASE}/quiz", headers=hf, json={**quiz, "coursId": 999999})
    verifier("cours inexistant refusé (Feign)", r.status_code == 400, r.json().get("message", ""))
    r = client.post(f"{BASE}/quiz/{quiz_id}/publier", headers=hf)
    verifier("quiz publié", r.json()["data"]["statut"] == "PUBLIE")

    examen = {**quiz, "titre": "Examen final", "type": "EXAMEN", "seuilReussite": 50, "tentativesMax": 2,
              "dureeMinutes": 30, "questions": [question(i, "Complexité logarithmique", "ANALYSER") for i in (1, 2)]}
    examen_id = client.post(f"{BASE}/quiz", headers=hf, json=examen).json()["data"]["id"]
    r = client.post(f"{BASE}/quiz/{examen_id}/publier", headers=hf)
    verifier("examen publié", r.json()["data"]["statut"] == "PUBLIE")

    # 3. Apprenant : passage (sans les bonnes réponses), tentative avec une erreur
    r = client.get(f"{BASE}/quiz/{quiz_id}/passage", headers=ha)
    passage = r.json()["data"]
    texte = json.dumps(passage)
    verifier("passage sans bonnes réponses ni erreurs associées",
             "estCorrecte" not in texte and "erreurAssociee" not in texte)
    q1, q2 = passage["questions"]
    faux = next(o for o in q2["choix"] if o["texte"] == "O(n)")
    juste = next(o for o in q1["choix"] if o["texte"] == "O(log n)")
    r = client.post(f"{BASE}/quiz/{quiz_id}/tentatives", headers=ha, json={"reponses": [
        {"questionId": q1["id"], "optionId": juste["id"]}, {"questionId": q2["id"], "optionId": faux["id"]}]})
    res = r.json()["data"]
    verifier("tentative corrigée : 50 %", res["pourcentage"] == 50 and not res["reussie"], f"{res['pourcentage']} %")
    erreur = next(c for c in res["corrections"] if not c["correcte"])
    verifier("diagnostic du distracteur", erreur["diagnostic"] == "Confond avec la recherche séquentielle")

    # 4. Remédiation IA (vrai modèle via ai-service) puis rattrapage
    t0 = time.time()
    r = client.post(f"{BASE}/quiz/reponses/{erreur['reponseId']}/remediation", headers=ha)
    rem = r.json().get("data") or {}
    verifier("remédiation IA", r.status_code == 200 and bool(rem.get("explication")),
             f"{r.status_code}, fournisseur={rem.get('fournisseur')}, {time.time() - t0:.1f} s")
    if rem.get("questionRattrapage"):
        r = client.post(f"{BASE}/quiz/remediations/{rem['notionRateeId']}/rattrapage", headers=ha,
                        json={"indexChoix": 0})
        verifier("rattrapage évalué", r.status_code == 200, f"correcte={r.json()['data'].get('correcte')}")
    r = client.post(f"{BASE}/quiz/reponses/{erreur['reponseId']}/remediation", headers=ha)
    verifier("remédiation mémorisée (pas de 2e appel IA)", r.json()["data"]["notionRateeId"] == rem.get("notionRateeId"))

    # 5. Notions ratées (contrat 3) : soi-même oui, un autre apprenant non
    r = client.get(f"{BASE}/quiz/notions-ratees", headers=ha, params={"etudiantId": id_apprenant})
    notions = r.json()["data"]
    verifier("notions-ratees (contrat 3)", any(n["notion"] == "Recherche dichotomique" for n in notions),
             json.dumps(notions, ensure_ascii=False)[:120])
    r = client.get(f"{BASE}/quiz/notions-ratees", headers=ha, params={"etudiantId": id_apprenant + 1000})
    verifier("notions-ratees d'un autre apprenant interdites", r.status_code == 403, str(r.status_code))

    # 6. Examen final réussi -> certificat
    pe = client.get(f"{BASE}/quiz/{examen_id}/passage", headers=ha).json()["data"]
    reponses = [{"questionId": q["id"], "optionId": next(o["id"] for o in q["choix"] if o["texte"] == "O(log n)")}
                for q in pe["questions"]]
    res = client.post(f"{BASE}/quiz/{examen_id}/tentatives", headers=ha, json={"reponses": reponses}).json()["data"]
    numero = res.get("certificatNumero")
    verifier("examen réussi, certificat délivré", res["reussie"] and bool(numero), f"{res['pourcentage']} %, {numero}")
    mes = client.get(f"{BASE}/quiz/certificats/mes", headers=ha).json()["data"]
    verifier("certificat dans « mes certificats »", any(c["numero"] == numero for c in mes))

    # 7. Vérification publique sans compte ; le reste reste protégé
    r = client.get(f"{BASE}/quiz/certificats/verification/{numero}")
    cert = r.json().get("data") or {}
    verifier("vérification publique sans jeton", r.status_code == 200 and cert.get("numero") == numero,
             str(r.status_code))
    verifier("aucun identifiant d'apprenant exposé", "etudiantId" not in cert)
    verifier("titre du cours copié dans le certificat", (cert.get("titreCours") or "").startswith("Algorithmique"),
             str(cert.get("titreCours")))
    r = client.get(f"{BASE}/quiz/certificats/verification/BW-INCONNU-0000")
    verifier("numéro inconnu -> 404", r.status_code == 404, str(r.status_code))
    r = client.get(f"{BASE}/quiz/certificats/mes")
    verifier("/certificats/mes sans jeton -> 401", r.status_code == 401, str(r.status_code))
    r = client.get(f"{BASE}/quiz/certificats", headers=ha)
    verifier("liste admin des certificats interdite à l'apprenant", r.status_code == 403, str(r.status_code))
    r = client.get(f"{BASE}/quiz/certificats/verification/{numero}",
                   headers={"X-User-Id": "1", "X-User-Role": "ADMIN"})
    verifier("en-têtes X-User-* forgés ignorés", r.status_code == 200)

    # 8. Formateur : statistiques et verrouillage des questions
    st = client.get(f"{BASE}/quiz/{quiz_id}/statistiques", headers=hf).json()["data"]
    verifier("statistiques du quiz", st.get("nbTentatives", 0) >= 1, json.dumps(st, ensure_ascii=False)[:100])
    r = client.put(f"{BASE}/quiz/{quiz_id}", headers=hf, json=quiz)
    verifier("questions verrouillées après une tentative (409)", r.status_code == 409, str(r.status_code))
    r = client.delete(f"{BASE}/quiz/{quiz_id}", headers=hf)
    verifier("suppression refusée après une tentative (409)", r.status_code == 409, str(r.status_code))

    nb_ok = sum(e["ok"] for e in etapes)
    SORTIE.parent.mkdir(exist_ok=True)
    SORTIE.write_text(json.dumps({"date": time.strftime("%Y-%m-%d %H:%M"), "reussies": nb_ok, "total": len(etapes),
                                  "coursId": cours_id, "quizId": quiz_id, "examenId": examen_id,
                                  "etapes": etapes}, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"\n{nb_ok}/{len(etapes)} étapes réussies")
    return 0 if nb_ok == len(etapes) else 1


if __name__ == "__main__":
    sys.exit(main())
