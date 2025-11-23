package domain.model.command;

public record UnreservePositionCommand(
        long accountId,
        String requestId
) {}
