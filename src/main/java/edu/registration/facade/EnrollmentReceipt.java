package edu.registration.facade;

import java.math.BigDecimal;

 
public record EnrollmentReceipt(String chainId, String studentId, String courseId,
                                String holdId, String seatId, String invoiceId, BigDecimal amount) { }
