package at.bbrz.mitarbeiter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import at.bbrz.mitarbeiter.model.MitarbeiterEntry;
import at.bbrz.mitarbeiter.model.MitarbeiterResponse;
import at.bbrz.mitarbeiter.repo.MitarbeiterRepository;
import at.bbrz.mitarbeiter.repo.entity.MitarbeiterEntity;
import at.bbrz.mitarbeiter.service.MitarbeiterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:mitarbeiter-test;DB_CLOSE_DELAY=-1")
class MitarbeiterApiTests {
    @Autowired
    WebApplicationContext context;
    @Autowired
    MitarbeiterRepository repository;
    @Autowired
    MitarbeiterService service;
    @Autowired
    JdbcTemplate jdbc;

    MockMvc mvc;
    final JsonMapper json = JsonMapper.builder().build();

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        repository.deleteAll();
        jdbc.update("UPDATE MITARBEITER_STATUS SET INITIALIZED = FALSE WHERE ID = 1");
        service.initializeExamples();
    }

    @Test
    void loadsExamplesFromDatabaseInRankingOrder() throws Exception {
        int count = Math.toIntExact(repository.count());
        mvc.perform(get("/api/mitarbeiter"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.mitarbeiter.length()").value(count))
                .andExpect(jsonPath("$.mitarbeiter[0].name").value("Franz Huber"))
                .andExpect(jsonPath("$.mitarbeiter[0].ranking").value(0))
                .andExpect(jsonPath("$.mitarbeiter[" + (count - 1) + "].name").value("Elena Mayer"))
                .andExpect(jsonPath("$.mitarbeiter[" + (count - 1) + "].ranking").value(count - 1))
                .andExpect(jsonPath("$.mitarbeiter[0].id").doesNotExist());
    }

    @Test
    void savesReorderedListAndReturnsItOnSubsequentGet() throws Exception {
        List<MitarbeiterEntry> entries = reversedRanking();
        String expected = json.writeValueAsString(new MitarbeiterResponse(entries));
        // Die Rankings entscheiden über den Platz, nicht die Array-Reihenfolge.
        List<MitarbeiterEntry> shuffled = new ArrayList<>(entries);
        Collections.rotate(shuffled, 3);
        mvc.perform(post("/api/save-mitarbeiter")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new MitarbeiterResponse(shuffled))))
                .andExpect(status().isOk()).andExpect(content().json(expected));
        mvc.perform(get("/api/mitarbeiter"))
                .andExpect(status().isOk()).andExpect(content().json(expected));
        assertThat(repository.findAllByOrderByRankingAsc()).hasSize(entries.size())
                .first().satisfies(row -> {
                    assertThat(row.getName()).isEqualTo("Elena Mayer");
                    assertThat(row.getRanking()).isZero();
                });
        service.initializeExamples();
        assertThat(service.getMitarbeiter()).isEqualTo(new MitarbeiterResponse(entries));
    }

    @Test
    void rejectsDuplicateRankingsWithoutChangingDatabase() throws Exception {
        List<MitarbeiterEntry> entries = reversedRanking();
        entries.getLast().setRanking(0);
        rejectsWithoutChanges(json.writeValueAsString(new MitarbeiterResponse(entries)));
    }

    @Test
    void rejectsDuplicateNamesRegardlessOfCaseAndSpaces() throws Exception {
        List<MitarbeiterEntry> entries = reversedRanking();
        entries.getLast().setName("  ELENA MAYER  ");
        rejectsWithoutChanges(json.writeValueAsString(new MitarbeiterResponse(entries)));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "null", "{}", "{\"mitarbeiter\":null}",
            "{\"mitarbeiter\":[null]}", "{\"mitarbeiter\":\"falsch\"}", "{kaputt"
    })
    void rejectsMalformedOrIncompleteRequests(String body) throws Exception {
        rejectsWithoutChanges(body);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"name\":\"\",\"ranking\":0}",
            "{\"name\":\"   \",\"ranking\":0}",
            "{\"ranking\":0}", "{\"name\":\"Franz Huber\"}",
            "{\"name\":\"Franz Huber\",\"ranking\":null}",
            "{\"name\":\"Franz Huber\",\"ranking\":-1}",
            "{\"name\":\"Franz Huber\",\"ranking\":2147483647}",
            "{\"name\":\"Franz Huber\",\"ranking\":0.5}",
            "{\"name\":\"Franz Huber\",\"ranking\":\"falsch\"}", "null"
    })
    void rejectsInvalidEntriesInAnOtherwiseCompleteList(String entry) throws Exception {
        String valid = json.writeValueAsString(service.getMitarbeiter());
        int firstEntryStart = valid.indexOf('{', 1);
        int firstEntryEnd = valid.indexOf('}', firstEntryStart) + 1;
        rejectsWithoutChanges(valid.substring(0, firstEntryStart) + entry + valid.substring(firstEntryEnd));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 3, 12, 37})
    void savesAndLoadsDynamicTeamSizes(int count) throws Exception {
        List<MitarbeiterEntry> entries = sampleEntries(count);
        String expected = json.writeValueAsString(new MitarbeiterResponse(entries));
        List<MitarbeiterEntry> shuffled = new ArrayList<>(entries);
        Collections.reverse(shuffled);
        mvc.perform(post("/api/save-mitarbeiter")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new MitarbeiterResponse(shuffled))))
                .andExpect(status().isOk()).andExpect(content().json(expected));
        mvc.perform(get("/api/mitarbeiter"))
                .andExpect(status().isOk()).andExpect(content().json(expected));
        assertThat(repository.count()).isEqualTo(count);
        service.initializeExamples();
        assertThat(service.getMitarbeiter()).isEqualTo(new MitarbeiterResponse(entries));
    }

    @Test
    void rejectsRankingsWithGapsOrWithoutZeroWithoutChangingDatabase() throws Exception {
        for (int count : List.of(1, 3, 12)) {
            List<MitarbeiterEntry> entries = sampleEntries(count);
            entries.getLast().setRanking(count);
            rejectsWithoutChanges(json.writeValueAsString(new MitarbeiterResponse(entries)));
            entries = sampleEntries(count);
            entries.forEach(entry -> entry.setRanking(entry.getRanking() + 1));
            rejectsWithoutChanges(json.writeValueAsString(new MitarbeiterResponse(entries)));
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 12})
    void appendsNewEmployeeAndReturnsEntirePersistedList(int count) throws Exception {
        List<MitarbeiterEntry> entries = sampleEntries(count);
        service.saveMitarbeiter(new MitarbeiterResponse(entries));
        entries.add(new MitarbeiterEntry("Max Meier", count));
        String expected = json.writeValueAsString(new MitarbeiterResponse(entries));

        mvc.perform(post("/api/new-mitarbeiter")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  Max Meier  \"}"))
                .andExpect(status().isOk())
                .andExpect(content().json(expected))
                .andExpect(jsonPath("$.mitarbeiter[0].id").doesNotExist());
        mvc.perform(get("/api/mitarbeiter"))
                .andExpect(status().isOk()).andExpect(content().json(expected));
        assertThat(repository.count()).isEqualTo(count + 1);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "null", "{}", "{kaputt", "[]", "{\"name\":null}", "{\"name\":\"\"}",
            "{\"name\":\"   \"}", "{\"name\":\"Franz Huber\"}",
            "{\"name\":\"  FRANZ HUBER  \"}"
    })
    void rejectsInvalidOrDuplicateNewEmployeesWithoutChangingDatabase(String body) throws Exception {
        MitarbeiterResponse before = service.getMitarbeiter();
        mvc.perform(post("/api/new-mitarbeiter").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").isNotEmpty());
        assertThat(service.getMitarbeiter()).isEqualTo(before);
    }

    @Test
    void validatesMaximumNameLengthForAllEndpoints() throws Exception {
        String tooLong = "A".repeat(81);
        MitarbeiterResponse before = service.getMitarbeiter();
        mvc.perform(post("/api/new-mitarbeiter").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Collections.singletonMap("name", tooLong))))
                .andExpect(status().isBadRequest());
        assertThat(service.getMitarbeiter()).isEqualTo(before);
        mvc.perform(post("/api/delete-mitarbeiter").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Collections.singletonMap("name", tooLong))))
                .andExpect(status().isBadRequest());
        assertThat(service.getMitarbeiter()).isEqualTo(before);
        List<MitarbeiterEntry> entries = sampleEntries(1);
        entries.getFirst().setName(tooLong);
        rejectsWithoutChanges(json.writeValueAsString(new MitarbeiterResponse(entries)));

        String longestName = "A".repeat(80);
        mvc.perform(post("/api/new-mitarbeiter").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Collections.singletonMap("name", longestName))))
                .andExpect(status().isOk());
        assertThat(repository.findAllByOrderByRankingAsc().getLast().getName()).isEqualTo(longestName);
        mvc.perform(post("/api/delete-mitarbeiter").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Collections.singletonMap("name", longestName))))
                .andExpect(status().isOk());
        assertThat(service.getMitarbeiter()).isEqualTo(before);
    }

    @ParameterizedTest
    @CsvSource({"1, 0", "5, 0", "5, 2", "5, 4", "12, 6"})
    void deletesEmployeeAndClosesRankingGapWithoutChangingRemainingIds(int count, int removedIndex) throws Exception {
        service.saveMitarbeiter(new MitarbeiterResponse(sampleEntries(count)));
        List<MitarbeiterEntity> before = repository.findAllByOrderByRankingAsc();
        MitarbeiterEntity removed = before.get(removedIndex);
        List<MitarbeiterEntity> remaining = new ArrayList<>(before);
        remaining.remove(removedIndex);
        List<MitarbeiterEntry> expected = IntStream.range(0, remaining.size())
                .mapToObj(index -> new MitarbeiterEntry(remaining.get(index).getName(), index)).toList();
        String expectedJson = json.writeValueAsString(new MitarbeiterResponse(expected));

        mvc.perform(post("/api/delete-mitarbeiter")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Collections.singletonMap("name",
                                "  " + removed.getName().toUpperCase(java.util.Locale.ROOT) + "  "))))
                .andExpect(status().isOk()).andExpect(content().json(expectedJson));
        mvc.perform(get("/api/mitarbeiter"))
                .andExpect(status().isOk()).andExpect(content().json(expectedJson));
        assertThat(repository.existsById(removed.getId())).isFalse();
        List<MitarbeiterEntity> stored = repository.findAllByOrderByRankingAsc();
        assertThat(stored).extracting(MitarbeiterEntity::getId)
                .containsExactlyElementsOf(remaining.stream().map(MitarbeiterEntity::getId).toList());
        assertThat(stored).extracting(MitarbeiterEntity::getRanking)
                .containsExactlyElementsOf(IntStream.range(0, remaining.size()).boxed().toList());
        // Nach dem Löschen des letzten Mitarbeiters dürfen auch keine Beispiele zurückkehren.
        runSchema();
        service.initializeExamples();
        assertThat(service.getMitarbeiter()).isEqualTo(new MitarbeiterResponse(expected));

        List<MitarbeiterEntry> afterAppend = new ArrayList<>(expected);
        afterAppend.add(new MitarbeiterEntry("Nachfolger", expected.size()));
        mvc.perform(post("/api/new-mitarbeiter").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Nachfolger\"}"))
                .andExpect(status().isOk())
                .andExpect(content().json(json.writeValueAsString(new MitarbeiterResponse(afterAppend))));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "null", "{}", "{kaputt", "[]", "{\"name\":null}",
            "{\"name\":\"\"}", "{\"name\":\"   \"}"
    })
    void rejectsInvalidDeleteRequestsWithoutChangingDatabase(String body) throws Exception {
        MitarbeiterResponse before = service.getMitarbeiter();
        mvc.perform(post("/api/delete-mitarbeiter").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").isNotEmpty());
        assertThat(service.getMitarbeiter()).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 3})
    void rejectsUnknownEmployeeWithoutChangingDatabase(int count) throws Exception {
        service.saveMitarbeiter(new MitarbeiterResponse(sampleEntries(count)));
        MitarbeiterResponse before = service.getMitarbeiter();
        mvc.perform(post("/api/delete-mitarbeiter").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Unbekannt\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Dieser Mitarbeiter wurde nicht gefunden."));
        assertThat(service.getMitarbeiter()).isEqualTo(before);
    }

    @Test
    void keepsExistingDataWhenInitializingAnExistingDatabase() {
        MitarbeiterResponse before = service.getMitarbeiter();
        jdbc.update("UPDATE MITARBEITER_STATUS SET INITIALIZED = FALSE WHERE ID = 1");
        service.initializeExamples();
        assertThat(service.getMitarbeiter()).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT INITIALIZED FROM MITARBEITER_STATUS WHERE ID = 1", Boolean.class))
                .isTrue();
    }

    @Test
    void schemaSeedsExamplesOnceInAnUninitializedEmptyDatabase() {
        repository.deleteAll();
        jdbc.update("UPDATE MITARBEITER_STATUS SET INITIALIZED = FALSE WHERE ID = 1");
        runSchema();
        List<MitarbeiterEntity> seeded = repository.findAllByOrderByRankingAsc();
        assertThat(seeded).extracting(MitarbeiterEntity::getName).containsExactly(
                "Franz Huber", "Anna Berger", "Lukas Steiner", "Sophie Wagner", "Thomas Gruber",
                "Julia Hofer", "Michael Bauer", "Sarah Leitner", "David Fischer", "Elena Mayer");
        assertThat(seeded).extracting(MitarbeiterEntity::getRanking)
                .containsExactly(0, 1, 2, 3, 4, 5, 6, 7, 8, 9);
        // Auch ein wiederholter SQL-Lauf vor dem ApplicationRunner dupliziert nichts.
        runSchema();
        service.initializeExamples();
        runSchema();
        assertThat(repository.findAllByOrderByRankingAsc()).isEqualTo(seeded);
    }

    @Test
    void schemaKeepsExistingEmployeesInAnUninitializedDatabase() {
        service.saveMitarbeiter(new MitarbeiterResponse(sampleEntries(3)));
        List<MitarbeiterEntity> before = repository.findAllByOrderByRankingAsc();
        jdbc.update("UPDATE MITARBEITER_STATUS SET INITIALIZED = FALSE WHERE ID = 1");
        runSchema();
        service.initializeExamples();
        assertThat(repository.findAllByOrderByRankingAsc()).isEqualTo(before);
    }

    @Test
    void schemaDoesNotRefillAnIntentionallyEmptyList() {
        service.saveMitarbeiter(new MitarbeiterResponse(List.of()));
        runSchema();
        service.initializeExamples();
        assertThat(repository.count()).isZero();
    }

    private void runSchema() {
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(jdbc.getDataSource());
    }

    @Test
    void concurrentNewEmployeesReceiveDifferentRankingsInAnEmptyList() throws Exception {
        service.saveMitarbeiter(new MitarbeiterResponse(List.of()));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> postAfterSignal("/api/new-mitarbeiter", "Max Meier", ready, start));
            var second = executor.submit(() -> postAfterSignal("/api/new-mitarbeiter", "Anna Neu", ready, start));
            try {
                assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            } finally {
                start.countDown();
            }
            assertThat(first.get(10, TimeUnit.SECONDS)).isEqualTo(200);
            assertThat(second.get(10, TimeUnit.SECONDS)).isEqualTo(200);
        }
        List<MitarbeiterEntity> stored = repository.findAllByOrderByRankingAsc();
        assertThat(stored).extracting(MitarbeiterEntity::getName)
                .containsExactlyInAnyOrder("Max Meier", "Anna Neu");
        assertThat(stored).extracting(MitarbeiterEntity::getRanking).containsExactly(0, 1);
    }

    @Test
    void concurrentDeletesKeepOrderAndContiguousRankings() throws Exception {
        service.saveMitarbeiter(new MitarbeiterResponse(sampleEntries(5)));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> postAfterSignal("/api/delete-mitarbeiter", "Person 2", ready, start));
            var second = executor.submit(() -> postAfterSignal("/api/delete-mitarbeiter", "Person 4", ready, start));
            try {
                assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            } finally {
                start.countDown();
            }
            assertThat(first.get(10, TimeUnit.SECONDS)).isEqualTo(200);
            assertThat(second.get(10, TimeUnit.SECONDS)).isEqualTo(200);
        }
        assertThat(service.getMitarbeiter()).isEqualTo(new MitarbeiterResponse(List.of(
                new MitarbeiterEntry("Person 1", 0),
                new MitarbeiterEntry("Person 3", 1),
                new MitarbeiterEntry("Person 5", 2))));
    }

    private int postAfterSignal(String endpoint, String name, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Startsignal für die parallele Anfrage fehlt.");
        }
        return mvc.perform(post(endpoint).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Collections.singletonMap("name", name))))
                .andReturn().getResponse().getStatus();
    }

    private List<MitarbeiterEntry> sampleEntries(int count) {
        return IntStream.range(0, count)
                .mapToObj(index -> new MitarbeiterEntry("Person " + (index + 1), index))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }

    private List<MitarbeiterEntry> reversedRanking() {
        List<MitarbeiterEntry> entries = new ArrayList<>(service.getMitarbeiter().getMitarbeiter());
        Collections.reverse(entries);
        for (int index = 0; index < entries.size(); index++) {
            entries.get(index).setRanking(index);
        }
        return entries;
    }

    private void rejectsWithoutChanges(String body) throws Exception {
        MitarbeiterResponse before = service.getMitarbeiter();
        mvc.perform(post("/api/save-mitarbeiter").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        assertThat(service.getMitarbeiter()).isEqualTo(before);
    }
}
