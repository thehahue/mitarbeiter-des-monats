package at.bbrz.mitarbeiter.model;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MitarbeiterResponse {
    @NotNull(message = "Die Mitarbeiterliste fehlt.")
    private List<@NotNull(message = "Mitarbeiter dürfen nicht null sein.") @Valid MitarbeiterEntry> mitarbeiter;
}
