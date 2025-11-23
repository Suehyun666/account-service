package domain.model.command;

public record UnreserveCashCommand(
        long accountId,
        String requestId
) {}
