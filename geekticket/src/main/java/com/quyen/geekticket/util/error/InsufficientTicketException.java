package com.quyen.geekticket.util.error;

public class InsufficientTicketException extends BusinessException {

    public InsufficientTicketException() {
        super(ErrorCode.INSUFFICIENT_TICKET_QUANTITY);
    }

    public InsufficientTicketException(String message) {
        super(ErrorCode.INSUFFICIENT_TICKET_QUANTITY, message);
    }
}
