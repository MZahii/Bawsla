package tn.esprit.bawsla.cours.client;

/** Corps de POST /api/ai/cours/audio. {@code langue} : "fr" ou "en". */
public record AudioIaRequestDto(String texte, String langue) {

    public AudioIaRequestDto(String texte) {
        this(texte, "fr");
    }
}
