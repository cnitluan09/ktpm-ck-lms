package com.example.library.e2e;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LibraryPlaywrightTest {

    @LocalServerPort int port;

    static Playwright playwright;
    static Browser browser;
    BrowserContext context;
    Page page;

    @BeforeAll
    static void launchBrowser() {
        playwright = Playwright.create();
        boolean headless = Boolean.parseBoolean(System.getProperty("headless", "false"));
        browser = playwright.chromium().launch(
            new BrowserType.LaunchOptions().setHeadless(headless).setSlowMo(headless ? 0 : 2000));
    }

    @AfterAll
    static void closeBrowser() {
        playwright.close();
    }

    @BeforeEach
    void newContext() {
        context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1280, 900));
        page = context.newPage();
        page.setDefaultTimeout(15000);
    }

    @AfterEach
    void closeContext() {
        context.close();
    }

    private String url(String path) { return "http://localhost:" + port + path; }

    @Test @Order(1)
    void PW01_homePageContainsBooks() {
        page.navigate(url("/"));
        assertTrue(page.content().contains("Clean Code") || page.content().contains("Thư viện"));
    }

    @Test @Order(2)
    void PW02_registerFormExists() {
        page.navigate(url("/register"));
        assertTrue(page.isVisible("form"));
        assertTrue(page.isVisible("[name=email]"));
        assertTrue(page.isVisible("[name=password]"));
    }

    @Test @Order(3)
    void PW03_registerNewUser() {
        page.navigate(url("/register"));
        page.fill("[name=readerId]", "PW_R01");
        page.fill("[name=name]", "Playwright User");
        page.fill("[name=email]", "pw_r011@test.com");
        page.fill("[name=password]", "pass123");
        page.fill("[name=confirmPassword]", "pass123");

        page.click("button[type=submit]");
        page.waitForLoadState(LoadState.NETWORKIDLE);

        // Xác nhận bằng cách kiểm tra reader có trong /readers
        page.navigate(url("/readers"));
        assertTrue(page.content().contains("pw_r01@test.com"),
            "Reader 'pw_r01@test.com' not found after registration. Current URL: " + page.url());
    }

    @Test @Order(4)
    void PW04_loansPageLoads() {
        page.navigate(url("/loans"));
        assertTrue(page.isVisible("table"));
    }

    @Test @Order(5)
    void PW05_readersPageLoads() {
        page.navigate(url("/readers"));
        assertTrue(page.content().contains("Nguyễn Văn An") || page.isVisible("table"));
    }
}
