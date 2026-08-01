CREATE TABLE "WorkingDay" (
    "id" TEXT NOT NULL,
    "date" DATE NOT NULL,
    "isWorking" BOOLEAN NOT NULL DEFAULT true,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "WorkingDay_pkey" PRIMARY KEY ("id")
);

CREATE UNIQUE INDEX "WorkingDay_date_key" ON "WorkingDay"("date");
