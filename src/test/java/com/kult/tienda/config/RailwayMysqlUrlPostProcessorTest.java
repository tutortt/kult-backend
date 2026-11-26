package com.kult.tienda.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.env.MockEnvironment;

class RailwayMysqlUrlPostProcessorTest {

    private final RailwayMysqlUrlPostProcessor processor = new RailwayMysqlUrlPostProcessor();

    @Test
    void convierteMysqlPublicUrlResueltaAJdbc() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("MYSQL_PUBLIC_URL", "mysql://root:secret123@shortline.proxy.rlwy.net:45590/railway");

        processor.postProcessEnvironment(env, new SpringApplication());

        assertEquals(
                "jdbc:mysql://shortline.proxy.rlwy.net:45590/railway?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&connectTimeout=10000",
                env.getProperty("spring.datasource.url"));
        assertEquals("root", env.getProperty("spring.datasource.username"));
        assertEquals("secret123", env.getProperty("spring.datasource.password"));
    }

    @Test
    void resuelvePlantillaDeRailwayCuandoExistenVariables() {
        MockEnvironment env = new MockEnvironment();
        env.getPropertySources().addLast(new MapPropertySource("railwayVars", Map.of(
                "MYSQL_PUBLIC_URL",
                "mysql://${{MYSQLUSER}}:${{MYSQLPASSWORD}}@${{RAILWAY_TCP_PROXY_DOMAIN}}:${{RAILWAY_TCP_PROXY_PORT}}/${{MYSQLDATABASE}}",
                "MYSQLUSER", "root",
                "MYSQLPASSWORD", "secret123",
                "RAILWAY_TCP_PROXY_DOMAIN", "shortline.proxy.rlwy.net",
                "RAILWAY_TCP_PROXY_PORT", "45590",
                "MYSQLDATABASE", "railway")));

        processor.postProcessEnvironment(env, new SpringApplication());

        assertEquals(
                "jdbc:mysql://shortline.proxy.rlwy.net:45590/railway?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&connectTimeout=10000",
                env.getProperty("spring.datasource.url"));
        assertEquals("root", env.getProperty("spring.datasource.username"));
        assertEquals("secret123", env.getProperty("spring.datasource.password"));
    }

    @Test
    void ignoraEnPerfilTest() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("test");
        env.setProperty("MYSQL_PUBLIC_URL", "mysql://root:secret123@shortline.proxy.rlwy.net:45590/railway");

        processor.postProcessEnvironment(env, new SpringApplication());

        assertNull(env.getProperty("spring.datasource.url"));
    }
}
