package com.kult.tienda.config;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.Profiles;
import org.springframework.core.env.PropertySource;

/**
 * Permite pegar directamente MYSQL_PUBLIC_URL o MYSQL_URL de Railway
 * (formato mysql://user:pass@host:port/db) en Render sin convertir manualmente a JDBC.
 * También resuelve referencias de plantilla Railway (${{VAR}}) si existen en el entorno.
 */
public class RailwayMysqlUrlPostProcessor implements EnvironmentPostProcessor {

    private static final Pattern RAILWAY_PLACEHOLDER = Pattern.compile("\\$\\{\\{\\s*([A-Za-z0-9_.-]+)\\s*}}");

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (environment.acceptsProfiles(Profiles.of("test"))) {
            return;
        }

        String explicitJdbc = getRawProperty(environment, "DB_URL");
        if (explicitJdbc != null && explicitJdbc.startsWith("jdbc:")) {
            return;
        }

        String rawUrl = firstNonBlank(
                getRawProperty(environment, "MYSQL_PUBLIC_URL"),
                getRawProperty(environment, "MYSQL_URL"),
                getRawProperty(environment, "MYSQL_PRIVATE_URL"),
                getRawProperty(environment, "DATABASE_URL"));

        if (rawUrl == null || !rawUrl.startsWith("mysql://")) {
            return;
        }

        String resolvedUrl = resolveRailwayPlaceholders(rawUrl, environment);
        if (resolvedUrl == null) {
            return;
        }

        try {
            URI uri = URI.create(resolvedUrl.trim());
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

    private static String getRawProperty(ConfigurableEnvironment environment, String key) {
        for (PropertySource<?> source : environment.getPropertySources()) {
            Object value = source.getProperty(key);
            if (value instanceof String str && !str.isBlank()) {
                return str.trim();
            }
        }
        return null;
    }

    private static String resolveRailwayPlaceholders(String rawUrl, ConfigurableEnvironment environment) {
        if (!rawUrl.contains("${{")) {
            return rawUrl;
        }
        Matcher matcher = RAILWAY_PLACEHOLDER.matcher(rawUrl);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            String value = getRawProperty(environment, key);
            if (value == null || value.isBlank()) {
                return null;
            }
            matcher.appendReplacement(sb, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
