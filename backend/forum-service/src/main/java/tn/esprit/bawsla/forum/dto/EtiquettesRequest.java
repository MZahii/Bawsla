package tn.esprit.bawsla.forum.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Modification des étiquettes d'un fil par la modération. */
public record EtiquettesRequest(@NotNull @Size(max = 5, message = "5 étiquettes au plus")
                                List<@NotBlank @Size(max = 40) String> etiquettes) {
}
