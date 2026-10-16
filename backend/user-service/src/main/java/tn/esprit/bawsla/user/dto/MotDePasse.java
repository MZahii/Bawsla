package tn.esprit.bawsla.user.dto;

/** Règles de mot de passe partagées par tous les DTO (inscription, création et réinitialisation par l'admin). */
public final class MotDePasse {

    public static final int MIN = 8;
    /** BCrypt ignore tout ce qui dépasse 72 octets. */
    public static final int MAX = 72;
    public static final String TAILLE_MESSAGE = "Entre 8 et 72 caractères";
    public static final String REGEX = "^(?=.*\\p{L})(?=.*\\d).*$";
    public static final String REGEX_MESSAGE = "Au moins une lettre et un chiffre";

    private MotDePasse() {
    }
}
