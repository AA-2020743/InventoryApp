import { Router } from "express";
import { Prisma } from "@prisma/client";
import { prisma } from "../db";
import { asyncHandler, HttpError } from "../middleware/errorHandler";

export const statsRouter = Router();

function rangeFor(period: string, dateStr?: string): { from: Date; to: Date } {
  const date = dateStr ? new Date(dateStr) : new Date();
  if (period === "day") {
    const from = new Date(date);
    from.setHours(0, 0, 0, 0);
    const to = new Date(from);
    to.setDate(to.getDate() + 1);
    return { from, to };
  }
  if (period === "month") {
    const from = new Date(date.getFullYear(), date.getMonth(), 1);
    const to = new Date(date.getFullYear(), date.getMonth() + 1, 1);
    return { from, to };
  }
  throw new HttpError(400, "period must be 'day' or 'month'");
}

// Most sold items in a given day or month. sortBy=quantity (default) ranks by
// units sold; sortBy=profit ranks by actual profit contributed (useful
// alongside /margins, since a high-volume item can still be low-margin).
statsRouter.get(
  "/top-products",
  asyncHandler(async (req, res) => {
    const period = typeof req.query.period === "string" ? req.query.period : "day";
    const date = typeof req.query.date === "string" ? req.query.date : undefined;
    const limit = req.query.limit ? Number(req.query.limit) : 10;
    const sortBy = req.query.sortBy === "profit" ? "profit" : "quantity";
    const { from, to } = rangeFor(period, date);

    const items = await prisma.saleItem.findMany({
      where: { sale: { createdAt: { gte: from, lt: to } } },
      include: { product: true },
    });

    const byProduct = new Map<
      string,
      {
        productId: string;
        name: string;
        quantitySold: number;
        revenue: Prisma.Decimal;
        cost: Prisma.Decimal;
      }
    >();

    for (const item of items) {
      const key = item.productId;
      const cost = item.unitCost.mul(item.quantity);
      const existing = byProduct.get(key);
      const qty = item.quantity.toNumber();
      if (existing) {
        existing.quantitySold += qty;
        existing.revenue = existing.revenue.add(item.subtotal);
        existing.cost = existing.cost.add(cost);
      } else {
        byProduct.set(key, {
          productId: item.productId,
          name: item.product.name,
          quantitySold: qty,
          revenue: item.subtotal,
          cost,
        });
      }
    }

    const ranked = Array.from(byProduct.values())
      .map((p) => ({ ...p, profit: p.revenue.sub(p.cost) }))
      .sort((a, b) =>
        sortBy === "profit" ? b.profit.sub(a.profit).toNumber() : b.quantitySold - a.quantitySold
      )
      .slice(0, limit);

    res.json({ period, from, to, sortBy, items: ranked });
  })
);

// Static per-product margin ranking (independent of sales volume) — which
// items are the most profitable *per unit* to sell, e.g. to prioritize
// promoting or restocking. marginPercent is relative to selling price.
statsRouter.get(
  "/margins",
  asyncHandler(async (req, res) => {
    const limit = req.query.limit ? Number(req.query.limit) : 10;

    const products = await prisma.product.findMany({ where: { active: true } });

    const ranked = products
      .filter((p) => p.sellingPrice.gt(0))
      .map((p) => {
        const marginAmount = p.sellingPrice.sub(p.purchaseCost);
        const marginPercent = marginAmount.div(p.sellingPrice).mul(100);
        return {
          productId: p.id,
          name: p.name,
          purchaseCost: p.purchaseCost,
          sellingPrice: p.sellingPrice,
          marginAmount,
          marginPercent,
        };
      })
      .sort((a, b) => b.marginPercent.sub(a.marginPercent).toNumber())
      .slice(0, limit);

    res.json({ items: ranked });
  })
);

// Revenue/cost/profit time series, bucketed by day or month, for charting.
statsRouter.get(
  "/revenue",
  asyncHandler(async (req, res) => {
    const period = typeof req.query.period === "string" ? req.query.period : "day";
    const from = typeof req.query.from === "string" ? new Date(req.query.from) : new Date(Date.now() - 30 * 86400000);
    const to = typeof req.query.to === "string" ? new Date(req.query.to) : new Date();

    const sales = await prisma.sale.findMany({
      where: { createdAt: { gte: from, lte: to } },
      orderBy: { createdAt: "asc" },
    });

    const buckets = new Map<string, { revenue: Prisma.Decimal; cost: Prisma.Decimal }>();
    for (const sale of sales) {
      const key =
        period === "month"
          ? `${sale.createdAt.getFullYear()}-${String(sale.createdAt.getMonth() + 1).padStart(2, "0")}`
          : sale.createdAt.toISOString().slice(0, 10);
      const bucket = buckets.get(key) ?? { revenue: new Prisma.Decimal(0), cost: new Prisma.Decimal(0) };
      bucket.revenue = bucket.revenue.add(sale.totalAmount);
      bucket.cost = bucket.cost.add(sale.totalCost);
      buckets.set(key, bucket);
    }

    const series = Array.from(buckets.entries())
      .sort(([a], [b]) => a.localeCompare(b))
      .map(([bucket, v]) => ({
        bucket,
        revenue: v.revenue,
        cost: v.cost,
        profit: v.revenue.sub(v.cost),
      }));

    res.json({ period, from, to, series });
  })
);
