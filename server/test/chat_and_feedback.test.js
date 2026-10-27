const { test, describe, before, after } = require('node:test');
const assert = require('node:assert/strict');
const app = require('../src/index');
const db = require('../src/db');
const jwt = require('jsonwebtoken');
const config = require('../src/config');
const financeRepository = require('../src/repositories/financeRepository');

describe('AI Chatbot & Formatted Feedback Test Suite', () => {
  let server;
  let baseUrl;
  let testUser;
  let authToken;

  before(async () => {
    await new Promise((resolve) => {
      server = app.listen(0, '127.0.0.1', () => {
        const port = server.address().port;
        baseUrl = `http://127.0.0.1:${port}`;
        resolve();
      });
    });

    // Create a dedicated test user
    const now = Date.now();
    const userRes = db.prepare(`
      INSERT INTO users (google_sub, email, name, email_verified, created_at, updated_at, last_login_at, is_active)
      VALUES (?, ?, ?, 1, ?, ?, ?, 1)
    `).run(`test_chat_feedback_${now}`, `ai_test_${now}@yosan.app`, 'AI Test User', now, now, now);
    
    testUser = { id: userRes.lastInsertRowid, email: `ai_test_${now}@yosan.app`, name: 'AI Test User' };

    // Seed an account
    const accountRes = financeRepository.insertAccount(
      testUser.id,
      'Salary Account',
      'HDFC Bank',
      '••••4567',
      'SAVINGS',
      50000,
      1,
      now
    );
    const accountId = accountRes.lastInsertRowid;

    // Seed some transactions (both income and expense)
    const today = new Date().toISOString().split('T')[0];
    financeRepository.insertTransaction(
      testUser.id,
      accountId,
      today,
      'Monthly Salary from Corp',
      0,
      50000,
      50000,
      'INCOME',
      null,
      'Salary',
      'Salary credit',
      'MANUAL',
      now
    );

    financeRepository.insertTransaction(
      testUser.id,
      accountId,
      today,
      'Supermarket Grocery Shopping',
      4200,
      0,
      4200,
      'EXPENSE',
      null,
      'Food & Dining',
      'UPI: grocer@hdfcbank Ref 987654321012',
      'MANUAL',
      now
    );

    financeRepository.insertTransaction(
      testUser.id,
      accountId,
      today,
      'Electricity & WiFi Bill',
      1800,
      0,
      1800,
      'EXPENSE',
      null,
      'Utilities',
      'Electricity bill pay',
      'MANUAL',
      now
    );

    // Seed a loan
    financeRepository.insertLoan(
      testUser.id,
      'John Doe Friend',
      'GIVEN',
      10000,
      0.0,
      '2026-11-30',
      'Emergency personal loan to John',
      now
    );

    authToken = jwt.sign(
      { userId: testUser.id, email: testUser.email },
      config.JWT_SECRET,
      { expiresIn: '1h' }
    );
  });

  after(async () => {
    if (server) {
      await new Promise((resolve) => server.close(resolve));
    }
  });

  // ==========================================
  // 1. AI CHATBOT TESTS
  // ==========================================

  test('AI Chat: Reject empty or missing message', async () => {
    const res = await fetch(`${baseUrl}/api/chat`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${authToken}`
      },
      body: JSON.stringify({ message: '' })
    });

    assert.equal(res.status, 400);
    const body = await res.json();
    assert.equal(body.error, 'INVALID_MESSAGE');
  });

  test('AI Chat: Returns structured table response for expenses', async () => {
    const res = await fetch(`${baseUrl}/api/chat`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${authToken}`
      },
      body: JSON.stringify({ message: 'Give me my monthly expense breakdown' })
    });

    assert.equal(res.status, 200);
    const body = await res.json();
    assert.equal(body.success, true);
    assert.ok(typeof body.reply === 'string' && body.reply.length > 0);
    
    // Verify Markdown table structure is strictly present
    assert.ok(body.reply.includes('|'), 'Response must contain markdown table pipe characters');
    assert.ok(body.reply.includes('| :---') || body.reply.includes('|---'), 'Response must contain markdown table alignment/separator syntax');
    assert.ok(body.source === 'gemini' || body.source === 'local_analyzer');
  });

  test('AI Chat: Returns table response for loans and debts query', async () => {
    const res = await fetch(`${baseUrl}/api/chat`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${authToken}`
      },
      body: JSON.stringify({ message: 'What are my active loans and debts?' })
    });

    assert.equal(res.status, 200);
    const body = await res.json();
    assert.equal(body.success, true);
    assert.ok(body.reply.includes('|'), 'Loans response must include markdown table');
  });

  test('AI Chat: Returns table response for account balances query', async () => {
    const res = await fetch(`${baseUrl}/api/chat`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${authToken}`
      },
      body: JSON.stringify({ message: 'Show me my account balances and net worth' })
    });

    assert.equal(res.status, 200);
    const body = await res.json();
    assert.equal(body.success, true);
    assert.ok(body.reply.includes('|'), 'Account balance response must include markdown table');
  });

  // ==========================================
  // 2. FORMATTED FEEDBACK TESTS
  // ==========================================

  test('Feedback: Rejects invalid category', async () => {
    const res = await fetch(`${baseUrl}/api/feedback`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        category: 'INVALID_CATEGORY',
        rating: 5,
        subject: 'Awesome app',
        description: 'I love using this app every day'
      })
    });

    assert.equal(res.status, 400);
    const body = await res.json();
    assert.equal(body.error, 'INVALID_CATEGORY');
  });

  test('Feedback: Rejects invalid rating (out of range 1-5)', async () => {
    const res = await fetch(`${baseUrl}/api/feedback`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        category: 'BUG_REPORT',
        rating: 6,
        subject: 'Crash on import',
        description: 'The app crashed when importing my statement'
      })
    });

    assert.equal(res.status, 400);
    const body = await res.json();
    assert.equal(body.error, 'INVALID_RATING');
  });

  test('Feedback: Rejects short subject (< 3 characters)', async () => {
    const res = await fetch(`${baseUrl}/api/feedback`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        category: 'FEATURE_REQUEST',
        rating: 4,
        subject: 'Hi',
        description: 'Please add dark mode export feature'
      })
    });

    assert.equal(res.status, 400);
    const body = await res.json();
    assert.equal(body.error, 'INVALID_SUBJECT');
  });

  test('Feedback: Rejects short description (< 5 characters)', async () => {
    const res = await fetch(`${baseUrl}/api/feedback`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        category: 'GENERAL',
        rating: 5,
        subject: 'Great design',
        description: 'Nice'
      })
    });

    assert.equal(res.status, 400);
    const body = await res.json();
    assert.equal(body.error, 'INVALID_DESCRIPTION');
  });

  test('Feedback: Successfully records valid feedback with system metadata', async () => {
    const syncId = `feedback_sync_${Date.now()}`;
    const res = await fetch(`${baseUrl}/api/feedback`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${authToken}`
      },
      body: JSON.stringify({
        category: 'STATEMENT_PARSING',
        rating: 4,
        subject: 'ICICI statement missing transfer tag',
        description: 'When importing ICICI Excel statement, NEFT transfers are categorized as personal expenses.',
        appVersion: 'v1.1.0',
        deviceModel: 'Pixel 7',
        androidVersion: 'Android 14 (API 34)',
        syncId
      })
    });

    assert.equal(res.status, 201);
    const body = await res.json();
    assert.equal(body.success, true);
    assert.ok(body.feedbackId > 0);

    // Verify stored row in database
    const row = db.prepare('SELECT * FROM feedbacks WHERE id = ?').get(body.feedbackId);
    assert.ok(row);
    assert.equal(row.category, 'STATEMENT_PARSING');
    assert.equal(row.rating, 4);
    assert.equal(row.user_id, testUser.id);
    assert.equal(row.device_model, 'Pixel 7');
    assert.equal(row.sync_id, syncId);
  });

  test('Feedback: Idempotency prevents duplicate rows on retry with same syncId', async () => {
    const syncId = `idempotent_test_${Date.now()}`;
    const payload = {
      category: 'BUG_REPORT',
      rating: 3,
      subject: 'Button unresponsive in dark mode',
      description: 'The export button does not respond occasionally when dark mode is enabled.',
      syncId
    };

    // First attempt
    const res1 = await fetch(`${baseUrl}/api/feedback`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    });
    assert.equal(res1.status, 201);
    const body1 = await res1.json();
    const firstId = body1.feedbackId;

    // Second attempt (e.g. offline retry)
    const res2 = await fetch(`${baseUrl}/api/feedback`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    });
    assert.equal(res2.status, 200);
    const body2 = await res2.json();
    assert.equal(body2.success, true);
    assert.equal(body2.feedbackId, firstId);
    assert.equal(body2.isDuplicate, true);
  });

  test('Feedback: Authenticated user can fetch their submitted feedbacks', async () => {
    const res = await fetch(`${baseUrl}/api/feedback/my`, {
      headers: { Authorization: `Bearer ${authToken}` }
    });

    assert.equal(res.status, 200);
    const body = await res.json();
    assert.equal(body.success, true);
    assert.ok(Array.isArray(body.feedbacks));
    assert.ok(body.feedbacks.length >= 1);
  });
});
