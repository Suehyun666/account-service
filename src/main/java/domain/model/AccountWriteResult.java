package domain.model;

import domain.model.snapshot.AccountSnapshot;

public record AccountWriteResult(
        ServiceResult result,
        AccountSnapshot snapshot
) {
    public static AccountWriteResult of(ServiceResult r, AccountSnapshot s) {
        return new AccountWriteResult(r, s);
    }

    public boolean isSuccess() {
        return result != null && result.isSuccess();
    }
}
