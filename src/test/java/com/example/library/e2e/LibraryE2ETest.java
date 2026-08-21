package com.example.library.e2e;

import org.junit.jupiter.api.*;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.*;
import org.openqa.selenium.support.ui.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// ║  E2E Test – Selenium ChromeDriver
// ║  13 test cases | Kiểm thử giao diện web từ đầu đến cuối
// ║
// ║  Nhóm 1 (E2E01–E2E08): Kiểm tra trang và form cơ bản
// ║  Nhóm 2 (E2E09):        UC10 – Yêu cầu mượn sách
// ║  Nhóm 3 (E2E10):        UC11 – Duyệt phiếu
// ║  Nhóm 4 (E2E11):        UC13 – Gia hạn
// ║  Nhóm 5 (E2E12):        UC12 – Trả sách
// ║  Nhóm 6 (E2E13):        UC11 – Từ chối phiếu
// ║
// ║  Lệnh chạy:
// ║    mvn verify -Pe2e                      (Chrome có giao diện)
// ║    mvn verify -Pe2e -Dheadless=true      (CI/CD, không giao diện)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LibraryE2ETest {

    @LocalServerPort int port;
    WebDriver driver;

    // ── Loan ID chia sẻ giữa các test liên tiếp ──────────────────────────
    // Tests E2E09→E2E12 tạo thành 1 chuỗi: request→approve→renew→return
    private static Long chainLoanId;

    // Test E2E13 dùng loanId riêng để test reject
    private static Long rejectLoanId;

    private static final long DEMO_PAUSE_MS =
            Long.parseLong(System.getProperty("demo.pause", "2000"));

    private void pause() {
        if (DEMO_PAUSE_MS > 0) {
            try { Thread.sleep(DEMO_PAUSE_MS); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
    }

    @BeforeEach
    void setUp() {
        boolean headless = Boolean.parseBoolean(System.getProperty("headless", "false"));
        ChromeOptions opts = new ChromeOptions();
        if (headless) opts.addArguments("--headless");
        opts.addArguments("--no-sandbox", "--disable-dev-shm-usage", "--window-size=1280,900");
        driver = new ChromeDriver(opts);
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(5));
        driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(15));
    }

    @AfterEach
    void tearDown() {
        if (driver != null) driver.quit();
    }

    private String url(String path) { return "http://localhost:" + port + path; }

    // ════════════════════════════════════════════════════════════════════════
    //  Helper methods – dùng chung cho nhiều test
    // ════════════════════════════════════════════════════════════════════════

    // Đợi trang hoàn tất load (document.readyState = "complete")
    private void waitForPageLoad() {
        new WebDriverWait(driver, Duration.ofSeconds(10)).until(d ->
                ((JavascriptExecutor) d).executeScript("return document.readyState").equals("complete"));
    }

    // Set giá trị cho <input type="date"> bằng JavaScript (đáng tin cậy hơn sendKeys)
    private void setDate(String fieldName, String isoDate) {
        WebElement input = driver.findElement(By.name(fieldName));
        ((JavascriptExecutor) driver).executeScript("arguments[0].value = arguments[1]", input, isoDate);
    }

    // Lấy loanId của phiếu cuối cùng trong bảng /loans
    private long getLastLoanId() {
        driver.get(url("/loans"));
        new WebDriverWait(driver, Duration.ofSeconds(5))
                .until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("tbody tr")));
        pause(); // demo: hiển thị bảng phiếu mượn
        List<WebElement> rows = driver.findElements(By.cssSelector("tbody tr"));
        WebElement lastRow = rows.get(rows.size() - 1);
        return Long.parseLong(lastRow.findElements(By.tagName("td")).get(0).getText().trim());
    }

    // Lấy giá trị ô (colIndex tính từ 0) của phiếu loanId trong bảng /loans
    // Cột: 0=ID, 1=Độc giả, 2=Sách, 3=Trạng thái, 4=Yêu cầu,
    //       5=Duyệt, 6=Hạn trả, 7=Ngày trả, 8=Gia hạn
    private String getLoanCell(long loanId, int colIndex) {
        driver.get(url("/loans"));
        By rowLocator = By.xpath("//tbody/tr/td[1][normalize-space()='" + loanId + "']/..");
        new WebDriverWait(driver, Duration.ofSeconds(10))
                .until(ExpectedConditions.presenceOfElementLocated(rowLocator));
        pause(); // demo: hiển thị kết quả trên bảng
        WebElement row = driver.findElement(rowLocator);
        return row.findElements(By.tagName("td")).get(colIndex).getText().trim();
    }

    // Tìm nút hành động cho phiếu loanId trong form có action khớp actionSuffix
    // Dùng khi có nhiều form cùng loanId trên 1 trang (vd: /approve có cả Duyệt và Từ chối)
    private WebElement findButton(long loanId, String actionSuffix, String buttonCss) {
        String hiddenCss = "form[action$='" + actionSuffix + "'] input[name='loanId'][value='" + loanId + "']";
        WebElement hidden = driver.findElement(By.cssSelector(hiddenCss));
        WebElement form = (WebElement) ((JavascriptExecutor) driver)
                .executeScript("return arguments[0].closest('form')", hidden);
        return form.findElement(By.cssSelector(buttonCss));
    }

    // Điền và submit form đăng ký độc giả
    private void registerReader(String id, String name, String email) {
        driver.get(url("/register"));
        driver.findElement(By.name("readerId")).sendKeys(id);
        driver.findElement(By.name("name")).sendKeys(name);
        driver.findElement(By.name("email")).sendKeys(email);
        driver.findElement(By.name("password")).sendKeys("pass123");
        driver.findElement(By.name("confirmPassword")).sendKeys("pass123");

        WebElement btn = driver.findElement(By.cssSelector("button[type=submit]"));
        ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", btn);
        new WebDriverWait(driver, Duration.ofSeconds(5)).until(ExpectedConditions.elementToBeClickable(btn));
        btn.click();
        new WebDriverWait(driver, Duration.ofSeconds(10))
                .until(d -> !d.getCurrentUrl().endsWith("/register") || d.getPageSource().contains("alert"));
    }

    // Click nút và đợi redirect hoàn thành
    private void clickAndWait(WebElement btn) {
        ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", btn);
        new WebDriverWait(driver, Duration.ofSeconds(5)).until(ExpectedConditions.elementToBeClickable(btn));
        pause(); // demo: dừng trước khi click để người xem thấy nút
        btn.click();
        // Wait for the button to become stale (page navigation started) before checking readyState,
        // to avoid the race where readyState is still "complete" from the previous page.
        try {
            new WebDriverWait(driver, Duration.ofSeconds(5)).until(ExpectedConditions.stalenessOf(btn));
        } catch (org.openqa.selenium.TimeoutException ignored) {}
        waitForPageLoad();
        pause(); // demo: dừng sau khi trang tải xong để người xem thấy kết quả
    }

    // In kết quả test ra console theo format thống nhất
    private static void log(String id, String desc, String input, String expect, String actual, String msg) {
        System.out.printf("%n[%s] %s%n", id, desc);
        System.out.printf("    Input  : %s%n", input);
        System.out.printf("    Expect : %s%n", expect);
        System.out.printf("    Actual : %s%n", actual);
        System.out.printf("    => %s  ✓ PASS%n", msg);
    }


    // ════════════════════════════════════════════════════════════════════════
    //  NHÓM 1 – Kiểm tra trang và form cơ bản (E2E01–E2E08)
    // ════════════════════════════════════════════════════════════════════════

    @Test @Order(1)
    @DisplayName("E2E01 – Trang chủ tải thành công")
    void E2E01_homePageLoads() {
        // Act
        driver.get(url("/"));
        pause();
        // Assert
        assertTrue(driver.getPageSource().contains("Thư viện") || driver.getPageSource().contains("LMS"));
        log("E2E01", "Trang chủ tải thành công",
                "GET /", "Trang có chứa 'Thư viện' hoặc 'LMS'",
                "pageSource contains expected text", "Trang chủ load đúng");
    }

    @Test @Order(2)
    @DisplayName("E2E02 – Đăng ký độc giả mới thành công [UC01]")
    void E2E02_registerSuccess() {
        // Act
        registerReader("E2E_R01", "Test User", "e2e_r01@test.com");
        // Assert: kiểm tra reader xuất hiện trong /readers
        driver.get(url("/readers"));
        pause();
        assertTrue(driver.getPageSource().contains("e2e_r01@test.com"),
                "Reader 'e2e_r01@test.com' not found in /readers after registration");
        log("E2E02", "Đăng ký độc giả mới [UC01]",
                "readerId=E2E_R01, email=e2e_r01@test.com",
                "email xuất hiện trong /readers",
                "e2e_r01@test.com found in /readers", "Đăng ký thành công");
    }

    @Test @Order(3)
    @DisplayName("E2E03 – Email trùng lặp → thông báo lỗi alert-danger [BR10]")
    void E2E03_registerDuplicateEmailShowsError() {
        // Arrange: đăng ký lần đầu
        registerReader("E2E_R03a", "User A", "dup_e2e@test.com");
        driver.get(url("/readers"));
        assertTrue(driver.getPageSource().contains("dup_e2e@test.com"));

        // Act: đăng ký lần 2 với email trùng
        driver.get(url("/register"));
        driver.findElement(By.name("readerId")).sendKeys("E2E_R03b");
        driver.findElement(By.name("name")).sendKeys("User B");
        driver.findElement(By.name("email")).sendKeys("dup_e2e@test.com");
        driver.findElement(By.name("password")).sendKeys("pass123");
        driver.findElement(By.name("confirmPassword")).sendKeys("pass123");

        WebElement btn = driver.findElement(By.cssSelector("button[type=submit]"));
        ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", btn);
        btn.click();

        // Assert: alert-danger xuất hiện với thông báo lỗi
        new WebDriverWait(driver, Duration.ofSeconds(10))
                .until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector(".alert-danger")));
        pause();
        assertTrue(driver.getPageSource().contains("Email đã tồn tại"));
        log("E2E03", "Email trùng lặp hiện lỗi [BR10]",
                "email=dup_e2e@test.com (đã đăng ký trước)",
                ".alert-danger chứa 'Email đã tồn tại'",
                ".alert-danger visible, message correct", "Hiển thị lỗi email trùng");
    }

    @Test @Order(4)
    @DisplayName("E2E04 – Form yêu cầu mượn sách có đủ các trường")
    void E2E04_requestLoanPageHasForm() {
        // Act
        driver.get(url("/request"));
        pause();
        // Assert: các trường bắt buộc phải có mặt
        assertNotNull(driver.findElement(By.name("readerId")));
        assertNotNull(driver.findElement(By.name("isbn")));
        assertNotNull(driver.findElement(By.name("requestDate")));
        log("E2E04", "Form mượn sách đủ trường",
                "GET /request", "Có select[readerId], select[isbn], input[requestDate]",
                "All 3 fields found", "Form hiển thị đầy đủ");
    }

    @Test @Order(5)
    @DisplayName("E2E05 – Trang duyệt phiếu tải thành công")
    void E2E05_approvePageLoads() {
        driver.get(url("/approve"));
        pause();
        assertTrue(driver.getPageSource().contains("Duyệt"));
        log("E2E05", "Trang /approve tải thành công",
                "GET /approve", "Trang chứa 'Duyệt'",
                "Page contains 'Duyệt'", "Trang duyệt phiếu load đúng");
    }

    @Test @Order(6)
    @DisplayName("E2E06 – Trang trả sách tải thành công")
    void E2E06_returnPageLoads() {
        driver.get(url("/return"));
        pause();
        assertTrue(driver.getPageSource().contains("Trả sách"));
        log("E2E06", "Trang /return tải thành công",
                "GET /return", "Trang chứa 'Trả sách'",
                "Page contains 'Trả sách'", "Trang trả sách load đúng");
    }

    @Test @Order(7)
    @DisplayName("E2E07 – Trang gia hạn tải thành công")
    void E2E07_renewPageLoads() {
        driver.get(url("/renew"));
        pause();
        assertTrue(driver.getPageSource().contains("Gia hạn"));
        log("E2E07", "Trang /renew tải thành công",
                "GET /renew", "Trang chứa 'Gia hạn'",
                "Page contains 'Gia hạn'", "Trang gia hạn load đúng");
    }

    @Test @Order(8)
    @DisplayName("E2E08 – Trang /readers hiển thị dữ liệu seed")
    void E2E08_readersPageShowsSeededData() {
        driver.get(url("/readers"));
        pause();
        assertTrue(driver.getPageSource().contains("Nguyễn Văn An"));
        log("E2E08", "Trang /readers hiển thị dữ liệu seed",
                "GET /readers", "Trang chứa 'Nguyễn Văn An'",
                "Page contains 'Nguyễn Văn An'", "Dữ liệu seed hiển thị đúng");
    }


    // ════════════════════════════════════════════════════════════════════════
    //  NHÓM 2 – UC10: Yêu cầu mượn sách (E2E09)
    //
    //  Dùng dữ liệu seed: R001 (Nguyễn Văn An), isbn=978-0-13-468599-1 (Clean Code)
    //  Sau test này, chainLoanId được lưu lại để E2E10–E2E12 dùng tiếp
    // ════════════════════════════════════════════════════════════════════════

    @Test @Order(9)
    @DisplayName("E2E09 – UC10: Yêu cầu mượn sách → Loan.status = PENDING")
    void E2E09_requestLoan() {
        // Arrange: dùng seeded reader R001 và sách Clean Code
        driver.get(url("/request"));
        pause();

        // Act: chọn từ dropdown và submit
        new Select(driver.findElement(By.name("readerId"))).selectByValue("R001");
        pause();
        new Select(driver.findElement(By.name("isbn"))).selectByValue("978-0-13-468599-1");
        setDate("requestDate", "2026-08-01");
        pause();

        WebElement submitBtn = driver.findElement(By.cssSelector("button[type=submit]"));
        clickAndWait(submitBtn);

        // Đợi redirect khỏi trang /request
        new WebDriverWait(driver, Duration.ofSeconds(10))
                .until(d -> !d.getCurrentUrl().endsWith("/request"));

        // Assert: phiếu mới xuất hiện trong /loans với status PENDING
        chainLoanId = getLastLoanId();
        String status  = getLoanCell(chainLoanId, 3);  // cột Trạng thái
        String reader  = getLoanCell(chainLoanId, 1);  // cột Độc giả
        String book    = getLoanCell(chainLoanId, 2);  // cột Sách

        assertEquals("PENDING",       status, "Phiếu vừa tạo phải có status PENDING");
        assertTrue(reader.contains("Nguyễn Văn An"), "Reader phải là Nguyễn Văn An");
        assertTrue(book.contains("Clean Code"),      "Sách phải là Clean Code");

        log("E2E09", "Yêu cầu mượn sách [UC10]",
                "readerId=R001 (Nguyễn Văn An), isbn=978-0-13-468599-1 (Clean Code), date=2026-08-01",
                "Loan.status = PENDING",
                "status=" + status + ", reader=" + reader + ", loanId=" + chainLoanId,
                "Tạo phiếu mượn PENDING thành công");
    }


    // ════════════════════════════════════════════════════════════════════════
    //  NHÓM 3 – UC11: Duyệt phiếu (E2E10)
    //
    //  Duyệt phiếu chainLoanId từ E2E09
    //  Kết quả: status = BORROWED, dueDate = 2026-08-01 + 14 ngày = 2026-08-15
    // ════════════════════════════════════════════════════════════════════════

    @Test @Order(10)
    @DisplayName("E2E10 – UC11: Duyệt phiếu PENDING → status=BORROWED, dueDate=approveDate+14")
    void E2E10_approveLoan() {
        // Arrange
        assertNotNull(chainLoanId, "Cần chạy E2E09 trước để có chainLoanId");

        driver.get(url("/approve"));
        new WebDriverWait(driver, Duration.ofSeconds(5))
                .until(ExpectedConditions.presenceOfElementLocated(
                        By.cssSelector("input[name='loanId'][value='" + chainLoanId + "']")));
        pause();

        // Act: set ngày duyệt = 2026-08-01, click Duyệt
        // findButton tìm button trong form có action="/approve"
        WebElement approveBtn = findButton(chainLoanId, "/approve", "button.btn-success");
        WebElement approveForm = (WebElement) ((JavascriptExecutor) driver)
                .executeScript("return arguments[0].closest('form')", approveBtn);
        ((JavascriptExecutor) driver).executeScript(
                "arguments[0].value = '2026-08-01'",
                approveForm.findElement(By.name("approveDate")));
        pause();

        clickAndWait(approveBtn);

        // Assert: status = BORROWED, dueDate = 2026-08-15 (BR02: +14 ngày)
        String status  = getLoanCell(chainLoanId, 3);
        String dueDate = getLoanCell(chainLoanId, 6);

        assertEquals("BORROWED",   status,  "Sau khi duyệt, status phải là BORROWED");
        assertEquals("2026-08-15", dueDate, "dueDate = approveDate(2026-08-01) + 14 ngày [BR02]");

        log("E2E10", "Duyệt phiếu mượn [UC11]",
                "loanId=" + chainLoanId + ", approveDate=2026-08-01",
                "status=BORROWED, dueDate=2026-08-15",
                "status=" + status + ", dueDate=" + dueDate,
                "Phiếu được duyệt đúng, hạn trả tính đúng");
    }


    // ════════════════════════════════════════════════════════════════════════
    //  NHÓM 4 – UC13: Gia hạn (E2E11)
    //
    //  Gia hạn phiếu chainLoanId (đang BORROWED)
    //  Kết quả: dueDate tăng thêm 7 ngày, cột Gia hạn = "Có"
    // ════════════════════════════════════════════════════════════════════════

    @Test @Order(11)
    @DisplayName("E2E11 – UC13: Gia hạn phiếu → dueDate += 7 ngày, renewed = Có [BR07, BR08]")
    void E2E11_renewLoan() {
        // Arrange: ghi nhận dueDate trước khi gia hạn
        assertNotNull(chainLoanId, "Cần chạy E2E09 trước để có chainLoanId");
        String dueDateBefore = getLoanCell(chainLoanId, 6);   // vd: 2026-08-15

        driver.get(url("/renew"));
        new WebDriverWait(driver, Duration.ofSeconds(5))
                .until(ExpectedConditions.presenceOfElementLocated(
                        By.cssSelector("input[name='loanId'][value='" + chainLoanId + "']")));

        // Act: click Gia hạn (+7 ngày)
        WebElement renewBtn = findButton(chainLoanId, "/renew", "button.btn-warning");
        clickAndWait(renewBtn);

        // Assert: cột Gia hạn = "Có", dueDate đã tăng lên
        String renewed     = getLoanCell(chainLoanId, 8);   // cột Gia hạn
        String dueDateAfter = getLoanCell(chainLoanId, 6);  // vd: 2026-08-22

        assertEquals("Có", renewed, "Sau gia hạn, cột Gia hạn phải là 'Có'");
        assertNotEquals(dueDateBefore, dueDateAfter, "dueDate phải tăng sau khi gia hạn");

        log("E2E11", "Gia hạn phiếu mượn [UC13]",
                "loanId=" + chainLoanId,
                "renewed=Có, dueDate tăng +7 ngày",
                "renewed=" + renewed + ", dueDate: " + dueDateBefore + " → " + dueDateAfter,
                "Gia hạn thành công: +" + java.time.temporal.ChronoUnit.DAYS.between(
                        java.time.LocalDate.parse(dueDateBefore),
                        java.time.LocalDate.parse(dueDateAfter)) + " ngày");
    }


    // ════════════════════════════════════════════════════════════════════════
    //  NHÓM 5 – UC12: Trả sách (E2E12)
    //
    //  Trả phiếu chainLoanId (đang BORROWED, đã gia hạn → dueDate=2026-08-22)
    //  returnDate = 2026-08-22 (đúng hạn) → không bị phạt
    //  Kết quả: status = RETURNED
    // ════════════════════════════════════════════════════════════════════════

    @Test @Order(12)
    @DisplayName("E2E12 – UC12: Trả sách đúng hạn → status=RETURNED, không phạt [BR03]")
    void E2E12_returnLoan() {
        // Arrange
        assertNotNull(chainLoanId, "Cần chạy E2E09 trước để có chainLoanId");

        driver.get(url("/return"));
        new WebDriverWait(driver, Duration.ofSeconds(5))
                .until(ExpectedConditions.presenceOfElementLocated(
                        By.cssSelector("input[name='loanId'][value='" + chainLoanId + "']")));

        // Act: trả ngày 2026-08-22 (đúng hạn sau gia hạn) → không phạt
        WebElement returnBtn = findButton(chainLoanId, "/return", "button.btn-primary");
        WebElement returnForm = (WebElement) ((JavascriptExecutor) driver)
                .executeScript("return arguments[0].closest('form')", returnBtn);
        ((JavascriptExecutor) driver).executeScript(
                "arguments[0].value = '2026-08-22'",
                returnForm.findElement(By.name("returnDate")));

        clickAndWait(returnBtn);

        // Assert: status = RETURNED, returnDate được điền
        String status     = getLoanCell(chainLoanId, 3);
        String returnDate = getLoanCell(chainLoanId, 7);  // cột Ngày trả

        assertEquals("RETURNED", status, "Sau khi trả sách, status phải là RETURNED");
        assertEquals("2026-08-22", returnDate, "Ngày trả phải được lưu đúng");

        log("E2E12", "Trả sách đúng hạn [UC12, BR03]",
                "loanId=" + chainLoanId + ", returnDate=2026-08-22 (= dueDate, đúng hạn)",
                "status=RETURNED, returnDate=2026-08-22",
                "status=" + status + ", returnDate=" + returnDate,
                "Trả sách thành công, không bị phạt");
    }


    // ════════════════════════════════════════════════════════════════════════
    //  NHÓM 6 – UC11: Từ chối phiếu (E2E13)
    //
    //  Tạo phiếu mới cho R002 (Design Patterns), sau đó từ chối
    //  Kết quả: status = REJECTED
    // ════════════════════════════════════════════════════════════════════════

    @Test @Order(13)
    @DisplayName("E2E13 – UC11: Từ chối phiếu PENDING → status=REJECTED")
    void E2E13_rejectLoan() {
        // Arrange: tạo một phiếu mới để từ chối
        driver.get(url("/request"));
        new Select(driver.findElement(By.name("readerId"))).selectByValue("R002");
        new Select(driver.findElement(By.name("isbn"))).selectByValue("978-0-201-63361-0");
        setDate("requestDate", "2026-08-01");

        WebElement submitBtn = driver.findElement(By.cssSelector("button[type=submit]"));
        clickAndWait(submitBtn);
        new WebDriverWait(driver, Duration.ofSeconds(10))
                .until(d -> !d.getCurrentUrl().endsWith("/request"));

        rejectLoanId = getLastLoanId();

        // Act: vào /approve, override confirm dialog, click Từ chối
        driver.get(url("/approve"));
        new WebDriverWait(driver, Duration.ofSeconds(5))
                .until(ExpectedConditions.presenceOfElementLocated(
                        By.cssSelector("input[name='loanId'][value='" + rejectLoanId + "']")));

        // Ghi đè confirm() để không hiện dialog popup
        ((JavascriptExecutor) driver).executeScript("window.confirm = function(){ return true; }");

        WebElement rejectBtn = findButton(rejectLoanId, "/reject", "button.btn-danger");
        clickAndWait(rejectBtn);

        // Assert: status = REJECTED
        String status = getLoanCell(rejectLoanId, 3);
        assertEquals("REJECTED", status, "Sau khi từ chối, status phải là REJECTED");

        log("E2E13", "Từ chối phiếu mượn [UC11]",
                "loanId=" + rejectLoanId + " (R002 – Trần Thị Bình, Design Patterns)",
                "status=REJECTED",
                "status=" + status,
                "Từ chối phiếu thành công");
    }
}
