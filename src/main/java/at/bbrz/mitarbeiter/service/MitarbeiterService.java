package at.bbrz.mitarbeiter.service;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.IntStream;

import at.bbrz.mitarbeiter.repo.entity.MitarbeiterEntity;
import at.bbrz.mitarbeiter.model.DeleteMitarbeiterRequest;
import at.bbrz.mitarbeiter.model.MitarbeiterEntry;
import at.bbrz.mitarbeiter.model.MitarbeiterResponse;
import at.bbrz.mitarbeiter.model.NewMitarbeiterRequest;
import at.bbrz.mitarbeiter.repo.MitarbeiterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class MitarbeiterService {
    private final MitarbeiterRepository repository;
    private final JdbcTemplate jdbc;

    @Transactional(readOnly = true)
    public MitarbeiterResponse getMitarbeiter() {
        return new MitarbeiterResponse(repository.findAllByOrderByRankingAsc().stream()
                .map(row -> new MitarbeiterEntry(row.getName(), row.getRanking()))
                .toList());
    }

    @Transactional
    public MitarbeiterResponse saveMitarbeiter(MitarbeiterResponse request) {
        lockRanking();
        Set<String> names = new HashSet<>();
        List<MitarbeiterEntry> entries = request.getMitarbeiter().stream()
                .map(entry -> new MitarbeiterEntry(entry.getName().strip(), entry.getRanking()))
                .sorted(Comparator.comparingInt(MitarbeiterEntry::getRanking))
                .toList();

        for (int ranking = 0; ranking < entries.size(); ranking++) {
            MitarbeiterEntry entry = entries.get(ranking);
            if (!names.add(entry.getName().toLowerCase(Locale.ROOT))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Jeder Mitarbeiter darf nur einmal in der Liste vorkommen.");
            }
            if (entry.getRanking() != ranking) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Rankings müssen bei 0 beginnen und lückenlos sowie eindeutig sein.");
            }
        }

        // Die vollständige Rangliste wird innerhalb einer Transaktion ersetzt.
        repository.deleteAll();
        repository.saveAll(entries.stream()
                .map(entry -> new MitarbeiterEntity(null, entry.getName(), entry.getRanking()))
                .toList());
        return getMitarbeiter();
    }

    @Transactional
    public MitarbeiterResponse newMitarbeiter(NewMitarbeiterRequest request) {
        lockRanking();
        String name = request.name().strip();
        List<MitarbeiterEntity> entries = repository.findAllByOrderByRankingAsc();
        String normalizedName = name.toLowerCase(Locale.ROOT);
        if (entries.stream().anyMatch(entry -> entry.getName().toLowerCase(Locale.ROOT).equals(normalizedName))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Dieser Mitarbeiter existiert bereits.");
        }
        repository.save(new MitarbeiterEntity(null, name, entries.size()));
        return getMitarbeiter();
    }

    @Transactional
    public MitarbeiterResponse deleteMitarbeiter(DeleteMitarbeiterRequest request) {
        lockRanking();
        String normalizedName = request.name().strip().toLowerCase(Locale.ROOT);
        List<MitarbeiterEntity> entries = repository.findAllByOrderByRankingAsc();
        int removedIndex = IntStream.range(0, entries.size())
                .filter(index -> entries.get(index).getName().toLowerCase(Locale.ROOT).equals(normalizedName))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Dieser Mitarbeiter wurde nicht gefunden."));

        repository.deleteById(entries.get(removedIndex).getId());
        // Aufsteigend nachrücken: Der jeweils nächste Zielplatz ist bereits frei.
        // So bleiben Rankings auch während der Updates eindeutig und IDs erhalten.
        for (int index = removedIndex + 1; index < entries.size(); index++) {
            MitarbeiterEntity entry = entries.get(index);
            entry.setRanking(index - 1);
            repository.save(entry);
        }
        return getMitarbeiter();
    }

    // Eine gemeinsame Datenbankzeile sperrt schreibende Transaktionen bis zum Commit,
    // auch bei einer leeren Rangliste. So erhalten parallele Neuanlagen eigene Plätze.
    private boolean lockRanking() {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT INITIALIZED FROM MITARBEITER_STATUS WHERE ID = 1 FOR UPDATE", Boolean.class));
    }

    @Transactional
    public void initializeExamples() {
        if (lockRanking()) {
            return;
        }
        if (repository.count() == 0) {
            List<String> names = List.of(
                    "Franz Huber", "Anna Berger", "Lukas Steiner", "Sophie Wagner", "Thomas Gruber",
                    "Julia Hofer", "Michael Bauer", "Sarah Leitner", "David Fischer", "Elena Mayer");
            repository.saveAll(IntStream.range(0, names.size())
                    .mapToObj(ranking -> new MitarbeiterEntity(null, names.get(ranking), ranking))
                    .toList());
        }
        jdbc.update("UPDATE MITARBEITER_STATUS SET INITIALIZED = TRUE WHERE ID = 1");
    }
}
