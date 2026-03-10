package com.zm.skill.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SensitiveInfoFilterTest {

    private SensitiveInfoFilter filter;

    @BeforeEach
    void setUp() {
        filter = new SensitiveInfoFilter();
    }

    @Test
    void shouldFilterIpAddresses() {
        String input = "连接 10.0.1.5:3306 数据库";
        String result = filter.filter(input);
        assertThat(result).contains("{FILTERED}");
        assertThat(result).doesNotContain("10.0.1.5");
    }

    @Test
    void shouldFilterIpWithPort() {
        String input = "Server at 192.168.1.100:8080 is down";
        String result = filter.filter(input);
        assertThat(result).contains("{FILTERED}");
        assertThat(result).doesNotContain("192.168.1.100");
    }

    @Test
    void shouldFilterDbUrls() {
        String input = "jdbc:mysql://10.0.1.5/db";
        String result = filter.filter(input);
        assertThat(result).contains("{FILTERED}");
        assertThat(result).doesNotContain("jdbc:mysql");
    }

    @Test
    void shouldFilterPostgresUrl() {
        String input = "连接字符串 jdbc:postgresql://db-host.internal:5432/mydb?user=admin";
        String result = filter.filter(input);
        assertThat(result).contains("{FILTERED}");
        assertThat(result).doesNotContain("jdbc:postgresql");
    }

    @Test
    void shouldFilterMongoUrl() {
        String input = "mongodb://admin:password123@mongo.internal:27017/skilldb";
        String result = filter.filter(input);
        assertThat(result).contains("{FILTERED}");
        assertThat(result).doesNotContain("mongodb://");
    }

    @Test
    void shouldFilterTokens() {
        String input = "api_key=sk-abc123def456";
        String result = filter.filter(input);
        assertThat(result).contains("{FILTERED}");
        assertThat(result).doesNotContain("sk-abc123def456");
    }

    @Test
    void shouldFilterApiKeyVariations() {
        assertThat(filter.filter("API_KEY=secret123")).contains("{FILTERED}");
        assertThat(filter.filter("apikey: mykey")).contains("{FILTERED}");
        assertThat(filter.filter("access_token=bearer_xyz")).contains("{FILTERED}");
        assertThat(filter.filter("password=mysecret")).contains("{FILTERED}");
        assertThat(filter.filter("secret_key = abcdef")).contains("{FILTERED}");
    }

    @Test
    void shouldFilterInternalUrls() {
        String input = "访问 http://192.168.1.100/admin";
        String result = filter.filter(input);
        assertThat(result).contains("{FILTERED}");
        assertThat(result).doesNotContain("192.168.1.100");
    }

    @Test
    void shouldFilterInternalHostnames() {
        String input = "Deploy to http://app.internal.company.com/api";
        String result = filter.filter(input);
        assertThat(result).contains("{FILTERED}");
        assertThat(result).doesNotContain("internal.company.com");
    }

    @Test
    void shouldFilterAwsKeys() {
        String input = "AWS_ACCESS_KEY_ID=AKIAIOSFODNN7EXAMPLE";
        String result = filter.filter(input);
        assertThat(result).contains("{FILTERED}");
        assertThat(result).doesNotContain("AKIAIOSFODNN7EXAMPLE");
    }

    @Test
    void shouldFilterAwsSecretKeys() {
        String input = "aws_secret_access_key=wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY";
        String result = filter.filter(input);
        assertThat(result).contains("{FILTERED}");
    }

    @Test
    void shouldPreserveNormalContent() {
        String input = "清算规则是 T+1";
        String result = filter.filter(input);
        assertThat(result).isEqualTo("清算规则是 T+1");
    }

    @Test
    void shouldPreserveNormalUrls() {
        String input = "参考文档 https://docs.example.com/api";
        String result = filter.filter(input);
        assertThat(result).isEqualTo("参考文档 https://docs.example.com/api");
    }

    @Test
    void shouldFilterMultipleSensitiveItems() {
        String input = "连接 10.0.1.5:3306 密码 password=secret123 访问 http://192.168.1.1/admin";
        String result = filter.filter(input);
        assertThat(result).doesNotContain("10.0.1.5");
        assertThat(result).doesNotContain("secret123");
        assertThat(result).doesNotContain("192.168.1.1");
    }

    @Test
    void shouldHandleNullInput() {
        assertThat(filter.filter(null)).isNull();
    }

    @Test
    void shouldHandleEmptyInput() {
        assertThat(filter.filter("")).isEmpty();
    }
}
