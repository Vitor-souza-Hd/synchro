package org.example.synchro.services;

import lombok.RequiredArgsConstructor;
import org.example.synchro.entities.User;
import org.example.synchro.entities.UserData;
import org.example.synchro.repositories.UserDataRepository;
import org.example.synchro.repositories.UserRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserDataService {

    private final LastfmService lastfmService;
    private final UserRepository userRepository;
    private final UserDataRepository userDataRepository;

    public void atualizarUserData(User user){
        if(user.getLastFmUsername() != null) {
            List<Integer> contadores = new ArrayList<>();
            UserData data = user.getData();
            contadores = lastfmService.contadores(user.getLastFmUsername());
            data.setScrobbles(contadores.get(0));
            data.setArtistas(contadores.get(1));
            userDataRepository.save(data);
        }

    }
}
