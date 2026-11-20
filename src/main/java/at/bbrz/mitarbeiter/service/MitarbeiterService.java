package at.bbrz.mitarbeiter.service;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.IntStream;

import at.bbrz.mitarbeiter.repo.entity.MitarbeiterEntity;
import at.bbrz.mitarbeiter.model.MitarbeiterEntry;
import at.bbrz.mitarbeiter.model.MitarbeiterResponse;
import at.bbrz.mitarbeiter.repo.MitarbeiterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class MitarbeiterService {
    private final MitarbeiterRepository repository;

    @Transactional(readOnly = true)
    public MitarbeiterResponse getMitarbeiter() {
        return new MitarbeiterResponse(repository.findAllByOrderByRankingAsc().stream()
                .map(row -> new MitarbeiterEntry(row.getName(), row.getRanking()))
                .toList());
    }

    @Transactional
    public MitarbeiterResponse saveMitarbeiter(MitarbeiterResponse request) {
        Set<String> names = new HashSet<>();
        Set<Integer> rankings = new HashSet<>();
        List<MitarbeiterEntry> entries = request.getMitarbeiter().stream()
                .map(entry -> new MitarbeiterEntry(entry.getName().strip(), entry.getRanking()))
                .sorted(Comparator.comparingInt(MitarbeiterEntry::getRanking))
                .toList();

        for (MitarbeiterEntry entry : entries) {
            if (!names.add(entry.getName().toLowerCase(Locale.ROOT))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Jeder Mitarbeiter darf nur einmal in der Liste vorkommen.");
            }
            if (!rankings.add(entry.getRanking())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Jedes Ranking von 0 bis 9 muss genau einmal vorkommen.");
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
    public void initializeExamples() {
        if (repository.count() != 0) {
            return;
        }
        List<String> names = List.of(
                "Franz Huber", "Anna Berger", "Lukas Steiner", "Sophie Wagner", "Thomas Gruber",
                "Julia Hofer", "Michael Bauer", "Sarah Leitner", "David Fischer", "Elena Mayer");
        repository.saveAll(IntStream.range(0, names.size())
                .mapToObj(ranking -> new MitarbeiterEntity(null, names.get(ranking), ranking))
                .toList());
    }
}
