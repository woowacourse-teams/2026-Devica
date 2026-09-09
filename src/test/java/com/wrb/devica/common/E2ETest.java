package com.wrb.devica.common;

import com.wrb.devica.fixture.FixtureSaver;
import io.restassured.RestAssured;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({DatabaseCleaner.class, FixtureSaver.class})
public abstract class E2ETest {

    @LocalServerPort
    private int port;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private DatabaseCleaner databaseCleaner;

    @Autowired
    protected FixtureSaver saver;

    @BeforeEach
    void setUpPortAndData() {
        RestAssured.port = port;
        databaseCleaner.clear();
    }

    @AfterEach
    void clearData() {
        databaseCleaner.clear();
    }

    protected <T> T saveWithTransaction(Supplier<T> fixture) {
        return transactionTemplate.execute(status -> fixture.get());
    }
}
