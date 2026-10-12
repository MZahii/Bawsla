package tn.esprit.bawsla.cours.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MinutageIaDto(int ordre, long debutMs, long finMs) {
}
