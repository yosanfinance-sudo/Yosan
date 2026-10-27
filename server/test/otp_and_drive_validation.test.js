const assert = require('assert');
const test = require('node:test');
const db = require('../src/db');
const app = require('../src/index');

test('Email OTP and Google Drive Email Validation Test Suite', async (t) => {
  let server;
  let baseUrl;
  let testUserToken;
  let testUserEmail = `otptest_${Date.now()}@yosan.app`;
  let receivedOtp;

  await new Promise((resolve) => {
    server = app.listen(0, '127.0.0.1', () => {
      baseUrl = `http://127.0.0.1:${server.address().port}`;
      resolve();
    });
  });

  t.after(() => {
    server.close();
  });

  async function api(path, options = {}) {
    const headers = { 'Content-Type': 'application/json', ...(options.headers || {}) };
    if (options.token) {
      headers['Authorization'] = `Bearer ${options.token}`;
    }
    const response = await fetch(`${baseUrl}${path}`, {
      ...options,
      headers
    });
    const body = await response.json();
    return { status: response.status, body };
  }

  await t.test('1. Send OTP: Invalid email syntax is rejected', async () => {
    const res = await api('/api/auth/send-otp', {
      method: 'POST',
      body: JSON.stringify({ email: 'invalid-email-address' })
    });
    assert.strictEqual(res.status, 400);
    assert.strictEqual(res.body.error, 'INVALID_EMAIL_FORMAT');
  });

  await t.test('2. Send OTP: Valid email receives 6-digit code', async () => {
    const res = await api('/api/auth/send-otp', {
      method: 'POST',
      body: JSON.stringify({ email: testUserEmail, purpose: 'LOGIN' })
    });
    assert.strictEqual(res.status, 200);
    assert.strictEqual(res.body.success, true);
    assert.ok(res.body.devOtp);
    assert.strictEqual(res.body.devOtp.length, 6);
    receivedOtp = res.body.devOtp;
  });

  await t.test('3. Send OTP: Cooldown prevents spamming within 60s', async () => {
    const res = await api('/api/auth/send-otp', {
      method: 'POST',
      body: JSON.stringify({ email: testUserEmail, purpose: 'LOGIN' })
    });
    assert.strictEqual(res.status, 400);
    assert.strictEqual(res.body.error, 'COOLDOWN_ACTIVE');
  });

  await t.test('4. Verify OTP: Incorrect code returns 400 and counts attempts', async () => {
    const res = await api('/api/auth/verify-otp', {
      method: 'POST',
      body: JSON.stringify({ email: testUserEmail, otp: '000000', purpose: 'LOGIN' })
    });
    assert.strictEqual(res.status, 400);
    assert.strictEqual(res.body.error, 'INVALID_OTP');
  });

  await t.test('5. Verify OTP: Correct code creates new user and returns JWT tokens', async () => {
    const res = await api('/api/auth/verify-otp', {
      method: 'POST',
      body: JSON.stringify({
        email: testUserEmail,
        otp: receivedOtp,
        purpose: 'LOGIN',
        name: 'OTP Verified User'
      })
    });
    assert.strictEqual(res.status, 200);
    assert.strictEqual(res.body.isNewUser, true);
    assert.strictEqual(res.body.user.email, testUserEmail);
    assert.ok(res.body.accessToken);
    assert.ok(res.body.refreshToken);
    testUserToken = res.body.accessToken;
  });

  await t.test('6. Request Email Change: Rejected if new email is identical to current email', async () => {
    const res = await api('/api/auth/request-email-change', {
      method: 'POST',
      token: testUserToken,
      body: JSON.stringify({ newEmail: testUserEmail })
    });
    assert.strictEqual(res.status, 400);
    assert.strictEqual(res.body.error, 'SAME_EMAIL');
  });

  let newEmail = `updated_${Date.now()}@yosan.app`;
  let changeOtp;

  await t.test('7. Request Email Change: Sends verification code to new email', async () => {
    const res = await api('/api/auth/request-email-change', {
      method: 'POST',
      token: testUserToken,
      body: JSON.stringify({ newEmail })
    });
    assert.strictEqual(res.status, 200);
    assert.strictEqual(res.body.success, true);
    assert.ok(res.body.devOtp);
    changeOtp = res.body.devOtp;
  });

  await t.test('8. Verify Email Change: Wrong OTP is rejected', async () => {
    const res = await api('/api/auth/verify-email-change', {
      method: 'POST',
      token: testUserToken,
      body: JSON.stringify({ newEmail, otp: '111111' })
    });
    assert.strictEqual(res.status, 400);
    assert.strictEqual(res.body.error, 'INVALID_OTP');
  });

  await t.test('9. Verify Email Change: Correct OTP updates user email in database', async () => {
    const res = await api('/api/auth/verify-email-change', {
      method: 'POST',
      token: testUserToken,
      body: JSON.stringify({ newEmail, otp: changeOtp })
    });
    assert.strictEqual(res.status, 200);
    assert.strictEqual(res.body.success, true);
    assert.strictEqual(res.body.email, newEmail);

    // Verify /api/auth/me returns updated email
    const meRes = await api('/api/auth/me', { token: testUserToken });
    assert.strictEqual(meRes.status, 200);
    assert.strictEqual(meRes.body.user.email, newEmail);
  });

  await t.test('10. Google Drive Validation: Connecting with MISMATCHED email is rejected', async () => {
    const res = await api('/api/drive/connect', {
      method: 'POST',
      token: testUserToken,
      body: JSON.stringify({
        authCode: 'mock_code',
        driveEmail: 'different_person@gmail.com'
      })
    });
    assert.strictEqual(res.status, 400);
    assert.strictEqual(res.body.error, 'EMAIL_MISMATCH');
    assert.ok(res.body.message.includes('does not match your registered email'));
  });

  await t.test('11. Google Drive Validation: Connecting with MATCHING email succeeds', async () => {
    const res = await api('/api/drive/connect', {
      method: 'POST',
      token: testUserToken,
      body: JSON.stringify({
        authCode: 'mock_code',
        driveEmail: newEmail
      })
    });
    assert.strictEqual(res.status, 200);
    assert.strictEqual(res.body.success, true);
    assert.strictEqual(res.body.driveEmail, newEmail);

    // Verify drive status reflects connected email
    const statusRes = await api('/api/drive/status', { token: testUserToken });
    assert.strictEqual(statusRes.status, 200);
    assert.strictEqual(statusRes.body.isConnected, true);
    assert.strictEqual(statusRes.body.driveEmail, newEmail);
  });

  await t.test('12. Latest Backup: Returns false for brand new user, true after backup created', async () => {
    // 1. Brand new user with no backups or transactions
    const initialRes = await api('/api/drive/backup/latest', { token: testUserToken });
    assert.strictEqual(initialRes.status, 200);
    assert.strictEqual(initialRes.body.hasBackup, false);

    // 2. Perform a backup
    const backupRes = await api('/api/drive/backup/now', { method: 'POST', token: testUserToken });
    assert.strictEqual(backupRes.status, 200);

    // 3. Now latest backup should be available
    const afterRes = await api('/api/drive/backup/latest', { token: testUserToken });
    assert.strictEqual(afterRes.status, 200);
    assert.strictEqual(afterRes.body.hasBackup, true);
    assert.ok(afterRes.body.backupJson);
    const parsed = JSON.parse(afterRes.body.backupJson);
    assert.strictEqual(parsed.version, 1);
  });
});
