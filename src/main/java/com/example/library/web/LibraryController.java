package com.example.library.web;

import com.example.library.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;

@Controller
public class LibraryController {

    private final LibraryService service;

    public LibraryController(LibraryService service) {
        this.service = service;
    }

    // ── Trang chủ ────────────────────────────────────────────────────────────

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("books", service.getAllBooks());
        model.addAttribute("pendingLoans", service.getLoansByStatus(LoanStatus.PENDING));
        model.addAttribute("borrowedLoans", service.getLoansByStatus(LoanStatus.BORROWED));
        return "home";
    }

    // ── UC01: Đăng ký ────────────────────────────────────────────────────────

    @GetMapping("/register")
    public String registerForm() { return "register"; }

    @PostMapping("/register")
    public String register(@RequestParam String readerId,
                           @RequestParam String name,
                           @RequestParam String email,
                           @RequestParam String password,
                           @RequestParam String confirmPassword,
                           RedirectAttributes ra) {
        try {
            service.registerReader(readerId, name, email, password, confirmPassword);
            ra.addFlashAttribute("success", "Đăng ký thành công! Độc giả: " + name);
            return "redirect:/";
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/register";
        }
    }

    // ── UC10: Yêu cầu mượn sách ──────────────────────────────────────────────

    @GetMapping("/request")
    public String requestForm(Model model) {
        model.addAttribute("books", service.getAllBooks());
        model.addAttribute("readers", service.getAllReaders());
        return "request";
    }

    @PostMapping("/request")
    public String requestLoan(@RequestParam String readerId,
                              @RequestParam String isbn,
                              @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate requestDate,
                              RedirectAttributes ra) {
        try {
            Loan loan = service.requestLoan(readerId, isbn, requestDate);
            ra.addFlashAttribute("success", "Yêu cầu mượn đã được tạo (ID: " + loan.getId() + ")");
            return "redirect:/";
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/request";
        }
    }

    // ── UC11: Duyệt / Từ chối ────────────────────────────────────────────────

    @GetMapping("/approve")
    public String approveForm(Model model) {
        model.addAttribute("pendingLoans", service.getLoansByStatus(LoanStatus.PENDING));
        return "approve";
    }

    @PostMapping("/approve")
    public String approveLoan(@RequestParam Long loanId,
                              @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate approveDate,
                              RedirectAttributes ra) {
        try {
            Loan loan = service.approveLoan(loanId, approveDate);
            ra.addFlashAttribute("success", "Đã duyệt phiếu #" + loanId + ". Hạn trả: " + loan.getDueDate());
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/approve";
    }

    @PostMapping("/reject")
    public String rejectLoan(@RequestParam Long loanId, RedirectAttributes ra) {
        try {
            service.rejectLoan(loanId);
            ra.addFlashAttribute("success", "Đã từ chối phiếu #" + loanId);
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/approve";
    }

    // ── UC12: Trả sách ───────────────────────────────────────────────────────

    @GetMapping("/return")
    public String returnForm(Model model) {
        model.addAttribute("borrowedLoans", service.getLoansByStatus(LoanStatus.BORROWED));
        return "return";
    }

    @PostMapping("/return")
    public String returnLoan(@RequestParam Long loanId,
                             @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate returnDate,
                             RedirectAttributes ra) {
        try {
            ReturnResult result = service.returnLoan(loanId, returnDate);
            if (result.getFine() > 0) {
                ra.addFlashAttribute("warning",
                    "Trả sách thành công. Trễ " + result.getDaysLate() + " ngày. Phạt: " +
                    String.format("%,d", result.getFine()) + "đ");
            } else {
                ra.addFlashAttribute("success", "Trả sách thành công, đúng/trước hạn.");
            }
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/return";
    }

    // ── UC13: Gia hạn ────────────────────────────────────────────────────────

    @GetMapping("/renew")
    public String renewForm(Model model) {
        model.addAttribute("borrowedLoans", service.getLoansByStatus(LoanStatus.BORROWED));
        return "renew";
    }

    @PostMapping("/renew")
    public String renewLoan(@RequestParam Long loanId, RedirectAttributes ra) {
        try {
            Loan loan = service.renewLoan(loanId);
            ra.addFlashAttribute("success", "Đã gia hạn phiếu #" + loanId + ". Hạn trả mới: " + loan.getDueDate());
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/renew";
    }

    // ── Danh sách độc giả (phụ trợ) ─────────────────────────────────────────

    @GetMapping("/readers")
    public String readers(Model model) {
        model.addAttribute("readers", service.getAllReaders());
        return "readers";
    }

    @GetMapping("/loans")
    public String loans(Model model) {
        model.addAttribute("loans", service.getAllLoans());
        return "loans";
    }
}
