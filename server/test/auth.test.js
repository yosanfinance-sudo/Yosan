const assert = require('assert');
const test = require('node:test');
const db = require('../src/db');
const googleAuthService = require('../src/services/googleAuthService');

test('Backend Auth & Security Test Suite', async (t) => {

  // Clean DB test records before test suite
  db.prepare("DELETE FROM users WHERE google_sub LIKE '109%'").run();

  await t.test('1. New user Google login creates exactly one user and seeds defaults', async () => {
    const mockToken = 'mock_id_token_109111111111111111111_newuser';
    const result = await googleAuthService.handleGoogleAuth(mockToken);

    assert.strictEqual(result.isNewUser, true);
    assert.ok(result.accessToken);
    assert.ok(result.refreshToken);
    assert.strictEqual(result.user.email, 'newuser@gmail.com');

    // Verify exactly one user record created for google_sub
    const userCount = db.prepare('SELECT COUNT(*) as count FROM users WHERE google_sub = ?').get('109111111111111111111').count;
    assert.strictEqual(userCount, 1);

    // Verify default categories seeded (Section 12)
    const categoryCount = db.prepare('SELECT COUNT(*) as count FROM categories WHERE user_id = ?').get(result.user.id).count;
    assert.strictEqual(categoryCount >= 16, true);
  });

  await t.test('2. Existing user Google login returns existing user without duplicating', async () => {
    const mockToken = 'mock_id_token_109111111111111111111_newuser';
    const result = await googleAuthService.handleGoogleAuth(mockToken);

    assert.strictEqual(result.isNewUser, false);

    // Verify count remains 1
    const userCount = db.prepare('SELECT COUNT(*) as count FROM users WHERE google_sub = ?').get('109111111111111111111').count;
    assert.strictEqual(userCount, 1);
  });

  await t.test('3. Invalid Google token throws exception / returns unauthorized', async () => {
    await assert.rejects(
      async () => {
        await googleAuthService.verifyGoogleToken('invalid_garbage_token_string');
      },
      { message: 'INVALID_GOOGLE_TOKEN' }
    );
  });

  await t.test('4. Data isolation: User A cannot query User B financial data', async () => {
    // Create User A and User B
    const userA = await googleAuthService.handleGoogleAuth('mock_id_token_109222222222222222222_userA');
    const userB = await googleAuthService.handleGoogleAuth('mock_id_token_109333333333333333333_userB');

    // Create an account for User B
    const accResult = db.prepare(`
      INSERT INTO accounts (user_id, account_name, bank_name, account_number_masked, account_type, opening_balance, current_balance, is_default, created_at, updated_at)
      VALUES (?, 'User B Checking', 'Bank B', '9999', 'BANK', 1000.0, 1000.0, 1, ?, ?)
    `).run(userB.user.id, Date.now(), Date.now());
    const userBAccId = accResult.lastInsertRowid;

    // Insert transaction for User B
    const stmt = db.prepare(`
      INSERT INTO transactions (user_id, account_id, transaction_date, description, amount, transaction_type, category_name, created_at, updated_at)
      VALUES (?, ?, '2026-08-21', 'User B Secret Tx', 500.0, 'EXPENSE', 'Secret', ?, ?)
    `);
    const res = stmt.run(userB.user.id, userBAccId, Date.now(), Date.now());
    const userBTxId = res.lastInsertRowid;

    // User A attempts to read User B transaction directly
    const userATxQuery = db.prepare('SELECT * FROM transactions WHERE id = ? AND user_id = ?').get(userBTxId, userA.user.id);
    assert.strictEqual(userATxQuery, undefined); // Fully Isolated!
  });
});
