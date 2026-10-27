const assert = require('assert');
const test = require('node:test');
const db = require('../src/db');
const app = require('../src/index');

test('Comprehensive Backend API Endpoints Test Suite', async (t) => {
  let server;
  let baseUrl;
  let accessToken;
  let refreshToken;
  let userId;
  let accountId;
  let categoryId;
  let ruleId;
  let transactionId;
  let loanId;

  // Start HTTP server on port 0 (random available port) before tests
  await new Promise((resolve) => {
    server = app.listen(0, '127.0.0.1', () => {
      const port = server.address().port;
      baseUrl = `http://127.0.0.1:${port}`;
      resolve();
    });
  });

  t.after(() => {
    server.close();
  });

  // Helper fetch function
  async function api(path, options = {}) {
    const headers = { 'Content-Type': 'application/json', ...(options.headers || {}) };
    if (accessToken) {
      headers['Authorization'] = `Bearer ${accessToken}`;
    }
    const response = await fetch(`${baseUrl}${path}`, {
      ...options,
      headers
    });
    const body = await response.json();
    return { status: response.status, body };
  }

  await t.test('1. GET /api/health - Health check endpoint', async () => {
    const res = await api('/api/health');
    assert.strictEqual(res.status, 200);
    assert.strictEqual(res.body.status, 'ok');
  });

  await t.test('2. POST /api/auth/google - Authenticate mock user', async () => {
    const res = await api('/api/auth/google', {
      method: 'POST',
      body: JSON.stringify({ idToken: 'mock_id_token_109999999999999999999_testsuiteuser' })
    });
    assert.strictEqual(res.status, 200);
    assert.ok(res.body.accessToken);
    assert.ok(res.body.refreshToken);
    accessToken = res.body.accessToken;
    refreshToken = res.body.refreshToken;
    userId = res.body.user.id;
  });

  await t.test('3. GET /api/auth/me - Fetch authenticated user profile', async () => {
    const res = await api('/api/auth/me');
    assert.strictEqual(res.status, 200);
    assert.strictEqual(res.body.user.id, userId);
    assert.strictEqual(res.body.user.email, 'testsuiteuser@gmail.com');
  });


  await t.test('4. POST /api/auth/refresh - Refresh access token', async () => {
    const res = await api('/api/auth/refresh', {
      method: 'POST',
      body: JSON.stringify({ refreshToken })
    });
    assert.strictEqual(res.status, 200);
    assert.ok(res.body.accessToken);
    accessToken = res.body.accessToken;
  });

  await t.test('5. POST /api/accounts - Create financial account', async () => {
    const res = await api('/api/accounts', {
      method: 'POST',
      body: JSON.stringify({
        accountName: 'Primary Checking',
        bankName: 'Indian Bank',
        accountNumberMasked: '1234',
        accountType: 'BANK',
        openingBalance: 5000.0,
        isDefault: true
      })
    });
    assert.strictEqual(res.status, 201);
    assert.strictEqual(res.body.account_name, 'Primary Checking');
    accountId = res.body.id;
  });

  await t.test('6. GET /api/accounts - List user accounts', async () => {
    const res = await api('/api/accounts');
    assert.strictEqual(res.status, 200);
    assert.ok(Array.isArray(res.body));
    assert.strictEqual(res.body.length >= 1, true);
  });

  await t.test('7. POST /api/categories - Create custom category', async () => {
    const res = await api('/api/categories', {
      method: 'POST',
      body: JSON.stringify({
        name: 'Subscriptions',
        type: 'EXPENSE',
        icon: 'subscriptions',
        color: '#6366F1'
      })
    });
    assert.strictEqual(res.status, 201);
    assert.strictEqual(res.body.name, 'Subscriptions');
    categoryId = res.body.id;
  });

  await t.test('8. GET /api/categories - List user categories', async () => {
    const res = await api('/api/categories');
    assert.strictEqual(res.status, 200);
    assert.ok(Array.isArray(res.body));
    assert.strictEqual(res.body.length >= 17, true);
  });

  await t.test('9. POST /api/rules - Create categorization rule', async () => {
    const res = await api('/api/rules', {
      method: 'POST',
      body: JSON.stringify({
        pattern: 'NETFLIX',
        categoryId,
        categoryName: 'Subscriptions',
        transactionType: 'EXPENSE',
        priority: 10,
        matchType: 'CONTAINS'
      })
    });
    assert.strictEqual(res.status, 201);
    assert.strictEqual(res.body.pattern, 'NETFLIX');
    ruleId = res.body.id;
  });

  await t.test('10. GET /api/rules - List categorization rules', async () => {
    const res = await api('/api/rules');
    assert.strictEqual(res.status, 200);
    assert.ok(Array.isArray(res.body));
    assert.strictEqual(res.body.length >= 1, true);
  });

  await t.test('11. POST /api/transactions - Create transaction', async () => {
    const res = await api('/api/transactions', {
      method: 'POST',
      body: JSON.stringify({
        accountId,
        transactionDate: '2026-08-25',
        description: 'Netflix Monthly Subscription',
        debitAmount: 499.0,
        creditAmount: 0.0,
        amount: 499.0,
        transactionType: 'EXPENSE',
        categoryId,
        categoryName: 'Subscriptions',
        notes: 'Recurring charge'
      })
    });
    assert.strictEqual(res.status, 201);
    assert.strictEqual(res.body.description, 'Netflix Monthly Subscription');
    transactionId = res.body.id;
  });

  await t.test('12. GET /api/transactions - List transactions', async () => {
    const res = await api('/api/transactions');
    assert.strictEqual(res.status, 200);
    assert.ok(Array.isArray(res.body));
    assert.strictEqual(res.body.length >= 1, true);
  });

  await t.test('13. GET /api/transactions/:id - Fetch transaction by ID', async () => {
    const res = await api(`/api/transactions/${transactionId}`);
    assert.strictEqual(res.status, 200);
    assert.strictEqual(res.body.id, transactionId);
  });

  await t.test('14. POST /api/loans - Create loan record', async () => {
    const res = await api('/api/loans', {
      method: 'POST',
      body: JSON.stringify({
        borrowerLenderName: 'John Doe',
        loanType: 'GIVEN',
        amount: 2000.0,
        interestRate: 0.0,
        dueDate: '2026-12-31',
        notes: 'Friend loan'
      })
    });
    assert.strictEqual(res.status, 201);
    assert.strictEqual(res.body.borrower_lender_name, 'John Doe');
    loanId = res.body.id;
  });

  await t.test('15. GET /api/loans - List loans', async () => {
    const res = await api('/api/loans');
    assert.strictEqual(res.status, 200);
    assert.ok(Array.isArray(res.body));
    assert.strictEqual(res.body.length >= 1, true);
  });

  await t.test('16. POST /api/loans/:id/repay - Record loan repayment', async () => {
    const res = await api(`/api/loans/${loanId}/repay`, {
      method: 'POST',
      body: JSON.stringify({
        amount: 500.0,
        repaymentDate: '2026-08-25',
        paymentMethod: 'UPI',
        notes: 'Partial repayment'
      })
    });
    assert.strictEqual(res.status, 201);
    assert.strictEqual(res.body.totalRepaid, 500.0);
    assert.strictEqual(res.body.remaining, 1500.0);
    assert.strictEqual(res.body.status, 'PARTIALLY_PAID');
  });

  await t.test('17. POST /api/company-expenses - Create company expense', async () => {
    const res = await api('/api/company-expenses', {
      method: 'POST',
      body: JSON.stringify({
        expenseDate: '2026-08-25',
        title: 'Client Lunch',
        amount: 1200.0,
        categoryName: 'Meals',
        notes: 'Official expense'
      })
    });
    assert.strictEqual(res.status, 201);
    assert.strictEqual(res.body.title, 'Client Lunch');
  });

  await t.test('18. GET /api/company-expenses - List company expenses', async () => {
    const res = await api('/api/company-expenses');
    assert.strictEqual(res.status, 200);
    assert.ok(Array.isArray(res.body));
    assert.strictEqual(res.body.length >= 1, true);
  });

  await t.test('19. GET /api/dashboard - Get dashboard overview data', async () => {
    const res = await api('/api/dashboard');
    assert.strictEqual(res.status, 200);
    assert.ok(res.body.totalBalance !== undefined);
    assert.ok(res.body.recentTransactions);
  });

  await t.test('20. GET /api/drive/status - Check Drive connection status', async () => {
    const res = await api('/api/drive/status');
    assert.strictEqual(res.status, 200);
    assert.strictEqual(res.body.isConnected, false);
  });

  await t.test('21. POST /api/drive/connect - Connect Google Drive', async () => {
    const res = await api('/api/drive/connect', {
      method: 'POST',
      body: JSON.stringify({ authCode: 'mock_code' })
    });
    assert.strictEqual(res.status, 200);
    assert.strictEqual(res.body.success, true);
  });

  await t.test('22. POST /api/drive/backup/now - Execute Google Drive backup', async () => {
    const res = await api('/api/drive/backup/now', {
      method: 'POST'
    });
    assert.strictEqual(res.status, 200);
    assert.strictEqual(res.body.success, true);
  });

  await t.test('23. GET /api/drive/backup/list - List backups', async () => {
    const res = await api('/api/drive/backup/list');
    assert.strictEqual(res.status, 200);
    assert.ok(Array.isArray(res.body.backups));
  });

  await t.test('24. POST /api/drive/backup/restore - Restore backup data', async () => {
    const backupData = {
      version: 1,
      createdAt: new Date().toISOString(),
      userId: 1,
      accounts: [{ id: 1, accountName: 'Main Checking', accountType: 'BANK', openingBalance: 1000, currentBalance: 1500, isDefault: 1 }],
      categories: [{ id: 1, name: 'Food', type: 'EXPENSE' }],
      rules: [],
      transactions: [],
      loans: [],
      loanRepayments: [],
      companyExpenses: []
    };
    const res = await api('/api/drive/backup/restore', {
      method: 'POST',
      body: JSON.stringify({ confirm: true, backupData })
    });
    assert.strictEqual(res.status, 200);
    assert.strictEqual(res.body.success, true);
  });

  await t.test('25. POST /api/drive/disconnect - Disconnect Google Drive', async () => {
    const res = await api('/api/drive/disconnect', {
      method: 'POST'
    });
    assert.strictEqual(res.status, 200);
    assert.strictEqual(res.body.success, true);
  });

  await t.test('26. POST /api/auth/logout - Logout user session', async () => {
    const res = await api('/api/auth/logout', {
      method: 'POST',
      body: JSON.stringify({ refreshToken })
    });
    assert.strictEqual(res.status, 200);
    assert.strictEqual(res.body.success, true);
  });
});
