package org.example.synchro.resources;

import lombok.RequiredArgsConstructor;
import org.example.synchro.dto.SearchResultDTO;
import org.example.synchro.services.LastFmCatalogService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/search")
@RequiredArgsConstructor
public class SearchResource {
    private final LastFmCatalogService catalog;

    @GetMapping
    public SearchResultDTO search(@RequestParam(name = "q", defaultValue = "") String query) {
        if (query.strip().length() > 200) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pesquisa muito longa");
        return catalog.search(query);
    }
}
