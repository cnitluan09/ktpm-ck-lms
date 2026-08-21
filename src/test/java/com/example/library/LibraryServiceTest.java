package com.example.library;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.LocalDate;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;

//
// ║  Unit Test – LibraryService
// ║  40 test cases | JUnit 5 + @DataJpaTest + H2 in-memory
// ║
// ║  Mỗi test theo cấu trúc AAA:
// ║    ARRANGE – chuẩn bị dữ liệu
// ║    ACT     – gọi phương thức cần kiểm tra
// ║    ASSERT  – so sánh kết quả thực tế vs mong muốn
// ║    LOG     – in thông tin ra console
//
@DataJpaTest
@Import({LibraryService.class})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LibraryServiceTest {

    @Autowired LibraryService   service;
    @Autowired BookRepository   bookRepo;
    @Autowired ReaderRepository readerRepo;

    // Format Ngày để test cho kết quả đồng nhất
    private static final LocalDate TODAY = LocalDate.of(2024, 6, 1);

    // ── In kết quả ra console theo format  ──────────────────────
    private static void log(String id, String desc,
                            String input, String expect, String actual, String msg) {
        System.out.printf("%n[%s] %s%n", id, desc);
        System.out.printf("    Input  : %s%n", input);
        System.out.printf("    Expect : %s%n", expect);
        System.out.printf("    Actual : %s%n", actual);
        System.out.printf("    => %s  ✓ PASS%n", msg);
    }

    // ── Tạo sách mẫu ─────────────────────────────────────────────────────
    private Book makeBook(String isbn, int copies) {
        return bookRepo.save(new Book(isbn, "Tiêu đề " + isbn, "Tác giả", copies));
    }

    // ── Tạo độc giả mẫu ──────────────────────────────────────────────────
    private Reader makeReader(String id, String email) {
        return readerRepo.save(new Reader(id, "Tên " + id, email));
    }

    // ── Tạo phiếu đã duyệt (dùng cho nhóm E, F) ─────────────────────────
    private Loan approvedLoan(String isbn, String readerId, String email) {
        makeBook(isbn, 2);
        makeReader(readerId, email);
        Loan loan = service.requestLoan(readerId, isbn, TODAY);
        return service.approveLoan(loan.getId(), TODAY);
    }


    //
    //  NHÓM A – Book Entity (TC01–TC06)
    //  Kiểm tra logic của Book: tồn kho, tăng/giảm, constructor
    //

    @Test @Order(1)
    @DisplayName("TC01 – Sách còn tồn kho → isAvailable() = true [BR05]")
    void TC01_bookIsAvailableWhenCopiesGT0() {
        // Arrange
        Book book = makeBook("B01", 2);
        // Act
        boolean result = book.isAvailable();
        // Assert
        assertTrue(result);
        // Log
        log("TC01", "Sách còn tồn kho [BR05]",
            "isbn=B01, availableCopies=2",
            "isAvailable() = true",
            "isAvailable() = " + result,
            "Sách còn hàng, có thể mượn");
    }

    @Test @Order(2)
    @DisplayName("TC02 – Sách hết tồn kho → isAvailable() = false [BR05]")
    void TC02_bookNotAvailableWhenCopiesEQ0() {
        // Arrange
        Book book = makeBook("B02", 1);
        book.decreaseAvailable();           // giảm 1 → availableCopies = 0
        // Act
        boolean result = book.isAvailable();
        // Assert
        assertFalse(result);
        // Log
        log("TC02", "Sách hết tồn kho [BR05]",
            "isbn=B02, totalCopies=1 → decreaseAvailable()",
            "isAvailable() = false",
            "isAvailable() = " + result,
            "Sách hết hàng, không thể mượn");
    }

    @Test @Order(3)
    @DisplayName("TC03 – decreaseAvailable() khi hết hàng → ném IllegalStateException [BR05]")
    void TC03_decreaseAvailableThrowsWhenZero() {
        // Arrange
        Book book = makeBook("B03", 0);     // bắt đầu đã = 0
        // Act + Assert: gọi giảm khi đã hết phải ném exception
        IllegalStateException ex = assertThrows(
            IllegalStateException.class, book::decreaseAvailable);
        // Log
        log("TC03", "decreaseAvailable() khi availableCopies=0 [BR05]",
            "isbn=B03, availableCopies=0",
            "IllegalStateException: \"Sách đã hết\"",
            "Exception: " + ex.getMessage(),
            "Ném đúng exception khi hết sách");
    }

    @Test @Order(4)
    @DisplayName("TC04 – increaseAvailable() khôi phục tồn kho sau khi trả sách")
    void TC04_increaseAvailableRestoresStock() {
        // Arrange
        Book book = makeBook("B04", 1);
        book.decreaseAvailable();           // mượn đi: availableCopies = 0
        // Act
        book.increaseAvailable();           // trả lại: availableCopies = 1
        // Assert
        assertEquals(1, book.getAvailableCopies());
        // Log
        log("TC04", "increaseAvailable() khôi phục tồn kho",
            "isbn=B04, totalCopies=1 → decrease → increase",
            "availableCopies = 1",
            "availableCopies = " + book.getAvailableCopies(),
            "Tồn kho được khôi phục đúng");
    }

    @Test @Order(5)
    @DisplayName("TC05 – increaseAvailable() khi đã đủ tồn kho → ném IllegalStateException")
    void TC05_increaseAvailableThrowsWhenFull() {
        // Arrange: availableCopies = totalCopies = 1 (không thể tăng thêm)
        Book book = makeBook("B05", 1);
        // Act + Assert
        IllegalStateException ex = assertThrows(
            IllegalStateException.class, book::increaseAvailable);
        // Log
        log("TC05", "increaseAvailable() khi tồn kho đã đầy",
            "isbn=B05, availableCopies=totalCopies=1",
            "IllegalStateException",
            "Exception: " + ex.getMessage(),
            "Ném đúng exception khi tồn kho đã đầy");
    }

    @Test @Order(6)
    @DisplayName("TC06 – Constructor Book gán đúng tất cả field")
    void TC06_bookConstructorSetsAllFields() {
        // Arrange + Act
        Book book = new Book("B06", "Clean Code", "Martin", 3);
        // Assert
        assertEquals("B06", book.getIsbn());
        assertEquals(3,     book.getTotalCopies());
        assertEquals(3,     book.getAvailableCopies());
        // Log
        log("TC06", "Constructor Book",
            "isbn=B06, title=Clean Code, author=Martin, totalCopies=3",
            "isbn=B06, totalCopies=3, availableCopies=3",
            "isbn=" + book.getIsbn() + ", totalCopies=" + book.getTotalCopies()
                + ", availableCopies=" + book.getAvailableCopies(),
            "Tất cả field được gán đúng");
    }


    //
    //  NHÓM B – registerReader / UC01 (TC07–TC14)
    //  Kiểm tra đăng ký độc giả: validate email, mật khẩu, tên
    //

    @Test @Order(7)
    @DisplayName("TC07 – Đăng ký độc giả hợp lệ → thành công [UC01]")
    void TC07_registerSuccess() {
        // Act
        Reader reader = service.registerReader(
            "R07", "Nguyen A", "r07@mail.com", "pass123", "pass123");
        // Assert
        assertNotNull(reader);
        assertEquals("r07@mail.com", reader.getEmail());
        // Log
        log("TC07", "Đăng ký độc giả hợp lệ [UC01]",
            "readerId=R07, name=Nguyen A, email=r07@mail.com, password=pass123",
            "Trả về Reader với email=r07@mail.com",
            "reader.id=" + reader.getReaderId() + ", email=" + reader.getEmail(),
            "Đăng ký thành công");
    }

    @Test @Order(8)
    @DisplayName("TC08 – Email sai định dạng → ném IllegalArgumentException [BR10]")
    void TC08_registerInvalidEmailFormat() {
        // Act + Assert
        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> service.registerReader("R08", "Name", "notAnEmail", "pass123", "pass123"));
        assertTrue(ex.getMessage().contains("Email không đúng định dạng"));
        // Log
        log("TC08", "Email sai định dạng [BR10]",
            "email=\"notAnEmail\" (không có @)",
            "IllegalArgumentException: \"Email không đúng định dạng\"",
            "Exception: " + ex.getMessage(),
            "Validate email đúng định dạng");
    }

    @Test @Order(9)
    @DisplayName("TC09 – Email null → ném IllegalArgumentException [BR10]")
    void TC09_registerNullEmail() {
        // Act + Assert
        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> service.registerReader("R09", "Name", null, "pass123", "pass123"));
        // Log
        log("TC09", "Email null [BR10]",
            "email=null",
            "IllegalArgumentException",
            "Exception: " + ex.getMessage(),
            "Bắt được email null");
    }

    @Test @Order(10)
    @DisplayName("TC10 – Email đã tồn tại → ném IllegalArgumentException [BR10]")
    void TC10_registerDuplicateEmail() {
        // Arrange: đăng ký lần đầu
        service.registerReader("R10a", "Name A", "dup@mail.com", "pass123", "pass123");
        // Act + Assert: đăng ký lần 2 cùng email phải lỗi
        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> service.registerReader("R10b", "Name B", "dup@mail.com", "pass123", "pass123"));
        assertTrue(ex.getMessage().contains("Email đã tồn tại"));
        // Log
        log("TC10", "Email trùng lặp [BR10]",
            "email=dup@mail.com (đã đăng ký trước)",
            "IllegalArgumentException: \"Email đã tồn tại\"",
            "Exception: " + ex.getMessage(),
            "Phát hiện email trùng lặp");
    }

    @Test @Order(11)
    @DisplayName("TC11 – Mật khẩu < 6 ký tự → ném IllegalArgumentException [BR06]")
    void TC11_registerPasswordTooShort() {
        // Act + Assert
        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> service.registerReader("R11", "Name", "r11@mail.com", "abc", "abc"));
        assertTrue(ex.getMessage().contains("Mật khẩu tối thiểu 6 ký tự"));
        // Log
        log("TC11", "Mật khẩu quá ngắn [BR06]",
            "password=\"abc\" (3 ký tự)",
            "IllegalArgumentException: \"Mật khẩu tối thiểu 6 ký tự\"",
            "Exception: " + ex.getMessage(),
            "Validate độ dài mật khẩu đúng");
    }

    @Test @Order(12)
    @DisplayName("TC12 – Xác nhận mật khẩu không khớp → ném IllegalArgumentException")
    void TC12_registerPasswordMismatch() {
        // Act + Assert
        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> service.registerReader("R12", "Name", "r12@mail.com", "pass123", "pass999"));
        assertTrue(ex.getMessage().contains("Xác nhận mật khẩu không khớp"));
        // Log
        log("TC12", "Xác nhận mật khẩu không khớp",
            "password=pass123, confirmPassword=pass999",
            "IllegalArgumentException: \"Xác nhận mật khẩu không khớp\"",
            "Exception: " + ex.getMessage(),
            "Phát hiện mật khẩu không khớp");
    }

    @Test @Order(13)
    @DisplayName("TC13 – Tên > 255 ký tự → ném IllegalArgumentException [BR09]")
    void TC13_registerNameTooLong() {
        // Arrange
        String longName = "A".repeat(256);  // 256 ký tự, vượt giới hạn 255
        // Act + Assert
        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> service.registerReader("R13", longName, "r13@mail.com", "pass123", "pass123"));
        // Log
        log("TC13", "Tên vượt 255 ký tự [BR09]",
            "name = 256 ký tự 'A'",
            "IllegalArgumentException: \"Tên vượt quá 255 ký tự\"",
            "Exception: " + ex.getMessage(),
            "Validate độ dài tên đúng");
    }

    @Test @Order(14)
    @DisplayName("TC14 – Mật khẩu đúng 6 ký tự (boundary) → đăng ký thành công [BR06]")
    void TC14_registerPasswordExactly6Chars() {
        // Arrange: đúng điểm biên dưới của BR06 (password ≥ 6 ký tự)
        String password = "123456";
        // Act
        Reader reader = service.registerReader(
            "R14", "Name", "r14@mail.com", password, password);
        // Assert
        assertNotNull(reader);
        // Log
        log("TC14", "Mật khẩu biên 6 ký tự [BR06]",
            "password=\"123456\" (đúng 6 ký tự)",
            "Đăng ký thành công",
            "reader.id=" + reader.getReaderId() + " được tạo",
            "Biên giới 6 ký tự được chấp nhận");
    }


    //
    //  NHÓM C – requestLoan / UC10 (TC15–TC23)
    //  Kiểm tra yêu cầu mượn sách: giới hạn số phiếu, nợ, tồn kho
    //

    @Test @Order(15)
    @DisplayName("TC15 – Yêu cầu mượn sách hợp lệ → Loan.status = PENDING [UC10]")
    void TC15_requestLoanSuccess() {
        // Arrange
        makeBook("ISBN15", 2);
        makeReader("R15", "r15@mail.com");
        // Act
        Loan loan = service.requestLoan("R15", "ISBN15", TODAY);
        // Assert
        assertEquals(LoanStatus.PENDING, loan.getStatus());
        // Log
        log("TC15", "Tạo phiếu mượn PENDING [UC10]",
            "readerId=R15, isbn=ISBN15, requestDate=" + TODAY,
            "Loan.status = PENDING",
            "Loan.id=" + loan.getId() + ", status=" + loan.getStatus(),
            "Tạo phiếu mượn PENDING thành công");
    }

    @Test @Order(16)
    @DisplayName("TC16 – readerId null → ném IllegalArgumentException")
    void TC16_requestLoanNullReader() {
        // Act + Assert
        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> service.requestLoan(null, "ISBN16", TODAY));
        // Log
        log("TC16", "readerId null",
            "readerId=null",
            "IllegalArgumentException: \"Độc giả không được null\"",
            "Exception: " + ex.getMessage(),
            "Bắt được readerId null");
    }

    @Test @Order(17)
    @DisplayName("TC17 – requestDate null → ném IllegalArgumentException")
    void TC17_requestLoanNullDate() {
        // Arrange
        makeReader("R17", "r17@mail.com");
        // Act + Assert
        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> service.requestLoan("R17", "ISBN17", null));
        // Log
        log("TC17", "requestDate null",
            "requestDate=null",
            "IllegalArgumentException: \"Ngày yêu cầu không được null\"",
            "Exception: " + ex.getMessage(),
            "Bắt được requestDate null");
    }

    @Test @Order(18)
    @DisplayName("TC18 – ISBN không tồn tại → ném NoSuchElementException")
    void TC18_requestLoanBookNotFound() {
        // Arrange
        makeReader("R18", "r18@mail.com");
        // Act + Assert
        NoSuchElementException ex = assertThrows(
            NoSuchElementException.class,
            () -> service.requestLoan("R18", "NOTFOUND", TODAY));
        // Log
        log("TC18", "ISBN không tồn tại",
            "isbn=NOTFOUND (không có trong DB)",
            "NoSuchElementException",
            "Exception: " + ex.getMessage(),
            "Phát hiện sách không tồn tại");
    }

    @Test @Order(19)
    @DisplayName("TC19 – Đã có 5 phiếu hiệu lực → từ chối phiếu thứ 6 [BR01]")
    void TC19_requestLoanBR01MaxFiveLoans() {
        // Arrange: tạo đủ 5 phiếu PENDING
        makeBook("ISBN19", 10);
        makeReader("R19", "r19@mail.com");
        for (int i = 0; i < 5; i++) {
            service.requestLoan("R19", "ISBN19", TODAY);
        }
        // Act + Assert: phiếu thứ 6 phải bị từ chối
        IllegalStateException ex = assertThrows(
            IllegalStateException.class,
            () -> service.requestLoan("R19", "ISBN19", TODAY));
        assertTrue(ex.getMessage().contains("5 cuốn"));
        // Log
        log("TC19", "Giới hạn 5 phiếu [BR01]",
            "R19 đã có 5 phiếu PENDING → tạo phiếu thứ 6",
            "IllegalStateException: \"đã mượn đủ 5 cuốn\"",
            "Exception: " + ex.getMessage(),
            "BR01 giới hạn 5 phiếu hiệu lực hoạt động đúng");
    }

    @Test @Order(20)
    @DisplayName("TC20 – Nợ phạt > 50.000đ → từ chối mượn [BR04]")
    void TC20_requestLoanBR04DebtOver50000() {
        // Arrange: gán nợ 51.000đ cho reader
        makeBook("ISBN20", 5);
        Reader reader = makeReader("R20", "r20@mail.com");
        reader.addDebt(51_000);
        readerRepo.save(reader);
        // Act + Assert
        IllegalStateException ex = assertThrows(
            IllegalStateException.class,
            () -> service.requestLoan("R20", "ISBN20", TODAY));
        assertTrue(ex.getMessage().contains("50.000"));
        // Log
        log("TC20", "Nợ phạt > 50.000đ [BR04]",
            "R20 có nợ phạt = 51.000đ (> 50.000đ)",
            "IllegalStateException: \"nợ phạt vượt ngưỡng\"",
            "Exception: " + ex.getMessage(),
            "BR04 kiểm tra nợ phạt hoạt động đúng");
    }

    @Test @Order(21)
    @DisplayName("TC21 – Sách hết tồn kho → từ chối mượn [BR05]")
    void TC21_requestLoanBR05NoStock() {
        // Arrange: sách đã hết
        makeBook("ISBN21", 0);
        makeReader("R21", "r21@mail.com");
        // Act + Assert
        IllegalStateException ex = assertThrows(
            IllegalStateException.class,
            () -> service.requestLoan("R21", "ISBN21", TODAY));
        // Log
        log("TC21", "Sách hết tồn kho [BR05]",
            "isbn=ISBN21, availableCopies=0",
            "IllegalStateException: \"đã hết\"",
            "Exception: " + ex.getMessage(),
            "BR05 kiểm tra tồn kho hoạt động đúng");
    }

    @Test @Order(22)
    @DisplayName("TC22 – Tạo phiếu mượn → availableCopies giảm 1 [BR05]")
    void TC22_requestLoanDecrementsAvailableCopies() {
        // Arrange
        makeBook("ISBN22", 3);
        makeReader("R22", "r22@mail.com");
        // Act
        service.requestLoan("R22", "ISBN22", TODAY);
        // Assert
        Book book = bookRepo.findById("ISBN22").orElseThrow();
        assertEquals(2, book.getAvailableCopies());
        // Log
        log("TC22", "Mượn sách giảm tồn kho [BR05]",
            "isbn=ISBN22, availableCopies=3 → requestLoan()",
            "availableCopies = 2",
            "availableCopies = " + book.getAvailableCopies(),
            "Tồn kho giảm đúng sau khi tạo phiếu");
    }

    @Test @Order(23)
    @DisplayName("TC23 – Nợ phạt = đúng 50.000đ (boundary) → được phép mượn [BR04]")
    void TC23_requestLoanDebtExactly50000IsAllowed() {
        // Arrange: đúng điểm biên trên của BR04 (debt ≤ 50.000)
        makeBook("ISBN23", 2);
        Reader reader = makeReader("R23", "r23@mail.com");
        reader.addDebt(50_000);
        readerRepo.save(reader);
        // Act
        Loan loan = service.requestLoan("R23", "ISBN23", TODAY);
        // Assert
        assertNotNull(loan);
        // Log
        log("TC23", "Nợ phạt biên 50.000đ [BR04]",
            "R23 có nợ phạt = 50.000đ (boundary)",
            "Loan được tạo thành công",
            "Loan.id=" + loan.getId() + " được tạo",
            "Biên giới 50.000đ được chấp nhận");
    }


    //
    //  NHÓM D – approveLoan / rejectLoan / UC11 (TC24–TC27)
    //  Kiểm tra duyệt và từ chối phiếu mượn
    //

    @Test @Order(24)
    @DisplayName("TC24 – Duyệt phiếu → dueDate = approveDate + 14 ngày [BR02]")
    void TC24_approveLoanSetsDueDatePlus14() {
        // Arrange
        makeBook("ISBN24", 1);
        makeReader("R24", "r24@mail.com");
        Loan pending = service.requestLoan("R24", "ISBN24", TODAY);
        // Act
        Loan approved = service.approveLoan(pending.getId(), TODAY);
        // Assert
        assertEquals(LoanStatus.BORROWED,      approved.getStatus());
        assertEquals(TODAY.plusDays(14),        approved.getDueDate());
        // Log
        log("TC24", "Duyệt phiếu tính hạn trả [BR02]",
            "approveDate=" + TODAY,
            "status=BORROWED, dueDate=" + TODAY.plusDays(14),
            "status=" + approved.getStatus() + ", dueDate=" + approved.getDueDate(),
            "BR02 tính hạn trả đúng: approveDate + 14 ngày");
    }

    @Test @Order(25)
    @DisplayName("TC25 – Từ chối phiếu → tồn kho được hoàn trả [UC11]")
    void TC25_rejectLoanRestoresStock() {
        // Arrange: sau khi requestLoan, availableCopies = 0
        makeBook("ISBN25", 1);
        makeReader("R25", "r25@mail.com");
        Loan loan = service.requestLoan("R25", "ISBN25", TODAY);
        // Act
        service.rejectLoan(loan.getId());
        // Assert: hoàn trả → availableCopies = 1
        Book book = bookRepo.findById("ISBN25").orElseThrow();
        assertEquals(1, book.getAvailableCopies());
        // Log
        log("TC25", "Từ chối phiếu hoàn trả tồn kho [UC11]",
            "phiếu PENDING của ISBN25 (availableCopies=0) → rejectLoan()",
            "availableCopies = 1 (hoàn trả)",
            "availableCopies = " + book.getAvailableCopies(),
            "Tồn kho được hoàn trả khi từ chối phiếu");
    }

    @Test @Order(26)
    @DisplayName("TC26 – Duyệt phiếu đã BORROWED → ném IllegalStateException")
    void TC26_approveAlreadyBorrowedThrows() {
        // Arrange: duyệt lần đầu
        makeBook("ISBN26", 1);
        makeReader("R26", "r26@mail.com");
        Loan loan = service.requestLoan("R26", "ISBN26", TODAY);
        service.approveLoan(loan.getId(), TODAY);
        // Act + Assert: duyệt lần 2 phải lỗi
        IllegalStateException ex = assertThrows(
            IllegalStateException.class,
            () -> service.approveLoan(loan.getId(), TODAY));
        // Log
        log("TC26", "Duyệt phiếu đã BORROWED",
            "Loan #" + loan.getId() + " đã BORROWED → approveLoan() lần 2",
            "IllegalStateException: \"không ở trạng thái Chờ duyệt\"",
            "Exception: " + ex.getMessage(),
            "Không thể duyệt phiếu đã được duyệt");
    }

    @Test @Order(27)
    @DisplayName("TC27 – Từ chối phiếu đã BORROWED → ném IllegalStateException")
    void TC27_rejectAlreadyBorrowedThrows() {
        // Arrange
        makeBook("ISBN27", 1);
        makeReader("R27", "r27@mail.com");
        Loan loan = service.requestLoan("R27", "ISBN27", TODAY);
        service.approveLoan(loan.getId(), TODAY);
        // Act + Assert
        IllegalStateException ex = assertThrows(
            IllegalStateException.class,
            () -> service.rejectLoan(loan.getId()));
        // Log
        log("TC27", "Từ chối phiếu đã BORROWED",
            "Loan #" + loan.getId() + " đã BORROWED → rejectLoan()",
            "IllegalStateException",
            "Exception: " + ex.getMessage(),
            "Không thể từ chối phiếu đã được duyệt");
    }


    //
    //  NHÓM E – returnLoan / UC12 (TC28–TC33)
    //  Kiểm tra trả sách: tính phạt, nhập kho, validate trạng thái
    //

    @Test @Order(28)
    @DisplayName("TC28 – Trả đúng hạn → daysLate=0, fine=0đ [BR03]")
    void TC28_returnOnTimeNoFine() {
        // Arrange
        Loan loan = approvedLoan("ISBN28", "R28", "r28@mail.com");
        LocalDate returnDate = loan.getDueDate();    // đúng bằng hạn
        // Act
        ReturnResult result = service.returnLoan(loan.getId(), returnDate);
        // Assert
        assertEquals(0, result.getDaysLate());
        assertEquals(0, result.getFine());
        // Log
        log("TC28", "Trả đúng hạn, không phạt [BR03]",
            "returnDate=" + returnDate + " (= dueDate, đúng hạn)",
            "daysLate=0, fine=0đ",
            "daysLate=" + result.getDaysLate() + ", fine=" + result.getFine() + "đ",
            "Không phạt khi trả đúng hạn");
    }

    @Test @Order(29)
    @DisplayName("TC29 – Trả trước hạn 3 ngày → không phạt [BR03]")
    void TC29_returnEarlyNoFine() {
        // Arrange
        Loan loan = approvedLoan("ISBN29", "R29", "r29@mail.com");
        LocalDate earlyDate = loan.getDueDate().minusDays(3);   // trước hạn 3 ngày
        // Act
        ReturnResult result = service.returnLoan(loan.getId(), earlyDate);
        // Assert
        assertEquals(0, result.getDaysLate());
        assertEquals(0, result.getFine());
        // Log
        log("TC29", "Trả trước hạn, không phạt [BR03]",
            "returnDate=" + earlyDate + " (trước dueDate=" + loan.getDueDate() + " 3 ngày)",
            "daysLate=0, fine=0đ",
            "daysLate=" + result.getDaysLate() + ", fine=" + result.getFine() + "đ",
            "Không phạt khi trả sớm");
    }

    @Test @Order(30)
    @DisplayName("TC30 – Trả trễ 3 ngày → phạt = 3 × 5.000 = 15.000đ [BR03]")
    void TC30_returnLateFineCalculated() {
        // Arrange
        Loan loan = approvedLoan("ISBN30", "R30", "r30@mail.com");
        LocalDate lateDate = loan.getDueDate().plusDays(3);     // trễ 3 ngày
        // Act
        ReturnResult result = service.returnLoan(loan.getId(), lateDate);
        // Assert
        assertEquals(3,      result.getDaysLate());
        assertEquals(15_000, result.getFine());
        // Log
        log("TC30", "Trả trễ 3 ngày → phạt 15.000đ [BR03]",
            "returnDate=" + lateDate + " (trễ 3 ngày, dueDate=" + loan.getDueDate() + ")",
            "daysLate=3, fine=15.000đ",
            "daysLate=" + result.getDaysLate() + ", fine=" + String.format("%,d", result.getFine()) + "đ",
            "BR03 tính phạt đúng: " + result.getDaysLate() + " ngày × 5.000đ = " + String.format("%,d", result.getFine()) + "đ");
    }

    @Test @Order(31)
    @DisplayName("TC31 – Trả sách → availableCopies tăng lại 1 [UC12]")
    void TC31_returnLoanRestoresStock() {
        // Arrange
        Loan loan = approvedLoan("ISBN31", "R31", "r31@mail.com");
        // Act
        service.returnLoan(loan.getId(), TODAY.plusDays(10));
        // Assert
        Book book = bookRepo.findById("ISBN31").orElseThrow();
        assertEquals(2, book.getAvailableCopies());
        // Log
        log("TC31", "Trả sách khôi phục tồn kho [UC12]",
            "isbn=ISBN31, availableCopies=1 (đang mượn) → returnLoan()",
            "availableCopies = 2",
            "availableCopies = " + book.getAvailableCopies(),
            "Tồn kho được khôi phục sau khi trả sách");
    }

    @Test @Order(32)
    @DisplayName("TC32 – returnDate null → ném IllegalArgumentException")
    void TC32_returnLoanNullDateThrows() {
        // Arrange
        Loan loan = approvedLoan("ISBN32", "R32", "r32@mail.com");
        // Act + Assert
        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> service.returnLoan(loan.getId(), null));
        // Log
        log("TC32", "returnDate null",
            "returnDate=null",
            "IllegalArgumentException: \"Ngày trả không được null\"",
            "Exception: " + ex.getMessage(),
            "Bắt được returnDate null");
    }

    @Test @Order(33)
    @DisplayName("TC33 – Trả phiếu đang PENDING (chưa duyệt) → ném IllegalStateException")
    void TC33_returnNonBorrowedThrows() {
        // Arrange: phiếu ở trạng thái PENDING (chưa được duyệt)
        makeBook("ISBN33", 1);
        makeReader("R33", "r33@mail.com");
        Loan loan = service.requestLoan("R33", "ISBN33", TODAY);
        // Act + Assert: không thể trả phiếu chưa duyệt
        IllegalStateException ex = assertThrows(
            IllegalStateException.class,
            () -> service.returnLoan(loan.getId(), TODAY));
        // Log
        log("TC33", "Trả phiếu PENDING",
            "Loan #" + loan.getId() + " status=PENDING → returnLoan()",
            "IllegalStateException: \"không ở trạng thái Đang mượn\"",
            "Exception: " + ex.getMessage(),
            "Không thể trả phiếu chưa được duyệt");
    }


    //
    //  NHÓM F – renewLoan / UC13 (TC34–TC37)
    //  Kiểm tra gia hạn: +7 ngày, chỉ 1 lần, phải ở trạng thái BORROWED
    //

    @Test @Order(34)
    @DisplayName("TC34 – Gia hạn lần 1 → dueDate += 7 ngày, renewed=true [BR07, BR08]")
    void TC34_renewExtendsDueDateBy7() {
        // Arrange
        Loan loan = approvedLoan("ISBN34", "R34", "r34@mail.com");
        LocalDate originalDue = loan.getDueDate();
        // Act
        Loan renewed = service.renewLoan(loan.getId());
        // Assert
        assertEquals(originalDue.plusDays(7), renewed.getDueDate());
        assertTrue(renewed.isRenewed());
        // Log
        log("TC34", "Gia hạn +7 ngày [BR07, BR08]",
            "dueDate hiện tại=" + originalDue + " → renewLoan()",
            "dueDate=" + originalDue.plusDays(7) + ", renewed=true",
            "dueDate=" + renewed.getDueDate() + ", renewed=" + renewed.isRenewed(),
            "BR08 gia hạn +7 ngày đúng");
    }

    @Test @Order(35)
    @DisplayName("TC35 – Gia hạn lần 2 → ném IllegalStateException [BR07]")
    void TC35_renewTwiceThrows() {
        // Arrange: gia hạn lần đầu
        Loan loan = approvedLoan("ISBN35", "R35", "r35@mail.com");
        service.renewLoan(loan.getId());
        // Act + Assert: lần 2 phải lỗi
        IllegalStateException ex = assertThrows(
            IllegalStateException.class,
            () -> service.renewLoan(loan.getId()));
        // Log
        log("TC35", "Giới hạn gia hạn 1 lần [BR07]",
            "Loan đã renewed=true → renewLoan() lần 2",
            "IllegalStateException: \"đã được gia hạn 1 lần trước đó\"",
            "Exception: " + ex.getMessage(),
            "BR07 giới hạn gia hạn 1 lần hoạt động đúng");
    }

    @Test @Order(36)
    @DisplayName("TC36 – Gia hạn phiếu PENDING → ném IllegalStateException")
    void TC36_renewPendingLoanThrows() {
        // Arrange
        makeBook("ISBN36", 1);
        makeReader("R36", "r36@mail.com");
        Loan loan = service.requestLoan("R36", "ISBN36", TODAY);
        // Act + Assert
        IllegalStateException ex = assertThrows(
            IllegalStateException.class,
            () -> service.renewLoan(loan.getId()));
        // Log
        log("TC36", "Gia hạn phiếu PENDING",
            "Loan #" + loan.getId() + " status=PENDING → renewLoan()",
            "IllegalStateException: \"không ở trạng thái Đang mượn\"",
            "Exception: " + ex.getMessage(),
            "Không thể gia hạn phiếu chưa được duyệt");
    }

    @Test @Order(37)
    @DisplayName("TC37 – Gia hạn phiếu đã RETURNED → ném IllegalStateException")
    void TC37_renewReturnedLoanThrows() {
        // Arrange: trả sách trước
        Loan loan = approvedLoan("ISBN37", "R37", "r37@mail.com");
        service.returnLoan(loan.getId(), TODAY.plusDays(5));
        // Act + Assert
        IllegalStateException ex = assertThrows(
            IllegalStateException.class,
            () -> service.renewLoan(loan.getId()));
        // Log
        log("TC37", "Gia hạn phiếu đã RETURNED",
            "Loan #" + loan.getId() + " status=RETURNED → renewLoan()",
            "IllegalStateException: \"không ở trạng thái Đang mượn\"",
            "Exception: " + ex.getMessage(),
            "Không thể gia hạn phiếu đã trả");
    }


    //
    //  NHÓM G – Phiếu mượn không tồn tại (TC38–TC39)
    //  Kiểm tra xử lý khi dùng ID phiếu không có trong DB
    //

    @Test @Order(38)
    @DisplayName("TC38 – Duyệt phiếu không tồn tại → ném NoSuchElementException")
    void TC38_approveNonExistentLoanThrows() {
        // Act + Assert: loanId=99999 không có trong DB
        NoSuchElementException ex = assertThrows(
            NoSuchElementException.class,
            () -> service.approveLoan(99999L, TODAY));
        // Log
        log("TC38", "Duyệt phiếu không tồn tại",
            "loanId=99999 (không có trong DB)",
            "NoSuchElementException",
            "Exception: " + ex.getMessage(),
            "Phát hiện phiếu không tồn tại khi duyệt");
    }

    @Test @Order(39)
    @DisplayName("TC39 – Trả phiếu không tồn tại → ném NoSuchElementException")
    void TC39_returnNonExistentLoanThrows() {
        // Act + Assert
        NoSuchElementException ex = assertThrows(
            NoSuchElementException.class,
            () -> service.returnLoan(99999L, TODAY));
        // Log
        log("TC39", "Trả phiếu không tồn tại",
            "loanId=99999 (không có trong DB)",
            "NoSuchElementException",
            "Exception: " + ex.getMessage(),
            "Phát hiện phiếu không tồn tại khi trả");
    }


    //
    //  NHÓM H – Hiệu năng / Non-Functional Requirement (TC40)
    //  Kiểm tra thời gian xử lý đăng ký hàng loạt
    //

    @Test @Order(40)
    @DisplayName("TC40 – Đăng ký 100 độc giả liên tiếp phải hoàn thành trong 2 giây [NFR]")
    void TC40_register100ReadersUnder2Seconds() {
        // Arrange
        bookRepo.save(new Book("PERF", "Sách hiệu năng", "Tác giả", 200));
        long start = System.currentTimeMillis();
        // Act: đăng ký 100 độc giả tuần tự
        for (int i = 0; i < 100; i++) {
            service.registerReader(
                "PERF" + i, "Tên " + i, "perf" + i + "@mail.com", "pass123", "pass123");
        }
        long elapsed = System.currentTimeMillis() - start;
        // Assert
        assertTrue(elapsed < 2000,
            "100 lần đăng ký mất " + elapsed + "ms (vượt 2000ms)");
        // Log
        log("TC40", "Hiệu năng đăng ký 100 độc giả [NFR]",
            "100 lần gọi registerReader() tuần tự",
            "Thời gian < 2000ms",
            "Thời gian thực tế = " + elapsed + "ms",
            "Hiệu năng đạt yêu cầu (" + elapsed + "ms < 2000ms)");
    }
}
