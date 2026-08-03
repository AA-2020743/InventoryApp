-- Singleton settings row: currently just the starting capital the owner
-- invested, used to compute whether the business's net worth has fallen
-- below what it started with.
CREATE TABLE "BusinessSettings" (
    "id" TEXT NOT NULL,
    "startingValue" DECIMAL(12,2) NOT NULL DEFAULT 0,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "BusinessSettings_pkey" PRIMARY KEY ("id")
);
