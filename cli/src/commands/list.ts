import { Command } from 'commander';
import axios from 'axios';
import { getApiBaseUrl } from '../config.js';

export const listCommand = new Command('list')
  .description('List available skills')
  .option('--domain <domain>', 'Filter by domain')
  .option('--team <team>', 'Filter by team')
  .option('--format <format>', 'Output format: table or json', 'table')
  .action(async (options: { domain?: string; team?: string; format?: string }) => {
    const chalk = (await import('chalk')).default;
    const baseUrl = getApiBaseUrl();

    try {
      const params: Record<string, string> = {};
      if (options.domain) params.domain = options.domain;
      if (options.team) params.team = options.team;

      const response = await axios.get(`${baseUrl}/skills`, { params });
      const skills: any[] = response.data;

      if (skills.length === 0) {
        console.log(chalk.dim('No skills found.'));
        return;
      }

      if (options.format === 'json') {
        console.log(JSON.stringify(skills, null, 2));
        return;
      }

      // Table output
      const nameWidth = 30;
      const domainWidth = 15;
      const typeWidth = 12;
      const scoreWidth = 6;

      const header = [
        'Name'.padEnd(nameWidth),
        'Domain'.padEnd(domainWidth),
        'Type'.padEnd(typeWidth),
        'Score'.padEnd(scoreWidth),
        'Summary',
      ].join('  ');

      console.log(chalk.bold(header));
      console.log('-'.repeat(100));

      for (const skill of skills) {
        const row = [
          truncate(skill.name || '', nameWidth).padEnd(nameWidth),
          truncate(skill.domain || '', domainWidth).padEnd(domainWidth),
          truncate(skill.type || '', typeWidth).padEnd(typeWidth),
          String(skill.qualityScore ?? '-').padEnd(scoreWidth),
          truncate(skill.summary || '', 40),
        ].join('  ');
        console.log(row);
      }

      console.log(chalk.dim(`\nTotal: ${skills.length} skills`));
    } catch (error: any) {
      const msg = error.response?.data?.message || error.message;
      console.error(chalk.red(`Error: ${msg}`));
      process.exit(1);
    }
  });

function truncate(str: string, maxLen: number): string {
  if (str.length <= maxLen) return str;
  return str.slice(0, maxLen - 1) + '\u2026';
}
