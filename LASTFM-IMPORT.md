# Importar o histórico do Last.fm

Ao iniciar `SynchroApplication` normalmente, o perfil padrão `local` abre o mesmo H2 persistente
(`jdbc:h2:file:./data/synchro`) e importa/atualiza automaticamente `jinka070`.
Os dados já importados ficam disponíveis imediatamente; novas execuções buscam apenas novas audições.
Se o Last.fm estiver indisponível, a aplicação mantém os dados salvos e tenta retomar na próxima inicialização.
O console está em `/h2-console`: URL JDBC `jdbc:h2:file:./data/synchro`, usuário `sa`, senha vazia.
Execute sempre com a pasta do projeto como diretório de trabalho.
Para desativar a atualização automática: `--lastfm.import.on-startup=false`.
O perfil `test` continua usando H2 em memória, com os dados de demonstração e sem importação automática.

Execute na pasta do projeto com JDK 17 ou superior em `JAVA_HOME`:

```powershell
./import-lastfm.ps1
```

O usuário padrão é `jinka070`. Para outro usuário: `./import-lastfm.ps1 -Username nome`.
A chave já configurada em `application.properties` é usada; `LASTFM_API_KEY` permite substituí-la sem editar arquivos.

O comando busca todas as páginas de `user.getRecentTracks`, com 200 registros por página e `extended=1`.
Cria/reutiliza `Artista`, `Album` e `Musica` pelo nome/título, ignorando maiúsculas e espaços nas pontas.
Cada tipo de catálogo tem sua própria unicidade. Músicas e álbuns com o mesmo título são unificados mesmo
quando pertencem a artistas diferentes; os vínculos com os artistas são acumulados.
Na inicialização, duplicatas antigas são unificadas, preservando referências do histórico, playlists e avaliações.
As reproduções continuam separadas, porque representam audições distintas.
Cada reprodução fica em `last_fm_scrobble`, vinculada ao catálogo, com usuário, instante UTC em segundos,
álbum daquela reprodução e JSON original completo (incluindo MBIDs, URLs, imagens e indicação de favorita quando fornecidos).
O histórico cobre apenas scrobbles disponíveis no Last.fm. Biografias, duração e listas completas de faixas dos álbuns
não fazem parte deste endpoint e não são inventados ou consultados separadamente.

Após importar o histórico, o comando consulta `track.getTopTags` para preencher `Musica.genero` nas músicas
desse usuário que ainda estejam sem gênero, incluindo músicas importadas em execuções anteriores.
Como as tags do Last.fm são livres, apenas os estilos reconhecidos em `LastFmGenres` são aproveitados;
rótulos pessoais não são tratados como gêneros. Até três gêneros são salvos, separados por vírgulas.
Músicas sem tags musicais reconhecidas continuam sem gênero; a data da consulta é salva em
`genero_consultado_em`, e elas podem ser consultadas novamente após sete dias. Gêneros já preenchidos são preservados.
Se houver interrupção, os resultados já gravados são mantidos e a próxima execução retoma as músicas pendentes.
Essa etapa também roda na inicialização normal da aplicação e pode levar alguns minutos na primeira execução.
Para desativá-la, use `--lastfm.import.genres.enabled=false`.

O banco padrão é **persistente**, em `data/synchro.mv.db`, compartilhado com o perfil `local` do site.
Feche o site antes de executar o importador com H2 em arquivo. Apenas um importador pode usar o arquivo por vez.
Para usar outro banco já existente, configure `LASTFM_IMPORT_DB_URL`, `LASTFM_IMPORT_DB_DRIVER`,
`LASTFM_IMPORT_DB_USER` e `LASTFM_IMPORT_DB_PASSWORD` (por exemplo, driver `org.postgresql.Driver`).
Não execute dois importadores simultaneamente para o mesmo usuário.

Cada página e seu progresso são gravados na mesma transação. Se a rede ou o processo falhar, rode o mesmo comando
para retomar. O horário de corte fica salvo em `last_fm_import_state`; novas audições não deslocam as páginas.
Ao concluir, outra execução busca apenas o intervalo posterior ao corte anterior. A faixa tocando agora é ignorada
até virar um scrobble. Reproduções idênticas no mesmo segundo são preservadas como ocorrências distintas.
Edições/exclusões retroativas no Last.fm durante uma importação podem alterar a paginação mesmo com o corte fixo;
a sincronização incremental não reconcilia alterações retroativas.

Não use o perfil `test` para armazenar o histórico: ele é temporário e o inicializador do site insere dados de demonstração.
O importador roda isoladamente, sem esse inicializador e sem exigir as chaves JWT do site.

Documentação: https://www.last.fm/api/show/user.getRecentTracks

# Pesquisa do catálogo no Last.fm

A barra de pesquisa consulta o Last.fm ao pressionar **Enter** ou clicar na lupa. São consultadas músicas, artistas e álbuns (até 5 resultados por categoria, configuráveis com `lastfm.search.limit`, máximo 10). Os resultados são gravados no H2 e enriquecidos com `track.getInfo`, `artist.getInfo` e `album.getInfo`. Pesquisar não registra reproduções no histórico do usuário.

São armazenados, quando fornecidos: capa, URL, MBID, ouvintes e reproduções globais, tags, gêneros musicais reconhecidos, duração, descrição/biografia, associação com artistas/álbum e faixas do álbum. A resposta JSON completa de cada consulta de detalhes fica em `lastfm_raw_json`, preservando também os campos que a interface ainda não utiliza. `lastfm_updated_at` registra a atualização. Textos longos são armazenados sem o limite antigo de 255 caracteres.

A regra de unicidade por nome continua valendo. Versões homônimas reutilizam o mesmo registro; a página destaca o artista da consulta de detalhes para manter capa e estatísticas coerentes. Uma resposta resumida da pesquisa não substitui detalhes já salvos.

Pesquisas iguais usam cache de 5 minutos; detalhes já consultados são reaproveitados por 24 horas. Ao abrir uma música ou álbum, a página completa dados antigos e consulta também os detalhes do álbum/artista relacionado. Se o Last.fm falhar, os dados salvos permanecem disponíveis e a busca informa quando está exibindo apenas o catálogo local. Consultas incompletas podem ser retomadas pesquisando novamente. Dados ausentes não são inventados; a data do texto da wiki não é tratada como data de lançamento.

Referências: [track.search](https://www.last.fm/api/show/track.search), [track.getInfo](https://www.last.fm/api/show/track.getInfo), [album.getInfo](https://www.last.fm/api/show/album.getInfo), [artist.getInfo](https://www.last.fm/api/show/artist.getInfo).
