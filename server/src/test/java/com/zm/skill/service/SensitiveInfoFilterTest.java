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
        String input = "\u8fde\u63a5 10.0.1.5:3306 \u6570\u636e\u5e93";
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
        String input = "\u8fde\u63a5\u5b57\u7b26\u4e32 jdbc:postgresql://db-host.internal:5432/mydb?user=admin";
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
        String input = "\u8bbf\u95ee http://192.168.1.100/admin";
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
        String input = "\u6e05\u7b97\u89c4\u5219\u662f T+1";
        String result = filter.filter(input);
        assertThat(result).isEqualTo("\u6e05\u7b97\u89c4\u5219\u662f T+1");
    }

    @Test
    void shouldPreserveNormalUrls() {
        String input = "\u53c2\u8003\u6587\u6863 https://docs.example.com/api";
        String result = filter.filter(input);
        assertThat(result).isEqualTo("\u53c2\u8003\u6587\u6863 https://docs.example.com/api");
    }

    @Test
    void shouldFilterMultipleSensitiveItems() {
        String input = "\u8fde\u63a5 10.0.1.5:3306 \u5bc6\u7801 password=secret123 \u8bbf\u95ee http://192.168.1.1/admin";
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

    // P0-12: New pattern tests

    @Test
    void shouldFilterChinaMobilePhone() {
        String input = "\u8054\u7cfb\u7535\u8bdd 13812345678 \u6216 15900001111";
        String result = filter.filter(input);
        assertThat(result).doesNotContain("13812345678");
        assertThat(result).doesNotContain("15900001111");
        assertThat(result).contains("{FILTERED}");
    }

    @Test
    void shouldNotFilterNonPhoneNumbers() {
        // 12-digit number should not match phone pattern
        String input = "\u8ba2\u5355\u53f7 123456789012";
        String result = filter.filter(input);
        // This is not a phone number (starts with 1 but is 12 digits)
        // The phone regex requires exactly 11 digits starting with 1[3-9]
        assertThat(result).contains("123456789012");
    }

    @Test
    void shouldFilterChinaIdCard() {
        String input = "\u8eab\u4efd\u8bc1\u53f7 110101199001011234";
        String result = filter.filter(input);
        assertThat(result).doesNotContain("110101199001011234");
        assertThat(result).contains("{FILTERED}");
    }

    @Test
    void shouldFilterChinaIdCardWithX() {
        String input = "\u8eab\u4efd\u8bc1\u53f7 11010119900101123X";
        String result = filter.filter(input);
        assertThat(result).doesNotContain("11010119900101123X");
        assertThat(result).contains("{FILTERED}");
    }

    @Test
    void shouldFilterEmail() {
        String input = "\u8054\u7cfb\u90ae\u7bb1 user@example.com \u6216 test.name+tag@company.co.jp";
        String result = filter.filter(input);
        assertThat(result).doesNotContain("user@example.com");
        assertThat(result).doesNotContain("test.name+tag@company.co.jp");
        assertThat(result).contains("{FILTERED}");
    }

    @Test
    void shouldFilterBankCard() {
        String input = "\u94f6\u884c\u5361\u53f7 6222021234567890123";
        String result = filter.filter(input);
        assertThat(result).doesNotContain("6222021234567890123");
        assertThat(result).contains("{FILTERED}");
    }

    @Test
    void shouldFilterBankCard16Digits() {
        String input = "\u5361\u53f7 4111111111111111";
        String result = filter.filter(input);
        assertThat(result).doesNotContain("4111111111111111");
        assertThat(result).contains("{FILTERED}");
    }

    @Test
    void shouldFilterJwtToken() {
        String input = "token: eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c";
        String result = filter.filter(input);
        assertThat(result).doesNotContain("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9");
        assertThat(result).contains("{FILTERED}");
    }

    @Test
    void shouldFilterPrivateKey() {
        String input = "key: -----BEGIN PRIVATE KEY-----\nMIIEvgIBADANBgkqhkiG9w0BAQEFAASC\n-----END PRIVATE KEY-----";
        String result = filter.filter(input);
        assertThat(result).doesNotContain("BEGIN PRIVATE KEY");
        assertThat(result).contains("{FILTERED}");
    }

    @Test
    void shouldFilterRsaPrivateKey() {
        String input = "-----BEGIN RSA PRIVATE KEY-----\nMIICXAIBAAJBAKj34GkxF...\n-----END RSA PRIVATE KEY-----";
        String result = filter.filter(input);
        assertThat(result).doesNotContain("RSA PRIVATE KEY");
        assertThat(result).contains("{FILTERED}");
    }

    @Test
    void shouldFilterEcPrivateKey() {
        String input = "-----BEGIN EC PRIVATE KEY-----\nMHQCAQEEIIosAkK...\n-----END EC PRIVATE KEY-----";
        String result = filter.filter(input);
        assertThat(result).doesNotContain("EC PRIVATE KEY");
        assertThat(result).contains("{FILTERED}");
    }
}
