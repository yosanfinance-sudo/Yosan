const nodemailer = require('nodemailer');
require('../config');

class EmailService {
  constructor() {
    this.transporter = null;
    this.initTransporter();
  }

  initTransporter() {
    const service = process.env.SMTP_SERVICE;
    const host = process.env.SMTP_HOST;
    const user = process.env.SMTP_USER;
    const pass = process.env.SMTP_PASS;
    const port = parseInt(process.env.SMTP_PORT || '587', 10);
    const secure = process.env.SMTP_SECURE === 'true' || port === 465;

    if (service && user && pass) {
      try {
        this.transporter = nodemailer.createTransport({
          service,
          auth: { user, pass },
          connectionTimeout: 6000,
          greetingTimeout: 6000,
          socketTimeout: 6000
        });
      } catch (err) {
        console.error('[EMAIL_SERVICE] Failed to initialize SMTP service:', err.message);
        this.transporter = null;
      }
    } else if (host && user && pass) {
      try {
        this.transporter = nodemailer.createTransport({
          host,
          port,
          secure,
          auth: { user, pass },
          connectionTimeout: 6000,
          greetingTimeout: 6000,
          socketTimeout: 6000
        });
      } catch (err) {
        console.error('[EMAIL_SERVICE] Failed to initialize SMTP transporter:', err.message);
        this.transporter = null;
      }
    }
  }

  async sendOtpEmail(toEmail, otp, purpose = 'LOGIN') {
    if (!this.transporter) {
      this.initTransporter();
    }

    const purposeTitles = {
      LOGIN: 'Login to Yosan',
      REGISTER: 'Welcome to Yosan - Verify Email',
      CHANGE_EMAIL: 'Verify Your New Email - Yosan'
    };

    const purposeDescriptions = {
      LOGIN: 'Use the verification code below to complete your login to Yosan.',
      REGISTER: 'Use the verification code below to verify your email and finish setting up your account.',
      CHANGE_EMAIL: 'Use the verification code below to confirm and update your email address in Yosan.'
    };

    const title = purposeTitles[purpose] || 'Verification Code - Yosan';
    const description = purposeDescriptions[purpose] || 'Use the verification code below:';
    const fromAddress = process.env.SMTP_FROM || '"Yosan Finance" <noreply@yosan.app>';

    const htmlContent = `
      <!DOCTYPE html>
      <html>
      <head>
        <meta charset="utf-8">
        <style>
          body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background-color: #f8fafc; margin: 0; padding: 24px; }
          .container { max-width: 520px; margin: 0 auto; background: #ffffff; border-radius: 16px; padding: 32px; border: 1px solid #e2e8f0; box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.05); }
          .logo { font-size: 24px; font-weight: 800; color: #10b981; letter-spacing: -0.5px; margin-bottom: 20px; }
          .title { font-size: 20px; font-weight: 700; color: #0f172a; margin-bottom: 12px; }
          .desc { font-size: 15px; color: #475569; line-height: 1.5; margin-bottom: 24px; }
          .code-box { background: #f0fdf4; border: 2px dashed #10b981; border-radius: 12px; padding: 18px; text-align: center; margin-bottom: 24px; }
          .code { font-family: monospace; font-size: 32px; font-weight: 800; letter-spacing: 8px; color: #047857; }
          .validity { font-size: 13px; color: #64748b; text-align: center; margin-bottom: 24px; }
          .warning { font-size: 13px; color: #94a3b8; border-top: 1px solid #e2e8f0; padding-top: 20px; line-height: 1.4; }
        </style>
      </head>
      <body>
        <div class="container">
          <div class="logo">Yosan Finance</div>
          <div class="title">${title}</div>
          <div class="desc">${description}</div>
          <div class="code-box">
            <span class="code">${otp}</span>
          </div>
          <div class="validity">This code is valid for <strong>10 minutes</strong>. Do not share this code with anyone.</div>
          <div class="warning">
            If you did not request this verification code, please ignore this email or contact support. No changes will be made to your account.
          </div>
        </div>
      </body>
      </html>
    `;

    // 1. Direct SMTP or Brevo HTTP API (Uses Port 443 HTTPS)

    // 2. Brevo HTTP API (Uses Port 443 HTTPS)
    const brevoApiKey = process.env.BREVO_API_KEY;
    if (brevoApiKey) {
      try {
        const res = await fetch('https://api.brevo.com/v3/smtp/email', {
          method: 'POST',
          headers: {
            'api-key': brevoApiKey,
            'Content-Type': 'application/json'
          },
          body: JSON.stringify({
            sender: { name: 'Yosan Finance', email: process.env.SMTP_USER || 'yosanfinance@gmail.com' },
            to: [{ email: toEmail }],
            subject: `${otp} is your ${title} verification code`,
            htmlContent: htmlContent
          })
        });

        if (res.ok) {
          console.log(`[EMAIL_SERVICE] Delivered OTP email to ${toEmail} via Brevo HTTP API`);
          return { success: true, delivered: true, provider: 'brevo' };
        } else {
          const errData = await res.text();
          console.error('[EMAIL_SERVICE] Brevo API error response:', errData);
        }
      } catch (err) {
        console.error('[EMAIL_SERVICE] Failed to send email via Brevo HTTP API:', err.message);
      }
    }

    // 3. Fallback to direct SMTP (With strict timeout so Render Free Tier SMTP block cannot hang the request)
    if (this.transporter) {
      try {
        await Promise.race([
          this.transporter.sendMail({
            from: fromAddress,
            to: toEmail,
            subject: `${otp} is your ${title} verification code`,
            text: `${title}\n\n${description}\n\nVerification Code: ${otp}\n\nValid for 10 minutes.`,
            html: htmlContent
          }),
          new Promise((_, reject) =>
            setTimeout(() => reject(new Error('SMTP timeout: Render Free Tier blocks outbound SMTP ports (25, 465, 587)')), 6000)
          )
        ]);
        console.log(`[EMAIL_SERVICE] Delivered OTP email to ${toEmail} via SMTP`);
        return { success: true, delivered: true, provider: 'smtp' };
      } catch (err) {
        console.error('[EMAIL_SERVICE] SMTP delivery failed or timed out:', err.message);
      }
    }

    // Fallback: log to console in dev/test/simulated mode
    console.log(`[EMAIL_SERVICE] [FALLBACK/CONSOLE] OTP for ${toEmail} (${purpose}): [${otp}]`);
    return { success: true, delivered: false, simulated: true };
  }
}

module.exports = new EmailService();
