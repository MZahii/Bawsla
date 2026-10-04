package tn.esprit.bawsla.user.dto;

public record AuthResponse(String token, String tokenType, long expiresIn, UserDto user) {

    public static AuthResponse bearer(String token, long expiresInMs, UserDto user) {
        return new AuthResponse(token, "Bearer", expiresInMs / 1000, user);
    }
}
