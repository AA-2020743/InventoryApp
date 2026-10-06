-- AlterTable
ALTER TABLE "Session" ADD COLUMN     "installId" TEXT,
ADD COLUMN     "newDevice" BOOLEAN NOT NULL DEFAULT false;

-- CreateTable
CREATE TABLE "KnownDevice" (
    "id" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "installId" TEXT,
    "deviceName" TEXT NOT NULL,
    "deviceModel" TEXT,
    "osVersion" TEXT,
    "firstIp" TEXT,
    "firstSeenAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "KnownDevice_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE INDEX "KnownDevice_userId_firstSeenAt_idx" ON "KnownDevice"("userId", "firstSeenAt");

-- CreateIndex
CREATE UNIQUE INDEX "KnownDevice_userId_installId_key" ON "KnownDevice"("userId", "installId");

-- AddForeignKey
ALTER TABLE "KnownDevice" ADD CONSTRAINT "KnownDevice_userId_fkey" FOREIGN KEY ("userId") REFERENCES "User"("id") ON DELETE CASCADE ON UPDATE CASCADE;
