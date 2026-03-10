import { Command } from 'commander';
import axios from 'axios';
import { getApiBaseUrl } from '../config.js';

export const statusCommand = new Command('status')
  .description('Check submission processing status')
  .argument('<id>', 'Submission ID')
  .option('--poll', 'Poll until processing completes')
  .option('--interval <ms>', 'Poll interval in milliseconds', '3000')
  .action(async (id: string, options: { poll?: boolean; interval?: string }) => {
    const ora = (await import('ora')).default;
    const chalk = (await import('chalk')).default;
    const baseUrl = getApiBaseUrl();

    const fetchStatus = async () => {
      const response = await axios.get(`${baseUrl}/submissions/${id}/status`);
      return response.data;
    };

    const printStatus = (data: any) => {
      console.log(chalk.bold(`Submission: ${id}`));
      console.log(`  Status:   ${colorizeStatus(chalk, data.status)}`);
      if (data.stage) {
        console.log(`  Stage:    ${data.stage}`);
      }
      if (data.progress !== undefined) {
        const pct = Math.round(data.progress * 100);
        const bar = renderProgressBar(pct);
        console.log(`  Progress: ${bar} ${pct}%`);
      }
      if (data.skillCount !== undefined) {
        console.log(`  Skills:   ${data.skillCount} generated`);
      }
      if (data.error) {
        console.log(chalk.red(`  Error:    ${data.error}`));
      }
    };

    if (!options.poll) {
      try {
        const data = await fetchStatus();
        printStatus(data);
      } catch (error: any) {
        const chalk = (await import('chalk')).default;
        const msg = error.response?.data?.message || error.message;
        console.error(chalk.red(`Error: ${msg}`));
        process.exit(1);
      }
      return;
    }

    // Polling mode
    const interval = parseInt(options.interval || '3000', 10);
    const spinner = ora('Waiting for processing to complete...').start();

    try {
      while (true) {
        const data = await fetchStatus();

        if (data.status === 'COMPLETED' || data.status === 'FAILED') {
          spinner.stop();
          printStatus(data);
          break;
        }

        const pct = data.progress !== undefined ? Math.round(data.progress * 100) : 0;
        spinner.text = `Processing... ${data.stage || ''} (${pct}%)`;

        await new Promise((resolve) => setTimeout(resolve, interval));
      }
    } catch (error: any) {
      spinner.fail('Failed to fetch status');
      const msg = error.response?.data?.message || error.message;
      console.error(chalk.red(`Error: ${msg}`));
      process.exit(1);
    }
  });

function colorizeStatus(chalk: any, status: string): string {
  switch (status) {
    case 'COMPLETED':
      return chalk.green(status);
    case 'FAILED':
      return chalk.red(status);
    case 'PROCESSING':
      return chalk.yellow(status);
    case 'PENDING':
      return chalk.dim(status);
    default:
      return status;
  }
}

function renderProgressBar(pct: number): string {
  const width = 20;
  const filled = Math.round((pct / 100) * width);
  const empty = width - filled;
  return `[${'#'.repeat(filled)}${'-'.repeat(empty)}]`;
}
