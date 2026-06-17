package org.example.synchro.config;

import de.umass.lastfm.Caller;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "lastfm")
@Getter
@Setter
public class LastFmConfig {
    private String apiKey;
    private String sharedSecret;
    private String baseUrl;

}
