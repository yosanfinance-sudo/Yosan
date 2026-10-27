const { test, describe, before, after } = require('node:test');
const assert = require('node:assert/strict');
const app = require('../src/index');

describe('AI Statement Parsing Test Suite', () => {
  let server;
  let baseUrl;

  before(async () => {
    await new Promise((resolve) => {
      server = app.listen(0, '127.0.0.1', () => {
        const port = server.address().port;
        baseUrl = `http://127.0.0.1:${port}`;
        resolve();
      });
    });
  });

  after(async () => {
    if (server) {
      await new Promise((resolve) => server.close(resolve));
    }
  });

  test('1. Reject missing or empty statement textContent', async () => {
    const res = await fetch(`${baseUrl}/api/statement/parse-ai`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({})
    });

    const body = await res.json();
    assert.equal(res.status, 400);
    assert.equal(body.error, 'INVALID_TEXT_CONTENT');
  });

  test('2. Cleanly parses Indian bank statement text, filtering out headers and disclaimers', async () => {
    const rawStatement = `
      STATE BANK OF INDIA
      Customer Name: John Doe
      Account Number: 123456789012
      Branch: KORAMANGALA BRANCH
      IFSC Code: SBIN0001234
      GSTIN: 29AABCS1429B1Z
      Date Range: 01/10/2026 to 05/10/2026
      --------------------------------------------------------
      01/10/2026 UPI/428919283921/DR/SWIGGY/SBIN00012/ORDER 450.00 12450.00
      02/10/2026 UPI/428919283922/CR/SALARY/INFOSYS LIMITED 75000.00 87450.00
      03/10/2026 UBER INDIA RIDE BANGALORE 320.00 87130.00
      04/10/2026 AMAZON PAY INDIA RETAIL 1499.00 85631.00
      --------------------------------------------------------
      Page 1 of 2
      Cheques are credited subject to realisation.
      This is a computer generated statement and requires no signature.
    `;

    const res = await fetch(`${baseUrl}/api/statement/parse-ai`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ textContent: rawStatement, fileName: 'sbi_october.pdf' })
    });

    const body = await res.json();
    assert.equal(res.status, 200);
    assert.equal(body.success, true);
    assert.ok(Array.isArray(body.transactions), 'transactions must be an array');
    assert.equal(body.totalParsed, 4, 'should extract exactly 4 valid transactions');

    const txs = body.transactions;

    // Check Swiggy (Food)
    assert.equal(txs[0].transactionDate, '2026-10-01');
    assert.equal(txs[0].amount, 450);
    assert.equal(txs[0].transactionType, 'EXPENSE');
    assert.equal(txs[0].suggestedCategory, 'Food & Dining');

    // Check Salary (Income)
    assert.equal(txs[1].transactionDate, '2026-10-02');
    assert.equal(txs[1].amount, 75000);
    assert.equal(txs[1].transactionType, 'INCOME');
    assert.equal(txs[1].suggestedCategory, 'Salary');

    // Check Uber (Travel)
    assert.equal(txs[2].transactionDate, '2026-10-03');
    assert.equal(txs[2].amount, 320);
    assert.equal(txs[2].transactionType, 'EXPENSE');
    assert.equal(txs[2].suggestedCategory, 'Travel & Fuel');

    // Check Amazon (Shopping)
    assert.equal(txs[3].transactionDate, '2026-10-04');
    assert.equal(txs[3].amount, 1499);
    assert.equal(txs[3].transactionType, 'EXPENSE');
    assert.equal(txs[3].suggestedCategory, 'Shopping');
  });

  test('3. Handles statement with no transaction rows gracefully', async () => {
    const junkOnly = `
      HDFC BANK NOTICE
      Annual Interest Rate Revision Notice w.e.f 01/10/2026
      Toll Free Customer Care: 1800 202 6161
      Registered Office: Senapati Bapat Marg, Lower Parel, Mumbai
      Charges are inclusive of GST.
    `;

    const res = await fetch(`${baseUrl}/api/statement/parse-ai`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ textContent: junkOnly, fileName: 'notice.txt' })
    });

    const body = await res.json();
    assert.equal(res.status, 200);
    assert.equal(body.success, true);
    assert.equal(body.totalParsed, 0);
    assert.deepEqual(body.transactions, []);
  });
});
