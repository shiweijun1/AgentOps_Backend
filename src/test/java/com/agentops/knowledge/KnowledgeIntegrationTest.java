package com.agentops.knowledge;

import com.agentops.knowledge.application.KnowledgeApplicationService;
import com.agentops.knowledge.domain.*;
import com.agentops.knowledge.infrastructure.persistence.*;
import com.agentops.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;
import java.time.Instant;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.placeholders.dev_seed_enabled=true",
        "spring.flyway.placeholders.dev_admin_password_hash=$2a$12$4RjCpvCT7V6ZKG/cEWsbq.UB.s/hHLNDypgrN3CjCd1sQ.yZl1o2S",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration",
        "agentops.messaging.enabled=false",
        "agentops.agent.worker-enabled=false"
})
class KnowledgeIntegrationTest {
    @Container static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:8.4"))
            .withDatabaseName("agentops");

    @DynamicPropertySource static void mysql(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired KnowledgeApplicationService service;
    @Autowired KnowledgeArticleRepository articles;
    @Autowired KnowledgeVersionRepository versions;
    @Autowired KnowledgeChunkRepository chunks;
    @Autowired PlatformTransactionManager transactionManager;

    @Test void chineseSearchFindsPublishedCurrentChunkWithSources() {
        var article = service.createArticle("tenant-a", "星河账号指南", null, null);
        var version = service.createVersion(article.getId(), "tenant-a",
                "星河账号登录失败时，请检查账号状态与双重认证。", "reviewer");
        service.publish(article.getId(), version.getId(), "tenant-a", "reviewer");

        var hits = service.search("tenant-a", "星河", 10);
        assertThat(hits).isNotEmpty();
        assertThat(hits.getFirst().articleId()).isEqualTo(article.getId());
        assertThat(hits.getFirst().versionId()).isEqualTo(version.getId());
        assertThat(hits.getFirst().chunkId()).isNotNull();
        assertThat(hits.getFirst().title()).isEqualTo("星河账号指南");
        assertThat(hits.getFirst().snippet()).contains("星河");
        assertThat(hits.getFirst().score()).isPositive();
        assertThat(chunks.findByTenantIdAndVersionIdOrderByChunkIndex("tenant-a", version.getId())
                .getFirst().isTokenCountEstimated()).isTrue();
    }

    @Test void newVersionSupersedesOldAndRepeatedPublishDoesNotDuplicateChunks() {
        var article = service.createArticle("tenant-b", "版本切换", null, null);
        var old = service.createVersion(article.getId(), "tenant-b", "赤兔方案需要更新。", "reviewer");
        service.publish(article.getId(), old.getId(), "tenant-b", "reviewer");
        long oldChunks = chunks.countByTenantIdAndVersionId("tenant-b", old.getId());
        service.publish(article.getId(), old.getId(), "tenant-b", "reviewer");
        assertThat(chunks.countByTenantIdAndVersionId("tenant-b", old.getId())).isEqualTo(oldChunks);

        var current = service.createVersion(article.getId(), "tenant-b", "青龙方案现在生效。", "reviewer");
        service.publish(article.getId(), current.getId(), "tenant-b", "reviewer");
        assertThat(service.search("tenant-b", "赤兔", 10)).isEmpty();
        assertThat(service.search("tenant-b", "青龙", 10)).extracting("versionId").contains(current.getId());
        assertThat(versions.findById(old.getId()).orElseThrow().getReviewStatus())
                .isEqualTo(KnowledgeReviewStatus.SUPERSEDED);
        assertThat(service.getArticle(article.getId(), "tenant-b").getCurrentVersionId()).isEqualTo(current.getId());
    }

    @Test void withdrawnAndExpiredArticlesCannotBeSearched() {
        var withdrawn = service.createArticle("tenant-c", "撤回指南", null, null);
        var withdrawnVersion = service.createVersion(withdrawn.getId(), "tenant-c", "月舟系统帮助内容。", "reviewer");
        service.publish(withdrawn.getId(), withdrawnVersion.getId(), "tenant-c", "reviewer");
        service.withdraw(withdrawn.getId(), "tenant-c");
        assertThat(service.search("tenant-c", "月舟", 10)).isEmpty();

        var expired = service.createArticle("tenant-c", "过期指南", null, Instant.now().minusSeconds(60));
        var expiredVersion = service.createVersion(expired.getId(), "tenant-c", "银狐系统帮助内容。", "reviewer");
        service.publish(expired.getId(), expiredVersion.getId(), "tenant-c", "reviewer");
        assertThat(service.search("tenant-c", "银狐", 10)).isEmpty();
    }

    @Test void crossTenantCannotReadVersionsOrSearch() {
        var article = service.createArticle("tenant-owner", "隔离测试", null, null);
        var version = service.createVersion(article.getId(), "tenant-owner", "白鹭系统专属内容。", "reviewer");
        service.publish(article.getId(), version.getId(), "tenant-owner", "reviewer");
        assertThat(service.search("tenant-other", "白鹭", 10)).isEmpty();
        assertThatThrownBy(() -> service.getArticle(article.getId(), "tenant-other"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.listVersions(article.getId(), "tenant-other"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.publish(article.getId(), version.getId(), "tenant-other", "reviewer"))
                .isInstanceOf(BusinessException.class);
    }

    @Test void rollbackRevertsArticleVersionAndChunksTogether() {
        var article = service.createArticle("tenant-rollback", "回滚测试", null, null);
        var version = service.createVersion(article.getId(), "tenant-rollback", "山海系统回滚验证。", "reviewer");
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        assertThatThrownBy(() -> template.executeWithoutResult(status -> {
            service.publish(article.getId(), version.getId(), "tenant-rollback", "reviewer");
            assertThat(chunks.countByTenantIdAndVersionId("tenant-rollback", version.getId())).isPositive();
            throw new IllegalStateException("Force rollback after publishing");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(articles.findById(article.getId()).orElseThrow().getStatus()).isEqualTo(KnowledgeArticleStatus.DRAFT);
        assertThat(versions.findById(version.getId()).orElseThrow().getReviewStatus()).isEqualTo(KnowledgeReviewStatus.DRAFT);
        assertThat(chunks.countByTenantIdAndVersionId("tenant-rollback", version.getId())).isZero();
    }
}
