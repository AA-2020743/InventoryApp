import bcrypt from "bcryptjs";
import { PrismaClient } from "@prisma/client";

const prisma = new PrismaClient();

async function main() {
  const email = process.env.SEED_OWNER_EMAIL ?? "owner@example.com";
  const password = process.env.SEED_OWNER_PASSWORD ?? "changeme123";
  const name = process.env.SEED_OWNER_NAME ?? "Owner";

  const existing = await prisma.user.findUnique({ where: { email } });
  if (existing) {
    console.log(`Owner account already exists: ${email}`);
    return;
  }

  const passwordHash = await bcrypt.hash(password, 10);
  await prisma.user.create({ data: { email, passwordHash, name } });
  console.log(`Created owner account: ${email} (change the password after first login!)`);
}

main()
  .catch((e) => {
    console.error(e);
    process.exit(1);
  })
  .finally(() => prisma.$disconnect());
