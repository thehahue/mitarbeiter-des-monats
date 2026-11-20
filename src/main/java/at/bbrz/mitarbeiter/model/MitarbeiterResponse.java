package at.bbrz.mitarbeiter.model;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MitarbeiterResponse {
    @NotNull(message = "Die Mitarbeiterliste fehlt.")
    @Size(min = 10, max = 10, message = "Die Rangliste muss genau 10 Mitarbeiter enthalten.")
    private List<@NotNull(message = "Mitarbeiter dürfen nicht null sein.") @Valid MitarbeiterEntry> mitarbeiter;
}
