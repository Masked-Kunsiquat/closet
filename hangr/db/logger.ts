// ---------------------------------------------------------------------------
// DB query logger — dev-mode only instrumentation.
//
// runQuery   — wraps a single db.*Async call; logs errors + slow queries.
// runTransaction — wraps db.withTransactionAsync; same logging contract.
//
// Both are no-ops in production (__DEV__ === false) with zero overhead.
// ---------------------------------------------------------------------------

const SLOW_QUERY_MS = 200;

/**
 * Run a single DB query with dev-mode logging.
 *
 * - Logs errors with the query label before rethrowing.
 * - Warns if the query exceeds SLOW_QUERY_MS.
 * - In production builds this is a transparent pass-through.
 */
export async function runQuery<T>(label: string, fn: () => Promise<T>): Promise<T> {
  if (!__DEV__) return fn();

  const t = Date.now();
  try {
    const result = await fn();
    const ms = Date.now() - t;
    if (ms > SLOW_QUERY_MS) console.warn(`[db/slow] ${label} took ${ms}ms`);
    return result;
  } catch (e) {
    console.error(`[db] ${label} failed`, e);
    throw e;
  }
}

/**
 * Run a DB transaction with dev-mode logging.
 *
 * Same contract as runQuery but intended for withTransactionAsync blocks
 * that contain multiple statements.
 */
export async function runTransaction(label: string, fn: () => Promise<void>): Promise<void> {
  if (!__DEV__) return fn();

  const t = Date.now();
  try {
    await fn();
    const ms = Date.now() - t;
    if (ms > SLOW_QUERY_MS) console.warn(`[db/slow] ${label} took ${ms}ms`);
  } catch (e) {
    console.error(`[db] ${label} failed`, e);
    throw e;
  }
}
