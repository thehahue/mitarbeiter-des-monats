package at.bbrz.mitarbeiter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import at.bbrz.mitarbeiter.model.MitarbeiterEntry;
import at.bbrz.mitarbeiter.model.MitarbeiterResponse;
import at.bbrz.mitarbeiter.repo.MitarbeiterRepository;
import at.bbrz.mitarbeiter.service.MitarbeiterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
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

    MockMvc mvc;
    final JsonMapper json = JsonMapper.builder().build();

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        repository.deleteAll();
        service.initializeExamples();
    }

    @Test
    void loadsTenExamplesFromDatabaseInRankingOrder() throws Exception {
        mvc.perform(get("/api/mitarbeiter"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.mitarbeiter.length()").value(10))
                .andExpect(jsonPath("$.mitarbeiter[0].name").value("Franz Huber"))
                .andExpect(jsonPath("$.mitarbeiter[0].ranking").value(0))
                .andExpect(jsonPath("$.mitarbeiter[9].name").value("Elena Mayer"))
                .andExpect(jsonPath("$.mitarbeiter[9].ranking").value(9))
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
        assertThat(repository.findAllByOrderByRankingAsc()).hasSize(10)
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
        entries.get(9).setRanking(0);
        rejectsWithoutChanges(json.writeValueAsString(new MitarbeiterResponse(entries)));
    }

    @Test
    void rejectsDuplicateNamesRegardlessOfCaseAndSpaces() throws Exception {
        List<MitarbeiterEntry> entries = reversedRanking();
        entries.get(9).setName("  ELENA MAYER  ");
        rejectsWithoutChanges(json.writeValueAsString(new MitarbeiterResponse(entries)));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "null", "{}", "{\"mitarbeiter\":null}", "{\"mitarbeiter\":[]}",
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
            "{\"name\":\"Franz Huber\",\"ranking\":10}",
            "{\"name\":\"Franz Huber\",\"ranking\":0.5}",
            "{\"name\":\"Franz Huber\",\"ranking\":\"falsch\"}", "null"
    })
    void rejectsInvalidEntriesInAnOtherwiseCompleteList(String entry) throws Exception {
        String valid = json.writeValueAsString(service.getMitarbeiter());
        int firstEntryStart = valid.indexOf('{', 1);
        int firstEntryEnd = valid.indexOf('}', firstEntryStart) + 1;
        rejectsWithoutChanges(valid.substring(0, firstEntryStart) + entry + valid.substring(firstEntryEnd));
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
