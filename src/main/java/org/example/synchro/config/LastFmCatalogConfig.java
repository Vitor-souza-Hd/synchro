package org.example.synchro.config;

import org.example.synchro.importer.LastFmHistoryClient;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import(LastFmHistoryClient.class)
public class LastFmCatalogConfig {}
