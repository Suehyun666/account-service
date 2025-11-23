package domain.model;

import com.hts.generated.grpc.AccoutResult;

public record ServiceResult(AccoutResult code) {
    public static ServiceResult of(AccoutResult code) {
        return new ServiceResult(code);
    }

    public static ServiceResult success() {
        return new ServiceResult(AccoutResult.SUCCESS);
    }

    public boolean isSuccess() {
        return code == AccoutResult.SUCCESS;
    }
}
