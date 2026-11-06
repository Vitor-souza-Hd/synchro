# Recursos da restauração integrados à main

Origem: `restauracao-08-out` no commit `f25fbc9`. Base mantida: `main` no commit `b4ea54b`.

## Recursos

- Favoritos de artistas, músicas e álbuns persistidos por conta. Os botões das páginas existentes usam a mesma biblioteca.
- Avaliações de músicas e álbuns: publicação, atualização, exclusão pelo autor, listagem pessoal e média de notas.
- Perfil musical com biografia, foto por URL HTTPS, banner, rankings por período e gêneros do Last.fm.
- Exploração de artistas, faixas, álbuns e singles; MusicBrainz, Cover Art Archive e fotos de artistas.
- Histórico de páginas recentes, controle de registro do histórico, limpeza do cache de capas e tema claro/escuro. Essas preferências ficam no navegador.
- Navegação entre o catálogo existente, as novas telas, a conta e as playlists. Músicas exploradas também podem ser adicionadas às playlists existentes.

## Navegação

| Tela | Caminho |
|---|---|
| Início e catálogo existente | `/index.html` |
| Explorar | `/frontend/pages/explorar.html` |
| Perfil musical, conta e conexão Last.fm | `/frontend/pages/perfil.html` |
| Favoritos | `/frontend/pages/favoritos.html` |
| Recentes | `/frontend/pages/recentes.html` |
| Preferências | `/frontend/pages/configuracoes.html` |
| Playlists | `/frontend/pages/playlists.html` |

A busca, as playlists e a exploração abrem a mesma página de música em `/frontend/pages/detalhes-musica.html`. Ela aceita tanto o ID do catálogo como artista e título. Links antigos para `musica.html` redirecionam para essa página.

## Preservação da main

Login, cadastro, logout, cookies, JWT e verificação de senhas permanecem com a implementação da main. A autorização do Last.fm mantém a verificação de estado e o callback dinâmico da main. Também permanecem o importador, catálogo, playlists, upload de imagem e alteração de nome de usuário.

Os novos arquivos de interface ficam em `frontend/pages` e `frontend/assets/{js,css}/explorar`. As imagens já organizadas pela main são reutilizadas. As consultas do Last.fm das novas telas passam pelo servidor e usam sua configuração existente; nenhuma chave precisa ser configurada no navegador.

## Verificação

- `mvn test`: autenticação, catálogo, importação, playlists, favoritos entre contas, permissões de avaliações, perfil e consultas de descoberta.
- `node tools/check-restoration-ui.cjs`: telas novas e antigas, comentários tratados como texto, favoritos persistidos, inclusão em playlists, recentes e preferências.
- `node tools/check-auth.cjs`, `node tools/check-playlist-page.cjs`, `node tools/check-song-playlists.cjs` e `node tools/check-lastfm-profile.cjs`: regressões dos fluxos existentes.

Os testes de navegador exigem Node.js, Playwright e Microsoft Edge. Eles usam respostas controladas, sem gravar dados em serviços externos. A disponibilidade de Last.fm, MusicBrainz, Cover Art Archive e Wikimedia depende desses serviços e da configuração do ambiente.
