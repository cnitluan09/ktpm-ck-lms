package com.example.library;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.regex.Pattern;

@Service
@Transactional
public class LibraryService {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private final BookRepository bookRepo;
    private final ReaderRepository readerRepo;
    private final LoanRepository loanRepo;

    public LibraryService(BookRepository bookRepo, ReaderRepository readerRepo, LoanRepository loanRepo) {
        this.bookRepo = bookRepo;
        this.readerRepo = readerRepo;
        this.loanRepo = loanRepo;
    }

    // ── UC01: Đăng ký tài khoản ───────────────────────────────────────────────

    public Reader registerReader(String readerId, String name, String email,
                                  String password, String confirmPassword) {
        // BR10: định dạng email
        if (email == null || !EMAIL_PATTERN.matcher(email).matches())
            throw new IllegalArgumentException("Email không đúng định dạng");

        // BR10: email đã tồn tại
        if (readerRepo.existsByEmail(email))
            throw new IllegalArgumentException("Email đã tồn tại");

        // BR06: mật khẩu tối thiểu 6 ký tự
        if (password == null || password.length() < 6)
            throw new IllegalArgumentException("Mật khẩu tối thiểu 6 ký tự");

        // Xác nhận mật khẩu
        if (!password.equals(confirmPassword))
            throw new IllegalArgumentException("Xác nhận mật khẩu không khớp");

        // BR09: tên tối đa 255 ký tự (kiểm tra trong constructor)
        Reader reader = new Reader(readerId, name, email);
        return readerRepo.save(reader);
    }

    // ── UC10: Yêu cầu mượn sách ──────────────────────────────────────────────

    public Loan requestLoan(String readerId, String isbn, LocalDate requestDate) {
        if (readerId == null) throw new IllegalArgumentException("Độc giả không được null");
        if (requestDate == null) throw new IllegalArgumentException("Ngày yêu cầu không được null");

        Reader reader = readerRepo.findById(readerId)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy độc giả: " + readerId));

        Book book = bookRepo.findById(isbn)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy sách: " + isbn));

        // BR01: tối đa 5 phiếu hiệu lực
        if (!reader.canBorrowMore())
            throw new IllegalStateException("Độc giả đã mượn đủ 5 cuốn sách");

        // BR04: nợ phạt không vượt 50.000đ
        if (!reader.isDebtWithinLimit())
            throw new IllegalStateException("Nợ phạt vượt ngưỡng cho phép (50.000đ)");

        // BR05: còn tồn kho (decreaseAvailable ném exception nếu hết)
        book.decreaseAvailable();
        bookRepo.save(book);

        Loan loan = new Loan(reader, book, requestDate);
        reader.incrementActiveLoans();
        readerRepo.save(reader);
        return loanRepo.save(loan);
    }

    // ── UC11: Duyệt / Từ chối yêu cầu mượn ─────────────────────────────────

    public Loan approveLoan(Long loanId, LocalDate approveDate) {
        Loan loan = getLoanOrThrow(loanId);
        if (loan.getStatus() != LoanStatus.PENDING)
            throw new IllegalStateException("Phiếu mượn không ở trạng thái Chờ duyệt");
        loan.approve(approveDate); // BR02: dueDate = approveDate + 14
        return loanRepo.save(loan);
    }

    public Loan rejectLoan(Long loanId) {
        Loan loan = getLoanOrThrow(loanId);
        if (loan.getStatus() != LoanStatus.PENDING)
            throw new IllegalStateException("Phiếu mượn không ở trạng thái Chờ duyệt");
        // Hoàn lại tồn kho và số lượng hiệu lực
        loan.getBook().increaseAvailable();
        bookRepo.save(loan.getBook());
        loan.getReader().decrementActiveLoans();
        readerRepo.save(loan.getReader());
        loan.reject();
        return loanRepo.save(loan);
    }

    // ── UC12: Trả sách ───────────────────────────────────────────────────────

    public ReturnResult returnLoan(Long loanId, LocalDate returnDate) {
        if (returnDate == null) throw new IllegalArgumentException("Ngày trả không được null");

        Loan loan = getLoanOrThrow(loanId);
        if (loan.getStatus() != LoanStatus.BORROWED)
            throw new IllegalStateException("Phiếu mượn không ở trạng thái Đang mượn");

        // BR03: phạt 5.000đ/ngày trễ
        long daysLate = Math.max(0, ChronoUnit.DAYS.between(loan.getDueDate(), returnDate));
        long fine = daysLate * 5_000;

        loan.markReturned(returnDate);
        loan.getBook().increaseAvailable();
        bookRepo.save(loan.getBook());
        loan.getReader().decrementActiveLoans();
        if (fine > 0) loan.getReader().addDebt(fine);
        readerRepo.save(loan.getReader());
        loanRepo.save(loan);

        return new ReturnResult(daysLate, fine);
    }

    // ── UC13: Gia hạn mượn sách ──────────────────────────────────────────────

    public Loan renewLoan(Long loanId) {
        Loan loan = getLoanOrThrow(loanId);
        if (loan.getStatus() != LoanStatus.BORROWED)
            throw new IllegalStateException("Phiếu mượn không ở trạng thái Đang mượn");
        // BR07: chỉ được gia hạn 1 lần
        if (loan.isRenewed())
            throw new IllegalStateException("Phiếu mượn đã được gia hạn 1 lần trước đó");
        loan.renew(); // BR08: dueDate += 7
        return loanRepo.save(loan);
    }

    // ── Query helpers ────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<Book> getAllBooks() { return bookRepo.findAll(); }

    @Transactional(readOnly = true)
    public List<Reader> getAllReaders() { return readerRepo.findAll(); }

    @Transactional(readOnly = true)
    public List<Loan> getAllLoans() { return loanRepo.findAll(); }

    @Transactional(readOnly = true)
    public List<Loan> getLoansByStatus(LoanStatus status) { return loanRepo.findByStatus(status); }

    @Transactional(readOnly = true)
    public Loan getLoanOrThrow(Long loanId) {
        return loanRepo.findById(loanId)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy phiếu mượn: " + loanId));
    }
}
