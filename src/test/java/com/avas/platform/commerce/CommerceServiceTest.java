package com.avas.platform.commerce;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.avas.platform.project.persistence.ProjectPersistenceService;

import java.util.List;
import java.util.UUID;

import static com.avas.platform.commerce.CommerceModels.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:commerce;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.data.mongodb.repositories.type=none",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration,org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration,org.springframework.boot.autoconfigure.data.mongo.MongoRepositoriesAutoConfiguration,org.springframework.boot.actuate.autoconfigure.data.mongo.MongoHealthContributorAutoConfiguration",
        "management.health.mongo.enabled=false",
        "avas.mongo.enabled=false",
        "avas.identity.store=sql",
        "avas.commerce.test-mode=true"
})
@Transactional
class CommerceServiceTest {
    @Autowired private CommerceService service;
    @Autowired private ProjectPersistenceService projectPersistence;
    private final UUID userId = UUID.randomUUID();

    @Test
    void pricesPersistsAndRefundsTheCartAndWalletOnTheServer() {
        var checkout = service.checkout(new CheckoutRequest(List.of(
                new CartLineRequest("architect-review", 1),
                new CartLineRequest("site-consultation", 2)),
                "Rohit Samota", "rohit@example.com", "9876543210", "project-1042", null), userId, "tenant-test");

        assertThat(checkout.order().total()).isEqualTo(30_000);
        assertThat(checkout.order().status()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(checkout.payment().mode()).isEqualTo("TEST");

        var receipt = service.simulate(checkout.payment().id(), userId);
        assertThat(receipt.order().status()).isEqualTo(OrderStatus.PAID);
        assertThat(service.orders(userId)).hasSize(1);

        var refund = service.refund(receipt.order().id(), new RefundRequest("Changed project scope"), userId);
        assertThat(refund.status()).isEqualTo("SUCCEEDED");
        assertThat(service.order(receipt.order().id(), userId).order().status()).isEqualTo(OrderStatus.REFUNDED);
    }

    @Test
    void creditsTheWalletOnlyAfterAValidatedTopUp() {
        var checkout = service.createTopUp(new WalletTopUpRequest(5_000), userId, "tenant-test", "buyer@example.com");
        assertThat(service.wallet(userId).balance()).isZero();
        service.simulate(checkout.payment().id(), userId);
        assertThat(service.wallet(userId).balance()).isEqualTo(5_000);
        assertThat(service.walletHistory(userId)).singleElement().satisfies(item -> {
            assertThat(item.type()).isEqualTo("CREDIT");
            assertThat(item.amount()).isEqualTo(5_000);
        });
    }

    @Test
    void rejectsProductsThatAreNotInTheServerCatalog() {
        assertThatThrownBy(() -> service.checkout(new CheckoutRequest(
                List.of(new CartLineRequest("client-invented-product", 1)),
                "Buyer", "buyer@example.com", null, null, null), userId, "tenant-test"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Unknown product");
    }

    @Test
    void startsWithoutPreloadedSampleProjectState() {
        assertThat(projectPersistence.loadStates()).isEmpty();
    }
}
