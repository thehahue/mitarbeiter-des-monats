package at.bbrz.mitarbeiter.repo.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Table("MITARBEITER")
public class MitarbeiterEntity {
    @Id
    private Long id;
    private String name;
    private int ranking;
}
