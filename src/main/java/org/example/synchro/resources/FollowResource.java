package org.example.synchro.resources;

import lombok.RequiredArgsConstructor;
import org.example.synchro.entities.Follow;
import org.example.synchro.entities.User;
import org.example.synchro.repositories.FollowRepository;
import org.example.synchro.repositories.UserRepository;
import org.example.synchro.services.CookieService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/follow")
@RequiredArgsConstructor
public class FollowResource {

    private final FollowRepository followRepository;
    private final UserRepository userRepository;
    private final CookieService cookieService;

    // Rota para SEGUIR um usuário
    @PostMapping("/{idParaSeguir}")
    public ResponseEntity<String> seguir(@CookieValue(name = "token_jwt", required = false) String token, @PathVariable Long idParaSeguir) {
        if (!cookieService.checkCookie(token)) {
            return ResponseEntity.status(401).body("Não autenticado");
        }

        Long meuId = cookieService.getId(token);
        if (meuId.equals(idParaSeguir)) {
            return ResponseEntity.badRequest().body("Você não pode seguir a si mesmo");
        }

        User eu = userRepository.findById(meuId).orElseThrow();
        User outro = userRepository.findById(idParaSeguir).orElseThrow();

        // Verifica se já segue para não duplicar
        if (followRepository.findBySeguidorAndSeguido(eu, outro).isPresent()) {
            return ResponseEntity.ok("Já segue este usuário");
        }

        followRepository.save(new Follow(eu, outro));
        return ResponseEntity.ok("Seguindo com sucesso");
    }

    @DeleteMapping("/{idParaDeixarDeSeguir}")
    public ResponseEntity<String> deixarDeSeguir(@CookieValue(name = "token_jwt", required = false) String token, @PathVariable Long idParaDeixarDeSeguir) {
        if (!cookieService.checkCookie(token)) {
            return ResponseEntity.status(401).body("Não autenticado");
        }

        Long meuId = cookieService.getId(token);
        User eu = userRepository.findById(meuId).orElseThrow();
        User outro = userRepository.findById(idParaDeixarDeSeguir).orElseThrow();

        Optional<Follow> follow = followRepository.findBySeguidorAndSeguido(eu, outro);
        follow.ifPresent(followRepository::delete);

        return ResponseEntity.ok("Deixou de seguir");

    }
    // Rota para VERIFICAR se já segue
    @GetMapping("/check/{idParaChecar}")
    public ResponseEntity<Boolean> checarSeSegue(@CookieValue(name = "token_jwt", required = false) String token, @PathVariable Long idParaChecar) {
        if (!cookieService.checkCookie(token)) {
            return ResponseEntity.ok(false); // Se não tiver logado, retorna false
        }

        Long meuId = cookieService.getId(token);
        Optional<User> euOpt = userRepository.findById(meuId);
        Optional<User> outroOpt = userRepository.findById(idParaChecar);

        if (euOpt.isEmpty() || outroOpt.isEmpty()) {
            return ResponseEntity.ok(false);
        }

        Optional<Follow> follow = followRepository.findBySeguidorAndSeguido(euOpt.get(), outroOpt.get());
        return ResponseEntity.ok(follow.isPresent());
    }
}