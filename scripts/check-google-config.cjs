// Public OAuth identifiers only. User accounts, tokens and signing keys stay out of git.
const assert = require('node:assert/strict');
const config = require('../config/google-oauth.json');
const platform = process.argv[2];
assert.equal(config.ios.urlScheme, config.ios.clientId.split('.').reverse().join('.'));
assert.equal(config.scope, 'https://www.googleapis.com/auth/drive.file');
if (platform === 'android') {
  // AuthorizationClient identifies the Android client by package and certificate.
  assert.equal(process.env.GOOGLE_CONFIGURED, 'true', 'Set GOOGLE_ANDROID_CONFIGURED=true after registering the release certificate');
} else if (platform === 'ios') {
  assert.equal(process.env.GOOGLE_CLIENT_ID, config.ios.clientId, 'GOOGLE_IOS_CLIENT_ID does not match the configured project');
  assert.equal(process.env.GOOGLE_URL_SCHEME, config.ios.urlScheme, 'GOOGLE_IOS_URL_SCHEME does not match the iOS OAuth client');
} else {
  throw new Error('Expected android or ios');
}
console.log(`Google ${platform} build configuration verified for project ${config.projectId}. Live authorization still requires a device test.`);
