package tn.esprit.bawsla.user.common.security;

import java.util.Arrays;

import tn.esprit.bawsla.user.common.exception.ForbiddenException;

/**
 * Utilisateur courant, construit à partir des en-têtes X-User-Id / X-User-Role
 * posés par l'api-gateway après validation du JWT. Le service ne revalide pas le JWT.
 * <p>Usage dans un controller : ajouter un paramètre {@code CurrentUser user}.</p>
 */
public record CurrentUser(Long id, String role) {

    public static final String ADMIN = "ADMIN";
    public static final String ENSEIGNANT = "ENSEIGNANT";
    public static final String ETUDIANT = "ETUDIANT";

    public boolean isAdmin() {
        return ADMIN.equals(role);
    }

    public boolean hasAnyRole(String... roles) {
        return Arrays.asList(roles).contains(role);
    }

    /** @throws ForbiddenException si le rôle courant n'est pas dans la liste. */
    public void requireAnyRole(String... roles) {
        if (!hasAnyRole(roles)) {
            throw new ForbiddenException("Action réservée aux rôles : " + String.join(", ", roles));
        }
    }

    /** Autorise le propriétaire de la ressource ou un ADMIN. */
    public void requireOwnerOrAdmin(Long ownerId) {
        if (!isAdmin() && !id.equals(ownerId)) {
            throw new ForbiddenException("Vous n'êtes pas propriétaire de cette ressource");
        }
    }
}
