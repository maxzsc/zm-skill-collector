/**
 * CLI configuration — reads server URL from environment or uses default.
 */

export function getServerUrl(): string {
  return process.env.ZM_SKILL_SERVER || 'http://localhost:8080';
}

export function getApiBaseUrl(): string {
  const server = getServerUrl();
  return server.endsWith('/') ? `${server}api` : `${server}/api`;
}
