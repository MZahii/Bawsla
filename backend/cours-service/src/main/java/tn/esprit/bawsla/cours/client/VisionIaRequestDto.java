package tn.esprit.bawsla.cours.client;

/**
 * Corps de POST /api/ai/cours/vision : une page rendue en image (PNG ou JPEG) en base64.
 * {@code forcerLocal} : ne pas essayer Gemini (le lot a déjà basculé sur le modèle local : on ne perd pas son délai à chaque page).
 */
public record VisionIaRequestDto(Long coursId, int numero, String imageBase64, String mime, boolean forcerLocal) {

    public VisionIaRequestDto(Long coursId, int numero, String imageBase64, String mime) {
        this(coursId, numero, imageBase64, mime, false);
    }
}
