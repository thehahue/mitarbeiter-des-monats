package at.bbrz.mitarbeiter.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MitarbeiterEntry {
    @NotBlank(message = "Jeder Mitarbeiter braucht einen Namen.")
    @Size(max = 80, message = "Namen dürfen höchstens 80 Zeichen lang sein.")
    private String name;

    @NotNull(message = "Jeder Mitarbeiter braucht ein Ranking.")
    @Min(value = 0, message = "Rankings müssen zwischen 0 und 9 liegen.")
    @Max(value = 9, message = "Rankings müssen zwischen 0 und 9 liegen.")
    private Integer ranking;
}
