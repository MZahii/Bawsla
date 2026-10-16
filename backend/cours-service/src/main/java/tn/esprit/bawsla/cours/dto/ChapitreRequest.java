package tn.esprit.bawsla.cours.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Corps de POST et PUT /api/cours/{id}/chapitres. {@code videoUrl} : vide, lien YouTube (watch, embed, youtu.be)
 * ou fichier .mp4, toujours en http(s).
 */
public record ChapitreRequest(
        @NotBlank(message = "Le titre est obligatoire")
        @Size(max = 200, message = "200 caractères maximum")
        String titre,
        String contenu,
        @Min(value = 0, message = "La durée ne peut pas être négative")
        @Max(value = 1440, message = "1440 minutes maximum")
        Integer dureeMinutes,
        @Size(max = 500, message = "500 caractères maximum")
        @Pattern(regexp = VIDEO_URL, flags = Pattern.Flag.CASE_INSENSITIVE,
                message = "Lien vidéo invalide : YouTube ou fichier .mp4 en http(s)")
        String videoUrl) {

    static final String VIDEO_URL =
            "^$|^https?://(www\\.|m\\.)?(youtube\\.com/(watch\\?\\S*v=|embed/)|youtu\\.be/)[\\w-]{6,}\\S*$"
                    + "|^https?://[^\\s?#]+\\.mp4(\\?\\S*)?(#\\S*)?$";
}
