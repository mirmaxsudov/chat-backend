package uz.mirmaxsudov.chatclonebackend.exceptions;

import lombok.Getter;

@Getter
public class AttachmentRangeNotSatisfiableException extends RuntimeException {
    private final long totalSize;

    public AttachmentRangeNotSatisfiableException(long totalSize) {
        super("Requested range is not satisfiable");
        this.totalSize = totalSize;
    }
}
