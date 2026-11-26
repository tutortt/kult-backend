package com.kult.tienda.config;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.Profiles;

/**
 * Permite pegar directamente MYSQL_PUBLIC_URL o MYSQL_URL de Railway
 * (formato mysql://user:pass@host:port/db) en Render sin convertir manualmente a JDBC.
 */
public class RailwayMysqlUrlPostProcessor implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (environment.acceptsProfiles(Profiles.of("test"))) {
            return;
        }

        String explicitJdbc = environment.getProperty("DB_URL");
        if (explicitJdbc != null && explicitJdbc.startsWith("jdbc:")) {
            return;
        }

        String rawUrl = firstNonBlank(
                environment.getProperty("MYSQL_PUBLIC_URL"),
                environment.getProperty("MYSQL_URL"),
                environment.getProperty("DATABASE_URL"));

        if (rawUrl == null || !rawUrl.startsWith("mysql://")) {
            return;
        }

        try {
            URI uri = URI.create(rawUrl.trim());
            String host = uri.getHost();
            int port = uri.getPort() > 0 ? uri.getPort() : 3306;
            String path = uri.getPath();
            String database = (path != null && path.length() > 1) ? path.substring(1) : "railway";
            if (host == null || host.isBlank()) {
                return;
            }

            Map<String, Object> props = new LinkedHashMap<>();
            props.put(
                    "spring.datasource.url",
                    "jdbc:mysql://" + host + ":" + port + "/" + database
                            + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&connectTimeout=10000");

            String userInfo = uri.getRawUserInfo();
            if (userInfo != null && !userInfo.isBlank()) {
                String[] parts = userInfo.split(":", 2);
                props.put("spring.datasource.username", decode(parts[0]));
                if (parts.length > 1) {
                    props.put("spring.datasource.password", decode(parts[1]));
                }
            }

            environment.getPropertySources().addFirst(new MapPropertySource("railwayMysqlUrl", props));
        } catch (IllegalArgumentException ignored) {
            // Si la URL no es válida, Spring usará las propiedades individuales habituales.
        }
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        }
        return null;
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
