IF DB_ID('payments') IS NULL CREATE DATABASE payments;
GO
IF DB_ID('ledger') IS NULL CREATE DATABASE ledger;
GO
-- Snapshot isolation lets readers (balance/status queries) avoid blocking on writers.
ALTER DATABASE payments SET READ_COMMITTED_SNAPSHOT ON WITH ROLLBACK IMMEDIATE;
GO
ALTER DATABASE ledger SET READ_COMMITTED_SNAPSHOT ON WITH ROLLBACK IMMEDIATE;
GO
