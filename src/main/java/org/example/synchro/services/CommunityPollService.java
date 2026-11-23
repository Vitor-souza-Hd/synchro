package org.example.synchro.services;

import lombok.RequiredArgsConstructor;
import org.example.synchro.entities.CommunityPoll;
import org.example.synchro.entities.CommunityPollVote;
import org.example.synchro.entities.CommunityPost;
import org.example.synchro.repositories.CommunityPollRepository;
import org.example.synchro.repositories.CommunityPollVoteRepository;
import org.example.synchro.repositories.UserRepository;
import org.example.synchro.services.exception.InvalidTokenException;
import org.example.synchro.services.exception.ResourceNotFoundException;
import org.example.synchro.services.exception.UnauthorizedException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommunityPollService {
    public static final int MAX_QUESTION_LENGTH = 160;
    public static final int MIN_OPTIONS = 2;
    public static final int MAX_OPTIONS = 5;
    public static final int MAX_OPTION_LENGTH = 80;

    private final CommunityPollRepository polls;
    private final CommunityPollVoteRepository votes;
    private final UserRepository users;
    private final CookieService cookies;

    public record PollRequest(String question, List<String> options) {}
    public record PollOptionView(int id, String text, long votes) {}
    public record PollView(String question, List<PollOptionView> options, long totalVotes, Integer selectedOption) {}

    @Transactional(propagation = Propagation.MANDATORY)
    public void create(CommunityPost post, PollRequest request) {
        if (request == null) return;
        String question = cleanText(request.question(), MAX_QUESTION_LENGTH, "Escreva uma pergunta de até 160 caracteres.");
        if (request.options() == null || request.options().size() < MIN_OPTIONS || request.options().size() > MAX_OPTIONS) {
            throw invalid("Adicione de 2 a 5 opções na enquete.");
        }

        var options = new ArrayList<String>();
        var distinct = new HashSet<String>();
        for (String value : request.options()) {
            String option = cleanText(value, MAX_OPTION_LENGTH, "Preencha cada opção com até 80 caracteres.");
            String key = Normalizer.normalize(option, Normalizer.Form.NFKC).replaceAll("(?U)\\s+", " ")
                    .strip().toLowerCase(Locale.ROOT);
            if (!distinct.add(key)) throw invalid("Use opções diferentes na enquete.");
            options.add(option);
        }
        polls.save(new CommunityPoll(post, question, options));
    }

    public Map<Long, PollView> findForPosts(Collection<Long> postIds, Long viewerId) {
        if (postIds.isEmpty()) return Map.of();
        return views(polls.findWithOptionsByPostIds(postIds), viewerId);
    }

    public PollView findForPost(Long postId, Long viewerId) {
        return findForPosts(List.of(postId), viewerId).get(postId);
    }

    @Transactional
    public PollView vote(String token, long postId, int optionId) {
        Long viewer = requireViewer(token);
        var voter = users.findById(viewer).orElseThrow(() -> new UnauthorizedException("Sua sessão expirou."));
        // Lock the poll before reading the vote so simultaneous submissions cannot create two votes.
        var poll = polls.findByPostIdForUpdate(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Enquete não encontrada."));
        if (optionId < 0 || optionId >= poll.getOptions().size()) {
            throw invalid("Escolha uma opção desta enquete.");
        }

        votes.findByPollIdAndVoterId(poll.getId(), viewer)
                .ifPresentOrElse(vote -> vote.select(optionId), () -> votes.save(new CommunityPollVote(poll, voter, optionId)));
        return views(List.of(poll), viewer).get(postId);
    }

    private Map<Long, PollView> views(List<CommunityPoll> entries, Long viewerId) {
        if (entries.isEmpty()) return Map.of();
        var ids = entries.stream().map(CommunityPoll::getId).toList();
        var counts = new HashMap<Long, Map<Integer, Long>>();
        for (var count : votes.countByPollAndOption(ids)) {
            counts.computeIfAbsent(count.getPollId(), ignored -> new HashMap<>())
                    .put(count.getOptionIndex(), count.getVoteCount());
        }

        var selected = new HashMap<Long, Integer>();
        if (viewerId != null) {
            for (var vote : votes.findSelections(ids, viewerId)) selected.put(vote.getPollId(), vote.getOptionIndex());
        }

        var result = new HashMap<Long, PollView>();
        for (var poll : entries) {
            Map<Integer, Long> optionCounts = counts.getOrDefault(poll.getId(), Map.of());
            var options = poll.getOptions().stream().map(option -> new PollOptionView(
                    option.getPosition(), option.getText(), optionCounts.getOrDefault(option.getPosition(), 0L))).toList();
            long total = options.stream().mapToLong(PollOptionView::votes).sum();
            result.put(poll.getPost().getId(), new PollView(poll.getQuestion(), options, total, selected.get(poll.getId())));
        }
        return result;
    }

    private Long requireViewer(String token) {
        try {
            if (cookies.checkCookie(token)) return cookies.getId(token);
        } catch (InvalidTokenException | NumberFormatException ignored) {
            // Treat an invalid session like a missing session.
        }
        throw new UnauthorizedException("Entre na sua conta para votar.");
    }

    private static String cleanText(String value, int maximum, String message) {
        if (value == null) throw invalid(message);
        String clean = value.replaceAll("(?U)^\\s+|\\s+$", "");
        if (clean.isEmpty() || clean.length() > maximum) throw invalid(message);
        return clean;
    }

    private static ResponseStatusException invalid(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
