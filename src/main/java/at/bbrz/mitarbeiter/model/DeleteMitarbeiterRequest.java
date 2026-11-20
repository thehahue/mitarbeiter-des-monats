package at.bbrz.mitarbeiter.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DeleteMitarbeiterRequest(
        @NotBlank(message = "Zum Löschen wird ein Mitarbeitername benötigt.")
        @Size(max = 80, message = "Namen dürfen höchstens 80 Zeichen lang sein.")
        String name) {
}
