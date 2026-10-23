package edu.registration.facade;

import java.math.BigDecimal;

/** What the client gets back from a successful enrollment. */
public record EnrollmentReceipt(String chainId, String studentId, String courseId,
                                String holdId, String seatId, String invoiceId, BigDecimal amount) { }
