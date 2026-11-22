package org.example.synchro.resources;

import lombok.RequiredArgsConstructor;
import org.example.synchro.dto.UserSearchResult;
import org.example.synchro.repositories.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class UserSearchResource {
    private static final int PAGE_SIZE = 20;
    private final UserRepository users;

    @GetMapping("/search/users")
    public Results search(@RequestParam(name = "q", defaultValue = "") String query,
                          @RequestParam(defaultValue = "0") int page) {
        String term = query.strip();
        if (term.length() > 200 || page < 0 || page > (Integer.MAX_VALUE - PAGE_SIZE) / PAGE_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pesquisa ou página inválida");
        }
        var matches = users.searchPeople(term, PageRequest.of(page, PAGE_SIZE));
        return new Results(matches.getContent(), matches.hasNext());
    }

    public record Results(List<UserSearchResult> items, boolean hasNext) {}
}
