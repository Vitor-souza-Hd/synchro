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
O histórico cobre apenas scrobbles disponíveis no Last.fm. Biografias, gêneros, duração e listas completas de faixas dos álbuns
não fazem parte deste endpoint e não são inventados ou consultados separadamente.

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
