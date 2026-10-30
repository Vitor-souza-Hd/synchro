# Frontend Synchro

- The main `index.html` is at the static root (`/index.html`).
- `pages/` contains the artist, user, login, and registration pages.
- `assets/css/` contains stylesheets.
- `assets/js/` contains scripts.
- `assets/images/` and `assets/videos/` contain local media.

Spring Boot serves the main page at `/` and `/index.html`.

## Last.fm no perfil

Em `/frontend/pages/user.html`, **Conectar ao lastFM** aparece enquanto a conta não tiver uma sessão Last.fm autorizada. Um nome preenchido no cadastro não conta como conexão. Após autorizar no Last.fm, o usuário volta ao perfil e o Synchro salva foto, nome no Last.fm, país e contagens de scrobbles, artistas, músicas e álbuns, quando disponíveis. Os dados de cadastro do Synchro são preservados.

**Atualizar dados do Last.fm** busca os números novamente e salva a data da atualização. Se a API falhar, os dados anteriores continuam visíveis. Perfis públicos exibem os dados salvos sem controles de conexão ou atualização.

O fluxo usa `lastfm.api-key` e `lastfm.shared-secret` já configurados no servidor. No cadastro da aplicação no Last.fm, configure o callback para a origem do Synchro seguida de `/last-fm/auth/callback` (localmente, `http://localhost:8080/last-fm/auth/callback`). A URL enviada na autorização inclui um estado temporário vinculado à sessão do navegador e à conta Synchro. A chave de sessão Last.fm fica somente no servidor.

Verificação: `LastFmProfileTests` cobre autorização, persistência e falhas com uma API simulada; `node tools/check-lastfm-profile.cjs` verifica a interface em desktop e celular (Playwright disponível em `NODE_PATH`).

Referências: [autenticação web](https://www.last.fm/api/webauth) e [user.getInfo](https://www.last.fm/api/show/user.getInfo).
