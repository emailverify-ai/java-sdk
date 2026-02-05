package com.emailverify.sdk.model;

/**
 * Filters for downloading verification results.
 */
public record ResultFilters(
    Boolean valid,
    Boolean invalid,
    Boolean catchall,
    Boolean role,
    Boolean unknown,
    Boolean disposable,
    Boolean risky
) {
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Boolean valid;
        private Boolean invalid;
        private Boolean catchall;
        private Boolean role;
        private Boolean unknown;
        private Boolean disposable;
        private Boolean risky;

        public Builder valid(boolean valid) {
            this.valid = valid;
            return this;
        }

        public Builder invalid(boolean invalid) {
            this.invalid = invalid;
            return this;
        }

        public Builder catchall(boolean catchall) {
            this.catchall = catchall;
            return this;
        }

        public Builder role(boolean role) {
            this.role = role;
            return this;
        }

        public Builder unknown(boolean unknown) {
            this.unknown = unknown;
            return this;
        }

        public Builder disposable(boolean disposable) {
            this.disposable = disposable;
            return this;
        }

        public Builder risky(boolean risky) {
            this.risky = risky;
            return this;
        }

        public ResultFilters build() {
            return new ResultFilters(valid, invalid, catchall, role, unknown, disposable, risky);
        }
    }
}
