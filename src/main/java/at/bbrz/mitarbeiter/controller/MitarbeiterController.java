package at.bbrz.mitarbeiter.controller;

import at.bbrz.mitarbeiter.model.MitarbeiterResponse;
import at.bbrz.mitarbeiter.service.MitarbeiterService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class MitarbeiterController {
    private final MitarbeiterService service;

    @GetMapping("/mitarbeiter")
    public ResponseEntity<MitarbeiterResponse> getMitarbeiter() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.getMitarbeiter());
    }

    @PostMapping("/save-mitarbeiter")
    public MitarbeiterResponse saveMitarbeiter(@Valid @RequestBody MitarbeiterResponse request) {
        return service.saveMitarbeiter(request);
    }
}
